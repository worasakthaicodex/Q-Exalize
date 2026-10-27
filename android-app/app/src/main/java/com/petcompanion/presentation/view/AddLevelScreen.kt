package com.petcompanion.presentation.view

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R
import com.petcompanion.domain.model.CustomLevelEntity
import com.petcompanion.domain.model.GameMode
import com.petcompanion.presentation.viewmodel.LevelViewModel

/**
 * หน้าสร้าง/แก้ไขด่านเกม
 * @param editEntity ถ้าไม่ null = โหมดแก้ไข (pre-fill ค่าจาก entity)
 */
@Composable
fun AddLevelScreen(
    levelViewModel: LevelViewModel,
    onBackClick: () -> Unit,
    onSaved: () -> Unit,
    editEntity: CustomLevelEntity? = null
) {
    val isEditMode = editEntity != null

    // ── State ของฟอร์ม (pre-fill จาก editEntity ถ้ามี) ─────────────────────
    var name           by remember { mutableStateOf(editEntity?.name           ?: "") }
    var timeSeconds    by remember { mutableStateOf(editEntity?.timeSeconds    ?: 60) }
    var mode           by remember { mutableStateOf(
        if (editEntity?.mode == "PIANO_KEYS") GameMode.PIANO_KEYS else GameMode.MUSIC_KEYS
    ) }
    var wrongPenalty   by remember { mutableStateOf(editEntity?.wrongPenalty   ?: 5) }
    var comboThreshold by remember { mutableStateOf(editEntity?.comboThreshold ?: 0) }
    var comboBonus     by remember { mutableStateOf(editEntity?.comboBonus     ?: 0) }
    var star1Score     by remember { mutableStateOf(editEntity?.star1Score     ?: 20) }
    var star2Score     by remember { mutableStateOf(editEntity?.star2Score     ?: 50) }
    var star3Score     by remember { mutableStateOf(editEntity?.star3Score     ?: 90) }
    var useAccidentals by remember { mutableStateOf(editEntity?.useAccidentals ?: true) }

    // entrance slide-in
    var hasEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasEntered = true }
    val slideProgress by animateFloatAsState(
        targetValue = if (hasEntered) 0f else 1f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "slideIn"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .paint(painterResource(id = R.drawable.game), contentScale = ContentScale.Crop)
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── หัว ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = -2400f * slideProgress },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoundImageButton(imageRes = R.drawable.home, onClick = onBackClick)
                Text(
                    text = if (isEditMode) "แก้ไขด่าน" else "สร้างด่านใหม่",
                    color = Color(0xFFD81B60),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.size(64.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── กล่องฟอร์ม ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 24.dp)
                    .graphicsLayer { translationY = 2400f * slideProgress }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.box_blue_square),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    // ── ชื่อด่าน ─────────────────────────────────────────────
                    AddSectionLabel("ชื่อด่าน")
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("เช่น ด่านพิเศษ", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4FC3F7), unfocusedBorderColor = Color(0xFF455A64),
                            cursorColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // ── เวลา ─────────────────────────────────────────────────
                    AddSectionLabel("เวลาที่ให้ทำ")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(30 to "30 วิ", 45 to "45 วิ", 60 to "1 นาที").forEach { (sec, label) ->
                            AddChip(text = label, selected = timeSeconds == sec, onClick = { timeSeconds = sec }, modifier = Modifier.weight(1f))
                        }
                    }

                    // ── โหมด ─────────────────────────────────────────────────
                    AddSectionLabel("โหมดเกม")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(GameMode.MUSIC_KEYS, GameMode.PIANO_KEYS).forEach { m ->
                            AddChip(text = m.displayName, selected = mode == m, onClick = { mode = m }, modifier = Modifier.weight(1f))
                        }
                    }

                    // ── ใช้คีย์ยาก (เฉพาะ MUSIC_KEYS) ──────────────────────
                    if (mode == GameMode.MUSIC_KEYS) {
                        AddSectionLabel("โน้ตยาก (C# D# F# G# A#)")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            AddChip(
                                text = "ใช้โน้ตยาก",
                                selected = useAccidentals,
                                onClick = { useAccidentals = true },
                                modifier = Modifier.weight(1f)
                            )
                            AddChip(
                                text = "แค่โน้ตง่าย",
                                selected = !useAccidentals,
                                onClick = { useAccidentals = false },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // ── หักคะแนน ─────────────────────────────────────────────
                    AddSectionLabel("หักคะแนนเมื่อตอบผิด")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(0 to "ไม่หัก", 3 to "-3", 5 to "-5", 10 to "-10", 15 to "-15").forEach { (p, label) ->
                            AddChip(text = label, selected = wrongPenalty == p, onClick = { wrongPenalty = p }, modifier = Modifier.weight(1f))
                        }
                    }

                    // ── คอมโบ ────────────────────────────────────────────────
                    AddSectionLabel("คอมโบ (ตอบถูกติดกัน)")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(0 to "ปิด", 2 to "2 ครั้ง", 3 to "3 ครั้ง").forEach { (t, label) ->
                            AddChip(text = label, selected = comboThreshold == t, onClick = {
                                comboThreshold = t
                                if (t == 0) comboBonus = 0
                            }, modifier = Modifier.weight(1f))
                        }
                    }

                    if (comboThreshold > 0) {
                        AddSectionLabel("โบนัสเมื่อครบคอมโบ")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(5, 8, 10, 15, 20).forEach { b ->
                                AddChip(text = "+$b", selected = comboBonus == b, onClick = { comboBonus = b }, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // ── เกณฑ์ดาว ─────────────────────────────────────────────
                    AddSectionLabel("⭐ 1 ดาว (ผ่านด่าน)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(10, 20, 30, 40).forEach { v ->
                            AddChip(text = "$v แต้ม", selected = star1Score == v, onClick = {
                                star1Score = v
                                if (star2Score <= v) star2Score = v + 10
                                if (star3Score <= star2Score) star3Score = star2Score + 10
                            }, modifier = Modifier.weight(1f))
                        }
                    }

                    AddSectionLabel("⭐⭐ 2 ดาว")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(40, 60, 80, 100).forEach { v ->
                            val enabled = v > star1Score
                            AddChip(text = "$v แต้ม", selected = star2Score == v, onClick = {
                                if (enabled) { star2Score = v; if (star3Score <= v) star3Score = v + 10 }
                            }, modifier = Modifier.weight(1f), dimmed = !enabled)
                        }
                    }

                    AddSectionLabel("⭐⭐⭐ 3 ดาว")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(70, 90, 120, 150).forEach { v ->
                            val enabled = v > star2Score
                            AddChip(text = "$v แต้ม", selected = star3Score == v, onClick = {
                                if (enabled) star3Score = v
                            }, modifier = Modifier.weight(1f), dimmed = !enabled)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // ── ปุ่มบันทึก ───────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .background(
                                color = if (name.isNotBlank()) Color(0xFF43A047) else Color(0xFF37474F),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = name.isNotBlank()) {
                                val entity = CustomLevelEntity(
                                    id             = editEntity?.id ?: 0,
                                    name           = name.trim(),
                                    timeSeconds    = timeSeconds,
                                    mode           = mode.name,
                                    wrongPenalty   = wrongPenalty,
                                    comboThreshold = comboThreshold,
                                    comboBonus     = comboBonus,
                                    star1Score     = star1Score,
                                    star2Score     = star2Score,
                                    star3Score     = star3Score,
                                    useAccidentals = if (mode == GameMode.MUSIC_KEYS) useAccidentals else false
                                )
                                if (isEditMode) levelViewModel.updateLevel(entity)
                                else            levelViewModel.addLevel(entity)
                                onSaved()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEditMode) "✅  บันทึกการแก้ไข" else "✅  บันทึกด่าน",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AddSectionLabel(text: String) {
    Text(text = text, color = Color(0xFFB0BEC5), fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun AddChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    val bgColor = when {
        dimmed   -> Color(0xFF263238)
        selected -> Color(0xFF0288D1)
        else     -> Color(0xFF1A2A40)
    }
    val borderColor = when {
        dimmed   -> Color(0xFF37474F)
        selected -> Color(0xFF4FC3F7)
        else     -> Color(0xFF2C3E50)
    }
    val textColor = when {
        dimmed   -> Color(0xFF546E7A)
        selected -> Color.White
        else     -> Color(0xFF78909C)
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .background(color = bgColor, shape = RoundedCornerShape(8.dp))
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .clickable(enabled = !dimmed) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, fontSize = 13.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal)
    }
}
