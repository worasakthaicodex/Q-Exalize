package com.petcompanion.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.Room
import androidx.room.TypeConverters
import com.petcompanion.data.mapper.StringListConverter
import com.petcompanion.domain.model.*


@Database(entities = [
    UserProfileEntity::class,
    QuizCategoryEntity::class,
    CourseEntity::class,
    QuestionEntity::class,
    QuizHistoryEntity::class,
    CustomLevelEntity::class,
    LevelProgressEntity::class
                     ], version = 16, exportSchema = false)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun categoryDao(): CategoryDao
    abstract fun levelDao(): LevelDao
    abstract fun levelProgressDao(): LevelProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "petcompanion_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
