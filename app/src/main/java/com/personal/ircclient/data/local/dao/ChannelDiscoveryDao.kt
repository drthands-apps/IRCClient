package com.personal.ircclient.data.local.dao

import androidx.room.*
import com.personal.ircclient.data.local.entities.ChannelDiscoveryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDiscoveryDao {
    @Query("SELECT * FROM channel_discovery WHERE serverId = :serverId")
    fun getDiscoveredChannels(serverId: Long): Flow<List<ChannelDiscoveryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscovered(channel: ChannelDiscoveryEntity)

    @Query("DELETE FROM channel_discovery WHERE serverId = :serverId")
    suspend fun clearDiscovered(serverId: Long)
}
