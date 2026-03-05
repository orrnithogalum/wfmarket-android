package com.wfmarket.api

import com.wfmarket.api.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Supplies per-request context, so the API layer needs no knowledge of how it's stored. */
interface WfmContext {
    val platform: String
    val language: String
    val crossplay: Boolean
    val token: String?

    /** Called when an authenticated request is rejected: the stored session is no longer valid. */
    fun onSessionExpired() {}
}

/**
 * The warframe.market HTTP API.
 *
 * The site is mid-migration: catalogue, orders and profile live on `/v2`, while sign-in, auctions,
 * chat, statistics, the ducanator and account settings are still on `/v1`. This class hides that
 * split behind one surface. It has no Android dependencies.
 */
class WfmApi(
    private val context: WfmContext,
    val http: OkHttpClient = defaultHttpClient(),
    private val userAgent: String = DEFAULT_USER_AGENT,
) {
    val json: Json get() = WfmJson

    // ---------------------------------------------------------------- Authentication

    /** Signs in and returns the session token. The password is sent once and never stored. */
    suspend fun signIn(email: String, password: String, deviceId: String): SignInResult {
        val body = buildJsonObject {
            put("auth_type", "header")
            put("email", email)
            put("password", password)
            put("device_id", deviceId)
        }
        val request = request(V1, "POST", "/auth/signin", emptyMap(), body, authenticated = false)
            .header("Authorization", "JWT")
            .build()
        return execute(request, authenticated = false) { response, text ->
            val fromHeader = response.header("Authorization")?.removePrefix("JWT ")?.removePrefix("Bearer ")?.trim()
            val fromCookie = response.headers("Set-Cookie")
                .firstOrNull { it.startsWith("JWT=") }
                ?.substringAfter("JWT=")?.substringBefore(';')
            val token = fromHeader?.takeIf { it.isNotBlank() && it != "JWT" } ?: fromCookie
                ?: throw WfmException(WfmErrorKind.UNKNOWN, listOf("The server didn't return a session."))
            SignInResult(token = token, rawUser = WfmJson.parseToJsonElement(text).jsonObject["payload"])
        }
    }

    suspend fun signOut() {
        runCatching { v1(JsonElement.serializer(), "POST", "/auth/signout", body = JsonObject(emptyMap())) }
    }

    // ---------------------------------------------------------------- Catalogue (public, v2)

    suspend fun versions(): Versions = v2(Versions.serializer(), "GET", "/versions")
    suspend fun items(): List<Item> = v2(ListSerializer(Item.serializer()), "GET", "/items")
    suspend fun item(slug: String): Item = v2(Item.serializer(), "GET", "/item/$slug")
    suspend fun itemSet(slug: String): ItemSet = v2(ItemSet.serializer(), "GET", "/item/$slug/set")
    suspend fun showcase(): Showcase = v2(Showcase.serializer(), "GET", "/dashboard/showcase")
    suspend fun rivenWeapons(): List<RivenWeapon> = v2(ListSerializer(RivenWeapon.serializer()), "GET", "/riven/weapons")
    suspend fun rivenAttributes(): List<RivenAttribute> = v2(ListSerializer(RivenAttribute.serializer()), "GET", "/riven/attributes")
    suspend fun lichWeapons(): List<NemesisWeapon> = v2(ListSerializer(NemesisWeapon.serializer()), "GET", "/lich/weapons")
    suspend fun lichEphemeras(): List<Ephemera> = v2(ListSerializer(Ephemera.serializer()), "GET", "/lich/ephemeras")
    suspend fun lichQuirks(): List<Quirk> = v2(ListSerializer(Quirk.serializer()), "GET", "/lich/quirks")
    suspend fun sisterWeapons(): List<NemesisWeapon> = v2(ListSerializer(NemesisWeapon.serializer()), "GET", "/sister/weapons")
    suspend fun sisterEphemeras(): List<Ephemera> = v2(ListSerializer(Ephemera.serializer()), "GET", "/sister/ephemeras")
    suspend fun sisterQuirks(): List<Quirk> = v2(ListSerializer(Quirk.serializer()), "GET", "/sister/quirks")
    suspend fun locations(): List<Location> = v2(ListSerializer(Location.serializer()), "GET", "/locations")
    suspend fun missions(): List<NamedEntity> = v2(ListSerializer(NamedEntity.serializer()), "GET", "/missions")
    suspend fun npcs(): List<NamedEntity> = v2(ListSerializer(NamedEntity.serializer()), "GET", "/npcs")
    suspend fun achievements(): List<Achievement> = v2(ListSerializer(Achievement.serializer()), "GET", "/achievements")

    // ---------------------------------------------------------------- Warframe's own data

    /**
     * The current Prime Resurgence, from the game's official world state, or null between rotations.
     * warframe.market's own showcase isn't used for this because it's maintained by hand and lags.
     */
    suspend fun primeResurgence(nowMillis: Long = System.currentTimeMillis()): PrimeResurgence? {
        val request = Request.Builder().url(WORLD_STATE).header("User-Agent", userAgent).get().build()
        val state = execute(request, authenticated = false) { _, text ->
            WfmJson.decodeFromString(WorldState.serializer(), text)
        }
        val trader = state.primeVaultTraders.firstOrNull {
            it.activation.date.millis <= nowMillis && nowMillis < it.expiry.date.millis
        } ?: return null
        val news = state.events
            .filter { event -> event.link?.contains("prime-resurgence") == true }
            .maxByOrNull { it.date.date.millis }
        val gameLanguage = when (context.language) {
            "zh-hant" -> "tc"
            "zh-hans" -> "zh"
            else -> context.language
        }
        return PrimeResurgence(
            expiresAtMillis = trader.expiry.date.millis,
            gameRefs = trader.manifest.map { it.itemType.replace("/StoreItems/", "/") },
            headline = news?.messages?.let { list ->
                (list.firstOrNull { it.language == gameLanguage } ?: list.firstOrNull { it.language == "en" })?.message
            },
            bannerUrl = news?.imageUrl,
            link = news?.link,
        )
    }

    /**
     * Last week's riven trades on [platform], which Warframe publishes every Monday, or null for
     * mobile, which it publishes none for.
     */
    suspend fun rivenTrades(platform: String): RivenTrades? {
        val file = RIVEN_TRADE_FILES[platform] ?: return null
        val request = Request.Builder().url("$RIVEN_TRADES$file.json").header("User-Agent", userAgent).get().build()
        return execute(request, authenticated = false) { response, text ->
            RivenTrades(
                platform = platform,
                publishedMillis = response.headers.getDate("Last-Modified")?.time ?: System.currentTimeMillis(),
                trades = WfmJson.decodeFromString(ListSerializer(RivenTrade.serializer()), relaxedJson(text)),
            )
        }
    }

    // ---------------------------------------------------------------- Statistics and tools (v1)

    suspend fun itemStatistics(slug: String): ItemStatistics = v1(ItemStatistics.serializer(), "GET", "/items/$slug/statistics")
    suspend fun dropSources(slug: String): List<DropSource> =
        v1(DropSourcesPayload.serializer(), "GET", "/items/$slug/dropsources").dropsources
    suspend fun ducats(): Ducats = v1(Ducats.serializer(), "GET", "/tools/ducats")

    // ---------------------------------------------------------------- Orders (v2)

    suspend fun recentOrders(): List<Order> = v2(ListSerializer(Order.serializer()), "GET", "/orders/recent")
    suspend fun ordersForItem(slug: String): List<Order> = v2(ListSerializer(Order.serializer()), "GET", "/orders/item/$slug")
    suspend fun topOrders(slug: String): TopOrders = v2(TopOrders.serializer(), "GET", "/orders/item/$slug/top")
    suspend fun ordersForUser(slug: String): List<Order> = v2(ListSerializer(Order.serializer()), "GET", "/orders/user/$slug")
    suspend fun myOrders(): List<Order> = v2(ListSerializer(Order.serializer()), "GET", "/orders/my")

    suspend fun createOrder(order: CreateOrder): Order =
        v2(Order.serializer(), "POST", "/order", body = WfmJson.encodeToJsonElement(order))

    suspend fun updateOrder(id: String, patch: UpdateOrder): Order =
        v2(Order.serializer(), "PATCH", "/order/$id", body = WfmJson.encodeToJsonElement(patch))

    suspend fun deleteOrder(id: String) {
        v2(JsonElement.serializer(), "DELETE", "/order/$id")
    }

    /** Records [quantity] of this order as traded. It's how sales reach the trading history. */
    suspend fun closeOrder(id: String, quantity: Int) {
        v2(JsonElement.serializer(), "POST", "/order/$id/close", body = buildJsonObject { put("quantity", quantity) })
    }

    /** Shows or hides all of the user's orders of one type (`sell`/`buy`), or all when null. */
    suspend fun setAllOrdersVisible(visible: Boolean, type: String?) {
        v2(JsonElement.serializer(), "PATCH", "/orders/group/all", body = buildJsonObject {
            put("visible", visible)
            type?.let { put("type", it) }
        })
    }

    // ---------------------------------------------------------------- Users

    suspend fun me(): User = v2(User.serializer(), "GET", "/me")
    suspend fun user(slug: String): User = v2(User.serializer(), "GET", "/user/$slug")
    suspend fun updateMe(patch: UpdateMe): User = v2(User.serializer(), "PATCH", "/me", body = WfmJson.encodeToJsonElement(patch))

    suspend fun uploadAvatar(bytes: ByteArray, fileName: String, mimeType: String): User =
        upload("/me/avatar", "avatar", bytes, fileName, mimeType)

    suspend fun uploadBackground(bytes: ByteArray, fileName: String, mimeType: String): User =
        upload("/me/background", "background", bytes, fileName, mimeType)

    private suspend fun upload(path: String, field: String, bytes: ByteArray, fileName: String, mimeType: String): User {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(field, fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val request = baseRequest(V2 + path, V2).method("POST", body).build()
        return execute(request, authenticated = true) { _, text -> unwrapV2(User.serializer(), text) }
    }

    suspend fun userAchievements(slug: String): List<Achievement> =
        v2(ListSerializer(Achievement.serializer()), "GET", "/achievements/user/$slug")

    /** The account's closed orders, i.e. the trading history on the profile's Statistics tab. */
    suspend fun closedOrders(slug: String): List<ClosedOrder> =
        v1(ClosedOrdersPayload.serializer(), "GET", "/profile/$slug/statistics").closedOrders

    suspend fun removeClosedOrder(id: String) {
        v1(JsonElement.serializer(), "DELETE", "/profile/statistics/remove/$id")
    }

    suspend fun reviews(slug: String, page: Int = 1): ReviewsPage =
        v1(ReviewsPage.serializer(), "GET", "/profile/$slug/reviews/$page")

    suspend fun createReview(slug: String, text: String, reviewType: Int) {
        v1(JsonElement.serializer(), "POST", "/profile/$slug/review", body = buildJsonObject {
            put("text", text)
            put("review_type", reviewType)
        })
    }

    suspend fun updateReview(slug: String, reviewId: String, text: String) {
        v1(JsonElement.serializer(), "PUT", "/profile/$slug/review/$reviewId", body = buildJsonObject { put("text", text) })
    }

    suspend fun deleteReview(slug: String, reviewId: String) {
        v1(JsonElement.serializer(), "DELETE", "/profile/$slug/review/$reviewId")
    }

    // ---------------------------------------------------------------- Account settings (v1)

    suspend fun changePassword(current: String, new: String, confirm: String) {
        v1(JsonElement.serializer(), "POST", "/settings/account/change_password", body = buildJsonObject {
            put("current_password", current)
            put("new_password", new)
            put("new_password_second", confirm)
        })
    }

    suspend fun changeEmail(current: String, newEmail: String) {
        v1(JsonElement.serializer(), "POST", "/settings/account/change_email", body = buildJsonObject {
            put("current_password", current)
            put("new_email", newEmail)
        })
    }

    suspend fun unlinkAccount(service: String) {
        v1(JsonElement.serializer(), "DELETE", "/settings/account/$service")
    }

    suspend fun requestAccountDeletion() {
        v1(JsonElement.serializer(), "POST", "/settings/account/extirpate")
    }

    suspend fun cancelAccountDeletion() {
        v1(JsonElement.serializer(), "DELETE", "/settings/account/extirpate")
    }

    // ---------------------------------------------------------------- Auctions (v1)

    suspend fun recentAuctions(): List<Auction> = v1(AuctionsPayload.serializer(), "GET", "/auctions").auctions
    suspend fun popularAuctions(): List<Auction> = v1(AuctionsPayload.serializer(), "GET", "/auctions/popular").auctions
    suspend fun searchAuctions(search: AuctionSearch): List<Auction> =
        v1(AuctionsPayload.serializer(), "GET", "/auctions/search", query = search.toQuery()).auctions
    suspend fun myAuctions(): List<Auction> = v1(AuctionsPayload.serializer(), "GET", "/profile/auctions").auctions
    suspend fun userAuctions(slug: String): List<Auction> = v1(AuctionsPayload.serializer(), "GET", "/profile/$slug/auctions").auctions
    suspend fun participatingAuctions(): List<Auction> = v1(AuctionsPayload.serializer(), "GET", "/profile/auctions/participant").auctions

    suspend fun auction(id: String): Auction = v1(AuctionPayload.serializer(), "GET", "/auctions/entry/$id").auction
    suspend fun auctionBids(id: String): List<Bid> = v1(BidsPayload.serializer(), "GET", "/auctions/entry/$id/bids").bids

    suspend fun createAuction(request: CreateAuction): Auction =
        v1(AuctionPayload.serializer(), "POST", "/auctions/create", body = WfmJson.encodeToJsonElement(request)).auction

    suspend fun updateAuction(id: String, update: UpdateAuction): Auction =
        v1(AuctionPayload.serializer(), "PUT", "/auctions/entry/$id", body = WfmJson.encodeToJsonElement(update)).auction

    suspend fun setAuctionVisible(id: String, visible: Boolean): Auction =
        v1(AuctionPayload.serializer(), "PUT", "/auctions/entry/$id", body = buildJsonObject { put("visible", visible) }).auction

    /** Closes the auction, or removes it outright when nobody has bid. */
    suspend fun closeAuction(id: String) {
        v1(JsonElement.serializer(), "PUT", "/auctions/entry/$id/close")
    }

    suspend fun markAuctionWinner(id: String, userId: String): Auction =
        v1(AuctionPayload.serializer(), "POST", "/auctions/entry/$id/win", body = buildJsonObject { put("user_id", userId) }).auction

    suspend fun clearAuctionWinner(id: String): Auction =
        v1(AuctionPayload.serializer(), "DELETE", "/auctions/entry/$id/win").auction

    // ---------------------------------------------------------------- Chat (v1, sending is over the socket)

    suspend fun chats(): List<Chat> = v1(ChatsPayload.serializer(), "GET", "/im/chats").chats

    /** Note: opening a chat is what marks its messages read on the server. */
    suspend fun chatMessages(chatId: String): List<ChatMessage> =
        v1(ChatMessagesPayload.serializer(), "GET", "/im/chats/$chatId").messages

    /** Opens the chat with a user, creating it if needed, and returns its id. */
    suspend fun openChat(userId: String): String =
        v1(ChatIdPayload.serializer(), "POST", "/im/chats", body = buildJsonObject { put("user_id", userId) }).chatId

    suspend fun deleteChat(chatId: String) {
        v1(JsonElement.serializer(), "DELETE", "/im/chats/$chatId")
    }

    suspend fun ignoreUser(userId: String, chatId: String?) {
        v1(JsonElement.serializer(), "POST", "/im/ignore", body = buildJsonObject {
            put("user_id", userId)
            chatId?.let { put("chat_id", it) }
        })
    }

    suspend fun unignoreUser(userId: String) {
        v1(JsonElement.serializer(), "DELETE", "/im/ignore/$userId")
    }

    // ---------------------------------------------------------------- Transport

    private val rateGate = RateGate(permitsPerSecond = 3)

    /** The auth scheme each API generation last accepted. Sign-in issues a token usable by both. */
    @Volatile private var v1Scheme = "JWT"
    @Volatile private var v2Scheme = "Bearer"

    /** Raw v2 `GET`, returned as text so callers can cache the response verbatim. */
    suspend fun rawV2(path: String): String {
        val request = baseRequest(V2 + path, V2).get().build()
        return execute(request, authenticated = false) { _, text -> text }
    }

    fun decodeItems(rawItemsResponse: String): List<Item> =
        unwrapV2(ListSerializer(Item.serializer()), rawItemsResponse)

    fun <T> decodeList(serializer: KSerializer<T>, raw: String): List<T> =
        unwrapV2(ListSerializer(serializer), raw)

    private suspend fun <T> v2(
        serializer: KSerializer<T>,
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
    ): T {
        val request = request(V2, method, path, query, body).build()
        return execute(request, authenticated = context.token != null) { _, text -> unwrapV2(serializer, text) }
    }

    private suspend fun <T> v1(
        serializer: KSerializer<T>,
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
    ): T {
        val request = request(V1, method, path, query, body).build()
        return execute(request, authenticated = context.token != null) { _, text ->
            val payload = WfmJson.parseToJsonElement(text).jsonObject["payload"] ?: JsonObject(emptyMap())
            WfmJson.decodeFromJsonElement(serializer, payload)
        }
    }

    private fun <T> unwrapV2(serializer: KSerializer<T>, text: String): T {
        val data = WfmJson.parseToJsonElement(text).jsonObject["data"] ?: JsonNull
        return WfmJson.decodeFromJsonElement(serializer, data)
    }

    private fun request(
        base: String,
        method: String,
        path: String,
        query: Map<String, String>,
        body: JsonElement?,
        authenticated: Boolean = true,
    ): Request.Builder {
        val url = (base + path).toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build().toString()
        val requestBody: RequestBody? = when {
            body != null -> body.toString().toRequestBody(JSON_TYPE)
            method == "GET" || method == "DELETE" -> null
            else -> "{}".toRequestBody(JSON_TYPE)
        }
        return baseRequest(url, base, authenticated).method(method, requestBody)
    }

    private fun baseRequest(url: String, base: String, authenticated: Boolean = true): Request.Builder =
        Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Platform", context.platform)
            .header("Language", context.language)
            .header("Crossplay", context.crossplay.toString())
            .apply {
                val token = context.token
                if (authenticated && token != null) {
                    header("Authorization", "${if (base == V1) v1Scheme else v2Scheme} $token")
                }
            }

    /** Reading a response and parsing it both block, so neither may touch the caller's thread. */
    private suspend fun <T> execute(
        request: Request,
        authenticated: Boolean,
        parse: (Response, String) -> T,
    ): T = withContext(Dispatchers.IO) { executeBlocking(request, authenticated, parse) }

    private suspend fun <T> executeBlocking(
        request: Request,
        authenticated: Boolean,
        parse: (Response, String) -> T,
    ): T {
        var attempt = 0
        var current = request
        var triedOtherScheme = false
        while (true) {
            rateGate.acquire()
            val response = try {
                http.newCall(current).await()
            } catch (e: IOException) {
                // GETs are idempotent, so ride out transient network failures.
                if (current.method == "GET" && attempt < 2) {
                    attempt++
                    delay(600L * attempt)
                    continue
                }
                throw WfmException(WfmErrorKind.NETWORK, emptyList(), cause = e)
            }
            response.use {
                val text = it.body.string()
                when {
                    it.isSuccessful -> try {
                        return parse(it, text)
                    } catch (e: WfmException) {
                        throw e
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw WfmException(WfmErrorKind.UNKNOWN, listOf("Unexpected response from warframe.market."), status = it.code, cause = e)
                    }
                    it.code == 429 && attempt < 3 -> {
                        attempt++
                        delay(1000L * attempt)
                    }
                    it.code >= 500 && current.method == "GET" && attempt < 2 -> {
                        attempt++
                        delay(800L * attempt)
                    }
                    it.code == 401 && authenticated && !triedOtherScheme && current.header("Authorization") != null -> {
                        // Both generations have accepted both schemes over time. Try the other
                        // before concluding that the session is dead.
                        triedOtherScheme = true
                        val isV1 = current.url.encodedPath.startsWith("/v1")
                        val other = if (current.header("Authorization")!!.startsWith("JWT")) "Bearer" else "JWT"
                        if (isV1) v1Scheme = other else v2Scheme = other
                        current = current.newBuilder().header("Authorization", "$other ${context.token}").build()
                    }
                    else -> {
                        if (it.code == 401 && authenticated) context.onSessionExpired()
                        throw failure(it.code, text)
                    }
                }
            }
        }
    }

    private fun failure(code: Int, text: String): WfmException {
        val errors = runCatching { WfmJson.parseToJsonElement(text).jsonObject["error"] }.getOrNull()
        val (messages, fields) = parseErrors(errors)
        val kind = when (code) {
            400, 422 -> WfmErrorKind.VALIDATION
            401 -> WfmErrorKind.UNAUTHORIZED
            403 -> WfmErrorKind.FORBIDDEN
            404 -> WfmErrorKind.NOT_FOUND
            429 -> WfmErrorKind.RATE_LIMITED
            in 500..599 -> WfmErrorKind.SERVER
            else -> WfmErrorKind.UNKNOWN
        }
        // A 404 body is often just the URL; don't show that to anyone.
        val shown = if (kind == WfmErrorKind.NOT_FOUND) emptyList() else messages
        return WfmException(kind, shown, fields, code)
    }

    companion object {
        const val V1 = "https://api.warframe.market/v1"
        const val V2 = "https://api.warframe.market/v2"
        const val WORLD_STATE = "https://api.warframe.com/cdn/worldState.php"
        const val RIVEN_TRADES = "https://www-static.warframe.com/repos/weeklyRivens"
        private val RIVEN_TRADE_FILES = mapOf("pc" to "PC", "ps4" to "PS4", "xbox" to "XB1", "switch" to "SWI")
        const val DEFAULT_USER_AGENT = "WfMarketAndroid/1.0"

        private val JSON_TYPE = "application/json".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

data class SignInResult(val token: String, val rawUser: JsonElement?)

val WfmJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = true
}

@kotlinx.serialization.Serializable
private data class DropSourcesPayload(val dropsources: List<DropSource> = emptyList())

