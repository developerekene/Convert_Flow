package com.devekene.konvertibles.domain.model

enum class ConversionGroup(val label: String) {
    ALL("All"),
    PDF("PDF"),
    DOCUMENTS("Word & Text"),
    SPREADSHEETS("Spreadsheets"),
    IMAGES("Images"),
    OPTIMIZE("Compress & Resize"),
    ARCHIVES("Archives")
}

enum class PipelineMode {
    CONVERT,
    COMPRESS_IMAGE,
    RESIZE_IMAGE,
    COMPRESS_PDF,
    RESIZE_PDF,
    MERGE_PDF,
    SPLIT_PDF,
    EXTRACT_ZIP
}

data class ConversionPipeline(
    val id: String,
    val title: String,
    val subtitle: String,
    val sourceFormats: Set<FileFormat>,
    val targetFormat: FileFormat,
    val group: ConversionGroup,
    val mode: PipelineMode = PipelineMode.CONVERT,
    val allowMultiple: Boolean = false,
    val usePhotoPicker: Boolean = false
) {
    val allowedSourceLabel: String
        get() = sourceFormats.joinToString(" / ") { ".${it.ext.uppercase()}" }
}

object ConversionCatalog {

    val allPipelines: List<ConversionPipeline> = listOf(
        // --- PDF CONVERSIONS (12) ---
        ConversionPipeline("pdf_to_docx", "PDF → Word", "Extract PDF text into an editable .docx", setOf(FileFormat.PDF), FileFormat.DOCX, ConversionGroup.PDF),
        ConversionPipeline("pdf_to_txt", "PDF → Text", "Extract clean plain text from PDF", setOf(FileFormat.PDF), FileFormat.TXT, ConversionGroup.PDF),
        ConversionPipeline("pdf_to_md", "PDF → Markdown", "Convert PDF structure into Markdown", setOf(FileFormat.PDF), FileFormat.MD, ConversionGroup.PDF),
        ConversionPipeline("pdf_to_html", "PDF → HTML", "Convert PDF document to a web page", setOf(FileFormat.PDF), FileFormat.HTML, ConversionGroup.PDF),
        ConversionPipeline("pdf_to_png", "PDF → PNG", "Render every PDF page as crisp PNG", setOf(FileFormat.PDF), FileFormat.PNG, ConversionGroup.PDF),
        ConversionPipeline("pdf_to_jpg", "PDF → JPG", "Render PDF pages as compact JPGs", setOf(FileFormat.PDF), FileFormat.JPG, ConversionGroup.PDF),
        ConversionPipeline("pdf_to_webp", "PDF → WEBP", "Render PDF pages as WEBP images", setOf(FileFormat.PDF), FileFormat.WEBP, ConversionGroup.PDF),
        ConversionPipeline("compress_pdf", "Compress PDF", "Shrink PDF size by 72/150/300 DPI", setOf(FileFormat.PDF), FileFormat.PDF, ConversionGroup.OPTIMIZE, mode = PipelineMode.COMPRESS_PDF),
        ConversionPipeline("resize_pdf", "Resize PDF Pages", "Normalize pages to A4, Letter, or Legal", setOf(FileFormat.PDF), FileFormat.PDF, ConversionGroup.OPTIMIZE, mode = PipelineMode.RESIZE_PDF),
        ConversionPipeline("merge_pdf", "Merge PDFs", "Combine multiple PDFs into one file", setOf(FileFormat.PDF), FileFormat.PDF, ConversionGroup.PDF, mode = PipelineMode.MERGE_PDF, allowMultiple = true),
        ConversionPipeline("split_pdf", "Extract PDF Pages", "Save chosen page ranges to a new PDF", setOf(FileFormat.PDF), FileFormat.PDF, ConversionGroup.PDF, mode = PipelineMode.SPLIT_PDF),

        // --- WORD & TEXT CONVERSIONS (15) ---
        ConversionPipeline("docx_to_pdf", "Word → PDF", "Typeset .docx document into a crisp PDF", setOf(FileFormat.DOCX), FileFormat.PDF, ConversionGroup.DOCUMENTS),
        ConversionPipeline("docx_to_txt", "Word → Text", "Strip formatting from .docx to .txt", setOf(FileFormat.DOCX), FileFormat.TXT, ConversionGroup.DOCUMENTS),
        ConversionPipeline("docx_to_md", "Word → Markdown", "Convert .docx headings & lists to .md", setOf(FileFormat.DOCX), FileFormat.MD, ConversionGroup.DOCUMENTS),
        ConversionPipeline("docx_to_html", "Word → HTML", "Export .docx document as styled HTML", setOf(FileFormat.DOCX), FileFormat.HTML, ConversionGroup.DOCUMENTS),
        ConversionPipeline("txt_to_pdf", "Text → PDF", "Turn plain .txt notes into a paginated PDF", setOf(FileFormat.TXT), FileFormat.PDF, ConversionGroup.DOCUMENTS),
        ConversionPipeline("txt_to_docx", "Text → Word", "Package .txt notes into a .docx file", setOf(FileFormat.TXT), FileFormat.DOCX, ConversionGroup.DOCUMENTS),
        ConversionPipeline("txt_to_md", "Text → Markdown", "Format .txt text as Markdown", setOf(FileFormat.TXT), FileFormat.MD, ConversionGroup.DOCUMENTS),
        ConversionPipeline("txt_to_html", "Text → HTML", "Wrap .txt file into clean HTML", setOf(FileFormat.TXT), FileFormat.HTML, ConversionGroup.DOCUMENTS),
        ConversionPipeline("md_to_pdf", "Markdown → PDF", "Render Markdown headings & lists to PDF", setOf(FileFormat.MD), FileFormat.PDF, ConversionGroup.DOCUMENTS),
        ConversionPipeline("md_to_docx", "Markdown → Word", "Convert Markdown into formatted .docx", setOf(FileFormat.MD), FileFormat.DOCX, ConversionGroup.DOCUMENTS),
        ConversionPipeline("md_to_html", "Markdown → HTML", "Compile Markdown into styled HTML", setOf(FileFormat.MD), FileFormat.HTML, ConversionGroup.DOCUMENTS),
        ConversionPipeline("md_to_txt", "Markdown → Text", "Remove Markdown syntax to plain text", setOf(FileFormat.MD), FileFormat.TXT, ConversionGroup.DOCUMENTS),
        ConversionPipeline("html_to_pdf", "HTML → PDF", "Convert HTML page content into PDF", setOf(FileFormat.HTML), FileFormat.PDF, ConversionGroup.DOCUMENTS),
        ConversionPipeline("html_to_docx", "HTML → Word", "Extract HTML content into .docx", setOf(FileFormat.HTML), FileFormat.DOCX, ConversionGroup.DOCUMENTS),
        ConversionPipeline("html_to_md", "HTML → Markdown", "Convert HTML tags into Markdown", setOf(FileFormat.HTML), FileFormat.MD, ConversionGroup.DOCUMENTS),
        ConversionPipeline("html_to_txt", "HTML → Text", "Strip HTML tags into clean plain text", setOf(FileFormat.HTML), FileFormat.TXT, ConversionGroup.DOCUMENTS),

        // --- SPREADSHEET & DATA CONVERSIONS (20) ---
        ConversionPipeline("xlsx_to_csv", "Excel → CSV", "Export .xlsx worksheet as CSV", setOf(FileFormat.XLSX), FileFormat.CSV, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("xlsx_to_tsv", "Excel → TSV", "Export .xlsx worksheet as tab-separated", setOf(FileFormat.XLSX), FileFormat.TSV, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("xlsx_to_json", "Excel → JSON", "Transform .xlsx rows into JSON objects", setOf(FileFormat.XLSX), FileFormat.JSON, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("xlsx_to_xml", "Excel → XML", "Export .xlsx table into structured XML", setOf(FileFormat.XLSX), FileFormat.XML, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("xlsx_to_html", "Excel → HTML", "Render .xlsx workbook as HTML table", setOf(FileFormat.XLSX), FileFormat.HTML, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("xlsx_to_pdf", "Excel → PDF", "Paginate .xlsx workbook into a PDF table", setOf(FileFormat.XLSX), FileFormat.PDF, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("csv_to_xlsx", "CSV → Excel", "Build a styled .xlsx workbook from CSV", setOf(FileFormat.CSV), FileFormat.XLSX, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("csv_to_tsv", "CSV → TSV", "Convert comma-separated to tab-separated", setOf(FileFormat.CSV), FileFormat.TSV, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("csv_to_json", "CSV → JSON", "Convert CSV rows into JSON array", setOf(FileFormat.CSV), FileFormat.JSON, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("csv_to_xml", "CSV → XML", "Convert CSV rows into XML records", setOf(FileFormat.CSV), FileFormat.XML, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("csv_to_html", "CSV → HTML", "Format CSV data as an HTML table", setOf(FileFormat.CSV), FileFormat.HTML, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("csv_to_pdf", "CSV → PDF", "Render CSV data as a clean PDF table", setOf(FileFormat.CSV), FileFormat.PDF, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("tsv_to_xlsx", "TSV → Excel", "Build an .xlsx workbook from TSV", setOf(FileFormat.TSV), FileFormat.XLSX, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("tsv_to_csv", "TSV → CSV", "Convert tab-separated to CSV", setOf(FileFormat.TSV), FileFormat.CSV, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("tsv_to_json", "TSV → JSON", "Transform TSV rows into JSON", setOf(FileFormat.TSV), FileFormat.JSON, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("tsv_to_pdf", "TSV → PDF", "Render TSV table into a PDF", setOf(FileFormat.TSV), FileFormat.PDF, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("json_to_csv", "JSON → CSV", "Flatten JSON array into CSV columns", setOf(FileFormat.JSON), FileFormat.CSV, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("json_to_xlsx", "JSON → Excel", "Convert JSON dataset into .xlsx", setOf(FileFormat.JSON), FileFormat.XLSX, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("json_to_xml", "JSON → XML", "Convert JSON records into XML", setOf(FileFormat.JSON), FileFormat.XML, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("json_to_pdf", "JSON → PDF", "Render JSON dataset as a PDF table", setOf(FileFormat.JSON), FileFormat.PDF, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("xml_to_json", "XML → JSON", "Convert XML records into JSON array", setOf(FileFormat.XML), FileFormat.JSON, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("xml_to_csv", "XML → CSV", "Extract XML records into CSV columns", setOf(FileFormat.XML), FileFormat.CSV, ConversionGroup.SPREADSHEETS),
        ConversionPipeline("xml_to_xlsx", "XML → Excel", "Convert XML records into .xlsx", setOf(FileFormat.XML), FileFormat.XLSX, ConversionGroup.SPREADSHEETS),

        // --- IMAGE CONVERSIONS & OPTIMIZATION (16) ---
        ConversionPipeline("images_to_pdf", "Images → PDF", "Combine one or more photos into a PDF", setOf(FileFormat.PNG, FileFormat.JPG, FileFormat.WEBP, FileFormat.BMP), FileFormat.PDF, ConversionGroup.IMAGES, allowMultiple = true, usePhotoPicker = true),
        ConversionPipeline("png_to_jpg", "PNG → JPG", "Convert PNG to compact JPG", setOf(FileFormat.PNG), FileFormat.JPG, ConversionGroup.IMAGES),
        ConversionPipeline("png_to_webp", "PNG → WEBP", "Convert PNG to modern WEBP", setOf(FileFormat.PNG), FileFormat.WEBP, ConversionGroup.IMAGES),
        ConversionPipeline("png_to_bmp", "PNG → BMP", "Convert PNG to uncompressed BMP", setOf(FileFormat.PNG), FileFormat.BMP, ConversionGroup.IMAGES),
        ConversionPipeline("jpg_to_png", "JPG → PNG", "Convert JPG photo to lossless PNG", setOf(FileFormat.JPG), FileFormat.PNG, ConversionGroup.IMAGES),
        ConversionPipeline("jpg_to_webp", "JPG → WEBP", "Convert JPG to smaller WEBP", setOf(FileFormat.JPG), FileFormat.WEBP, ConversionGroup.IMAGES),
        ConversionPipeline("jpg_to_bmp", "JPG → BMP", "Convert JPG to 24-bit BMP", setOf(FileFormat.JPG), FileFormat.BMP, ConversionGroup.IMAGES),
        ConversionPipeline("webp_to_png", "WEBP → PNG", "Convert WEBP to lossless PNG", setOf(FileFormat.WEBP), FileFormat.PNG, ConversionGroup.IMAGES),
        ConversionPipeline("webp_to_jpg", "WEBP → JPG", "Convert WEBP to universal JPG", setOf(FileFormat.WEBP), FileFormat.JPG, ConversionGroup.IMAGES),
        ConversionPipeline("webp_to_bmp", "WEBP → BMP", "Convert WEBP to 24-bit BMP", setOf(FileFormat.WEBP), FileFormat.BMP, ConversionGroup.IMAGES),
        ConversionPipeline("bmp_to_png", "BMP → PNG", "Compress BMP bitmap to PNG", setOf(FileFormat.BMP), FileFormat.PNG, ConversionGroup.IMAGES),
        ConversionPipeline("bmp_to_jpg", "BMP → JPG", "Compress BMP bitmap to JPG", setOf(FileFormat.BMP), FileFormat.JPG, ConversionGroup.IMAGES),
        ConversionPipeline("bmp_to_webp", "BMP → WEBP", "Compress BMP bitmap to WEBP", setOf(FileFormat.BMP), FileFormat.WEBP, ConversionGroup.IMAGES),
        ConversionPipeline("compress_image", "Compress Image", "Shrink PNG, JPG, or WEBP to target KB", setOf(FileFormat.PNG, FileFormat.JPG, FileFormat.WEBP), FileFormat.JPG, ConversionGroup.OPTIMIZE, mode = PipelineMode.COMPRESS_IMAGE, usePhotoPicker = true),
        ConversionPipeline("resize_image", "Resize Image", "Scale by %, HD/Square preset, or exact px", setOf(FileFormat.PNG, FileFormat.JPG, FileFormat.WEBP, FileFormat.BMP), FileFormat.PNG, ConversionGroup.OPTIMIZE, mode = PipelineMode.RESIZE_IMAGE, usePhotoPicker = true),

        // --- ARCHIVES (2) ---
        ConversionPipeline("pack_zip", "Create ZIP Archive", "Bundle one or more files into a .zip", FileFormat.entries.filter { it != FileFormat.ZIP }.toSet(), FileFormat.ZIP, ConversionGroup.ARCHIVES, allowMultiple = true),
        ConversionPipeline("extract_zip", "Extract ZIP Archive", "Unpack files from a .zip archive", setOf(FileFormat.ZIP), FileFormat.ZIP, ConversionGroup.ARCHIVES, mode = PipelineMode.EXTRACT_ZIP)
    )

    fun byId(id: String): ConversionPipeline? = allPipelines.firstOrNull { it.id == id }
}
