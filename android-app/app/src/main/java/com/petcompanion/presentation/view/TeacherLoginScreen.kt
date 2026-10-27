package com.petcompanion.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.presentation.theme.LocalAppColors
import com.petcompanion.presentation.viewmodel.FirebaseViewModel

@Composable
fun TeacherLoginScreen(
    viewModel: FirebaseViewModel,
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val colors = LocalAppColors.current
    val authResult by viewModel.authResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(authResult) {
        if (authResult is FirebaseViewModel.AuthResult.Success) {
            viewModel.clearResult()
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "กลับ", tint = colors.textPrimary)
            }
            Text(
                if (isRegisterMode) "สมัครสมาชิกครู" else "เข้าสู่ระบบครู",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Icon(
                Icons.Default.School,
                contentDescription = null,
                tint = Color(0xFF4285F4),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Q-Growth",
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "ระบบห้องเรียนออนไลน์",
                color = colors.textSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(32.dp))

            // ชื่อ (เฉพาะสมัครสมาชิก)
            if (isRegisterMode) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("ชื่อ-นามสกุล", color = colors.textSecondary) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = colors.textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = outlinedTextFieldColors(colors)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("อีเมล", color = colors.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = colors.textSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = outlinedTextFieldColors(colors)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("รหัสผ่าน", color = colors.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.textSecondary) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = colors.textSecondary
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = outlinedTextFieldColors(colors)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Error
            val error = (authResult as? FirebaseViewModel.AuthResult.Error)?.message
            if (error != null) {
                Text(
                    error,
                    color = Color(0xFFF44336),
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ปุ่มหลัก
            Button(
                onClick = {
                    viewModel.clearResult()
                    if (isRegisterMode) {
                        viewModel.teacherRegister(email.trim(), password, displayName.trim())
                    } else {
                        viewModel.teacherLogin(email.trim(), password)
                    }
                },
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank() &&
                        (!isRegisterMode || displayName.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        if (isRegisterMode) "สมัครสมาชิก" else "เข้าสู่ระบบ",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // สลับโหมด
            TextButton(onClick = {
                isRegisterMode = !isRegisterMode
                viewModel.clearResult()
            }) {
                Text(
                    if (isRegisterMode) "มีบัญชีแล้ว? เข้าสู่ระบบ"
                    else "ยังไม่มีบัญชี? สมัครสมาชิกฟรี",
                    color = Color(0xFF4285F4)
                )
            }
        }
    }
}

@Composable
private fun outlinedTextFieldColors(colors: com.petcompanion.presentation.theme.QGrowthColors) =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedBorderColor = Color(0xFF4285F4),
        unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.4f),
        cursorColor = Color(0xFF4285F4)
    )
