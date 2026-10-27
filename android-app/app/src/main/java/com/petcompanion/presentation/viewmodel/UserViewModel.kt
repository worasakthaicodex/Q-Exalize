package com.petcompanion.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcompanion.data.local.UserProfileDao
import com.petcompanion.domain.model.UserProfileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn // 📍 เติม import ตัวนี้เข้ามา
import kotlinx.coroutines.launch // 📍 เติม import ตัวนี้เผื่อไว้ใช้ทำฟังก์ชัน Save

class UserViewModel(private val userProfileDao: UserProfileDao) : ViewModel() {

    // 📍 ตอนนี้ getUserProfile() เป็น Flow แล้ว เส้นแดงตรง stateIn จะหายไปทันทีครับ!
    val userProfile = userProfileDao.getUserProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // 📍 ฟังก์ชันสำหรับกดปุ่ม Save ในหน้า Profile
    fun saveProfile(newName: String, newImageUri: String?) {
        viewModelScope.launch {
            val profile = UserProfileEntity(
                id = 1,
                name = newName,
                email="",
                profileImagePath = newImageUri
            )
            userProfileDao.insertOrUpdateProfile(profile)
        }
    }
}