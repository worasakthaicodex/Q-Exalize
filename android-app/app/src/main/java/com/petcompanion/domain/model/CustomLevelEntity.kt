package com.petcompanion.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_levels")
data class CustomLevelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val timeSeconds: Int = 60,
    val mode: String = "MUSIC_KEYS",   // serialized GameMode
    val wrongPenalty: Int = 5,
    val comboThreshold: Int = 0,
    val comboBonus: Int = 0,
    val star3Score: Int = 100,
    val star2Score: Int = 60,
    val star1Score: Int = 20,
    val useAccidentals: Boolean = true  // ใช้โน้ต C# D# F# G# A# หรือเปล่า
)

/** แปลง Entity → GameLevelConfig */
fun CustomLevelEntity.toConfig(): GameLevelConfig = GameLevelConfig(
    id             = 1000 + id,
    name           = name,
    timeSeconds    = timeSeconds,
    mode           = if (mode == "PIANO_KEYS") GameMode.PIANO_KEYS else GameMode.MUSIC_KEYS,
    wrongPenalty   = wrongPenalty,
    comboThreshold = comboThreshold,
    comboBonus     = comboBonus,
    star3Score     = star3Score,
    star2Score     = star2Score,
    star1Score     = star1Score,
    useAccidentals = useAccidentals,
    isBuiltIn      = false
)
