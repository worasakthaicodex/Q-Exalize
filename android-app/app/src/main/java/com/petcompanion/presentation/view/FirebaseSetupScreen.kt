package com.petcompanion.presentation.view

import androidx.compose.runtime.Composable
import com.petcompanion.presentation.viewmodel.FirebaseViewModel

// หน้านี้ถูกแทนที่ด้วย TeacherLoginScreen แล้ว — เก็บไว้เพื่อไม่ให้ reference เดิมพัง
@Composable
fun FirebaseSetupScreen(
    viewModel: FirebaseViewModel,
    onBackClick: () -> Unit
) {
    TeacherLoginScreen(
        viewModel = viewModel,
        onBackClick = onBackClick,
        onLoginSuccess = onBackClick
    )
}
