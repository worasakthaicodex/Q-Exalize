package com.petcompanion.presentation.viewmodel

import android.app.Application
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import com.petcompanion.R
import com.petcompanion.domain.model.GameLevelConfig
import com.petcompanion.domain.model.GameMode

data class MusicNoteQuestion(
    val imageRes: Int,
    val correctNote: String
)

class MusicGameViewModel(application: Application) : AndroidViewModel(application) {

    // ── คลังโจทย์ ─────────────────────────────────────────────────────────────

    private val naturalNotes = listOf(
        MusicNoteQuestion(R.drawable.note_c, "C"),
        MusicNoteQuestion(R.drawable.note_d, "D"),
        MusicNoteQuestion(R.drawable.note_e, "E"),
        MusicNoteQuestion(R.drawable.note_f, "F"),
        MusicNoteQuestion(R.drawable.note_g, "G"),
        MusicNoteQuestion(R.drawable.note_a, "A"),
        MusicNoteQuestion(R.drawable.note_b, "B")
    )

    private val accidentalNotes = listOf(
        MusicNoteQuestion(R.drawable.note_c_sharp, "C#"),
        MusicNoteQuestion(R.drawable.note_d_sharp, "D#"),
        MusicNoteQuestion(R.drawable.note_f_sharp, "F#"),
        MusicNoteQuestion(R.drawable.note_g_sharp, "G#"),
        MusicNoteQuestion(R.drawable.note_a_sharp, "A#")
    )

    private val musicKeyPool = naturalNotes + accidentalNotes
    private val pianoKeyPool = naturalNotes

    private fun nextQuestion(): MusicNoteQuestion {
        val config = currentLevelConfig
        return when {
            config?.mode == GameMode.PIANO_KEYS     -> pianoKeyPool.random()
            config?.useAccidentals == true          -> musicKeyPool.random()
            else                                    -> naturalNotes.random()
        }
    }

    // ── SoundPool — โหลดล่วงหน้าทันทีที่ ViewModel ถูกสร้าง ──────────────────

    private val soundPool = SoundPool.Builder().setMaxStreams(6).build()
    private val soundMap  = mutableMapOf<String, Int>()
    private val mainHandler = Handler(Looper.getMainLooper())

    val totalSounds = 13          // จำนวนเสียงทั้งหมดที่ต้องโหลด
    var soundsLoaded by mutableStateOf(0)
        private set
    val loadingProgress get() = soundsLoaded.toFloat() / totalSounds

    init {
        val ctx = application.applicationContext
        soundPool.setOnLoadCompleteListener { pool, sampleId, status ->
            if (status == 0) {
                pool.play(sampleId, 0f, 0f, 0, 0, 1f)   // warm-up เงียบ
                mainHandler.post { soundsLoaded++ }       // อัปเดต progress บน main thread
            }
        }
        // โน้ตธรรมชาติ
        soundMap["C"]  = soundPool.load(ctx, R.raw.piano_c, 1)
        soundMap["D"]  = soundPool.load(ctx, R.raw.piano_d, 1)
        soundMap["E"]  = soundPool.load(ctx, R.raw.piano_e, 1)
        soundMap["F"]  = soundPool.load(ctx, R.raw.piano_f, 1)
        soundMap["G"]  = soundPool.load(ctx, R.raw.piano_g, 1)
        soundMap["A"]  = soundPool.load(ctx, R.raw.piano_a, 1)
        soundMap["B"]  = soundPool.load(ctx, R.raw.piano_b, 1)
        // โน้ต Sharp
        soundMap["C#"] = soundPool.load(ctx, R.raw.piano_c_sharp, 1)
        soundMap["D#"] = soundPool.load(ctx, R.raw.piano_d_sharp, 1)
        soundMap["F#"] = soundPool.load(ctx, R.raw.piano_f_sharp, 1)
        soundMap["G#"] = soundPool.load(ctx, R.raw.piano_g_sharp, 1)
        soundMap["A#"] = soundPool.load(ctx, R.raw.piano_a_sharp, 1)
        // เสียงสำเร็จ
        soundMap["success"] = soundPool.load(ctx, R.raw.success, 1)
    }

    /** เล่นเสียงโน้ต */
    fun playNote(note: String) {
        soundMap[note]?.let { soundPool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    /** เล่นเสียงสำเร็จ (จบด่าน) */
    fun playSuccess() {
        soundMap["success"]?.let { soundPool.play(it, 1f, 1f, 2, 0, 1f) }
    }

    override fun onCleared() {
        super.onCleared()
        soundPool.release()
    }

    // ── Game State ────────────────────────────────────────────────────────────

    var currentLevelConfig: GameLevelConfig? by mutableStateOf(null)
        private set
    var score by mutableStateOf(0)
        private set
    var timeRemaining by mutableStateOf(60)
        private set
    var currentQuestion by mutableStateOf(naturalNotes.random())
        private set
    var comboCount by mutableStateOf(0)
        private set

    fun startWithConfig(config: GameLevelConfig) {
        currentLevelConfig = config
        score = 0
        timeRemaining = config.timeSeconds
        comboCount = 0
        currentQuestion = nextQuestion()
    }

    fun tickTime() {
        if (timeRemaining > 0) timeRemaining -= 1
    }

    fun checkAnswer(selectedNote: String): Boolean {
        if (timeRemaining <= 0) return false

        val penalty        = currentLevelConfig?.wrongPenalty   ?: 5
        val comboThreshold = currentLevelConfig?.comboThreshold ?: 0
        val comboBonus     = currentLevelConfig?.comboBonus     ?: 0

        return if (selectedNote == currentQuestion.correctNote) {
            score += 10
            comboCount++
            if (comboThreshold > 0 && comboCount % comboThreshold == 0) score += comboBonus
            currentQuestion = nextQuestion()
            true
        } else {
            if (score > 0) score = maxOf(0, score - penalty)
            comboCount = 0
            false
        }
    }

    fun computeStars(): Int = currentLevelConfig?.starsForScore(score) ?: 0

    fun restartGame() {
        score = 0
        timeRemaining = currentLevelConfig?.timeSeconds ?: 60
        comboCount = 0
        currentQuestion = nextQuestion()
    }
}
