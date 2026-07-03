package com.nomemmurrakh.documents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KeysTest {

    @Test
    fun fieldComposesDocAndFieldWithSeparator() {
        assertEquals("user::name", Keys.field("user", "name"))
    }

    @Test
    fun prefixIsDocKeyFollowedBySeparator() {
        assertEquals("user::", Keys.prefix("user"))
    }

    @Test
    fun fieldNameRoundTripsFromComposedKey() {
        val key = Keys.field("user", "email")
        assertEquals("email", Keys.fieldName("user", key))
    }

    @Test
    fun roundTripPreservesFieldNameWithUnderscoresAndDigits() {
        val key = Keys.field("settings", "is_dark_mode2")
        assertEquals("is_dark_mode2", Keys.fieldName("settings", key))
    }

    @Test
    fun fieldRejectsSeparatorInDocumentKey() {
        val error = assertFailsWith<IllegalArgumentException> {
            Keys.field("user::admin", "name")
        }
        assertTrue(error.message!!.contains("::"))
        assertTrue(error.message!!.contains("user::admin"))
    }

    @Test
    fun prefixRejectsSeparatorInDocumentKey() {
        assertFailsWith<IllegalArgumentException> {
            Keys.prefix("a::b")
        }
    }

    @Test
    fun fieldNameRejectsKeyNotBelongingToDocument() {
        assertFailsWith<IllegalArgumentException> {
            Keys.fieldName("user", "post::title")
        }
    }
}
