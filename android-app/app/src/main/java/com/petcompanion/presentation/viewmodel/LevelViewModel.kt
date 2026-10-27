package com.petcompanion.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.petcompanion.data.local.LevelDao
import com.petcompanion.data.local.LevelProgressDao
import com.petcompanion.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LevelViewModel(
    private val levelDao: LevelDao,
    private val progressDao: LevelProgressDao
) : ViewModel() {

    private val gson = Gson()

    /**
     * Raw list ของ custom levels (ใช้ใน ManageScreen)
     */
    val customLevelsRaw: StateFlow<List<CustomLevelEntity>> =
        levelDao.getAllCustomLevels()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * รวม built-in + custom levels พร้อม stars/isLocked จากฐานข้อมูลจริง
     * - level แรกเสมอ unlock
     * - level N unlock ก็ต่อเมื่อ level N-1 bestStars >= 1
     */
    val allLevels: StateFlow<List<GameLevelConfig>> = combine(
        levelDao.getAllCustomLevels(),
        progressDao.getAllProgress()
    ) { customEntities, progressList ->
        val custom = customEntities.map { it.toConfig() }
        val all = BUILT_IN_LEVELS + custom
        val progressMap = progressList.associateBy { it.levelId }

        all.mapIndexed { index, config ->
            val bestStars = progressMap[config.id]?.bestStars ?: 0
            val isUnlocked = index == 0 ||
                    (progressMap[all[index - 1].id]?.bestStars ?: 0) >= 1
            config.copy(stars = bestStars, isLocked = !isUnlocked)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        // ค่าเริ่มต้น: built-in ทั้งหมด, เฉพาะด่านแรก unlock
        initialValue = BUILT_IN_LEVELS.mapIndexed { index, config ->
            config.copy(stars = 0, isLocked = index > 0)
        }
    )

    // ── CRUD custom levels ───────────────────────────────────────────────────

    fun addLevel(entity: CustomLevelEntity) {
        viewModelScope.launch { levelDao.insertLevel(entity) }
    }

    fun updateLevel(entity: CustomLevelEntity) {
        viewModelScope.launch { levelDao.updateLevel(entity) }
    }

    fun deleteLevel(config: GameLevelConfig) {
        viewModelScope.launch { levelDao.deleteLevelById(config.id - 1000) }
    }

    fun deleteLevelEntity(entity: CustomLevelEntity) {
        viewModelScope.launch { levelDao.deleteLevel(entity) }
    }

    // ── QR Share / Import ────────────────────────────────────────────────────

    /** แปลง custom level เป็น JSON string สำหรับ QR Code */
    fun toShareJson(entity: CustomLevelEntity): String =
        gson.toJson(entity.toSharedDto())

    /**
     * นำเข้าด่านจาก JSON string (จาก QR scan)
     * return true ถ้าสำเร็จ, false ถ้า JSON ผิดรูปแบบ
     */
    fun importFromJson(jsonStr: String): Boolean {
        return try {
            val dto = gson.fromJson(jsonStr, SharedLevelDto::class.java)
                ?: return false
            // ตรวจ field สำคัญ
            if (dto.name.isBlank()) return false
            viewModelScope.launch { levelDao.insertLevel(dto.toEntity()) }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── Progress ─────────────────────────────────────────────────────────────

    /** บันทึกผล (เก็บเฉพาะถ้าทำได้ดีกว่าครั้งก่อน) */
    fun saveProgress(levelId: Int, stars: Int, score: Int) {
        viewModelScope.launch {
            val prev = progressDao.getProgressForLevel(levelId)
            if (stars > (prev?.bestStars ?: -1) || score > (prev?.bestScore ?: -1)) {
                progressDao.upsertProgress(
                    LevelProgressEntity(levelId = levelId, bestStars = stars, bestScore = score)
                )
            }
        }
    }

    /** หา GameLevelConfig ถัดไปหลังจาก currentLevelId (ไม่สนว่าล็อคหรือเปล่า) */
    fun getNextLevel(currentLevelId: Int): GameLevelConfig? {
        val levels = allLevels.value
        val idx = levels.indexOfFirst { it.id == currentLevelId }
        return if (idx >= 0 && idx + 1 < levels.size) levels[idx + 1] else null
    }
}
