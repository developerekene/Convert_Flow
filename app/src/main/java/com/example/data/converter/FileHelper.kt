package com.example.data.converter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.domain.model.FileFormat
import com.example.domain.model.PickedFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FileHelper {

    fun convertedDir(context: Context): File =
        File(context.filesDir, "converted").apply { mkdirs() }

    fun inspectUri(context: Context, uri: Uri): PickedFile? {
        val resolver = context.contentResolver
        var displayName = "document"
        var sizeBytes = 0L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                }
            }

        if (displayName == "document") {
            uri.lastPathSegment?.substringAfterLast('/')?.let { if (it.isNotBlank()) displayName = it }
        }

        val mime = resolver.getType(uri)
        val extFromName = displayName.substringAfterLast('.', "")
        val format = FileFormat.fromExtension(extFromName)
            ?: FileFormat.fromMimeType(mime)
            ?: return null

        val tempFile = copyUriToTemp(context, uri, "inspect_${System.nanoTime()}.${format.ext}")
        return try {
            if (sizeBytes <= 0L) sizeBytes = tempFile.length()
            var pageCount: Int? = null
            var widthPx: Int? = null
            var heightPx: Int? = null
            var sheetCount: Int? = null

            when (format.category) {
                com.example.domain.model.FormatCategory.IMAGE -> {
                    val dims = ImageEngine.inspectDimensions(tempFile)
                    widthPx = dims?.first
                    heightPx = dims?.second
                }
                com.example.domain.model.FormatCategory.DOCUMENT -> {
                    if (format == FileFormat.PDF) {
                        pageCount = PdfEngine.getPageCount(tempFile)
                    }
                }
                com.example.domain.model.FormatCategory.SPREADSHEET -> {
                    if (format == FileFormat.XLSX) {
                        sheetCount = runCatching { OoxmlSpreadsheetHandler.readXlsx(tempFile).sheets.size }.getOrNull()
                    }
                }
                else -> Unit
            }

            PickedFile(
                uri = uri,
                displayName = displayName,
                format = format,
                sizeBytes = sizeBytes,
                pageCount = pageCount,
                widthPx = widthPx,
                heightPx = heightPx,
                sheetCount = sheetCount
            )
        } finally {
            tempFile.delete()
        }
    }

    fun copyUriToTemp(context: Context, uri: Uri, tempName: String): File {
        val dir = File(context.cacheDir, "inputs").apply { mkdirs() }
        val out = File(dir, tempName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        } ?: error("Unable to open selected file stream.")
        return out
    }

    fun createUniqueOutputFile(context: Context, baseName: String, ext: String): File {
        val cleanBase = baseName.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "konvertibles" }
        val dir = convertedDir(context)
        var candidate = File(dir, "$cleanBase.$ext")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(dir, "${cleanBase}_$counter.$ext")
            counter++
        }
        return candidate
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share ${file.name}")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun openExternally(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { shareFile(context, file, mimeType) }
    }

    fun copyFileToUri(context: Context, source: File, destUri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                FileInputStream(source).use { input -> input.copyTo(out) }
            } != null
        }.getOrDefault(false)
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return if (digitGroups == 0) {
            "$bytes B"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        }
    }

    fun calculateConvertedStorageBytes(context: Context): Long {
        return convertedDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
