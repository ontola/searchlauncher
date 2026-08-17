package com.searchlauncher.app.util

import org.junit.Assert.*
import org.junit.Test

class MathEvaluatorTest {
  @Test
  fun testIsExpression() {
    assertTrue(MathEvaluator.isExpression("1+1"))
    assertTrue(MathEvaluator.isExpression("2*3"))
    assertFalse(MathEvaluator.isExpression("+331"))
    assertFalse(MathEvaluator.isExpression(" +331"))
    assertFalse(MathEvaluator.isExpression("abc"))
    assertFalse(MathEvaluator.isExpression("123"))
  }

  @Test
  fun testIsUnambiguouslyArithmetic() {
    assertTrue(MathEvaluator.isUnambiguouslyArithmetic("1234*56"))
    assertTrue(MathEvaluator.isUnambiguouslyArithmetic("100/4"))
    assertTrue(MathEvaluator.isUnambiguouslyArithmetic("(2+3)*4"))
    assertTrue(MathEvaluator.isUnambiguouslyArithmetic("2^8"))

    // Phone numbers get typed with dashes and spaces, and the contact matching them matters more
    // than the subtraction they happen to spell.
    assertFalse(MathEvaluator.isUnambiguouslyArithmetic("06-12345678"))
    assertFalse(MathEvaluator.isUnambiguouslyArithmetic("020 123 4567"))
    assertFalse(MathEvaluator.isUnambiguouslyArithmetic("1+1"))

    assertFalse(MathEvaluator.isUnambiguouslyArithmetic("abc"))
    assertFalse(MathEvaluator.isUnambiguouslyArithmetic("123"))
  }

  @Test
  fun testLooksLikePhoneNumber() {
    // A leading zero is not how sums get written, and nine digits is long for operands.
    assertTrue(MathEvaluator.looksLikePhoneNumber("06-12345678"))
    assertTrue(MathEvaluator.looksLikePhoneNumber("020 123 4567"))
    assertTrue(MathEvaluator.looksLikePhoneNumber("0612345678"))
    assertTrue(MathEvaluator.looksLikePhoneNumber("555-123-4567"))

    // Real sums keep their answer.
    assertFalse(MathEvaluator.looksLikePhoneNumber("100-50"))
    assertFalse(MathEvaluator.looksLikePhoneNumber("2026-1990"))
    assertFalse(MathEvaluator.looksLikePhoneNumber("1+1"))
    assertFalse(MathEvaluator.looksLikePhoneNumber("1234*56"))
    assertFalse(MathEvaluator.looksLikePhoneNumber("(2+3)*4"))

    assertFalse(MathEvaluator.looksLikePhoneNumber("abc"))
    assertFalse(MathEvaluator.looksLikePhoneNumber(""))
  }
}
