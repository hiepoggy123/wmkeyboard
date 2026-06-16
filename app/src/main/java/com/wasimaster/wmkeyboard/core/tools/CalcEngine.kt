package com.wasimaster.wmkeyboard.core.tools

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/**
 * Expression evaluator for the calculator tool. Recursive descent over
 * `+ - × ÷ * / mod ^ ( )`, scientific functions, the constants π and e,
 * and implicit multiplication (`2π`, `3(4+1)`). Trig honors the tool's
 * degree/radian setting. Pure and synchronous — nothing leaves the device.
 */
object CalcEngine {

    class CalcException(message: String) : Exception(message)

    /**
     * Evaluates [expression]; throws [CalcException] with a short,
     * user-facing message when it can't. [degrees] applies to the trig
     * functions (inverse ones return in the same unit).
     */
    fun evaluate(expression: String, degrees: Boolean = true): Double {
        val parser = Parser(expression, degrees)
        val value = parser.parseExpression()
        parser.skipSpaces()
        if (!parser.atEnd) throw CalcException("Unexpected “${parser.rest().take(8)}”")
        if (value.isNaN()) throw CalcException("Undefined result")
        if (value.isInfinite()) throw CalcException("Result is too large")
        return value
    }

    /**
     * Result formatted for insertion: up to [precision] significant
     * decimals, trailing zeros stripped, integer values without a point.
     * Very large/small magnitudes fall back to scientific notation.
     */
    fun format(value: Double, precision: Int = 8): String {
        if (value == 0.0) return "0"
        val magnitude = abs(value)
        if (magnitude >= 1e15 || magnitude < 1e-9) {
            return String.format(java.util.Locale.US, "%.${precision.coerceIn(1, 12)}e", value)
                .replace(Regex("0+e"), "e")
                .replace(".e", "e")
                .replace("e+0", "e+").replace("e-0", "e-")
        }
        val rounded = String.format(java.util.Locale.US, "%.${precision.coerceIn(0, 12)}f", value)
        return rounded.trimEnd('0').trimEnd('.')
    }

    private class Parser(private val text: String, private val degrees: Boolean) {
        private var pos = 0

        val atEnd: Boolean get() = pos >= text.length
        fun rest(): String = text.substring(pos)

        fun skipSpaces() {
            while (pos < text.length && text[pos] == ' ') pos++
        }

        private fun peek(): Char? = text.getOrNull(pos)

        private fun accept(vararg chars: Char): Char? {
            skipSpaces()
            val c = peek() ?: return null
            return if (c in chars) { pos++; c } else null
        }

        // expression := term (('+' | '-') term)*
        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                val op = accept('+', '-', '−') ?: return value
                val rhs = parseTerm()
                value = if (op == '+') value + rhs else value - rhs
            }
        }

        // term := unary (('*' | '/' | '%' | juxtaposition) unary)*
        private fun parseTerm(): Double {
            var value = parseUnary()
            while (true) {
                skipSpaces()
                val c = peek() ?: return value
                when {
                    c == '*' || c == '×' || c == '·' -> { pos++; value *= parseUnary() }
                    c == '/' || c == '÷' -> {
                        pos++
                        val rhs = parseUnary()
                        if (rhs == 0.0) throw CalcException("Division by zero")
                        value /= rhs
                    }
                    c == '%' -> {
                        pos++
                        skipSpaces()
                        // Trailing % is "percent"; % followed by a value is modulo.
                        val next = peek()
                        if (next == null || next == ')' || next == '+' || next == '-' ||
                            next == '−' || next == '*' || next == '×' || next == '/' || next == '÷'
                        ) {
                            value /= 100.0
                        } else {
                            val rhs = parseUnary()
                            if (rhs == 0.0) throw CalcException("Division by zero")
                            value = value.mod(rhs)
                        }
                    }
                    text.startsWith("mod", pos) -> { pos += 3
                        val rhs = parseUnary()
                        if (rhs == 0.0) throw CalcException("Division by zero")
                        value = value.mod(rhs)
                    }
                    // Implicit multiplication: 2π, 2(3+4), (1+2)(3+4), 3√4.
                    c == '(' || c == '√' || c.isLetter() && !text.startsWith("mod", pos) ->
                        value *= parseUnary()
                    else -> return value
                }
            }
        }

        // unary := ('-' | '+')* power
        private fun parseUnary(): Double {
            val op = accept('-', '−', '+')
            return when (op) {
                '-', '−' -> -parseUnary()
                '+' -> parseUnary()
                else -> parsePower()
            }
        }

        // power := atom ('^' unary)?   (right-associative)
        private fun parsePower(): Double {
            val base = parseAtom()
            skipSpaces()
            if (peek() == '^') {
                pos++
                return base.pow(parseUnary())
            }
            return base
        }

        private fun parseAtom(): Double {
            skipSpaces()
            val c = peek() ?: throw CalcException("Expression is incomplete")
            return when {
                c == '(' -> {
                    pos++
                    val value = parseExpression()
                    if (accept(')') == null) throw CalcException("Missing “)”")
                    value
                }
                c == '√' -> { pos++; applyChecked("√", sqrt(parseUnary())) }
                c.isDigit() || c == '.' -> parseNumber()
                c.isLetter() || c == 'π' -> parseNameOrFunction()
                else -> throw CalcException("Unexpected “$c”")
            }
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            // Scientific literal: 1.2e-3 (only when digits follow the sign).
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                var probe = pos + 1
                if (probe < text.length && (text[probe] == '+' || text[probe] == '-')) probe++
                if (probe < text.length && text[probe].isDigit()) {
                    pos = probe
                    while (pos < text.length && text[pos].isDigit()) pos++
                }
            }
            return text.substring(start, pos).toDoubleOrNull()
                ?: throw CalcException("Bad number “${text.substring(start, pos)}”")
        }

        private fun parseNameOrFunction(): Double {
            if (peek() == 'π') { pos++; return Math.PI }
            val start = pos
            while (pos < text.length && text[pos].isLetter()) pos++
            val name = text.substring(start, pos).lowercase()
            when (name) {
                "pi" -> return Math.PI
                "e" -> return Math.E
            }
            skipSpaces()
            val arg = if (peek() == '(') {
                pos++
                val value = parseExpression()
                if (accept(')') == null) throw CalcException("Missing “)”")
                value
            } else {
                // sin 30, √-style tight binding for a parenless argument.
                parseUnary()
            }
            val toRad = if (degrees) Math.PI / 180 else 1.0
            val fromRad = if (degrees) 180 / Math.PI else 1.0
            val result = when (name) {
                "sin" -> sin(arg * toRad)
                "cos" -> cos(arg * toRad)
                "tan" -> tan(arg * toRad)
                "asin" -> asin(arg) * fromRad
                "acos" -> acos(arg) * fromRad
                "atan" -> atan(arg) * fromRad
                "sinh" -> sinh(arg)
                "cosh" -> cosh(arg)
                "tanh" -> tanh(arg)
                "ln" -> ln(arg)
                "log" -> log10(arg)
                "lg" -> log2(arg)
                "sqrt" -> sqrt(arg)
                "cbrt" -> cbrt(arg)
                "abs" -> abs(arg)
                "exp" -> exp(arg)
                "floor" -> floor(arg)
                "ceil" -> ceil(arg)
                "round" -> kotlin.math.round(arg)
                else -> throw CalcException("Unknown function “$name”")
            }
            return applyChecked(name, result)
        }

        private fun applyChecked(name: String, value: Double): Double {
            if (value.isNaN()) throw CalcException("$name is undefined there")
            return value
        }
    }
}
