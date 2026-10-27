package com.petcompanion.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "quiz_history",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class QuizHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseId: Int,
    val scorePercentage: Int,
    val correctCount: Int,
    val totalQuestions: Int,
    val timeSpentSeconds: Int,
    val attemptNumber: Int, // รอบที่เท่าไหร่ของวิชานี้
    val isPassed: Boolean,
    val closeAnswersCount: Int, // จำนวนข้อที่ตอบ "เกือบถูก"
    val isTimeout: Boolean = false, // 📍 เพิ่มตัวนี้เข้าไปครับ!
    val timestamp: Long = System.currentTimeMillis(),
    val selectedAnswers: Map<Int, Int> = emptyMap()
)