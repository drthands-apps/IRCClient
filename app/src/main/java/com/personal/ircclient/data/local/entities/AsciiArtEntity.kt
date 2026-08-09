package com.personal.ircclient.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ascii_art")
data class AsciiArtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val content: String, // Multi-line string
    val isPhrase: Boolean = false
)
