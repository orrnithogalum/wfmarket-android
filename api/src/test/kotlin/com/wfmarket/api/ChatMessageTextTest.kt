package com.wfmarket.api

import com.wfmarket.api.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageTextTest {
    private fun received(raw: String) = ChatMessage(rawMessage = raw).text

    @Test
    fun decodesTheApostropheTheServerSends() {
        assertEquals("Hey I'll buy your riven", received("Hey I&#x27;ll buy your riven"))
    }

    @Test
    fun decodesOtherEscapes() {
        assertEquals("<a> & \"b\" 'c' 'd'", received("&lt;a&gt; &amp; &quot;b&quot; &#39;c&#39; &apos;d&apos;"))
        assertEquals("😀", received("&#x1F600;"))
    }

    @Test
    fun decodesOnlyOnce() {
        assertEquals("&#39;", received("&amp;#39;"))
    }

    @Test
    fun leavesOrdinaryTextAlone() {
        for (text in listOf("AT&T", "a & b; c", "&unknown;", "&#xFFFFFFFFF;", "&#99999999;", "50% off")) {
            assertEquals(text, received(text))
        }
    }

    @Test
    fun fallsBackToTheRenderedMessage() {
        assertEquals("I'll", ChatMessage(message = "I&#x27;ll").text)
    }
}
