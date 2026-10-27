package com.petcompanion.presentation.view

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.domain.model.CourseEntity
import com.petcompanion.domain.model.QuizHistoryEntity
import com.petcompanion.presentation.theme.*
import com.petcompanion.presentation.viewmodel.QuizViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.res.stringResource
import com.petcompanion.R
// --- 📍 1. Data Classes สำหรับเก็บสถิติ ---
data class OverviewStats(
    val passRate: Int,
    val completionRate: Int,
    val onTimeRate: Int
)

data class HunterStats(
    val knowledge: Float,   // 🧠 ความรู้รอบตัว
    val timeMgmt: Float,    // ⏱️ การจัดการเวลา
    val analytical: Float,  // 👁️ การวิเคราะห์
    val growth: Float,      // 📈 การพัฒนาการ
    val diligence: Float    // 🔥 ความขยัน
)

// --- 📍 จุดที่ 2: อัปเดตใน HistoryScreen (Main) ---
@Composable
fun HistoryScreen(
    viewModel: QuizViewModel,
    onBackClick: () -> Unit,
    onHistoryItemClick: (Int) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // ดึงข้อมูลจริง (StateFlow)
    val allHistory by viewModel.allHistory.collectAsState()
    val allCourses by viewModel.allCourses.collectAsState(initial = emptyList())

    val overviewStats = remember(allHistory, allCourses) {
        OverviewStats(
            // 1. อัตราการสอบผ่าน
            passRate = if (allHistory.isEmpty()) 0
            else (allHistory.count { it.isPassed } * 100 / allHistory.size),

            // 2. อัตราการทำวิชาที่มีทั้งหมดในเครื่องจนครบ
            completionRate = if (allCourses.isEmpty()) 0
            else (allHistory.map { it.courseId }.distinct().size * 100 / allCourses.size),

            // 3. ⏱️ อัตราการทำทันเวลา (ของจริง!)
            // นับเฉพาะครั้งที่ isTimeout == false (ทำเสร็จก่อนเวลา)
            onTimeRate = if (allHistory.isEmpty()) 0
            else (allHistory.count { !it.isTimeout } * 100 / allHistory.size)
        )
    }

    // 📍 คำนวณ HunterStats (เรดาร์) ของจริง
    val hunterStats = remember(allHistory, allCourses) {
        calculateRealHunterStats(allHistory, allCourses)
    }

    Scaffold(
        containerColor = QTheme.colors.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding() // 📍 พระเอกช่วยชีวิต! สั่งให้ดึงเนื้อหาลงมาหลบ Status Bar อัตโนมัติ
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = QTheme.colors.textPrimary)
                }
                Text(text = stringResource(id = R.string.title_stats_history), color = QTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = QTheme.colors.card,
                divider = {},
                contentColor = PrimaryBlue,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = PrimaryBlue
                    )
                }
            ) {
                val tabs = listOf(
                    R.string.tab_stats,
                    R.string.tab_local,
                    R.string.tab_online
                )
                tabs.forEachIndexed { index, resId -> // เปลี่ยนชื่อจาก title เป็น resId จะได้ไม่งงครับ
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        selectedContentColor = QTheme.colors.textPrimary,
                        unselectedContentColor = Color.Gray,
                        text = {
                            // 📍 ใช้ stringResource ครอบ resId ไว้ เพื่อดึงคำแปลออกมา
                            Text(text = stringResource(id = resId), fontSize = 14.sp)
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> StatsTab(overviewStats, hunterStats)
                1 -> LocalHistoryTab(allHistory, allCourses, onHistoryItemClick)
                2 -> OnlineHistoryTab()
            }
        }
    }
}

// --- 📍 3. Tab: สถิติ (StatsTab) ---
@Composable
fun StatsTab(overviewStats: OverviewStats, hunterStats: HunterStats) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 📍 1. หัวข้อ "สรุปภาพรวมการสอบ"
        Text(
            text = stringResource(id = R.string.header_overview_stats),
            color = QTheme.colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 📍 2. การ์ดสถิติ 3 ใบ
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewCard(
                modifier = Modifier.weight(1f),
                title = stringResource(id = R.string.card_pass_rate), // "สอบผ่าน"
                value = "${overviewStats.passRate}%",
                icon = Icons.Rounded.CheckCircle,
                color = GreenAccent
            )
            OverviewCard(
                modifier = Modifier.weight(1f),
                title = stringResource(id = R.string.card_completion_rate), // "ทำครบ"
                value = "${overviewStats.completionRate}%",
                icon = Icons.Rounded.LibraryBooks,
                color = PrimaryBlue
            )
            OverviewCard(
                modifier = Modifier.weight(1f),
                title = stringResource(id = R.string.card_on_time_rate), // "ทันเวลา"
                value = "${overviewStats.onTimeRate}%",
                icon = Icons.Rounded.Timer,
                color = OrangeAccent
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = QTheme.colors.textSecondary.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(32.dp))

        // 📍 3. หัวข้อ "เรดาร์วิเคราะห์ความสามารถ"
        Text(
            text = stringResource(id = R.string.header_radar_analysis),
            color = QTheme.colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(40.dp))

        // วาดกราฟของจริง
        RadarChart(stats = hunterStats, modifier = Modifier.size(280.dp))
    }
}

// --- 📍 4. Tab: ประวัติในเครื่อง (LocalHistoryTab) ---
@Composable
fun LocalHistoryTab(history: List<QuizHistoryEntity>, courses: List<CourseEntity>, onItemClick: (Int) -> Unit) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(id = R.string.empty_history_message), color = QTheme.colors.textSecondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(history) { item ->
                val courseName = courses.find { it.id == item.courseId }?.title ?: "Unknown Course"
                HistoryItemCard(item, courseName, onItemClick)
            }
        }
    }
}

// --- 📍 5. UI Components ย่อย ---

@Composable
fun OverviewCard(modifier: Modifier, title: String, value: String, icon: ImageVector, color: Color) {
    Card(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = QTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = QTheme.colors.textSecondary, fontSize = 11.sp)
        }
    }
}


@Composable
fun StatDetailRow(label: String, score: Float) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = QTheme.colors.textPrimary, modifier = Modifier.width(120.dp), fontSize = 14.sp)
        LinearProgressIndicator(
            progress = score / 5f,
            modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
            color = PrimaryBlue,
            trackColor = QTheme.colors.card
        )
        Text("${score}/5", color = PrimaryBlue, modifier = Modifier.padding(start = 12.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable
fun RadarChart(stats: HunterStats, modifier: Modifier) {
    // 📍 1. ดึงสีที่ต้องใช้ "ทั้งหมด" ออกมาเตรียมไว้ที่นี่ (นอก Canvas)
    // เพราะตรงนี้ยังอยู่ในโลก Composable เรียกใช้ QTheme.colors ได้ปกติครับ
    val radarLineColor = QTheme.colors.textSecondary.copy(alpha = 0.2f)
    val radarAxisColor = QTheme.colors.textSecondary.copy(alpha = 0.2f)
    val radarTextColor = QTheme.colors.textPrimary // หรือ Color.White ถ้าบอสจะให้ขาวตลอด

    val labelIds = listOf(
        R.string.stat_knowledge,
        R.string.stat_time,
        R.string.stat_analysis,
        R.string.stat_growth,
        R.string.stat_diligence
    )

    val translatedLabels = labelIds.map { stringResource(id = it) }
    val values = listOf(stats.knowledge, stats.timeMgmt, stats.analytical, stats.growth, stats.diligence)
    val textMeasurer = rememberTextMeasurer()

    // 📍 ปรับ TextStyle ให้ใช้สีตามธีมที่เราดึงมา
    val textStyle = TextStyle(color = radarTextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val center = Offset(canvasWidth / 2, canvasHeight / 2)

        val radius = minOf(canvasWidth, canvasHeight) / 2 * 0.65f
        val angleStep = (2 * Math.PI / 5).toFloat()

        // --- วาดใยแมงมุม ---
        for (i in 1..5) {
            val r = radius * (i / 5f)
            val path = Path()
            for (j in 0..5) {
                val angle = j * angleStep - Math.PI.toFloat() / 2
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // 📍 ใช้ตัวแปร radarLineColor ที่เตรียมไว้ (ไม่แดงแล้ว!)
            drawPath(path, color = radarLineColor, style = Stroke(width = 1.dp.toPx()))
        }

        // --- วาดแกนกลาง ---
        for (j in 0..4) {
            val angle = j * angleStep - Math.PI.toFloat() / 2
            // 📍 ใช้ตัวแปร radarAxisColor ที่เตรียมไว้
            drawLine(
                color = radarAxisColor,
                start = center,
                end = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
            )
        }

        // 📍 3. วาดตัวหนังสือ
        translatedLabels.forEachIndexed { j, labelText ->
            val angle = j * angleStep - Math.PI.toFloat() / 2
            val labelRadius = radius * 1.35f
            val x = center.x + labelRadius * cos(angle)
            val y = center.y + labelRadius * sin(angle)

            val textLayoutResult = textMeasurer.measure(
                text = labelText,
                style = textStyle,
                softWrap = false,
                maxLines = 1
            )

            val topLeft = Offset(
                x - (textLayoutResult.size.width / 2f),
                y - (textLayoutResult.size.height / 2f)
            )

            drawText(
                textMeasurer = textMeasurer,
                text = labelText,
                style = textStyle,
                topLeft = topLeft,
                softWrap = false,
                maxLines = 1
            )
        }

        // --- วาดพื้นที่พลัง (Area) ---
        val areaPath = Path()
        values.forEachIndexed { j, v ->
            val r = radius * (v / 5f)
            val angle = j * angleStep - Math.PI.toFloat() / 2
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            if (j == 0) areaPath.moveTo(x, y) else areaPath.lineTo(x, y)
        }
        areaPath.close()

        // ส่วนนี้เป็นสี Accent (Blue) ใช้ค่าเดิมได้เลยครับ เพราะไม่ใช่ Composable
        drawPath(areaPath, color = PrimaryBlue.copy(alpha = 0.4f))
        drawPath(areaPath, color = PrimaryBlue, style = Stroke(width = 2.dp.toPx()))
    }
}



@Composable
fun HistoryItemCard(history: QuizHistoryEntity, courseName: String, onClick: (Int) -> Unit) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val dateString = sdf.format(Date(history.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(history.courseId) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(45.dp).clip(CircleShape).background(if(history.isPassed) GreenAccent.copy(0.1f) else RedAccent.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(if(history.isPassed) Icons.Default.Check else Icons.Default.Close, null, tint = if(history.isPassed) GreenAccent else RedAccent)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(courseName, color = QTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(dateString, color = QTheme.colors.textSecondary, fontSize = 12.sp)
            }
            Text("${history.scorePercentage}%", color = if(history.isPassed) GreenAccent else RedAccent, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
    }
}
@Composable
fun OnlineHistoryTab() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.CloudOff, null, tint = QTheme.colors.textSecondary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(id = R.string.online_ranking_pro), color = QTheme.colors.textSecondary)
        }
    }

}



fun calculateRealHunterStats(history: List<QuizHistoryEntity>, courses: List<CourseEntity>): HunterStats {
    if (history.isEmpty()) return HunterStats(0f, 0f, 0f, 0f, 0f)

    // 1. ความรู้ (Knowledge): วัดจากอัตราการสอบผ่านในรอบแรก (Attempt 1)
    val firstAttempts = history.filter { it.attemptNumber == 1 }
    val knowledge = if (firstAttempts.isNotEmpty()) {
        (firstAttempts.count { it.isPassed }.toFloat() / firstAttempts.size) * 5f
    } else 0f

    // 2. การจัดการเวลา (Time Mgmt): คำนวณจากเปอร์เซ็นต์เวลาที่เหลือเฉลี่ย (ถ้าไม่ Timeout)
    // ในที่นี้เราจะให้คะแนนพื้นฐาน 4.0 และหักลบถ้ามีการทำข้อสอบช้า
    val timeMgmt = if (history.isNotEmpty()) {
        (history.count { !it.isTimeout }.toFloat() / history.size) * 5f
    } else 0f

    // 3. การวิเคราะห์ (Analytical): วัดจาก (ข้อที่ถูก + ข้อที่เกือบถูก) / จำนวนข้อทั้งหมด
    val totalQ = history.sumOf { it.totalQuestions }.coerceAtLeast(1)
    val totalCorrectAndClose = history.sumOf { it.correctCount + it.closeAnswersCount }
    val analytical = (totalCorrectAndClose.toFloat() / totalQ) * 5f

    // 4. การพัฒนาการ (Growth): เทียบคะแนนเฉลี่ยรอบล่าสุด เทียบกับรอบแรก
    val coursesWithMultipleAttempts = history.groupBy { it.courseId }.filter { it.value.size > 1 }
    val growth = if (coursesWithMultipleAttempts.isNotEmpty()) {
        var totalImprovement = 0f
        coursesWithMultipleAttempts.forEach { (_, attempts) ->
            val first = attempts.minByOrNull { it.attemptNumber }?.scorePercentage ?: 0
            val latest = attempts.maxByOrNull { it.timestamp }?.scorePercentage ?: 0
            if (latest > first) totalImprovement += 5f else totalImprovement += 3f
        }
        totalImprovement / coursesWithMultipleAttempts.size
    } else {
        // ถ้ายังไม่มีการทำซ้ำ ให้คะแนนตามค่าเฉลี่ยความถูกต้อง
        (history.map { it.scorePercentage }.average().toFloat() / 100f) * 5f
    }

    // 5. ความขยัน (Diligence): สัดส่วนวิชาที่ทำไปแล้ว เทียบกับวิชาทั้งหมดในเครื่อง
    val uniqueDone = history.map { it.courseId }.distinct().size
    val totalAvailable = courses.size.coerceAtLeast(1)
    val diligence = (uniqueDone.toFloat() / totalAvailable) * 5f

    return HunterStats(
        knowledge = knowledge.coerceIn(0f, 5f),
        timeMgmt = timeMgmt.coerceIn(0f, 5f),
        analytical = analytical.coerceIn(0f, 5f),
        growth = growth.coerceIn(0f, 5f),
        diligence = diligence.coerceIn(0f, 5f)
    )
}