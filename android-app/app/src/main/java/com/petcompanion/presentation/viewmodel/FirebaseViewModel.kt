package com.petcompanion.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.petcompanion.data.firebase.FirebaseConfig
import com.petcompanion.data.firebase.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FirebaseViewModel(application: Application) : AndroidViewModel(application) {

    val manager = FirebaseManager.getInstance(application)

    // ── Auth state ─────────────────────────────────────────────────────────
    val currentUser: StateFlow<FirebaseUser?> = manager.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, manager.auth.currentUser)

    val isConnected: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    // ── UI state ───────────────────────────────────────────────────────────
    private val _authResult = MutableStateFlow<AuthResult?>(null)
    val authResult: StateFlow<AuthResult?> = _authResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    sealed class AuthResult {
        object Success : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    // ── Teacher login ──────────────────────────────────────────────────────
    fun teacherLogin(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            manager.teacherLogin(email, password)
                .onSuccess { _authResult.value = AuthResult.Success }
                .onFailure { _authResult.value = AuthResult.Error(it.message ?: "Login ล้มเหลว") }
            _isLoading.value = false
        }
    }

    // ── Teacher register ───────────────────────────────────────────────────
    fun teacherRegister(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            manager.teacherRegister(email, password, displayName)
                .onSuccess { _authResult.value = AuthResult.Success }
                .onFailure { _authResult.value = AuthResult.Error(it.message ?: "สมัครสมาชิกล้มเหลว") }
            _isLoading.value = false
        }
    }

    fun teacherLogout() = manager.teacherLogout()

    fun clearResult() { _authResult.value = null }

    // ── compat กับโค้ดเดิม ─────────────────────────────────────────────────
    @Deprecated("ใช้ teacherLogin แทน")
    val savedConfig: FirebaseConfig? get() = null

    @Deprecated("ใช้ teacherLogin แทน")
    fun connect(config: FirebaseConfig) {}

    @Deprecated("ใช้ teacherLogout แทน")
    fun disconnect() = manager.teacherLogout()
}
