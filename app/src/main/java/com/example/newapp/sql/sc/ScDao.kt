package com.example.newapp.SQL.SC

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ScDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scontact: SecondaryContact): Long
    
    @Query("SELECT * FROM secondarycontacts WHERE userID = :userID ORDER BY scID DESC")
    fun getContactsByUserId(userID: Int): LiveData<List<SecondaryContact>>
    
    @Query("SELECT * FROM secondarycontacts WHERE userID = :userID LIMIT 1")
    suspend fun getSecondaryContact(userID: Int): SecondaryContact?
    
    @Query("SELECT EXISTS(SELECT 1 FROM secondarycontacts WHERE userID = :userID LIMIT 1)")
    suspend fun contactExists(userID: Int): Boolean

    @Query("SELECT * FROM secondarycontacts ORDER BY scID DESC")
    fun getAllContacts(): LiveData<List<SecondaryContact>>

    @Update
    suspend fun update(scontact: SecondaryContact)

    @Delete
    suspend fun delete(scontact: SecondaryContact)
}
