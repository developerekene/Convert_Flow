package com.example.domain.model

import android.net.Uri
import java.io.File

enum class PdfDpiPreset(val dpi: Int, val label: String, val subtitle: String) {
    SCREEN_72(72, "72 DPI", "Smallest file • Screen"),
    EBOOK_150(150, "150 DPI", "Balanced • E-book"),
    PRINT_300(300, "300 DPI", "Crisp • Print ready")
}

enum class PdfPageSize(val label: String, val widthPt: Int, val heightPt: Int) {
    ORIGINAL("Original", 0, 0),
    A4("A4", 595, 842),
    LETTER("US Letter", 612, 792),
    LEGAL("US Legal", 612, 1008)
}

enum class ResizeMode(val label: String) {
    NONE("Original Size"),
    PERCENTAGE("Scale %"),
    PRESET("Preset"),
    DIMENSIONS("Exact px")
}

enum class ImageResizePreset(val label: String, val maxDim: Int) {
    HD_1280("HD 1280px", 1280),
    FHD_1920("Full HD 1920px", 1920),
    SQUARE_1024("Square 1024px", 1024),
    THUMB_480("Thumb 480px", 480)
}

enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light Studio"),
    DARK("Dark Obsidian")
}

enum class StudioTool(
    val title: String,
    val subtitle: String,
    val badge: String,
    val category: FormatCategory
) {
    COMPRESS_IMAGE(
        "Compress Image",
        "Shrink JPG, PNG & WEBP with quality or target KB limit",
        "COMP",
        FormatCategory.IMAGE
    ),
    RESIZE_IMAGE(
        "Resize Image",
        "Scale by %, exact dimensions, or HD/Square presets",
        "RESZ",
        FormatCategory.IMAGE
    ),
    IMAGES_TO_PDF(
        "Photos to PDF",
        "Combine one or many images into a clean A4/Letter PDF",
        "PDF+",
        FormatCategory.IMAGE
    ),
    PDF_TO_IMAGES(
        "PDF to Images",
        "Export every PDF page as crisp PNG, JPG, or WEBP",
        "IMG",
        FormatCategory.DOCUMENT
    ),
    COMPRESS_PDF(
        "Compress PDF",
        "Reduce PDF size using 72/150/300 DPI re-encoding",
        "COMP",
        FormatCategory.DOCUMENT
    ),
    RESIZE_PDF(
        "Resize PDF Pages",
        "Normalize pages to A4, US Letter, or Legal format",
        "PAGE",
        FormatCategory.DOCUMENT
    ),
    MERGE_PDF(
        "Merge PDFs",
        "Combine two or more PDF files into one document",
        "MRG",
        FormatCategory.DOCUMENT
    ),
    SPLIT_PDF(
        "Extract PDF Pages",
        "Save specific page ranges (e.g. 1-3, 5) to a new PDF",
        "SPLT",
        FormatCategory.DOCUMENT
    ),
    SPREADSHEET_STUDIO(
        "Spreadsheet Lab",
        "Convert CSV, XLSX, TSV, JSON & PDF data tables",
        "DATA",
        FormatCategory.SPREADSHEET
    ),
    ZIP_ARCHIVE(
        "ZIP Pack & Unpack",
        "Bundle multiple files into .zip or inspect archives",
        "ZIP",
        FormatCategory.ARCHIVE
    )
}

data class PickedFile(
    val uri: Uri,
    val displayName: String,
    val format: FileFormat,
    val sizeBytes: Long,
    val pageCount: Int? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val sheetCount: Int? = null
)

data class ConversionOptions(
    val quality: Int = 85,
    val targetSizeKb: Int? = null,
    val pdfDpi: PdfDpiPreset = PdfDpiPreset.EBOOK_150,
    val pdfPageSize: PdfPageSize = PdfPageSize.A4,
    val resizeMode: ResizeMode = ResizeMode.NONE,
    val scalePercent: Int = 75,
    val resizePreset: ImageResizePreset = ImageResizePreset.FHD_1920,
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    val keepAspectRatio: Boolean = true,
    val stripExif: Boolean = true,
    val pageRange: String = "",
    val customOutputName: String = ""
)

data class ConversionProgress(
    val stepLabel: String,
    val fraction: Float
)

data class ConversionOutput(
    val recordId: Long,
    val outputFile: File,
    val outputFormat: FileFormat,
    val sourceName: String,
    val sourceFormat: FileFormat,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val durationMs: Long,
    val summary: String,
    val extraOutputFiles: List<File> = emptyList()
) {
    val savingsPercent: Int
        get() = if (originalSizeBytes <= 0L) 0
        else (((originalSizeBytes - outputSizeBytes).toDouble() / originalSizeBytes.toDouble()) * 100.0).toInt()
}

sealed interface ConversionUiState {
    data object Idle : ConversionUiState
    data class Running(val progress: ConversionProgress) : ConversionUiState
    data class Success(val output: ConversionOutput) : ConversionUiState
    data class Error(val message: String) : ConversionUiState
}
