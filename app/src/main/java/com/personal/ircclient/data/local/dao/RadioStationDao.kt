package com.personal.ircclient.data.local.dao

import androidx.room.*
import com.personal.ircclient.data.local.entities.RadioStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {
    @Query("SELECT * FROM radio_stations")
    fun getAll(): Flow<List<RadioStationEntity>>

    @Query("SELECT COUNT(*) FROM radio_stations")
    suspend fun getCount(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: RadioStationEntity)

    @Delete
    suspend fun delete(station: RadioStationEntity)
}
