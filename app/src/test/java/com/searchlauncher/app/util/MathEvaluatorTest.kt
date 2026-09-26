package com.searchlauncher.app.util

import kotlin.math.sqrt
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

  @Test
  fun testKeyboardOperatorsAreExpressions() {
    assertTrue(MathEvaluator.isExpression("2×3"))
    assertTrue(MathEvaluator.isExpression("8÷2"))
    assertTrue(MathEvaluator.isExpression("√16"))
    assertTrue(MathEvaluator.isExpression("√(4+5)"))
    assertTrue(MathEvaluator.isExpression("π"))
    assertTrue(MathEvaluator.isExpression("2π"))
    assertTrue(MathEvaluator.isExpression("√π"))
    assertTrue(MathEvaluator.isExpression("180°-45°"))

    assertTrue(MathEvaluator.isUnambiguouslyArithmetic("2×3"))
    assertTrue(MathEvaluator.isUnambiguouslyArithmetic("8÷2"))
    assertTrue(MathEvaluator.isUnambiguouslyArithmetic("√16"))
    assertTrue(MathEvaluator.isUnambiguouslyArithmetic("2π"))
    assertTrue(MathEvaluator.isUnambiguouslyArithmetic("180°"))

    assertFalse(MathEvaluator.isExpression("√"))
    assertFalse(MathEvaluator.isExpression("×"))
    assertFalse(MathEvaluator.looksLikePhoneNumber("2×3"))
    assertFalse(MathEvaluator.looksLikePhoneNumber("√16"))
  }

  @Test
  fun testEvaluate() {
    assertEquals(2.0, MathEvaluator.evaluate("1+1")!!, 0.0)
    assertEquals(6.0, MathEvaluator.evaluate("2*3")!!, 0.0)
    assertEquals(25.0, MathEvaluator.evaluate("100/4")!!, 0.0)
    assertEquals(256.0, MathEvaluator.evaluate("2^8")!!, 0.0)
    assertEquals(20.0, MathEvaluator.evaluate("(2+3)*4")!!, 0.0)
    assertEquals(1.0, MathEvaluator.evaluate("10%3")!!, 0.0)
    assertEquals(-2.0, MathEvaluator.evaluate("-5+3")!!, 0.0)
    assertEquals(-10.0, MathEvaluator.evaluate("5*-2")!!, 0.0)
    assertEquals(-5.0, MathEvaluator.evaluate("-(2+3)")!!, 0.0)
    assertEquals(64.0, MathEvaluator.evaluate("2^3^2")!!, 0.0)
    assertEquals(50.0, MathEvaluator.evaluate("100-50")!!, 0.0)
    assertEquals(36.0, MathEvaluator.evaluate("2026-1990")!!, 0.0)

    assertEquals(6.0, MathEvaluator.evaluate("2×3")!!, 0.0)
    assertEquals(7.0, MathEvaluator.evaluate("2×3+1")!!, 0.0)
    assertEquals(4.0, MathEvaluator.evaluate("8÷2")!!, 0.0)
    assertEquals(2.5, MathEvaluator.evaluate("10÷2÷2")!!, 0.0)
    assertEquals(12.0, MathEvaluator.evaluate("8÷2×3")!!, 0.0)
    assertEquals(6.0, MathEvaluator.evaluate("2 × 3")!!, 0.0)

    assertEquals(3.0, MathEvaluator.evaluate("√9")!!, 0.0)
    assertEquals(4.0, MathEvaluator.evaluate("√16")!!, 0.0)
    assertEquals(3.0, MathEvaluator.evaluate("√(4+5)")!!, 0.0)
    assertEquals(10.0, MathEvaluator.evaluate("√9+7")!!, 0.0)
    assertEquals(6.0, MathEvaluator.evaluate("2*√9")!!, 0.0)
    assertEquals(6.0, MathEvaluator.evaluate("2√9")!!, 0.0)
    assertEquals(2.0, MathEvaluator.evaluate("√√16")!!, 0.0)
    assertEquals(1.5, MathEvaluator.evaluate("√2.25")!!, 1e-9)
    assertEquals(4.0, MathEvaluator.evaluate("2^√4")!!, 0.0)
    assertEquals(-3.0, MathEvaluator.evaluate("-√9")!!, 0.0)
    assertNull(MathEvaluator.evaluate("√-1"))
    assertNull(MathEvaluator.evaluate("√(-9)"))

    assertEquals(Math.PI, MathEvaluator.evaluate("π")!!, 0.0)
    assertEquals(2 * Math.PI, MathEvaluator.evaluate("2*π")!!, 0.0)
    assertEquals(2 * Math.PI, MathEvaluator.evaluate("2π")!!, 0.0)
    assertEquals(2 * Math.PI, MathEvaluator.evaluate("π*2")!!, 0.0)
    assertEquals(2 * Math.PI, MathEvaluator.evaluate("π2")!!, 0.0)
    assertEquals(5 * Math.PI, MathEvaluator.evaluate("(2+3)×π")!!, 1e-9)
    assertEquals(3 * Math.PI, MathEvaluator.evaluate("2π+π")!!, 1e-9)
    assertEquals(sqrt(Math.PI), MathEvaluator.evaluate("√π")!!, 1e-9)
    assertEquals(2 * (Math.PI + 1), MathEvaluator.evaluate("2(π+1)")!!, 1e-9)
    assertEquals(6 * Math.PI, MathEvaluator.evaluate("2(3)π")!!, 1e-9)
    assertEquals(2 * Math.PI, MathEvaluator.evaluate("(2)(π)")!!, 1e-9)

    assertEquals(135.0, MathEvaluator.evaluate("180°-45°")!!, 0.0)
    assertEquals(90.0, MathEvaluator.evaluate("360°/4")!!, 0.0)
    assertEquals(180.0, MathEvaluator.evaluate("180°")!!, 0.0)

    assertNull(MathEvaluator.evaluate("abc"))
    assertNull(MathEvaluator.evaluate(""))
    assertNull(MathEvaluator.evaluate("√"))
  }
}
