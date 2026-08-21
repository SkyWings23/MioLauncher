package com.miolauncher.app.data

import kotlin.math.pow

/**
 * FCL 控制布局的 dynamicX/dynamicY 表达式求值器。
 * 支持：四则运算、幂(^)、括号、abs()、px()、${screen_width/${screen_height}/${preferred_scale}/${margin}。
 */
object FclExpr {

    fun eval(
        expr: String,
        screenW: Float,
        screenH: Float,
        preferredScale: Float,
        margin: Float,
    ): Float {
        val tokens = tokenize(expr, screenW, screenH, preferredScale, margin) ?: return 0f
        val parser = Parser(tokens)
        return parser.parse()
    }

    private class Token(val type: Int, val num: Float, val ident: String) {
        companion object {
            const val NUM = 0
            const val IDENT = 1   // 函数名
            const val OP = 2
            const val LP = 3
            const val RP = 4
        }
    }

    private class Parser(private val tokens: List<Token>) {
        private var i = 0

        fun parse(): Float = expr()

        private fun expr(): Float {
            var v = term()
            while (i < tokens.size && tokens[i].type == Token.OP &&
                (tokens[i].ident == "+" || tokens[i].ident == "-")) {
                val op = tokens[i].ident
                i++
                val r = term()
                v = if (op == "+") v + r else v - r
            }
            return v
        }

        private fun term(): Float {
            var v = factor()
            while (i < tokens.size && tokens[i].type == Token.OP &&
                (tokens[i].ident == "*" || tokens[i].ident == "/")) {
                val op = tokens[i].ident
                i++
                val r = factor()
                v = if (op == "*") v * r else v / r
            }
            return v
        }

        private fun factor(): Float {
            if (i < tokens.size && tokens[i].type == Token.OP && tokens[i].ident == "-") {
                i++
                return -factor()
            }
            if (i < tokens.size && tokens[i].type == Token.OP && tokens[i].ident == "+") {
                i++
                return factor()
            }
            var v = primary()
            if (i < tokens.size && tokens[i].type == Token.OP && tokens[i].ident == "^") {
                i++
                val r = factor()
                v = v.toDouble().pow(r.toDouble()).toFloat()
            }
            return v
        }

        private fun primary(): Float {
            if (i >= tokens.size) return 0f
            val t = tokens[i]
            if (t.type == Token.NUM) {
                i++
                return t.num
            }
            if (t.type == Token.IDENT) {
                i++
                // 函数 abs( / px(
                if (i < tokens.size && tokens[i].type == Token.LP) {
                    i++
                    val arg = expr()
                    if (i < tokens.size && tokens[i].type == Token.RP) i++
                    return when (t.ident) {
                        "abs" -> kotlin.math.abs(arg)
                        else -> arg
                    }
                }
                return t.num // 已替换为数值的变量
            }
            if (t.type == Token.LP) {
                i++
                val v = expr()
                if (i < tokens.size && tokens[i].type == Token.RP) i++
                return v
            }
            return 0f
        }
    }

    private fun tokenize(
        expr: String,
        screenW: Float,
        screenH: Float,
        preferredScale: Float,
        margin: Float,
    ): List<Token>? {
        val vars = mapOf(
            "screen_width" to screenW,
            "screen_height" to screenH,
            "preferred_scale" to preferredScale,
            "margin" to margin,
        )
        val tokens = mutableListOf<Token>()
        var s = expr
        var idx = 0
        while (idx < s.length) {
            val c = s[idx]
            when {
                c.isWhitespace() -> idx++
                c == '(' -> { tokens.add(Token(Token.LP, 0f, "")); idx++ }
                c == ')' -> { tokens.add(Token(Token.RP, 0f, "")); idx++ }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' -> {
                    tokens.add(Token(Token.OP, 0f, c.toString())); idx++
                }
                c.isDigit() || c == '.' -> {
                    // 数字（含科学计数法 1.23e-5）
                    val sb = StringBuilder()
                    while (idx < s.length && (s[idx].isDigit() || s[idx] == '.')) {
                        sb.append(s[idx]); idx++
                    }
                    if (idx < s.length && (s[idx] == 'e' || s[idx] == 'E')) {
                        // 科学计数法：把 10^-13 处理为 pow 更稳妥，这里仅处理完整科学计数
                        val save = idx
                        val sb2 = StringBuilder(sb.toString())
                        sb2.append(s[idx]); idx++
                        if (idx < s.length && (s[idx] == '+' || s[idx] == '-')) { sb2.append(s[idx]); idx++ }
                        while (idx < s.length && s[idx].isDigit()) { sb2.append(s[idx]); idx++ }
                        val asStr = sb2.toString()
                        val v = asStr.toFloatOrNull()
                        if (v != null) {
                            tokens.add(Token(Token.NUM, v, ""))
                        } else {
                            idx = save
                            tokens.add(Token(Token.NUM, sb.toString().toFloatOrNull() ?: 0f, ""))
                        }
                    } else {
                        tokens.add(Token(Token.NUM, sb.toString().toFloatOrNull() ?: 0f, ""))
                    }
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    // 标识符：${xxx} 或 函数名
                    if (c == '$' || (c == '{')) {
                        // 处理 ${var}
                        val sb = StringBuilder()
                        var j = idx
                        if (s[j] == '$') { sb.append(s[j]); j++ }
                        if (j < s.length && s[j] == '{') { j++ } else return null
                        while (j < s.length && s[j] != '}') { sb.append(s[j]); j++ }
                        j++ // skip }
                        val name = sb.toString().removePrefix("$").removePrefix("{").removeSuffix("}")
                        tokens.add(Token(Token.NUM, vars[name] ?: 0f, ""))
                        idx = j
                    } else {
                        val sb = StringBuilder()
                        while (idx < s.length && (s[idx].isLetter() || s[idx] == '_')) {
                            sb.append(s[idx]); idx++
                        }
                        tokens.add(Token(Token.IDENT, 0f, sb.toString()))
                    }
                }
                else -> idx++
            }
        }
        return tokens
    }
}
