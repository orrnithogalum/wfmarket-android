package com.wfmarket.app.data

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wfmarket.api.WfmApi
import com.wfmarket.app.Shortcuts
import com.wfmarket.api.model.AuctionSearch
import com.wfmarket.api.socket.SocketEvent
import com.wfmarket.api.socket.WfmSockets
import com.wfmarket.app.notifications.Notifier
import com.wfmarket.app.notifications.RealtimeService
import com.wfmarket.app.notifications.ServiceNotice
import com.wfmarket.app.notifications.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Something to show on the Auctions tab once it's on screen, such as from a link or shortcut. */
data class AuctionRequest(val search: AuctionSearch? = null, val openFilters: Boolean = false)

/** What the connection and background work should be doing right now. */
private data class Delivery(val socket: Boolean, val worker: Boolean, val service: Boolean)

/** Manual dependency graph: one instance of each repository for the life of the process. */
class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsStore(context, scope)
    val session = SessionStore(context)
    private val wfmContext = AppWfmContext(settings, session) { account.onSessionExpired() }
    val api: WfmApi = WfmApi(wfmContext)
    val sockets: WfmSockets = WfmSockets(wfmContext, api.http)
    val notifier = Notifier(context)
    val connectivity = ConnectivityMonitor(context)
    val changes = DataChanges()
    val notices = Notices()

    @Volatile private var socketWanted = false

