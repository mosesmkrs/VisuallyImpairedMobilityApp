package com.example.newapp.SQL.PC

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PcDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pcontact: PrimaryContact): Long
    
    @Query("SELECT * FROM primarycontacts WHERE userID = :userID ORDER BY pcID DESC")
    fun getContactsByUserId(userID: Int): LiveData<List<PrimaryContact>>
    
    @Query("SELECT * FROM primarycontacts WHERE userID = :userID LIMIT 1")
    suspend fun getPrimaryContact(userID: Int): PrimaryContact?
    
    @Query("SELECT EXISTS(SELECT 1 FROM primarycontacts WHERE userID = :userID LIMIT 1)")
    suspend fun contactExists(userID: Int): Boolean

    @Query("SELECT * FROM primarycontacts ORDER BY pcID DESC")
    fun getAllContacts(): LiveData<List<PrimaryContact>>

    @Update
    suspend fun update(pcontact: PrimaryContact)

    @Delete
    suspend fun delete(pcontact: PrimaryContact)
}
