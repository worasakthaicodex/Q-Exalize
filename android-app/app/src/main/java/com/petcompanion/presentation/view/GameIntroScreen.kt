package com.petcompanion.presentation.view

import android.media.MediaPlayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.petcompanion.R

@Composable
fun GameIntroScreen(
    onStartClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    // 🎵 เพลงพื้นหลัง — เปิด loop ตลอด, หยุดเมื่อออกจากหน้า
    DisposableEffect(Unit) {
        val mp = MediaPlayer.create(context, R.raw.bgm_intro)?.apply {
            isLooping = true
            start()
        }
        onDispose {
            mp?.stop()
            mp?.release()
        }
    }

    // --- Transition: slide-out ก่อนเปลี่ยนหน้า ---
    var isTransitioning by remember { mutableStateOf(false) }
    val slideOut by animateFloatAsState(
        targetValue = if (isTransitioning) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        finishedListener = { if (isTransitioning) onStartClick() },
        label = "slideOut"
    )

    // --- Entrance animation ---
    var hasEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasEntered = true }
    val slideIn by animateFloatAsState(
        targetValue = if (hasEntered) 0f else 1f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "slideIn"
    )

    // --- Pulse บนปุ่ม Start ---
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // --- Credits dialog ---
    var showCredits by remember { mutableStateOf(false) }

    // ===== UI =====
    Box(
        modifier = Modifier
            .fillMaxSize()
            .paint(painterResource(id = R.drawable.game), contentScale = ContentScale.Crop)
    ) {

        // ปุ่มบ้าน — บนซ้าย (กลับโปรแกรมหลัก)
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .size(52.dp)
                .align(Alignment.TopStart)
                .clickable { onBackClick() },
            contentAlignment = Alignment.Center
        ) {
            Image(painter = painterResource(id = R.drawable.button_round_flat), contentDescription = null, modifier = Modifier.fillMaxSize())
            Image(painter = painterResource(id = R.drawable.home), contentDescription = "กลับหลัก", modifier = Modifier.size(28.dp))
        }

        // ❓ ปุ่มช่วยเหลือ / เครดิต — บนขวา
        Image(
            painter = painterResource(id = R.drawable.help),
            contentDescription = "เครดิต",
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .size(52.dp)
                .align(Alignment.TopEnd)
                .clickable { showCredits = true }
        )

        // เนื้อหากลาง
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // 🎮 โลโก้เกม — สไลด์เข้าจากบน / ออกทางบน
            Image(
                painter = painterResource(id = R.drawable.game1),
                contentDescription = "Q Piano Rhythm Master",
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .graphicsLayer {
                        translationY = -2400f * slideIn - 2400f * slideOut
                    }
            )

            Spacer(modifier = Modifier.height(56.dp))

            // ▶ ปุ่ม Start + Text ซ้อนกันใน Box เดียว
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .height(88.dp)
                    .scale(if (isTransitioning) 1f else pulseScale)
                    .graphicsLayer { translationY = 2400f * slideIn + 2400f * slideOut }
                    .clickable(enabled = !isTransitioning) { isTransitioning = true },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.startgame),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Text(
                    text = "เริ่มเล่น",
                    color = Color(0xFF7A4800),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.offset(y = 4.dp)
                )
            }
        }
    }

    // 📋 Credits Dialog
    if (showCredits) {
        Dialog(onDismissRequest = { showCredits = false }) {
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .background(Color(0xFF1E1E2E), shape = RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎵 เครดิต", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Q Piano Rhythm Master\n\nพัฒนาโดย: PetCompanion Team\n\nเพลง: Viacheslav Starostin\n(Royalty-Free Music)\n\nขอบคุณที่เล่น! 🎹",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF4CAF50), shape = RoundedCornerShape(8.dp))
                            .clickable { showCredits = false }
                            .padding(horizontal = 32.dp, vertical = 10.dp)
                    ) {
                        Text("ปิด", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
