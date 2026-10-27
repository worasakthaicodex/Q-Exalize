package com.petcompanion.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey



@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"], // 📍 ผูกกับวิชา
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("courseId")]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val qid: Int = 0,
    val courseId: Int,           // 📍 ชี้ไปหาวิชา
    val chapter: String,         // บทที่ (ใช้อ้างอิงว่าข้อนี้อยู่บทไหน)
    val text: String,            // โจทย์คำถาม
    val options: List<String>,   // ตัวเลือก 3-5 ข้อ
    val correctIndex: Int,       // อินเด็กซ์ข้อที่ถูก (0, 1, 2, 3...)
    val closeIndex: Int? = null, // 📍 เพิ่มตัวนี้: เก็บตำแหน่งคำตอบใกล้เคียง (0-3) ถ้าไม่มีให้เป็น null
    val explanation: String      // คำอธิบาย/เหตุผลที่ตอบข้อนี้
)