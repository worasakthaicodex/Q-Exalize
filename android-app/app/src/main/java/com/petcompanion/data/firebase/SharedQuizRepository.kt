package com.petcompanion.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Firestore: shared_quizzes/{code}
 *   - quizJson      : String   — ข้อสอบ (QuizImportModel JSON)
 *   - title         : String
 *   - shareType     : "open" | "locked"
 *   - createdAt     : Long
 *   - lockedToDevice: String?  — deviceId แรกที่ใช้โค้ด (null = ยังไม่มีใครใช้)
 */
object SharedQuizRepository {

    private val db get() = FirebaseFirestore.getInstance()

    // ────────────────────────────────────────────────────────────────────────
    // Upload — สร้างโค้ดและฝากข้อสอบไว้ใน Firebase
    // ────────────────────────────────────────────────────────────────────────
    suspend fun uploadQuiz(
        quizJson: String,
        title: String,
        isLocked: Boolean
    ): Result<String> = runCatching {
        val code = generateCode()
        db.collection("shared_quizzes").document(code).set(
            mapOf(
                "quizJson"       to quizJson,
                "title"          to title,
                "shareType"      to if (isLocked) "locked" else "open",
                "createdAt"      to System.currentTimeMillis(),
                "lockedToDevice" to null
            )
        ).await()
        code
    }

    // ────────────────────────────────────────────────────────────────────────
    // Download — รับข้อสอบด้วยโค้ด
    // คืนค่า Pair<quizJson, isLocked>
    // ────────────────────────────────────────────────────────────────────────
    suspend fun downloadQuiz(
        code: String,
        deviceId: String
    ): Result<Pair<String, Boolean>> = runCatching {
        val ref  = db.collection("shared_quizzes").document(code.uppercase().trim())
        val snap = ref.get().await()

        if (!snap.exists()) throw Exception("ไม่พบโค้ดนี้ในระบบ")

        val quizJson   = snap.getString("quizJson") ?: throw Exception("ข้อมูลข้อสอบเสียหาย")
        val shareType  = snap.getString("shareType") ?: "open"
        val isLocked   = shareType == "locked"
        val lockedTo   = snap.getString("lockedToDevice")

        if (isLocked) {
            when {
                lockedTo == null -> {
                    // โค้ดยังไม่ถูกใช้ → ล็อกให้เครื่องนี้เลย
                    ref.update("lockedToDevice", deviceId).await()
                }
                lockedTo != deviceId -> {
                    throw Exception("โค้ดนี้ถูกใช้งานโดยเครื่องอื่นไปแล้ว")
                }
                // lockedTo == deviceId → เครื่องเดิม download ซ้ำได้
            }
        }

        quizJson to isLocked
    }

    // ────────────────────────────────────────────────────────────────────────
    private fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
