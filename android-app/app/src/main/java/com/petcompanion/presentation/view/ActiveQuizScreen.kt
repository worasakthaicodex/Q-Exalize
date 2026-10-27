package com.petcompanion.presentation.view
import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.petcompanion.domain.model.CourseEntity
import com.petcompanion.domain.model.QuestionEntity
import com.petcompanion.presentation.theme.*
import com.petcompanion.presentation.viewmodel.QuizViewModel
import kotlinx.coroutines.delay


import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.petcompanion.R

@Composable
fun ActiveQuizScreen(
    courseId: Int,
    viewModel: QuizViewModel,
    onBackClick: () -> Unit,
    onSubmitClick: (Int, List<QuestionEntity>, Map<Int, Int>, Int) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var course by remember { mutableStateOf<CourseEntity?>(null) }
    var quizQuestions by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var currentIndex by remember { mutableStateOf(0) }
    val selectedAnswers = remember { mutableStateMapOf<Int, Int>() }
    val shuffledOptionIndices = remember { mutableStateMapOf<Int, List<Int>>() }
    var timeLeftSeconds by remember { mutableIntStateOf(0) }
    var hasCheated by remember { mutableStateOf(false) }

    // 📍 Feature 1 & 2: State สำหรับเก็บข้อที่ปักธง และ เวลาที่ใช้แต่ละข้อ
    val flaggedQuestions = remember { mutableStateListOf<Int>() }
    val timePerQuestion = remember { mutableStateMapOf<Int, Int>() }

    val avgTimePerSec = remember(course, quizQuestions.size) {
        val limitMinutes = course?.timeLimit ?: 0
        val totalQ = quizQuestions.size
        if (limitMinutes > 0 && totalQ > 0) (limitMinutes * 60) / totalQ else 0
    }


    // 📍 Feature 3: State สำหรับเปิด/ปิด หน้าต่างแผงควบคุม (Grid)
    var showGridDialog by remember { mutableStateOf(false) }
    var showSubmitConfirmDialog by remember { mutableStateOf(false) }
    // 📍 1. ระบบป้องกันการแคปจอ (ทำงานเมื่อวิชานั้นเปิด isPreventScreenshot เท่านั้น)

    // 📍 1. ระบบป้องกันการแคปจอ (ทำงานเมื่อวิชานั้นเปิด isPreventScreenshot เท่านั้น)
    DisposableEffect(course) {
        val preventScreenshot = course?.isPreventScreenshot == true

        if (preventScreenshot) {
            // ถ้าเปิดระบบ ให้ใส่ Flag กันแคปจอ
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            // 📍 [บรรทัดที่ต้องเพิ่ม!] ถ้าวิชานี้ปิดอยู่ หรือเป็นวิชาใหม่ที่ค่ายังเป็น null
            // ต้องสั่ง "ถอด" Flag ออกเสมอ เพื่อล้างสถานะเดิมทิ้งไปครับ!
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        onDispose {
            // คืนค่าหน้าจอให้ปกติเสมอเมื่อออกจากการสอบ
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }



    // 📍 2. ระบบจับโกงสลับแอป (ทำงานเมื่อวิชานั้นเปิด isAntiCheat เท่านั้น)
    DisposableEffect(course, lifecycleOwner) {
        val isAntiCheatActive = course?.isAntiCheat == true

        val observer = LifecycleEventObserver { _, event ->
            // จับโกงเฉพาะตอนที่ดึงข้อสอบเสร็จแล้ว (isLoaded) + เปิดโหมดห้ามสลับแอป + พับจอ (ON_PAUSE)
            if (isAntiCheatActive && event == Lifecycle.Event.ON_PAUSE && isLoaded) {
                hasCheated = true
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    val msgNoQuestions = stringResource(id = R.string.error_no_questions)
    val msgNotFound = stringResource(id = R.string.error_course_not_found)
    LaunchedEffect(courseId) {
        val (fetchedCourse, fetchedQuestions) = viewModel.getQuizSessionData(courseId)
        if (fetchedCourse != null) {
            course = fetchedCourse
            if (fetchedQuestions.isNotEmpty()) {

                // 📍 1. ดึงโควต้าข้อสอบจาก Database ที่บอสตั้งไว้ (questionsToUse)
                // ถ้าค่าใน DB ดันตั้งไว้เยอะกว่าข้อสอบที่มีจริง ก็ดึงมาเท่าที่มีเพื่อกันแอปเด้ง
                val targetQuota = fetchedCourse.questionsToUse
                val quota = if (fetchedQuestions.size < targetQuota) fetchedQuestions.size else targetQuota

                // 📍 2. ลอจิกการสุ่มแบบกระจายบท (Stratified Random)
                val isShuffleQuestionsEnabled = true // สมมติว่ามีเช็คเปิด/ปิดสับข้อสอบ

                val finalQuestions = if (isShuffleQuestionsEnabled) {
                    // จัดกลุ่มข้อสอบตาม Chapter
                    val questionsByChapter = fetchedQuestions.groupBy { it.chapter }
                    val chaptersCount = questionsByChapter.size

                    // คำนวณโควต้าเบื้องต้นให้แต่ละบท (พยายามหารให้ลงตัวที่สุด)
                    val baseQuotaPerChapter = if (chaptersCount > 0) quota / chaptersCount else 0

                    val selectedQuestions = mutableListOf<QuestionEntity>()

                    // สุ่มดึงข้อสอบจากแต่ละบทตามโควต้าเบื้องต้น
                    for ((chapter, questionsInChapter) in questionsByChapter) {
                        selectedQuestions.addAll(questionsInChapter.shuffled().take(baseQuotaPerChapter))
                    }

                    // ถ้ายังได้ข้อสอบไม่ครบตามโควต้ารวม (เพราะเศษหารไม่ลงตัว หรือบางบทข้อน้อยกว่าโควต้า)
                    // ให้เอาข้อสอบที่ "เหลือ" ของทุกบท มารวมกัน สับไพ่ แล้วเติมให้เต็ม
                    val remainingNeeded = quota - selectedQuestions.size
                    if (remainingNeeded > 0) {
                        val poolOfRemaining = fetchedQuestions.filter { it !in selectedQuestions }
                        selectedQuestions.addAll(poolOfRemaining.shuffled().take(remainingNeeded))
                    }

                    // สับไพ่ทั้งหมดอีกรอบ ก่อนโชว์ให้นักเรียนสอบ
                    selectedQuestions.shuffled()
                } else {
                    // ถ้าวิชานี้ไม่ได้เปิดโหมดสับข้อสอบ ก็แค่ตัดมา 20 ข้อเรียงตามลำดับดื้อๆ
                    fetchedQuestions.take(quota)
                }

                quizQuestions = finalQuestions

                // 📍 ลอจิกเตรียมลำดับตัวเลือก ก, ข, ค (เหมือนของเดิมบอส)
                val isShuffleOptionsEnabled = fetchedCourse.isShuffleOptions == true
                finalQuestions.forEach { q ->
                    val originalIndices = q.options.indices.toList()
                    shuffledOptionIndices[q.qid] = if (isShuffleOptionsEnabled) {
                        originalIndices.shuffled()
                    } else {
                        originalIndices
                    }
                }

                timeLeftSeconds = fetchedCourse.timeLimit * 60
                isLoaded = true
            } else {
                errorMessage = msgNoQuestions
            }
        }
    }
    var isSubmitting by remember { mutableStateOf(false) }

    val finalSubmit = {
        if (!isSubmitting) {
            isSubmitting = true
            val correctCount = quizQuestions.count { q -> selectedAnswers[q.qid] == q.correctIndex }
            val closeCount = quizQuestions.count { q -> q.closeIndex != null && selectedAnswers[q.qid] == q.closeIndex }
            val percentage = if (quizQuestions.isNotEmpty()) ((correctCount.toFloat() / quizQuestions.size) * 100).toInt() else 0
            val isPassed = correctCount >= (course?.passingScore ?: 0)
            val timeLimitSeconds = (course?.timeLimit ?: 0) * 60
            val timeSpent = if (timeLimitSeconds > 0) timeLimitSeconds - timeLeftSeconds else 0

            viewModel.submitQuizResult(
                courseId = courseId, percentageScore = percentage, timeSpent = timeSpent,
                correctCount = correctCount, totalQ = quizQuestions.size, isPassed = isPassed,
                closeCount = closeCount, isTimeout = timeLeftSeconds <= 0,
                selectedAnswers = selectedAnswers.toMap()
            )

            onSubmitClick(percentage, quizQuestions, selectedAnswers, courseId)
        }
    }

    // 📍 Feature 4: Timer แบบใหม่ บวกเวลาให้ข้อที่กำลังเปิดอยู่ทุกๆ 1 วินาที
    LaunchedEffect(isLoaded, hasCheated) {
        while (timeLeftSeconds > 0 && isLoaded && !hasCheated) {
            delay(1000L)
            timeLeftSeconds--
            // จับเวลาเฉพาะข้อที่กำลังเปิดอยู่
            val currentQid = quizQuestions.getOrNull(currentIndex)?.qid
            if (currentQid != null) {
                timePerQuestion[currentQid] = (timePerQuestion[currentQid] ?: 0) + 1
            }
        }
        if (timeLeftSeconds <= 0 && isLoaded && !hasCheated) {
            finalSubmit()
        }
    }

    // 📍 1. ส่งค่า 0 ทันทีเมื่อโกง (ไม่ต้องรอให้กดปุ่ม)
    LaunchedEffect(hasCheated) {
        if (hasCheated && !isSubmitting) {
            isSubmitting = true
            viewModel.submitQuizResult(
                courseId = courseId,
                percentageScore = 0,
                timeSpent = 0,
                correctCount = 0,
                totalQ = quizQuestions.size,
                isPassed = false,
                closeCount = 0,
                isTimeout = false,
                selectedAnswers = emptyMap()
            )
        }
    }

// 📍 2. UI จับโกงที่ดึงค่าจาก Strings.xml และทำหน้าที่เป็นแค่ "พิธีการ"
    if (hasCheated) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(QTheme.colors.background)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.ReportProblem,
                contentDescription = "Cheat Detected",
                tint = RedAccent,
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // แสดงผล 2 ภาษาพร้อมกันเพื่อความชัดเจน
            Text(
                text = "${stringResource(id = R.string.anticheat_title)}",
                color = QTheme.colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${stringResource(id = R.string.anticheat_desc)}",
                color = QTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    // 📍 ปุ่มนี้แค่ทำหน้าที่พาวาร์ปไปหน้าสรุปผล (เพราะข้อมูลถูกส่งไปแล้วใน LaunchedEffect)
                    onSubmitClick(0, quizQuestions, emptyMap(), courseId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(65.dp)
            ) {
                Text(
                    text = "${stringResource(id = R.string.anticheat_btn)}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // --- 🚨 2. UI เมื่อเกิดข้อผิดพลาด (เช่น ไม่มีข้อสอบ) ---
    if (errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize().background(QTheme.colors.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = errorMessage!!, color = RedAccent, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBackClick) { Text("กลับไปหน้าหลัก") }
            }
        }
        return
    }

    // --- ⏳ 3. UI ระหว่างโหลดข้อมูล (Loading State) ---
    if (!isLoaded || course == null) {
        Box(modifier = Modifier.fillMaxSize().background(QTheme.colors.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryBlue)
        }
        return
    }

    val minutes = String.format("%02d", timeLeftSeconds / 60)
    val seconds = String.format("%02d", timeLeftSeconds % 60)
    val currentQuestion = quizQuestions[currentIndex]
    val currentQid = currentQuestion.qid

    // 📍 UI แผงควบคุมข้อสอบ (Grid Overview)
    if (showGridDialog) {
        AlertDialog(
            onDismissRequest = { showGridDialog = false },
            containerColor = QTheme.colors.card,
            title = { Text(text = stringResource(id = R.string.quiz_overview), color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quizQuestions.size) { idx ->
                        val qInfo = quizQuestions[idx]
                        val isAnswered = selectedAnswers.containsKey(qInfo.qid)
                        val isFlag = flaggedQuestions.contains(qInfo.qid)
                        val isCurrent = idx == currentIndex

                        Box(
                            modifier = Modifier
                                .size(45.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) PrimaryBlue else if (isAnswered) GreenAccent else QTheme.colors.background)
                                .border(
                                    width = if (isFlag) 2.dp else 1.dp,
                                    color = if (isFlag) OrangeAccent else if (isCurrent) Color.Transparent else QTheme.colors.textSecondary,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    currentIndex = idx
                                    showGridDialog = false // 📍 Feature 3: กดปุ๊บวาร์ปปั๊บ
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${idx + 1}", color = if (isCurrent || isAnswered) Color.White else QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                            // ถ้าปักธงให้มีจุดสีส้มมุมขวาบน
                            if (isFlag) {
                                Box(modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(8.dp).clip(CircleShape).background(OrangeAccent))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGridDialog = false }) { Text(text = stringResource(id = R.string.close_dashboard), color = PrimaryBlue) }
            }
        )
    }

    Scaffold(
        containerColor = QTheme.colors.background,
        bottomBar = {
            Box(modifier = Modifier.navigationBarsPadding()) {
            Surface(color = QTheme.colors.background, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    // 📍 ปุ่มย้อนกลับ
                    TextButton(
                        onClick = { if (currentIndex > 0) currentIndex-- },
                        enabled = currentIndex > 0
                    ) {
                        Text(
                            text = stringResource(id = R.string.btn_back),
                            color = if (currentIndex > 0) QTheme.colors.textPrimary else QTheme.colors.textSecondary,
                            fontSize = 16.sp
                        )
                    }

                    if (currentIndex < quizQuestions.size - 1) {
                        // 📍 ปุ่มถัดไป
                        Button(
                            onClick = { currentIndex++ },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text(
                                text = stringResource(id = R.string.btn_next),
                                color = Color.White
                            )
                        }
                    } else {
                        // 📍 ปุ่มส่งข้อสอบ
                        Button(
                            onClick = { showSubmitConfirmDialog = true },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                        ) {
                            Text(
                                text = stringResource(id = R.string.btn_submit),
                                color = Color.White
                            )
                        }
                    }
                }
            }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {

            // 📍 ปรับ Header ใหม่ให้มีปุ่ม Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) { Icon(Icons.Rounded.ArrowBack, "Back", tint = QTheme.colors.textPrimary) }
                    Text(text = stringResource(id = R.string.quiz_in_progress), color = QTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp) // เว้นระยะห่างระหว่าง 2 ปุ่มให้สวยงาม
                ) {
                    // 📍 1. ปุ่มเปิดแผงควบคุม Grid (พื้นหลังเทา, ไอคอนขาว)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF424242)) // สีเทาเข้ม (Dark Gray) เข้ากับธีมมืด
                            .clickable { showGridDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = "Overview",
                            tint = Color.White
                        )
                    }

                    // 📍 2. ปุ่มส่งข้อสอบ (ไอคอนเครื่องหมายถูก, พื้นหลังเขียวละมุนตา, ไอคอนขาว)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (!isSubmitting) Color(0xFF388E3C) // สีเขียว Forest Green (ตัดกับจอดำแล้วดูพรีเมียม ไม่แสบตา)
                                else Color(0xFF555555) // ถ้ากดส่งไปแล้วให้เปลี่ยนเป็นสีเทาหม่นๆ
                            )
                            .clickable(enabled = !isSubmitting) {
                                showSubmitConfirmDialog = true // 👈 เปลี่ยนตรงนี้
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // ใช้ไอคอน Check (เครื่องหมายถูก) หรือถ้าชอบรูปจรวดกระดาษ เปลี่ยนเป็น Icons.Rounded.Send ได้ครับ
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Submit",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(course!!.title, color = QTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = currentQuestion.chapter,
                    color = QTheme.colors.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1, // 📍 บังคับให้อยู่บรรทัดเดียว
                    overflow = TextOverflow.Ellipsis, // 📍 ถ้ายาวเกินให้ตัดจบด้วย ...
                    modifier = Modifier
                        .weight(1f) // 📍 บังคับให้ชื่อบทกินพื้นที่แค่ที่ว่าง ไม่ไปเบียดตัวจับเวลา
                        .padding(end = 16.dp) // 📍 เว้นระยะขอบกันไม่ให้ข้อความชิดเวลาเกินไป
                )
                Text("⏱ $minutes:$seconds", color = if (timeLeftSeconds < 60) RedAccent else QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = (currentIndex + 1).toFloat() / quizQuestions.size,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = PrimaryBlue, trackColor = QTheme.colors.card, strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(30.dp))

            // 📍 Card ข้อสอบ
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = QTheme.colors.card), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        // 📍 ลอจิกแจ้งเตือนเวลาเกินเฉลี่ย
                        val timeSpentOnCurrent = timePerQuestion[currentQid] ?: 0
                        val isTimeExceeded = avgTimePerSec > 0 && timeSpentOnCurrent > avgTimePerSec
                        val timeColor = if (isTimeExceeded) Color(0xFFFF5252) else OrangeAccent // ถ้าเกินให้แดงสว่าง ถ้าไม่เกินให้ส้มๆ

                        Column {
                            Text(
                                // 📍 แสดง "ข้อที่ X / Y"
                                text = stringResource(id = R.string.quiz_question_number, currentIndex + 1, quizQuestions.size),
                                color = QTheme.colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // 📍 แสดงเวลาพร้อมตัวเปรียบเทียบ
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = timeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))

                                Text(
                                    text = if (avgTimePerSec > 0) {
                                        // 📍 กรณีมีเวลาเฉลี่ย (ส่ง 2 ตัวแปร: เวลาที่ใช้, เวลาที่ควรทำ)
                                        stringResource(id = R.string.time_spent_with_limit, timeSpentOnCurrent, avgTimePerSec)
                                    } else {
                                        // 📍 กรณีไม่มีเวลาเฉลี่ย (ส่ง 1 ตัวแปร: เวลาที่ใช้)
                                        stringResource(id = R.string.time_spent_only, timeSpentOnCurrent)
                                    },
                                    color = timeColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isTimeExceeded) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // 📍 Feature: ปุ่มปักธง (Flag)
                        val isFlagged = flaggedQuestions.contains(currentQid)
                        IconButton(onClick = {
                            if (isFlagged) flaggedQuestions.remove(currentQid) else flaggedQuestions.add(currentQid)
                        }) {
                            Icon(
                                imageVector = if (isFlagged) Icons.Rounded.Flag else Icons.Rounded.OutlinedFlag,
                                contentDescription = "Flag",
                                tint = if (isFlagged) OrangeAccent else QTheme.colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(currentQuestion.text, color = QTheme.colors.textPrimary, fontSize = 16.sp, lineHeight = 24.sp)

                    Spacer(modifier = Modifier.height(24.dp))
                    val optionLetters = listOf("A", "B", "C", "D", "E")

                    // ดึงลำดับ Index ที่อาจจะถูกสลับหรือไม่ถูกสลับก็ได้ (ตามเงื่อนไขที่เซ็ตไว้)
                    val currentShuffledIndices = shuffledOptionIndices[currentQid] ?: currentQuestion.options.indices.toList()

                    currentShuffledIndices.forEachIndexed { displayIndex, originalIndex ->
                        val optionText = currentQuestion.options[originalIndex]
                        val isSelected = selectedAnswers[currentQid] == originalIndex
                        val letter = if (displayIndex < optionLetters.size) optionLetters[displayIndex] else "-"

                        OptionCard(
                            letter = letter,
                            text = optionText,
                            isSelected = isSelected,
                            onClick = { selectedAnswers[currentQid] = originalIndex }
                        )
                    }
                }
            }
        }
    }

    // 📍 UI: Dialog ยืนยันการส่งข้อสอบ
    if (showSubmitConfirmDialog) {
        // นับจำนวนข้อที่ยังไม่ได้ทำ
        val unansweredCount = quizQuestions.size - selectedAnswers.size

        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            containerColor = QTheme.colors.card,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.dialog_confirm_submit_title), color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = stringResource(id = R.string.dialog_confirm_submit_desc),
                        color = QTheme.colors.textPrimary
                    )

                    // แจ้งเตือนถ้ายังมีข้อที่ไม่ได้ทำ หรือ ปักธงไว้
                    if (unansweredCount > 0 || flaggedQuestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                            border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // 📍 เช็คข้อที่ยังไม่ได้ทำ
                                if (unansweredCount > 0) {
                                    Text(
                                        text = stringResource(id = R.string.unanswered_warning, unansweredCount),
                                        color = OrangeAccent,
                                        fontSize = 14.sp
                                    )
                                }

                                // 📍 เช็คข้อที่ปักธงไว้
                                if (flaggedQuestions.isNotEmpty()) {
                                    Text(
                                        text = stringResource(id = R.string.flagged_count, flaggedQuestions.size),
                                        color = PrimaryBlue,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirmDialog = false
                        finalSubmit() // 👈 กดยืนยันแล้วค่อยเรียกฟังก์ชันส่งจริง
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                ) {
                    Text(
                        text = stringResource(id = R.string.btn_submit_answers), // 👈 "ส่งคำตอบ"
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirmDialog = false }) {
                    Text(
                        text = stringResource(id = R.string.btn_continue_quiz), // 👈 "กลับไปทำต่อ"
                        color = QTheme.colors.textSecondary
                    )
                }
            }
        )
    }
}

// 📍 ฟังก์ชันคำนวณคะแนนเป็นเปอร์เซ็นต์ (0-100)
fun calculateRealScore(questions: List<QuestionEntity>, answers: Map<Int, Int>): Int {
    if (questions.isEmpty()) return 0
    var correctCount = 0

    // วนลูปตรวจทีละข้อ
    questions.forEach { q ->
        // ถ้าคำตอบที่เลือก (answers[q.qid]) ตรงกับเฉลย (q.correctIndex) ให้บวกคะแนน
        if (answers[q.qid] == q.correctIndex) correctCount++
    }

    // คำนวณเป็นเปอร์เซ็นต์ เช่น ถูก 4 จาก 5 ข้อ = (4/5) * 100 = 80 คะแนน
    return ((correctCount.toFloat() / questions.size) * 100).toInt()
}

// 📍 Component สำหรับวาดตัวเลือก A, B, C, D (เผื่อหลุดหายไปด้วยครับ)
@Composable
fun OptionCard(letter: String, text: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) PrimaryBlue.copy(alpha = 0.2f) else QTheme.colors.background
    val borderColor = if (isSelected) PrimaryBlue else Color.Transparent

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$letter.", color = if (isSelected) PrimaryBlue else QTheme.colors.textSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
            Text(text, color = if (isSelected) PrimaryBlue else QTheme.colors.textPrimary, fontSize = 14.sp)
        }
    }
}

