package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_records")
data class ConversionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceName: String,
    val sourceExtension: String,
    val outputName: String,
    val outputExtension: String,
    val outputPath: String,
    val category: String,
    val operationLabel: String,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val summary: String,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val savingsPercent: Int
        get() = if (originalSizeBytes <= 0L) 0
        else (((originalSizeBytes - outputSizeBytes).toDouble() / originalSizeBytes.toDouble()) * 100.0).toInt()
}
