package com.example.data.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.domain.model.FileFormat
import com.example.domain.model.PdfPageSize
import java.io.File
import java.io.FileOutputStream

object SampleFileGenerator {

    enum class SamplePreset(
        val fileName: String,
        val title: String,
        val subtitle: String,
        val format: FileFormat
    ) {
        REPORT_PDF("Quarterly_Studio_Report.pdf", "Quarterly Studio Report", "Multi-page PDF with headings & table", FileFormat.PDF),
        BRIEF_DOCX("Project_Brief_2026.docx", "Project Brief", "Word DOCX with headings, bullets & table", FileFormat.DOCX),
        SALES_XLSX("Sales_Data_Q4.xlsx", "Sales Data Q4", "Excel XLSX workbook with 8 records", FileFormat.XLSX),
        BUDGET_CSV("Department_Budget.csv", "Department Budget", "CSV spreadsheet ready for XLSX/PDF", FileFormat.CSV),
        DIAGRAM_PNG("Studio_Showcase_Card.png", "Studio Showcase Card", "High-res PNG graphic for resize/compress", FileFormat.PNG)
    }

    fun generateSample(context: Context, preset: SamplePreset): Uri {
        val sampleDir = File(context.cacheDir, "samples").apply { mkdirs() }
        val file = File(sampleDir, preset.fileName)

        when (preset) {
            SamplePreset.REPORT_PDF -> {
                val doc = sampleDocxDocument()
                PdfEngine.renderDocumentToPdf(doc, file, PdfPageSize.A4)
            }
            SamplePreset.BRIEF_DOCX -> {
                OoxmlWordHandler.writeDocx(sampleDocxDocument(), file)
            }
            SamplePreset.SALES_XLSX -> {
                OoxmlSpreadsheetHandler.writeXlsx(sampleSpreadsheet(), file)
            }
            SamplePreset.BUDGET_CSV -> {
                val csv = OoxmlSpreadsheetHandler.toDelimited(sampleSpreadsheet(), ',')
                file.writeText(csv, Charsets.UTF_8)
            }
            SamplePreset.DIAGRAM_PNG -> {
                generateSamplePng(file)
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun sampleDocxDocument(): DocxDocument = DocxDocument(
        title = "Convert Flow Studio Brief",
        blocks = listOf(
            DocBlock.Heading(1, "Q4 Product & Engineering Summary"),
            DocBlock.Paragraph("Convert Flow processes all document, spreadsheet, and image transformations locally on-device with zero cloud uploads."),
            DocBlock.Heading(2, "Key Deliverables"),
            DocBlock.BulletItem("Bi-directional PDF and Word DOCX conversion with structural layout preservation."),
            DocBlock.BulletItem("Spreadsheet pipeline supporting XLSX, CSV, TSV, JSON, and paginated PDF tables."),
            DocBlock.BulletItem("Image compressor and resizer with target-KB optimization and EXIF stripping."),
            DocBlock.Heading(2, "Benchmark Metrics"),
            DocBlock.Table(
                listOf(
                    listOf("Pipeline", "Avg Speed", "Size Reduction", "Mode"),
                    listOf("PDF -> DOCX", "420 ms", "Text Extracted", "Local"),
                    listOf("Image Compress", "180 ms", "62% Smaller", "Local"),
                    listOf("XLSX -> PDF", "310 ms", "Paginated A4", "Local")
                )
            ),
            DocBlock.Paragraph("All output files can be previewed in-app, exported via Storage Access Framework, or shared directly.")
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
                    listOf("Security", "Local Audits", "16000", "15400", "+600", "On Track"),
                    listOf("Operations", "Build Pipeline", "22500", "20100", "+2400", "Under")
                )
            )
        )
    )

    private fun generateSamplePng(file: File) {
        val w = 1600
        val h = 1000
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(Color.parseColor("#141923"), Color.parseColor("#2457F5"), Color.parseColor("#E85D2A")),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(42, 255, 255, 255)
        }
        canvas.drawRoundRect(120f, 140f, 1480f, 860f, 48f, 48f, cardPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 92f
            isFakeBoldText = true
        }
        canvas.drawText("CONVERT FLOW", 220f, 430f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCE5FF")
            textSize = 42f
        }
        canvas.drawText("1600 x 1000 PNG • Local Compression & Resize Sample", 220f, 520f, subPaint)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F9D6E")
        }
        canvas.drawCircle(1320f, 310f, 95f, accentPaint)

        FileOutputStream(file).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bmp.recycle()
    }
}
