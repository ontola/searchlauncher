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
}
