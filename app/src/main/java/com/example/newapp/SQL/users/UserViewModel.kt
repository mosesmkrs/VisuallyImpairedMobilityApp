package com.example.newapp.SQL.users

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.newapp.SQL.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: UserRepository
    val _allUsers: LiveData<List<Users>>

    init{
        val userDao = AppDatabase.Companion.getDatabase(application).userDao()
        repository = UserRepository(userDao)
        _allUsers = repository.allUsers
    }
    
    /**
     * Inserts a user into the database if they don't already exist
     * @param user The user to insert
     * @return true if the user was inserted, false if they already existed
     */
    suspend fun insertIfNotExists(user: Users): Boolean = withContext(Dispatchers.IO) {
        val exists = repository.userExists(user.firebaseUUID)
        if (!exists) {
            val result = repository.insert(user)
            return@withContext result > 0 // Insert successful
        }
        return@withContext false // User already exists
    }
    
    /**
     * Gets a user by their Firebase UUID
     * @param firebaseUUID The Firebase UUID to search for
     * @return The user if found, null otherwise
     */
    suspend fun getUserByFirebaseUUID(firebaseUUID: String): Users? = withContext(Dispatchers.IO) {
        return@withContext repository.getUserByFirebaseUUID(firebaseUUID)
    }
    
    /**
     * Checks if a user with the given Firebase UUID exists
     * @param firebaseUUID The Firebase UUID to check
     * @return true if the user exists, false otherwise
     */
    suspend fun userExists(firebaseUUID: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext repository.userExists(firebaseUUID)
    }
    
    /**
     * Gets a user's ID by their Firebase UUID
     * @param firebaseUUID The Firebase UUID to search for
     * @return The user ID if found, 0 otherwise
     */
    suspend fun getUserIDByFirebaseUUID(firebaseUUID: String): Int = withContext(Dispatchers.IO) {
        val user = repository.getUserByFirebaseUUID(firebaseUUID)
        return@withContext user?.userID ?: 0
    }
    
    /**
     * Inserts or updates a user in the database
     * If the user already exists (by Firebase UUID), they will be updated
     * Otherwise, they will be inserted
     * @param user The user to insert or update
     */
    fun insertOrUpdate(user: Users) = viewModelScope.launch {
        val existingUser = repository.getUserByFirebaseUUID(user.firebaseUUID)
        if (existingUser != null) {
            // Update the existing user with the new data but keep the same ID
            val updatedUser = user.copy(userID = existingUser.userID)
            repository.update(updatedUser)
        } else {
            repository.insert(user)
        }
    }
    
    fun insert(user: Users) = viewModelScope.launch {
        repository.insert(user)
    }
    
    fun update(user: Users) = viewModelScope.launch {
        repository.update(user)
    }
    
    fun delete(user: Users) = viewModelScope.launch {
        repository.delete(user)
    }
}