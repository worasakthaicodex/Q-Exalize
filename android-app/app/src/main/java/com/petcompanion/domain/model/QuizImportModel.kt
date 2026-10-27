package com.petcompanion.domain.model

data class QuizImportModel(
    val courseCode: String,
    val title: String,
    val description: String,
    val timeLimit: Int,
    val passingScore: Int,
    val questions: List<QuestionImportModel>
)

data class QuestionImportModel(
    val chapter: String,
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val closeIndex: Int? = null, // 📍 เพิ่มตัวนี้ด้วย
    val explanation: String
)