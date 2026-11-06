package com.omniview.viewer.ui.viewer.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Lightweight regex-based syntax highlighter (keywords/strings/comments/numbers).
 * Not a full tokenizer, but covers the common languages the app supports and keeps
 * the APK free of a heavy third-party highlighting engine.
 */
object CodeHighlighter {

    private val keywordsByLang = mapOf(
        "py" to setOf("def", "class", "import", "from", "return", "if", "elif", "else", "for", "while", "try", "except", "with", "as", "lambda", "None", "True", "False", "self", "in", "not", "and", "or", "yield", "pass", "break", "continue"),
        "js" to setOf("function", "const", "let", "var", "return", "if", "else", "for", "while", "class", "extends", "import", "export", "from", "default", "new", "this", "typeof", "async", "await", "try", "catch", "null", "undefined", "true", "false"),
        "ts" to setOf("function", "const", "let", "var", "return", "if", "else", "for", "while", "class", "interface", "type", "extends", "implements", "import", "export", "from", "async", "await", "public", "private", "readonly"),
        "java" to setOf("public", "private", "protected", "class", "interface", "extends", "implements", "static", "final", "void", "return", "if", "else", "for", "while", "new", "import", "package", "try", "catch", "this", "null", "true", "false"),
        "kt" to setOf("fun", "val", "var", "class", "object", "interface", "return", "if", "else", "for", "while", "when", "import", "package", "try", "catch", "this", "null", "true", "false", "companion", "override", "private", "public"),
        "html" to setOf(),
        "css" to setOf(),
        "c" to setOf("int", "float", "double", "char", "void", "return", "if", "else", "for", "while", "struct", "include", "define"),
        "cpp" to setOf("int", "float", "double", "char", "void", "return", "if", "else", "for", "while", "class", "struct", "include", "namespace", "public", "private", "protected", "new", "delete", "this")
    )

    private val keywordColor = Color(0xFFC678DD)
    private val stringColor = Color(0xFF98C379)
    private val commentColor = Color(0xFF7F848E)
    private val numberColor = Color(0xFFD19A66)
    private val defaultColor = Color(0xFFABB2BF)

    fun highlight(code: String, extension: String): AnnotatedString {
        val keywords = keywordsByLang[extension.lowercase()] ?: emptySet()
        return buildAnnotatedString {
            var i = 0
            while (i < code.length) {
                val c = code[i]
                when {
                    // line comment
                    c == '/' && i + 1 < code.length && code[i + 1] == '/' -> {
                        val end = code.indexOf('\n', i).let { if (it == -1) code.length else it }
                        withStyle(SpanStyle(color = commentColor)) { append(code.substring(i, end)) }
                        i = end
                    }
                    c == '#' && extension.lowercase() == "py" -> {
                        val end = code.indexOf('\n', i).let { if (it == -1) code.length else it }
                        withStyle(SpanStyle(color = commentColor)) { append(code.substring(i, end)) }
                        i = end
                    }
                    c == '"' || c == '\'' -> {
                        val quote = c
                        var end = i + 1
                        while (end < code.length && code[end] != quote) end++
                        end = (end + 1).coerceAtMost(code.length)
                        withStyle(SpanStyle(color = stringColor)) { append(code.substring(i, end)) }
                        i = end
                    }
                    c.isDigit() -> {
                        var end = i
                        while (end < code.length && (code[end].isDigit() || code[end] == '.')) end++
                        withStyle(SpanStyle(color = numberColor)) { append(code.substring(i, end)) }
                        i = end
                    }
                    c.isLetter() || c == '_' -> {
                        var end = i
                        while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) end++
                        val word = code.substring(i, end)
                        if (word in keywords) {
                            withStyle(SpanStyle(color = keywordColor)) { append(word) }
                        } else {
                            withStyle(SpanStyle(color = defaultColor)) { append(word) }
                        }
                        i = end
                    }
                    else -> {
                        withStyle(SpanStyle(color = defaultColor)) { append(c) }
                        i++
                    }
                }
            }
        }
    }
}
