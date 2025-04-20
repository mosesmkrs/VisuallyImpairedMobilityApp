package com.example.newapp.SQL.users

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["firebaseUUID"], unique = true)]
)
data class Users (
    @PrimaryKey(autoGenerate = true) val userID: Int = 0,
    val firebaseUUID: String,
    val name: String,
    val email: String,
    val photoURL: String
)