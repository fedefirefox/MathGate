package dev.mathgate

import java.util.Locale
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Numeric arithmetic answers without eval, reflection, or code execution. */
object AnswerParser {
    fun parse(raw: String): Double? {
        if (raw.length !in 1..128) return null
        return try {
            val p = Parser(raw.trim().replace(',', '.').lowercase(Locale.US))
            val value = p.expression()
            p.skipSpaces()
            if (p.hasMore() || !value.isFinite()) null else value
        } catch (_: Exception) { null }
    }

    private class Parser(private val input: String) {
        private var pos = 0
        private var calls = 0
        fun skipSpaces() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
        fun hasMore(): Boolean { skipSpaces(); return pos < input.length }
        private fun accept(c: Char): Boolean {
            skipSpaces()
            if (pos < input.length && input[pos] == c) { pos++; return true }
            return false
        }
        private fun guard() { require(++calls <= 256) }
        fun expression(): Double {
            guard()
            var v = term()
            while (true) {
                v = when {
                    accept('+') -> v + term()
                    accept('-') -> v - term()
                    else -> return v
                }
            }
        }
        private fun term(): Double {
            var v = unary()
            while (true) {
                v = when {
                    accept('*') -> v * unary()
                    accept('/') -> v / unary()
                    else -> return v
                }
            }
        }
        private fun unary(): Double = when {
            accept('-') -> -unary()
            accept('+') -> unary()
            else -> power()
        }
        private fun power(): Double {
            val base = primary()
            return if (accept('^')) base.pow(unary()) else base
        }
        private fun primary(): Double {
            guard()
            if (accept('(')) {
                val v = expression()
                require(accept(')'))
                return v
            }
            skipSpaces()
            val start = pos
            while (pos < input.length &&
                (input[pos].isDigit() || input[pos] == '.')) pos++
            if (pos > start) {
                val n = input.substring(start, pos).toDouble()
                require(n.isFinite())
                return n
            }
            while (pos < input.length && input[pos].isLetter()) pos++
            return when (input.substring(start, pos)) {
                "pi" -> Math.PI
                "e" -> Math.E
                "sqrt" -> function { sqrt(it) }
                "exp" -> function { exp(it) }
                "sin" -> function { sin(it) }
                "cos" -> function { cos(it) }
                "ln", "log" -> function { ln(it) }
                else -> throw IllegalArgumentException("Unknown expression")
            }
        }
        private fun function(op: (Double) -> Double): Double {
            require(accept('('))
            val x = expression()
            require(accept(')'))
            return op(x)
        }
    }
}
