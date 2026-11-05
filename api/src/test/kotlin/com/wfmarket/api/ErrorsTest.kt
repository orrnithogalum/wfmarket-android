package com.wfmarket.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorsTest {
    private fun errors(text: String): Pair<List<String>, Map<String, String>> =
        parseErrors(Json.parseToJsonElement(text))

    @Test
    fun knownKeysReadAsTheSiteShowsThem() {
        assertEquals("Invalid password.", ErrorMessages.translate("app.account.password_invalid"))
        assertEquals("Field is required", ErrorMessages.translate("app.field.required"))
    }

    @Test
    fun unknownKeysAreSpelledOut() {
        assertEquals("Too many orders", ErrorMessages.translate("app.post_order.too_many_orders"))
        assertEquals("Item Not Found", ErrorMessages.translate("app.errors.itemNotFound"))
        assertEquals("plain text", ErrorMessages.translate("plain text"))
    }

    @Test
    fun readsTheV1StringAndListShapes() {
        assertEquals(listOf("Invalid password.") to emptyMap<String, String>(), errors(""""app.account.password_invalid""""))
        assertEquals(listOf("Invalid password.", "Field is required"), errors("""["app.account.password_invalid", "app.field.required"]""").first)
    }

    @Test
    fun readsTheV2RequestAndInputs() {
        val (messages, fields) = errors("""{"request": ["app.account.password_invalid"], "inputs": {"platinum": "app.field.required"}}""")
        assertEquals(listOf("Invalid password."), messages)
        assertEquals(mapOf("platinum" to "Platinum: Field is required"), fields)
    }

    @Test
    fun labelsGenericFieldErrorsWithTheirField() {
        val (_, fields) = errors("""{"item": {"modRank": ["app.field.invalid"]}}""")
        assertEquals("Mod Rank: Invalid value", fields["modRank"])
    }

    @Test
    fun leavesSpecificFieldErrorsAsTheyAre() {
        val (_, fields) = errors("""{"password": ["app.account.password_invalid"]}""")
        assertEquals("Invalid password.", fields["password"])
    }

    @Test
    fun aMissingEnvelopeHasNothingToSay() {
        assertEquals(emptyList<String>() to emptyMap<String, String>(), parseErrors(null as JsonElement?))
    }

    @Test
    fun theSummaryFallsBackToTheKindOfFailure() {
        assertTrue(WfmException(WfmErrorKind.NETWORK, emptyList()).summary.startsWith("Can't reach warframe.market"))
        assertEquals("Please sign in again.", WfmException(WfmErrorKind.UNAUTHORIZED, emptyList()).summary)
        assertEquals("Too many requests. Try again in a moment.", WfmException(WfmErrorKind.RATE_LIMITED, emptyList()).summary)
    }

    @Test
    fun theSummaryListsEachProblemOnce() {
        val error = WfmException(WfmErrorKind.VALIDATION, listOf("Invalid password."), mapOf("password" to "Invalid password.", "email" to "Taken"))
        assertEquals("Invalid password.\nTaken", error.summary)
        assertEquals("Invalid password.", error.message)
    }
}
