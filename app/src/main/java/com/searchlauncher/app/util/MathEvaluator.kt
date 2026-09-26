package com.searchlauncher.app.util

import java.util.*
import kotlin.math.*

object MathEvaluator {
  /**
   * Keys from the home keyboard's symbol pages. × and ÷ match * and /, √ is square root, π is the
   * circle constant, and ° marks a number of degrees (180° - 45° is 135).
   */
  private val BINARY_OPERATORS = setOf('+', '-', '*', '/', '^', '%', '×', '÷')

  private val PREFIX_OPERATORS = setOf('√')

  private val CONSTANTS = setOf('π')

  /** Degree sign annotates the number already typed; the arithmetic uses that number. */
  private val DEGREE = '°'

  private val OPERATORS = BINARY_OPERATORS + PREFIX_OPERATORS + DEGREE

  /** Operators that never turn up in a phone number, so a query using one can only be a sum. */
  private val ARITHMETIC_ONLY = setOf('*', '/', '^', '%', '(', ')', '×', '÷', '√', 'π', '°')

  private val PRECEDENCE =
    mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2, "%" to 2, "^" to 3, "√" to 4, "neg" to 4)

  private val PREFIX_TOKENS = setOf("√", "neg")

  fun isExpression(input: String): Boolean {
    if (input.isBlank()) return false
    // If it starts with +, it's likely a phone number, not a math expression (user request)
    if (input.trimStart().startsWith("+")) return false
    // A digit plus an operator, or π on its own (2π, √π, π).
    val hasOperator = input.any { it in OPERATORS }
    val hasDigit = input.any { it.isDigit() }
    val hasConstant = input.any { it in CONSTANTS }
    val allValid =
      input.all {
        it.isDigit() ||
          it in OPERATORS ||
          it in CONSTANTS ||
          it == '.' ||
          it == '(' ||
          it == ')' ||
          it.isWhitespace()
      }
    return allValid && (hasConstant || (hasOperator && hasDigit))
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
      val result = computeRPN(rpn)
      if (result.isNaN()) null else result
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
        c.isWhitespace() || c == DEGREE -> i++
        c == '-' && atUnaryPosition(tokens) -> {
          var j = i + 1
          while (j < expr.length && (expr[j].isWhitespace() || expr[j] == DEGREE)) j++
          if (j < expr.length && (expr[j].isDigit() || expr[j] == '.')) {
            val sb = StringBuilder("-")
            i = j
            while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
              sb.append(expr[i++])
            }
            tokens.add(sb.toString())
          } else {
            tokens.add("neg")
            i++
          }
        }
        c.isDigit() || c == '.' -> {
          val sb = StringBuilder()
          while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
            sb.append(expr[i++])
          }
          tokens.add(sb.toString())
        }
        c in CONSTANTS -> {
          tokens.add(c.toString())
          i++
        }
        c in BINARY_OPERATORS || c in PREFIX_OPERATORS || c == '(' || c == ')' -> {
          tokens.add(canonicalOperator(c))
          i++
        }
        else -> i++
      }
    }
    return insertImplicitMultiplication(tokens)
  }

  /** Minus is unary at the start and after another operator, so 5×-2 is five times negative two. */
  private fun atUnaryPosition(tokens: List<String>): Boolean {
    if (tokens.isEmpty()) return true
    val prev = tokens.last()
    return prev == "(" || prev in PRECEDENCE
  }

  private fun canonicalOperator(c: Char): String =
    when (c) {
      '×' -> "*"
      '÷' -> "/"
      else -> c.toString()
    }

  /**
   * 2π, 2√4, and 2(π+1) are products. A missing × is filled in only when π or √ is part of the sum,
   * so an expression that never used those keys is tokenized exactly as before.
   */
  private fun insertImplicitMultiplication(tokens: List<String>): List<String> {
    if (tokens.size < 2) return tokens
    val juxtapose = tokens.any { it == "π" || it == "√" }
    if (!juxtapose) return tokens
    val out = ArrayList<String>(tokens.size + 4)
    out.add(tokens[0])
    for (i in 1 until tokens.size) {
      val prev = out.last()
      val cur = tokens[i]
      if (isValueEnd(prev) && isValueStart(cur)) out.add("*")
      out.add(cur)
    }
    return out
  }

  private fun isNumberToken(token: String): Boolean {
    if (token.isEmpty()) return false
    val start = if (token[0] == '-') 1 else 0
    if (start >= token.length) return false
    return token[start].isDigit() || token[start] == '.'
  }

  private fun isValueEnd(token: String): Boolean =
    token == "π" || token == ")" || isNumberToken(token)

  private fun isValueStart(token: String): Boolean =
    token == "π" || token == "(" || token == "√" || isNumberToken(token)

  private fun toRPN(tokens: List<String>): List<String> {
    val output = mutableListOf<String>()
    val stack = Stack<String>()

    for (token in tokens) {
      when {
        isNumberToken(token) || token == "π" -> output.add(token)
        token == "(" -> stack.push(token)
        token == ")" -> {
          while (stack.isNotEmpty() && stack.peek() != "(") {
            output.add(stack.pop())
          }
          if (stack.isNotEmpty()) stack.pop()
        }
        else -> {
          val tokenPrec = PRECEDENCE[token] ?: 0
          while (stack.isNotEmpty() && stack.peek() != "(") {
            val stackPrec = PRECEDENCE[stack.peek()] ?: 0
            val shouldPop =
              if (token in PREFIX_TOKENS) stackPrec > tokenPrec else stackPrec >= tokenPrec
            if (!shouldPop) break
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
      when {
        token == "π" -> stack.push(PI)
        isNumberToken(token) -> stack.push(token.toDouble())
        token == "√" -> stack.push(sqrt(stack.pop()))
        token == "neg" -> stack.push(-stack.pop())
        else -> {
          val b = stack.pop()
          val a = if (stack.isNotEmpty()) stack.pop() else 0.0
          when (token) {
            "+" -> stack.push(a + b)
            "-" -> stack.push(a - b)
            "*" -> stack.push(a * b)
            "/" -> stack.push(a / b)
            "%" -> stack.push(a % b)
            "^" -> stack.push(a.pow(b))
            else -> throw IllegalArgumentException(token)
          }
        }
      }
    }
    return stack.pop()
  }
}
