package com.devekene.konvertibles.data.converter

sealed interface DocBlock {
    data class Heading(val level: Int, val text: String) : DocBlock
    data class Paragraph(val text: String, val isBold: Boolean = false, val isItalic: Boolean = false) : DocBlock
    data class BulletItem(val text: String) : DocBlock
    data class Table(val rows: List<List<String>>) : DocBlock
}

data class DocxDocument(
    val title: String,
    val blocks: List<DocBlock>
)

data class SheetData(
    val name: String,
    val rows: List<List<String>>
)

data class SpreadsheetData(
    val sheets: List<SheetData>
)

object TextFormatConverter {
    fun fromPlainText(title: String, raw: String): DocxDocument {
        val lines = raw.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<DocBlock>()
        val paraBuffer = mutableListOf<String>()

        fun flushPara() {
            if (paraBuffer.isNotEmpty()) {
                blocks += DocBlock.Paragraph(paraBuffer.joinToString(" ").trim())
                paraBuffer.clear()
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flushPara()
                trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flushPara()
                    blocks += DocBlock.BulletItem(trimmed.drop(2).trim())
                }
                else -> paraBuffer += trimmed
            }
        }
        flushPara()
        if (blocks.isEmpty()) {
            blocks += DocBlock.Paragraph("")
        }
        return DocxDocument(title, blocks)
    }

    fun fromMarkdown(title: String, raw: String): DocxDocument {
        val lines = raw.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<DocBlock>()
        val paraBuffer = mutableListOf<String>()

        fun flushPara() {
            if (paraBuffer.isNotEmpty()) {
                blocks += DocBlock.Paragraph(stripMdInline(paraBuffer.joinToString(" ").trim()))
                paraBuffer.clear()
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flushPara()
                trimmed.startsWith("### ") -> {
                    flushPara()
                    blocks += DocBlock.Heading(3, stripMdInline(trimmed.removePrefix("### ").trim()))
                }
                trimmed.startsWith("## ") -> {
                    flushPara()
                    blocks += DocBlock.Heading(2, stripMdInline(trimmed.removePrefix("## ").trim()))
                }
                trimmed.startsWith("# ") -> {
                    flushPara()
                    blocks += DocBlock.Heading(1, stripMdInline(trimmed.removePrefix("# ").trim()))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flushPara()
                    blocks += DocBlock.BulletItem(stripMdInline(trimmed.drop(2).trim()))
                }
                else -> paraBuffer += trimmed
            }
        }
        flushPara()
        if (blocks.isEmpty()) blocks += DocBlock.Paragraph("")
        return DocxDocument(title, blocks)
    }

    fun fromHtml(title: String, raw: String): DocxDocument {
        val body = raw
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?i)<h1[^>]*>(.*?)</h1>"), "\n# $1\n")
            .replace(Regex("(?i)<h2[^>]*>(.*?)</h2>"), "\n## $1\n")
            .replace(Regex("(?i)<h3[^>]*>(.*?)</h3>"), "\n### $1\n")
            .replace(Regex("(?i)<li[^>]*>(.*?)</li>"), "\n- $1\n")
            .replace(Regex("(?i)<(p|div|tr|br)[^>]*/?>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .let { decodeHtmlEntities(it) }
        return fromMarkdown(title, body)
    }

    fun toPlainText(doc: DocxDocument): String = buildString {
        doc.blocks.forEachIndexed { idx, block ->
            if (idx > 0) append("\n\n")
            when (block) {
                is DocBlock.Heading -> append(block.text)
                is DocBlock.Paragraph -> append(block.text)
                is DocBlock.BulletItem -> append("• ${block.text}")
                is DocBlock.Table -> {
                    block.rows.forEach { row ->
                        append(row.joinToString("\t"))
                        append("\n")
                    }
                }
            }
        }
    }

    fun toMarkdown(doc: DocxDocument): String = buildString {
        doc.blocks.forEachIndexed { idx, block ->
            if (idx > 0) append("\n\n")
            when (block) {
                is DocBlock.Heading -> {
                    val hashes = "#".repeat(block.level.coerceIn(1, 3))
                    append("$hashes ${block.text}")
                }
                is DocBlock.Paragraph -> {
                    val text = when {
                        block.isBold -> "**${block.text}**"
                        block.isItalic -> "*${block.text}*"
                        else -> block.text
                    }
                    append(text)
                }
                is DocBlock.BulletItem -> append("- ${block.text}")
                is DocBlock.Table -> {
                    if (block.rows.isNotEmpty()) {
                        val header = block.rows.first()
                        append("| ${header.joinToString(" | ")} |\n")
                        append("| ${header.joinToString(" | ") { "---" }} |\n")
                        block.rows.drop(1).forEach { row ->
                            append("| ${row.joinToString(" | ")} |\n")
                        }
                    }
                }
            }
        }
    }

    fun toHtml(doc: DocxDocument): String = buildString {
        append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\"/>\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>\n")
        append("<title>${escapeHtml(doc.title)}</title>\n")
        append("<style>body{font-family:-apple-system,sans-serif;max-width:780px;margin:32px auto;padding:0 20px;line-height:1.6;color:#141619;}h1,h2,h3{color:#0A2472;}table{border-collapse:collapse;width:100%;margin:16px 0;}th,td{border:1px solid #D5D2C8;padding:8px 10px;text-align:left;}th{background:#EEF2FF;}</style>\n")
        append("</head>\n<body>\n")
        var inList = false
        for (block in doc.blocks) {
            if (block !is DocBlock.BulletItem && inList) {
                append("</ul>\n")
                inList = false
            }
            when (block) {
                is DocBlock.Heading -> {
                    val lvl = block.level.coerceIn(1, 3)
                    append("<h$lvl>${escapeHtml(block.text)}</h$lvl>\n")
                }
                is DocBlock.Paragraph -> {
                    val content = escapeHtml(block.text)
                    val styled = when {
                        block.isBold -> "<strong>$content</strong>"
                        block.isItalic -> "<em>$content</em>"
                        else -> content
                    }
                    append("<p>$styled</p>\n")
                }
                is DocBlock.BulletItem -> {
                    if (!inList) {
                        append("<ul>\n")
                        inList = true
                    }
                    append("  <li>${escapeHtml(block.text)}</li>\n")
                }
                is DocBlock.Table -> {
                    append("<table>\n")
                    block.rows.forEachIndexed { rIdx, row ->
                        append("  <tr>")
                        val tag = if (rIdx == 0) "th" else "td"
                        row.forEach { cell ->
                            append("<$tag>${escapeHtml(cell)}</$tag>")
                        }
                        append("</tr>\n")
                    }
                    append("</table>\n")
                }
            }
        }
        if (inList) append("</ul>\n")
        append("</body>\n</html>")
    }

    private fun stripMdInline(s: String): String =
        s.replace("**", "").replace("__", "").replace("`", "")

    fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun decodeHtmlEntities(s: String): String =
        s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
}
