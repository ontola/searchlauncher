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
}
