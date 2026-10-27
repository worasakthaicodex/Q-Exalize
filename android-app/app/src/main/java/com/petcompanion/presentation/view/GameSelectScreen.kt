package com.petcompanion.presentation.view

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R

/**
 * หน้าเลือกเกมเพื่อการศึกษา — แสดงเกมที่มีในระบบ
 */
@Composable
fun GameSelectScreen(
    onMusicGameClick: () -> Unit,
    onBackClick: () -> Unit
) {
    // Entrance animation
    var hasEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasEntered = true }
    val slideProgress by animateFloatAsState(
        targetValue = if (hasEntered) 0f else 1f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "slideIn"
    )

    // Pulse บนการ์ดเกม
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .paint(painterResource(id = R.drawable.game), contentScale = ContentScale.Crop)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top bar ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .graphicsLayer { translationY = -2400f * slideProgress },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoundImageButton(imageRes = R.drawable.home, onClick = onBackClick)

                Text(
                    text = "เกมเพื่อการศึกษา",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.size(64.dp))
            }

            // ── รายการเกม ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer { translationY = 2400f * slideProgress },
                contentAlignment = Alignment.TopCenter
            ) {
                // ── การ์ดเกม: Q Piano Rhythm Master ────────────────────────
                GameCard(
                    logoPainter = R.drawable.game1,
                    title = "Q Piano Rhythm Master",
                    subtitle = "ฝึกอ่านโน้ตดนตรีและเล่นเปียโน\nสนุกไปกับจังหวะและทำนองเพลง",
                    badgeText = "ดนตรี",
                    badgeColor = Color(0xFF0288D1),
                    pulseScale = pulseScale,
                    onClick = onMusicGameClick
                )
            }
        }
    }
}

// ── Game Card Component ───────────────────────────────────────────────────────

@Composable
private fun GameCard(
    logoPainter: Int,
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    pulseScale: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulseScale)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A2A40), Color(0xFF0D1B2A))
                )
            )
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // โลโก้เกม
            Image(
                painter = painterResource(id = logoPainter),
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentScale = ContentScale.Fit
            )

            // ข้อมูลด้านล่าง
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1B2A).copy(alpha = 0.6f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Badge
                        Box(
                            modifier = Modifier
                                .background(badgeColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(badgeText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(subtitle, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // ปุ่มเล่น
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFFD81B60), Color(0xFFAD1457))),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▶  เล่นเลย!", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
