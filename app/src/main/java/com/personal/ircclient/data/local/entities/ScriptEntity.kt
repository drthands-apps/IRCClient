package com.personal.ircclient.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val content: String,
    val isActive: Boolean = true,
    val triggerType: String = "ALL", // ALL, INCOMING, OUTGOING
    val createdAt: Long = System.currentTimeMillis()
)
