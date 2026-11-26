package com.devekene.konvertibles.data.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.devekene.konvertibles.domain.model.FileFormat
import com.devekene.konvertibles.domain.model.PdfPageSize
import java.io.File
import java.io.FileOutputStream

object SampleFileGenerator {

    enum class SamplePreset(
        val fileName: String,
        val title: String,
        val subtitle: String,
        val format: FileFormat
    ) {
        REPORT_PDF("Quarterly_Report.pdf", "Quarterly Report", "Multi-page PDF with headings & table", FileFormat.PDF),
        BRIEF_DOCX("Project_Brief_2026.docx", "Project Brief", "Word DOCX with headings, bullets & table", FileFormat.DOCX),
        SALES_XLSX("Sales_Data_Q4.xlsx", "Sales Data Q4", "Excel XLSX workbook with 6 records", FileFormat.XLSX),
        BUDGET_CSV("Department_Budget.csv", "Department Budget", "CSV spreadsheet ready for Excel/PDF", FileFormat.CSV),
        DIAGRAM_PNG("Konvertibles_Card.png", "Konvertibles Card", "High-res PNG graphic for resize/compress", FileFormat.PNG)
    }

    fun generateSample(context: Context, preset: SamplePreset): Uri {
        return generateSampleForFormat(context, preset.format, preset.fileName)
    }

    fun generateSampleForFormat(
        context: Context,
        format: FileFormat,
        explicitName: String? = null
    ): Uri {
        val sampleDir = File(context.cacheDir, "samples").apply { mkdirs() }
        val fileName = explicitName ?: defaultSampleFileName(format)
        val file = File(sampleDir, fileName)

        when (format) {
            FileFormat.PDF -> {
                PdfEngine.renderDocumentToPdf(sampleDocxDocument(), file, PdfPageSize.A4)
            }
            FileFormat.DOCX -> {
                OoxmlWordHandler.writeDocx(sampleDocxDocument(), file)
            }
            FileFormat.TXT -> {
                file.writeText(TextFormatConverter.toPlainText(sampleDocxDocument()), Charsets.UTF_8)
            }
            FileFormat.MD -> {
                file.writeText(TextFormatConverter.toMarkdown(sampleDocxDocument()), Charsets.UTF_8)
            }
            FileFormat.HTML -> {
                file.writeText(TextFormatConverter.toHtml(sampleDocxDocument()), Charsets.UTF_8)
            }
            FileFormat.XLSX -> {
                OoxmlSpreadsheetHandler.writeXlsx(sampleSpreadsheet(), file)
            }
            FileFormat.CSV -> {
                file.writeText(OoxmlSpreadsheetHandler.toDelimited(sampleSpreadsheet(), ','), Charsets.UTF_8)
            }
            FileFormat.TSV -> {
                file.writeText(OoxmlSpreadsheetHandler.toDelimited(sampleSpreadsheet(), '\t'), Charsets.UTF_8)
            }
            FileFormat.JSON -> {
                file.writeText(OoxmlSpreadsheetHandler.toJson(sampleSpreadsheet()), Charsets.UTF_8)
            }
            FileFormat.XML -> {
                file.writeText(OoxmlSpreadsheetHandler.toXml(sampleSpreadsheet()), Charsets.UTF_8)
            }
            FileFormat.PNG -> generateSampleRasterImage(file, FileFormat.PNG)
            FileFormat.JPG -> generateSampleRasterImage(file, FileFormat.JPG)
            FileFormat.WEBP -> generateSampleRasterImage(file, FileFormat.WEBP)
            FileFormat.BMP -> generateSampleRasterImage(file, FileFormat.BMP)
            FileFormat.ZIP -> {
                val f1 = File(sampleDir, "Brief_Summary.txt").apply {
                    writeText(TextFormatConverter.toPlainText(sampleDocxDocument()), Charsets.UTF_8)
                }
                val f2 = File(sampleDir, "Metrics.csv").apply {
                    writeText(OoxmlSpreadsheetHandler.toDelimited(sampleSpreadsheet(), ','), Charsets.UTF_8)
                }
                ZipEngine.packFiles(listOf(f1.name to f1, f2.name to f2), file)
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun defaultSampleFileName(format: FileFormat): String = when (format) {
        FileFormat.PDF -> "Quarterly_Report.pdf"
        FileFormat.DOCX -> "Project_Brief_2026.docx"
        FileFormat.TXT -> "Meeting_Notes.txt"
        FileFormat.MD -> "Release_Notes.md"
        FileFormat.HTML -> "Product_Spec.html"
        FileFormat.XLSX -> "Sales_Data_Q4.xlsx"
        FileFormat.CSV -> "Department_Budget.csv"
        FileFormat.TSV -> "Export_Table.tsv"
        FileFormat.JSON -> "Dataset_Records.json"
        FileFormat.XML -> "Catalog_Feed.xml"
        FileFormat.PNG -> "Konvertibles_Card.png"
        FileFormat.JPG -> "Konvertibles_Photo.jpg"
        FileFormat.WEBP -> "Konvertibles_Asset.webp"
        FileFormat.BMP -> "Konvertibles_Bitmap.bmp"
        FileFormat.ZIP -> "Konvertibles_Bundle.zip"
    }

    private fun sampleDocxDocument(): DocxDocument = DocxDocument(
        title = "Konvertibles Studio Brief",
        blocks = listOf(
            DocBlock.Heading(1, "Q4 Product & Engineering Summary"),
            DocBlock.Paragraph("Konvertibles: Pocket ready to go. Transform documents, spreadsheets, images, and archives directly on your phone with precision formatting."),
            DocBlock.Heading(2, "Key Deliverables"),
            DocBlock.BulletItem("Bi-directional PDF and Word DOCX conversion with structural layout preservation."),
            DocBlock.BulletItem("Spreadsheet pipeline supporting XLSX, CSV, TSV, JSON, XML, and paginated PDF tables."),
            DocBlock.BulletItem("Image compressor and resizer with target-KB optimization and EXIF stripping."),
            DocBlock.Heading(2, "Benchmark Metrics"),
            DocBlock.Table(
                listOf(
                    listOf("Pipeline", "Avg Speed", "Output", "Status"),
                    listOf("PDF -> DOCX", "420 ms", "Text Extracted", "Ready"),
                    listOf("Image Compress", "180 ms", "62% Smaller", "Ready"),
                    listOf("XLSX -> PDF", "310 ms", "Paginated A4", "Ready")
                )
            ),
            DocBlock.Paragraph("All output files can be previewed in-app, saved to device folders, or shared directly.")
        )
    )

    private fun sampleSpreadsheet(): SpreadsheetData = SpreadsheetData(
        listOf(
            SheetData(
                name = "Q4_Metrics",
                rows = listOf(
                    listOf("Department", "Category", "Budget", "Actual", "Variance", "Status"),
                    listOf("Engineering", "Infrastructure", "42000", "38450", "+3550", "On Track"),
                    listOf("Design", "Typography & Assets", "14500", "13900", "+600", "On Track"),
                    listOf("QA Studio", "Device Lab", "18000", "19200", "-1200", "Over"),
                    listOf("Product", "User Research", "11000", "9800", "+1200", "Under"),
                    listOf("Security", "Code Audits", "16000", "15400", "+600", "On Track"),
                    listOf("Operations", "Build Pipeline", "22500", "20100", "+2400", "Under")
                )
            )
        )
    )

    private fun generateSampleRasterImage(file: File, format: FileFormat) {
        val w = 1600
        val h = 1000
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#FFF5F5"), Color.parseColor("#FEE2E2")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DC2626")
        }
        canvas.drawRoundRect(110f, 130f, 1490f, 870f, 52f, 52f, bandPaint)

        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        canvas.drawRoundRect(140f, 160f, 1460f, 840f, 40f, 40f, innerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#141518")
            textSize = 96f
            isFakeBoldText = true
        }
        canvas.drawText("KONVERTIBLES", 220f, 440f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DC2626")
            textSize = 44f
            isFakeBoldText = true
        }
        canvas.drawText("Pocket ready to go • 1600 x 1000 ${format.ext.uppercase()} Sample", 220f, 530f, subPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DC2626")
        }
        canvas.drawCircle(1320f, 300f, 75f, dotPaint)

        when (format) {
            FileFormat.BMP -> ImageEngine.writeBmp24(bmp, file)
            FileFormat.PNG -> FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileFormat.WEBP -> FileOutputStream(file).use {
                val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                bmp.compress(fmt, 92, it)
            }
            else -> FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }
        bmp.recycle()
    }
}
