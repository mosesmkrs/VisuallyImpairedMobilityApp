package com.example.newapp.SQL.users

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDao {
    @Insert
    suspend fun insert(users: Users)

    @Query("SELECT * FROM users ORDER BY userID DESC")
    fun getAllUsers(): LiveData<List<Users>>

    @Update
    suspend fun update(users: Users)

    @Delete
    suspend fun delete(users: Users)

}