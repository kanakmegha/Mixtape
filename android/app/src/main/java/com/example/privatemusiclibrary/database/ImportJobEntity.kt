package com.example.privatemusiclibrary.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey val id: String,
    val sourceUrl: String,
    val title: String?,
    val status: String,
    val progress: Int,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)
