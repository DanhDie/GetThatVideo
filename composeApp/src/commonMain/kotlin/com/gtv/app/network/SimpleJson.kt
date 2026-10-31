package com.gtv.app.network

/**
 * Utilitário leve e didático para serialização e parsing de JSON em Kotlin Multiplatform (`commonMain`).
 *
 * Por que ter este parser no projeto de estudo?
 * - Funciona em todas as plataformas (Desktop JVM, Android, iOS) sem depender de plugins extras.
 * - Permite estudar como um parser recursivo descendente lê objetos `{...}` e listas `[...]` em JSON.
 */
object SimpleJson {

    /**
     * Converte um mapa simples `Map<String, String>` em uma string JSON válida.
     */
    fun encodeMap(map: Map<String, String>): String {
        val entries = map.entries.joinToString(", ") { (k, v) ->
            "\"${escapeString(k)}\": \"${escapeString(v)}\""
        }
        return "{$entries}"
    }

    /**
     * Escapa caracteres especiais para formato JSON seguro.
     */
    fun escapeString(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Faz o parse de um texto JSON cujo elemento raiz é um objeto `{ ... }`.
     */
    @Suppress("UNCHECKED_CAST")
    fun parseObject(jsonText: String): Map<String, Any?> {
        val parser = Parser(jsonText.trim())
        val value = parser.parseValue()
        return (value as? Map<String, Any?>) ?: emptyMap()
    }

    private class Parser(private val input: String) {
        private var pos: Int = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= input.length) return null
            return when (val ch = input[pos]) {
                '{' -> parseObjectInternal()
                '[' -> parseArrayInternal()
                '"' -> parseStringInternal()
                't', 'f' -> parseBooleanInternal()
                'n' -> parseNullInternal()
                else -> if (ch == '-' || ch.isDigit()) parseNumberInternal() else null
            }
        }

        private fun parseObjectInternal(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // consome '{'
            skipWhitespace()
            if (pos < input.length && input[pos] == '}') {
                pos++
                return map
            }
            while (pos < input.length) {
                skipWhitespace()
                if (pos >= input.length || input[pos] != '"') break
                val key = parseStringInternal()
                skipWhitespace()
                if (pos < input.length && input[pos] == ':') pos++
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos < input.length && input[pos] == ',') {
                    pos++
                } else if (pos < input.length && input[pos] == '}') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return map
        }

        private fun parseArrayInternal(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // consome '['
            skipWhitespace()
            if (pos < input.length && input[pos] == ']') {
                pos++
                return list
            }
            while (pos < input.length) {
                list.add(parseValue())
                skipWhitespace()
                if (pos < input.length && input[pos] == ',') {
                    pos++
                } else if (pos < input.length && input[pos] == ']') {
                    pos++
                    break
                } else {
                    break
                }
            }
            return list
        }

        private fun parseStringInternal(): String {
            pos++ // consome '"'
            val sb = StringBuilder()
            while (pos < input.length) {
                val ch = input[pos++]
                if (ch == '"') {
                    return sb.toString()
                } else if (ch == '\\' && pos < input.length) {
                    when (val esc = input[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 <= input.length) {
                                val hex = input.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16)
                                if (code != null) sb.append(code.toChar())
                                pos += 4
                            }
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(ch)
                }
            }
            return sb.toString()
        }

        private fun parseBooleanInternal(): Boolean {
            return if (input.startsWith("true", pos)) {
                pos += 4
                true
            } else {
                pos = (pos + 5).coerceAtMost(input.length)
                false
            }
        }

        private fun parseNullInternal(): Any? {
            pos = (pos + 4).coerceAtMost(input.length)
            return null
        }

        private fun parseNumberInternal(): Number {
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] in "-+.eE")) {
                pos++
            }
            val raw = input.substring(start, pos)
            return if (raw.contains('.') || raw.contains('e', ignoreCase = true)) {
                raw.toDoubleOrNull() ?: 0.0
            } else {
                raw.toLongOrNull() ?: 0L
            }
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
