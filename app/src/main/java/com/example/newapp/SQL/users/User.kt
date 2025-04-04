package com.example.newapp.SQL.users

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class Users (
    @PrimaryKey(autoGenerate = true) val userID: Int = 0,
    val firebaseUUID: String,
    val name: String,
    val email: String,
    val photoURL: String,

)
