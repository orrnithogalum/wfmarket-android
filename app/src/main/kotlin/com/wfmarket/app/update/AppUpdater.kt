package com.wfmarket.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.wfmarket.app.BuildConfig
import com.wfmarket.app.data.Notices
import com.wfmarket.app.data.SettingsStore
import com.wfmarket.app.notifications.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A version on GitHub newer than this build, and the APK that installs it. */
@Serializable
data class Release(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkSize: Long,
    /** The SHA-256 GitHub records for the APK, in hex, when it has one. */
    val apkSha256: String? = null,
    val publishedAt: String? = null,
)

sealed interface UpdateState {
    /** Nothing newer than this build as of [checkedAt], or never checked when that's null. */
    data class UpToDate(val checkedAt: Long?) : UpdateState
    data object Checking : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val bytes: Long, val total: Long) : UpdateState {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data class Ready(val release: Release) : UpdateState
    data class Installing(val release: Release) : UpdateState

    /** A check, download or install that failed. With a [release], it was getting or installing that one. */
    data class Failed(val message: String, val release: Release? = null) : UpdateState
}

/** The release that a state is about, if it's about one. */
val UpdateState.release: Release?
    get() = when (this) {
        is UpdateState.Available -> release
        is UpdateState.Downloading -> release
        is UpdateState.Ready -> release
        is UpdateState.Installing -> release
        is UpdateState.Failed -> release
        else -> null
    }

/**
 * Keeps the app up to date from its GitHub releases, the way most apps published there do: it asks
 * GitHub for the latest release, downloads its APK when it's newer than this build, checks the
 * download against GitHub's checksum and this app's signature, and hands it to Android's package
 * installer. Android asks the user to confirm the first time, and afterwards may update without
 * asking, since the app is updating itself.
 */
class AppUpdater(
    private val context: Context,
    private val settings: SettingsStore,
    private val notifier: Notifier,
    private val notices: Notices,
    private val scope: CoroutineScope,
    private val isAppForeground: () -> Boolean,
) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val folder = File(context.noBackupFilesDir, "updates")
    private val current: Version? = Version.parse(BuildConfig.VERSION_NAME)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Debug builds have their own package name, so a release can't update them. A test feed, set
     * with the debug build's hook, points them at a stand-in release instead.
     */
    var testFeed: String? = null

    /** Whether this build can update itself at all. */
    val supported: Boolean get() = !BuildConfig.DEBUG || testFeed != null

    private val feedUrl: String
        get() = testFeed ?: "https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest"

    /** Every version and what changed. */
    val releasesPage = "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases"

    private val _state = MutableStateFlow(restore())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** How many update cards are on screen. While one is, it says the download finished, so nothing else does. */
    @Volatile var cardsShown = 0

    /** Set when a notification or link asks to install, for the screen that can ask permission to act on. */
    val installRequest = MutableStateFlow(false)

    /** The version whose card the user put away with Later, so it stays away until the next one. */
    private val _dismissed = MutableStateFlow(prefs.getString(KEY_DISMISSED, null))
    val dismissed: StateFlow<String?> = _dismissed.asStateFlow()

    private var checkJob: Job? = null
    private var download: Deferred<Boolean>? = null

    /** The last state from a previous run: a release already found, or downloaded, carries over. */
    private fun restore(): UpdateState {
        val checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L).takeIf { it > 0 }
        val release = prefs.getString(KEY_RELEASE, null)?.let { runCatching { json.decodeFromString(Release.serializer(), it) }.getOrNull() }
        if (release == null || !isNewer(release.version)) {
            clearDownloads()
            return UpdateState.UpToDate(checkedAt)
        }
        return if (apkFile(release).isComplete(release)) UpdateState.Ready(release) else UpdateState.Available(release)
    }

    private fun isNewer(version: String): Boolean {
        val theirs = Version.parse(version) ?: return false
        return current == null || theirs > current
    }

    /** Checks now if it's been a while, as when the app opens. */
    fun checkIfDue() {
        if (!supported || !settings.current.checkUpdates) return
        val last = prefs.getLong(KEY_CHECKED_AT, 0L)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        scope.launch { check() }
    }

    /** Looks for a newer release, and downloads it if downloading on its own is on. */
    fun checkNow() {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch { check(userAsked = true) }
    }

    /**
     * Asks GitHub for the latest release. A newer one is downloaded straight away when automatic
     * downloads are on; it waits for the user otherwise.
     */
    suspend fun check(userAsked: Boolean = false) {
        val before = _state.value
        if (before is UpdateState.Downloading || before is UpdateState.Installing) return
        _state.value = UpdateState.Checking
        val release = try {
            fetchLatest()
        } catch (e: CancellationException) {
            _state.value = before
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't check for updates", e)
            // A failed background check keeps whatever was known; only a check the user asked for says so.
            _state.value = if (userAsked) UpdateState.Failed(e.updateMessage()) else before.takeUnless { it is UpdateState.Checking } ?: UpdateState.UpToDate(null)
            return
        }
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_CHECKED_AT, now).apply()
        if (release == null || !isNewer(release.version)) {
            prefs.edit().remove(KEY_RELEASE).apply()
            clearDownloads()
            _state.value = UpdateState.UpToDate(now)
            return
        }
        prefs.edit().putString(KEY_RELEASE, json.encodeToString(Release.serializer(), release)).apply()
        clearDownloads(except = release)
        if (apkFile(release).isComplete(release)) {
            _state.value = UpdateState.Ready(release)
            return
        }
        _state.value = UpdateState.Available(release)
        if (settings.current.autoDownloadUpdates) download()
    }

    /** The latest published release with an APK attached, or null if there's none yet. */
    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(feedUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "WfMarket/${BuildConfig.VERSION_NAME} (Android)")
            .build()
        http.newCall(request).execute().use { response ->
            // A repository without a published release answers 404.
            if (response.code == 404) return@withContext null
            if (response.code == 403 || response.code == 429) throw IOException("GitHub is limiting update checks right now. Try again in a while.")
            if (!response.isSuccessful) throw IOException("GitHub answered ${response.code}.")
            val latest = json.decodeFromString(GitHubRelease.serializer(), response.body.string())
            if (latest.draft || latest.prerelease) return@withContext null
            val apk = latest.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
            Release(
                version = latest.tagName.removePrefix("v"),
                notes = latest.body.orEmpty(),
                pageUrl = latest.htmlUrl,
                apkUrl = apk.downloadUrl,
                apkSize = apk.size,
                apkSha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                publishedAt = latest.publishedAt,
            )
        }
    }

    /** Starts downloading the release found, unless that's already under way. */
    fun startDownload() {
        scope.launch { download() }
    }

    /**
     * Downloads the release found, resuming a download a previous run left partway, and returns
     * whether it's ready to install. Two callers share one download.
     */
    suspend fun download(): Boolean {
        download?.takeIf { it.isActive }?.let { return it.await() }
        val release = _state.value.release ?: return false
        return scope.async { runDownload(release) }.also { download = it }.await()
    }

    fun cancelDownload() {
        download?.cancel()
    }

    private suspend fun runDownload(release: Release): Boolean {
        val target = apkFile(release)
        if (target.isComplete(release)) {
            _state.value = UpdateState.Ready(release)
            return true
        }
        val partial = File(folder, target.name + ".part")
        _state.value = UpdateState.Downloading(release, partial.length(), release.apkSize)
        return try {
            withContext(Dispatchers.IO) { fetch(release, partial) }
            notifier.cancelUpdateProgress()
            verify(release, partial)
            if (!partial.renameTo(target)) throw IOException("Couldn't save the download.")
            _state.value = UpdateState.Ready(release)
            when {
                !isAppForeground() -> notifier.showUpdateReady(release.version)
                cardsShown == 0 -> notices.show("WF Market ${release.version} is ready to install", action = "Install", onAction = ::requestInstall)
            }
            true
        } catch (e: CancellationException) {
            notifier.cancelUpdateProgress()
            _state.value = UpdateState.Available(release)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't download ${release.version}", e)
            notifier.cancelUpdateProgress()
            if (e is BadDownload) partial.delete()
            _state.value = UpdateState.Failed(e.updateMessage(), release)
            false
        }
    }

    /** Streams the APK into [partial], picking up after what's already there. */
    private suspend fun fetch(release: Release, partial: File) {
        val context = currentCoroutineContext()
        folder.mkdirs()
        var have = partial.length().takeIf { it < release.apkSize } ?: 0L
        val request = Request.Builder().url(release.apkUrl)
            .header("User-Agent", "WfMarket/${BuildConfig.VERSION_NAME} (Android)")
            .apply { if (have > 0) header("Range", "bytes=$have-") }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The download failed (${response.code}).")
            // A server that ignores the range sends the whole file again.
            if (response.code != 206) have = 0
            val total = if (have > 0) release.apkSize else response.body.contentLength().takeIf { it > 0 } ?: release.apkSize
            var lastShown = 0L
            response.body.byteStream().use { input ->
                java.io.FileOutputStream(partial, have > 0).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytes = have
                    while (true) {
                        context.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytes += read
                        val now = System.currentTimeMillis()
                        // Often enough for the bar to move smoothly, not so often that it floods the screen.
                        if (now - lastShown >= 80 || bytes == total) {
                            lastShown = now
                            _state.value = UpdateState.Downloading(release, bytes, total)
                            if (!isAppForeground()) notifier.showUpdateProgress(release.version, bytes, total)
                        }
                    }
                }
            }
        }
    }

    /**
     * Refuses a download that isn't what GitHub says it is, or that Android wouldn't install over
     * this app anyway: another app's, an older version's, or one signed with another key.
     */
    private fun verify(release: Release, file: File) {
        release.apkSha256?.let { expected ->
            if (sha256(file) != expected) throw BadDownload("The download was damaged. Try again.")
        }
        val pm = context.packageManager
        val archive = pm.signedArchiveInfo(file.path) ?: throw BadDownload("The download isn't an app Android can read. Try again.")
        if (archive.packageName != context.packageName) throw BadDownload("This release is a different app, so it can't update this one.")
        val installed = pm.signedPackageInfo(context.packageName)
        if (archive.longVersionCode <= installed.longVersionCode) throw BadDownload("This release isn't newer than the installed app.")
        if (archive.signatures() != installed.signatures()) {
            throw BadDownload("This release is signed with a different key than the installed app, so Android won't install it over this one. Install it from GitHub instead.")
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.signedArchiveInfo(path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        else getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)

    @Suppress("DEPRECATION")
    private fun PackageManager.signedPackageInfo(name: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) getPackageInfo(name, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        else getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)

    private fun PackageInfo.signatures(): Set<String> =
        signingInfo?.apkContentsSigners.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet()

    /** Whether Android lets this app install packages. The user grants it once, in system settings. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Hands the downloaded APK to Android's package installer. The app closes as the update
     * installs, and the new version reports back through [onPackageReplaced].
     */
    fun install() {
        val release = (_state.value as? UpdateState.Ready)?.release ?: return
        val file = apkFile(release)
        if (!file.isComplete(release)) {
            _state.value = UpdateState.Available(release)
            return
        }
        _state.value = UpdateState.Installing(release)
        scope.launch(Dispatchers.IO) {
            try {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    setSize(file.length())
                    setInstallReason(PackageManager.INSTALL_REASON_USER)
                    // An app updating itself may skip the confirmation once the user has allowed it to install apps.
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        file.inputStream().use { it.copyTo(output) }
                        session.fsync(output)
                    }
                    prefs.edit().putString(KEY_INSTALLING, release.version).apply()
                    val status = Intent(context, InstallReceiver::class.java).setAction(InstallReceiver.ACTION_STATUS)
                    // Mutable, so the installer can add the result to it.
                    val callback = PendingIntent.getBroadcast(context, id, status, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    session.commit(callback.intentSender)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't start installing ${release.version}", e)
                prefs.edit().remove(KEY_INSTALLING).apply()
                _state.value = UpdateState.Failed("Couldn't start the install: ${e.message ?: "unknown error"}", release)
            }
        }
    }

    /** The installer's answer when it can't finish on its own: the user said no, or it failed. */
    fun onInstallResult(status: Int, message: String?) {
        val release = _state.value.release ?: return
        prefs.edit().remove(KEY_INSTALLING).apply()
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Installing(release)
            // Turned down at the confirmation: still ready for when the user wants it.
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Ready(release)
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed("There isn't enough storage to install the update.", release)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed("Android couldn't install this version over the one you have.", release)
            else -> UpdateState.Failed(message?.takeIf { it.isNotBlank() } ?: "The update didn't install.", release)
        }
    }

    /** The new version's first word after Android replaced the app: tidy up, and say it's done. */
    fun onPackageReplaced() {
        val installed = prefs.getString(KEY_INSTALLING, null)
        prefs.edit().remove(KEY_INSTALLING).remove(KEY_RELEASE).apply()
        clearDownloads()
        _state.value = UpdateState.UpToDate(prefs.getLong(KEY_CHECKED_AT, 0L).takeIf { it > 0 })
        if (installed == BuildConfig.VERSION_NAME) notifier.showUpdated(installed)
    }

    /** Puts the card for [version] away until a newer one comes out. */
    fun dismiss(version: String) {
        prefs.edit().putString(KEY_DISMISSED, version).apply()
        _dismissed.value = version
    }

    /** Asks for the install from outside a screen, as a notification does. */
    fun requestInstall() = installRequest.update { true }

    private fun apkFile(release: Release) = File(folder, "wfmarket-${release.version}.apk")

    private fun File.isComplete(release: Release) = exists() && length() == release.apkSize

    private fun clearDownloads(except: Release? = null) {
        val keep = except?.let { apkFile(it).name }
        folder.listFiles()?.filter { keep == null || !it.name.startsWith(keep) }?.forEach { it.delete() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private class BadDownload(message: String) : IOException(message)

    private fun Throwable.updateMessage(): String = when (this) {
        is BadDownload -> message.orEmpty()
        is java.net.UnknownHostException, is java.net.ConnectException -> "No connection. Try again when you're online."
        is java.net.SocketTimeoutException -> "GitHub took too long to answer. Try again."
        else -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url") val downloadUrl: String,
        val digest: String? = null,
    )

    companion object {
        private const val TAG = "AppUpdater"
        private const val KEY_CHECKED_AT = "checked_at"
        private const val KEY_RELEASE = "release"
        private const val KEY_INSTALLING = "installing"
        private const val KEY_DISMISSED = "dismissed"

        /** How long the app waits between checks it makes on its own. */
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
