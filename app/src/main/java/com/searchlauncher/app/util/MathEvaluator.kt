package com.searchlauncher.app.util

import java.util.*
import kotlin.math.*

object MathEvaluator {
  private val OPERATORS = setOf('+', '-', '*', '/', '^', '%')

  /** Operators that never turn up in a phone number, so a query using one can only be a sum. */
  private val ARITHMETIC_ONLY = setOf('*', '/', '^', '%', '(', ')')

  fun isExpression(input: String): Boolean {
    if (input.isBlank()) return false
    // If it starts with +, it's likely a phone number, not a math expression (user request)
    if (input.trimStart().startsWith("+")) return false
    // Must contain at least one operator and some numbers
    val hasOperator = input.any { it in OPERATORS }
    val hasDigit = input.any { it.isDigit() }
    val allValid =
      input.all {
        it.isDigit() || it in OPERATORS || it == '.' || it == '(' || it == ')' || it.isWhitespace()
      }
    return hasOperator && hasDigit && allValid
  }

  /**
   * True when the query cannot be anything but arithmetic, so the caller can drop everything else
   * from the results.
   *
   * `+` and `-` are deliberately not enough on their own: `06-12345678` is how a phone number gets
   * typed, and hiding the contact it matches behind a subtraction would be worse than showing both.
   */
  fun isUnambiguouslyArithmetic(input: String): Boolean =
    isExpression(input) && input.any { it in ARITHMETIC_ONLY }

  /** Digits written with the separators people put in phone numbers, and nothing else. */
  private val PHONE_SHAPED = Regex("""[0-9][0-9\-. ]*[0-9]""")

  /**
   * True when a string that parses as arithmetic is far more likely to be a phone number, so the
   * caller can leave the calculator out of it.
   *
   * `06-12345678` is a subtraction to a parser and a mobile number to everyone else, and answering
   * it with -12345672 is noise sitting on top of the contact the user actually wanted. Two signals
   * separate the cases without spoiling real sums: a leading zero, which is not how anyone writes
   * arithmetic, and sheer length, since operands long enough to total nine digits are rare next to
   * numbers that are exactly that long. So `100-50` and `2026-1990` still get an answer.
   */
  fun looksLikePhoneNumber(input: String): Boolean {
    val trimmed = input.trim()
    if (!PHONE_SHAPED.matches(trimmed)) return false
    return trimmed.startsWith("0") || trimmed.count { it.isDigit() } >= 9
  }

  fun evaluate(expression: String): Double? {
    return try {
      val tokens = tokenize(expression)
      val rpn = toRPN(tokens)
      computeRPN(rpn)
    } catch (e: Exception) {
      null
    }
  }

  private fun tokenize(expr: String): List<String> {
    val tokens = mutableListOf<String>()
    var i = 0
    while (i < expr.length) {
      val c = expr[i]
      when {
        c.isWhitespace() -> i++
        c.isDigit() || c == '.' -> {
          val sb = StringBuilder()
          while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
            sb.append(expr[i++])
          }
          tokens.add(sb.toString())
        }
        c in OPERATORS || c == '(' || c == ')' -> {
          tokens.add(c.toString())
          i++
        }
        else -> i++
      }
    }
    return tokens
  }

  private fun toRPN(tokens: List<String>): List<String> {
    val output = mutableListOf<String>()
    val stack = Stack<String>()

    val precedence = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2, "%" to 2, "^" to 3)

    for (token in tokens) {
      when {
        token[0].isDigit() -> output.add(token)
        token == "(" -> stack.push(token)
        token == ")" -> {
          while (stack.isNotEmpty() && stack.peek() != "(") {
            output.add(stack.pop())
          }
          if (stack.isNotEmpty()) stack.pop()
        }
        else -> {
          while (
            stack.isNotEmpty() &&
              stack.peek() != "(" &&
              (precedence[stack.peek()] ?: 0) >= (precedence[token] ?: 0)
          ) {
            output.add(stack.pop())
          }
          stack.push(token)
        }
      }
    }

    while (stack.isNotEmpty()) {
      output.add(stack.pop())
    }

    return output
  }

  private fun computeRPN(rpn: List<String>): Double {
    val stack = Stack<Double>()
    for (token in rpn) {
      if (token[0].isDigit()) {
        stack.push(token.toDouble())
      } else {
        val b = stack.pop()
        val a =
          if (stack.isNotEmpty()) stack.pop()
          else 0.0 // Handle unary minus implicitly if needed, but this is simple
        when (token) {
          "+" -> stack.push(a + b)
          "-" -> stack.push(a - b)
          "*" -> stack.push(a * b)
          "/" -> stack.push(a / b)
          "%" -> stack.push(a % b)
          "^" -> stack.push(a.pow(b))
        }
      }
    }
    return stack.pop()
  }
}
