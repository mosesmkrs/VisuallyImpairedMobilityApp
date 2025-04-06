package com.example.newapp.SQL.PC

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.newapp.SQL.users.Users

@Entity(
    tableName = "primarycontacts",
    foreignKeys = [
        ForeignKey(
            entity = Users::class,
            parentColumns = ["userID"],
            childColumns = ["userID"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userID")]
)
data class PrimaryContact (
    @PrimaryKey(autoGenerate = true) val pcID: Int = 0,
    val userID: Int,
    val contactname: String,
    val contactnumber: String
)