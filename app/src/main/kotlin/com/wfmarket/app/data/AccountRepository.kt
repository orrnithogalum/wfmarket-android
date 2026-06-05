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

    /** Mirrors [AppSettings.heldStatus], whose writes land a moment later. */
    @Volatile private var held: HeldStatus? = settings.current.heldStatus

    /** Until then, status reports describe the connection just opened rather than a choice. */
    @Volatile private var openingUntil = 0L

    /** What the site last reported, which can differ from what's shown while it's being put right. */
    @Volatile private var reported: String? = null


