package com.petcompanion.presentation.view

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun SpriteButton(
    spriteSheet: ImageBitmap,
    // รับพิกัดสี่เหลี่ยม (x, y, กว้าง, สูง) ในไฟล์สไปรต์ สำหรับเฟรมปกติ
    normalFrame: androidx.compose.ui.unit.IntRect,
    // รับพิกัดสี่เหลี่ยม (x, y, กว้าง, สูง) ในไฟล์สไปรต์ สำหรับเฟรมตอนกระพริบ/โดนกด
    pressedFrame: androidx.compose.ui.unit.IntRect,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // สร้างตัวแปรคุมสถานะการกระพริบ (สลับค่า 0 กับ 1)
    val infiniteTransition = rememberInfiniteTransition(label = "Blink")
    val frameIndex by infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = 1,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing), // สลับทุกๆ 0.5 วินาที
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlinkFrame"
    )

    // เลือกพิกัดเฟรมที่จะแสดงผล ตามสถานะ frameIndex
    val currentFrame = if (frameIndex == 0) normalFrame else pressedFrame

    Canvas(
        modifier = modifier
            .size(currentFrame.width.dp, currentFrame.height.dp) // ตั้งขนาด Canvas ให้เท่ากับขนาดปุ่ม
            .clickable { onClick() }
    ) {
        // วาดรูปโดยดึงมาจาก spriteSheet
        drawImage(
            image = spriteSheet,
            srcOffset = IntOffset(currentFrame.left, currentFrame.top), // จุดเริ่มต้นการ Crop (ซ้ายบน)
            srcSize = IntSize(currentFrame.width, currentFrame.height), // ขนาดการ Crop
            dstOffset = IntOffset.Zero, // จุดที่จะวาดบน Canvas
            dstSize = IntSize(size.width.toInt(), size.height.toInt()) // ขนาดที่จะวาด
        )
    }
}

// ปุ่ม Sprite ที่กำหนดขนาดแสดงผล (dp) ได้อิสระ — ใช้กับปุ่มนำทางสไตล์เกม
@Composable
fun SpriteIconButton(
    spriteSheet: ImageBitmap,
    normalFrame: androidx.compose.ui.unit.IntRect,
    pressedFrame: androidx.compose.ui.unit.IntRect,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Blink")
    val frameIndex by infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = 1,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlinkFrame"
    )

    val currentFrame = if (frameIndex == 0) normalFrame else pressedFrame

    Canvas(
        modifier = modifier
            .size(size)
            .clickable { onClick() }
    ) {
        drawImage(
            image = spriteSheet,
            srcOffset = IntOffset(currentFrame.left, currentFrame.top),
            srcSize = IntSize(currentFrame.width, currentFrame.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt())
        )
    }
}