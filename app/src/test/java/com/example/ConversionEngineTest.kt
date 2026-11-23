package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.converter.DocBlock
import com.example.data.converter.DocxDocument
import com.example.data.converter.ImageEngine
import com.example.data.converter.OoxmlSpreadsheetHandler
import com.example.data.converter.OoxmlWordHandler
import com.example.data.converter.PdfEngine
import com.example.data.converter.SampleFileGenerator
import com.example.data.converter.SheetData
import com.example.data.converter.SpreadsheetData
import com.example.data.converter.ZipEngine
import com.example.domain.model.ConversionCatalog
import com.example.domain.model.ConversionOptions
import com.example.domain.model.FileFormat
import com.example.domain.model.PdfPageSize
import com.example.domain.model.ResizeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConversionEngineTest {

    @Test
    fun `catalog exposes at least 30 distinct conversions with strict source filters`() {
        val pipelines = ConversionCatalog.allPipelines
        assertTrue("Expected at least 30 conversions, got ${pipelines.size}", pipelines.size >= 30)
        pipelines.forEach { pipeline ->
            assertTrue(pipeline.sourceFormats.isNotEmpty())
            assertTrue(FileFormat.mimeFilterFor(pipeline.sourceFormats).isNotEmpty())
        }
    }

    @Test
    fun `docx write and read preserves headings bullets and tables`() {
        val tempFile = File.createTempFile("test_doc", ".docx")
        try {
            val doc = DocxDocument(
                title = "Brief",
                blocks = listOf(
                    DocBlock.Heading(1, "Konvertibles Report"),
                    DocBlock.Paragraph("On-device conversion summary."),
                    DocBlock.BulletItem("Fast pocket-ready processing"),
                    DocBlock.Table(listOf(listOf("Format", "Support"), listOf("PDF", "Full")))
                )
            )
            OoxmlWordHandler.writeDocx(doc, tempFile)
            assertTrue(tempFile.length() > 200L)

            val parsed = OoxmlWordHandler.readDocx(tempFile, "Brief")
            assertEquals(4, parsed.blocks.size)
            assertTrue(parsed.blocks[0] is DocBlock.Heading)
            assertEquals("Konvertibles Report", (parsed.blocks[0] as DocBlock.Heading).text)
            assertTrue(parsed.blocks[3] is DocBlock.Table)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `xlsx csv json xml round trip preserves cells`() {
        val xlsxFile = File.createTempFile("sheet", ".xlsx")
        try {
            val data = SpreadsheetData(
                listOf(
                    SheetData(
                        "Sales",
                        listOf(
                            listOf("Item", "Qty", "Price"),
                            listOf("Widget", "12", "19.5"),
                            listOf("Gadget", "4", "99")
                        )
                    )
                )
            )
            OoxmlSpreadsheetHandler.writeXlsx(data, xlsxFile)
            val readBack = OoxmlSpreadsheetHandler.readXlsx(xlsxFile)
            assertEquals(3, readBack.sheets.first().rows.size)
            assertEquals("Widget", readBack.sheets.first().rows[1][0])
            assertEquals("19.5", readBack.sheets.first().rows[1][2])

            val json = OoxmlSpreadsheetHandler.toJson(readBack)
            val fromJson = OoxmlSpreadsheetHandler.readJsonTable(json, "JSON")
            assertEquals(3, fromJson.sheets.first().rows.size)
            assertEquals("Gadget", fromJson.sheets.first().rows[2][0])

            val xml = OoxmlSpreadsheetHandler.toXml(readBack)
            val fromXml = OoxmlSpreadsheetHandler.readXmlTable(xml, "XML")
            assertEquals(3, fromXml.sheets.first().rows.size)
            assertEquals("Widget", fromXml.sheets.first().rows[1][0])
        } finally {
            xlsxFile.delete()
        }
    }

    @Test
    fun `image resize and convert to jpg and bmp works`() {
        val srcPng = File.createTempFile("src_img", ".png")
        val outJpg = File.createTempFile("out_img", ".jpg")
        val outBmp = File.createTempFile("out_img", ".bmp")
        try {
            val bmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.BLUE)
            FileOutputStream(srcPng).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()

            val (w, h) = ImageEngine.convertOrCompressImage(
                srcPng,
                outJpg,
                FileFormat.JPG,
                ConversionOptions(quality = 75, resizeMode = ResizeMode.PERCENTAGE, scalePercent = 50)
            )
            assertEquals(200, w)
            assertEquals(100, h)
            assertTrue(outJpg.length() > 0L)

            val (bw, bh) = ImageEngine.convertOrCompressImage(
                srcPng,
                outBmp,
                FileFormat.BMP,
                ConversionOptions(resizeMode = ResizeMode.NONE)
            )
            assertEquals(400, bw)
            assertEquals(200, bh)
            assertTrue(outBmp.length() > 54L)
        } finally {
            srcPng.delete()
            outJpg.delete()
            outBmp.delete()
        }
    }

    @Test
    fun `zip pack inspect and extract entries`() {
        val f1 = File.createTempFile("aaa_", ".txt").apply { writeText("hello") }
        val f2 = File.createTempFile("bbb_", ".txt").apply { writeText("world!") }
        val zip = File.createTempFile("bundle", ".zip")
        val extractDir = File(zip.parentFile, "extract_test_${System.nanoTime()}")
        try {
            val count = ZipEngine.packFiles(listOf("first.txt" to f1, "second.txt" to f2), zip)
            assertEquals(2, count)
            val entries = ZipEngine.inspectZip(zip)
            assertEquals(2, entries.size)
            assertEquals("first.txt", entries[0].name)

            val extracted = ZipEngine.extractZip(zip, extractDir)
            assertEquals(2, extracted.size)
            assertEquals("hello", extracted.first { it.name == "first.txt" }.readText())
        } finally {
            f1.delete()
            f2.delete()
            zip.delete()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun `sample file generator and pdf to docx round trip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = SampleFileGenerator.generateSample(context, SampleFileGenerator.SamplePreset.SALES_XLSX)
        assertTrue(uri.toString().contains("Sales_Data_Q4.xlsx"))

        val xmlUri = SampleFileGenerator.generateSampleForFormat(context, FileFormat.XML)
        assertTrue(xmlUri.toString().contains(".xml"))

        val pdfFile = File.createTempFile("doc_test", ".pdf")
        val docxFile = File.createTempFile("doc_out", ".docx")
        try {
            val pages = PdfEngine.renderDocumentToPdf(
                DocxDocument("T", listOf(DocBlock.Heading(1, "Quarterly Summary"), DocBlock.Paragraph("Body text content for PDF extraction."))),
                pdfFile,
                PdfPageSize.A4
            )
            assertEquals(1, pages)
            assertTrue(pdfFile.length() > 100L)

            val extracted = PdfEngine.extractTextToDocxDocument(pdfFile, "Extracted")
            OoxmlWordHandler.writeDocx(extracted, docxFile)
            val readDocx = OoxmlWordHandler.readDocx(docxFile, "Extracted")
            assertTrue(readDocx.blocks.any { block ->
                when (block) {
                    is DocBlock.Heading -> block.text.contains("Quarterly Summary")
                    is DocBlock.Paragraph -> block.text.contains("Body text content")
                    else -> false
                }
            })
        } finally {
            pdfFile.delete()
            docxFile.delete()
        }
    }
}
