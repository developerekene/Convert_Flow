package com.example.data.converter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.domain.model.FileFormat
import com.example.domain.model.PdfDpiPreset
import com.example.domain.model.PdfPageSize
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PdfEngine {

    fun getPageCount(file: File): Int {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        }.getOrElse {
            runCatching {
                PDDocument.load(file).use { it.numberOfPages }
            }.getOrDefault(0)
        }
    }

    fun extractTextToDocxDocument(pdfFile: File, title: String): DocxDocument {
        val extracted = runCatching {
            PDDocument.load(pdfFile).use { doc ->
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }
                stripper.getText(doc) ?: ""
            }
        }.getOrDefault("")

        if (extracted.isBlank()) {
            val pages = getPageCount(pdfFile)
            return DocxDocument(
                title = title,
                blocks = listOf(
                    DocBlock.Heading(1, title),
                    DocBlock.Paragraph(
                        "This PDF ($pages page${if (pages == 1) "" else "s"}) appears to be image-based or scanned with no embedded text layer. Convert it to PNG/JPG or use Compress PDF to optimize its pages."
                    )
                )
            )
        }

        val lines = extracted.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<DocBlock>()
        val paraBuffer = mutableListOf<String>()

        fun flushPara() {
            if (paraBuffer.isNotEmpty()) {
                blocks += DocBlock.Paragraph(paraBuffer.joinToString(" ").trim())
                paraBuffer.clear()
            }
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> flushPara()
                line.startsWith("• ") || line.startsWith("- ") || line.startsWith("* ") -> {
                    flushPara()
                    blocks += DocBlock.BulletItem(line.drop(2).trim())
                }
                line.length in 3..64 && !line.endsWith(".") && !line.endsWith(",") &&
                    (line.all { !it.isLowerCase() || !it.isLetter() } ||
                        (paraBuffer.isEmpty() && line.first().isUpperCase() && line.split(" ").size <= 7)) -> {
                    flushPara()
                    val level = if (blocks.isEmpty()) 1 else 2
                    blocks += DocBlock.Heading(level, line)
                }
                else -> paraBuffer += line
            }
        }
        flushPara()
        if (blocks.isEmpty()) blocks += DocBlock.Paragraph(extracted.trim())
        return DocxDocument(title, blocks)
    }

    fun renderPdfToImages(
        pdfFile: File,
        outputDir: File,
        baseName: String,
        targetFormat: FileFormat,
        dpiPreset: PdfDpiPreset,
        quality: Int
    ): List<File> {
        val outFiles = mutableListOf<File>()
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = renderer.pageCount
                val scale = dpiPreset.dpi / 72f
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        val width = max(1, (page.width * scale).roundToInt()).coerceAtMost(3600)
                        val height = max(1, (page.height * scale).roundToInt()).coerceAtMost(4800)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val fileName = if (count == 1) {
                            "${baseName}.${targetFormat.ext}"
                        } else {
                            "${baseName}_p${i + 1}.${targetFormat.ext}"
                        }
                        val outFile = File(outputDir, fileName)
                        FileOutputStream(outFile).use { fos ->
                            val compressFmt = when (targetFormat) {
                                FileFormat.PNG -> Bitmap.CompressFormat.PNG
                                FileFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    Bitmap.CompressFormat.WEBP_LOSSY
                                } else {
                                    @Suppress("DEPRECATION")
                                    Bitmap.CompressFormat.WEBP
                                }
                                else -> Bitmap.CompressFormat.JPEG
                            }
                            bmp.compress(compressFmt, quality.coerceIn(15, 100), fos)
                        }
                        bmp.recycle()
                        outFiles.add(outFile)
                    }
                }
            }
        }
        return outFiles
    }

    fun compressAndResizePdf(
        pdfFile: File,
        outputFile: File,
        dpiPreset: PdfDpiPreset,
        quality: Int,
        pageSize: PdfPageSize,
        pageRange: String = ""
    ): Int {
        val pdfDocument = PdfDocument()
        var written = 0
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    val indices = parsePageRange(pageRange, total)
                    val renderScale = dpiPreset.dpi / 72f
                    val jpegBaos = ByteArrayOutputStream()

                    indices.forEachIndexed { outIdx, pageIdx ->
                        renderer.openPage(pageIdx).use { page ->
                            val renderW = max(1, (page.width * renderScale).roundToInt()).coerceAtMost(2400)
                            val renderH = max(1, (page.height * renderScale).roundToInt()).coerceAtMost(3400)
                            val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            // Re-encode via JPEG into byte stream so embedded PDF bitmap shrinks substantially
                            jpegBaos.reset()
                            bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(20, 95), jpegBaos)
                            bmp.recycle()
                            val compressedBytes = jpegBaos.toByteArray()
                            val compressedBmp = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                            val (targetW, targetH) = when (pageSize) {
                                PdfPageSize.ORIGINAL -> page.width to page.height
                                else -> {
                                    if (page.width > page.height) pageSize.heightPt to pageSize.widthPt
                                    else pageSize.widthPt to pageSize.heightPt
                                }
                            }

                            val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, outIdx + 1).create()
                            val outPage = pdfDocument.startPage(pageInfo)
                            val canvas = outPage.canvas
                            canvas.drawColor(Color.WHITE)

                            val scale = min(
                                targetW.toFloat() / compressedBmp.width.toFloat(),
                                targetH.toFloat() / compressedBmp.height.toFloat()
                            )
                            val drawW = compressedBmp.width * scale
                            val drawH = compressedBmp.height * scale
                            val left = (targetW - drawW) / 2f
                            val top = (targetH - drawH) / 2f

                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            canvas.drawBitmap(compressedBmp, null, RectF(left, top, left + drawW, top + drawH), paint)
                            compressedBmp.recycle()

                            pdfDocument.finishPage(outPage)
                            written++
                        }
                    }
                }
            }
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }
        return written
    }

    fun mergePdfs(inputFiles: List<File>, outputFile: File): Int {
        val merger = PDFMergerUtility()
        merger.destinationFileName = outputFile.absolutePath
        inputFiles.forEach { merger.addSource(it) }
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        return getPageCount(outputFile)
    }

    fun splitPdf(inputFile: File, outputFile: File, pageRange: String): Int {
        PDDocument.load(inputFile).use { sourceDoc ->
            val indices = parsePageRange(pageRange, sourceDoc.numberOfPages)
            PDDocument().use { targetDoc ->
                indices.forEach { idx ->
                    targetDoc.addPage(sourceDoc.getPage(idx))
                }
                targetDoc.save(outputFile)
                return targetDoc.numberOfPages
            }
        }
    }

    fun renderDocumentToPdf(
        doc: DocxDocument,
        outputFile: File,
        pageSize: PdfPageSize = PdfPageSize.A4
    ): Int {
        val mediaBox = pdfBoxPageSize(pageSize)
        val pageW = mediaBox.width
        val pageH = mediaBox.height
        val margin = 48f
        val contentWidth = pageW - margin * 2
        val minY = margin + 24f

        PDDocument().use { pdDoc ->
            val fontRegular = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
            val fontBold = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
            val fontItalic = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_OBLIQUE

            var pageNum = 1
            var page = com.tom_roush.pdfbox.pdmodel.PDPage(mediaBox)
            pdDoc.addPage(page)
            var cs = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pdDoc, page)
            var y = pageH - margin

            fun drawFooter() {
                cs.beginText()
                cs.setFont(fontRegular, 8.5f)
                cs.setNonStrokingColor(122f / 255f, 129f / 255f, 142f / 255f)
                cs.newLineAtOffset(margin, 24f)
                cs.showText("Convert Flow • Page $pageNum")
                cs.endText()
            }

            fun newPage() {
                drawFooter()
                cs.close()
                pageNum++
                page = com.tom_roush.pdfbox.pdmodel.PDPage(mediaBox)
                pdDoc.addPage(page)
                cs = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pdDoc, page)
                y = pageH - margin
            }

            fun drawWrappedLines(
                text: String,
                font: com.tom_roush.pdfbox.pdmodel.font.PDFont,
                fontSize: Float,
                leading: Float,
                xOffset: Float,
                maxWidth: Float,
                r: Int,
                g: Int,
                b: Int
            ) {
                val lines = wrapPdfText(sanitizePdfText(text), font, fontSize, maxWidth)
                for (line in lines) {
                    if (y - leading < minY) newPage()
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.setNonStrokingColor(r / 255f, g / 255f, b / 255f)
                    cs.newLineAtOffset(xOffset, y)
                    cs.showText(line)
                    cs.endText()
                    y -= leading
                }
            }

            for (block in doc.blocks) {
                when (block) {
                    is DocBlock.Heading -> {
                        val size = when (block.level) {
                            1 -> 18f
                            2 -> 14f
                            else -> 12f
                        }
                        val (r, g, b) = if (block.level == 2) Triple(36, 87, 245) else Triple(20, 22, 25)
                        drawWrappedLines(block.text, fontBold, size, size * 1.35f, margin, contentWidth, r, g, b)
                        y -= 6f
                    }
                    is DocBlock.Paragraph -> {
                        val font = when {
                            block.isBold -> fontBold
                            block.isItalic -> fontItalic
                            else -> fontRegular
                        }
                        drawWrappedLines(block.text, font, 11f, 15.5f, margin, contentWidth, 34, 37, 42)
                        y -= 6f
                    }
                    is DocBlock.BulletItem -> {
                        drawWrappedLines("-  ${block.text}", fontRegular, 11f, 15f, margin + 12f, contentWidth - 12f, 34, 37, 42)
                        y -= 4f
                    }
                    is DocBlock.Table -> {
                        y = drawPdfBoxTable(
                            rows = block.rows,
                            startY = y,
                            margin = margin,
                            contentWidth = contentWidth,
                            minY = minY,
                            fontRegular = fontRegular,
                            fontBold = fontBold,
                            getCs = { cs },
                            onNewPage = {
                                newPage()
                                y
                            }
                        )
                        y -= 10f
                    }
                }
            }

            drawFooter()
            cs.close()
            pdDoc.save(outputFile)
            return pageNum
        }
    }

    fun renderSpreadsheetToPdf(
        data: SpreadsheetData,
        title: String,
        outputFile: File,
        pageSize: PdfPageSize = PdfPageSize.A4
    ): Int {
        val sheet = data.sheets.firstOrNull() ?: SheetData("Sheet 1", emptyList())
        val colCount = sheet.rows.maxOfOrNull { it.size } ?: 1
        val baseBox = pdfBoxPageSize(pageSize)
        val mediaBox = if (colCount > 5) {
            com.tom_roush.pdfbox.pdmodel.common.PDRectangle(baseBox.height, baseBox.width)
        } else {
            baseBox
        }
        val pageW = mediaBox.width
        val pageH = mediaBox.height
        val margin = 36f
        val contentWidth = pageW - margin * 2
        val minY = margin + 22f

        PDDocument().use { pdDoc ->
            val fontRegular = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
            val fontBold = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
            var pageNum = 1
            var page = com.tom_roush.pdfbox.pdmodel.PDPage(mediaBox)
            pdDoc.addPage(page)
            var cs = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pdDoc, page)
            var y = pageH - margin

            cs.beginText()
            cs.setFont(fontBold, 14f)
            cs.setNonStrokingColor(20f / 255f, 22f / 255f, 25f / 255f)
            cs.newLineAtOffset(margin, y)
            cs.showText(sanitizePdfText(title))
            cs.endText()
            y -= 24f

            fun drawFooter() {
                cs.beginText()
                cs.setFont(fontRegular, 8.5f)
                cs.setNonStrokingColor(122f / 255f, 129f / 255f, 142f / 255f)
                cs.newLineAtOffset(margin, 20f)
                cs.showText("Convert Flow • Page $pageNum")
                cs.endText()
            }

            drawPdfBoxTable(
                rows = sheet.rows,
                startY = y,
                margin = margin,
                contentWidth = contentWidth,
                minY = minY,
                fontRegular = fontRegular,
                fontBold = fontBold,
                getCs = { cs },
                onNewPage = {
                    drawFooter()
                    cs.close()
                    pageNum++
                    page = com.tom_roush.pdfbox.pdmodel.PDPage(mediaBox)
                    pdDoc.addPage(page)
                    cs = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pdDoc, page)
                    pageH - margin
                }
            )

            drawFooter()
            cs.close()
            pdDoc.save(outputFile)
            return pageNum
        }
    }

    private fun drawPdfBoxTable(
        rows: List<List<String>>,
        startY: Float,
        margin: Float,
        contentWidth: Float,
        minY: Float,
        fontRegular: com.tom_roush.pdfbox.pdmodel.font.PDFont,
        fontBold: com.tom_roush.pdfbox.pdmodel.font.PDFont,
        getCs: () -> com.tom_roush.pdfbox.pdmodel.PDPageContentStream,
        onNewPage: () -> Float
    ): Float {
        if (rows.isEmpty()) return startY
        val colCount = rows.maxOf { it.size }.coerceAtLeast(1)
        val colWidth = contentWidth / colCount
        val cellPad = 5f
        val fontSize = 9f
        val lineH = 12f
        val maxCellTextW = (colWidth - cellPad * 2).coerceAtLeast(18f)

        var y = startY
        rows.forEachIndexed { rIdx, row ->
            val font = if (rIdx == 0) fontBold else fontRegular
            val wrappedCells = (0 until colCount).map { cIdx ->
                val raw = sanitizePdfText(row.getOrElse(cIdx) { "" })
                wrapPdfText(raw, font, fontSize, maxCellTextW)
            }
            val maxLines = (wrappedCells.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
            val rowHeight = maxLines * lineH + cellPad * 2

            if (y - rowHeight < minY) {
                y = onNewPage()
            }

            val cs = getCs()
            val rowBottom = y - rowHeight
            if (rIdx == 0) {
                cs.setNonStrokingColor(36f / 255f, 87f / 255f, 245f / 255f)
                cs.addRect(margin, rowBottom, contentWidth, rowHeight)
                cs.fill()
            } else if (rIdx % 2 == 0) {
                cs.setNonStrokingColor(244f / 255f, 245f / 255f, 248f / 255f)
                cs.addRect(margin, rowBottom, contentWidth, rowHeight)
                cs.fill()
            }

            cs.setStrokingColor(213f / 255f, 215f / 255f, 222f / 255f)
            cs.setLineWidth(0.75f)
            for (cIdx in 0 until colCount) {
                val cellX = margin + cIdx * colWidth
                cs.addRect(cellX, rowBottom, colWidth, rowHeight)
                cs.stroke()

                val cellLines = wrappedCells[cIdx]
                cellLines.forEachIndexed { lIdx, line ->
                    val textY = y - cellPad - fontSize - (lIdx * lineH)
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    if (rIdx == 0) cs.setNonStrokingColor(1f, 1f, 1f)
                    else cs.setNonStrokingColor(20f / 255f, 22f / 255f, 25f / 255f)
                    cs.newLineAtOffset(cellX + cellPad, textY)
                    cs.showText(line)
                    cs.endText()
                }
            }
            y = rowBottom
        }
        return y
    }

    private fun pdfBoxPageSize(pageSize: PdfPageSize): com.tom_roush.pdfbox.pdmodel.common.PDRectangle = when (pageSize) {
        PdfPageSize.LETTER -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LETTER
        PdfPageSize.LEGAL -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LEGAL
        else -> com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
    }

    private fun wrapPdfText(
        text: String,
        font: com.tom_roush.pdfbox.pdmodel.font.PDFont,
        fontSize: Float,
        maxWidth: Float
    ): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            val w = runCatching { font.getStringWidth(candidate) / 1000f * fontSize }.getOrDefault(candidate.length * fontSize * 0.5f)
            if (w <= maxWidth || current.isEmpty()) {
                current = StringBuilder(candidate)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun sanitizePdfText(raw: String): String {
        return raw
            .replace("\u2022", "-")
            .replace("\u2014", "-")
            .replace("\u2013", "-")
            .replace("\u2018", "'")
            .replace("\u2019", "'")
            .replace("\u201C", "\"")
            .replace("\u201D", "\"")
            .map { ch -> if (ch.code in 32..255) ch else ' ' }
            .joinToString("")
    }

    fun parsePageRange(rangeInput: String, totalPages: Int): List<Int> {
        if (totalPages <= 0) return emptyList()
        if (rangeInput.isBlank()) return (0 until totalPages).toList()
        val result = linkedSetOf<Int>()
        rangeInput.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val bounds = trimmed.split("-")
                val start = bounds.getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(1, totalPages)
                val end = bounds.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(1, totalPages)
                if (start != null && end != null) {
                    val range = if (start <= end) start..end else end..start
                    range.forEach { result.add(it - 1) }
                }
            } else {
                val single = trimmed.toIntOrNull()?.coerceIn(1, totalPages)
                if (single != null) result.add(single - 1)
            }
        }
        return if (result.isEmpty()) (0 until totalPages).toList() else result.toList()
    }
}
