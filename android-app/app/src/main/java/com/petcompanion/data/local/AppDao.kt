package com.petcompanion.data.local

import androidx.room.*
import com.petcompanion.data.mapper.ChapterCount
import com.petcompanion.domain.model.*
import kotlinx.coroutines.flow.Flow

// จัดการข้อมูล Profile
@Dao
interface UserProfileDao {

    // 📍 1. เติมคำว่า Flow<...> ครอบตรงนี้ครับ!
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    // 📍 2. เติมคำว่า suspend กลับเข้ามาตรงนี้ครับ (เพื่อไม่ให้แอปกระตุกตอนเซฟ)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity)
}

// จัดการข้อมูล Category
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<QuizCategoryEntity>> // ใช้ Flow เพื่อให้ UI อัปเดตอัตโนมัติเมื่อข้อมูลเปลี่ยน



    @Delete
     fun deleteCategory(category: QuizCategoryEntity)

    @Query("SELECT * FROM courses")
    fun getAllCourses(): Flow<List<CourseEntity>>

    // 📍 เตรียมไว้สำหรับตอน Import ด้วยเลย!
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long // คืนค่า ID ของวิชาที่เพิ่งสร้าง

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)


    @Query("SELECT * FROM courses WHERE categoryId = :catId")
    fun getCoursesByCategoryId(catId: Int): Flow<List<CourseEntity>>

    // ดึงข้อมูลวิชา 1 ตัว
    @Query("SELECT * FROM courses WHERE id = :courseId")
    fun getCourseById(courseId: Int): Flow<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :courseId LIMIT 1")
    suspend fun getCourseByIdOnce(courseId: Int): CourseEntity?

    // ดึงข้อสอบทั้งหมดของวิชานั้น
    @Query("SELECT * FROM questions WHERE courseId = :courseId")
    fun getQuestionsByCourseId(courseId: Int): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE courseId = :courseId")
    suspend fun getQuestionsByCourseIdOnce(courseId: Int): List<QuestionEntity>

    // 📍 เพิ่มคำสั่งนี้ใน CategoryDao
    @Query("UPDATE courses SET progress = :progress, latestScore = :score WHERE id = :courseId")
    suspend fun updateCourseProgress(courseId: Int, progress: Float, score: Int)


    // 📍 1. ลบข้อสอบทิ้ง "เฉพาะบทที่กำหนด" ในวิชานั้นๆ
    @Query("DELETE FROM questions WHERE courseId = :courseId AND chapter = :chapter")
    suspend fun deleteQuestionsByCourseAndChapter(courseId: Int, chapter: String)

    // 📍 2. นับจำนวนข้อสอบทั้งหมดที่มีในวิชานั้น (เอาไว้อัปเดตยอดรวม)
    @Query("SELECT COUNT(*) FROM questions WHERE courseId = :courseId")
    suspend fun countQuestionsByCourseId(courseId: Int): Int

    // 📍 3. อัปเดตจำนวนข้อสอบรวมของวิชา
    @Query("UPDATE courses SET totalQuestions = :total WHERE id = :courseId")
    suspend fun updateCourseTotalQuestions(courseId: Int, total: Int)


    @Insert
    suspend fun insertHistory(history: QuizHistoryEntity)

    @Query("SELECT * FROM quiz_history ORDER BY timestamp DESC LIMIT 100")
    fun getAllHistory(): Flow<List<QuizHistoryEntity>>

    @Query("SELECT * FROM quiz_history WHERE courseId = :courseId ORDER BY attemptNumber ASC LIMIT 100")
    fun getHistoryByCourse(courseId: Int): Flow<List<QuizHistoryEntity>>

    // นับจำนวนรอบที่เคยทำวิชานี้มาแล้ว (เพื่อหา attemptNumber ล่าสุด)
    @Query("SELECT COUNT(*) FROM quiz_history WHERE courseId = :courseId")
    suspend fun getAttemptCount(courseId: Int): Int

    // ดึงประวัติของวิชานี้ โดยเรียงจากเวลาล่าสุด (timestamp DESC) เอามาแค่ 1 อัน (LIMIT 1)
    @Query("SELECT * FROM quiz_history WHERE courseId = :courseId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestHistoryForCourse(courseId: Int): QuizHistoryEntity?


    @Delete
    suspend fun deleteCourse(course: CourseEntity)


    @Query("SELECT chapter, COUNT(*) as count FROM questions WHERE courseId = :courseId GROUP BY chapter")
    fun getQuestionCountPerChapter(courseId: Int): Flow<List<ChapterCount>>

    @Update
    suspend fun updateCourse(course: CourseEntity)


    @Query("DELETE FROM courses") // 'courses' คือชื่อตารางของ CourseEntity
    suspend fun deleteAllCourses()

    // 📍 ลบประวัติการสอบทั้งหมด
    @Query("DELETE FROM quiz_history") // 'quiz_history' คือชื่อตารางประวัติสอบ
    suspend fun deleteAllHistory()

    // 📍 ลบข้อมูลอื่นๆ ที่ท่านมี (ตัวอย่างเพิ่มเติม)
    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    // 📍 ของเดิม (เอาไว้ใช้ตอนสร้างใหม่เท่านั้น)
    @Insert(onConflict = OnConflictStrategy.REPLACE) // หรือจะใช้ IGNORE ก็ได้
    suspend fun insertCategory(category: QuizCategoryEntity)

    // 📍 ของใหม่ที่ต้องเพิ่มเข้าไป! (เอาไว้ใช้ตอนแก้ไข)
    @Update
    suspend fun updateCategory(category: QuizCategoryEntity)
}

// จัดการด่านที่ผู้ใช้สร้างเอง
@Dao
interface LevelDao {
    @Query("SELECT * FROM custom_levels ORDER BY id")
    fun getAllCustomLevels(): Flow<List<CustomLevelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLevel(level: CustomLevelEntity)

    @Update
    suspend fun updateLevel(level: CustomLevelEntity)

    @Delete
    suspend fun deleteLevel(level: CustomLevelEntity)

    @Query("DELETE FROM custom_levels WHERE id = :levelId")
    suspend fun deleteLevelById(levelId: Int)
}

// เก็บผลคะแนน/ดาวดีสุดของแต่ละด่าน
@Dao
interface LevelProgressDao {
    @Query("SELECT * FROM level_progress")
    fun getAllProgress(): Flow<List<LevelProgressEntity>>

    @Query("SELECT * FROM level_progress WHERE levelId = :levelId LIMIT 1")
    suspend fun getProgressForLevel(levelId: Int): LevelProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: LevelProgressEntity)
}

