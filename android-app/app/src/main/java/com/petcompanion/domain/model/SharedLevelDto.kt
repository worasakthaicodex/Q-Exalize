package com.petcompanion.domain.model

/**
 * DTO สำหรับแชร์ด่านผ่าน QR Code (แปลงเป็น JSON แล้ว encode เป็น QR)
 * ไม่มี id หรือข้อมูลส่วนตัว ส่งได้อย่างปลอดภัย
 */
data class SharedLevelDto(
    val name: String,
    val timeSeconds: Int = 60,
    val mode: String = "MUSIC_KEYS",
    val wrongPenalty: Int = 5,
    val comboThreshold: Int = 0,
    val comboBonus: Int = 0,
    val star1Score: Int = 20,
    val star2Score: Int = 60,
    val star3Score: Int = 100,
    val useAccidentals: Boolean = true,
    val schemaVersion: Int = 1          // สำหรับ backward compat ในอนาคต
)

/** แปลง Entity → DTO สำหรับส่งออก */
fun CustomLevelEntity.toSharedDto() = SharedLevelDto(
    name           = name,
    timeSeconds    = timeSeconds,
    mode           = mode,
    wrongPenalty   = wrongPenalty,
    comboThreshold = comboThreshold,
    comboBonus     = comboBonus,
    star1Score     = star1Score,
    star2Score     = star2Score,
    star3Score     = star3Score,
    useAccidentals = useAccidentals
)

/** แปลง DTO → Entity สำหรับบันทึก (id=0 เพื่อให้ Room auto-generate) */
fun SharedLevelDto.toEntity() = CustomLevelEntity(
    id             = 0,
    name           = name,
    timeSeconds    = timeSeconds,
    mode           = mode,
    wrongPenalty   = wrongPenalty,
    comboThreshold = comboThreshold,
    comboBonus     = comboBonus,
    star1Score     = star1Score,
    star2Score     = star2Score,
    star3Score     = star3Score,
    useAccidentals = useAccidentals
)
