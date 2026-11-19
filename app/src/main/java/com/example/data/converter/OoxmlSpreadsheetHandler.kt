package com.example.data.converter

import android.util.Xml
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object OoxmlSpreadsheetHandler {
    private val prettyJson = Json { prettyPrint = true }

    fun readXlsx(file: File): SpreadsheetData {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetNames = entries["xl/workbook.xml"]?.let { parseWorkbookSheetNames(it) } ?: emptyList()

        val worksheetKeys = entries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sortedBy {
                it.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: Int.MAX_VALUE
            }

        val sheets = worksheetKeys.mapIndexed { idx, key ->
            val name = sheetNames.getOrNull(idx) ?: "Sheet ${idx + 1}"
            val rows = parseWorksheet(entries.getValue(key), sharedStrings)
            SheetData(name, rows)
        }.ifEmpty {
            listOf(SheetData("Sheet 1", emptyList()))
        }

        return SpreadsheetData(sheets)
    }

    fun writeXlsx(data: SpreadsheetData, outputFile: File) {
        val sheet = data.sheets.firstOrNull() ?: SheetData("Sheet 1", emptyList())
        val sharedStrings = mutableListOf<String>()
        val sharedMap = mutableMapOf<String, Int>()

        fun internString(value: String): Int {
            return sharedMap.getOrPut(value) {
                sharedStrings.add(value)
                sharedStrings.size - 1
            }
        }

        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("<sheetData>")
            sheet.rows.forEachIndexed { rowIndex, row ->
                val rNum = rowIndex + 1
                append("<row r=\"$rNum\">")
                row.forEachIndexed { colIndex, cellVal ->
                    val ref = "${colIndexToLetters(colIndex)}$rNum"
                    val numVal = cellVal.toDoubleOrNull()
                    val styleAttr = if (rowIndex == 0) " s=\"1\"" else ""
                    if (numVal != null && cellVal.isNotBlank() && !cellVal.startsWith("0") || cellVal == "0") {
                        append("<c r=\"$ref\"$styleAttr><v>$cellVal</v></c>")
                    } else {
                        val sIdx = internString(cellVal)
                        append("<c r=\"$ref\" t=\"s\"$styleAttr><v>$sIdx</v></c>")
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val sharedStringsXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">""")
            sharedStrings.forEach { s ->
                append("<si><t xml:space=\"preserve\">${escapeXml(s)}</t></si>")
            }
            append("</sst>")
        }

        val safeSheetName = escapeXml(sheet.name.take(31).ifBlank { "Sheet 1" })
        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="$safeSheetName" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            fun put(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            put("[Content_Types].xml", XLSX_CONTENT_TYPES)
            put("_rels/.rels", XLSX_ROOT_RELS)
            put("xl/_rels/workbook.xml.rels", XLSX_WORKBOOK_RELS)
            put("xl/workbook.xml", workbookXml)
            put("xl/styles.xml", XLSX_STYLES)
            put("xl/sharedStrings.xml", sharedStringsXml)
            put("xl/worksheets/sheet1.xml", sheetXml)
        }
    }

    fun readDelimited(raw: String, delimiter: Char, sheetName: String = "Sheet 1"): SpreadsheetData {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')

        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    delimiter -> {
                        currentRow.add(cell.toString())
                        cell.clear()
                    }
                    '\n' -> {
                        currentRow.add(cell.toString())
                        cell.clear()
                        if (currentRow.any { it.isNotEmpty() }) {
                            rows.add(currentRow.toList())
                        }
                        currentRow.clear()
                    }
                    else -> cell.append(ch)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(cell.toString())
            if (currentRow.any { it.isNotEmpty() }) {
                rows.add(currentRow.toList())
            }
        }
        return SpreadsheetData(listOf(SheetData(sheetName, rows)))
    }

    fun toDelimited(data: SpreadsheetData, delimiter: Char): String {
        val sheet = data.sheets.firstOrNull() ?: return ""
        return sheet.rows.joinToString("\n") { row ->
            row.joinToString(delimiter.toString()) { field ->
                if (field.contains(delimiter) || field.contains('"') || field.contains('\n')) {
                    "\"${field.replace("\"", "\"\"")}\""
                } else {
                    field
                }
            }
        }
    }

    fun readJsonTable(raw: String, sheetName: String = "JSON Data"): SpreadsheetData {
        val parsed = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
            ?: return SpreadsheetData(listOf(SheetData(sheetName, listOf(listOf("Raw"), listOf(raw)))))

        val rows = when (parsed) {
            is JsonArray -> {
                if (parsed.isEmpty()) {
                    emptyList()
                } else if (parsed.first() is JsonObject) {
                    val headers = linkedSetOf<String>()
                    parsed.forEach { el ->
                        (el as? JsonObject)?.keys?.forEach { headers.add(it) }
                    }
                    val headerList = headers.toList()
                    val dataRows = parsed.mapNotNull { el ->
                        (el as? JsonObject)?.let { obj ->
                            headerList.map { key -> elementToCell(obj[key]) }
                        }
                    }
                    listOf(headerList) + dataRows
                } else if (parsed.first() is JsonArray) {
                    parsed.mapNotNull { rowEl ->
                        (rowEl as? JsonArray)?.map { elementToCell(it) }
                    }
                } else {
                    listOf(listOf("Value")) + parsed.map { listOf(elementToCell(it)) }
                }
            }
            is JsonObject -> {
                listOf(listOf("Key", "Value")) + parsed.entries.map { (k, v) -> listOf(k, elementToCell(v)) }
            }
            else -> listOf(listOf("Value"), listOf(parsed.jsonPrimitive.content))
        }
        return SpreadsheetData(listOf(SheetData(sheetName, rows)))
    }

    fun toJson(data: SpreadsheetData): String {
        val sheet = data.sheets.firstOrNull() ?: return "[]"
        if (sheet.rows.isEmpty()) return "[]"
        val headers = sheet.rows.first().mapIndexed { idx, h -> h.ifBlank { "col_${idx + 1}" } }
        val jsonArray = buildJsonArray {
            sheet.rows.drop(1).forEach { row ->
                add(buildJsonObject {
                    headers.forEachIndexed { idx, key ->
                        val v = row.getOrElse(idx) { "" }
                        val num = v.toLongOrNull()
                        val dbl = v.toDoubleOrNull()
                        when {
                            num != null -> put(key, JsonPrimitive(num))
                            dbl != null -> put(key, JsonPrimitive(dbl))
                            v.equals("true", true) || v.equals("false", true) ->
                                put(key, JsonPrimitive(v.toBoolean()))
                            else -> put(key, JsonPrimitive(v))
                        }
                    }
                })
            }
        }
        return prettyJson.encodeToString(JsonElement.serializer(), jsonArray)
    }

    fun toHtmlTable(data: SpreadsheetData, title: String): String = buildString {
        append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\"/>\n")
        append("<title>${TextFormatConverter.escapeHtml(title)}</title>\n")
        append("<style>body{font-family:-apple-system,sans-serif;margin:28px;color:#141619;}h2{color:#0F9D6E;}table{border-collapse:collapse;width:100%;font-size:14px;}th,td{border:1px solid #DCDAD2;padding:8px 12px;text-align:left;}th{background:#0F9D6E;color:#fff;}tr:nth-child(even){background:#F7F6F2;}</style>\n")
        append("</head>\n<body>\n")
        data.sheets.forEach { sheet ->
            append("<h2>${TextFormatConverter.escapeHtml(sheet.name)}</h2>\n<table>\n")
            sheet.rows.forEachIndexed { idx, row ->
                val tag = if (idx == 0) "th" else "td"
                append("  <tr>")
                row.forEach { cell ->
                    append("<$tag>${TextFormatConverter.escapeHtml(cell)}</$tag>")
                }
                append("</tr>\n")
            }
            append("</table>\n")
        }
        append("</body>\n</html>")
    }

    private fun elementToCell(el: JsonElement?): String = when (el) {
        null -> ""
        is JsonPrimitive -> el.content
        else -> el.toString()
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(xmlBytes.inputStream(), "UTF-8")
        }
        var inSi = false
        val buffer = StringBuilder()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(':') ?: ""
            when (ev) {
                XmlPullParser.START_TAG -> {
                    if (tag == "si") {
                        inSi = true
                        buffer.clear()
                    } else if (tag == "t" && inSi) {
                        buffer.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tag == "si" && inSi) {
                        list.add(buffer.toString())
                        inSi = false
                    }
                }
            }
            ev = parser.next()
        }
        return list
    }

    private fun parseWorkbookSheetNames(xmlBytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(xmlBytes.inputStream(), "UTF-8")
        }
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(':') ?: ""
            if (ev == XmlPullParser.START_TAG && tag == "sheet") {
                val n = parser.getAttributeValue(null, "name")
                if (!n.isNullOrBlank()) names.add(n)
            }
            ev = parser.next()
        }
        return names
    }

    private fun parseWorksheet(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(xmlBytes.inputStream(), "UTF-8")
        }
        var currentRow = mutableMapOf<Int, String>()
        var currentColIdx = 0
        var cellType = ""
        var inCell = false
        var cellValue = ""
        var ev = parser.eventType

        while (ev != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(':') ?: ""
            when (ev) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "row" -> currentRow = mutableMapOf()
                        "c" -> {
                            inCell = true
                            val ref = parser.getAttributeValue(null, "r") ?: ""
                            currentColIdx = cellRefToColIndex(ref) ?: currentRow.size
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            cellValue = ""
                        }
                        "v" -> if (inCell) {
                            val rawV = parser.nextText()
                            cellValue = when (cellType) {
                                "s" -> rawV.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: rawV
                                "b" -> if (rawV == "1") "TRUE" else "FALSE"
                                else -> rawV
                            }
                        }
                        "t" -> if (inCell && cellType == "inlineStr") {
                            cellValue = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "c" -> if (inCell) {
                            currentRow[currentColIdx] = cellValue
                            inCell = false
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) {
                                val maxCol = currentRow.keys.maxOrNull() ?: 0
                                val dense = (0..maxCol).map { currentRow[it] ?: "" }
                                rows.add(dense)
                            }
                        }
                    }
                }
            }
            ev = parser.next()
        }
        val globalCols = rows.maxOfOrNull { it.size } ?: 0
        return rows.map { r ->
            if (r.size < globalCols) r + List(globalCols - r.size) { "" } else r
        }
    }

    private fun cellRefToColIndex(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        var col = 0
        for (ch in letters) {
            col = col * 26 + (ch - 'A' + 1)
        }
        return col - 1
    }

    private fun colIndexToLetters(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A' + rem))
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private const val XLSX_CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>"""

    private const val XLSX_ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val XLSX_WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>"""

    private const val XLSX_STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0F9D6E"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>"""
}
