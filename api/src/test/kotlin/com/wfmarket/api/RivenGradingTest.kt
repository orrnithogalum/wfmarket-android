package com.wfmarket.api

import com.wfmarket.api.model.RivenClass
import com.wfmarket.api.model.RivenGrade
import com.wfmarket.api.model.RivenGrading
import com.wfmarket.api.model.RivenStat
import com.wfmarket.api.model.RivenTrade
import com.wfmarket.api.model.RivenWeapon
import com.wfmarket.api.model.relaxedJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RivenGradingTest {
    private fun grades(rivenClass: RivenClass, rank: Int, vararg stats: RivenStat, oldDisposition: Double? = null) =
        RivenGrading.grade(rivenClass, stats.toList(), rank, oldDisposition)!!

    @Test
    fun matchesAPublishedGrade() {
        // The Altair bot's example: a Bubonico at its old 0.75 disposition.
        val rolls = grades(
            RivenClass.SHOTGUN, 8,
            RivenStat("multishot", 88.4, true),
            RivenStat("base_damage_/_melee_damage", 119.6, true),
            RivenStat("critical_damage", 62.7, true),
            RivenStat("puncture_damage", -72.5, false),
            oldDisposition = 0.75,
        )
        assertEquals(listOf(RivenGrade.A_MINUS, RivenGrade.B_PLUS, RivenGrade.B, RivenGrade.C), rolls.stats.map { it?.grade })
        assertEquals(-7.4, rolls.stats[3]!!.deviation, 0.1)
        assertEquals(0.75, rolls.oldDisposition!!, 0.0)
        assertEquals(1.6, rolls.nowFactor!!, 1e-9)
    }

    @Test
    fun readsMaxedStatsListedAtRankZero() {
        val rolls = grades(RivenClass.RIFLE, 0, RivenStat("critical_chance", 222.0, true), RivenStat("multishot", 134.0, true), RivenStat("zoom", -36.0, false))
        assertEquals(8, rolls.rank)
        assertTrue(rolls.stats.all { it?.grade == RivenGrade.B })
    }

    @Test
    fun keepsTheListedRankWhenItFits() {
        // Two buffs alone roll 178.2% crit chance and 118.8% multishot at rank 8, and a ninth of that at rank 0.
        val rolls = grades(RivenClass.RIFLE, 0, RivenStat("critical_chance", 19.8, true), RivenStat("multishot", 11.9, true))
        assertEquals(0, rolls.rank)
        assertTrue(rolls.stats.all { it?.grade != null })
    }

    @Test
    fun leavesRoundedAndImpossibleValuesUngraded() {
        val rolls = grades(RivenClass.PISTOL, 0, RivenStat("damage_vs_corpus", 1.03, true), RivenStat("critical_chance", 400.0, true))
        val (faction, crit) = rolls.stats
        assertTrue(faction!!.tooRounded)
        assertNull(faction.grade)
        assertTrue(crit!!.outOfRange)
        assertNull(crit.grade)
    }

    @Test
    fun signsRangesTheWayTheGameShowsThem() {
        val rolls = grades(RivenClass.RIFLE, 8, RivenStat("recoil", -90.0, true), RivenStat("critical_chance", 150.0, true), RivenStat("damage_vs_grineer", 0.7, false))
        val (recoil, _, faction) = rolls.stats
        assertTrue(recoil!!.max < 0)
        assertTrue(faction!!.max < 1 && faction.min > 0)
    }

    @Test
    fun onlyGradesLayoutsTheGameRolls() {
        assertNull(RivenGrading.grade(RivenClass.RIFLE, listOf(RivenStat("critical_chance", 100.0, true)), 8))
        assertNull(RivenGrading.grade(RivenClass.MELEE, listOf(RivenStat("range", 1.0, true), RivenStat("critical_chance", 100.0, false)), 8))
    }

    @Test
    fun gradeBandsAreTwoPointsWide() {
        assertEquals(RivenGrade.S, RivenGrade.of(9.5))
        assertEquals(RivenGrade.A_PLUS, RivenGrade.of(9.49))
        assertEquals(RivenGrade.B, RivenGrade.of(-1.5))
        assertEquals(RivenGrade.B_MINUS, RivenGrade.of(-1.51))
        assertEquals(RivenGrade.F, RivenGrade.of(-11.0))
    }
}
