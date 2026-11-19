package com.example.data.converter

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ZipEntryInfo(
    val name: String,
    val sizeBytes: Long
)

object ZipEngine {

    fun packFiles(files: List<Pair<String, File>>, outputZip: File): Int {
        val usedNames = mutableSetOf<String>()
        var count = 0
        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            files.forEach { (rawName, file) ->
                var entryName = rawName.substringAfterLast('/').ifBlank { file.name }
                if (!usedNames.add(entryName)) {
                    val base = entryName.substringBeforeLast('.')
                    val ext = entryName.substringAfterLast('.', "")
                    var suffix = 2
                    while (!usedNames.add(if (ext.isEmpty()) "${base}_$suffix" else "${base}_$suffix.$ext")) {
                        suffix++
                    }
                    entryName = if (ext.isEmpty()) "${base}_$suffix" else "${base}_$suffix.$ext"
                }
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).use { fis -> fis.copyTo(zos) }
                zos.closeEntry()
                count++
            }
        }
        return count
    }

    fun inspectZip(zipFile: File): List<ZipEntryInfo> {
        val list = mutableListOf<ZipEntryInfo>()
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val bytes = zis.readBytes()
                    list.add(ZipEntryInfo(entry.name, bytes.size.toLong()))
                }
                entry = zis.nextEntry
            }
        }
        return list
    }

    fun extractZip(zipFile: File, targetDir: File): List<File> {
        targetDir.mkdirs()
        val extracted = mutableListOf<File>()
        val canonicalTarget = targetDir.canonicalPath
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val safeName = entry.name.substringAfterLast('/').ifBlank { "entry_${extracted.size}" }
                    val outFile = File(targetDir, safeName)
                    if (outFile.canonicalPath.startsWith(canonicalTarget)) {
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                        extracted.add(outFile)
                    }
                }
                entry = zis.nextEntry
            }
        }
        return extracted
    }
}
