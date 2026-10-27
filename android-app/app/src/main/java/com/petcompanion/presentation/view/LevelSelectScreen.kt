package com.petcompanion.presentation.view

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

import com.petcompanion.R
import com.petcompanion.domain.model.GameLevelConfig
import com.petcompanion.presentation.viewmodel.LevelViewModel
import com.petcompanion.presentation.viewmodel.UserViewModel
import com.petcompanion.presentation.theme.PrimaryBlue

@Composable
fun LevelSelectScreen(
    levelViewModel: LevelViewModel,
    userViewModel: UserViewModel,
    onLevelClick: (GameLevelConfig) -> Unit,
    onBackClick: () -> Unit,
    onAddLevelClick: () -> Unit
) {
    val context = LocalContext.current

    // 🎵 เพลงพื้นหลัง
    DisposableEffect(Unit) {
        val mp = MediaPlayer.create(context, R.raw.bgm_intro)?.apply {
            isLooping = true
            start()
        }
        onDispose { mp?.stop(); mp?.release() }
    }

    // รวม built-in + custom + progress จาก ViewModel
    val allLevels by levelViewModel.allLevels.collectAsState()
    val totalStars = allLevels.sumOf { it.stars }

    // ข้อมูลโปรไฟล์
    val profileState by userViewModel.userProfile.collectAsState()
    val displayName = profileState?.name ?: "P"
    val imageUri = profileState?.profileImagePath

    // ── Pagination ──────────────────────────────────────────────────────────
    val pageSize = 16
    val totalPages = maxOf(1, (allLevels.size + pageSize - 1) / pageSize)
    var currentPage by remember { mutableStateOf(0) }

    // เริ่มที่หน้าที่มีด่านล่าสุดที่ปลดล็อกแล้ว
    LaunchedEffect(allLevels) {
        if (allLevels.isNotEmpty()) {
            val lastUnlockedIdx = allLevels.indexOfLast { !it.isLocked }
            currentPage = if (lastUnlockedIdx < 0) 0 else lastUnlockedIdx / pageSize
        }
    }

    val visibleLevels = allLevels.drop(currentPage * pageSize).take(pageSize)
    val lastUnlockedGlobalIdx = allLevels.indexOfLast { !it.isLocked }

    // Transition state
    var selectedConfig by remember { mutableStateOf<GameLevelConfig?>(null) }
    var isTransitioning by remember { mutableStateOf(false) }

    val slideProgress by animateFloatAsState(
        targetValue = if (isTransitioning) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        finishedListener = { if (isTransitioning) selectedConfig?.let { onLevelClick(it) } },
        label = "slide"
    )

    fun handleLevelClick(config: GameLevelConfig) {
        if (isTransitioning) return
        MediaPlayer.create(context, R.raw.sound_correct)?.apply {
            start()
            setOnCompletionListener { release() }
        }
        selectedConfig = config
        isTransitioning = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .paint(painterResource(id = R.drawable.game), contentScale = ContentScale.Crop)
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── ปุ่มบน + โปรไฟล์กลาง — สไลด์ขึ้น ─────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer { translationY = -2400f * slideProgress },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoundImageButton(imageRes = R.drawable.home, onClick = onBackClick)

                // ── กลาง: โปรไฟล์ + ดาวรวม ────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // รูปโปรไฟล์
                    if (!imageUri.isNullOrEmpty()) {
                        AsyncImage(
                            model = Uri.fromFile(java.io.File(imageUri)),
                            contentDescription = "Profile",
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(displayName.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    // ดาวรวม
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("$totalStars", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Image(painter = painterResource(id = R.drawable.icon_small_whiteoutline_star), contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }

                RoundImageButton(imageRes = R.drawable.music, onClick = onAddLevelClick)
            }

            // ── บอร์ดด่าน — สไลด์ลง ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 150.dp)
                    .graphicsLayer { translationY = 2400f * slideProgress },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.width(340.dp).height(460.dp)) {

                    Image(
                        painter = painterResource(id = R.drawable.box_blue_square),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )

                    // ป้าย "เลือกระดับ"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(65.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = (-25).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.banner_green),
                            contentDescription = null,
                            modifier = Modifier.width(220.dp).fillMaxHeight(),
                            contentScale = ContentScale.FillBounds
                        )
                        Text(
                            text = "เลือกระดับ",
                            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.offset(y = (-4).dp)
                        )
                    }

                    // ตารางด่าน (16 ด่านต่อหน้า)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 50.dp, start = 20.dp, end = 20.dp, bottom = 70.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        userScrollEnabled = false
                    ) {
                        itemsIndexed(visibleLevels) { localIndex, level ->
                            val globalIndex = currentPage * pageSize + localIndex
                            LevelConfigItem(
                                level = level,
                                displayNumber = globalIndex + 1,
                                onLevelClick = ::handleLevelClick,
                                isPulsing = globalIndex == lastUnlockedGlobalIdx
                            )
                        }
                    }

                    // ── ปุ่มซ้าย / หน้า / ขวา ────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth().align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // ◀ ก่อนหน้า
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { alpha = if (currentPage > 0) 1f else 0.3f }
                                .clickable(enabled = currentPage > 0) { currentPage-- },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.scrollslider_blank_arrow),
                                contentDescription = "หน้าก่อน",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // เลขหน้า
                        Text(
                            text = "${currentPage + 1} / $totalPages",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        // ▶ ถัดไป
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { alpha = if (currentPage < totalPages - 1) 1f else 0.3f }
                                .clickable(enabled = currentPage < totalPages - 1) { currentPage++ },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.scrollslider_blank_arrow),
                                contentDescription = "หน้าถัดไป",
                                modifier = Modifier.fillMaxSize().rotate(180f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── ปุ่มด่าน ────────────────────────────────────────────────────────────────

@Composable
fun LevelConfigItem(
    level: GameLevelConfig,
    displayNumber: Int,
    onLevelClick: (GameLevelConfig) -> Unit,
    isPulsing: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 1.12f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(700, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "pulseScale"
    )

    val buttonRes = if (level.stars > 0) R.drawable.iconbutton_small_orange_square else R.drawable.iconbutton_small_blue_square

    Box(
        modifier = Modifier
            .size(64.dp).scale(pulseScale)
            .clickable(enabled = !level.isLocked) { onLevelClick(level) },
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(id = buttonRes), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)

        if (level.isLocked) {
            Image(painter = painterResource(id = R.drawable.icon_small_whiteoutline_logk), contentDescription = null, modifier = Modifier.size(28.dp))
        } else {
            Text(text = "$displayNumber", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.offset(y = (-2).dp))
        }

        // ดาว
        if (!level.isLocked && level.stars > 0) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 8.dp),
                horizontalArrangement = Arrangement.spacedBy((-6).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (level.stars >= 1) Image(painter = painterResource(id = R.drawable.icon_small_whiteoutline_star), contentDescription = null, modifier = Modifier.size(20.dp).rotate(-15f))
                if (level.stars >= 2) Image(painter = painterResource(id = R.drawable.icon_small_whiteoutline_star), contentDescription = null, modifier = Modifier.size(24.dp).offset(y = (-4).dp))
                if (level.stars == 3) Image(painter = painterResource(id = R.drawable.icon_small_whiteoutline_star), contentDescription = null, modifier = Modifier.size(20.dp).rotate(15f))
            }
        }
    }
}

// ── shared helpers ───────────────────────────────────────────────────────────

@Composable
fun RoundImageButton(
    imageRes: Int,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 64.dp
) {
    Box(
        modifier = Modifier.size(size).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(id = R.drawable.button_round_flat), contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(painter = painterResource(id = imageRes), contentDescription = null, modifier = Modifier.size(size * 0.55f))
    }
}
