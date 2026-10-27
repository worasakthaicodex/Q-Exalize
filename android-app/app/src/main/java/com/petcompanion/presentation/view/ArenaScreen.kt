package com.petcompanion.presentation.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.StrokeCap
import com.petcompanion.presentation.theme.GreenAccent
import com.petcompanion.presentation.theme.OrangeAccent
import com.petcompanion.presentation.theme.RedAccent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.data.firebase.*
import com.petcompanion.domain.model.CourseEntity
import com.petcompanion.domain.model.QuestionEntity
import com.petcompanion.presentation.theme.PrimaryBlue
import com.petcompanion.presentation.theme.QTheme
import com.petcompanion.presentation.viewmodel.ClassroomViewModel
import com.petcompanion.presentation.viewmodel.QuizViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Colors
// ─────────────────────────────────────────────────────────────────────────────
private val GoldColor   = Color(0xFFFFD700)
private val SilverColor = Color(0xFFB0BEC5)
private val BronzeColor = Color(0xFFBF8E63)
private val ExamPurple  = Color(0xFF6A1B9A)
private val ArenaOrange = Color(0xFFE64A19)

// ─────────────────────────────────────────────────────────────────────────────
// StudentRanking helper
// ─────────────────────────────────────────────────────────────────────────────
private data class StudentRanking(
    val name: String,
    val avatarBase64: String,
    val totalScore: Double,
    val totalMax: Double,
    val gradeCount: Int
) {
    val percentage: Double get() = if (totalMax > 0) (totalScore / totalMax) * 100.0 else 0.0
}

private fun computeRankings(
    grades: List<GradeRecord>,
    students: List<StudentProfile>
): List<StudentRanking> {
    val avatarMap = students.associate { it.name to it.avatarBase64 }
    return grades
        .filter { it.studentName.isNotBlank() }
        .groupBy { it.studentName }
        .map { (name, recs) ->
            StudentRanking(
                name         = name,
                avatarBase64 = avatarMap[name] ?: "",
                totalScore   = recs.sumOf { it.score },
                totalMax     = recs.sumOf { it.maxScore },
                gradeCount   = recs.size
            )
        }
        .sortedWith(
            compareByDescending<StudentRanking> { it.percentage }.thenByDescending { it.totalScore }
        )
}

// ─────────────────────────────────────────────────────────────────────────────
// ArenaScreen — Online Exam Hub
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ArenaScreen(
    viewModel: ClassroomViewModel,
    quizViewModel: QuizViewModel,
    onBackClick: () -> Unit
) {
    val grades        by viewModel.grades.collectAsState()
    val students      by viewModel.students.collectAsState()
    val isTeacher     by viewModel.isTeacherMode.collectAsState()
    val isEffTeacher  by viewModel.isEffectiveTeacher.collectAsState()
    val exams         by viewModel.exams.collectAsState()
    val questions     by viewModel.examQuestions.collectAsState()
    val mySubmission  by viewModel.myExamSubmission.collectAsState()
    val submissions   by viewModel.examSubmissions.collectAsState()
    val teacherError  by viewModel.teacherError.collectAsState()
    val allCourses    by quizViewModel.allCourses.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(teacherError) {
        teacherError?.let { viewModel.clearTeacherError(); snackbarHostState.showSnackbar(it) }
    }

    // ── State ──────────────────────────────────────────────────────────────
    var selectedTab         by remember { mutableIntStateOf(0) }  // 0=ข้อสอบ 1=อันดับ
    var examTaking          by remember { mutableStateOf<OnlineExam?>(null) }   // นักเรียนกำลังทำ
    var examManaging        by remember { mutableStateOf<OnlineExam?>(null) }   // ครูจัดการ
    var showAddExamDialog   by remember { mutableStateOf(false) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }
    var editingQuestion     by remember { mutableStateOf<ExamQuestion?>(null) }

    val ranked = remember(grades, students) { computeRankings(grades, students) }
    val dateFmt = remember { SimpleDateFormat("d MMM HH:mm", Locale("th","TH")) }

    // ── ถ้าอยู่ใน exam taking mode → แสดง full-screen exam ───────────────
    val takingExam = examTaking
    if (takingExam != null) {
        ExamTakingScreen(
            exam        = takingExam,
            questions   = questions,
            mySubmission = mySubmission,
            showAnswers = takingExam.showAnswers,
            onSubmit    = { answers ->
                viewModel.submitExam(takingExam.id, answers, takingExam)
                // refresh my submission after submit
                viewModel.loadMyExamSubmission(takingExam.id)
            },
            onBack = {
                examTaking = null
                viewModel.selectExam(null)
                viewModel.clearMyExamSubmission()
            }
        )
        return
    }

    // ── ถ้าอยู่ใน exam manage mode (ครู) → แสดงหน้าจัดการ ────────────────
    val managingExam = examManaging
    if (managingExam != null) {
        ExamManageScreen(
            exam         = managingExam,
            questions    = questions,
            submissions  = submissions,
            courses      = allCourses,
            quizViewModel = quizViewModel,
            onPublish    = { pub -> viewModel.publishExam(managingExam, pub); examManaging = managingExam.copy(isPublished = pub) },
            onUpdateExam = { updated -> viewModel.updateExam(updated); examManaging = updated },
            onAddQuestion    = { q -> viewModel.saveQuestion(managingExam.id, q) },
            onEditQuestion   = { q -> viewModel.saveQuestion(managingExam.id, q); editingQuestion = null },
            onDeleteQuestion = { qId -> viewModel.deleteQuestion(managingExam.id, qId) },
            onImportFromBank = { qs -> viewModel.importQuestionsFromCourse(managingExam.id, qs) },
            onDelete = { viewModel.deleteExam(managingExam.id); examManaging = null },
            onBack = { examManaging = null; viewModel.selectExam(null) },
            showAddQuestionDialog = showAddQuestionDialog,
            onDismissAddQuestion = { showAddQuestionDialog = false },
            onRequestAddQuestion = { showAddQuestionDialog = true },
            editingQuestion = editingQuestion,
            onRequestEditQuestion = { editingQuestion = it },
            onDismissEditQuestion = { editingQuestion = null }
        )
        return
    }

    // ── Main Screen ────────────────────────────────────────────────────────
    Scaffold(
        containerColor = QTheme.colors.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isEffTeacher && selectedTab == 0) {
                FloatingActionButton(
                    onClick        = { showAddExamDialog = true },
                    containerColor = ExamPurple,
                    contentColor   = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "สร้างข้อสอบ") }
            }
        },
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, null, tint = QTheme.colors.textPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "🏛️ Online Exam & Arena",
                            color = QTheme.colors.textPrimary, fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            if (selectedTab == 0) "${exams.size} ข้อสอบ"
                            else "${ranked.size} นักเรียนในอันดับ",
                            color = QTheme.colors.textSecondary, fontSize = 12.sp
                        )
                    }
                    if (isEffTeacher) {
                        Surface(shape = RoundedCornerShape(6.dp), color = ExamPurple.copy(0.15f)) {
                            Text("ครู", color = ExamPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
                // ── Tabs ───────────────────────────────────────────────────
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = QTheme.colors.background,
                    contentColor     = ExamPurple
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        text = { Text("ข้อสอบ", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Assignment, null, modifier = Modifier.size(16.dp)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        text = { Text("อันดับ", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Leaderboard, null, modifier = Modifier.size(16.dp)) })
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> ExamListTab(
                exams        = exams,
                isEffTeacher = isEffTeacher,
                padding      = padding,
                dateFmt      = dateFmt,
                onTakeExam   = { exam ->
                    viewModel.selectExam(exam.id)
                    viewModel.loadMyExamSubmission(exam.id)
                    examTaking = exam
                },
                onManageExam = { exam ->
                    viewModel.selectExam(exam.id)
                    examManaging = exam
                }
            )
            1 -> LeaderboardTab(ranked = ranked, padding = padding)
        }
    }

    // ── Create Exam Dialog (ครู) ───────────────────────────────────────────
    if (showAddExamDialog) {
        CreateExamDialog(
            onDismiss = { showAddExamDialog = false },
            onConfirm = { exam ->
                viewModel.createExam(exam)
                showAddExamDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExamListTab — แสดงรายการข้อสอบ
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ExamListTab(
    exams: List<OnlineExam>,
    isEffTeacher: Boolean,
    padding: PaddingValues,
    dateFmt: SimpleDateFormat,
    onTakeExam: (OnlineExam) -> Unit,
    onManageExam: (OnlineExam) -> Unit
) {
    val activeExams  = exams.filter { it.isActive }
    val expiredExams = exams.filter { it.isPublished && it.isExpired }
    val draftExams   = exams.filter { !it.isPublished }

    if (exams.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Quiz, null,
                    tint = QTheme.colors.textSecondary, modifier = Modifier.size(72.dp))
                Text(
                    if (isEffTeacher) "ยังไม่มีข้อสอบ" else "ยังไม่มีข้อสอบที่เปิดสอบ",
                    color = QTheme.colors.textSecondary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isEffTeacher)
                    Text("กดปุ่ม + เพื่อสร้างข้อสอบใหม่",
                        color = QTheme.colors.textSecondary, fontSize = 13.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier        = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp),
        contentPadding  = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── กำลังเปิดสอบ ─────────────────────────────────────────────────
        if (activeExams.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                    Text("กำลังเปิดสอบ (${activeExams.size})",
                        color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(activeExams) { exam ->
                ActiveExamCard(
                    exam        = exam,
                    isTeacher   = isEffTeacher,
                    dateFmt     = dateFmt,
                    onTake      = { onTakeExam(exam) },
                    onManage    = { onManageExam(exam) }
                )
            }
        }

        // ── แบบร่าง (ครูเท่านั้น) ─────────────────────────────────────────
        if (isEffTeacher && draftExams.isNotEmpty()) {
            item {
                Text("แบบร่าง (${draftExams.size})",
                    color = QTheme.colors.textSecondary, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (activeExams.isNotEmpty()) 8.dp else 0.dp))
            }
            items(draftExams) { exam ->
                DraftExamCard(exam = exam, onManage = { onManageExam(exam) })
            }
        }

        // ── สอบเสร็จแล้ว ─────────────────────────────────────────────────
        if (expiredExams.isNotEmpty()) {
            item {
                Text("สอบเสร็จแล้ว (${expiredExams.size})",
                    color = QTheme.colors.textSecondary, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp))
            }
            items(expiredExams) { exam ->
                ExpiredExamCard(
                    exam      = exam,
                    isTeacher = isEffTeacher,
                    dateFmt   = dateFmt,
                    onManage  = { onManageExam(exam) }
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ActiveExamCard(
    exam: OnlineExam, isTeacher: Boolean, dateFmt: SimpleDateFormat,
    onTake: () -> Unit, onManage: () -> Unit
) {
    // countdown timer
    var millisLeft by remember(exam.id) {
        mutableLongStateOf(exam.millisRemaining())
    }
    LaunchedEffect(exam.id, exam.endTime) {
        while (millisLeft > 0) {
            delay(1000L)
            millisLeft = exam.millisRemaining()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = ExamPurple.copy(0.08f)),
        border   = androidx.compose.foundation.BorderStroke(1.5.dp, ExamPurple.copy(0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(10.dp), color = ExamPurple.copy(0.15f),
                    modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Assignment, null,
                            tint = ExamPurple, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF4CAF50).copy(0.15f)) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                Text("LIVE", color = Color(0xFF4CAF50), fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        if (exam.autoGrade) {
                            Surface(shape = RoundedCornerShape(4.dp), color = PrimaryBlue.copy(0.12f)) {
                                Text("Auto-grade", color = PrimaryBlue, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(exam.title, color = ExamPurple, fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp)
                    if (exam.description.isNotBlank())
                        Text(exam.description, color = QTheme.colors.textSecondary,
                            fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            // Countdown
            if (exam.endTime > 0L) {
                val h = millisLeft / 3_600_000L
                val m = (millisLeft % 3_600_000L) / 60_000L
                val s = (millisLeft % 60_000L) / 1_000L
                val timeStr = when {
                    h > 0   -> "%dชม. %02dน.".format(h, m)
                    m > 0   -> "%dนาที %02dวิ".format(m, s)
                    else    -> "${s}วิ"
                }
                Surface(shape = RoundedCornerShape(8.dp), color = ExamPurple.copy(0.06f)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, null, tint = ExamPurple,
                                modifier = Modifier.size(14.dp))
                            Text("เหลือเวลา", color = ExamPurple, fontSize = 11.sp)
                        }
                        Text(timeStr, color = if (millisLeft < 300_000) Color(0xFFEF5350) else ExamPurple,
                            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // Action button
            if (isTeacher) {
                OutlinedButton(
                    onClick  = onManage,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, ExamPurple)
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp), tint = ExamPurple)
                    Spacer(Modifier.width(6.dp))
                    Text("จัดการข้อสอบ", color = ExamPurple)
                }
            } else {
                Button(
                    onClick  = onTake,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ExamPurple)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("เข้าทำข้อสอบ", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DraftExamCard(exam: OnlineExam, onManage: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onManage),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Row(modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = QTheme.colors.textSecondary.copy(0.1f),
                modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.EditNote, null,
                        tint = QTheme.colors.textSecondary, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(exam.title, color = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("ยังไม่ได้ publish • กดเพื่อจัดการ",
                    color = QTheme.colors.textSecondary, fontSize = 11.sp)
            }
            Surface(shape = RoundedCornerShape(6.dp),
                color = QTheme.colors.textSecondary.copy(0.12f)) {
                Text("แบบร่าง", color = QTheme.colors.textSecondary, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = QTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ExpiredExamCard(
    exam: OnlineExam, isTeacher: Boolean, dateFmt: SimpleDateFormat, onManage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .then(if (isTeacher) Modifier.clickable(onClick = onManage) else Modifier),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Row(modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(8.dp),
                color = QTheme.colors.textSecondary.copy(0.08f),
                modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Lock, null,
                        tint = QTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(exam.title, color = QTheme.colors.textSecondary,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (exam.endTime > 0L)
                    Text("หมดเวลา ${dateFmt.format(Date(exam.endTime))}",
                        color = QTheme.colors.textSecondary.copy(0.7f), fontSize = 11.sp)
            }
            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEF5350).copy(0.1f)) {
                Text("หมดเวลา", color = Color(0xFFEF5350), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExamManageScreen — ครูจัดการข้อสอบ (เพิ่มข้อ, publish, ดู submissions)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ExamManageScreen(
    exam: OnlineExam,
    questions: List<ExamQuestion>,
    submissions: List<ExamSubmission>,
    courses: List<CourseEntity>,
    quizViewModel: QuizViewModel,
    onPublish: (Boolean) -> Unit,
    onUpdateExam: (OnlineExam) -> Unit,
    onAddQuestion: (ExamQuestion) -> Unit,
    onEditQuestion: (ExamQuestion) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onImportFromBank: (List<QuestionEntity>) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    showAddQuestionDialog: Boolean,
    onDismissAddQuestion: () -> Unit,
    onRequestAddQuestion: () -> Unit,
    editingQuestion: ExamQuestion?,
    onRequestEditQuestion: (ExamQuestion) -> Unit,
    onDismissEditQuestion: () -> Unit
) {
    var showDeleteConfirm  by remember { mutableStateOf(false) }
    var showImportDialog   by remember { mutableStateOf(false) }
    var currentExam        by remember(exam.id) { mutableStateOf(exam) }
    val totalPoints         = questions.sumOf { it.points }
    val dateFmt             = remember { SimpleDateFormat("d MMM HH:mm", Locale("th","TH")) }

    Scaffold(
        containerColor = QTheme.colors.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = QTheme.colors.textPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(currentExam.title, color = QTheme.colors.textPrimary,
                        fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${questions.size} ข้อ · ${totalPoints.toInt()} คะแนน · " +
                        "${submissions.size} คนส่งแล้ว",
                        color = QTheme.colors.textSecondary, fontSize = 12.sp
                    )
                }
                // ปุ่ม publish/unpublish
                val pubColor = if (currentExam.isPublished) Color(0xFF4CAF50) else ExamPurple
                Surface(
                    shape    = RoundedCornerShape(8.dp),
                    color    = pubColor.copy(0.15f),
                    modifier = Modifier.clickable {
                        val newPub = !currentExam.isPublished
                        onPublish(newPub)
                        currentExam = currentExam.copy(isPublished = newPub)
                    }
                ) {
                    Text(
                        if (currentExam.isPublished) "✓ Published" else "Publish",
                        color = pubColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = onRequestAddQuestion,
                containerColor = ExamPurple,
                contentColor   = Color.White
            ) { Icon(Icons.Default.Add, contentDescription = "เพิ่มข้อถาม") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            contentPadding      = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Settings card ──────────────────────────────────────────────
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)) {
                    Column(modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ตั้งค่าข้อสอบ", color = ExamPurple, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold)
                        // end time
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, null, tint = ExamPurple,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("หมดเวลา: ", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                            Text(
                                if (currentExam.endTime > 0L) dateFmt.format(Date(currentExam.endTime))
                                else "ไม่จำกัด",
                                color = QTheme.colors.textPrimary, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        // auto-grade toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-grade:", color = QTheme.colors.textSecondary, fontSize = 12.sp,
                                modifier = Modifier.weight(1f))
                            Switch(
                                checked = currentExam.autoGrade,
                                onCheckedChange = {
                                    val updated = currentExam.copy(autoGrade = it,
                                        showAnswers = if (!it) false else currentExam.showAnswers)
                                    onUpdateExam(updated); currentExam = updated
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF4CAF50))
                            )
                        }
                        // show answers toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("อนุญาตดูเฉลย:", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                                if (!currentExam.autoGrade)
                                    Text("(ต้องเปิด auto-grade ก่อน)",
                                        color = QTheme.colors.textSecondary.copy(0.6f), fontSize = 10.sp)
                            }
                            Switch(
                                checked  = currentExam.showAnswers,
                                enabled  = currentExam.autoGrade,
                                onCheckedChange = {
                                    val updated = currentExam.copy(showAnswers = it)
                                    onUpdateExam(updated); currentExam = updated
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor  = Color.White,
                                    checkedTrackColor  = ExamPurple)
                            )
                        }
                    }
                }
            }

            // ── Questions ──────────────────────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ข้อถาม (${questions.size})", color = ExamPurple, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${totalPoints.toInt()} คะแนนรวม",
                        color = QTheme.colors.textSecondary, fontSize = 11.sp)
                }
            }
            // ── Import from Quiz Bank ────────────────────────────────────
            if (courses.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick  = { showImportDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, ExamPurple.copy(0.5f))
                    ) {
                        Icon(Icons.Default.LibraryBooks, null,
                            modifier = Modifier.size(16.dp), tint = ExamPurple)
                        Spacer(Modifier.width(6.dp))
                        Text("นำเข้าจากคลังข้อสอบ",
                            color = ExamPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (questions.isEmpty()) {
                item {
                    Text("ยังไม่มีข้อถาม — กดปุ่ม + เพื่อเพิ่ม หรือนำเข้าจากคลัง",
                        color = QTheme.colors.textSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            itemsIndexed(questions) { idx, q ->
                QuestionManageCard(
                    index    = idx + 1,
                    question = q,
                    onEdit   = { onRequestEditQuestion(q) },
                    onDelete = { onDeleteQuestion(q.id) }
                )
            }

            // ── Submissions ────────────────────────────────────────────────
            if (submissions.isNotEmpty()) {
                item {
                    Text("ส่งแล้ว (${submissions.size} คน)",
                        color = ExamPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                }
                items(submissions) { sub ->
                    SubmissionCard(sub = sub, exam = currentExam)
                }
            }

            // ── Delete exam ───────────────────────────────────────────────
            item {
                TextButton(
                    onClick  = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFEF5350),
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ลบข้อสอบนี้", color = Color(0xFFEF5350), fontSize = 13.sp)
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Add question dialog
    if (showAddQuestionDialog) {
        QuestionFormDialog(
            question  = null,
            nextOrder = questions.size,
            onDismiss = onDismissAddQuestion,
            onConfirm = { q -> onAddQuestion(q); onDismissAddQuestion() }
        )
    }
    // Import from Bank dialog
    if (showImportDialog) {
        ImportFromBankDialog(
            courses      = courses,
            quizViewModel = quizViewModel,
            onDismiss    = { showImportDialog = false },
            onImport     = { qs ->
                onImportFromBank(qs)
                showImportDialog = false
            }
        )
    }

    // Edit question dialog
    editingQuestion?.let { eq ->
        QuestionFormDialog(
            question  = eq,
            nextOrder = eq.order,
            onDismiss = onDismissEditQuestion,
            onConfirm = { q -> onEditQuestion(q); onDismissEditQuestion() }
        )
    }
    // Delete exam confirm
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = QTheme.colors.card,
            icon  = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFEF5350)) },
            title = { Text("ลบข้อสอบ?", color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text("ลบ \"${currentExam.title}\" และข้อมูลทั้งหมด?",
                color = QTheme.colors.textSecondary) },
            confirmButton = {
                Button(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))) {
                    Text("ลบถาวร")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("ยกเลิก", color = QTheme.colors.textSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ImportFromBankDialog — เลือกชุดข้อสอบจากคลัง แล้ว import ขึ้น Firestore
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ImportFromBankDialog(
    courses: List<CourseEntity>,
    quizViewModel: QuizViewModel,
    onDismiss: () -> Unit,
    onImport: (List<QuestionEntity>) -> Unit
) {
    var selectedCourse   by remember { mutableStateOf<CourseEntity?>(null) }
    var loadedQuestions  by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
    var isLoading        by remember { mutableStateOf(false) }

    // โหลดคำถามทันทีที่เลือก course
    LaunchedEffect(selectedCourse?.id) {
        val courseId = selectedCourse?.id
        if (courseId != null) {
            isLoading = true
            loadedQuestions = quizViewModel.getQuestionsByCourseId(courseId).firstOrNull() ?: emptyList()
            isLoading = false
        } else {
            loadedQuestions = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        title = {
            Column {
                Text("นำเข้าจากคลังข้อสอบ", color = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (selectedCourse != null && !isLoading) {
                    Text("${loadedQuestions.size} ข้อจาก \"${selectedCourse!!.title}\"",
                        color = ExamPurple, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── รายการ courses ────────────────────────────────────────
                Text("เลือกชุดข้อสอบ:", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                courses.forEach { course ->
                    val isSelected = selectedCourse?.id == course.id
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedCourse = course },
                        shape    = RoundedCornerShape(10.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = if (isSelected) ExamPurple.copy(0.12f)
                                             else QTheme.colors.background
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(shape = CircleShape, color = ExamPurple.copy(0.15f),
                                modifier = Modifier.size(34.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.LibraryBooks, null,
                                        tint = ExamPurple, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(course.title, color = QTheme.colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 13.sp, maxLines = 2)
                                Text("${course.totalQuestions} ข้อ · ${course.timeLimit} นาที",
                                    color = QTheme.colors.textSecondary, fontSize = 11.sp)
                            }
                            if (isSelected) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp, color = ExamPurple)
                                } else {
                                    Icon(Icons.Default.CheckCircle, null,
                                        tint = ExamPurple, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // ── Preview คำถาม ─────────────────────────────────────────
                if (selectedCourse != null && !isLoading && loadedQuestions.isNotEmpty()) {
                    HorizontalDivider(color = ExamPurple.copy(0.2f))
                    Text("ตัวอย่างคำถาม (${loadedQuestions.size} ข้อ):",
                        color = ExamPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    loadedQuestions.take(3).forEachIndexed { idx, q ->
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = ExamPurple.copy(0.06f)) {
                            Row(modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${idx+1}.", color = ExamPurple, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold)
                                Text(q.text, color = QTheme.colors.textPrimary,
                                    fontSize = 11.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (loadedQuestions.size > 3) {
                        Text("และอีก ${loadedQuestions.size - 3} ข้อ...",
                            color = QTheme.colors.textSecondary, fontSize = 10.sp,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (loadedQuestions.isNotEmpty()) onImport(loadedQuestions) },
                enabled  = loadedQuestions.isNotEmpty() && !isLoading,
                colors   = ButtonDefaults.buttonColors(containerColor = ExamPurple)
            ) {
                Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("นำเข้า ${loadedQuestions.size} ข้อ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = QTheme.colors.textSecondary)
            }
        }
    )
}

@Composable
private fun QuestionManageCard(
    index: Int, question: ExamQuestion,
    onEdit: () -> Unit, onDelete: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    val typeLabel = when (question.type) {
        ExamQuestion.TYPE_CHOICE -> "ตัวเลือก"
        ExamQuestion.TYPE_BOOL   -> "ถูก/ผิด"
        else                     -> "เติมคำ"
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)) {
        Row(modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = ExamPurple.copy(0.15f),
                modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$index", color = ExamPurple, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(question.text, color = QTheme.colors.textPrimary, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(4.dp), color = ExamPurple.copy(0.1f)) {
                        Text(typeLabel, color = ExamPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                    Text("${question.points.toInt()} คะแนน",
                        color = QTheme.colors.textSecondary, fontSize = 10.sp)
                    if (question.correctAnswer.isNotBlank())
                        Text("เฉลย: ${question.correctAnswer}",
                            color = Color(0xFF4CAF50), fontSize = 10.sp)
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, null, tint = ExamPurple, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
            }
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor   = QTheme.colors.card,
            title = { Text("ลบข้อที่ $index?", color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(onClick = { onDelete(); showConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))) {
                    Text("ลบ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("ยกเลิก", color = QTheme.colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun SubmissionCard(sub: ExamSubmission, exam: OnlineExam) {
    val pct = sub.percentage
    val color = when {
        pct >= 80 -> Color(0xFF4CAF50)
        pct >= 50 -> Color(0xFFFF9800)
        else      -> Color(0xFFEF5350)
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)) {
        Row(modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarImage(avatarBase64 = "", name = sub.studentName, size = 32.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(sub.studentName, color = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (sub.isGraded)
                    Text("${sub.score.toInt()} / ${sub.maxScore.toInt()} คะแนน",
                        color = QTheme.colors.textSecondary, fontSize = 11.sp)
                else
                    Text("รอการตรวจ", color = QTheme.colors.textSecondary, fontSize = 11.sp)
            }
            if (sub.isGraded) {
                Surface(shape = RoundedCornerShape(8.dp), color = color.copy(0.15f)) {
                    Text("%.0f%%".format(pct), color = color, fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExamTakingScreen — นักเรียนทำข้อสอบ (สไตล์เดียวกับ ActiveQuizScreen)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun ExamTakingScreen(
    exam: OnlineExam,
    questions: List<ExamQuestion>,
    mySubmission: ExamSubmission?,
    showAnswers: Boolean,
    onSubmit: (Map<String, String>) -> Unit,
    onBack: () -> Unit
) {
    // ── State — ทุกอย่างต้องอยู่ก่อน early-return เพื่อ Compose rules ──────
    var submitted      by remember { mutableStateOf(mySubmission != null) }
    var result         by remember { mutableStateOf(mySubmission) }
    var started        by remember { mutableStateOf(mySubmission != null) }
    var acknowledged   by remember { mutableStateOf(false) }
    var currentIndex   by remember { mutableIntStateOf(0) }
    var answers        by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showConfirm    by remember { mutableStateOf(false) }
    var showGridDialog by remember { mutableStateOf(false) }
    var millisLeft     by remember(exam.id) { mutableLongStateOf(exam.millisRemaining()) }
    var targetSecsPerQ by remember { mutableIntStateOf(0) }

    val flaggedIds      = remember { mutableStateListOf<String>() }
    val timePerQuestion = remember { mutableStateMapOf<String, Int>() }

    // คำนวณเป้าหมาย วิ/ข้อ ทันทีที่เริ่มสอบ
    LaunchedEffect(started, exam.endTime, questions.size) {
        if (started && exam.endTime > 0L && questions.isNotEmpty()) {
            targetSecsPerQ = ((exam.endTime - System.currentTimeMillis()) / 1000L / questions.size)
                .toInt().coerceAtLeast(10)
        }
    }

    // นับเวลาต่อข้อ (เริ่มนับเมื่อ started, รีเซตเมื่อเปลี่ยนข้อ)
    LaunchedEffect(currentIndex, started, submitted) {
        if (!started || submitted || questions.isEmpty()) return@LaunchedEffect
        val qId = questions.getOrNull(currentIndex)?.id ?: return@LaunchedEffect
        while (!submitted) {
            delay(1000L)
            timePerQuestion[qId] = (timePerQuestion[qId] ?: 0) + 1
        }
    }

    // Countdown + auto-submit เมื่อหมดเวลา
    LaunchedEffect(exam.id, exam.endTime, started) {
        if (!started) return@LaunchedEffect
        while (!submitted && millisLeft > 0) {
            delay(1000L)
            millisLeft = exam.millisRemaining()
        }
        if (!submitted && answers.isNotEmpty()) {
            onSubmit(answers); submitted = true
        }
    }

    // Sync กับ mySubmission จาก parent (เช่น teacher ส่งคะแนน)
    LaunchedEffect(mySubmission) {
        if (mySubmission != null && !submitted) {
            submitted = true; result = mySubmission
        }
    }

    // ── หน้ารับทราบก่อนเข้าสอบ ─────────────────────────────────────────────
    if (!started) {
        PreExamScreen(
            exam          = exam,
            questions     = questions,
            acknowledged  = acknowledged,
            onAcknowledge = { acknowledged = it },
            onStart       = { started = true },
            onBack        = onBack
        )
        return
    }

    val currentQuestion = questions.getOrNull(currentIndex)
    val isLast = currentIndex == questions.size - 1

    // ── Grid Overview Dialog (เหมือน ActiveQuizScreen) ───────────────────────
    if (showGridDialog && questions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showGridDialog = false },
            containerColor   = QTheme.colors.card,
            title = {
                Text("ภาพรวมข้อสอบ", color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    gridItemsIndexed(questions) { idx, q ->
                        val isAnswered = q.id in answers
                        val isFlag     = q.id in flaggedIds
                        val isCurrent  = idx == currentIndex
                        Box(
                            modifier = Modifier
                                .size(45.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isCurrent) PrimaryBlue
                                    else if (isAnswered) GreenAccent
                                    else QTheme.colors.background
                                )
                                .border(
                                    width = if (isFlag) 2.dp else 1.dp,
                                    color = if (isFlag) OrangeAccent
                                            else if (isCurrent) Color.Transparent
                                            else QTheme.colors.textSecondary.copy(0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { currentIndex = idx; showGridDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${idx + 1}",
                                color = if (isCurrent || isAnswered) Color.White
                                        else QTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            if (isFlag) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(OrangeAccent)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGridDialog = false }) {
                    Text("ปิดแผงควบคุม", color = PrimaryBlue)
                }
            }
        )
    }

    // ── Scaffold (เหมือน ActiveQuizScreen — no topBar, bottomBar เท่านั้น) ──
    Scaffold(
        containerColor = QTheme.colors.background,
        bottomBar = {
            if (!submitted && questions.isNotEmpty()) {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    Surface(color = QTheme.colors.background, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { if (currentIndex > 0) currentIndex-- },
                                enabled = currentIndex > 0
                            ) {
                                Text(
                                    "ย้อนกลับ",
                                    color = if (currentIndex > 0) QTheme.colors.textPrimary
                                            else QTheme.colors.textSecondary,
                                    fontSize = 16.sp
                                )
                            }
                            if (!isLast) {
                                Button(
                                    onClick = { currentIndex++ },
                                    colors  = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                ) { Text("ถัดไป", color = Color.White) }
                            } else {
                                Button(
                                    onClick = { showConfirm = true },
                                    colors  = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                                ) { Text("ส่งข้อสอบ", color = Color.White) }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (submitted) {
            // ── Result Screen ─────────────────────────────────────────────────
            val sub = result
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
                contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item { ResultCard(sub = sub, exam = exam, onBack = onBack) }
                    if (showAnswers && sub != null && questions.isNotEmpty()) {
                        item {
                            Text("เฉลยข้อสอบ", color = PrimaryBlue, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold)
                        }
                        itemsIndexed(questions) { idx, q ->
                            AnswerReviewCard(index = idx + 1, question = q,
                                studentAnswer = sub.answers[q.id] ?: "—")
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        } else if (currentQuestion != null) {
            // ── Exam Body (layout เหมือน ActiveQuizScreen) ────────────────────
            val qId       = currentQuestion.id
            val isFlagged = qId in flaggedIds
            val timeSpent = timePerQuestion[qId] ?: 0

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // ── Header Row (manual, เหมือน ActiveQuizScreen) ──────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, "กลับ", tint = QTheme.colors.textPrimary)
                        }
                        Text("กำลังสอบ", color = QTheme.colors.textPrimary,
                            fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Grid overview box (สีเทาเข้ม เหมือน ActiveQuizScreen)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF424242))
                                .clickable { showGridDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.GridView, "ภาพรวม", tint = Color.White)
                        }
                        // Submit box (สีเขียว เหมือน ActiveQuizScreen)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF388E3C))
                                .clickable { showConfirm = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Check, "ส่งข้อสอบ", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(exam.title, color = QTheme.colors.textPrimary,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)

                // Description / Timer row (เหมือน chapter + timer ใน ActiveQuizScreen)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = exam.description.ifBlank { "ข้อสอบออนไลน์" },
                        color = QTheme.colors.textSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 16.dp)
                    )
                    if (exam.endTime > 0L) {
                        val h  = millisLeft / 3_600_000L
                        val m  = (millisLeft % 3_600_000L) / 60_000L
                        val s  = (millisLeft % 60_000L) / 1_000L
                        val ts = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
                        Text(
                            "⏱ $ts",
                            color = if (millisLeft < 60_000L) RedAccent else QTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                // Progress (เหมือน ActiveQuizScreen — StrokeCap.Round)
                LinearProgressIndicator(
                    progress   = { (currentIndex + 1).toFloat() / questions.size.coerceAtLeast(1) },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = PrimaryBlue,
                    trackColor = QTheme.colors.card,
                    strokeCap  = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(30.dp))

                // ── Question Card ──────────────────────────────────────────────
                QuestionCard(
                    index          = currentIndex + 1,
                    totalCount     = questions.size,
                    question       = currentQuestion,
                    selectedAnswer = answers[qId],
                    isFlagged      = isFlagged,
                    timeSpent      = timeSpent,
                    targetSecs     = targetSecsPerQ,
                    onAnswer       = { ans -> answers = answers + (qId to ans) },
                    onFlag         = {
                        if (isFlagged) flaggedIds.remove(qId) else flaggedIds.add(qId)
                    }
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = PrimaryBlue)
                    Text("กำลังโหลดข้อสอบ...", color = QTheme.colors.textSecondary)
                }
            }
        }
    }

    // ── Submit Dialog (เหมือน ActiveQuizScreen) ──────────────────────────────
    if (showConfirm) {
        val unanswered = questions.count { it.id !in answers }
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor   = QTheme.colors.card,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, tint = OrangeAccent,
                        modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ยืนยันการส่งข้อสอบ", color = QTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("ตรวจสอบคำตอบก่อนส่ง", color = QTheme.colors.textPrimary)
                    if (unanswered > 0 || flaggedIds.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                            border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (unanswered > 0) {
                                    Text("ยังไม่ตอบ $unanswered ข้อ",
                                        color = OrangeAccent, fontSize = 14.sp)
                                }
                                if (flaggedIds.isNotEmpty()) {
                                    Text("ปักธงไว้ ${flaggedIds.size} ข้อ",
                                        color = PrimaryBlue, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSubmit(answers); submitted = true; showConfirm = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                ) { Text("ส่งคำตอบ", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("กลับไปทำต่อ", color = QTheme.colors.textSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PreExamScreen — หน้ารับทราบก่อนเข้าสอบ (สไตล์เดียวกับ app)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PreExamScreen(
    exam: OnlineExam,
    questions: List<ExamQuestion>,
    acknowledged: Boolean,
    onAcknowledge: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("d MMM HH:mm", Locale("th", "TH")) }
    Scaffold(
        containerColor = QTheme.colors.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = QTheme.colors.textPrimary)
                }
                Text("รายละเอียดข้อสอบ", color = QTheme.colors.textPrimary,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Exam Info Card ────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card),
                shape    = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Assignment, null,
                            tint = PrimaryBlue, modifier = Modifier.size(34.dp))
                    }
                    Text(exam.title, color = QTheme.colors.textPrimary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center)
                    if (exam.description.isNotBlank()) {
                        Text(exam.description, color = QTheme.colors.textSecondary,
                            fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    // Stats row
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        PreExamStat(Icons.Rounded.Quiz,  "${questions.size}", "ข้อ")
                        PreExamStat(Icons.Rounded.Grade, "${questions.sumOf { it.points }.toInt()}", "คะแนน")
                        if (exam.endTime > 0L) {
                            PreExamStat(Icons.Rounded.Timer,
                                "${exam.millisRemaining() / 60_000L}", "นาทีที่เหลือ")
                        }
                    }
                    if (exam.endTime > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Timer, null, tint = RedAccent,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("หมดเวลา: ${dateFmt.format(Date(exam.endTime))}",
                                color = RedAccent, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Rules Card ───────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card),
                shape    = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ข้อกำหนดการสอบ", color = QTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    listOf(
                        "ห้ามออกจากหน้าข้อสอบระหว่างทำ",
                        "สามารถกลับไปแก้คำตอบได้ก่อนส่ง",
                        "เมื่อส่งแล้วไม่สามารถแก้ไขคำตอบได้",
                        "หากหมดเวลาระบบจะส่งคำตอบอัตโนมัติ",
                        "ใช้ปุ่มปักธงเพื่อทำเครื่องหมายข้อที่ยังไม่แน่ใจ"
                    ).forEach { rule ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top) {
                            Box(modifier = Modifier.padding(top = 5.dp).size(5.dp)
                                .clip(CircleShape).background(PrimaryBlue))
                            Text(rule, color = QTheme.colors.textSecondary,
                                fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }

            // ── Acknowledge Checkbox ─────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onAcknowledge(!acknowledged) },
                colors   = CardDefaults.cardColors(
                    containerColor = if (acknowledged) PrimaryBlue.copy(0.1f)
                                     else QTheme.colors.card),
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp,
                    if (acknowledged) PrimaryBlue.copy(0.5f)
                    else QTheme.colors.textSecondary.copy(0.2f))
            ) {
                Row(modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Checkbox(
                        checked         = acknowledged,
                        onCheckedChange = { onAcknowledge(it) },
                        colors          = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                    )
                    Text(
                        "ข้าพเจ้าอ่านและยอมรับข้อกำหนดแล้ว",
                        color = if (acknowledged) PrimaryBlue else QTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (acknowledged) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // ── Start Button ─────────────────────────────────────────────────
            Button(
                onClick  = onStart,
                enabled  = acknowledged && questions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = GreenAccent)
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("เริ่มทำข้อสอบ", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = Color.White)
            }
            if (questions.isEmpty()) {
                Text("ข้อสอบนี้ยังไม่มีข้อถาม",
                    color = QTheme.colors.textSecondary.copy(0.6f), fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PreExamStat(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
        }
        Text(value, color = QTheme.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = QTheme.colors.textSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QuestionCard — Card ข้อสอบ (สไตล์เดียวกับ ActiveQuizScreen)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun QuestionCard(
    index: Int,
    totalCount: Int = 0,
    question: ExamQuestion,
    selectedAnswer: String?,
    isFlagged: Boolean = false,
    timeSpent: Int = 0,
    targetSecs: Int = 0,
    onAnswer: (String) -> Unit,
    onFlag: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // ── Header (เหมือน ActiveQuizScreen — ข้อที่ X/Y + timer + flag) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (totalCount > 0) "ข้อที่ $index / $totalCount" else "ข้อที่ $index",
                        color = QTheme.colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Time tracking (เหมือน ActiveQuizScreen)
                    val isTimeExceeded = targetSecs > 0 && timeSpent > targetSecs
                    val timeColor = if (isTimeExceeded) RedAccent else OrangeAccent
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = timeColor,
                            modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (targetSecs > 0)
                                "${timeSpent}วิ / เฉลี่ย ${targetSecs}วิ"
                            else
                                "${timeSpent}วิ",
                            color = timeColor,
                            fontSize = 12.sp,
                            fontWeight = if (isTimeExceeded) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                // ปุ่มปักธง (เหมือน ActiveQuizScreen)
                if (onFlag != null) {
                    IconButton(onClick = onFlag) {
                        Icon(
                            imageVector = if (isFlagged) Icons.Rounded.Flag
                                          else Icons.Rounded.OutlinedFlag,
                            contentDescription = "Flag",
                            tint = if (isFlagged) OrangeAccent else QTheme.colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(question.text, color = QTheme.colors.textPrimary,
                fontSize = 16.sp, lineHeight = 24.sp)
            Spacer(modifier = Modifier.height(24.dp))

            // ── Answer Options (สไตล์ OptionCard เหมือน ActiveQuizScreen) ──────
            val optionLetters = listOf("A", "B", "C", "D", "E", "F")
            when (question.type) {
                ExamQuestion.TYPE_BOOL -> {
                    listOf("ถูก", "ผิด").forEachIndexed { i, opt ->
                        OnlineExamOptionCard(
                            letter     = optionLetters[i],
                            text       = opt,
                            isSelected = selectedAnswer == opt,
                            onClick    = { onAnswer(opt) }
                        )
                    }
                }
                ExamQuestion.TYPE_CHOICE -> {
                    question.choices.forEachIndexed { i, choice ->
                        OnlineExamOptionCard(
                            letter     = optionLetters.getOrElse(i) { "${i + 1}" },
                            text       = choice,
                            isSelected = selectedAnswer == choice,
                            onClick    = { onAnswer(choice) }
                        )
                    }
                }
                ExamQuestion.TYPE_SHORT -> {
                    OutlinedTextField(
                        value         = selectedAnswer ?: "",
                        onValueChange = { onAnswer(it) },
                        label         = { Text("พิมพ์คำตอบ...") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp),
                        maxLines      = 3,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PrimaryBlue,
                            unfocusedBorderColor = QTheme.colors.textSecondary.copy(0.4f),
                            focusedTextColor     = QTheme.colors.textPrimary,
                            unfocusedTextColor   = QTheme.colors.textPrimary
                        )
                    )
                }
            }
        }
    }
}

// OptionCard สำหรับข้อสอบออนไลน์ (เหมือน ActiveQuizScreen's OptionCard)
@Composable
private fun OnlineExamOptionCard(
    letter: String, text: String, isSelected: Boolean, onClick: () -> Unit
) {
    val bgColor     = if (isSelected) PrimaryBlue.copy(alpha = 0.2f) else QTheme.colors.background
    val borderColor = if (isSelected) PrimaryBlue else Color.Transparent
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onClick() },
        shape    = RoundedCornerShape(12.dp),
        border   = BorderStroke(1.dp, borderColor),
        colors   = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$letter.",
                color = if (isSelected) PrimaryBlue else QTheme.colors.textSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(30.dp)
            )
            Text(
                text,
                color = if (isSelected) PrimaryBlue else QTheme.colors.textPrimary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(10.dp),
        color     = if (selected) ExamPurple.copy(0.12f) else QTheme.colors.background,
        border    = if (selected)
            androidx.compose.foundation.BorderStroke(1.5.dp, ExamPurple)
        else
            androidx.compose.foundation.BorderStroke(1.dp, QTheme.colors.textSecondary.copy(0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick  = onClick,
                colors   = RadioButtonDefaults.colors(selectedColor = ExamPurple)
            )
            Text(text, color = if (selected) ExamPurple else QTheme.colors.textPrimary,
                fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
internal fun ResultCard(sub: ExamSubmission?, exam: OnlineExam, onBack: () -> Unit) {
    val isGraded = sub?.isGraded == true
    val pct = sub?.percentage ?: 0.0
    val color = when {
        !isGraded  -> QTheme.colors.textSecondary
        pct >= 80  -> Color(0xFF4CAF50)
        pct >= 50  -> Color(0xFFFF9800)
        else       -> Color(0xFFEF5350)
    }
    val resultIcon = when {
        !isGraded  -> Icons.Default.AccessTime
        pct >= 80  -> Icons.Default.EmojiEvents
        pct >= 50  -> Icons.Default.ThumbUp
        else       -> Icons.Default.MenuBook
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.3f))) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape  = CircleShape,
                color  = color.copy(0.12f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(resultIcon, null, tint = color, modifier = Modifier.size(40.dp))
                }
            }
            Text("ส่งสำเร็จแล้ว!", color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            if (isGraded && sub != null) {
                Text(
                    "${sub.score.toInt()} / ${sub.maxScore.toInt()} คะแนน",
                    color = QTheme.colors.textPrimary, fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Surface(shape = RoundedCornerShape(20.dp), color = color.copy(0.15f)) {
                    Text("%.1f%%".format(pct), color = color, fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                }
            } else {
                Text(
                    if (exam.autoGrade) "กำลังคำนวณคะแนน..."
                    else "ครูจะตรวจและให้คะแนนภายหลัง",
                    color = QTheme.colors.textSecondary, fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (!exam.showAnswers) {
                Surface(shape = RoundedCornerShape(8.dp), color = QTheme.colors.textSecondary.copy(0.08f)) {
                    Row(modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = QTheme.colors.textSecondary,
                            modifier = Modifier.size(14.dp))
                        Text("ครูยังไม่เปิดให้ดูเฉลย",
                            color = QTheme.colors.textSecondary, fontSize = 11.sp)
                    }
                }
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("กลับหน้าข้อสอบ") }
        }
    }
}

@Composable
internal fun AnswerReviewCard(index: Int, question: ExamQuestion, studentAnswer: String) {
    val isCorrect = studentAnswer.trim().equals(question.correctAnswer.trim(), ignoreCase = true)
    val color     = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFEF5350)

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(0.06f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.25f))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = color.copy(0.15f), modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$index", color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text(question.text, color = QTheme.colors.textPrimary, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    null, tint = color, modifier = Modifier.size(20.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("คำตอบคุณ:", color = QTheme.colors.textSecondary, fontSize = 10.sp)
                    Text(studentAnswer.ifBlank { "—" }, color = color, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                }
                if (!isCorrect && question.correctAnswer.isNotBlank()) {
                    Column {
                        Text("เฉลย:", color = QTheme.colors.textSecondary, fontSize = 10.sp)
                        Text(question.correctAnswer, color = Color(0xFF4CAF50), fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QuestionFormDialog — เพิ่ม / แก้ไข ข้อถาม
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun QuestionFormDialog(
    question: ExamQuestion?,
    nextOrder: Int,
    onDismiss: () -> Unit,
    onConfirm: (ExamQuestion) -> Unit
) {
    val isEdit = question != null
    var text          by remember { mutableStateOf(question?.text ?: "") }
    var type          by remember { mutableStateOf(question?.type ?: ExamQuestion.TYPE_CHOICE) }
    var correctAnswer by remember { mutableStateOf(question?.correctAnswer ?: "") }
    var points        by remember { mutableStateOf((question?.points ?: 1.0).toInt().toString()) }
    // choice fields (A,B,C,D)
    var choices       by remember {
        mutableStateOf(
            if (question?.choices?.size == 4) question.choices
            else listOf("", "", "", "")
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        title = {
            Text(if (isEdit) "แก้ไขข้อถาม" else "เพิ่มข้อถาม",
                color = ExamPurple, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Question text
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("ข้อถาม *") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 4,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = ExamPurple,
                        unfocusedBorderColor = QTheme.colors.textSecondary.copy(0.4f),
                        focusedTextColor     = QTheme.colors.textPrimary,
                        unfocusedTextColor   = QTheme.colors.textPrimary
                    )
                )
                // Type selector
                Text("ประเภทข้อถาม", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ExamQuestion.TYPE_CHOICE to "ตัวเลือก",
                        ExamQuestion.TYPE_BOOL   to "ถูก/ผิด",
                        ExamQuestion.TYPE_SHORT  to "เติมคำ"
                    ).forEach { (t, label) ->
                        FilterChip(
                            selected = type == t, onClick = { type = t },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ExamPurple.copy(0.2f),
                                selectedLabelColor     = ExamPurple
                            )
                        )
                    }
                }
                // Choices (เฉพาะ TYPE_CHOICE)
                if (type == ExamQuestion.TYPE_CHOICE) {
                    Text("ตัวเลือก", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                    choices.forEachIndexed { i, choice ->
                        OutlinedTextField(
                            value         = choice,
                            onValueChange = { v -> choices = choices.toMutableList().also { it[i] = v } },
                            label         = { Text("ตัวเลือก ${listOf("A","B","C","D")[i]}") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(10.dp),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = ExamPurple,
                                unfocusedBorderColor = QTheme.colors.textSecondary.copy(0.4f),
                                focusedTextColor     = QTheme.colors.textPrimary,
                                unfocusedTextColor   = QTheme.colors.textPrimary
                            )
                        )
                    }
                }
                // Correct answer
                if (type != ExamQuestion.TYPE_SHORT) {
                    Text("เฉลย (ใช้ auto-grade)", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                    if (type == ExamQuestion.TYPE_BOOL) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ถูก", "ผิด").forEach { opt ->
                                FilterChip(
                                    selected = correctAnswer == opt,
                                    onClick  = { correctAnswer = opt },
                                    label    = { Text(opt) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF4CAF50).copy(0.2f),
                                        selectedLabelColor     = Color(0xFF4CAF50)
                                    )
                                )
                            }
                        }
                    } else {
                        // TYPE_CHOICE — เลือกจาก choices
                        val validChoices = choices.filter { it.isNotBlank() }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            validChoices.forEachIndexed { i, c ->
                                FilterChip(
                                    selected = correctAnswer == c,
                                    onClick  = { correctAnswer = c },
                                    label    = { Text(listOf("A","B","C","D")[i], fontSize = 10.sp) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF4CAF50).copy(0.2f),
                                        selectedLabelColor     = Color(0xFF4CAF50)
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Short answer — free text
                    OutlinedTextField(
                        value = correctAnswer, onValueChange = { correctAnswer = it },
                        label = { Text("เฉลย (ตัวอย่าง)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color(0xFF4CAF50),
                            unfocusedBorderColor = QTheme.colors.textSecondary.copy(0.4f),
                            focusedTextColor     = QTheme.colors.textPrimary,
                            unfocusedTextColor   = QTheme.colors.textPrimary
                        )
                    )
                }
                // Points
                OutlinedTextField(
                    value = points, onValueChange = { if (it.all { c -> c.isDigit() }) points = it },
                    label = { Text("คะแนน") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(0.4f),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = ExamPurple,
                        unfocusedBorderColor = QTheme.colors.textSecondary.copy(0.4f),
                        focusedTextColor     = QTheme.colors.textPrimary,
                        unfocusedTextColor   = QTheme.colors.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            val canSave = text.isNotBlank() &&
                (type != ExamQuestion.TYPE_CHOICE || choices.count { it.isNotBlank() } >= 2)
            Button(
                onClick  = {
                    val pts = points.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 1.0
                    val finalChoices = if (type == ExamQuestion.TYPE_CHOICE)
                        choices.filter { it.isNotBlank() } else emptyList()
                    onConfirm(ExamQuestion(
                        id            = question?.id ?: "",
                        text          = text.trim(),
                        type          = type,
                        choices       = finalChoices,
                        correctAnswer = correctAnswer.trim(),
                        points        = pts,
                        order         = question?.order ?: nextOrder
                    ))
                },
                enabled  = canSave,
                colors   = ButtonDefaults.buttonColors(containerColor = ExamPurple)
            ) { Text(if (isEdit) "บันทึก" else "เพิ่มข้อ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = QTheme.colors.textSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CreateExamDialog — ครูสร้างข้อสอบใหม่ (standalone)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CreateExamDialog(onDismiss: () -> Unit, onConfirm: (OnlineExam) -> Unit) {
    var title       by remember { mutableStateOf("") }
    var desc        by remember { mutableStateOf("") }
    var autoGrade   by remember { mutableStateOf(true) }
    var showAnswers by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        icon  = { Icon(Icons.Default.Quiz, null, tint = ExamPurple) },
        title = { Text("สร้างข้อสอบใหม่", color = ExamPurple, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("ชื่อข้อสอบ *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = ExamPurple,
                        unfocusedBorderColor = QTheme.colors.textSecondary.copy(0.4f),
                        focusedTextColor     = QTheme.colors.textPrimary,
                        unfocusedTextColor   = QTheme.colors.textPrimary
                    )
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("คำอธิบาย (ไม่บังคับ)") }, maxLines = 2,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = ExamPurple,
                        unfocusedBorderColor = QTheme.colors.textSecondary.copy(0.4f),
                        focusedTextColor     = QTheme.colors.textPrimary,
                        unfocusedTextColor   = QTheme.colors.textPrimary
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ให้คะแนนอัตโนมัติ", color = QTheme.colors.textPrimary,
                        fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = autoGrade, onCheckedChange = { autoGrade = it; if(!it) showAnswers = false },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50)))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("อนุญาตดูเฉลย", color = if (autoGrade) QTheme.colors.textPrimary else QTheme.colors.textSecondary,
                        fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = showAnswers, enabled = autoGrade,
                        onCheckedChange = { if (autoGrade) showAnswers = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White,
                            checkedTrackColor = ExamPurple))
                }
                Surface(shape = RoundedCornerShape(8.dp), color = ExamPurple.copy(0.07f)) {
                    Row(modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = ExamPurple, modifier = Modifier.size(14.dp))
                        Text("หลังสร้างแล้ว ให้เพิ่มข้อถาม แล้วกด Publish เพื่อเปิดสอบ",
                            color = ExamPurple, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    onConfirm(OnlineExam(
                        title       = title.trim(),
                        description = desc.trim(),
                        autoGrade   = autoGrade,
                        showAnswers  = showAnswers,
                        isPublished = false
                    ))
                },
                enabled  = title.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = ExamPurple)
            ) { Text("สร้าง") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = QTheme.colors.textSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// LeaderboardTab — Leaderboard เดิม
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LeaderboardTab(ranked: List<StudentRanking>, padding: PaddingValues) {
    if (ranked.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.EmojiEvents, null,
                    tint = QTheme.colors.textSecondary, modifier = Modifier.size(72.dp))
                Text("ยังไม่มีข้อมูล Leaderboard",
                    color = QTheme.colors.textSecondary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("Arena จะแสดงอันดับโดยอัตโนมัติ\nเมื่อมีการให้คะแนนนักเรียน",
                    color = QTheme.colors.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        return
    }
    LazyColumn(
        modifier            = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp),
        contentPadding      = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PodiumSection(ranked = ranked.take(3)) }
        item { Text("อันดับทั้งหมด", color = ArenaOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        itemsIndexed(ranked) { index, entry -> RankingCard(rank = index + 1, entry = entry) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Podium (same as before)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PodiumSection(ranked: List<StudentRanking>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)) {
        Column(modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.EmojiEvents, null,
                    tint = GoldColor, modifier = Modifier.size(18.dp))
                Text("Podium", color = GoldColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(modifier = Modifier.fillMaxWidth().height(180.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center) {
                if (ranked.size >= 2)
                    PodiumBlock(rank = 2, entry = ranked[1], barHeight = 100.dp, color = SilverColor,
                        modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                PodiumBlock(rank = 1, entry = ranked[0], barHeight = 130.dp, color = GoldColor,
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                if (ranked.size >= 3)
                    PodiumBlock(rank = 3, entry = ranked[2], barHeight = 76.dp, color = BronzeColor,
                        modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PodiumBlock(rank: Int, entry: StudentRanking, barHeight: Dp, color: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom) {
        AvatarImage(avatarBase64 = entry.avatarBase64, name = entry.name, size = 36.dp)
        Spacer(Modifier.height(4.dp))
        Icon(Icons.Default.EmojiEvents, null, tint = color, modifier = Modifier.size(22.dp))
        Text(entry.name, color = QTheme.colors.textPrimary, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("%.0f%%".format(entry.percentage), color = color, fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Surface(modifier = Modifier.fillMaxWidth().height(barHeight),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            color = color.copy(alpha = 0.18f)) {
            Box(contentAlignment = Alignment.Center) {
                Text("$rank", color = color, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun RankingCard(rank: Int, entry: StudentRanking) {
    val rankColor = when (rank) { 1 -> GoldColor; 2 -> SilverColor; 3 -> BronzeColor; else -> QTheme.colors.textSecondary }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rank <= 3) rankColor.copy(0.07f) else QTheme.colors.card)) {
        Column {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(8.dp), color = rankColor.copy(0.15f),
                    modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (rank <= 3) {
                            Icon(Icons.Default.EmojiEvents, null,
                                tint = rankColor, modifier = Modifier.size(22.dp))
                        } else {
                            Text("#$rank", color = rankColor,
                                fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }
                AvatarImage(avatarBase64 = entry.avatarBase64, name = entry.name, size = 36.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.name, color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entry.gradeCount} รายการคะแนน",
                        color = QTheme.colors.textSecondary, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.0f / %.0f".format(entry.totalScore, entry.totalMax),
                        color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("%.1f%%".format(entry.percentage), color = rankColor, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
            LinearProgressIndicator(
                progress   = { (entry.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier   = Modifier.fillMaxWidth().height(3.dp),
                color      = rankColor,
                trackColor = rankColor.copy(0.1f)
            )
        }
    }
}
