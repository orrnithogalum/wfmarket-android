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


