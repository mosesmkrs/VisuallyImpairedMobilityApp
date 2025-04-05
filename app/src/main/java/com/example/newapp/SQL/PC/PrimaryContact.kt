package com.example.newapp.SQL.PC

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "primarycontacts")
data class PrimaryContact (
    @PrimaryKey(autoGenerate = true) val pcID: Int = 0,
    val userID: Int,
    val contactname: String,
    val contactnumber: String
)