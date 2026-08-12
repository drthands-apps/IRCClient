package com.personal.ircclient.data.local.dao

import androidx.room.*
import com.personal.ircclient.data.local.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE serverId = :serverId")
    fun getUsersForServer(serverId: Long): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE nickname = :nickname AND serverId = :serverId")
    suspend fun getUser(nickname: String, serverId: Long): UserEntity?

    @Query("SELECT * FROM users WHERE nickname = :nickname AND serverId = :serverId")
    fun getUserFlow(nickname: String, serverId: Long): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE serverId = :serverId AND isFriend = 1")
    fun getFriendsForServer(serverId: Long): Flow<List<UserEntity>>

    @Query("UPDATE users SET ignoreStatus = 'NONE' WHERE serverId = :serverId AND ignoreStatus = 'TEMPORAL'")
    suspend fun resetTemporalIgnore(serverId: Long)

    @Query("UPDATE users SET silenceStatus = 'NONE' WHERE serverId = :serverId AND silenceStatus = 'TEMPORAL'")
    suspend fun resetTemporalSilence(serverId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE serverId = :serverId AND hostmask = :hostmask AND ignoreStatus != 'NONE')")
    suspend fun isHostmaskIgnored(serverId: Long, hostmask: String): Boolean
}
