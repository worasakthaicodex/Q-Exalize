package com.petcompanion.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1, // มีแค่ Record เดียว
    val name: String,
    val email: String,
    val profileImagePath: String?
)