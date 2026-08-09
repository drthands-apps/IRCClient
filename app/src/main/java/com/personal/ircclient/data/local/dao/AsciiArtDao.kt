package com.personal.ircclient.data.local.dao

import androidx.room.*
import com.personal.ircclient.data.local.entities.AsciiArtEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AsciiArtDao {
    @Query("SELECT * FROM ascii_art ORDER BY isPhrase ASC, name ASC")
    fun getAll(): Flow<List<AsciiArtEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AsciiArtEntity): Long

    @Query("SELECT COUNT(*) FROM ascii_art")
    suspend fun getCount(): Long

    @Delete
    suspend fun delete(item: AsciiArtEntity)
}
