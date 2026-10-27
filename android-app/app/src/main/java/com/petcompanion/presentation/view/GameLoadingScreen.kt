package com.petcompanion.presentation.view

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R
import com.petcompanion.presentation.viewmodel.MusicGameViewModel
import kotlinx.coroutines.delay

@Composable
fun GameLoadingScreen(
    viewModel: MusicGameViewModel,
    onLoadingComplete: () -> Unit
) {
    // ── ตัวแปร progress ──────────────────────────────────────────────────────
    val rawProgress = viewModel.loadingProgress          // 0f..1f จาก SoundPool

    // ทำให้ progress bar เคลื่อนที่ลื่น ๆ
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "loadingBar"
    )

    // เวลาขั้นต่ำ 2 วินาที ให้ user เห็น loading screen
    var minTimeElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2_000L)
        minTimeElapsed = true
    }

    // พอโหลดครบ + ผ่านเวลาขั้นต่ำ → ไปหน้าถัดไป
    LaunchedEffect(rawProgress, minTimeElapsed) {
        if (rawProgress >= 1f && minTimeElapsed) {
            delay(300L)   // หน่วงนิดหนึ่งให้ bar เต็มก่อน
            onLoadingComplete()
        }
    }

    // ── Entrance animation ────────────────────────────────────────────────────
    var hasEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasEntered = true }
    val slideIn by animateFloatAsState(
        targetValue = if (hasEntered) 0f else 1f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "slideIn"
    )

    // ── Pulse โลโก้ ──────────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "logoPulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.04f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    // ── กระพริบจุด loading text ──────────────────────────────────────────────
    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 3.99f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )
    val dots = ".".repeat(dotCount.toInt() + 1)

    // ── หน้า ─────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .paint(painterResource(id = R.drawable.game), contentScale = ContentScale.Crop)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // โลโก้เกม
            Image(
                painter = painterResource(id = R.drawable.game1),
                contentDescription = "Q Piano Rhythm Master",
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .graphicsLayer { translationY = -2000f * slideIn }
                    .scale(logoScale)
            )

            Spacer(modifier = Modifier.height(64.dp))

            // ── Progress Bar + ข้อความ ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = 2000f * slideIn },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ข้อความสถานะ
                Text(
                    text = if (rawProgress >= 1f) "พร้อมแล้ว! 🎵" else "กำลังโหลด$dots",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // กรอบ Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                ) {
                    // พื้นหลัง bar
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF1A2A40))
                    )

                    // แถบที่เต็มขึ้น
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF0288D1), Color(0xFF4FC3F7), Color(0xFFB3E5FC))
                                )
                            )
                    )

                    // แถบวาว (gloss) บนสุด
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .fillMaxHeight(0.45f)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    // เปอร์เซ็นต์
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // รายการที่กำลังโหลด
                Text(
                    text = when {
                        rawProgress < 0.3f -> "🎵 โหลดเสียงโน้ตดนตรี..."
                        rawProgress < 0.7f -> "🎹 โหลดเสียงเปียโน..."
                        rawProgress < 1f   -> "⭐ เตรียมด่านและเอฟเฟกต์..."
                        else               -> "✅ โหลดเสร็จแล้ว!"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }
    }
}
