package com.petcompanion.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** เก็บผลดีสุดของแต่ละด่าน */
@Entity(tableName = "level_progress")
data class LevelProgressEntity(
    @PrimaryKey val levelId: Int,
    val bestStars: Int = 0,
    val bestScore: Int = 0
)
