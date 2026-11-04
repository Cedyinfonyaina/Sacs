package com.livesac.manager.domain

/** Petit lecteur/écrivain JSON sans dépendance (même code sur Android et dans les tests JVM). */
object MiniJson {

    fun write(value: Any?): String = StringBuilder().also { writeValue(it, value, 0) }.toString()

    private fun indent(sb: StringBuilder, level: Int) {
        sb.append('\n')
        repeat(level) { sb.append("  ") }
    }

    private fun writeValue(sb: StringBuilder, value: Any?, level: Int) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(value.toString())
            is Int, is Long -> sb.append(value.toString())
            is Map<*, *> -> {
                if (value.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    indent(sb, level + 1)
                    writeString(sb, k.toString())
                    sb.append(": ")
                    writeValue(sb, v, level + 1)
                }
                indent(sb, level)
                sb.append('}')
            }
            is List<*> -> {
                if (value.isEmpty()) { sb.append("[]"); return }
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    indent(sb, level + 1)
                    writeValue(sb, v, level + 1)
                }
                indent(sb, level)
                sb.append(']')
            }
            else -> throw IllegalArgumentException("Type non géré : ${value::class.java.name}")
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append("\\u").append(String.format("%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    /** Renvoie Map<String, Any?>, List<Any?>, String, Long, Double, Boolean ou null. */
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (!p.atEnd()) p.fail("texte inattendu après la fin")
        return v
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length
        fun fail(msg: String): Nothing = throw IllegalArgumentException("JSON invalide (position $i) : $msg")

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
            // BOM éventuel
            if (i == 0 && s.isNotEmpty() && s[0] == '\uFEFF') { i++; skipWs() }
        }

        fun value(): Any? {
            if (atEnd()) fail("fin inattendue")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("caractère '$c' inattendu")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("mot inconnu")
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return map }
            while (true) {
                skipWs()
                if (atEnd() || s[i] != '"') fail("clé attendue")
                val k = str()
                skipWs()
                if (atEnd() || s[i] != ':') fail("':' attendu")
                i++
                skipWs()
                map[k] = value()
                skipWs()
                if (atEnd()) fail("fin inattendue")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return map }
                fail("',' ou '}' attendu")
            }
        }

        private fun arr(): List<Any?> {
            val list = ArrayList<Any?>()
            i++
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return list }
            while (true) {
                skipWs()
                list.add(value())
                skipWs()
                if (atEnd()) fail("fin inattendue")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return list }
                fail("',' ou ']' attendu")
            }
        }

        private fun str(): String {
            val sb = StringBuilder()
            i++
            while (true) {
                if (atEnd()) fail("texte non terminé")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) fail("échappement incomplet")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("\\u incomplet")
                                val hex = s.substring(i, i + 4)
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: fail("\\u invalide"))
                                i += 4
                            }
                            else -> fail("échappement '\\$e' inconnu")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(start, i)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: fail("nombre invalide '$t'")
        }
    }
}
