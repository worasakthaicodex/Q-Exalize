package com.petcompanion.presentation.view
import android.app.Dialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.petcompanion.domain.model.GameMode
import com.petcompanion.presentation.viewmodel.MusicGameViewModel
import kotlinx.coroutines.delay
import com.petcompanion.R
import com.petcompanion.presentation.theme.QTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.launch

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
// 🎨 ธีมสีสไตล์ Candy Game (อัปเดตพื้นหลังชมพูอ่อนหวานเจี๊ยบ)
object QGameTheme {
    val backgroundStart = Color(0xFFFFF0F5) // ชมพูอ่อนพาสเทลนมๆ (LavenderBlush)
    val backgroundEnd = Color(0xFFFFD1DC)   // ชมพูเข้มขึ้นมานิดนึงให้มีมิติ
    val panelGold = Color(0xFFFFEB3B)
    val textGameTitle = Color(0xFFD81B60)   // 🚀 เปลี่ยนหัวข้อเป็นสีชมพูบานเย็นเข้มๆ จะได้ตัดกับพื้นหลังอ่อนครับ
}

@Composable
fun MusicGameScreen(
    viewModel: MusicGameViewModel,
    levelNumber: Int = 1,             // เลขด่านสำหรับแสดง "ด่านที่ X"
    onBackClick: () -> Unit,          // กลับหน้าเลือกด่าน (ระหว่างเกม หรือจาก popup)
    onGameComplete: (stars: Int, score: Int) -> Unit = { _, _ -> },  // บันทึกผล
    onNextLevel: () -> Unit = {},     // ไปด่านต่อ (ถ้าผ่าน)
    onPlayAgain: () -> Unit = {}      // เล่นใหม่ (ถ้าไม่ผ่าน)
) {
    // เริ่มที่ tab ที่ตรงกับ mode ของด่านที่เลือก
    var selectedTab by remember {
        mutableStateOf(
            if (viewModel.currentLevelConfig?.mode == GameMode.PIANO_KEYS) 1 else 0
        )
    }
    var feedbackStatus by remember { mutableStateOf<Boolean?>(null) }

    val isGameOver = viewModel.timeRemaining <= 0
    var star1Visible by remember { mutableStateOf(false) }
    var star2Visible by remember { mutableStateOf(false) }
    var star3Visible by remember { mutableStateOf(false) }

    var totalAttempts by remember { mutableStateOf(0) }
    var correctAttempts by remember { mutableStateOf(0) }

    var scoreScale by remember { mutableStateOf(1f) }
    val animatedScoreScale by animateFloatAsState(targetValue = scoreScale, animationSpec = spring(dampingRatio = 0.3f, stiffness = 800f), finishedListener = { if (scoreScale > 1f) scoreScale = 1f })

    val scope = rememberCoroutineScope()
    var scoreTargetPosition by remember { mutableStateOf(Offset.Zero) }
    var flyStartPosition by remember { mutableStateOf(Offset.Zero) }

    var isFlying by remember { mutableStateOf(false) }
    val flyProgress = remember { Animatable(0f) }

    var isExploding by remember { mutableStateOf(false) }
    val explosionProgress = remember { Animatable(0f) }

    // Entrance slide-in (mirrors LevelSelectScreen exit)
    var hasEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasEntered = true }
    val slideProgress by animateFloatAsState(
        targetValue = if (hasEntered) 0f else 1f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "slideIn"
    )

    // บันทึกผลครั้งเดียวต่อจบเกม
    var progressSaved by remember { mutableStateOf(false) }

    LaunchedEffect(isGameOver) {
        if (isGameOver) {
            viewModel.playSuccess()
            delay(300L); star1Visible = true; delay(250L); star2Visible = true; delay(250L); star3Visible = true
            if (!progressSaved) {
                progressSaved = true
                onGameComplete(viewModel.computeStars(), viewModel.score)
            }
        } else {
            star1Visible = false; star2Visible = false; star3Visible = false
        }
    }

    val scaleStar1 by animateFloatAsState(targetValue = if (star1Visible) 1f else 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    val scaleStar2 by animateFloatAsState(targetValue = if (star2Visible) 1f else 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    val scaleStar3 by animateFloatAsState(targetValue = if (star3Visible) 1f else 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))

    LaunchedEffect(viewModel.timeRemaining) { if (viewModel.timeRemaining > 0) { delay(1000L); viewModel.tickTime() } }
    LaunchedEffect(feedbackStatus) { if (feedbackStatus != null) { delay(300L); feedbackStatus = null } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .paint(painterResource(id = R.drawable.game), contentScale = ContentScale.Crop)
    ) {

        // 🍰 เลเยอร์ 1.5: พื้นเค้กสุดคิวท์ (ติดขอบล่างของหน้าจอเสมอ)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter) // สั่งให้เกาะติดขอบล่าง
                .height(80.dp) // ความสูงของฐานเค้กปรับตามความเหมาะสม
        ) {
            // ซ้าย
            Image(
                painter = painterResource(id = R.drawable.cake_left), // 💡 เรียกใช้ชื่อไฟล์พิมพ์เล็กตามที่เตือนไว้นะครับบอส
                contentDescription = null,
                modifier = Modifier.fillMaxHeight().width(64.dp), // ล็อกความกว้าง
                contentScale = ContentScale.FillBounds
            )
            // กลาง (ยืดเต็มที่)
            Image(
                painter = painterResource(id = R.drawable.cake_mid),
                contentDescription = null,
                modifier = Modifier.weight(1f).fillMaxHeight(), // 🚀 หมัดเด็ด! ให้กางออกจนเต็มเนื้อที่
                contentScale = ContentScale.FillBounds
            )
            // ขวา
            Image(
                painter = painterResource(id = R.drawable.cake_right),
                contentDescription = null,
                modifier = Modifier.fillMaxHeight().width(64.dp), // ล็อกความกว้าง
                contentScale = ContentScale.FillBounds
            )
        }

        // เนื้อหาหลักของเกม
        Column(modifier = Modifier.statusBarsPadding().padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {

            // หัวจอ — สไลด์เข้าจากบน
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .graphicsLayer { translationY = -2400f * slideProgress },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoundImageButton(imageRes = R.drawable.home, onClick = onBackClick)
                Text(
                    text = "ด่านที่ $levelNumber",
                    color = QGameTheme.textGameTitle, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.width(64.dp))
            }

            // เนื้อหาเกม — สไลด์เข้าจากล่าง (ห่อรวมใน Column เดียว)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { translationY = 2400f * slideProgress },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

            Spacer(modifier = Modifier.height(16.dp))

            // แผงสถิติ
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(84.dp), contentAlignment = Alignment.Center) {
                Image(painter = painterResource(id = R.drawable.button_rectangle_depth_gloss), contentDescription = "แถบฐานเขียวยาว", modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 24.dp).height(54.dp).align(Alignment.Center), contentScale = ContentScale.FillBounds)
                Text(text = "00:${viewModel.timeRemaining.toString().padStart(2, '0')}", color = if (viewModel.timeRemaining <= 10) Color(0xFFFF5252) else Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center).offset(x = (-8).dp)) {
                    val accuracyPercentage = if (totalAttempts > 0) (correctAttempts * 100) / totalAttempts else 0
                    Text(text = "แม่นยำ $accuracyPercentage%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(modifier = Modifier.size(76.dp).align(Alignment.CenterEnd).onGloballyPositioned { layoutCoordinates -> scoreTargetPosition = layoutCoordinates.boundsInRoot().center }.scale(animatedScoreScale), contentAlignment = Alignment.Center) {
                    Image(painter = painterResource(id = R.drawable.check_round_color), contentDescription = "วงกลมคะแนน", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "คะแนน", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = "${viewModel.score}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // แผงรูปโจทย์
            val borderColor = when (feedbackStatus) { true -> Color(0xFF4CAF50); false -> Color(0xFFF44336); null -> Color.Transparent }
            // 📍 3. พื้นที่โชว์รูปโจทย์ (การ์ดเหลี่ยมเป๊ะ + ไม้เท้าอิสระลอยนอกกรอบ!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 🚀 ขยายพื้นที่ Box ให้สูงขึ้น เพื่อให้ไม้เท้ามีที่ยืน (ไม่โดนตัดขอบ)
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {

                // 🌟 เลเยอร์ 1: Card โจทย์หลัก (อยู่ด้านล่าง)
                val borderColor = when (feedbackStatus) { true -> Color(0xFF4CAF50); false -> Color(0xFFF44336); null -> Color(0xFFFFD1DC) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp) // ความสูงตัวการ์ดจริง
                        // 🚀 ดันการ์ดลงมาด้านล่างของ Box เพื่อเว้นที่ว่างด้านบนให้ไม้เท้า
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = androidx.compose.ui.graphics.RectangleShape, // เหลี่ยมเป๊ะ ห้ามโค้ง!
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // แบนติดพื้น ห้ามนูน!
                    border = BorderStroke(4.dp, borderColor)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = viewModel.currentQuestion,
                            transitionSpec = { slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn(animationSpec = tween(300)) togetherWith slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut(animationSpec = tween(300)) },
                            label = "QuestionAnimation"
                        ) { question ->
                            Image(
                                painter = painterResource(id = question.imageRes),
                                contentDescription = "โจทย์โน้ตดนตรี",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(16.dp)
                            )
                        }
                    }
                }

                // 🍬 เลเยอร์ 2: ไม้เท้าลูกกวาด (ลอยอิสระเหนือการ์ด!)
                Image(
                    painter = painterResource(id = R.drawable.cane_pink_small),
                    contentDescription = null,
                    modifier = Modifier
                        // 🚀 เกาะมุมขวาบนของ Box แม่ (ไม่ใช่ของการ์ด)
                        .align(Alignment.TopEnd)
                        .padding(end = 20.dp) // ขยับออกจากขอบจอขวาหน่อยให้ดูบาลานซ์
                        .size(80.dp) // ปรับขนาดให้ใหญ่ขึ้นนิดนึงจะได้เด่นๆ
                        .offset(y = -15.dp)


                        // 🚀 offset แกน Y ลงมานิดนึง ให้เหมือนกำลังเกาะขอบการ์ดพอดี

                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // แผง Tab
            Surface(shape = RoundedCornerShape(50), color = Color(0xFFEBEBEB), modifier = Modifier.width(260.dp).height(44.dp)) {
                Row(modifier = Modifier.fillMaxSize()) {
                    listOf("Music Notes", "Piano Keys").forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp).background(color = if (isSelected) Color.White else Color.Transparent, shape = RoundedCornerShape(50)).clickable { selectedTab = index }, contentAlignment = Alignment.Center) {
                            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.Black else Color.Gray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // แผงปุ่มควบคุม
            if (selectedTab == 0) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val handleKeyPress = { note: String, buttonOffset: Offset ->
                        if (viewModel.timeRemaining > 0) {
                            viewModel.playNote(note); totalAttempts++
                            val isCorrect = viewModel.checkAnswer(note); if (isCorrect) correctAttempts++; feedbackStatus = isCorrect
                            if (isCorrect) { scope.launch { flyStartPosition = buttonOffset; isFlying = true; flyProgress.snapTo(0f); flyProgress.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing)); isFlying = false; scoreScale = 1.5f; isExploding = true; explosionProgress.snapTo(0f); explosionProgress.animateTo(1f, animationSpec = tween(300)); isExploding = false } }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("C#", "D#").forEach { note -> PianoButton(text = note, modifier = Modifier.width(46.dp), onClick = { btnOffset -> handleKeyPress(note, btnOffset) }) }
                        }
                        Spacer(modifier = Modifier.width(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("F#", "G#", "A#").forEach { note -> PianoButton(text = note, modifier = Modifier.width(46.dp), onClick = { btnOffset -> handleKeyPress(note, btnOffset) }) }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("C", "D", "E", "F", "G", "A", "B").forEach { note -> PianoButton(text = note, modifier = Modifier.weight(1f), onClick = { btnOffset -> handleKeyPress(note, btnOffset) }) }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val handleThaiKeyPress = { thaiNote: String, engNote: String, buttonOffset: Offset ->
                        if (viewModel.timeRemaining > 0) {
                            viewModel.playNote(engNote); totalAttempts++
                            val isCorrect = viewModel.checkAnswer(engNote); if (isCorrect) correctAttempts++; feedbackStatus = isCorrect
                            if (isCorrect) { scope.launch { flyStartPosition = buttonOffset; isFlying = true; flyProgress.snapTo(0f); flyProgress.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing)); isFlying = false; scoreScale = 1.5f; isExploding = true; explosionProgress.snapTo(0f); explosionProgress.animateTo(1f, animationSpec = tween(300)); isExploding = false } }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ColorfulButton(text = "โด", imageRes = R.drawable.button_square_depth_border, modifier = Modifier.weight(1f), onClick = { offset -> handleThaiKeyPress("โด", "C", offset) })
                        ColorfulButton(text = "เร", imageRes = R.drawable.button_square_depth_borde_y, modifier = Modifier.weight(1f), onClick = { offset -> handleThaiKeyPress("เร", "D", offset) })
                        ColorfulButton(text = "มี", imageRes = R.drawable.button_square_depth_border_g, modifier = Modifier.weight(1f), onClick = { offset -> handleThaiKeyPress("มี", "E", offset) })
                        ColorfulButton(text = "ฟา", imageRes = R.drawable.button_square_depth_border_b, modifier = Modifier.weight(1f), onClick = { offset -> handleThaiKeyPress("ฟา", "F", offset) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth(0.75f)) {
                        ColorfulButton(text = "ซอล", imageRes = R.drawable.button_square_depth_border, modifier = Modifier.weight(1f), onClick = { offset -> handleThaiKeyPress("ซอล", "G", offset) })
                        ColorfulButton(text = "ลา", imageRes = R.drawable.button_square_depth_borde_y, modifier = Modifier.weight(1f), onClick = { offset -> handleThaiKeyPress("ลา", "A", offset) })
                        ColorfulButton(text = "ที", imageRes = R.drawable.button_square_depth_border_g, modifier = Modifier.weight(1f), onClick = { offset -> handleThaiKeyPress("ที", "B", offset) })
                    }
                }
            }

            // เผื่อพื้นที่ให้แผงปุ่มไม่โดนตัวเค้กบดบัง
            Spacer(modifier = Modifier.height(100.dp))

            } // end inner content Column
        } // end outer Column

        // 🛸 เลเยอร์พิเศษ: สายรุ้ง + ระเบิด
        if (isFlying || isExploding) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (isFlying) {
                    val p0 = flyStartPosition; val p3 = scoreTargetPosition
                    val p1 = Offset(p0.x - 100f, p0.y + 100f); val p2 = Offset(p0.x + (p3.x - p0.x) * 0.5f, p3.y - 300f); val t = flyProgress.value
                    val rainbowColors = listOf(Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF4CD964), Color(0xFF5AC8FA), Color(0xFF5856D6))
                    for (i in 0..20) { val trailT = t - (i * 0.02f); if (trailT in 0f..1f) { val mt = 1f - trailT; val cx = mt*mt*mt*p0.x + 3f*mt*mt*trailT*p1.x + 3f*mt*trailT*trailT*p2.x + trailT*trailT*trailT*p3.x; val cy = mt*mt*mt*p0.y + 3f*mt*mt*trailT*p1.y + 3f*mt*trailT*trailT*p2.y + trailT*trailT*trailT*p3.y; val alpha = (1f - (i / 20f)) * (if (trailT < 0.2f) trailT * 5f else 1f); val sizeFactor = (15f - (i * 0.5f)).coerceAtLeast(2f); rainbowColors.forEachIndexed { index, color -> val dispersionX = if (i > 5) (sin(trailT * 20.0 + index) * i).toFloat() else 0f; val dispersionY = if (i > 5) (cos(trailT * 20.0 + index) * i).toFloat() else 0f; drawCircle(color = color, radius = sizeFactor, center = Offset(cx + dispersionX, cy + dispersionY + (index * 6f) - 15f), alpha = alpha * 0.8f) } } }
                }
                if (isExploding) { val exT = explosionProgress.value; val radius = exT * 120f; val alpha = 1f - exT; drawCircle(color = Color.White, radius = radius, center = scoreTargetPosition, alpha = alpha * 0.6f); for (i in 0..7) { val angle = (i * PI * 2) / 8; val px = scoreTargetPosition.x + (cos(angle) * radius).toFloat(); val py = scoreTargetPosition.y + (sin(angle) * radius).toFloat(); drawCircle(color = Color(0xFFFFCC00), radius = (1f - exT) * 15f, center = Offset(px, py), alpha = alpha) } }
            }
        }

        // 🚨 เลเยอร์ป็อปอัปหมดเวลา
        if (isGameOver) {
            val starsCount = viewModel.computeStars()
            val passed = starsCount >= 1
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(enabled = false) { }, contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(300.dp).height(340.dp), contentAlignment = Alignment.Center) {
                    Image(painter = painterResource(id = R.drawable.button_square_gradient), contentDescription = null, modifier = Modifier.fillMaxWidth().height(260.dp).align(Alignment.Center), contentScale = ContentScale.FillBounds)
                    Column(modifier = Modifier.align(Alignment.Center).padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = when (starsCount) { 3 -> "สุดยอด! 🎉"; 2 -> "เก่งมาก! 👍"; 1 -> "ผ่านแล้ว! ✅"; else -> "พยายามใหม่นะ 💪" },
                            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Box(modifier = Modifier.width(220.dp).height(84.dp), contentAlignment = Alignment.Center) {
                            Image(painter = painterResource(id = R.drawable.button_rectangle_border), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("คะแนน", color = Color(0xFF7A7A7A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("${viewModel.score} แต้ม", color = Color(0xFF424242), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                                // แสดง threshold เล็กๆ
                                val cfg = viewModel.currentLevelConfig
                                if (cfg != null && !passed) {
                                    Text("ผ่าน ${cfg.star1Score} แต้ม", color = Color(0xFF9E9E9E), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    // ดาว
                    Row(modifier = Modifier.align(Alignment.TopCenter).offset(y = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(id = if (starsCount >= 1) R.drawable.star else R.drawable.star_outline_depth), contentDescription = null, modifier = Modifier.size(54.dp).rotate(-15f).offset(y = 12.dp).scale(scaleStar1))
                        Image(painter = painterResource(id = if (starsCount >= 2) R.drawable.star else R.drawable.star_outline_depth), contentDescription = null, modifier = Modifier.size(72.dp).scale(scaleStar2))
                        Image(painter = painterResource(id = if (starsCount >= 3) R.drawable.star else R.drawable.star_outline_depth), contentDescription = null, modifier = Modifier.size(54.dp).rotate(15f).offset(y = 12.dp).scale(scaleStar3))
                    }
                    // ปุ่ม: กลับเลือกด่าน | เล่นต่อ/เล่นใหม่
                    Row(modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-10).dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // ปุ่มกลับ
                        Box(modifier = Modifier.size(56.dp).clickable { onBackClick() }, contentAlignment = Alignment.Center) {
                            Image(painter = painterResource(id = R.drawable.button_round_depth_line), contentDescription = null, modifier = Modifier.fillMaxSize())
                            Icon(painter = painterResource(id = android.R.drawable.ic_menu_revert), contentDescription = null, tint = Color(0xFF5A5A5A), modifier = Modifier.size(24.dp))
                        }
                        // ปุ่มเล่นต่อ / เล่นใหม่
                        Box(modifier = Modifier.size(56.dp).clickable {
                            if (passed) onNextLevel() else onPlayAgain()
                        }, contentAlignment = Alignment.Center) {
                            Image(painter = painterResource(id = R.drawable.button_round_flat), contentDescription = null, modifier = Modifier.fillMaxSize())
                            Icon(painter = painterResource(id = android.R.drawable.ic_media_play), contentDescription = null, tint = Color(0xFF5A5A5A), modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

// 📍 Component คีย์เปียโนขาวดำ
@Composable
fun PianoButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (Offset) -> Unit
) {
    var buttonPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .height(68.dp)
            .onGloballyPositioned { layoutCoordinates -> buttonPosition = layoutCoordinates.boundsInRoot().center }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = Color.Gray),
                onClick = { onClick(buttonPosition) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(id = R.drawable.button_square_depth_line), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Text(text = text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF5A5D64))
    }
}

// 📍 Component ปุ่มมีสีสัน (โด เร มี)
@Composable
fun ColorfulButton(
    text: String,
    imageRes: Int,
    modifier: Modifier = Modifier,
    onClick: (Offset) -> Unit
) {
    var buttonPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .height(72.dp)
            .onGloballyPositioned { layoutCoordinates -> buttonPosition = layoutCoordinates.boundsInRoot().center }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = Color.Gray),
                onClick = { onClick(buttonPosition) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(id = imageRes), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Text(text = text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF424242))
    }
}