package com.petcompanion.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = QuizCategoryEntity::class,
            parentColumns = ["id"],          // ID ของตาราง Category
            childColumns = ["categoryId"],   // ตรงกับตัวแปร categoryId ในตารางนี้
            onDelete = ForeignKey.CASCADE    // 📍 ถ้าลบหมวดหมู่ปุ๊บ วิชาในหมวดนี้จะโดนลบทิ้งอัตโนมัติ!
        )
    ],
    indices = [Index("categoryId")] // สร้าง Index ให้ค้นหาวิชาตามหมวดหมู่ได้เร็วปรื๊ด
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val categoryId: Int,
    val courseCode: String,
    val title: String,
    val description: String,
    val timeLimit: Int,
    val passingScore: Int,
    val totalQuestions: Int = 0,

    // 📍 ใส่ค่า Default ไว้ตรงนี้เลยครับ!
    val questionsToUse: Int = 20,           // ค่าเริ่มต้นสอบ 20 ข้อ
    val isShuffleOptions: Boolean = true,   // สุ่มตัวเลือกเป็นปกติ
    val isPreventScreenshot: Boolean = false,
    val isAntiCheat: Boolean = false,
    val isAllowReview: Boolean = true,
    val isAllowRetry: Boolean = true,
    val gradingConfigJson: String = "",     // เว้นว่างไว้ก่อน
    val chapterRandomConfigJson: String = "",

    val progress: Float = 0f,
    val latestScore: Int? = null,

    // ล็อกจากระบบ share — ป้องกันการ export/แชร์ต่อ
    val isLocked: Boolean = false
)