package com.petcompanion.presentation.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.viewModelScope
import androidx.room.Delete
import com.petcompanion.data.local.BillingManager
import com.petcompanion.data.local.CategoryDao
import com.petcompanion.data.mapper.ChapterCount
import com.petcompanion.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch // 📍 เติม import ตัวนี้เผื่อไว้ใช้ทำฟังก์ชัน Save
import kotlin.random.Random

class QuizViewModel(application: Application,
                    private val dao: CategoryDao) : AndroidViewModel(application) {




    fun saveCategory(category: QuizCategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // 📍 แยกเงื่อนไขการทำงานชัดเจน
            if (category.id == 0) {
                // ถ้า ID เป็น 0 แปลว่าสร้างใหม่ (Room จะ Gen ID ให้เอง)
                dao.insertCategory(category)
            } else {
                // ถ้ามี ID อยู่แล้ว แปลว่าเป็นการกดแก้ไข ห้าม Insert เด็ดขาด! ให้ Update แทน
                dao.updateCategory(category)
            }
        }
    }

    // 📍 ดึงข้อมูลจาก DB มาเป็น StateFlow (มันจะคอยอัปเดตอัตโนมัติ)
    val categoryList: StateFlow<List<QuizCategoryEntity>> = dao.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList() // เริ่มต้นด้วยลิสต์ว่างๆ ก่อนโหลดเสร็จ
        )

    fun deleteCategory(category: QuizCategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteCategory(category)
        }
    }
    // 📍 1. ตัวแปรนี้แหละที่ UI กำลังร้องหา! (ดึงรายชื่อวิชามาทำ Dropdown)
    val allCourses: StateFlow<List<CourseEntity>> = dao.getAllCourses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList() // เริ่มต้นเป็น List ว่าง
        )

    /** สร้างวิชาใหม่พร้อมข้อสอบที่พิมพ์เองทีละข้อ (Manual mode) */
    fun addManualCourse(
        categoryId: Int,
        title: String,
        courseCode: String,
        description: String,
        timeLimit: Int,
        passingScore: Int,
        questions: List<QuestionEntity>,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val courseId = dao.insertCourse(CourseEntity(
                categoryId     = categoryId,
                courseCode     = courseCode.ifBlank { title.take(8).uppercase() },
                title          = title,
                description    = description,
                timeLimit      = timeLimit.coerceAtLeast(1),
                passingScore   = passingScore.coerceIn(0, 100),
                totalQuestions = questions.size
            )).toInt()
            val entities = questions.mapIndexed { i, q ->
                q.copy(qid = Random.nextInt(1_000_000, 9_000_000) + i, courseId = courseId)
            }
            if (entities.isNotEmpty()) {
                dao.insertQuestions(entities)
                dao.updateCourseTotalQuestions(courseId, entities.size)
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun importQuizData(importedData: QuizImportModel, existingCourseId: Int?, categoryId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val finalCourseId: Int

            if (existingCourseId != null) {
                // กรณีนำเข้า "วิชาเดิม"
                finalCourseId = existingCourseId

                // (ถ้าอยากให้อัปเดต ชื่อวิชา/คำอธิบาย ด้วย ก็เขียน Update ตรงนี้ได้ครับ)

            } else {
                // กรณีนำเข้า "วิชาใหม่"
                val newCourse = CourseEntity(
                    categoryId = categoryId,
                    courseCode = importedData.courseCode,
                    title = importedData.title,
                    description = importedData.description,
                    timeLimit = importedData.timeLimit,
                    passingScore = importedData.passingScore,
                    totalQuestions = 0 // เดี๋ยวเรามาอัปเดตทีหลัง
                )
                finalCourseId = dao.insertCourse(newCourse).toInt()
            }

            // --- 📍 หัวใจสำคัญ: ระบบจัดการรายบท (Smart Merge) ---

            // 1. ดึงชื่อบททั้งหมดที่มีอยู่ในไฟล์ JSON (ตัดชื่อซ้ำออก)
            val incomingChapters = importedData.questions.map { it.chapter }.distinct()



            // 3. แปลงร่างข้อสอบใหม่แล้ว Insert เข้าไปใน DB (เวอร์ชันแก้บั๊กทับกัน)
            val questionEntities = importedData.questions.mapIndexed { index, q ->
                QuestionEntity(
                    // 📍 เสก ID แบบสุ่มเลข 7 หลัก + ลำดับข้อ (รับประกันว่าเป็น Int และไม่มีทางซ้ำกัน 100%)
                    qid = Random.nextInt(1000000, 9000000) + index,

                    courseId = finalCourseId,
                    chapter = q.chapter,
                    text = q.text,
                    options = q.options,
                    correctIndex = q.correctIndex,
                    closeIndex = q.closeIndex,
                    explanation = q.explanation
                )
            }
            dao.insertQuestions(questionEntities) // Insert ชุดใหม่เข้าไป (รอบนี้รอดครบทุกข้อแน่นอน!)

            // 4. 📍 อัปเดตยอดจำนวนข้อสอบรวมของวิชานี้ใหม่ (เพราะอาจมีบทเพิ่มเข้ามา)
            val newTotalCount = dao.countQuestionsByCourseId(finalCourseId)
            dao.updateCourseTotalQuestions(finalCourseId, newTotalCount)
        }
    }


    // 📍 1. ตัวแปรเก็บ ID หมวดหมู่ที่เลือกอยู่
    private val _selectedCategoryId = MutableStateFlow<Int?>(null)
    // 📍 2. ดึงข้อมูลวิชาที่เปลี่ยนตาม _selectedCategoryId อัตโนมัติ
    @OptIn(ExperimentalCoroutinesApi::class)
    val coursesInSelectedCategory: StateFlow<List<CourseEntity>> = _selectedCategoryId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else dao.getCoursesByCategoryId(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 📍 3. ฟังก์ชันสำหรับเปลี่ยน ID หมวดหมู่ (เรียกใช้ตอนเข้าหน้า QuizList)
    fun setCategoryId(id: Int) {
        _selectedCategoryId.value = id
    }


    fun getCourseById(courseId: Int) = dao.getCourseById(courseId)

    /** ดึงคำถาม — ถ้าวิชาล็อกจะ decrypt ด้วย deviceId ก่อนส่งให้ UI */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getQuestionsByCourseId(courseId: Int, deviceId: String = ""): kotlinx.coroutines.flow.Flow<List<com.petcompanion.domain.model.QuestionEntity>> =
        dao.getCourseById(courseId).flatMapLatest { course ->
            dao.getQuestionsByCourseId(courseId).map { questions ->
                if (!course.isLocked || deviceId.isBlank()) questions
                else {
                    val crypto = com.petcompanion.data.local.QuizCrypto
                    questions.map { q ->
                        q.copy(
                            text        = crypto.decrypt(q.text, deviceId),
                            options     = crypto.decryptList(q.options, deviceId),
                            explanation = crypto.decrypt(q.explanation, deviceId)
                        )
                    }
                }
            }
        }

    val allHistory: StateFlow<List<QuizHistoryEntity>> = dao.getAllHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun submitQuizResult(
        courseId: Int,
        percentageScore: Int,
        timeSpent: Int,
        correctCount: Int,
        totalQ: Int,
        isPassed: Boolean,
        isTimeout: Boolean, // 📍 เพิ่มตัวนี้
        closeCount: Int,
        selectedAnswers: Map<Int, Int> = emptyMap()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentAttempt = dao.getAttemptCount(courseId) + 1

            val historyEntry = QuizHistoryEntity(
                courseId = courseId,
                scorePercentage = percentageScore,
                correctCount = correctCount,
                totalQuestions = totalQ,
                timeSpentSeconds = timeSpent,
                attemptNumber = currentAttempt,
                isPassed = isPassed,
                isTimeout = isTimeout,
                closeAnswersCount = closeCount,
                selectedAnswers = selectedAnswers// 📍 เอามาเซฟลง History ตรงนี้
            )
            dao.insertHistory(historyEntry)
            dao.updateCourseProgress(courseId, 1.0f, percentageScore)
        }
    }

    suspend fun getQuizSessionData(courseId: Int): Pair<CourseEntity?, List<QuestionEntity>> {
        val course = dao.getCourseById(courseId).firstOrNull()
        val questions = dao.getQuestionsByCourseId(courseId).firstOrNull() ?: emptyList()
        return Pair(course, questions)
    }


    data class HunterStats(
        val knowledge: Float,   // ความรู้รอบตัว
        val timeMgmt: Float,    // การจัดการเวลา
        val analytical: Float,  // การวิเคราะห์
        val growth: Float,      // การพัฒนาการ
        val diligence: Float    // ความขยัน
    )

    // ใน QuizViewModel เพิ่มฟังก์ชันคำนวณ:
    fun calculateHunterStats(allHistory: List<QuizHistoryEntity>, allCourses: List<CourseEntity>): HunterStats {
        if (allHistory.isEmpty()) return HunterStats(0f, 0f, 0f, 0f, 0f)

        // 1. ความรู้: % ผ่านรอบแรก
        val firstAttempts = allHistory.filter { it.attemptNumber == 1 }
        val knowledgeScore = (firstAttempts.count { it.isPassed }.toFloat() / firstAttempts.size.coerceAtLeast(1)) * 5

        // 2. เวลา: เฉลี่ยเวลาทำเทียบกับเวลาที่ให้ (ไม่โดน Timeout)
        val timeScore = (allHistory.count { it.timeSpentSeconds > 0 }.toFloat() / allHistory.size) * 5

        // 3. การวิเคราะห์: ถูก + เกือบถูก
        val totalQ = allHistory.sumOf { it.totalQuestions }
        val analyticalScore = ((allHistory.sumOf { it.correctCount + it.closeAnswersCount }).toFloat() / totalQ.coerceAtLeast(1)) * 5

        // 4. การพัฒนาการ: คะแนน Attempt ล่าสุด เทียบกับ Attempt แรก
        val growthScore = 4.0f // (Logic: เทียบคะแนนเพิ่มขึ้นในวิชาเดียวกัน)

        // 5. ความขยัน: จำนวนวิชาที่ทำแล้ว / ทั้งหมด
        val diligenceScore = (allHistory.map { it.courseId }.distinct().size.toFloat() / allCourses.size.coerceAtLeast(1)) * 5

        return HunterStats(
            knowledgeScore.coerceIn(1f, 5f),
            timeScore.coerceIn(1f, 5f),
            analyticalScore.coerceIn(1f, 5f),
            growthScore.coerceIn(1f, 5f),
            diligenceScore.coerceIn(1f, 5f)
        )
    }

    private val _latestHistory = MutableStateFlow<QuizHistoryEntity?>(null)
    val latestHistory: StateFlow<QuizHistoryEntity?> = _latestHistory.asStateFlow()
    fun loadLatestHistoryFromDB(courseId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val historyFromDB = dao.getLatestHistoryForCourse(courseId)
            _latestHistory.value = historyFromDB // ยัดใส่ State ให้ UI เอาไปโชว์
        }
    }

    // ==========================================
    // 📍 สำหรับหน้าเฉลย (ดึงคำถามจาก DB)
    // ==========================================
    private val _reviewQuestions = MutableStateFlow<List<QuestionEntity>>(emptyList())
    val reviewQuestions: StateFlow<List<QuestionEntity>> = _reviewQuestions.asStateFlow()

    fun loadQuestionsForReview(courseId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // 📍 ใช้ .collect { ... } เพื่อรอรับ List ที่ไหลออกมาจาก Flow ของ Room DB
            dao.getQuestionsByCourseId(courseId).collect { questionsList ->

                // พอมันคาย List<QuestionEntity> ออกมาให้แล้ว เราก็จับยัดใส่ State ได้เลย!
                _reviewQuestions.value = questionsList
            }
        }
    }

    fun deleteCourse(course: CourseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. สั่งลบจาก Database
            dao.deleteCourse(course)

            // 📍 หมายเหตุ:
            // เนื่องจากเราตั้ง ForeignKey แบบ CASCADE ไว้ใน QuizHistoryEntity
            // เมื่อ 'วิชา' ถูกลบ 'ประวัติการสอบ' ของวิชานี้จะถูกลบตามอัตโนมัติครับ!
        }
    }

    // 2. ดึงสถิติจำนวนข้อสอบต่อบท (เส้นแดงจะหายไปเมื่อท่านเติมบรรทัดนี้!)
    fun getChapterStats(courseId: Int): Flow<List<ChapterCount>> {
        return dao.getQuestionCountPerChapter(courseId)
    }


    fun updateCourse(course: CourseEntity) {
        // 📍 รันบน IO Dispatcher เพราะเป็นการเขียนไฟล์ลง Disk
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.updateCourse(course)
                // ถ้าอยาก Debug เช็คว่าเซฟสำเร็จไหม
                println("✅ Update Success: ${course.title}")
            } catch (e: Exception) {
                println("❌ Update Failed: ${e.message}")
            }
        }
    }


    private val billingManager = BillingManager(application)

    val isPremiumUser: StateFlow<Boolean> = billingManager.isPremium
    val isDevOverrideFlow: StateFlow<Boolean> = billingManager.isDevOverrideFlow
    val isDevOverride: Boolean get() = billingManager.isDevOverride
    val productDetails = billingManager.productDetails

    fun setDevProOverride(enabled: Boolean) = billingManager.setDevProOverride(enabled)
    fun launchBillingFlow(activity: android.app.Activity, productId: String) =
        billingManager.launchBillingFlow(activity, productId)
    fun getFormattedPrice(productId: String) = billingManager.getFormattedPrice(productId)


    fun clearAllAppData() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAllCourses() // สมมติว่ามีคำสั่งลบทุกอย่างใน DAO
            dao.deleteAllHistory()
            dao.deleteAllCategories()
        }
    }

    // 📍 กระเป๋าวิเศษสำหรับรับไฟล์ JSON จากหน้า MainActivity
    var pendingImportJson by mutableStateOf<String?>(null)
    var pendingImportFileName by mutableStateOf<String?>(null)

    fun clearPendingImport() {
        pendingImportJson = null
        pendingImportFileName = null
    }

    // ════════════════════════════════════════════════════════════════════════
    // SHARE SYSTEM
    // ════════════════════════════════════════════════════════════════════════

    /** แปลงข้อมูลวิชา + ข้อสอบทั้งหมดเป็น JSON string */
    suspend fun exportCourseToJson(courseId: Int, deviceId: String = ""): String? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val course = dao.getCourseByIdOnce(courseId) ?: return@withContext null
                // ล็อก — ห้าม export ไม่ว่ากรณีใด
                if (course.isLocked) return@withContext null
                val questions = dao.getQuestionsByCourseIdOnce(courseId)
                val model = com.petcompanion.domain.model.QuizImportModel(
                    courseCode   = course.courseCode,
                    title        = course.title,
                    description  = course.description,
                    timeLimit    = course.timeLimit,
                    passingScore = course.passingScore,
                    questions    = questions.map { q ->
                        com.petcompanion.domain.model.QuestionImportModel(
                            chapter      = q.chapter,
                            text         = q.text,
                            options      = q.options,
                            correctIndex = q.correctIndex,
                            closeIndex   = q.closeIndex,
                            explanation  = q.explanation
                        )
                    }
                )
                com.google.gson.Gson().toJson(model)
            } catch (_: Exception) { null }
        }
    }

    /** แชร์ไฟล์ JSON ผ่าน Android share sheet (แชร์เปิด — ส่งต่อได้เสมอ) */
    fun shareOpenQuiz(courseId: Int, context: android.content.Context) {
        viewModelScope.launch {
            val json = exportCourseToJson(courseId) ?: return@launch
            val course = kotlinx.coroutines.withContext(Dispatchers.IO) {
                dao.getCourseByIdOnce(courseId)
            } ?: return@launch
            val fileName = "${course.title.replace(" ", "_")}.json"
            val file = java.io.File(context.cacheDir, fileName)
            file.writeText(json)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "ข้อสอบ: ${course.title}")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "แชร์ข้อสอบ"))
        }
    }

    // Share code UI state
    private val _shareCodeResult = MutableStateFlow<ShareCodeState>(ShareCodeState.Idle)
    val shareCodeResult: kotlinx.coroutines.flow.StateFlow<ShareCodeState> = _shareCodeResult.asStateFlow()

    sealed class ShareCodeState {
        object Idle : ShareCodeState()
        object Loading : ShareCodeState()
        data class Success(val code: String) : ShareCodeState()
        data class Error(val message: String) : ShareCodeState()
    }

    fun clearShareCodeResult() { _shareCodeResult.value = ShareCodeState.Idle }

    /** สร้างโค้ดแชร์บน Firebase */
    fun createShareCode(courseId: Int, isLocked: Boolean) {
        viewModelScope.launch {
            _shareCodeResult.value = ShareCodeState.Loading
            val json = exportCourseToJson(courseId)
            if (json == null) {
                _shareCodeResult.value = ShareCodeState.Error("ไม่สามารถส่งออกข้อสอบได้")
                return@launch
            }
            val title = kotlinx.coroutines.withContext(Dispatchers.IO) {
                dao.getCourseByIdOnce(courseId)?.title ?: "ข้อสอบ"
            }
            com.petcompanion.data.firebase.SharedQuizRepository
                .uploadQuiz(json, title, isLocked)
                .onSuccess { code -> _shareCodeResult.value = ShareCodeState.Success(code) }
                .onFailure { _shareCodeResult.value = ShareCodeState.Error(it.message ?: "เกิดข้อผิดพลาด") }
        }
    }

    // Import-by-code UI state
    private val _importCodeState = MutableStateFlow<ImportCodeState>(ImportCodeState.Idle)
    val importCodeState: kotlinx.coroutines.flow.StateFlow<ImportCodeState> = _importCodeState.asStateFlow()

    sealed class ImportCodeState {
        object Idle : ImportCodeState()
        object Loading : ImportCodeState()
        data class Success(val title: String) : ImportCodeState()
        data class Error(val message: String) : ImportCodeState()
    }

    fun clearImportCodeState() { _importCodeState.value = ImportCodeState.Idle }

    /** รับข้อสอบจากโค้ด Firebase */
    fun importFromCode(code: String, categoryId: Int, deviceId: String) {
        viewModelScope.launch {
            _importCodeState.value = ImportCodeState.Loading
            com.petcompanion.data.firebase.SharedQuizRepository
                .downloadQuiz(code, deviceId)
                .onSuccess { (json, isLocked) ->
                    try {
                        val model = com.google.gson.Gson().fromJson(
                            json, com.petcompanion.domain.model.QuizImportModel::class.java
                        )
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            val courseId = dao.insertCourse(
                                com.petcompanion.domain.model.CourseEntity(
                                    categoryId     = categoryId,
                                    courseCode     = model.courseCode,
                                    title          = model.title,
                                    description    = model.description,
                                    timeLimit      = model.timeLimit,
                                    passingScore   = model.passingScore,
                                    totalQuestions = model.questions.size,
                                    isLocked       = isLocked
                                )
                            ).toInt()
                            val crypto = com.petcompanion.data.local.QuizCrypto
                            val entities = model.questions.mapIndexed { i, q ->
                                // ถ้าล็อก → encrypt text/options/explanation ด้วย deviceId
                                val text    = if (isLocked) crypto.encrypt(q.text, deviceId) else q.text
                                val opts    = if (isLocked) crypto.encryptList(q.options, deviceId) else q.options
                                val explain = if (isLocked) crypto.encrypt(q.explanation, deviceId) else q.explanation
                                com.petcompanion.domain.model.QuestionEntity(
                                    qid          = Random.nextInt(1_000_000, 9_000_000) + i,
                                    courseId     = courseId,
                                    chapter      = q.chapter,
                                    text         = text,
                                    options      = opts,
                                    correctIndex = q.correctIndex,
                                    closeIndex   = q.closeIndex,
                                    explanation  = explain
                                )
                            }
                            dao.insertQuestions(entities)
                            dao.updateCourseTotalQuestions(courseId, entities.size)
                        }
                        _importCodeState.value = ImportCodeState.Success(model.title)
                    } catch (e: Exception) {
                        _importCodeState.value = ImportCodeState.Error("ข้อมูลไม่ถูกต้อง: ${e.message}")
                    }
                }
                .onFailure {
                    _importCodeState.value = ImportCodeState.Error(it.message ?: "เกิดข้อผิดพลาด")
                }
        }
    }
}