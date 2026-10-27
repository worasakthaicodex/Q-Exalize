package com.petcompanion.presentation.view
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.presentation.theme.PrimaryBlue
import com.petcompanion.presentation.viewmodel.QuizViewModel
import androidx.compose.ui.res.stringResource
import com.petcompanion.R
import com.petcompanion.presentation.theme.QTheme

/**
 * หน้าสำหรับแก้ไขข้อมูลรายวิชา (Phase 1: เตรียมโครงสร้างไว้ก่อน)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCourseScreen(
    courseId: Int,
    viewModel: QuizViewModel,
    onBackClick: () -> Unit
) {
    val course by viewModel.getCourseById(courseId).collectAsState(initial = null)
    val chapterStats by viewModel.getChapterStats(courseId).collectAsState(initial = emptyList())





    // --- State สำหรับเก็บค่าที่แก้ ---
    var title by remember { mutableStateOf("") }
    var questionsToUse by remember { mutableStateOf("20") }
    var isShuffle by remember { mutableStateOf(true) }
    var isAntiCheat by remember { mutableStateOf(false) }
    var isPreventScreenshot by remember { mutableStateOf(false) }

    // 📍 State ใหม่ที่เพิ่มเข้ามา
    var timeLimit by remember { mutableStateOf("30") }
    var passingScore by remember { mutableStateOf("60") }
    var gradingConfig by remember { mutableStateOf("") } // เก็บ JSON หรือ Text การให้เกรด
    var isAllowReview by remember { mutableStateOf(true) }
    var isAllowRetry by remember { mutableStateOf(true) }

    LaunchedEffect(course) {
        course?.let {
            title = it.title
            questionsToUse = it.questionsToUse.toString()
            isShuffle = it.isShuffleOptions
            isAntiCheat = it.isAntiCheat
            isPreventScreenshot = it.isPreventScreenshot

            // 📍 โหลดข้อมูลใหม่ขึ้นมาโชว์
            timeLimit = it.timeLimit.toString()
            passingScore = it.passingScore.toString()
            gradingConfig = it.gradingConfigJson
            isAllowReview = it.isAllowReview
            isAllowRetry = it.isAllowRetry
        }
    }

    var gradeH by remember { mutableStateOf("70") }
    var gradeS by remember { mutableStateOf("60") }

    LaunchedEffect(course) {
        course?.let {
            // ... (โหลดค่าอื่นๆ) ...

            // ลอจิกดึงค่าจาก JSON "H:70,S:60" (ถ้ามี)
            val config = it.gradingConfigJson
            if (config.contains("H:") && config.contains("S:")) {
                val parts = config.split(",")
                gradeH = parts.find { p -> p.startsWith("H:") }?.substringAfter(":") ?: "70"
                gradeS = parts.find { p -> p.startsWith("S:") }?.substringAfter(":") ?: "60"
            }
        }
    }


    Scaffold(
        containerColor = QTheme.colors.background,

        topBar = {
            // ส่วนหัวหน้าจอ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding() // 📍 พระเอกช่วยชีวิต! สั่งให้ดึงเนื้อหาลงมาหลบ Status Bar อัตโนมัติ
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = QTheme.colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.title_edit_course),
                    color = QTheme.colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 📝 Section 1: ข้อมูลพื้นฐาน
            item {
                Text(stringResource(id = R.string.header_basic_info), color = PrimaryBlue, fontWeight = FontWeight.Bold)
                EditField(label = stringResource(id = R.string.label_course_name), value = title, onValueChange = { title = it })
            }

            // ⚙️ Section 2: กฎการสอบและเวลา
            item {
                Card(colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(id = R.string.header_quiz_settings), color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        EditField(label = stringResource(id = R.string.label_time_limit), value = timeLimit, onValueChange = { timeLimit = it })
                        EditField(label = stringResource(id = R.string.label_questions_to_use), value = questionsToUse, onValueChange = { questionsToUse = it })

                        ToggleSetting(stringResource(id = R.string.toggle_shuffle), isShuffle) { isShuffle = it }
                        ToggleSetting(stringResource(id = R.string.toggle_allow_review), isAllowReview) { isAllowReview = it }
                        ToggleSetting(stringResource(id = R.string.toggle_allow_retry), isAllowRetry) { isAllowRetry = it }
                    }
                }
            }

            // 💯 Section 3: การตัดเกรด
            item {
                Card(colors = CardDefaults.cardColors(containerColor = QTheme.colors.card), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(id = R.string.header_grading), color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        GradeInputRow(label = "H", value = gradeH, onValueChange = { gradeH = it }, color = Color(0xFFFFD700))
                        Spacer(modifier = Modifier.height(8.dp))
                        GradeInputRow(label = "S", value = gradeS, onValueChange = { gradeS = it }, color = Color(0xFF4CAF50))

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(id = R.string.grading_hint, gradeH, gradeS),
                            color = QTheme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 🛡️ Section 4: ความปลอดภัย
            item {
                Card(colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(id = R.string.header_anti_cheat), color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        ToggleSetting(stringResource(id = R.string.toggle_anti_cheat), isAntiCheat) { isAntiCheat = it }
                        ToggleSetting(stringResource(id = R.string.toggle_screenshot), isPreventScreenshot) { isPreventScreenshot = it }
                    }
                }
            }

            // 📚 Section 5: สถิติ
            item {
                Text(stringResource(id = R.string.header_summary), color = PrimaryBlue, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(id = R.string.total_bank, course?.totalQuestions ?: 0),
                    color = QTheme.colors.textPrimary, fontSize = 14.sp
                )
                Text(
                    text = stringResource(id = R.string.random_setting, questionsToUse),
                    color = if ((course?.totalQuestions ?: 0) < (questionsToUse.toIntOrNull() ?: 0)) Color.Red else Color.Green,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                chapterStats.forEach { stat ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stat.chapter, color = QTheme.colors.textPrimary, fontSize = 14.sp)
                        Text(stringResource(id = R.string.unit_items, stat.count), color = QTheme.colors.textSecondary, fontSize = 14.sp)
                    }
                }
            }

            // 💾 ปุ่มบันทึก
            item {
                Button(
                    onClick = {
                        course?.let { oldCourse ->
                            val sScore = gradeS.toIntOrNull() ?: oldCourse.passingScore
                            val updatedCourse = oldCourse.copy(
                                title = title,
                                timeLimit = timeLimit.toIntOrNull() ?: oldCourse.timeLimit,
                                questionsToUse = questionsToUse.toIntOrNull() ?: oldCourse.questionsToUse,
                                passingScore = sScore,
                                isShuffleOptions = isShuffle,
                                isAntiCheat = isAntiCheat,
                                isPreventScreenshot = isPreventScreenshot,
                                isAllowReview = isAllowReview,
                                isAllowRetry = isAllowRetry,
                                gradingConfigJson = "H:$gradeH,S:$gradeS"
                            )
                            viewModel.updateCourse(updatedCourse)
                            onBackClick()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(stringResource(id = R.string.btn_save_all), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ToggleSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = QTheme.colors.textPrimary.copy(0.8f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, color = Color.Gray) }, // สีข้อความ Label
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        textStyle = TextStyle(color = QTheme.colors.textPrimary, fontSize = 16.sp), // สีตัวอักษรที่พิมพ์
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF0078D4),   // สีขอบตอนกด (PrimaryBlue)
            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f), // สีขอบตอนไม่ได้กด
            cursorColor = Color(0xFF0078D4),          // สีตัวกระพริบพิมพ์
            focusedLabelColor = Color(0xFF0078D4)     // สี Label ตอนกด
        )
    )
}

@Composable
fun GradeInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // วงกลมสัญลักษณ์เกรด
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))
        Text("คะแนนขั้นต่ำ", color = QTheme.colors.textPrimary, modifier = Modifier.weight(1f))

        // ช่องกรอกตัวเลข (จำกัดแค่ 3 หลัก)
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 3 && it.all { char -> char.isDigit() }) onValueChange(it) },
            modifier = Modifier.width(90.dp),
            textStyle = TextStyle(color = QTheme.colors.textPrimary, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            suffix = { Text("%", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = color,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                cursorColor = color
            )
        )
    }
}