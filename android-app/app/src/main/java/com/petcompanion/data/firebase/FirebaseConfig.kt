package com.petcompanion.data.firebase

data class FirebaseConfig(
    val apiKey: String,
    val projectId: String,
    val appId: String,
    val storageBucket: String = ""
)
