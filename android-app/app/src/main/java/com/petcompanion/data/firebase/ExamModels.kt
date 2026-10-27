package com.petcompanion.data.firebase

// ── ข้อสอบออนไลน์ ──────────────────────────────────────────────────────────
// Firestore: rooms/{roomId}/exams/{examId}
data class OnlineExam(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val startTime: Long = 0L,           // เริ่มสอบ (0 = เปิดทันทีที่ publish)
    val endTime: Long = 0L,             // หมดเวลา (0 = ไม่จำกัดเวลา)
    val scheduleEventId: String = "",   // ผูกกับ ScheduleEvent (ถ้ามี)
    val autoGrade: Boolean = true,      // คิดคะแนนอัตโนมัติจาก correctAnswer
    val showAnswers: Boolean = false,   // อนุญาตให้นักเรียนดูเฉลยหลังส่ง
    val isPublished: Boolean = false,   // เผยแพร่ให้นักเรียนเห็น
    val linkedCourseId: Int = 0,        // 0 = สร้างข้อถามเอง, >0 = นำเข้าจากคลังข้อสอบ (CourseEntity.id)
    val createdAt: Long = 0L
) {
    /** หมดเวลาแล้วหรือยัง */
    val isExpired: Boolean
        get() = endTime > 0L && System.currentTimeMillis() > endTime

    /** เริ่มเวลาแล้วหรือยัง */
    val hasStarted: Boolean
        get() = startTime == 0L || System.currentTimeMillis() >= startTime

    /** เปิดให้ทำสอบ = publish แล้ว + ยังไม่หมดเวลา + เริ่มแล้ว */
    val isActive: Boolean
        get() = isPublished && hasStarted && !isExpired

    /** ข้อสอบ publish แล้วแต่ยังไม่ถึงเวลาเริ่ม */
    val isUpcoming: Boolean
        get() = isPublished && !hasStarted && !isExpired

    /** มิลลิวินาทีที่เหลือก่อนหมดเวลา (0 = ไม่จำกัด / หมดแล้ว) */
    fun millisRemaining(): Long =
        if (endTime == 0L) 0L else (endTime - System.currentTimeMillis()).coerceAtLeast(0L)

    /** มิลลิวินาทีที่เหลือก่อนจะเริ่ม (0 = เริ่มแล้ว) */
    fun millisUntilStart(): Long =
        if (startTime == 0L || hasStarted) 0L
        else (startTime - System.currentTimeMillis()).coerceAtLeast(0L)
}

// ── ข้อถาม ─────────────────────────────────────────────────────────────────
// Firestore: rooms/{roomId}/exams/{examId}/questions/{questionId}
data class ExamQuestion(
    val id: String = "",
    val text: String = "",
    val type: String = TYPE_CHOICE,
    val choices: List<String> = emptyList(),   // สำหรับ TYPE_CHOICE (ปกติ 4 ข้อ)
    val correctAnswer: String = "",            // ใช้เปรียบเทียบอัตโนมัติ
    val points: Double = 1.0,
    val order: Int = 0
) {
    companion object {
        const val TYPE_CHOICE = "choice"    // ตัวเลือก
        const val TYPE_SHORT  = "short"     // เติมคำตอบสั้น
        const val TYPE_BOOL   = "bool"      // ถูก / ผิด
    }
}

// ── คำตอบนักเรียน ─────────────────────────────────────────────────────────
// Firestore: rooms/{roomId}/exams/{examId}/submissions/{deviceId}
data class ExamSubmission(
    val id: String = "",
    val examId: String = "",
    val studentName: String = "",
    val deviceId: String = "",
    val answers: Map<String, String> = emptyMap(),  // questionId → คำตอบที่เลือก
    val score: Double = 0.0,
    val maxScore: Double = 0.0,
    val submittedAt: Long = 0L,
    val isGraded: Boolean = false
) {
    val percentage: Double
        get() = if (maxScore > 0.0) (score / maxScore) * 100.0 else 0.0
}
