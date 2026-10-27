package com.petcompanion.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R
import com.petcompanion.presentation.theme.*
import com.petcompanion.presentation.viewmodel.QuizViewModel

@Composable
fun QuizResultScreen(
    courseId: Int,
    viewModel: QuizViewModel,
    onHomeClick: () -> Unit,
    onRetryClick: (Int) -> Unit,
    onCheckAnswersClick: (Map<Int, Int>) -> Unit
) {
    val course by viewModel.getCourseById(courseId).collectAsState(initial = null)

    if (course == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(QTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = PrimaryBlue)
        }
        return
    }

    // ── คำนวณคะแนนและเกรด ──────────────────────────────────────────────────
    val totalQuestions  = course!!.questionsToUse
    val percentage      = course!!.latestScore ?: 0
    val correctAnswers  = if (totalQuestions > 0) Math.round((percentage / 100f) * totalQuestions) else 0
    val wrongAnswers    = totalQuestions - correctAnswers
    val progress        = percentage / 100f

    val config       = course!!.gradingConfigJson
    val gradeHScore  = config.split(",").find { it.startsWith("H:") }?.substringAfter(":")?.toIntOrNull() ?: 70
    val gradeSScore  = config.split(",").find { it.startsWith("S:") }?.substringAfter(":")?.toIntOrNull() ?: 60

    val currentGrade = when {
        percentage >= gradeHScore -> "H"
        percentage >= gradeSScore -> "S"
        else                      -> "U"
    }
    val isPassed     = currentGrade != "U"
    val statusColor  = when (currentGrade) {
        "H"  -> Color(0xFFFFD700)
        "S"  -> Color(0xFF4CAF50)
        else -> Color(0xFFFF5252)
    }

    val canReview = course!!.isAllowReview
    val canRetry  = course!!.isAllowRetry

    Scaffold(containerColor = QTheme.colors.background) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {

            // ── 1. Top Bar (2 ภาษา) ─────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onHomeClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(id = R.string.btn_back),
                        tint = QTheme.colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "ผลการสอบ",
                        color = QTheme.colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = stringResource(id = R.string.title_quiz_result), // "Exam Results"
                        color = QTheme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 2. Header Card ─────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = course!!.title,
                        color = QTheme.colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.label_course_code_display, course!!.courseCode),
                        color = QTheme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 3. Score Card ──────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // วงกลมคะแนน
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                        CircularProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxSize(),
                            color = statusColor,
                            trackColor = QTheme.colors.background,
                            strokeWidth = 6.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = QTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)) {
                                        append("$correctAnswers")
                                    }
                                    withStyle(SpanStyle(color = QTheme.colors.textSecondary, fontSize = 14.sp)) {
                                        append("/$totalQuestions")
                                    }
                                }
                            )
                            Text(
                                text = stringResource(id = R.string.label_score_unit),
                                color = QTheme.colors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // ข้อความเกรด
                    Text(
                        text = buildAnnotatedString {
                            val msg = if (isPassed)
                                stringResource(id = R.string.msg_congrats)
                            else
                                stringResource(id = R.string.msg_dont_give_up)
                            withStyle(SpanStyle(color = QTheme.colors.textPrimary)) { append(msg) }
                            withStyle(SpanStyle(color = statusColor, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)) {
                                append(currentGrade)
                            }
                            withStyle(SpanStyle(color = QTheme.colors.textPrimary)) {
                                append(stringResource(id = R.string.msg_with_score))
                            }
                            withStyle(SpanStyle(color = PrimaryBlue, fontWeight = FontWeight.Bold)) {
                                append("$percentage%.")
                            }
                        },
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 4. Stat Cards (2 ภาษา) ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BilingualStatCard(
                    modifier    = Modifier.weight(1f),
                    bgColor     = GreenAccent,
                    value       = "$correctAnswers",
                    labelTh     = "ตอบถูก",
                    labelEn     = stringResource(id = R.string.stat_correct_answers)
                )
                BilingualStatCard(
                    modifier    = Modifier.weight(1f),
                    bgColor     = RedAccent,
                    value       = "$wrongAnswers",
                    labelTh     = "ตอบผิด",
                    labelEn     = stringResource(id = R.string.stat_wrong_answers)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BilingualStatCard(
                    modifier    = Modifier.weight(1f),
                    bgColor     = PrimaryBlue,
                    value       = "$gradeSScore%",
                    labelTh     = "เกณฑ์ผ่าน",
                    labelEn     = stringResource(id = R.string.stat_passing_target)
                )
                BilingualStatCard(
                    modifier    = Modifier.weight(1f),
                    bgColor     = OrangeAccent,
                    value       = "$percentage%",
                    labelTh     = "คะแนนที่ได้",
                    labelEn     = stringResource(id = R.string.stat_accuracy_rate)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 5. ปุ่ม Actions ────────────────────────────────────────────
            LaunchedEffect(courseId) { viewModel.loadLatestHistoryFromDB(courseId) }
            val historyData by viewModel.latestHistory.collectAsState()

            // ปุ่ม ดูเฉลย
            Button(
                onClick  = { if (canReview) onCheckAnswersClick(historyData?.selectedAnswers ?: emptyMap()) },
                enabled  = canReview,
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = if (canReview) PrimaryBlue.copy(alpha = 0.12f) else QTheme.colors.card,
                    disabledContainerColor = QTheme.colors.card
                )
            ) {
                if (!canReview) {
                    Icon(Icons.Default.Lock, null, tint = QTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text      = if (canReview) "ดูเฉลย" else "ปิดการดูเฉลย",
                        color     = if (canReview) PrimaryBlue else QTheme.colors.textSecondary,
                        fontSize  = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text      = if (canReview)
                            stringResource(id = R.string.btn_check_answers_label)
                        else
                            stringResource(id = R.string.btn_check_answers_restricted),
                        color     = if (canReview) PrimaryBlue.copy(0.7f) else QTheme.colors.textSecondary.copy(0.6f),
                        fontSize  = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ปุ่ม สอบใหม่
            Button(
                onClick  = { if (canRetry) onRetryClick(courseId) },
                enabled  = canRetry,
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = if (canRetry) PrimaryBlue else QTheme.colors.card,
                    disabledContainerColor = QTheme.colors.card
                )
            ) {
                Icon(
                    imageVector = if (canRetry) Icons.Default.Refresh else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (canRetry) Color.White else QTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = if (canRetry) "สอบใหม่อีกครั้ง" else "ไม่อนุญาตสอบซ้ำ",
                        color      = if (canRetry) Color.White else QTheme.colors.textSecondary,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text  = if (canRetry)
                            stringResource(id = R.string.btn_try_again)
                        else
                            stringResource(id = R.string.btn_retry_not_allowed),
                        color = if (canRetry) Color.White.copy(0.7f) else QTheme.colors.textSecondary.copy(0.6f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 6. Bottom Footer (2 ภาษา) ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(QTheme.colors.card)
                    .padding(16.dp),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text      = "เสร็จสิ้นแล้ว?",
                        color     = QTheme.colors.textSecondary,
                        fontSize  = 12.sp
                    )
                    Text(
                        text      = stringResource(id = R.string.footer_finished), // "Finished?"
                        color     = QTheme.colors.textSecondary,
                        fontSize  = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text      = "กลับไปหน้าเลือกวิชา",
                        color     = QTheme.colors.textPrimary,
                        fontSize  = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text      = stringResource(id = R.string.footer_back_to_list),
                        color     = QTheme.colors.textSecondary,
                        fontSize  = 11.sp
                    )
                }
                Button(
                    onClick        = onHomeClick,
                    shape          = RoundedCornerShape(8.dp),
                    colors         = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    modifier       = Modifier.height(40.dp)
                ) {
                    Text(
                        text       = stringResource(id = R.string.btn_go),
                        color      = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ── การ์ดสถิติ 2 ภาษา ────────────────────────────────────────────────────────
@Composable
fun BilingualStatCard(
    modifier : Modifier,
    bgColor  : Color,
    value    : String,
    labelTh  : String,
    labelEn  : String
) {
    Card(
        modifier = modifier.height(95.dp),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(value,   color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(labelTh, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(labelEn, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
        }
    }
}

// ── StatCard เดิม (ยังคงไว้ให้ backward compat) ──────────────────────────────
@Composable
fun StatCard(modifier: Modifier, bgColor: Color, title: String, sub: String) {
    Card(
        modifier = modifier.height(90.dp),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(sub, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
        }
    }
}
