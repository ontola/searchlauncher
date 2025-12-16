package com.searchlauncher.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactUtilsTest {
  @Test
  fun getIndexableVariants_generatesDutchVariant() {
    val variants = ContactUtils.getIndexableVariants("+31 6 1234 5678")
    // Should contain "31612345678" AND "0612345678"
    assertEquals(2, variants.size)
    assertEquals("31612345678", variants[0])
    assertEquals("0612345678", variants[1])
  }

  @Test
  fun getIndexableVariants_handlesStandardNumber() {
    val variants = ContactUtils.getIndexableVariants("06-1234-5678")
    assertEquals(1, variants.size)
    assertEquals("0612345678", variants[0])
  }

  @Test
  fun normalizePhoneNumber_stripsNonDigits() {
    assertEquals("31612345678", ContactUtils.normalizePhoneNumber("+31 6 1234 5678"))
    assertEquals("0612345678", ContactUtils.normalizePhoneNumber("06-1234-5678"))
    assertEquals("1234", ContactUtils.normalizePhoneNumber("(12) 34"))
  }

  @Test
  fun normalizePhoneNumber_returnsNullIfAlreadyNormalized() {
    assertNull(ContactUtils.normalizePhoneNumber("0612345678"))
    assertNull(ContactUtils.normalizePhoneNumber("1234"))
  }

  @Test
  fun normalizePhoneNumber_returnsNullForInvalidInput() {
    assertNull(ContactUtils.normalizePhoneNumber(null))
    assertNull(ContactUtils.normalizePhoneNumber(""))
    assertNull(ContactUtils.normalizePhoneNumber("ABC")) // No digits
    assertNull(ContactUtils.normalizePhoneNumber("  "))
  }
}
