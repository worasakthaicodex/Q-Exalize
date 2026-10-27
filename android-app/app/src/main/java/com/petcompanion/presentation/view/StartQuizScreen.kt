package com.petcompanion.presentation.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.presentation.theme.*
import com.petcompanion.presentation.viewmodel.QuizViewModel

import androidx.compose.ui.res.stringResource
import com.petcompanion.R

@Composable
fun StartQuizScreen(
    courseId: Int, // 📍 รับ ID เพื่อไปดึงข้อมูลจริง
    viewModel: QuizViewModel,
    onBackClick: () -> Unit,
    onStartClick: (Int) -> Unit // 📍 ส่ง ID กลับไปเพื่อเริ่มทำข้อสอบ
) {
    // 📍 ดึงข้อมูลวิชาจริงจาก DB
    val course by viewModel.getCourseById(courseId).collectAsState(initial = null)

    // 📍 ลอจิกตรวจสอบสถานะ (สอบไปแล้ว? ห้ามสอบซ้ำ?)
    val isAlreadyCompleted = (course?.progress ?: 0f) >= 1.0f
    val canRetry = course?.isAllowRetry ?: true
    val isLocked = isAlreadyCompleted && !canRetry // ถ้าจริง = ล็อกปุ่ม

    // 📍 ดึงค่าเกรด S มาโชว์ในเกณฑ์ผ่าน
    val config = course?.gradingConfigJson ?: ""
    val gradeS = if (config.contains("S:")) config.split(",").find { it.startsWith("S:") }?.substringAfter(":") ?: "60" else "60"

    Scaffold(
        containerColor = QTheme.colors.background,

        bottomBar = {
            Box(modifier = Modifier.navigationBarsPadding()) {
            Surface(
                color = QTheme.colors.background,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${course?.timeLimit ?: 0} ${stringResource(id = R.string.unit_min_brief)}",
                            color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { onStartClick(courseId) },
                        enabled = !isLocked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLocked) Color.Gray.copy(alpha = 0.5f) else PrimaryBlue,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(if (isLocked) 0.dp else 8.dp)
                    ) {
                        if (isLocked) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (isLocked) stringResource(id = R.string.status_restricted) else stringResource(id = R.string.btn_start_quiz),
                            color = if (isLocked) Color.LightGray else Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Top Bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(id = R.string.btn_back), tint = QTheme.colors.textPrimary)
                }
                Text(
                    text = stringResource(id = R.string.title_course_details),
                    color = QTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = course?.description ?: stringResource(id = R.string.default_course_desc),
                color = QTheme.colors.textSecondary, fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Banner Card
            Card(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f))
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    Column {
                        Text(course?.title ?: "Unknown Course", color = PrimaryBlue, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(id = R.string.label_course_code_brief, course?.courseCode ?: "-"),
                            color = QTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.label_questions_count, course?.questionsToUse ?: course?.totalQuestions ?: 0),
                            color = QTheme.colors.textSecondary, fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ข้อมูลสรุป
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryItem(stringResource(id = R.string.label_total_time_header), "${course?.timeLimit ?: 0} ${stringResource(id = R.string.unit_min_brief)}")
                SummaryItem(stringResource(id = R.string.label_passing_criteria), "$gradeS%")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ส่วน Security Warnings
            Text(stringResource(id = R.string.header_security_rules), color = RedAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            SecurityRuleItem(
                icon = Icons.Default.Warning,
                text = stringResource(id = R.string.rule_auto_submit)
            )

            if (!canRetry) {
                SecurityRuleItem(
                    icon = Icons.Default.Block,
                    text = stringResource(id = R.string.rule_no_retry_allowed)
                )
            }

            if (course?.isAntiCheat == true) {
                SecurityRuleItem(
                    icon = Icons.Default.Lock,
                    text = stringResource(id = R.string.rule_anti_cheat_active)
                )
            }

            if (course?.isPreventScreenshot == true) {
                SecurityRuleItem(
                    icon = Icons.Default.Notifications,
                    text = stringResource(id = R.string.rule_prevent_screenshot)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // คำแนะนำตอนท้าย
            Text(stringResource(id = R.string.header_instructions), color = QTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.instruction_body),
                color = QTheme.colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp
            )
        }
    }
}
@Composable
fun SummaryItem(label: String, value: String) {
    Column {
        Text(label, color = QTheme.colors.textSecondary, fontSize = 12.sp)
        Text(value, color = QTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecurityRuleItem(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = RedAccent, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, color = QTheme.colors.textPrimary.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
fun DynamicRuleItem(
    icon: ImageVector,
    text: String,
    isWarning: Boolean,
    accentColor: Color? = null
) {
    val color = accentColor ?: if (isWarning) Color(0xFFFF5252) else Color(0xFF0078D4)

    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = if (isWarning) QTheme.colors.textPrimary else QTheme.colors.textPrimary.copy(alpha = 0.7f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontWeight = if (isWarning) FontWeight.Medium else FontWeight.Normal
        )
    }
}