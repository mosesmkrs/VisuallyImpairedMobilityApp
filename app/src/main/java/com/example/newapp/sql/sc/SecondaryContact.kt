package com.example.newapp.SQL.SC

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.newapp.SQL.users.Users

@Entity(
    tableName = "secondarycontacts",
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
data class SecondaryContact (
    @PrimaryKey(autoGenerate = true) val scID: Int = 0,
    val userID: Int,
    val contactname: String,
    val contactnumber: String
)
