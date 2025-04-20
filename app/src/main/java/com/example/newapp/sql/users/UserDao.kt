package com.example.newapp.SQL.users

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(users: Users): Long

    @Query("SELECT * FROM users WHERE firebaseUUID = :firebaseUUID LIMIT 1")
    suspend fun getUserByFirebaseUUID(firebaseUUID: String): Users?

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE firebaseUUID = :firebaseUUID LIMIT 1)")
    suspend fun userExists(firebaseUUID: String): Boolean

    @Query("SELECT * FROM users ORDER BY userID DESC")
    fun getAllUsers(): LiveData<List<Users>>

    @Update
    suspend fun update(users: Users)

    @Delete
    suspend fun delete(users: Users)
}