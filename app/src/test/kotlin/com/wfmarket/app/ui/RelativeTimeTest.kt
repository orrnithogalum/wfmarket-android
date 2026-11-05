package com.wfmarket.app.ui

import com.wfmarket.app.ui.components.epochMillis
import com.wfmarket.app.ui.components.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RelativeTimeTest {
    private fun ago(duration: Duration) = relativeTime(Instant.now().minus(duration).toString())

    @Test
    fun saysHowLongAgo() {
        assertEquals("just now", ago(Duration.ofSeconds(20)))
        assertEquals("5m ago", ago(Duration.ofMinutes(5).plusSeconds(10)))
        assertEquals("3h ago", ago(Duration.ofHours(3).plusMinutes(5)))
        assertEquals("2d ago", ago(Duration.ofDays(2).plusHours(1)))
        assertEquals("2mo ago", ago(Duration.ofDays(65)))
        assertEquals("1y ago", ago(Duration.ofDays(400)))
    }

    @Test
    fun readsBothTimestampShapesTheSiteSends() {
        assertEquals(1_793_891_319_000L, epochMillis("2026-11-05T15:08:39Z"))
        assertEquals(1_793_891_319_000L, epochMillis("2026-11-05T15:08:39+00:00"))
    }

    @Test
    fun saysNothingForWhatItCantRead() {
        assertEquals("", relativeTime(null))
        assertEquals("", relativeTime("yesterday"))
        assertNull(epochMillis("not a date"))
    }
}
