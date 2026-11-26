package com.devekene.konvertibles.data.converter

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object OoxmlWordHandler {

    fun readDocx(file: File, fallbackTitle: String): DocxDocument {
        var documentXmlBytes: ByteArray? = null
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    documentXmlBytes = zis.readBytes()
                    break
                }
                entry = zis.nextEntry
            }
        }
        val xmlBytes = documentXmlBytes
            ?: return DocxDocument(fallbackTitle, listOf(DocBlock.Paragraph("Empty DOCX archive.")))

        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(xmlBytes.inputStream(), "UTF-8")
        }

        val blocks = mutableListOf<DocBlock>()
        var inTable = false
        val currentTableRows = mutableListOf<List<String>>()
        var currentRowCells = mutableListOf<String>()
        var inCell = false
        val cellBuffer = StringBuilder()

        var inParagraph = false
        val paraBuffer = StringBuilder()
        var pStyle = ""
        var isBullet = false
        var hasBold = false
        var hasItalic = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "tbl" -> {
                            inTable = true
                            currentTableRows.clear()
                        }
                        "tr" -> if (inTable) currentRowCells = mutableListOf()
                        "tc" -> if (inTable) {
                            inCell = true
                            cellBuffer.clear()
                        }
                        "p" -> if (!inTable) {
                            inParagraph = true
                            paraBuffer.clear()
                            pStyle = ""
                            isBullet = false
                            hasBold = false
                            hasItalic = false
                        }
                        "pStyle" -> if (inParagraph) {
                            pStyle = parser.getAttributeValue(null, "w:val")
                                ?: parser.getAttributeValue(null, "val") ?: ""
                        }
                        "numPr" -> if (inParagraph) isBullet = true
                        "b" -> if (inParagraph) hasBold = true
                        "i" -> if (inParagraph) hasItalic = true
                        "t" -> {
                            val text = parser.nextText()
                            if (inCell) {
                                if (cellBuffer.isNotEmpty() && !cellBuffer.endsWith(" ")) cellBuffer.append(" ")
                                cellBuffer.append(text)
                            } else if (inParagraph) {
                                paraBuffer.append(text)
                            }
                        }
                        "tab" -> if (inParagraph) paraBuffer.append("    ")
                        "br" -> if (inParagraph) paraBuffer.append("\n")
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "tc" -> if (inTable && inCell) {
                            currentRowCells.add(cellBuffer.toString().trim())
                            inCell = false
                        }
                        "tr" -> if (inTable) {
                            if (currentRowCells.isNotEmpty()) currentTableRows.add(currentRowCells.toList())
                        }
                        "tbl" -> {
                            if (currentTableRows.isNotEmpty()) {
                                blocks += DocBlock.Table(currentTableRows.toList())
                            }
                            inTable = false
                        }
                        "p" -> if (inParagraph && !inTable) {
                            val txt = paraBuffer.toString().trim()
                            if (txt.isNotEmpty()) {
                                val styleLower = pStyle.lowercase()
                                when {
                                    styleLower.contains("heading1") || styleLower == "title" || styleLower == "1" ->
                                        blocks += DocBlock.Heading(1, txt)
                                    styleLower.contains("heading2") || styleLower == "subtitle" || styleLower == "2" ->
                                        blocks += DocBlock.Heading(2, txt)
                                    styleLower.contains("heading3") || styleLower == "3" ->
                                        blocks += DocBlock.Heading(3, txt)
                                    isBullet ->
                                        blocks += DocBlock.BulletItem(txt)
                                    else ->
                                        blocks += DocBlock.Paragraph(txt, isBold = hasBold, isItalic = hasItalic)
                                }
                            }
                            inParagraph = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (blocks.isEmpty()) {
            blocks += DocBlock.Paragraph("No readable text blocks found in document.")
        }
        return DocxDocument(fallbackTitle, blocks)
    }

    fun writeDocx(doc: DocxDocument, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            fun putEntry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            putEntry("[Content_Types].xml", CONTENT_TYPES_XML)
            putEntry("_rels/.rels", ROOT_RELS_XML)
            putEntry("word/_rels/document.xml.rels", DOC_RELS_XML)
            putEntry("word/styles.xml", STYLES_XML)
            putEntry("word/document.xml", buildDocumentXml(doc))
        }
    }

    private fun buildDocumentXml(doc: DocxDocument): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        append("<w:body>")
        for (block in doc.blocks) {
            when (block) {
                is DocBlock.Heading -> {
                    val styleId = when (block.level.coerceIn(1, 3)) {
                        1 -> "Heading1"
                        2 -> "Heading2"
                        else -> "Heading3"
                    }
                    append("<w:p><w:pPr><w:pStyle w:val=\"$styleId\"/></w:pPr>")
                    append("<w:r><w:t xml:space=\"preserve\">${escapeXml(block.text)}</w:t></w:r></w:p>")
                }
                is DocBlock.Paragraph -> {
                    append("<w:p><w:r>")
                    if (block.isBold || block.isItalic) {
                        append("<w:rPr>")
                        if (block.isBold) append("<w:b/>")
                        if (block.isItalic) append("<w:i/>")
                        append("</w:rPr>")
                    }
                    append("<w:t xml:space=\"preserve\">${escapeXml(block.text)}</w:t></w:r></w:p>")
                }
                is DocBlock.BulletItem -> {
                    append("<w:p><w:pPr><w:ind w:left=\"360\"/></w:pPr>")
                    append("<w:r><w:t xml:space=\"preserve\">• ${escapeXml(block.text)}</w:t></w:r></w:p>")
                }
                is DocBlock.Table -> {
                    append("<w:tbl>")
                    append("<w:tblPr><w:tblBorders>")
                    append("<w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CCCCCC\"/>")
                    append("<w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CCCCCC\"/>")
                    append("<w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CCCCCC\"/>")
                    append("<w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"CCCCCC\"/>")
                    append("<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"DDDDDD\"/>")
                    append("<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"DDDDDD\"/>")
                    append("</w:tblBorders></w:tblPr>")
                    block.rows.forEachIndexed { rIdx, row ->
                        append("<w:tr>")
                        row.forEach { cell ->
                            append("<w:tc><w:p><w:r>")
                            if (rIdx == 0) append("<w:rPr><w:b/></w:rPr>")
                            append("<w:t xml:space=\"preserve\">${escapeXml(cell)}</w:t></w:r></w:p></w:tc>")
                        }
                        append("</w:tr>")
                    }
                    append("</w:tbl>")
                }
            }
        }
        append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>")
        append("</w:body></w:document>")
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val DOC_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault></w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:spacing w:after="160" w:line="276" w:lineRule="auto"/></w:pPr></w:style>
  <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="240" w:after="120"/></w:pPr><w:rPr><w:b/><w:sz w:val="36"/><w:color w:val="141619"/></w:rPr></w:style>
  <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="200" w:after="80"/></w:pPr><w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="2457F5"/></w:rPr></w:style>
  <w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="160" w:after="60"/></w:pPr><w:rPr><w:b/><w:sz w:val="24"/></w:rPr></w:style>
</w:styles>"""
}
