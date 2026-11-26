package com.devekene.konvertibles.data.converter

import android.content.Context
import com.devekene.konvertibles.data.local.ConversionRecord
import com.devekene.konvertibles.data.local.HistoryRepository
import com.devekene.konvertibles.domain.model.ConversionOptions
import com.devekene.konvertibles.domain.model.ConversionOutput
import com.devekene.konvertibles.domain.model.ConversionProgress
import com.devekene.konvertibles.domain.model.FileFormat
import com.devekene.konvertibles.domain.model.FormatCategory
import com.devekene.konvertibles.domain.model.PdfPageSize
import com.devekene.konvertibles.domain.model.PickedFile
import com.devekene.konvertibles.domain.model.ResizeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ConversionEngine(
    private val context: Context,
    private val historyRepository: HistoryRepository
) {
    suspend fun executeConversion(
        pickedFiles: List<PickedFile>,
        targetFormat: FileFormat,
        options: ConversionOptions,
        operationLabel: String? = null,
        extractArchive: Boolean = false,
        onProgress: (ConversionProgress) -> Unit = {}
    ): ConversionOutput = withContext(Dispatchers.IO) {
        require(pickedFiles.isNotEmpty()) { "Select at least one file to convert." }
        val startMs = System.currentTimeMillis()
        val tempInputs = mutableListOf<Pair<PickedFile, File>>()

        try {
            onProgress(ConversionProgress("Preparing workspace...", 0.12f))
            pickedFiles.forEachIndexed { idx, pf ->
                val temp = FileHelper.copyUriToTemp(context, pf.uri, "in_${idx}_${pf.displayName}")
                tempInputs.add(pf to temp)
            }

            val primary = pickedFiles.first()
            val totalInputBytes = tempInputs.sumOf { it.second.length() }
            val desiredBase = options.customOutputName.trim().ifBlank {
                if (pickedFiles.size > 1) "${primary.displayName.substringBeforeLast('.')}_bundle"
                else primary.displayName.substringBeforeLast('.')
            }

            onProgress(ConversionProgress("Converting ${primary.format.displayName} → ${targetFormat.displayName}...", 0.45f))

            var finalOutputFile: File
            var finalOutputFormat = targetFormat
            var extraFiles: List<File> = emptyList()
            var summary: String

            // Multi-file workflows
            if (pickedFiles.size > 1) {
                when {
                    targetFormat == FileFormat.ZIP -> {
                        finalOutputFile = FileHelper.createUniqueOutputFile(context, desiredBase, "zip")
                        val count = ZipEngine.packFiles(tempInputs.map { it.first.displayName to it.second }, finalOutputFile)
                        summary = "Packed $count files into a compressed ZIP archive."
                    }
                    pickedFiles.all { it.format.category == FormatCategory.IMAGE } && targetFormat == FileFormat.PDF -> {
                        finalOutputFile = FileHelper.createUniqueOutputFile(context, desiredBase, "pdf")
                        val pages = ImageEngine.imagesToPdf(
                            tempInputs.map { it.second },
                            finalOutputFile,
                            options.pdfPageSize,
                            options.quality
                        )
                        summary = "Combined $pages images into ${options.pdfPageSize.label} PDF."
                    }
                    pickedFiles.all { it.format == FileFormat.PDF } && targetFormat == FileFormat.PDF -> {
                        finalOutputFile = FileHelper.createUniqueOutputFile(context, "${desiredBase}_merged", "pdf")
                        val pages = PdfEngine.mergePdfs(tempInputs.map { it.second }, finalOutputFile)
                        summary = "Merged ${pickedFiles.size} PDFs into $pages total pages."
                    }
                    pickedFiles.all { it.format.category == FormatCategory.IMAGE } && targetFormat.category == FormatCategory.IMAGE -> {
                        val convertedImages = mutableListOf<File>()
                        val tempOutDir = File(context.cacheDir, "batch_${System.nanoTime()}").apply { mkdirs() }
                        try {
                            tempInputs.forEachIndexed { idx, (pf, file) ->
                                val outImg = File(tempOutDir, "${pf.displayName.substringBeforeLast('.')}_${idx + 1}.${targetFormat.ext}")
                                ImageEngine.convertOrCompressImage(file, outImg, targetFormat, options)
                                convertedImages.add(outImg)
                            }
                            finalOutputFile = FileHelper.createUniqueOutputFile(context, "${desiredBase}_images", "zip")
                            ZipEngine.packFiles(convertedImages.map { it.name to it }, finalOutputFile)
                            finalOutputFormat = FileFormat.ZIP
                            summary = "Processed ${convertedImages.size} images to ${targetFormat.displayName} and bundled as ZIP."
                        } finally {
                            tempOutDir.deleteRecursively()
                        }
                    }
                    else -> {
                        finalOutputFile = FileHelper.createUniqueOutputFile(context, desiredBase, "zip")
                        val count = ZipEngine.packFiles(tempInputs.map { it.first.displayName to it.second }, finalOutputFile)
                        finalOutputFormat = FileFormat.ZIP
                        summary = "Bundled $count files into ZIP archive."
                    }
                }
            } else {
                // Single-file workflows
                val inputFile = tempInputs.first().second
                val srcFormat = primary.format

                when {
                    srcFormat == FileFormat.ZIP && (extractArchive || targetFormat == FileFormat.ZIP) -> {
                        val tempExtractDir = File(context.cacheDir, "zip_extract_${System.nanoTime()}").apply { mkdirs() }
                        try {
                            val extracted = ZipEngine.extractZip(inputFile, tempExtractDir)
                            if (extracted.isEmpty()) {
                                finalOutputFile = FileHelper.createUniqueOutputFile(context, "${desiredBase}_manifest", "txt")
                                finalOutputFormat = FileFormat.TXT
                                finalOutputFile.writeText("The selected ZIP archive is empty.", Charsets.UTF_8)
                                summary = "ZIP archive contained 0 files."
                            } else if (extracted.size == 1) {
                                val single = extracted.first()
                                val detected = FileFormat.fromExtension(single.extension) ?: FileFormat.TXT
                                finalOutputFormat = detected
                                finalOutputFile = FileHelper.createUniqueOutputFile(
                                    context,
                                    single.nameWithoutExtension.ifBlank { desiredBase },
                                    single.extension.ifBlank { "txt" }
                                )
                                single.copyTo(finalOutputFile, overwrite = true)
                                summary = "Extracted ${single.name} (${FileHelper.formatSize(finalOutputFile.length())})."
                            } else {
                                val savedFiles = extracted.map { entryFile ->
                                    val dest = FileHelper.createUniqueOutputFile(
                                        context,
                                        entryFile.nameWithoutExtension.ifBlank { "entry" },
                                        entryFile.extension.ifBlank { "bin" }
                                    )
                                    entryFile.copyTo(dest, overwrite = true)
                                    dest
                                }
                                finalOutputFormat = FileFormat.TXT
                                finalOutputFile = FileHelper.createUniqueOutputFile(context, "${desiredBase}_extracted", "txt")
                                val manifest = buildString {
                                    appendLine("Konvertibles Archive Extraction Report")
                                    appendLine("Source: ${primary.displayName}")
                                    appendLine("Extracted ${savedFiles.size} file(s):")
                                    appendLine()
                                    savedFiles.forEachIndexed { i, f ->
                                        appendLine("${i + 1}. ${f.name} (${FileHelper.formatSize(f.length())})")
                                    }
                                }
                                finalOutputFile.writeText(manifest, Charsets.UTF_8)
                                extraFiles = savedFiles
                                summary = "Extracted ${savedFiles.size} files from ${primary.displayName}."
                            }
                        } finally {
                            tempExtractDir.deleteRecursively()
                        }
                    }

                    targetFormat == FileFormat.ZIP -> {
                        finalOutputFile = FileHelper.createUniqueOutputFile(context, desiredBase, "zip")
                        ZipEngine.packFiles(listOf(primary.displayName to inputFile), finalOutputFile)
                        summary = "Compressed ${primary.displayName} into ZIP archive."
                    }

                    // IMAGE -> IMAGE
                    srcFormat.category == FormatCategory.IMAGE && targetFormat.category == FormatCategory.IMAGE -> {
                        val suffix = when {
                            srcFormat == targetFormat && options.resizeMode != ResizeMode.NONE -> "_resized"
                            srcFormat == targetFormat -> "_compressed"
                            else -> ""
                        }
                        finalOutputFile = FileHelper.createUniqueOutputFile(context, "$desiredBase$suffix", targetFormat.ext)
                        val (w, h) = ImageEngine.convertOrCompressImage(inputFile, finalOutputFile, targetFormat, options)
                        val kbNote = options.targetSizeKb?.let { " • Target ≤${it}KB" } ?: " • Q${options.quality}%"
                        summary = "Output ${w}×${h}px ${targetFormat.displayName}$kbNote."
                    }

                    // IMAGE -> PDF
                    srcFormat.category == FormatCategory.IMAGE && targetFormat == FileFormat.PDF -> {
                        finalOutputFile = FileHelper.createUniqueOutputFile(context, desiredBase, "pdf")
                        ImageEngine.imagesToPdf(listOf(inputFile), finalOutputFile, options.pdfPageSize, options.quality)
                        summary = "Embedded image onto ${options.pdfPageSize.label} PDF page."
                    }

                    // PDF -> IMAGE(S)
                    srcFormat == FileFormat.PDF && targetFormat.category == FormatCategory.IMAGE -> {
                        val tempRenderDir = File(context.cacheDir, "pdf_imgs_${System.nanoTime()}").apply { mkdirs() }
                        try {
                            val rendered = PdfEngine.renderPdfToImages(
                                pdfFile = inputFile,
                                outputDir = tempRenderDir,
                                baseName = desiredBase,
                                targetFormat = targetFormat,
                                dpiPreset = options.pdfDpi,
                                quality = options.quality
                            )
                            if (rendered.size <= 1) {
                                finalOutputFile = FileHelper.createUniqueOutputFile(context, desiredBase, targetFormat.ext)
                                rendered.first().copyTo(finalOutputFile, overwrite = true)
                                summary = "Rendered 1 PDF page at ${options.pdfDpi.label} to ${targetFormat.displayName}."
                            } else {
                                val savedPages = rendered.mapIndexed { idx, file ->
                                    val dest = FileHelper.createUniqueOutputFile(context, "${desiredBase}_p${idx + 1}", targetFormat.ext)
                                    file.copyTo(dest, overwrite = true)
                                    dest
                                }
                                finalOutputFile = FileHelper.createUniqueOutputFile(context, "${desiredBase}_pages", "zip")
                                ZipEngine.packFiles(savedPages.map { it.name to it }, finalOutputFile)
                                finalOutputFormat = FileFormat.ZIP
                                extraFiles = savedPages
                                summary = "Exported ${savedPages.size} PDF pages as ${targetFormat.displayName} (${options.pdfDpi.label}) + ZIP bundle."
                            }
                        } finally {
                            tempRenderDir.deleteRecursively()
                        }
                    }

                    // PDF -> PDF (Compress / Resize / Extract Pages)
                    srcFormat == FileFormat.PDF && targetFormat == FileFormat.PDF -> {
                        val hasRange = options.pageRange.isNotBlank()
                        val suffix = if (hasRange) "_extracted" else "_optimized"
                        finalOutputFile = FileHelper.createUniqueOutputFile(context, "$desiredBase$suffix", "pdf")
                        val totalPages = PdfEngine.getPageCount(inputFile)
                        val written = if (hasRange && options.pdfPageSize == PdfPageSize.ORIGINAL && options.quality >= 90) {
                            PdfEngine.splitPdf(inputFile, finalOutputFile, options.pageRange)
                        } else {
                            PdfEngine.compressAndResizePdf(
                                pdfFile = inputFile,
                                outputFile = finalOutputFile,
                                dpiPreset = options.pdfDpi,
                                quality = options.quality,
                                pageSize = options.pdfPageSize,
                                pageRange = options.pageRange
                            )
                        }
                        summary = if (hasRange) {
                            "Extracted $written of $totalPages pages (${options.pdfPageSize.label}, ${options.pdfDpi.label})."
                        } else {
                            "Optimized $written PDF pages at ${options.pdfDpi.label} (${options.pdfPageSize.label})."
                        }
                    }

                    // PDF -> DOCX / TXT / MD / HTML
                    srcFormat == FileFormat.PDF && targetFormat in setOf(FileFormat.DOCX, FileFormat.TXT, FileFormat.MD, FileFormat.HTML) -> {
                        val doc = PdfEngine.extractTextToDocxDocument(inputFile, desiredBase)
                        finalOutputFile = FileHelper.createUniqueOutputFile(context, desiredBase, targetFormat.ext)
                        writeStructuredDocument(doc, finalOutputFile, targetFormat)
                        summary = "Extracted ${doc.blocks.size} content blocks from PDF to ${targetFormat.displayName}."
                    }

                    // DOCX / TXT / MD / HTML <-> DOCX / TXT / MD / HTML / PDF
                    srcFormat in setOf(FileFormat.DOCX, FileFormat.TXT, FileFormat.MD, FileFormat.HTML) &&
                        targetFormat in setOf(FileFormat.PDF, FileFormat.DOCX, FileFormat.TXT, FileFormat.MD, FileFormat.HTML) -> {
                        val doc = readStructuredDocument(inputFile, srcFormat, desiredBase)
                        finalOutputFile = FileHelper.createUniqueOutputFile(context, desiredBase, targetFormat.ext)
                        if (targetFormat == FileFormat.PDF) {
                            val pages = PdfEngine.renderDocumentToPdf(doc, finalOutputFile, options.pdfPageSize)
                            summary = "Typeset ${doc.blocks.size} blocks into $pages ${options.pdfPageSize.label} PDF page(s)."
                        } else {
                            writeStructuredDocument(doc, finalOutputFile, targetFormat)
                            summary = "Converted ${srcFormat.displayName} (${doc.blocks.size} blocks) to ${targetFormat.displayName}."
                        }
                    }

                    // SPREADSHEET <-> SPREADSHEET / PDF / HTML / XML
                    srcFormat.category == FormatCategory.SPREADSHEET &&
                        (targetFormat.category == FormatCategory.SPREADSHEET || targetFormat in setOf(FileFormat.HTML, FileFormat.PDF, FileFormat.TXT)) -> {
                        val sheetData = readSpreadsheet(inputFile, srcFormat, desiredBase)
                        finalOutputFile = FileHelper.createUniqueOutputFile(context, desiredBase, targetFormat.ext)
                        when (targetFormat) {
                            FileFormat.XLSX -> OoxmlSpreadsheetHandler.writeXlsx(sheetData, finalOutputFile)
                            FileFormat.CSV -> finalOutputFile.writeText(OoxmlSpreadsheetHandler.toDelimited(sheetData, ','), Charsets.UTF_8)
                            FileFormat.TSV, FileFormat.TXT -> finalOutputFile.writeText(OoxmlSpreadsheetHandler.toDelimited(sheetData, '\t'), Charsets.UTF_8)
                            FileFormat.JSON -> finalOutputFile.writeText(OoxmlSpreadsheetHandler.toJson(sheetData), Charsets.UTF_8)
                            FileFormat.XML -> finalOutputFile.writeText(OoxmlSpreadsheetHandler.toXml(sheetData), Charsets.UTF_8)
                            FileFormat.HTML -> finalOutputFile.writeText(OoxmlSpreadsheetHandler.toHtmlTable(sheetData, desiredBase), Charsets.UTF_8)
                            FileFormat.PDF -> PdfEngine.renderSpreadsheetToPdf(sheetData, desiredBase, finalOutputFile, options.pdfPageSize)
                            else -> error("Unsupported spreadsheet target: ${targetFormat.displayName}")
                        }
                        val rowCount = sheetData.sheets.firstOrNull()?.rows?.size ?: 0
                        summary = "Transformed $rowCount rows from ${srcFormat.displayName} to ${targetFormat.displayName}."
                    }

                    else -> error("Conversion ${srcFormat.displayName} → ${targetFormat.displayName} is not supported.")
                }
            }

            onProgress(ConversionProgress("Saving to library...", 0.90f))
            val outBytes = finalOutputFile.length()
            val opLabel = operationLabel ?: if (primary.format == finalOutputFormat) {
                "Optimize ${finalOutputFormat.displayName}"
            } else {
                "${primary.format.displayName} → ${finalOutputFormat.displayName}"
            }

            val recordId = historyRepository.insert(
                ConversionRecord(
                    sourceName = if (pickedFiles.size > 1) "${pickedFiles.size} files (${primary.displayName})" else primary.displayName,
                    sourceExtension = primary.format.ext,
                    outputName = finalOutputFile.name,
                    outputExtension = finalOutputFormat.ext,
                    outputPath = finalOutputFile.absolutePath,
                    category = finalOutputFormat.category.name,
                    operationLabel = opLabel,
                    originalSizeBytes = totalInputBytes,
                    outputSizeBytes = outBytes,
                    summary = summary
                )
            )

            onProgress(ConversionProgress("Complete", 1.0f))
            ConversionOutput(
                recordId = recordId,
                outputFile = finalOutputFile,
                outputFormat = finalOutputFormat,
                sourceName = primary.displayName,
                sourceFormat = primary.format,
                originalSizeBytes = totalInputBytes,
                outputSizeBytes = outBytes,
                durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
                summary = summary,
                extraOutputFiles = extraFiles
            )
        } finally {
            tempInputs.forEach { it.second.delete() }
        }
    }

    private fun readStructuredDocument(file: File, format: FileFormat, title: String): DocxDocument {
        return when (format) {
            FileFormat.DOCX -> OoxmlWordHandler.readDocx(file, title)
            FileFormat.TXT -> TextFormatConverter.fromPlainText(title, file.readText(Charsets.UTF_8))
            FileFormat.MD -> TextFormatConverter.fromMarkdown(title, file.readText(Charsets.UTF_8))
            FileFormat.HTML -> TextFormatConverter.fromHtml(title, file.readText(Charsets.UTF_8))
            else -> error("Cannot read $format as text document")
        }
    }

    private fun writeStructuredDocument(doc: DocxDocument, outputFile: File, format: FileFormat) {
        when (format) {
            FileFormat.DOCX -> OoxmlWordHandler.writeDocx(doc, outputFile)
            FileFormat.TXT -> outputFile.writeText(TextFormatConverter.toPlainText(doc), Charsets.UTF_8)
            FileFormat.MD -> outputFile.writeText(TextFormatConverter.toMarkdown(doc), Charsets.UTF_8)
            FileFormat.HTML -> outputFile.writeText(TextFormatConverter.toHtml(doc), Charsets.UTF_8)
            else -> error("Unsupported text document target: $format")
        }
    }

    private fun readSpreadsheet(file: File, format: FileFormat, title: String): SpreadsheetData {
        return when (format) {
            FileFormat.XLSX -> OoxmlSpreadsheetHandler.readXlsx(file)
            FileFormat.CSV -> OoxmlSpreadsheetHandler.readDelimited(file.readText(Charsets.UTF_8), ',', title)
            FileFormat.TSV -> OoxmlSpreadsheetHandler.readDelimited(file.readText(Charsets.UTF_8), '\t', title)
            FileFormat.JSON -> OoxmlSpreadsheetHandler.readJsonTable(file.readText(Charsets.UTF_8), title)
            FileFormat.XML -> OoxmlSpreadsheetHandler.readXmlTable(file.readText(Charsets.UTF_8), title)
            else -> error("Cannot read $format as spreadsheet")
        }
    }
}
