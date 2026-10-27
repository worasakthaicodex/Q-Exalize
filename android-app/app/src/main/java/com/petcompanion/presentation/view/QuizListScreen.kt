package com.petcompanion.presentation.view

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R
import com.petcompanion.domain.model.CourseEntity
import com.petcompanion.domain.model.QuestionEntity
import com.petcompanion.domain.model.QuizCategoryEntity
import com.petcompanion.presentation.theme.*
import com.petcompanion.presentation.viewmodel.QuizViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// --- Data Class จำลองข้อมูลชุดข้อสอบ ---
enum class QuizStatus { PASSED, FAILED, IN_PROGRESS, NOT_STARTED }
@Composable
fun QuizListScreen(
    categoryId: Int,
    categoryName: String,
    viewModel: QuizViewModel,
    onBackClick: () -> Unit,
    onQuizClick: (Int) -> Unit,
    onHomeClick: () -> Unit,
    currentScreen: String,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: (Int) -> Unit,
    onEditCourse: (CourseEntity) -> Unit // 📍 1. รับ Event สำหรับกดแก้ไข
) {
    LaunchedEffect(categoryId) {
        viewModel.setCategoryId(categoryId)
    }

    val realQuizzes        by viewModel.coursesInSelectedCategory.collectAsState()
    val shareCodeResult    by viewModel.shareCodeResult.collectAsState()
    val context            = androidx.compose.ui.platform.LocalContext.current

    var showDeleteDialog   by remember { mutableStateOf(false) }
    var courseToDelete     by remember { mutableStateOf<CourseEntity?>(null) }
    var showShareDialog    by remember { mutableStateOf(false) }
    var courseToShare      by remember { mutableStateOf<CourseEntity?>(null) }

    // แสดงโค้ดที่สร้างสำเร็จ
    var generatedCode      by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(shareCodeResult) {
        if (shareCodeResult is QuizViewModel.ShareCodeState.Success) {
            generatedCode = (shareCodeResult as QuizViewModel.ShareCodeState.Success).code
            viewModel.clearShareCodeResult()
        }
    }

    Scaffold(
        containerColor = QTheme.colors.background, // สมมติว่า QTheme.colors.background คือ Color(0xFF1E1E29)

        bottomBar = {
            Box(modifier = Modifier.navigationBarsPadding()) {
            DarkBottomNav2(
                currentScreen = currentScreen,
                onHomeClick = onHomeClick,
                onListClick = { /* อยู่หน้า List อยู่แล้ว */ },
                onAddClick = { onAddClick(categoryId) }, // โยน
                onProfileClick = onProfileClick,
                onSettingsClick = onSettingsClick
            )
        }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {

                // --- Top Bar ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = QTheme.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.label_select_course),
                        color = QTheme.colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (realQuizzes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.msg_no_data),
                                color = QTheme.colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // 📍 3. เปลี่ยนจาก CourseTopicCard มาใช้ SwipeRevealCourseCard
                items(realQuizzes, key = { it.id }) { courseEntity ->
                    SwipeRevealCourseCard(
                        course      = courseEntity,
                        onClick     = { onQuizClick(courseEntity.id) },
                        onEditClick = { onEditCourse(courseEntity) },
                        onDeleteClick = {
                            courseToDelete = courseEntity
                            showDeleteDialog = true
                        },
                        onShareClick = {
                            courseToShare = courseEntity
                            showShareDialog = true
                            viewModel.clearShareCodeResult()
                            generatedCode = null
                        }
                    )
                }
            }

    }
    }
    if (showDeleteDialog && courseToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.dialog_delete_course_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(
                        id = R.string.dialog_delete_course_desc,
                        courseToDelete?.title ?: "" // 📍 ส่งชื่อวิชาเข้าไปใน %1$s
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCourse(courseToDelete!!)
                    showDeleteDialog = false
                    courseToDelete = null
                }) {
                    Text(
                        text = stringResource(id = R.string.btn_confirm_delete),
                        color = Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(id = R.string.btn_cancel), color = Color.Gray)
                }
            }
        )
    }

    // ── Share Dialog ───────────────────────────────────────────────────────
    if (showShareDialog && courseToShare != null) {
        val isLoading = shareCodeResult is QuizViewModel.ShareCodeState.Loading
        AlertDialog(
            onDismissRequest = { if (!isLoading) { showShareDialog = false; viewModel.clearShareCodeResult() } },
            containerColor = com.petcompanion.presentation.theme.QTheme.colors.card,
            title = {
                androidx.compose.material3.Text(
                    "แชร์ข้อสอบ: ${courseToShare!!.title}",
                    color = com.petcompanion.presentation.theme.QTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isLoading) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(32.dp), color = com.petcompanion.presentation.theme.PrimaryBlue
                            )
                        }
                    } else if (shareCodeResult is QuizViewModel.ShareCodeState.Error) {
                        Text((shareCodeResult as QuizViewModel.ShareCodeState.Error).message,
                            color = com.petcompanion.presentation.theme.RedAccent)
                    } else {
                        // ── แชร์เปิด ──
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = com.petcompanion.presentation.theme.QTheme.colors.background)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("แชร์เปิด", color = com.petcompanion.presentation.theme.GreenAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("ส่งต่อได้ไม่จำกัด — ใครรับก็นำไปต่อได้เรื่อยๆ",
                                    color = com.petcompanion.presentation.theme.QTheme.colors.textSecondary, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.material3.Button(
                                        onClick = { viewModel.shareOpenQuiz(courseToShare!!.id, context); showShareDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = com.petcompanion.presentation.theme.GreenAccent),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("ส่งไฟล์", color = Color.White, fontSize = 13.sp) }
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { viewModel.createShareCode(courseToShare!!.id, isLocked = false) },
                                        modifier = Modifier.weight(1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, com.petcompanion.presentation.theme.GreenAccent)
                                    ) { Text("สร้างโค้ด", color = com.petcompanion.presentation.theme.GreenAccent, fontSize = 13.sp) }
                                }
                            }
                        }
                        // ── แชร์ล็อก ──
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = com.petcompanion.presentation.theme.QTheme.colors.background)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("แชร์ล็อก", color = com.petcompanion.presentation.theme.OrangeAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("โค้ดใช้ได้เฉพาะเครื่องแรกที่กรอก — ส่งต่อข้อสอบไม่ได้",
                                    color = com.petcompanion.presentation.theme.QTheme.colors.textSecondary, fontSize = 12.sp)
                                androidx.compose.material3.Button(
                                    onClick = { viewModel.createShareCode(courseToShare!!.id, isLocked = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = com.petcompanion.presentation.theme.OrangeAccent),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("สร้างโค้ดล็อก", color = Color.White, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShareDialog = false; viewModel.clearShareCodeResult() }) {
                    Text("ปิด", color = com.petcompanion.presentation.theme.QTheme.colors.textSecondary)
                }
            }
        )
    }

    // ── แสดงโค้ดที่ได้ ─────────────────────────────────────────────────────
    if (generatedCode != null) {
        AlertDialog(
            onDismissRequest = { generatedCode = null; showShareDialog = false },
            containerColor   = com.petcompanion.presentation.theme.QTheme.colors.card,
            title = { Text("โค้ดของคุณ", color = com.petcompanion.presentation.theme.PrimaryBlue, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ส่งโค้ดนี้ให้คนที่ต้องการรับข้อสอบ",
                        color = com.petcompanion.presentation.theme.QTheme.colors.textSecondary, fontSize = 13.sp)
                    Text(
                        generatedCode!!,
                        color = com.petcompanion.presentation.theme.PrimaryBlue,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 8.sp
                    )
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    TextButton(onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(generatedCode!!))
                    }) { Text("คัดลอกโค้ด", color = com.petcompanion.presentation.theme.PrimaryBlue) }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = { generatedCode = null; showShareDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = com.petcompanion.presentation.theme.PrimaryBlue)
                ) { Text("เสร็จแล้ว", color = Color.White) }
            }
        )
    }
}
@Composable
fun DarkBottomNav3(
    currentScreen: String, // รับชื่อหน้าปัจจุบันมา เพื่อเปลี่ยนสีไอคอน
    onHomeClick: () -> Unit,
    onListClick: () -> Unit,
    onAddClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,

    ) {
    Surface(color = QTheme.colors.card, modifier = Modifier.fillMaxWidth().height(80.dp)) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            IconButton(onClick = onHomeClick) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = if (currentScreen == "Home") PrimaryBlue else QTheme.colors.textSecondary, modifier = Modifier.size(28.dp))
            }
            // 2. List
            IconButton(onClick = onListClick) {
                Icon(Icons.Default.List, contentDescription = "List", tint = if (currentScreen == "QuizList") PrimaryBlue else QTheme.colors.textSecondary, modifier = Modifier.size(28.dp))
            }


            // 3. Add (ปุ่มบวกตรงกลาง)
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.offset(y = (-10).dp).size(55.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }


            // 4. Person (Profile)
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Default.Person, contentDescription = "Profile", tint = if (currentScreen == "Profile") PrimaryBlue else QTheme.colors.textSecondary, modifier = Modifier.size(28.dp))
            }
            // 5. Settings
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (currentScreen == "Settings") PrimaryBlue else QTheme.colors.textSecondary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun CourseTopicCard(course: CourseEntity, onClick: () -> Unit) {
    // 📍 2. ลอจิกคำนวณสถานะใหม่! (แยกเขียว-แดง)
    val status = when {
        course.progress >= 1.0f -> {
            val score = course.latestScore ?: 0
            val passCriteria = course.passingScore // ดึงเกณฑ์ผ่านจาก DB (สมมติถ้าไม่มีให้ default เป็น 60)

            if (score >= passCriteria) QuizStatus.PASSED else QuizStatus.FAILED
        }
        course.progress > 0f -> QuizStatus.IN_PROGRESS
        else -> QuizStatus.NOT_STARTED
    }

    // 📍 3. ตั้งค่าสีและไอคอนตามสถานะ (เพิ่มสีแดงสำหรับสอบตก)
    val (statusColor, statusIcon, statusText) = when (status) {
        QuizStatus.PASSED -> Triple(Color(0xFF388E3C), Icons.Default.CheckCircle, "Passed") // สีเขียว
        QuizStatus.FAILED -> Triple(Color(0xFFFF5252), Icons.Default.Cancel, "Failed") // สีแดง
        QuizStatus.IN_PROGRESS -> Triple(Color(0xFFFF9800), Icons.Default.Refresh, "In Progress") // สีส้ม
        QuizStatus.NOT_STARTED -> Triple(Color(0xFF0078D4), Icons.Default.PlayArrow, "Start") // สีฟ้า
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ไอคอนด้านซ้าย
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(26.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            // ข้อมูลตรงกลาง
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.title,
                    color = QTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${course.questionsToUse} Qs • ${course.timeLimit} mins",
                    color = QTheme.colors.textSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 📍 4. ส่วนแสดงคะแนน (แสดงทั้งตอน ผ่าน และ ตก แต่สีจะเปลี่ยนไปตามลอจิกด้านบน)
            if ((status == QuizStatus.PASSED || status == QuizStatus.FAILED) && course.latestScore != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${course.latestScore}%",
                        color = statusColor, // จะเขียวหรือแดงขึ้นอยู่กับว่าผ่านไหม
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (status == QuizStatus.PASSED) "Passed" else "Failed",
                        color = statusColor.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.1f),
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SwipeRevealCourseCard(
    course: CourseEntity,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit = {}
) {
    // 📏 ตั้งค่าระยะการปัด (ปุ่มสามอัน)
    val buttonWidth = 188.dp
    val buttonWidthPx = with(LocalDensity.current) { buttonWidth.toPx() }

    // 🔄 ตัวแปรควบคุมการเลื่อน
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 📍 1. ปุ่มด้านหลัง (โผล่ออกมาตอนปัดซ้าย)
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- ปุ่ม Share (สีน้ำเงิน) — ซ่อนถ้าข้อสอบถูกล็อก ---
            if (!course.isLocked) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2196F3))
                        .clickable {
                            coroutineScope.launch { offsetX.animateTo(0f) }
                            onShareClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // --- ปุ่ม Edit (สีเขียว) ---
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
                    .clickable {
                        coroutineScope.launch { offsetX.animateTo(0f) }
                        onEditClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            // --- ปุ่ม Delete (สีแดง) ---
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF44336))
                    .clickable {
                        coroutineScope.launch { offsetX.animateTo(0f) } // ปิดการปัดคืน
                        onDeleteClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        // 📍 2. ตัวการ์ดด้านหน้า (ที่เอานิ้วลากได้)
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                // ถ้าลากไปเกินครึ่งทาง ให้ดีดไปเปิดสุด ถ้าไม่ถึงให้ดีดกลับ
                                if (offsetX.value < -buttonWidthPx / 2) {
                                    offsetX.animateTo(-buttonWidthPx)
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                // คำนวณตำแหน่งใหม่ ไม่ให้ลากเกินขอบที่กำหนด
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-buttonWidthPx, 0f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        ) {
            // 📍 เรียกใช้ CourseTopicCard เดิมที่ท่าน CEO ทำไว้
            CourseTopicCard(
                course = course,
                onClick = {
                    if (offsetX.value < 0f) {
                        // ถ้าการ์ดถูกปัดเปิดอยู่ ให้ปิดก่อน
                        coroutineScope.launch { offsetX.animateTo(0f) }
                    } else {
                        // ถ้าการ์ดปิดอยู่ ให้ทำงานตามปกติ (เข้าทำข้อสอบ)
                        onClick()
                    }
                }
            )
        }
    }
}
