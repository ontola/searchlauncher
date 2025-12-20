package com.searchlauncher.app.util

import java.util.*
import kotlin.math.*

object MathEvaluator {
  private val OPERATORS = setOf('+', '-', '*', '/', '^', '%')

  fun isExpression(input: String): Boolean {
    if (input.isBlank()) return false
    // Must contain at least one operator and some numbers
    val hasOperator = input.any { it in OPERATORS }
    val hasDigit = input.any { it.isDigit() }
    val allValid =
      input.all {
        it.isDigit() || it in OPERATORS || it == '.' || it == '(' || it == ')' || it.isWhitespace()
      }
    return hasOperator && hasDigit && allValid
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
