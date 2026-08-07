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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)
}
