package com.petcompanion.data.firebase

// ── ห้องเรียน ──────────────────────────────────────────────────────────────
data class ClassRoom(
    val id: String = "",
    val name: String = "",
    val teacherName: String = "",
    val description: String = "",
    val createdAt: Long = 0L
)

// ── ตารางงาน / กิจกรรม ────────────────────────────────────────────────────
data class ScheduleEvent(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val dateTime: Long = 0L,          // เวลาเริ่ม (epoch millis)
    val endTime: Long = 0L,           // เวลาสิ้นสุด (0 = ไม่กำหนด)
    val type: String = TYPE_EVENT,    // event | exam | homework | holiday
    val notifyBeforeMinutes: Int = 30
) {
    companion object {
        const val TYPE_EVENT    = "event"
        const val TYPE_EXAM     = "exam"
        const val TYPE_HOMEWORK = "homework"
        const val TYPE_HOLIDAY  = "holiday"
    }
}

// ── การบ้าน ───────────────────────────────────────────────────────────────
data class HomeworkItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val dueDate: Long = 0L,           // epoch millis
    val fileUrl: String = "",         // URL ไฟล์แนบ (Firebase Storage)
    val fileName: String = "",
    val notifyBeforeMinutes: Int = 60
)

// ── การส่งการบ้าน (นักเรียน → ครูตรวจ) ───────────────────────────────────
// Firestore: rooms/{roomId}/submissions/{hwId}_{studentName}
data class HomeworkSubmission(
    val id: String = "",
    val hwId: String = "",
    val hwTitle: String = "",
    val studentName: String = "",
    val deviceId: String = "",        // unique per device (UUID)
    val answer: String = "",
    val fileUrl: String = "",         // Firebase Storage download URL
    val fileName: String = "",        // ชื่อไฟล์ต้นฉบับ
    val fileSizeBytes: Long = 0L,
    val submittedAt: Long = 0L,
    val grade: String = "",           // ครูให้คะแนน (ตัวเลขหรือ A/B/C)
    val feedback: String = "",        // ครูคอมเมนต์
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_GRADED  = "graded"
    }
    val hasFile: Boolean get() = fileUrl.isNotBlank()
}

// ── เซสชันเช็คชื่อ (ครูเปิด/ปิด) ─────────────────────────────────────────
// Firestore: rooms/{roomId}/att_sessions/{id}
//            rooms/{roomId}/att_sessions/{id}/checkins/{studentName}
data class AttendanceSession(
    val id: String = "",
    val label: String = "",           // เช่น "คาบ 1", "เช้า"
    val date: String = "",            // "2025-05-23"
    val isOpen: Boolean = false,      // ครูเปิดให้เช็คชื่อได้
    val openedAt: Long = 0L,
    val closedAt: Long = 0L
)

data class AttendanceCheckIn(
    val studentName: String = "",
    val deviceId: String = "",         // UUID ของเครื่องที่เช็คชื่อ (กัน check-in แทนกัน)
    val checkedAt: Long = 0L,
    val sessionId: String = ""
)

// ── เช็คชื่อ (แบบเดิม — ยังคงไว้สำหรับ backward-compat) ──────────────────
data class AttendanceRecord(
    val date: String = "",            // "2025-05-23"
    val status: String = STATUS_PRESENT,
    val note: String = ""
) {
    companion object {
        const val STATUS_PRESENT = "present"
        const val STATUS_ABSENT  = "absent"
        const val STATUS_LATE    = "late"
    }
}

// ── นักเรียน / สมาชิกลงทะเบียนในห้อง (base64 avatar — ไม่ใช้ Firebase Storage) ─
// Firestore: rooms/{roomId}/students/{deviceId}
data class StudentProfile(
    val deviceId: String = "",
    val name: String = "",
    val avatarBase64: String = "",    // "data:image/jpeg;base64,..." (≤ 15 KB)
    val joinedAt: Long = 0L,
    val status: String = STATUS_PENDING,  // pending | approved | removed
    val role: String = ROLE_STUDENT       // student | teacher
) {
    companion object {
        const val STATUS_PENDING  = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REMOVED  = "removed"
        const val ROLE_STUDENT    = "student"
        const val ROLE_TEACHER    = "teacher"
    }
    val isApproved: Boolean get() = status == STATUS_APPROVED
}

// ── คะแนน ─────────────────────────────────────────────────────────────────
data class GradeRecord(
    val id: String = "",
    val studentName: String = "",     // ชื่อนักเรียน (เพื่อแสดงใน Arena / GradeManager)
    val subject: String = "",
    val score: Double = 0.0,
    val maxScore: Double = 100.0,
    val date: String = "",            // "2025-05-23"
    val note: String = ""
)

// ── ไฟล์เอกสาร ────────────────────────────────────────────────────────────
data class ClassFile(
    val id: String = "",
    val name: String = "",
    val downloadUrl: String = "",
    val sizeBytes: Long = 0L,
    val uploadedAt: Long = 0L,
    val type: String = "pdf"
)

// ── ห้องเรียนที่นักเรียนเข้าร่วม (สำหรับแสดง room list ฝั่งนักเรียน) ──────
data class StudentRoomEntry(
    val room: ClassRoom,
    val myProfile: StudentProfile?   // null = ยังไม่ได้ลงทะเบียน / โหลดไม่เสร็จ
)

// ── Arena / Live Leaderboard ───────────────────────────────────────────────
data class ArenaEntry(
    val id: String = "",
    val studentName: String = "",
    val score: Double = 0.0,
    val maxScore: Double = 100.0,
    val tag: String = "",          // e.g. "Quiz 1", "รอบชิงชนะเลิศ"
    val createdAt: Long = 0L
) {
    val percentage: Double get() = if (maxScore > 0) (score / maxScore) * 100.0 else 0.0
}
