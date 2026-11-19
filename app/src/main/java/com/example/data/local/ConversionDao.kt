package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionDao {
    @Query("SELECT * FROM conversion_records ORDER BY createdAtMillis DESC")
    fun observeAllRecords(): Flow<List<ConversionRecord>>

    @Query("SELECT * FROM conversion_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): ConversionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ConversionRecord): Long

    @Query("DELETE FROM conversion_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM conversion_records")
    suspend fun clearAll()
}
