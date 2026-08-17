package com.muhabbet.shared.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * One definition of "a two-step PIN", used by the field that accepts it and by the service that
 * stores it.
 *
 * They disagreed before #544: the Compose screen required six digits and the endpoint required
 * nothing at all, so the rule held exactly as long as the app was the only caller.
 */
class TwoStepPinRuleTest {

    @Test
    fun sixDigits_isAPin() {
        assertTrue(ValidationRules.isValidTwoStepPin("123456"))
        assertTrue(ValidationRules.isValidTwoStepPin("000000"))
    }

    @Test
    fun theWrongLength_isNot() {
        assertFalse(ValidationRules.isValidTwoStepPin(""))
        assertFalse(ValidationRules.isValidTwoStepPin("12345"))
        assertFalse(ValidationRules.isValidTwoStepPin("1234567"))
    }

    @Test
    fun anythingButDigits_isNot() {
        assertFalse(ValidationRules.isValidTwoStepPin("abcdef"))
        assertFalse(ValidationRules.isValidTwoStepPin("12345a"))
        assertFalse(ValidationRules.isValidTwoStepPin("12 456"))
        // Arabic-Indic digits. `Char.isDigit()` accepts these and this rule does not, which is why
        // the PIN field filters on `'0'..'9'` rather than `isDigit()` — a field that accepted a
        // character the endpoint rejects would fail at submit with no way to see why.
        assertFalse(ValidationRules.isValidTwoStepPin("١٢٣٤٥٦"))
    }

    @Test
    fun theLengthIsTheOneTheFieldLimitsTo() {
        assertEquals(6, ValidationRules.TWO_STEP_PIN_LENGTH)
    }
}
