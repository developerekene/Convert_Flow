package com.example.data.local

import kotlinx.coroutines.flow.Flow
import java.io.File

class HistoryRepository(private val dao: ConversionDao) {
    val records: Flow<List<ConversionRecord>> = dao.observeAllRecords()

    suspend fun getById(id: Long): ConversionRecord? = dao.getRecordById(id)

    suspend fun insert(record: ConversionRecord): Long = dao.insertRecord(record)

    suspend fun delete(record: ConversionRecord) {
        runCatching { File(record.outputPath).delete() }
        dao.deleteRecordById(record.id)
    }

    suspend fun clearAll(convertedDir: File) {
        runCatching {
            convertedDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        dao.clearAll()
    }
}
