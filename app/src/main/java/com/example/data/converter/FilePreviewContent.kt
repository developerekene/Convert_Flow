package com.example.data.converter

import java.io.File

sealed interface FilePreviewContent {
    data class PdfPages(val pageCount: Int, val file: File) : FilePreviewContent
    data class ImageFile(val file: File, val width: Int, val height: Int) : FilePreviewContent
    data class TableGrid(val sheetName: String, val rows: List<List<String>>) : FilePreviewContent
    data class DocumentBlocks(val blocks: List<DocBlock>) : FilePreviewContent
    data class PlainCode(val text: String) : FilePreviewContent
    data class ZipContents(val entries: List<ZipEntryInfo>) : FilePreviewContent
    data class Unavailable(val reason: String) : FilePreviewContent
}
