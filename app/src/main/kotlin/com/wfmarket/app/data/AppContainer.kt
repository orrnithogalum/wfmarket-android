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

    val catalog = CatalogRepository(context, api, settings, scope)
    val rivens = RivenRepository(context, api, settings, scope)
    val account: AccountRepository = AccountRepository(api, sockets, session, settings, scope, context)
    val chats: ChatRepository = ChatRepository(api, sockets, session, account, notifier, settings, scope)
    val market = MarketRepository(api, changes)
    val auctions = AuctionRepository(api, sockets, changes)

    private val _appForeground = MutableStateFlow(false)
    val appForeground: StateFlow<Boolean> = _appForeground.asStateFlow()

    val alerts = AuctionAlerts(context, api, sockets, notifier, myId = { account.me.value?.id }, isAppForeground = { _appForeground.value }, scope = scope)

    val auctionRequest = MutableStateFlow<AuctionRequest?>(null)

    /** Set when something outside the app, like the status notification, asks for the status sheet. */
    val statusSheetRequest = MutableStateFlow(false)

    /**
     * The status being kept while the app is closed, or null. It turns null on its own when a timed
     * hold runs out, so the connection closes with it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val keptStatus: StateFlow<HeldStatus?> =
        combine(account.isSignedIn, settings.flow.map { it.keepStatusInBackground to it.heldStatus }) { signedIn, (keep, held) ->
            held.takeIf { signedIn && keep }
        }.distinctUntilChanged().flatMapLatest { held ->
            flow {
                if (held == null || !held.isActive()) {
                    emit(null)
                    return@flow
                }
                emit(held)
                held.until?.let {
                    delay(it - System.currentTimeMillis())
                    emit(null)
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    private val instantDelivery: Flow<Boolean> =
        combine(account.isSignedIn, settings.flow) { signedIn, it -> signedIn && it.backgroundMessages && it.instantMessages }

    /** Whether the connection should stay open with the app closed, which takes the service. */
    val serviceWanted: StateFlow<Boolean> = combine(keptStatus, instantDelivery) { kept, instant -> kept != null || instant }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val serviceNotice: StateFlow<ServiceNotice?> = combine(keptStatus, instantDelivery, account.status) { kept, instant, status ->
        when {
            kept != null -> ServiceNotice(status.takeIf { it == "online" || it == "ingame" } ?: kept.status, kept.until, instant)
            instant -> ServiceNotice(null, null, true)
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    @Volatile private var socketWanted = false

