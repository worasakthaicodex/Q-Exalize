package com.petcompanion.domain.model

enum class GameMode(val displayName: String) {
    MUSIC_KEYS("โน้ตดนตรี"),
    PIANO_KEYS("เปียโนคีย์")
}

data class GameLevelConfig(
    val id: Int,
    val name: String,
    val stars: Int = 0,               // ดาวที่เคยได้ดีสุด (จาก DB, ไม่ใช่ preset)
    val isLocked: Boolean = false,    // ล็อคหรือไม่ (คำนวณจาก progress)
    val timeSeconds: Int = 60,
    val mode: GameMode = GameMode.MUSIC_KEYS,
    val wrongPenalty: Int = 5,        // หักคะแนนเมื่อตอบผิด
    val comboThreshold: Int = 0,      // ตอบถูกติดกันกี่ครั้งถึงได้ combo (0 = ปิด)
    val comboBonus: Int = 0,          // คะแนนพิเศษเมื่อ combo
    val isBuiltIn: Boolean = true,
    val star3Score: Int = 100,        // คะแนนที่ได้ 3 ดาว
    val star2Score: Int = 60,         // คะแนนที่ได้ 2 ดาว
    val star1Score: Int = 20,         // คะแนนที่ได้ 1 ดาว (= ผ่านด่าน)
    val useAccidentals: Boolean = true // ใช้โน้ต C# D# F# G# A# ด้วยไหม (เฉพาะ MUSIC_KEYS)
) {
    /** คำนวณดาวที่ได้จากคะแนน */
    fun starsForScore(score: Int): Int = when {
        score >= star3Score -> 3
        score >= star2Score -> 2
        score >= star1Score -> 1
        else -> 0
    }
}

/** 8 ด่านมาตรฐาน ไล่ระดับความยาก */
val BUILT_IN_LEVELS = listOf(
    // 60 วินาที — ง่าย (ไม่มีโน้ตยาก)
    GameLevelConfig(1, "เริ่มต้น",   timeSeconds=60, mode=GameMode.MUSIC_KEYS, wrongPenalty=0,  comboThreshold=0, comboBonus=0,  star3Score=80,  star2Score=40, star1Score=10, useAccidentals=false),
    GameLevelConfig(2, "ฝึกหัด",    timeSeconds=60, mode=GameMode.MUSIC_KEYS, wrongPenalty=3,  comboThreshold=0, comboBonus=0,  star3Score=80,  star2Score=40, star1Score=10, useAccidentals=false),
    GameLevelConfig(3, "พื้นฐาน",   timeSeconds=60, mode=GameMode.MUSIC_KEYS, wrongPenalty=5,  comboThreshold=2, comboBonus=5,  star3Score=90,  star2Score=50, star1Score=15, useAccidentals=false),
    // 45 วินาที — ปานกลาง
    GameLevelConfig(4, "เรียนคีย์",  timeSeconds=45, mode=GameMode.PIANO_KEYS, wrongPenalty=5,  comboThreshold=0, comboBonus=0,  star3Score=70,  star2Score=40, star1Score=10, useAccidentals=false),
    GameLevelConfig(5, "ผสมเสียง",  timeSeconds=45, mode=GameMode.MUSIC_KEYS, wrongPenalty=5,  comboThreshold=2, comboBonus=8,  star3Score=80,  star2Score=45, star1Score=15, useAccidentals=true),
    // 30 วินาที — ยาก (มีโน้ตยาก)
    GameLevelConfig(6, "ความเร็ว",  timeSeconds=30, mode=GameMode.MUSIC_KEYS, wrongPenalty=10, comboThreshold=3, comboBonus=15, star3Score=60,  star2Score=35, star1Score=10, useAccidentals=true),
    GameLevelConfig(7, "คีย์เร็ว",  timeSeconds=30, mode=GameMode.PIANO_KEYS, wrongPenalty=10, comboThreshold=2, comboBonus=10, star3Score=60,  star2Score=35, star1Score=10, useAccidentals=false),
    GameLevelConfig(8, "ผู้เชี่ยวชาญ", timeSeconds=30, mode=GameMode.PIANO_KEYS, wrongPenalty=15, comboThreshold=3, comboBonus=20, star3Score=60, star2Score=35, star1Score=10, useAccidentals=false),
)
