package com.petcompanion.data.firebase

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.io.InputStream

/**
 * Firestore structure (ใหม่):
 *   teachers/{teacherUid}/rooms/{roomId}
 *   teachers/{teacherUid}/rooms/{roomId}/events/{id}
 *   teachers/{teacherUid}/rooms/{roomId}/homework/{id}
 *   teachers/{teacherUid}/rooms/{roomId}/students/{deviceId}
 *   teachers/{teacherUid}/rooms/{roomId}/grades/{id}
 *   teachers/{teacherUid}/rooms/{roomId}/files/{id}
 *   teachers/{teacherUid}/rooms/{roomId}/arena/{id}
 *   teachers/{teacherUid}/rooms/{roomId}/att_sessions/{id}/checkins/{deviceId}
 *   teachers/{teacherUid}/rooms/{roomId}/exams/{examId}/questions/{qId}
 *   teachers/{teacherUid}/rooms/{roomId}/exams/{examId}/submissions/{deviceId}
 */
class ClassroomRepository(private val manager: FirebaseManager) {

    /** teacherUid ที่กำลัง active — ClassroomViewModel ต้องตั้งค่าก่อนใช้งาน */
    var teacherUid: String = ""

    private val db get() = manager.firestore

    private fun roomsCol() = db.collection("teachers").document(teacherUid).collection("rooms")
    private fun roomDoc(roomId: String) = roomsCol().document(roomId)

    // ── รายการห้องทั้งหมดของครู (real-time) ──────────────────────────────
    fun getRoomsFlow(): Flow<List<ClassRoom>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomsCol()
                .addSnapshotListener { snap, _ ->
                    val list = snap?.documents?.mapNotNull { doc ->
                        try { ClassRoom(
                            id          = doc.id,
                            name        = doc.getString("name") ?: "",
                            teacherName = doc.getString("teacherName") ?: "",
                            description = doc.getString("description") ?: "",
                            createdAt   = doc.getLong("createdAt") ?: 0L
                        ) } catch (_: Exception) { null }
                    }?.sortedByDescending { it.createdAt } ?: emptyList()
                    trySend(list)
                }
            awaitClose { listener.remove() }
        }
    }

    // ── ดึงข้อมูลห้องเรียน ────────────────────────────────────────────────
    suspend fun getRoom(roomId: String): Result<ClassRoom> = runCatching {
        val doc = roomDoc(roomId).get().await()
        if (!doc.exists()) throw Exception("Room not found: $roomId")
        ClassRoom(
            id          = doc.id,
            name        = doc.getString("name") ?: "",
            teacherName = doc.getString("teacherName") ?: "",
            description = doc.getString("description") ?: "",
            createdAt   = doc.getLong("createdAt") ?: 0L
        )
    }

    // ── ตารางงาน (real-time) ─────────────────────────────────────────────
    fun getEventsFlow(roomId: String): Flow<List<ScheduleEvent>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("events").orderBy("dateTime")
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null) { trySend(emptyList()); return@addSnapshotListener }
                    trySend(snap.documents.mapNotNull { doc ->
                        try { ScheduleEvent(
                            id                  = doc.id,
                            title               = doc.getString("title") ?: "",
                            description         = doc.getString("description") ?: "",
                            dateTime            = doc.getLong("dateTime") ?: 0L,
                            endTime             = doc.getLong("endTime") ?: 0L,
                            type                = doc.getString("type") ?: ScheduleEvent.TYPE_EVENT,
                            notifyBeforeMinutes = (doc.getLong("notifyBeforeMinutes") ?: 30L).toInt()
                        ) } catch (_: Exception) { null }
                    })
                }
            awaitClose { listener.remove() }
        }
    }

    // ── การบ้าน (real-time) ───────────────────────────────────────────────
    fun getHomeworkFlow(roomId: String): Flow<List<HomeworkItem>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("homework").orderBy("dueDate")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try { HomeworkItem(
                            id                  = doc.id,
                            title               = doc.getString("title") ?: "",
                            description         = doc.getString("description") ?: "",
                            dueDate             = doc.getLong("dueDate") ?: 0L,
                            fileUrl             = doc.getString("fileUrl") ?: "",
                            fileName            = doc.getString("fileName") ?: "",
                            notifyBeforeMinutes = (doc.getLong("notifyBeforeMinutes") ?: 60L).toInt()
                        ) } catch (_: Exception) { null }
                    } ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    // ── เช็คชื่อ (real-time) ──────────────────────────────────────────────
    fun getAttendanceFlow(roomId: String): Flow<List<AttendanceRecord>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("attendance")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try { AttendanceRecord(
                            date   = doc.id,
                            status = doc.getString("status") ?: AttendanceRecord.STATUS_PRESENT,
                            note   = doc.getString("note") ?: ""
                        ) } catch (_: Exception) { null }
                    }?.sortedByDescending { it.date } ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    // ── นักเรียน (real-time) ──────────────────────────────────────────────
    fun getStudentsFlow(roomId: String): Flow<List<StudentProfile>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("students")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try { StudentProfile(
                            deviceId     = doc.id,
                            name         = doc.getString("name") ?: "",
                            avatarBase64 = doc.getString("avatarBase64") ?: "",
                            joinedAt     = doc.getLong("joinedAt") ?: 0L,
                            status       = doc.getString("status") ?: StudentProfile.STATUS_PENDING,
                            role         = doc.getString("role") ?: StudentProfile.ROLE_STUDENT
                        ) } catch (_: Exception) { null }
                    }?.sortedBy { it.joinedAt } ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    // ── คะแนน (real-time) ─────────────────────────────────────────────────
    fun getGradesFlow(roomId: String): Flow<List<GradeRecord>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("grades")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try { GradeRecord(
                            id          = doc.id,
                            studentName = doc.getString("studentName") ?: "",
                            subject     = doc.getString("subject") ?: "",
                            score       = doc.getDouble("score") ?: 0.0,
                            maxScore    = doc.getDouble("maxScore") ?: 100.0,
                            date        = doc.getString("date") ?: "",
                            note        = doc.getString("note") ?: ""
                        ) } catch (_: Exception) { null }
                    }?.sortedByDescending { it.date } ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    // ── ไฟล์ PDF (real-time) ──────────────────────────────────────────────
    fun getFilesFlow(roomId: String): Flow<List<ClassFile>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("files").orderBy("uploadedAt")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try { ClassFile(
                            id          = doc.id,
                            name        = doc.getString("name") ?: "",
                            downloadUrl = doc.getString("downloadUrl") ?: "",
                            sizeBytes   = doc.getLong("sizeBytes") ?: 0L,
                            uploadedAt  = doc.getLong("uploadedAt") ?: 0L,
                            type        = doc.getString("type") ?: "pdf"
                        ) } catch (_: Exception) { null }
                    }?.reversed() ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WRITE — ครูเท่านั้น
    // ═══════════════════════════════════════════════════════════════════════

    suspend fun createOrUpdateRoom(roomId: String, room: ClassRoom): Result<Unit> = runCatching {
        roomDoc(roomId).set(mapOf(
            "name"        to room.name,
            "teacherName" to room.teacherName,
            "description" to room.description,
            "createdAt"   to System.currentTimeMillis()
        )).await()
    }

    suspend fun deleteRoom(roomId: String): Result<Unit> = runCatching {
        roomDoc(roomId).delete().await()
    }

    // ── ตารางงาน CRUD ─────────────────────────────────────────────────────
    suspend fun addEvent(roomId: String, event: ScheduleEvent): Result<Unit> = runCatching {
        val col = roomDoc(roomId).collection("events")
        val ref = if (event.id.isBlank()) col.document() else col.document(event.id)
        ref.set(mapOf(
            "title"               to event.title,
            "description"         to event.description,
            "dateTime"            to event.dateTime,
            "endTime"             to event.endTime,
            "type"                to event.type,
            "notifyBeforeMinutes" to event.notifyBeforeMinutes
        )).await()
    }

    suspend fun deleteEvent(roomId: String, eventId: String): Result<Unit> = runCatching {
        roomDoc(roomId).collection("events").document(eventId).delete().await()
    }

    // ── การบ้าน CRUD ──────────────────────────────────────────────────────
    suspend fun addHomework(roomId: String, hw: HomeworkItem): Result<Unit> = runCatching {
        val col = roomDoc(roomId).collection("homework")
        val ref = if (hw.id.isBlank()) col.document() else col.document(hw.id)
        ref.set(mapOf(
            "title"               to hw.title,
            "description"         to hw.description,
            "dueDate"             to hw.dueDate,
            "fileUrl"             to hw.fileUrl,
            "fileName"            to hw.fileName,
            "notifyBeforeMinutes" to hw.notifyBeforeMinutes
        )).await()
    }

    suspend fun deleteHomework(roomId: String, hwId: String): Result<Unit> = runCatching {
        roomDoc(roomId).collection("homework").document(hwId).delete().await()
    }

    // ── เช็คชื่อ CRUD ─────────────────────────────────────────────────────
    suspend fun setAttendance(roomId: String, record: AttendanceRecord): Result<Unit> = runCatching {
        roomDoc(roomId).collection("attendance").document(record.date)
            .set(mapOf("status" to record.status, "note" to record.note)).await()
    }

    suspend fun deleteAttendance(roomId: String, date: String): Result<Unit> = runCatching {
        roomDoc(roomId).collection("attendance").document(date).delete().await()
    }

    // ── คะแนน CRUD ────────────────────────────────────────────────────────
    suspend fun addGrade(roomId: String, grade: GradeRecord): Result<Unit> = runCatching {
        val col = roomDoc(roomId).collection("grades")
        val ref = if (grade.id.isBlank()) col.document() else col.document(grade.id)
        ref.set(mapOf(
            "studentName" to grade.studentName,
            "subject"     to grade.subject,
            "score"       to grade.score,
            "maxScore"    to grade.maxScore,
            "date"        to grade.date,
            "note"        to grade.note
        )).await()
    }

    suspend fun bulkAddGrades(roomId: String, grades: List<GradeRecord>): Result<Unit> {
        if (grades.isEmpty()) return Result.success(Unit)
        return runCatching {
            val batch = db.batch()
            grades.forEach { grade ->
                val ref = roomDoc(roomId).collection("grades").document()
                batch.set(ref, mapOf(
                    "studentName" to grade.studentName,
                    "subject"     to grade.subject,
                    "score"       to grade.score,
                    "maxScore"    to grade.maxScore,
                    "date"        to grade.date,
                    "note"        to grade.note
                ))
            }
            batch.commit().await()
        }
    }

    suspend fun deleteGrade(roomId: String, gradeId: String): Result<Unit> = runCatching {
        roomDoc(roomId).collection("grades").document(gradeId).delete().await()
    }

    // ── ไฟล์ PDF CRUD ─────────────────────────────────────────────────────
    suspend fun addFile(roomId: String, file: ClassFile): Result<Unit> = runCatching {
        val col = roomDoc(roomId).collection("files")
        val ref = if (file.id.isBlank()) col.document() else col.document(file.id)
        ref.set(mapOf(
            "name"        to file.name,
            "downloadUrl" to file.downloadUrl,
            "sizeBytes"   to file.sizeBytes,
            "uploadedAt"  to System.currentTimeMillis(),
            "type"        to file.type
        )).await()
    }

    suspend fun deleteFile(roomId: String, fileId: String): Result<Unit> = runCatching {
        roomDoc(roomId).collection("files").document(fileId).delete().await()
    }

    // ── Arena / Leaderboard ───────────────────────────────────────────────
    fun getArenaFlow(roomId: String): Flow<List<ArenaEntry>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("arena")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try { ArenaEntry(
                            id          = doc.id,
                            studentName = doc.getString("studentName") ?: "",
                            score       = doc.getDouble("score") ?: 0.0,
                            maxScore    = doc.getDouble("maxScore") ?: 100.0,
                            tag         = doc.getString("tag") ?: "",
                            createdAt   = doc.getLong("createdAt") ?: 0L
                        ) } catch (_: Exception) { null }
                    } ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    suspend fun addArenaEntry(roomId: String, entry: ArenaEntry): Result<Unit> = runCatching {
        val col = roomDoc(roomId).collection("arena")
        val ref = if (entry.id.isBlank()) col.document() else col.document(entry.id)
        ref.set(mapOf(
            "studentName" to entry.studentName,
            "score"       to entry.score,
            "maxScore"    to entry.maxScore,
            "tag"         to entry.tag,
            "createdAt"   to System.currentTimeMillis()
        )).await()
    }

    suspend fun deleteArenaEntry(roomId: String, entryId: String): Result<Unit> = runCatching {
        roomDoc(roomId).collection("arena").document(entryId).delete().await()
    }

    suspend fun clearArena(roomId: String): Result<Unit> = runCatching {
        val snap = roomDoc(roomId).collection("arena").get().await()
        val batch = db.batch()
        snap.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HOMEWORK SUBMISSIONS
    // ═══════════════════════════════════════════════════════════════════════

    fun getAllSubmissionsFlow(roomId: String): Flow<List<HomeworkSubmission>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("submissions")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try { HomeworkSubmission(
                            id            = doc.id,
                            hwId          = doc.getString("hwId") ?: "",
                            hwTitle       = doc.getString("hwTitle") ?: "",
                            studentName   = doc.getString("studentName") ?: "",
                            deviceId      = doc.getString("deviceId") ?: "",
                            answer        = doc.getString("answer") ?: "",
                            fileUrl       = doc.getString("fileUrl") ?: "",
                            fileName      = doc.getString("fileName") ?: "",
                            fileSizeBytes = doc.getLong("fileSizeBytes") ?: 0L,
                            submittedAt   = doc.getLong("submittedAt") ?: 0L,
                            grade         = doc.getString("grade") ?: "",
                            feedback      = doc.getString("feedback") ?: "",
                            status        = doc.getString("status") ?: HomeworkSubmission.STATUS_PENDING
                        ) } catch (_: Exception) { null }
                    }?.sortedByDescending { it.submittedAt } ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    suspend fun uploadHomeworkFile(
        roomId: String, hwId: String, deviceId: String,
        stream: InputStream, fileName: String, onProgress: (Float) -> Unit
    ): Result<String> = runCatching {
        val ext = fileName.substringAfterLast(".", "dat")
        val ref = manager.storage.reference
            .child("teachers/$teacherUid/rooms/$roomId/hw/$hwId/${deviceId}.$ext")
        val uploadTask = ref.putStream(stream)
        uploadTask.addOnProgressListener { snap ->
            val total = snap.totalByteCount.coerceAtLeast(1L)
            onProgress((snap.bytesTransferred.toFloat() / total).coerceIn(0f, 1f))
        }
        uploadTask.await()
        stream.close()
        ref.downloadUrl.await().toString()
    }

    suspend fun submitHomework(roomId: String, sub: HomeworkSubmission): Result<Unit> = runCatching {
        val docId = "${sub.hwId}_${sub.deviceId.ifBlank { sub.studentName.replace(" ", "_") }}"
        roomDoc(roomId).collection("submissions").document(docId).set(mapOf(
            "hwId"          to sub.hwId,
            "hwTitle"       to sub.hwTitle,
            "studentName"   to sub.studentName,
            "deviceId"      to sub.deviceId,
            "answer"        to sub.answer,
            "fileUrl"       to sub.fileUrl,
            "fileName"      to sub.fileName,
            "fileSizeBytes" to sub.fileSizeBytes,
            "submittedAt"   to System.currentTimeMillis(),
            "grade"         to sub.grade.ifBlank { "" },
            "feedback"      to sub.feedback.ifBlank { "" },
            "status"        to HomeworkSubmission.STATUS_PENDING
        )).await()
    }

    suspend fun gradeSubmission(
        roomId: String, submissionId: String, grade: String, feedback: String
    ): Result<Unit> = runCatching {
        roomDoc(roomId).collection("submissions").document(submissionId).update(mapOf(
            "grade"    to grade,
            "feedback" to feedback,
            "status"   to HomeworkSubmission.STATUS_GRADED
        )).await()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ATTENDANCE SESSIONS
    // ═══════════════════════════════════════════════════════════════════════

    fun getAttSessionsFlow(roomId: String): Flow<List<AttendanceSession>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("att_sessions")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try { AttendanceSession(
                            id       = doc.id,
                            label    = doc.getString("label") ?: "",
                            date     = doc.getString("date") ?: "",
                            isOpen   = doc.getBoolean("isOpen") ?: false,
                            openedAt = doc.getLong("openedAt") ?: 0L,
                            closedAt = doc.getLong("closedAt") ?: 0L
                        ) } catch (_: Exception) { null }
                    }?.sortedByDescending { it.openedAt } ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    fun getCheckInsFlow(roomId: String, sessionId: String): Flow<List<AttendanceCheckIn>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("att_sessions").document(sessionId)
                .collection("checkins")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try { AttendanceCheckIn(
                            studentName = doc.getString("studentName") ?: doc.id.replace("_", " "),
                            deviceId    = doc.getString("deviceId") ?: doc.id,
                            checkedAt   = doc.getLong("checkedAt") ?: 0L,
                            sessionId   = sessionId
                        ) } catch (_: Exception) { null }
                    }?.sortedBy { it.checkedAt } ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    suspend fun createAttSession(roomId: String, session: AttendanceSession): Result<String> = runCatching {
        val ref = roomDoc(roomId).collection("att_sessions").document()
        ref.set(mapOf(
            "label"    to session.label,
            "date"     to session.date,
            "isOpen"   to true,
            "openedAt" to System.currentTimeMillis(),
            "closedAt" to 0L
        )).await()
        ref.id
    }

    suspend fun toggleAttSession(roomId: String, sessionId: String, isOpen: Boolean): Result<Unit> = runCatching {
        val update = if (isOpen)
            mapOf("isOpen" to true,  "openedAt" to System.currentTimeMillis())
        else
            mapOf("isOpen" to false, "closedAt" to System.currentTimeMillis())
        roomDoc(roomId).collection("att_sessions").document(sessionId).update(update).await()
    }

    suspend fun deleteAttSession(roomId: String, sessionId: String): Result<Unit> = runCatching {
        roomDoc(roomId).collection("att_sessions").document(sessionId).delete().await()
    }

    suspend fun checkIn(
        roomId: String, sessionId: String, studentName: String, deviceId: String
    ): Result<Unit> = runCatching {
        roomDoc(roomId).collection("att_sessions").document(sessionId)
            .collection("checkins").document(deviceId)
            .set(mapOf(
                "studentName" to studentName,
                "deviceId"    to deviceId,
                "checkedAt"   to System.currentTimeMillis()
            )).await()
    }

    suspend fun getCheckInsOnce(roomId: String, sessionId: String): List<AttendanceCheckIn> {
        if (teacherUid.isBlank()) return emptyList()
        return try {
            roomDoc(roomId).collection("att_sessions").document(sessionId)
                .collection("checkins").get().await()
                .documents.mapNotNull { doc ->
                    try { AttendanceCheckIn(
                        studentName = doc.getString("studentName") ?: doc.id.replace("_", " "),
                        deviceId    = doc.getString("deviceId") ?: doc.id,
                        checkedAt   = doc.getLong("checkedAt") ?: 0L,
                        sessionId   = sessionId
                    ) } catch (_: Exception) { null }
                }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun isCheckedIn(roomId: String, sessionId: String, deviceId: String): Boolean {
        if (teacherUid.isBlank()) return false
        return try {
            roomDoc(roomId).collection("att_sessions").document(sessionId)
                .collection("checkins").document(deviceId).get().await().exists()
        } catch (_: Exception) { false }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ONLINE EXAMS
    // ═══════════════════════════════════════════════════════════════════════

    fun getExamsFlow(roomId: String): Flow<List<OnlineExam>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("exams")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try { OnlineExam(
                            id              = doc.id,
                            title           = doc.getString("title") ?: "",
                            description     = doc.getString("description") ?: "",
                            startTime       = doc.getLong("startTime") ?: 0L,
                            endTime         = doc.getLong("endTime") ?: 0L,
                            scheduleEventId = doc.getString("scheduleEventId") ?: "",
                            autoGrade       = doc.getBoolean("autoGrade") ?: true,
                            showAnswers     = doc.getBoolean("showAnswers") ?: false,
                            isPublished     = doc.getBoolean("isPublished") ?: false,
                            linkedCourseId  = (doc.getLong("linkedCourseId") ?: 0L).toInt(),
                            createdAt       = doc.getLong("createdAt") ?: 0L
                        ) } catch (_: Exception) { null }
                    }?.sortedByDescending { it.createdAt } ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    suspend fun createExam(roomId: String, exam: OnlineExam): Result<String> = runCatching {
        val col = roomDoc(roomId).collection("exams")
        val ref = if (exam.id.isBlank()) col.document() else col.document(exam.id)
        ref.set(mapOf(
            "title"           to exam.title,
            "description"     to exam.description,
            "startTime"       to exam.startTime,
            "endTime"         to exam.endTime,
            "scheduleEventId" to exam.scheduleEventId,
            "autoGrade"       to exam.autoGrade,
            "showAnswers"     to exam.showAnswers,
            "isPublished"     to exam.isPublished,
            "linkedCourseId"  to exam.linkedCourseId,
            "createdAt"       to System.currentTimeMillis()
        )).await()
        ref.id
    }

    suspend fun updateExam(roomId: String, exam: OnlineExam): Result<Unit> = runCatching {
        roomDoc(roomId).collection("exams").document(exam.id).update(mapOf(
            "title"       to exam.title,
            "description" to exam.description,
            "startTime"   to exam.startTime,
            "endTime"     to exam.endTime,
            "autoGrade"   to exam.autoGrade,
            "showAnswers" to exam.showAnswers,
            "isPublished" to exam.isPublished
        )).await()
    }

    suspend fun deleteExam(roomId: String, examId: String): Result<Unit> = runCatching {
        roomDoc(roomId).collection("exams").document(examId).delete().await()
    }

    // ── Questions ──────────────────────────────────────────────────────────
    fun getQuestionsFlow(roomId: String, examId: String): Flow<List<ExamQuestion>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("exams").document(examId)
                .collection("questions").orderBy("order")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try {
                            @Suppress("UNCHECKED_CAST")
                            ExamQuestion(
                                id            = doc.id,
                                text          = doc.getString("text") ?: "",
                                type          = doc.getString("type") ?: ExamQuestion.TYPE_CHOICE,
                                choices       = (doc.get("choices") as? List<*>)?.map { it.toString() } ?: emptyList(),
                                correctAnswer = doc.getString("correctAnswer") ?: "",
                                points        = doc.getDouble("points") ?: 1.0,
                                order         = (doc.getLong("order") ?: 0L).toInt()
                            )
                        } catch (_: Exception) { null }
                    } ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    suspend fun saveQuestion(roomId: String, examId: String, q: ExamQuestion): Result<Unit> = runCatching {
        val col = roomDoc(roomId).collection("exams").document(examId).collection("questions")
        val ref = if (q.id.isBlank()) col.document() else col.document(q.id)
        ref.set(mapOf(
            "text"          to q.text,
            "type"          to q.type,
            "choices"       to q.choices,
            "correctAnswer" to q.correctAnswer,
            "points"        to q.points,
            "order"         to q.order
        )).await()
    }

    suspend fun deleteQuestion(roomId: String, examId: String, questionId: String): Result<Unit> = runCatching {
        roomDoc(roomId).collection("exams").document(examId)
            .collection("questions").document(questionId).delete().await()
    }

    // ── Submissions ────────────────────────────────────────────────────────
    fun getExamSubmissionsFlow(roomId: String, examId: String): Flow<List<ExamSubmission>> {
        if (teacherUid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val listener = roomDoc(roomId).collection("exams").document(examId)
                .collection("submissions")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.documents?.mapNotNull { doc ->
                        try {
                            @Suppress("UNCHECKED_CAST")
                            ExamSubmission(
                                id          = doc.id,
                                examId      = examId,
                                studentName = doc.getString("studentName") ?: "",
                                deviceId    = doc.getString("deviceId") ?: doc.id,
                                answers     = (doc.get("answers") as? Map<String, String>) ?: emptyMap(),
                                score       = doc.getDouble("score") ?: 0.0,
                                maxScore    = doc.getDouble("maxScore") ?: 0.0,
                                submittedAt = doc.getLong("submittedAt") ?: 0L,
                                isGraded    = doc.getBoolean("isGraded") ?: false
                            )
                        } catch (_: Exception) { null }
                    }?.sortedByDescending { it.submittedAt } ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
    }

    suspend fun getMyExamSubmission(roomId: String, examId: String, deviceId: String): ExamSubmission? {
        if (teacherUid.isBlank()) return null
        return try {
            val doc = roomDoc(roomId).collection("exams").document(examId)
                .collection("submissions").document(deviceId).get().await()
            if (!doc.exists()) return null
            @Suppress("UNCHECKED_CAST")
            ExamSubmission(
                id          = doc.id,
                examId      = examId,
                studentName = doc.getString("studentName") ?: "",
                deviceId    = doc.getString("deviceId") ?: doc.id,
                answers     = (doc.get("answers") as? Map<String, String>) ?: emptyMap(),
                score       = doc.getDouble("score") ?: 0.0,
                maxScore    = doc.getDouble("maxScore") ?: 0.0,
                submittedAt = doc.getLong("submittedAt") ?: 0L,
                isGraded    = doc.getBoolean("isGraded") ?: false
            )
        } catch (_: Exception) { null }
    }

    suspend fun submitExam(roomId: String, examId: String, submission: ExamSubmission): Result<Unit> = runCatching {
        roomDoc(roomId).collection("exams").document(examId)
            .collection("submissions").document(submission.deviceId)
            .set(mapOf(
                "examId"      to examId,
                "studentName" to submission.studentName,
                "deviceId"    to submission.deviceId,
                "answers"     to submission.answers,
                "score"       to submission.score,
                "maxScore"    to submission.maxScore,
                "submittedAt" to System.currentTimeMillis(),
                "isGraded"    to submission.isGraded
            )).await()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STUDENT REGISTRATION
    // ═══════════════════════════════════════════════════════════════════════

    suspend fun approveStudent(roomId: String, deviceId: String, role: String): Result<Unit> = runCatching {
        roomDoc(roomId).collection("students").document(deviceId)
            .update(mapOf("status" to StudentProfile.STATUS_APPROVED, "role" to role)).await()
    }

    suspend fun removeStudent(roomId: String, deviceId: String): Result<Unit> = runCatching {
        roomDoc(roomId).collection("students").document(deviceId).delete().await()
    }

    suspend fun transferStudentDevice(
        roomId: String, oldDeviceId: String, newDeviceId: String
    ): Result<Unit> = runCatching {
        val oldDoc = roomDoc(roomId).collection("students").document(oldDeviceId).get().await()
        val name   = oldDoc.getString("name")         ?: ""
        val avatar = oldDoc.getString("avatarBase64") ?: ""
        val role   = oldDoc.getString("role")         ?: StudentProfile.ROLE_STUDENT
        roomDoc(roomId).collection("students").document(newDeviceId).update(mapOf(
            "name"         to name,
            "avatarBase64" to avatar,
            "role"         to role,
            "status"       to StudentProfile.STATUS_APPROVED
        )).await()
        roomDoc(roomId).collection("students").document(oldDeviceId).delete().await()
    }

    suspend fun getStudentOnce(roomId: String, deviceId: String): StudentProfile? {
        if (teacherUid.isBlank()) return null
        return runCatching {
            val doc = roomDoc(roomId).collection("students").document(deviceId).get().await()
            if (!doc.exists()) return@runCatching null
            StudentProfile(
                deviceId     = doc.id,
                name         = doc.getString("name") ?: "",
                avatarBase64 = doc.getString("avatarBase64") ?: "",
                joinedAt     = doc.getLong("joinedAt") ?: 0L,
                status       = doc.getString("status") ?: StudentProfile.STATUS_PENDING,
                role         = doc.getString("role") ?: StudentProfile.ROLE_STUDENT
            )
        }.getOrNull()
    }

    suspend fun registerStudent(roomId: String, profile: StudentProfile): Result<Unit> = runCatching {
        val docRef = roomDoc(roomId).collection("students").document(profile.deviceId)
        val existing = docRef.get().await()
        if (existing.exists()) {
            docRef.update(mapOf("name" to profile.name, "avatarBase64" to profile.avatarBase64)).await()
        } else {
            docRef.set(mapOf(
                "name"         to profile.name,
                "avatarBase64" to profile.avatarBase64,
                "joinedAt"     to (profile.joinedAt.takeIf { it > 0 } ?: System.currentTimeMillis()),
                "status"       to StudentProfile.STATUS_PENDING,
                "role"         to StudentProfile.ROLE_STUDENT
            )).await()
        }
    }
}
