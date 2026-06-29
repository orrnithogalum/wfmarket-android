package com.wfmarket.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = "",
    @SerialName("chat_id") val chatId: String = "",
    val message: String = "",
    @SerialName("raw_message") val rawMessage: String? = null,
    @SerialName("message_from") val messageFrom: String = "",
    @SerialName("send_date") val sendDate: String? = null,
    /** Client-side only: true until the server echoes the message back. */
    val pending: Boolean = false,
    /** Client-side only: the server never confirmed this message. */
    val failed: Boolean = false,
    /** Client-side only: the id this message had while it was sending, so it stays the same bubble once confirmed. */
    val localId: String? = null,
) {
    /**
     * What the sender typed. The server HTML-escapes what it returns, so `'` arrives as `&#x27;`.
     * A message it hasn't echoed yet is still exactly what was typed here.
     */
    val text: String get() = (rawMessage ?: message).let { if (pending || failed) it else decodeHtmlEntities(it) }
}

private val HtmlEntity = Regex("&(?:#(\\d+)|#[xX]([0-9a-fA-F]+)|(amp|lt|gt|quot|apos|nbsp));")

