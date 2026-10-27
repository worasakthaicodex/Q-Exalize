package com.petcompanion.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class QuizCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val totalQuizzes: Int,
    val progress: Float,
    val iconName: String, // เก็บเป็นชื่อ String เพื่อให้ดึง Icon มาโชว์ได้
    val colorHex: String  // เก็บสีเป็น Hex Code เช่น "#FF0000"
)