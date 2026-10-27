package com.petcompanion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.petcompanion.data.local.AppDatabase
import com.petcompanion.domain.model.CourseEntity
import com.petcompanion.domain.model.QuestionEntity
import com.petcompanion.domain.model.QuizCategoryEntity
import com.petcompanion.domain.model.QuizImportModel
import com.petcompanion.presentation.theme.DarkColorScheme
import com.petcompanion.presentation.theme.LightColorScheme
import com.petcompanion.presentation.theme.LocalAppColors
import com.petcompanion.presentation.view.*
import com.petcompanion.presentation.viewmodel.FirebaseViewModel
import com.petcompanion.presentation.viewmodel.LevelViewModel
import com.petcompanion.presentation.viewmodel.MusicGameViewModel
import com.petcompanion.presentation.viewmodel.QuizViewModel
import com.petcompanion.presentation.viewmodel.UserViewModel

class MainActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 📍 1. ดึง Database และ Dao ออกมา
        val database = AppDatabase.getDatabase(applicationContext)
        val userProfileDao = database.userProfileDao()
        val categoryDao = database.categoryDao()
        val levelDao = database.levelDao()
        val levelProgressDao = database.levelProgressDao()
        val app = this.application
        // 📍 1. เช็คว่าแอปเคยถูกตั้งค่าภาษาไว้หรือยัง
        val currentLocales = AppCompatDelegate.getApplicationLocales()





        // 📍 2. ถ้ายังไม่เคยตั้งค่า (isEmpty) แปลว่าเพิ่งโหลดแอปมาเปิดครั้งแรก
        if (currentLocales.isEmpty) {
            // บังคับให้เป็นภาษาไทย (th) เป็นค่าเริ่มต้น!
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("th"))
        }


        enableEdgeToEdge()
        // สร้าง Notification Channel (ต้องทำก่อน Android 8+)
        com.petcompanion.notification.NotificationHelper.createChannel(this)
        // โหลด in-app notifications จาก SharedPreferences
        com.petcompanion.notification.NotificationStore.init(this)
        var importedJsonString: String? = null

        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data // นี่คือที่อยู่ของไฟล์ที่ Android ส่งมาให้

            if (uri != null) {
                try {
                    // 📍 2. อ่านไฟล์จาก URI ด้วย contentResolver (สำคัญมากสำหรับ Android รุ่นใหม่ๆ)
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        // อ่านข้อมูลทั้งหมดออกมาเป็นก้อน String
                        importedJsonString = inputStream.bufferedReader().use { it.readText() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // TODO: บอสอาจจะใส่ Toast โชว์แจ้งเตือนผู้ใช้ตรงนี้ว่า "เปิดไฟล์ไม่ได้"
                }
            }
        }



        setContent {
            MaterialTheme {

                // ── บันทึก / โหลดธีมจาก SharedPreferences ──────────────────
                val prefs = remember { getSharedPreferences("app_prefs", MODE_PRIVATE) }
                var isDarkMode by remember { mutableStateOf(prefs.getBoolean("isDarkMode", false)) }
                val currentColors = if (isDarkMode) DarkColorScheme else LightColorScheme

                var currentScreen by remember { mutableStateOf("Home") }
                var selectedTopic by remember { mutableStateOf("") }
                var finalScore by remember { mutableStateOf(0) }
                var categoryToEdit by remember { mutableStateOf<QuizCategoryEntity?>(null) }
                var quizToEdit by remember { mutableStateOf<QuestionEntity?>(null) }

                var currentCategoryId by remember { mutableStateOf(1) }
                var currentCategoryName by remember { mutableStateOf("") }
                var currentCourseId by remember { mutableStateOf(-1)}
                var previousScreen by remember { mutableStateOf("Home") }
                // จำว่า AddQuiz ถูกเปิดจากหน้าไหน เพื่อ back ถูกที่
                var quizFormReturn by remember { mutableStateOf("QuizList") }
                // จำว่าหน้าสมัครถูกเปิดจากไหน เพื่อ back ถูกที่
                var subscriptionReturn by remember { mutableStateOf("Classroom") }

                var lastQuizQuestions by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
                var lastQuizAnswers by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
                var courseToEdit by remember { mutableStateOf<CourseEntity?>(null) }
                // entity ของ custom level ที่กำลังแก้ไข (null = โหมดสร้างใหม่)
                var editLevelEntity by remember { mutableStateOf<com.petcompanion.domain.model.CustomLevelEntity?>(null) }
                // AndroidViewModel — factory จัดการ Application ให้อัตโนมัติ
                val musicGameViewModel: MusicGameViewModel = viewModel()
                val firebaseViewModel: FirebaseViewModel = viewModel()
                // ClassroomViewModel — hoist ขึ้นมาที่นี่เพื่อให้ HomeScreen ดึงข้อมูล events/homework
                val sharedCvm: com.petcompanion.presentation.viewmodel.ClassroomViewModel = viewModel()
                val homeEvents   by sharedCvm.allEvents.collectAsState()
                val homeHomework by sharedCvm.allHomework.collectAsState()
                val now = System.currentTimeMillis()
                val upcomingEvents  = remember(homeEvents)   { homeEvents.filter { it.dateTime > now }.take(5) }
                val pendingHomework = remember(homeHomework)  { homeHomework.filter { it.dueDate == 0L || it.dueDate > now }.take(5) }
                val levelViewModel: LevelViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return LevelViewModel(levelDao, levelProgressDao) as T
                        }
                    }
                )
                // key เพื่อ force recreate MusicGameScreen เมื่อเปลี่ยนด่านหรือเล่นใหม่
                var gameKey by remember { mutableStateOf(0) }
                // เลขด่านที่แสดงใน header (index ใน allLevels + 1)
                var currentLevelNumber by remember { mutableStateOf(1) }
                // 📍 2. สร้างพนักงาน ViewModel แบบถูกต้อง (ไม่โดนลบตอนหมุนจอ)
                val userViewModel: UserViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return UserViewModel(userProfileDao) as T
                        }
                    }
                )


                // 2. สร้าง ViewModel และส่ง application เข้าไปด้วย
                val quizViewModel: QuizViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST") // ใส่ไว้กันแจ้งเตือนสีเหลืองกวนใจ
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            // 📍 เพิ่ม 'application' เข้าไปเป็นตัวแรก ตามที่เราแก้ไว้ใน QuizViewModel ครับ
                            return QuizViewModel(app, categoryDao) as T
                        }
                    }
                )

                // Pro Gate — ดึงจาก quizViewModel ที่มี BillingManager อยู่แล้ว
                val isPro by quizViewModel.isPremiumUser.collectAsState()
                val isDevProActive by quizViewModel.isDevOverrideFlow.collectAsState()
                val isFirebaseConnected by firebaseViewModel.isConnected.collectAsState()
                // Badge แจ้งเตือน — observe notification list → คำนวณจำนวนที่ยังไม่อ่าน
                val allNotifications by com.petcompanion.notification.NotificationStore.notifications.collectAsState()
                val unreadNotifCount = remember(allNotifications) { allNotifications.count { !it.isRead } }

                // 📍 1. สร้าง State คุมหน้าต่างล่าง และคุม Error
                var showImportBottomSheet by remember { mutableStateOf(importedJsonString != null) }
                var jsonParseError by remember { mutableStateOf(false) }
                val context = LocalContext.current

                // 📍 2. โค้ดหน้าต่างเด้งจากด้านล่าง (Bottom Sheet)
                if (showImportBottomSheet && importedJsonString != null) {

                    ModalBottomSheet(
                        onDismissRequest = {
                            showImportBottomSheet = false
                            jsonParseError = false // ปิดแล้วเคลียร์ Error ด้วย
                        },
                        containerColor = Color(0xFF1E1E1E) // สีพื้นหลังเทาเข้ม
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .padding(bottom = 32.dp), // ดันหนีขอบจอล่าง
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("จัดการไฟล์ข้อสอบ", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

                            // แจ้งเตือนถ้าสแกนไฟล์แล้วพัง
                            if (jsonParseError) {
                                Text("❌ โครงสร้างไฟล์ไม่ถูกต้อง! (ไม่ใช่ไฟล์ข้อสอบของ Q-Growth)", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                            }

                            // 🔘 ตัวเลือกที่ 1: เอาเข้าวิชา (พร้อมสแกนโครงสร้าง)
                            Button(
                                onClick = {
                                    try {
                                        val testParse = Gson().fromJson(importedJsonString, QuizImportModel::class.java)

                                        showImportBottomSheet = false
                                        quizViewModel.pendingImportJson = importedJsonString
                                        quizViewModel.pendingImportFileName = "Imported_Quiz.json"

                                        // 📍 1. บอกระบบว่า "ไม่มีหมวดหมู่นะ (-1) ไปให้ผู้ใช้เลือกเอาดาบหน้า!"
                                        currentCategoryId = -1
                                        // 📍 2. วาร์ปพุ่งตรงไปหน้าฟอร์มเลย!
                                        quizFormReturn = "QuizList"
                                        currentScreen = "AddQuiz"

                                    } catch (e: Exception) {
                                        jsonParseError = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) // สีเขียว
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("นำเข้าสู่ระบบ (สแกนโครงสร้างก่อน)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }

                            // 🔘 ตัวเลือกที่ 2: เปิดอ่านไฟล์ดิบ
                            OutlinedButton(
                                onClick = {
                                    showImportBottomSheet = false
                                    quizViewModel.pendingImportJson = importedJsonString // ฝากไฟล์ไว้ในกระเป๋าก่อน
                                    currentScreen = "JsonViewer" // 🚀 วาร์ปไปหน้าใหม่ที่เรากำลังจะสร้าง!
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.Gray)
                            ) {
                                Icon(Icons.Default.List, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("เปิดอ่านไฟล์เนื้อหา (View Code)", color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                }

                BackHandler(enabled = true) {
                    if (currentScreen == "Home") {
                        // 🔒 อยู่หน้า Home แล้วเหรอ? ฉันไม่ทำอะไรทั้งนั้น!
                        // แอปจะนิ่งสนิท ไม่ปิดแน่นอน (จนกว่าจะกดปุ่ม Home ของเครื่อง)
                        println("Lock Home: Back button pressed, but I won't let you leave!")
                    } else {
                        // 🔄 ถ้าอยู่หน้าอื่น ก็ถอยกลับตาม Logic เดิมที่เราคุยกัน
                        when (currentScreen) {
                            "ReviewQuiz" -> currentScreen = "QuizResult"
                            "QuizResult" -> {
                                // 📍 กลับไปหน้าเดิมที่จากมา (จะเป็น History หรือ QuizList ก็ได้)
                                currentScreen = previousScreen
                            }
                            "ActiveQuiz" -> currentScreen = "StartQuiz"
                            "StartQuiz"  -> currentScreen = "QuizList"
                            "QuizList"   -> currentScreen = "Category"
                            "Category"   -> currentScreen = "Home"
                            "AddCategory" -> currentScreen = "Category"
                            "AddQuiz"     -> currentScreen = quizFormReturn
                            "QuizManage"  -> currentScreen = "Home"
                            "ManualQuiz"  -> currentScreen = "QuizManage"
                            "ReceiveCode" -> currentScreen = "QuizManage"
                            "AllSchedule" -> currentScreen = "Home"
                            "AllHomework" -> currentScreen = "Home"
                            "GameSelect"     -> currentScreen = "Home"
                            "MusicLoading"   -> currentScreen = "Home"
                            "MusicIntro"     -> currentScreen = "Home"
                            "MusicGame"      -> currentScreen = "MusicIntro"
                            "ManageLevels"   -> currentScreen = "MusicGame"
                            "AddLevel"       -> currentScreen = "ManageLevels"
                            "EditLevel"      -> currentScreen = "ManageLevels"
                            "PlayMusicGame"  -> currentScreen = "MusicGame"
                            "AiPrompt" -> currentScreen = "Settings"
                            "TeacherLogin"         -> currentScreen = "Settings"
                            "TeacherSubscription"  -> currentScreen = subscriptionReturn
                            "Classroom"         -> currentScreen = "Home"
                            "ClassSchedule"     -> currentScreen = "Classroom"
                            "ClassHomework"     -> currentScreen = "Classroom"
                            "ClassAttendance"   -> currentScreen = "Classroom"
                            "ClassGrades"       -> currentScreen = "Classroom"
                            "ClassFiles"        -> currentScreen = "Classroom"
                            "ClassArena"        -> currentScreen = "Classroom"
                            "ClassOnline"       -> currentScreen = "Classroom"
                            "Online"            -> currentScreen = "Home"
                            "Settings" -> currentScreen = "Home"
                            "Profile"  -> currentScreen = "Home"
                            "History"  -> currentScreen = "Home"
                            else -> currentScreen = "Home"
                        }
                    }
                }


            CompositionLocalProvider(LocalAppColors provides currentColors) {
                when (currentScreen) {
                    "Home" -> DarkHomeDashboard(
                        currentScreen     = currentScreen,
                        upcomingEvents    = upcomingEvents,
                        pendingHomework   = pendingHomework,
                        onScheduleClick   = { currentScreen = "AllSchedule" },
                        onHomeworkClick   = { currentScreen = "AllHomework" },
                        onQuizManageClick = {
                            if (isPro) currentScreen = "QuizManage"
                            else { subscriptionReturn = "Home"; currentScreen = "TeacherSubscription" }
                        },
                        onNavigateToCategory = { currentScreen = "Category" },
                        onProfileClick = { currentScreen = "Profile" },
                        onSettingsClick = { currentScreen = "Settings" },
                        onHistoryClick = { currentScreen = "History" },
                        onOnlineClick = { currentScreen = "Online" },
                        onNotifiClick = { currentScreen = "Notif" },
                        onMusicClick = { currentScreen = "MusicLoading" },
                        onGameClick = { currentScreen = "GameSelect" },
                        onClassroomClick = { currentScreen = "Classroom" },
                        viewModel = userViewModel,
                        unreadNotifCount = unreadNotifCount
                    )
                    "GameSelect" -> GameSelectScreen(
                        onMusicGameClick = { currentScreen = "MusicLoading" },
                        onBackClick = { currentScreen = "Home" }
                    )


                    "Category" -> QuizCategoryScreen(
                        onBackClick = { currentScreen = "Home" },
                        onCategoryClick = { id, name -> // สมมติว่าการ์ดหมวดหมู่ส่งค่า ID กับชื่อมา
                            currentCategoryId = id
                            currentCategoryName = name
                            currentScreen = "QuizList"
                        } ,
                        currentScreen = currentScreen,
                        onProfileClick = { currentScreen = "Profile" },
                        onSettingsClick = { currentScreen = "Settings" },
                        onHomeClick = { currentScreen = "Home" },
                        viewModel= quizViewModel,
                        // 📍 2. กดปุ่มบวก (+) = อยากสร้างของใหม่ (ล้างความจำซะ)
                        onAddClick = {
                            categoryToEdit = null
                            currentScreen = "AddCategory"
                        },

                        // 📍 3. แก้ตรงนี้ครับ! เปลี่ยนเป็นปีกกา { entity -> ... }
                        onEditCategory = { entity ->
                            categoryToEdit = entity      // จำข้อมูลวิชาที่โดนปัดมา
                            currentScreen = "AddCategory" // แล้วส่งทะลุไปหน้าฟอร์ม!
                        }

                    )
                    "QuizList" -> QuizListScreen(
                        categoryName = currentCategoryName,
                        onBackClick = { currentScreen = "Category" },
                        currentScreen = currentScreen,
                        onProfileClick = { currentScreen = "Profile" },
                        onSettingsClick = { currentScreen = "Settings" },
                        viewModel= quizViewModel,
                        onAddClick = {
                            quizToEdit = null
                            quizFormReturn = "QuizList"
                            currentScreen = "AddQuiz"
                        },
                        onQuizClick = { courseId ->
                            currentCourseId = courseId
                            currentScreen = "StartQuiz"
                        },
                        onEditCourse = { course ->
                            courseToEdit = course
                            currentScreen = "EditCourse"
                        },
                        onHomeClick = { currentScreen = "Home" },
                         categoryId = currentCategoryId
                    )
                    // 📍 1. หน้ายืนยัน
                    "StartQuiz" -> StartQuizScreen(
                        courseId = currentCourseId, // โยน ID วิชาให้มันไปดึงข้อมูลจริงมาโชว์
                        viewModel = quizViewModel,
                        onBackClick = { currentScreen = "QuizList" },
                        onStartClick = { id ->
                            // 📍 4. พอกดยืนยันเริ่มทำข้อสอบ ก็พุ่งไปหน้า ActiveQuiz เลย!
                            currentCourseId = id // จำ ID ไว้ใช้อีกหน้ากันเหนียว
                            currentScreen = "ActiveQuiz"
                        }
                    )
                    // 📍 2. หน้าทำข้อสอบ
                    "ActiveQuiz" -> ActiveQuizScreen(
                        courseId = currentCourseId,
                        viewModel = quizViewModel,
                        onBackClick = { currentScreen = "StartQuiz" },
                        // 📍 รับครบ 4 ตัว: score, questions, answers, และ cid (courseId)
                        onSubmitClick = { score, questions, answers, cid ->
                            finalScore = score
                            lastQuizQuestions = questions
                            lastQuizAnswers = answers
                            currentCourseId = cid // 🚩 อัปเดต ID วิชาที่เพิ่งทำเสร็จที่นี่!

                            previousScreen = "QuizList"
                            currentScreen = "QuizResult"
                        }
                    )



                    "QuizResult" -> QuizResultScreen(
                        courseId = currentCourseId, // 📍 ส่ง ID วิชาที่เพิ่งทำเสร็จเข้าไป
                        viewModel = quizViewModel,
                        onHomeClick = { currentScreen = "QuizList" }, // กลับหน้ารายการวิชา
                        onRetryClick = { id ->
                            currentCourseId = id
                            currentScreen = "StartQuiz" // เริ่มใหม่ ไปหน้าอ่านเงื่อนไขก่อน
                        },
                        onCheckAnswersClick = { answersFromDB ->
                            lastQuizAnswers = answersFromDB // อัปเดตข้อมูลให้สดใหม่! (ไม่ว่าจะเพิ่งสอบเสร็จ หรือดูย้อนหลัง)
                            currentScreen = "ReviewQuiz"    // แล้วค่อยวาร์ปไปหน้าเฉลย
                        }
                    )
                    "Settings" -> SettingsScreen(
                        isDarkMode = isDarkMode,
                        onThemeToggle = { newState ->
                            isDarkMode = newState
                            prefs.edit().putBoolean("isDarkMode", newState).apply()
                        },
                        onBackClick = { currentScreen = "Home" },
                        onAiSettingsClick = { currentScreen = "AiPrompt" },
                        onServerSetupClick = { currentScreen = "FirebaseSetup" },
                        isFirebaseConnected = isFirebaseConnected,
                        isDevProActive = isDevProActive,
                        onDevProToggle = { quizViewModel.setDevProOverride(it) },
                        isPro = isPro,
                        onSubscriptionClick = { subscriptionReturn = "Settings"; currentScreen = "TeacherSubscription" },
                        onClearDataClick = {
                            quizViewModel.clearAllAppData()
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.clear_data_success),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    "TeacherLogin" -> TeacherLoginScreen(
                        viewModel = firebaseViewModel,
                        onBackClick = { currentScreen = "Settings" },
                        onLoginSuccess = { currentScreen = "Classroom" }
                    )
                    "TeacherSubscription" -> TeacherSubscriptionScreen(
                        viewModel   = quizViewModel,
                        onBackClick = { currentScreen = subscriptionReturn },
                        onSubscribed = { currentScreen = subscriptionReturn }
                    )

                    // ── Classroom System (ViewModel สร้างแบบ lazy ที่นี่) ───────
                    "Classroom" -> {
                        val cvm = sharedCvm
                        ClassroomScreen(
                            viewModel              = cvm,
                            onBackClick            = { currentScreen = "Home" },
                            onScheduleClick        = { currentScreen = "ClassSchedule" },
                            onHomeworkClick        = { currentScreen = "ClassHomework" },
                            onAttendanceClick      = { currentScreen = "ClassAttendance" },
                            onGradesClick          = { currentScreen = "ClassGrades" },
                            onFilesClick           = { currentScreen = "ClassFiles" },
                            onArenaClick           = { currentScreen = "ClassArena" },
                            onOnlineClick          = { currentScreen = "ClassOnline" },
                            onGoToSettings         = { currentScreen = "Settings" },
                            onGoToTeacherLogin     = { currentScreen = "TeacherLogin" },
                            onGoToSubscription     = { subscriptionReturn = "Classroom"; currentScreen = "TeacherSubscription" },
                            isTeacherSubscribed    = isPro
                        )
                    }
                    "ClassSchedule" -> {
                        val cvm = sharedCvm
                        ScheduleScreen(
                            viewModel     = cvm,
                            quizViewModel = quizViewModel,
                            onBackClick   = { currentScreen = "Classroom" }
                        )
                    }
                    "ClassHomework" -> {
                        val cvm = sharedCvm
                        HomeworkScreen(viewModel = cvm, onBackClick = { currentScreen = "Classroom" })
                    }
                    // ── เข้าจากหน้าแรก: รวมทุกห้อง ───────────────────────────
                    "AllSchedule" -> {
                        ScheduleScreen(
                            viewModel     = sharedCvm,
                            quizViewModel = quizViewModel,
                            onBackClick   = { currentScreen = "Home" },
                            combinedMode  = true
                        )
                    }
                    "AllHomework" -> {
                        HomeworkScreen(
                            viewModel    = sharedCvm,
                            onBackClick  = { currentScreen = "Home" },
                            combinedMode = true
                        )
                    }
                    "ClassAttendance" -> {
                        val cvm = sharedCvm
                        AttendanceScreen(viewModel = cvm, onBackClick = { currentScreen = "Classroom" })
                    }
                    "ClassGrades" -> {
                        val cvm = sharedCvm
                        GradeManagerScreen(viewModel = cvm, onBackClick = { currentScreen = "Classroom" })
                    }
                    "ClassFiles" -> {
                        val cvm = sharedCvm
                        ClassFilesScreen(viewModel = cvm, onBackClick = { currentScreen = "Classroom" })
                    }
                    "ClassArena" -> {
                        val cvm = sharedCvm
                        ArenaScreen(
                            viewModel     = cvm,
                            quizViewModel = quizViewModel,
                            onBackClick   = { currentScreen = "Classroom" }
                        )
                    }
                    // สนามสอบออนไลน์ — เข้าจาก ClassroomScreen (roomId ยังคงอยู่)
                    "ClassOnline" -> {
                        val cvm = sharedCvm
                        OnlineScreen(
                            viewModel   = cvm,
                            onBackClick = { currentScreen = "Classroom" }
                        )
                    }

                    // --- 📍 เพิ่มหน้า AiPromptScreen เข้าไปในระบบ Navigation ---
                    "AiPrompt" -> AiPromptScreen(
                        onBackClick = { currentScreen = "Settings" } // 👈 กดกลับไปหน้าตั้งค่า
                    )
                    "Profile" -> ProfileScreen(
                        onBackClick = { currentScreen = "Home" }, // กด Back ให้กลับมาหน้า Home
                        viewModel= userViewModel
                    )
                    "AddCategory" -> CategoryFormScreen(
                        // 📍 4. หน้าฟอร์มก็จะรับค่าไปโชว์ (ถ้าเป็น null ฟอร์มก็จะว่างเปล่า)
                        categoryToEdit = categoryToEdit,
                        onBack = { currentScreen = "Category" },
                        onSaveClick = { savedCategory ->
                            quizViewModel.saveCategory(savedCategory)
                            currentScreen = "Category"
                        }
                    )
                    "AddQuiz" -> QuizFormScreen(
                        categoryId = currentCategoryId,
                        viewModel = quizViewModel,
                        application = app,
                        // 📍 ดักรับ ID ที่ส่งกลับมาจากหน้าฟอร์ม
                        onBack = { newCategoryId ->
                            currentCategoryId = newCategoryId
                            val category = quizViewModel.categoryList.value.find { it.id == newCategoryId }
                            currentCategoryName = category?.title ?: "Unknown"
                            // กลับไปหน้าที่เปิดมา (QuizList หรือ QuizManage)
                            currentScreen = quizFormReturn
                        }
                    )

                    // ── โต๊ะบริการจัดการข้อสอบ ──────────────────────────────
                    "QuizManage" -> QuizManageScreen(
                        onBackClick        = { currentScreen = "Home" },
                        onLibraryClick     = { currentScreen = "Category" },
                        onManualClick      = { currentScreen = "ManualQuiz" },
                        onImportClick      = { currentCategoryId = -1; quizFormReturn = "QuizManage"; currentScreen = "AddQuiz" },
                        onReceiveCodeClick = { currentScreen = "ReceiveCode" }
                    )
                    "ManualQuiz" -> QuizFormScreen(
                        categoryId  = -1,
                        viewModel   = quizViewModel,
                        application = app,
                        formMode    = "MANUAL",
                        onBack      = { currentScreen = "QuizManage" }
                    )
                    "ReceiveCode" -> QuizFormScreen(
                        categoryId  = -1,
                        viewModel   = quizViewModel,
                        application = app,
                        formMode    = "CODE",
                        onBack      = { currentScreen = "QuizManage" }
                    )

                    "History" -> HistoryScreen(
                        viewModel = quizViewModel,
                        onBackClick = { currentScreen = "Home" }, // 👈 เวลากด Back ให้กลับไปหน้าหลัก
                        onHistoryItemClick = { courseId ->
                            // 👈 ตามที่คุณสั่งไว้: "กด เเล้วไป หน้า QuizResultScreen อีกที"
                            currentCourseId = courseId
                            previousScreen = "History"
                            currentScreen = "QuizResult"
                        }
                    )
                    "ReviewQuiz" -> ReviewQuizScreen(
                        courseId = currentCourseId,
                        userAnswers = lastQuizAnswers,
                        onBackClick = { currentScreen = "QuizResult"} ,
                        viewModel = quizViewModel
                        // พอกดกลับ ให้ไปหน้าสรุปคะแนนเหมือนเดิม
                    )
                    "Online" -> {
                        val cvm = sharedCvm
                        OnlineScreen(
                            viewModel        = cvm,
                            onBackClick      = { currentScreen = "Home" },
                            onGoToClassroom  = { currentScreen = "Classroom" }
                        )
                    }
                    "Notif" -> NotifScreen(
                        onBackClick = { currentScreen = "Home" } // พอกดกลับ ให้ไปหน้าสรุปคะแนนเหมือนเดิม
                    )

                    "EditCourse" -> courseToEdit?.id?.let {
                        EditCourseScreen(
                            courseId = it, // ส่ง ID ไปดึงข้อมูลสดๆ
                            onBackClick = { currentScreen = "QuizList" },
                            viewModel = quizViewModel
                        )
                    }
                    "JsonViewer" -> JsonViewerScreen(
                        jsonContent = quizViewModel.pendingImportJson ?: "",
                        onBackClick = { currentScreen = "Home" }
                    )
                    "MusicLoading" -> GameLoadingScreen(
                        viewModel = musicGameViewModel,
                        onLoadingComplete = { currentScreen = "MusicIntro" }
                    )
                    "MusicIntro" -> GameIntroScreen(
                        onStartClick = { currentScreen = "MusicGame" },
                        onBackClick  = { currentScreen = "Home" }
                    )
                    "MusicGame" -> LevelSelectScreen(
                        levelViewModel = levelViewModel,
                        userViewModel = userViewModel,
                        onBackClick = { currentScreen = "MusicIntro" },
                        onLevelClick = { config ->
                            val idx = levelViewModel.allLevels.value.indexOfFirst { it.id == config.id }
                            currentLevelNumber = if (idx >= 0) idx + 1 else 1
                            musicGameViewModel.startWithConfig(config)
                            gameKey++
                            currentScreen = "PlayMusicGame"
                        },
                        onAddLevelClick = { currentScreen = "ManageLevels" }
                    )
                    "ManageLevels" -> LevelManageScreen(
                        levelViewModel = levelViewModel,
                        onBackClick = { currentScreen = "MusicGame" },
                        onAddClick  = {
                            editLevelEntity = null
                            currentScreen = "AddLevel"
                        },
                        onEditClick = { entity ->
                            editLevelEntity = entity
                            currentScreen = "EditLevel"
                        }
                    )
                    "AddLevel" -> AddLevelScreen(
                        levelViewModel  = levelViewModel,
                        onBackClick     = { currentScreen = "ManageLevels" },
                        onSaved         = { currentScreen = "ManageLevels" },
                        editEntity      = null
                    )
                    "EditLevel" -> AddLevelScreen(
                        levelViewModel  = levelViewModel,
                        onBackClick     = { currentScreen = "ManageLevels" },
                        onSaved         = { currentScreen = "ManageLevels" },
                        editEntity      = editLevelEntity
                    )
                    "PlayMusicGame" -> key(gameKey) {
                        MusicGameScreen(
                            viewModel = musicGameViewModel,
                            levelNumber = currentLevelNumber,
                            onBackClick = { currentScreen = "MusicGame" },
                            onGameComplete = { stars, score ->
                                musicGameViewModel.currentLevelConfig?.let { cfg ->
                                    levelViewModel.saveProgress(cfg.id, stars, score)
                                }
                            },
                            onNextLevel = {
                                val next = levelViewModel.getNextLevel(
                                    musicGameViewModel.currentLevelConfig?.id ?: -1
                                )
                                if (next != null) {
                                    val idx = levelViewModel.allLevels.value.indexOfFirst { it.id == next.id }
                                    currentLevelNumber = if (idx >= 0) idx + 1 else currentLevelNumber + 1
                                    musicGameViewModel.startWithConfig(next)
                                    gameKey++
                                } else {
                                    currentScreen = "MusicGame"
                                }
                            },
                            onPlayAgain = {
                                musicGameViewModel.restartGame()
                                gameKey++  // recreate screen (reset local state)
                            }
                        )
                    }



                }
            }

            }
        }
    }


}
