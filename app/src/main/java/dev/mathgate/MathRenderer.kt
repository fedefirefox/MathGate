package dev.mathgate

import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import android.widget.LinearLayout

/**
 * Offline TeX subset -> MathML Core. No network, JavaScript, scripts, or HTML
 * from the question library. MathML is rendered by Android System WebView.
 */
object MathRenderer {
    fun create(context: Context, latex: String): WebView {
        val web = WebView(context)
        web.settings.javaScriptEnabled = false
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.blockNetworkLoads = true
        web.setBackgroundColor(Color.WHITE)
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.layoutParams = LinearLayout.LayoutParams(
            -1, GateUi.dp(context, 220)
        )
        val math = Parser(latex).render()
        val page = "<!doctype html><html><head><meta name=\"viewport\" " +
            "content=\"width=device-width,initial-scale=1,minimum-scale=1\" />" +
            "<style>html,body{margin:0;padding:0;background:#fff;color:#102A2E}" +
            ".formula{margin:3px 0;padding:8px 4px;max-width:100%;" +
            "overflow-x:auto;overflow-y:hidden;}" +
            "math{font-family:serif;font-size:25px;line-height:1.6;}" +
            "</style></head><body><div class=\"formula\">" + math +
            "</div></body></html>"
        web.loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
        return web
    }

    private class Parser(private val source: String) {
        private var position = 0

        fun render(): String =
            "<math xmlns=\"http://www.w3.org/1998/Math/MathML\" display=\"block\">" +
                sequence() + "</math>"

        private fun escape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

        private fun tag(name: String, body: String) = "<" + name + ">" + body + "</" + name + ">"
        private fun mi(value: String) = tag("mi", escape(value))
        private fun mn(value: String) = tag("mn", escape(value))
        private fun mo(value: String) = tag("mo", escape(value))

        private fun sequence(until: Char? = null): String {
            val result = StringBuilder()
            while (position < source.length && (until == null || source[position] != until)) {
                if (source[position].isWhitespace()) {
                    position++
                    continue
                }
                val base = atom()
                if (base.isEmpty()) continue
                var low: String? = null
                var high: String? = null
                while (position < source.length &&
                    (source[position] == '_' || source[position] == '^')) {
                    val op = source[position++]
                    val script = argument()
                    if (op == '_') low = script else high = script
                }
                result.append(
                    when {
                        low != null && high != null ->
                            tag("msubsup", base + low + high)
                        low != null -> tag("msub", base + low)
                        high != null -> tag("msup", base + high)
                        else -> base
                    }
                )
            }
            if (until != null && position < source.length && source[position] == until) position++
            return result.toString()
        }

        private fun argument(): String {
            while (position < source.length && source[position].isWhitespace()) position++
            if (position >= source.length) return tag("mrow", "")
            if (source[position] == '{') {
                position++
                return tag("mrow", sequence('}'))
            }
            return atom()
        }

        private fun atom(): String {
            if (position >= source.length) return ""
            val c = source[position++]
            return when {
                c == '{' -> tag("mrow", sequence('}'))
                c == '}' -> ""
                c == '\\' -> command()
                c.isDigit() -> {
                    val start = position - 1
                    while (position < source.length &&
                        (source[position].isDigit() || source[position] == '.')) position++
                    mn(source.substring(start, position))
                }
                c.isLetter() -> mi(c.toString())
                c == '\'' -> mo("′")
                c == '−' -> mo("−")
                c == '~' -> tag("mspace width=\"0.4em\"", "")
                else -> mo(c.toString())
            }
        }

        private fun rawBraced(): String {
            while (position < source.length && source[position].isWhitespace()) position++
            if (position >= source.length || source[position] != '{') return ""
            val start = ++position
            var depth = 1
            while (position < source.length && depth > 0) {
                when (source[position]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                position++
            }
            return source.substring(start, (position - 1).coerceAtLeast(start))
        }

        private fun command(): String {
            if (position >= source.length) return mo("\\")
            if (!source[position].isLetter()) {
                val single = source[position++]
                return when (single) {
                    ',', ';', ':', '!' -> "<mspace width=\"0.22em\" />"
                    '\\' -> "<mspace width=\"0.35em\" />"
                    else -> mo(single.toString())
                }
            }
            val start = position
            while (position < source.length && source[position].isLetter()) position++
            val name = source.substring(start, position)
            return when (name) {
                "frac", "dfrac", "tfrac" -> {
                    val numerator = argument()
                    val denominator = argument()
                    tag("mfrac", numerator + denominator)
                }
                "sqrt" -> tag("msqrt", argument())
                "left", "right" -> {
                    if (position >= source.length) ""
                    else {
                        val delimiter = source[position++]
                        if (delimiter == '.') "" else mo(delimiter.toString())
                    }
                }
                "vec" -> tag("mover", argument() + mo("→"))
                "hat" -> tag("mover", argument() + mo("^"))
                "bar", "overline" -> tag("mover", argument() + mo("¯"))
                "text", "mathrm" -> tag("mtext", escape(rawBraced()))
                "operatorname" -> "<mi mathvariant=\"normal\">" +
                    escape(rawBraced()) + "</mi>"
                "mathbb" -> "<mi mathvariant=\"double-struck\">" +
                    escape(rawBraced()) + "</mi>"
                "mathbf" -> "<mi mathvariant=\"bold\">" +
                    escape(rawBraced()) + "</mi>"
                "sin", "cos", "tan", "exp", "ln", "log", "lim", "min", "max" ->
                    "<mi mathvariant=\"normal\">" + escape(name) + "</mi>"
                "pi" -> mi("π")
                "theta" -> mi("θ")
                "alpha" -> mi("α")
                "beta" -> mi("β")
                "gamma" -> mi("γ")
                "Gamma" -> mi("Γ")
                "lambda" -> mi("λ")
                "omega" -> mi("ω")
                "Omega" -> mi("Ω")
                "Delta" -> mi("Δ")
                "xi" -> mi("ξ")
                "infty" -> mo("∞")
                "partial" -> mo("∂")
                "nabla" -> mo("∇")
                "cdot" -> mo("·")
                "times" -> mo("×")
                "le", "leq" -> mo("≤")
                "ge", "geq" -> mo("≥")
                "ne" -> mo("≠")
                "approx" -> mo("≈")
                "in" -> mo("∈")
                "to", "rightarrow" -> mo("→")
                "sum" -> mo("∑")
                "prod" -> mo("∏")
                "int" -> mo("∫")
                "iint" -> mo("∬")
                "iiint" -> mo("∭")
                "oint" -> mo("∮")
                "quad", "qquad" -> "<mspace width=\"1em\" />"
                else -> tag("mtext", escape("\\" + name))
            }
        }
    }
}
