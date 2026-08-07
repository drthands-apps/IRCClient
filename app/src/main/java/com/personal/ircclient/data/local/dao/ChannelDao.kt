package com.personal.ircclient.data.local.dao

import androidx.room.*
import com.personal.ircclient.data.local.entities.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE serverId = :serverId")
    fun getChannelsForServer(serverId: Long): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity): Long

    @Update
    suspend fun updateChannel(channel: ChannelEntity)

    @Delete
    suspend fun deleteChannel(channel: ChannelEntity)
}
