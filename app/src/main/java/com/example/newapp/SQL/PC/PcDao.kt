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
    suspend fun insert(pcontact: PrimaryContact)

    @Query("SELECT * FROM primarycontacts ORDER BY pcID DESC")
    fun getAllContacts(): LiveData<List<PrimaryContact>>

    @Update
    suspend fun update(pcontact: PrimaryContact)

    @Delete
    suspend fun delete(pcontact: PrimaryContact)
}
