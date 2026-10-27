package com.petcompanion.presentation.view

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.petcompanion.R
import com.petcompanion.domain.model.CustomLevelEntity
import com.petcompanion.domain.model.GameMode
import com.petcompanion.presentation.viewmodel.LevelViewModel

@Composable
fun LevelManageScreen(
    levelViewModel: LevelViewModel,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (CustomLevelEntity) -> Unit
) {
    val customLevels by levelViewModel.customLevelsRaw.collectAsState()

    // ── State สำหรับ dialog ──────────────────────────────────────────────────
    var showQrDialog  by remember { mutableStateOf(false) }
    var qrBitmap      by remember { mutableStateOf<Bitmap?>(null) }
    var qrTitle       by remember { mutableStateOf("") }

    var deleteTarget  by remember { mutableStateOf<CustomLevelEntity?>(null) }
    var showDeleteDlg by remember { mutableStateOf(false) }

    // ── Snackbar (import success/fail) ───────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snackMsg) {
        snackMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackMsg = null
        }
    }

    // ── QR Scanner launcher ──────────────────────────────────────────────────
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val json = result.contents
        if (json != null) {
            val ok = levelViewModel.importFromJson(json)
            snackMsg = if (ok) "นำเข้าด่านสำเร็จ!" else "QR Code ไม่ถูกต้อง"
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .paint(painterResource(id = R.drawable.game), contentScale = ContentScale.Crop)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // ── Top bar ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RoundImageButton(imageRes = R.drawable.home, onClick = onBackClick)

                    Text(
                        text = "จัดการด่านของฉัน",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    // ปุ่มสแกน QR
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFF1A2A40), CircleShape)
                            .border(2.dp, Color(0xFF4FC3F7), CircleShape)
                            .clickable {
                                val opts = ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt("สแกน QR ด่านจากเพื่อน")
                                    setBeepEnabled(true)
                                    setOrientationLocked(false)
                                }
                                scanLauncher.launch(opts)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("QR", color = Color(0xFF4FC3F7), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // ── รายการด่าน ───────────────────────────────────────────────
                if (customLevels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ยังไม่มีด่านของคุณเลย", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("กด + เพื่อสร้างด่านแรก", color = Color(0xFF4FC3F7), fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(customLevels, key = { it.id }) { entity ->
                            LevelManageItem(
                                entity = entity,
                                onEditClick  = { onEditClick(entity) },
                                onDeleteClick = {
                                    deleteTarget = entity
                                    showDeleteDlg = true
                                },
                                onShareClick = {
                                    // สร้าง QR bitmap
                                    val json = levelViewModel.toShareJson(entity)
                                    qrBitmap = try {
                                        BarcodeEncoder().encodeBitmap(json, BarcodeFormat.QR_CODE, 600, 600)
                                    } catch (e: Exception) { null }
                                    qrTitle = entity.name
                                    showQrDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // ── FAB เพิ่มด่าน ────────────────────────────────────────────────
            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 32.dp),
                containerColor = Color(0xFFD81B60),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "เพิ่มด่าน")
            }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    if (showDeleteDlg && deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDlg = false },
            containerColor = Color(0xFF1A2A40),
            title = {
                Text("ลบด่าน?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "คุณต้องการลบ \"${deleteTarget!!.name}\" ใช่ไหม?\nการลบไม่สามารถเรียกคืนได้",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget?.let { levelViewModel.deleteLevelEntity(it) }
                    showDeleteDlg = false
                    deleteTarget = null
                }) {
                    Text("ลบเลย", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDlg = false }) {
                    Text("ยกเลิก", color = Color(0xFF90A4AE))
                }
            }
        )
    }

    // ── QR dialog ────────────────────────────────────────────────────────────
    if (showQrDialog) {
        Dialog(onDismissRequest = { showQrDialog = false }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A2A40))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "แชร์ด่าน: $qrTitle",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .size(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(8.dp)
                        )
                        Text(
                            text = "ให้เพื่อนสแกน QR นี้เพื่อนำเข้าด่าน",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    } else {
                        Text("สร้าง QR Code ไม่ได้", color = Color(0xFFF44336))
                    }

                    TextButton(onClick = { showQrDialog = false }) {
                        Text("ปิด", color = Color(0xFF4FC3F7), fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// ── Item card ─────────────────────────────────────────────────────────────────

@Composable
private fun LevelManageItem(
    entity: CustomLevelEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val modeColor = if (entity.mode == "PIANO_KEYS") Color(0xFF81C784) else Color(0xFF4FC3F7)
    val modeLabel = if (entity.mode == "PIANO_KEYS") "เปียโนคีย์" else "โน้ตดนตรี"
    val accidentalBadge = entity.mode != "PIANO_KEYS" && entity.useAccidentals

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A2A40), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF2C3E50), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── ข้อมูลด่าน ───────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // โหมด badge
                    Box(
                        modifier = Modifier
                            .background(modeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(modeLabel, color = modeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    // accidentals badge
                    if (accidentalBadge) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFB300).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("C#D#F#...", color = Color(0xFFFFB300), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    // เวลา
                    Text("${entity.timeSeconds}วิ", color = Color(0xFF78909C), fontSize = 11.sp)
                }
            }

            // ── ปุ่มแชร์ / แก้ไข / ลบ ───────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Share QR
                IconButton(onClick = onShareClick) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF0288D1).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Color(0xFF0288D1), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("QR", color = Color(0xFF4FC3F7), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                // Edit
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "แก้ไข", tint = Color(0xFF90CAF9))
                }
                // Delete
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "ลบ", tint = Color(0xFFEF9A9A))
                }
            }
        }
    }
}
