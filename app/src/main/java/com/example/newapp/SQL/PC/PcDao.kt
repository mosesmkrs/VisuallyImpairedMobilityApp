package com.example.newapp.SQL.PC

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update


@Dao
interface PcDao {
    @Insert
    suspend fun insert(pcontact: primaryContact)

    @Query("SELECT * FROM users ORDER BY userID DESC")
    fun getAllContacts(): LiveData<List<primaryContact>>

    @Update
    suspend fun update(pcontact: primaryContact)

    @Delete
    suspend fun delete(pcontact: primaryContact)
}
