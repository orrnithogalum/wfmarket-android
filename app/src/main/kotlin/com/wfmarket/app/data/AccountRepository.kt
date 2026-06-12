package com.wfmarket.app.data

import android.content.Context
import com.wfmarket.api.WfmApi
import com.wfmarket.api.WfmErrorKind
import com.wfmarket.api.WfmException
import com.wfmarket.api.model.UpdateMe
import com.wfmarket.api.model.User
import com.wfmarket.api.socket.SocketEvent
import com.wfmarket.api.socket.WfmSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Durations the site offers for holding a status, in seconds. Index 0 is "until changed". */
val StatusDurations: List<Int?> = listOf(null, 1800, 3600, 7200, 14400)

/** Who is signed in, and every action on the account itself. */
class AccountRepository(
    private val api: WfmApi,
    private val sockets: WfmSockets,
    private val session: SessionStore,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val context: Context,
) {
    private val _isSignedIn = MutableStateFlow(session.token != null)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _me = MutableStateFlow<User?>(null)
    val me: StateFlow<User?> = _me.asStateFlow()

    private val _status = MutableStateFlow("offline")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _statusUntil = MutableStateFlow<String?>(null)
    val statusUntil: StateFlow<String?> = _statusUntil.asStateFlow()

    private val _onlineCount = MutableStateFlow<SocketEvent.OnlineCount?>(null)
    val onlineCount: StateFlow<SocketEvent.OnlineCount?> = _onlineCount.asStateFlow()

    /** Mirrors [AppSettings.heldStatus], whose writes land a moment later. */
    @Volatile private var held: HeldStatus? = settings.current.heldStatus

    /** Until then, status reports describe the connection just opened rather than a choice. */
    @Volatile private var openingUntil = 0L

    /** What the site last reported, which can differ from what's shown while it's being put right. */
    @Volatile private var reported: String? = null

    fun start() {
        scope.launch {
            sockets.events.collect { event ->
                when (event) {
                    is SocketEvent.StatusChanged -> onStatusChanged(event)
                    is SocketEvent.OnlineCount -> _onlineCount.value = event
                    else -> Unit
                }
            }
        }
        scope.launch {
            sockets.state.map { it.statusConnected to it.statusAuthorized }.distinctUntilChanged().collectLatest { (connected, authorized) ->
                when {
                    authorized -> {
                        openingUntil = System.currentTimeMillis() + OPENING_WINDOW_MS
                        delay(OPENING_WINDOW_MS)
                        restoreHeldStatus()
                    }
                    connected -> openingUntil = System.currentTimeMillis() + SIGN_IN_WINDOW_MS
                }
            }
        }
        if (session.token != null) scope.launch { runCatching { refresh() } }
    }

    private fun onStatusChanged(event: SocketEvent.StatusChanged) {
        reported = event.status
        // The site gives each new connection a status of its own (invisible, when tried), whatever
        // was chosen before. restoreHeldStatus puts the choice back, so this mustn't replace it,
        // and there's no point flashing it on screen in the meantime.
        val opening = System.currentTimeMillis() < openingUntil
        val kept = held?.takeIf { it.isActive() }
        if (opening && kept != null) {
            _status.value = kept.status
            return
        }
        _status.value = event.status
        _statusUntil.value = event.until
        if (opening) return
        val current = held
        if (event.status == current?.status) return
        if (current?.until != null && System.currentTimeMillis() >= current.until - EXPIRY_SLACK_MS) {
            // The hold ran out and the site has moved on from it. That isn't a new choice to keep.
            hold(null)
            return
        }
        // Changed somewhere else, such as on the website, so that's the status to keep now.
        hold(HeldStatus(event.status, event.until?.let(::parseMillis)))
    }

    private fun restoreHeldStatus() {
        val wanted = held?.takeIf { it.isActive() } ?: return
        if (reported == wanted.status) return
        val remaining = wanted.until?.let { ((it - System.currentTimeMillis()) / 1000).toInt() }
        // The site offers fixed holds, so the rest of one rounds up to the next it accepts.
        val duration = remaining?.let { left -> StatusDurations.filterNotNull().firstOrNull { it >= left } ?: StatusDurations.filterNotNull().last() }
        if (sockets.setStatus(wanted.status, duration)) {
            _status.value = wanted.status
        } else {
            reported?.let { _status.value = it }
        }
    }

    private fun hold(value: HeldStatus?) {
        held = value
        settings.setHeldStatus(value)
    }

    suspend fun refresh(): User? {
        if (session.token == null) return null
        val user = api.me()
        _me.value = user
        adoptAccountPreferences(user)
        return user
    }

    /** The site's account language and platform become the app's, as they do on the website. */
    private fun adoptAccountPreferences(user: User) {
        settings.setPlatform(user.platform)
        settings.setCrossplay(user.crossplay)
    }

    suspend fun signIn(email: String, password: String) {
        val result = api.signIn(email.trim(), password, session.deviceId)
        session.save(result.token)
        _isSignedIn.value = true
        try {
            refresh()
        } catch (e: WfmException) {
            // Signed in but /me failed: treat it as a failed sign-in rather than half a session.
            if (e.kind == WfmErrorKind.UNAUTHORIZED) throw e
        }
        sockets.reconnect()
    }

    /** Signs out on this device even if the site can't be told, such as with no network. */
    suspend fun signOut() {
        runCatching { api.signOut() }
        clearSession()
    }

    fun onSessionExpired() {
        if (session.token != null) clearSession()
    }

    private fun clearSession() {
        session.clear()
        _isSignedIn.value = false
        _me.value = null
        _status.value = "offline"
        hold(null)
        sockets.reconnect()
    }

    fun setStatus(status: String, durationOption: Int): Boolean {
        val duration = if (status == "invisible") null else StatusDurations.getOrNull(durationOption)
        val sent = sockets.setStatus(status, duration)
        if (sent) {
            _status.value = status
            hold(HeldStatus(status, duration?.let { System.currentTimeMillis() + it * 1000L }))
        }
        return sent
    }

    /** For a shortcut or notification, which can run before the connection is up. */
    suspend fun setStatusWhenConnected(status: String): Boolean {
        sockets.connect()
        withTimeoutOrNull(15_000) { sockets.state.first { it.statusAuthorized } } ?: return false
        // Let the new connection's opening report go by, or it would undo this.
        delay((openingUntil - System.currentTimeMillis()).coerceAtLeast(0))
        return setStatus(status, settings.current.statusDurationOption)
    }

    /** Invisible to everyone, and nothing to keep the connection open for. */
    fun goOffline() {
        if (!setStatus("invisible", 0)) hold(HeldStatus("invisible", null))
    }

    /** Applies preference changes locally at once, and to the account when signed in. */
    suspend fun updatePreferences(platform: String? = null, crossplay: Boolean? = null, locale: String? = null) {
        platform?.let(settings::setPlatform)
        crossplay?.let(settings::setCrossplay)
        locale?.let(settings::setLanguage)
        if (session.token != null) {
            _me.value = api.updateMe(UpdateMe(platform = platform, crossplay = crossplay, locale = locale))
        }
    }

    suspend fun updateAbout(about: String) {
        _me.value = api.updateMe(UpdateMe(about = about))
    }

    suspend fun uploadAvatar(bytes: ByteArray, fileName: String, mimeType: String) {
        _me.value = api.uploadAvatar(bytes, fileName, mimeType)
    }

    suspend fun uploadBackground(bytes: ByteArray, fileName: String, mimeType: String) {
        _me.value = api.uploadBackground(bytes, fileName, mimeType)
    }


