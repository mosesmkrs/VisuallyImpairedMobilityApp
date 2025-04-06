package com.example.newapp.SQL.users

import androidx.lifecycle.LiveData

class UserRepository(private val userDao: UserDao) {
    val allUsers: LiveData<List<Users>> = userDao.getAllUsers()

    suspend fun insert(users: Users): Long {
        return userDao.insert(users)
    }
    
    suspend fun getUserByFirebaseUUID(firebaseUUID: String): Users? {
        return userDao.getUserByFirebaseUUID(firebaseUUID)
    }
    
    suspend fun userExists(firebaseUUID: String): Boolean {
        return userDao.userExists(firebaseUUID)
    }
    
    suspend fun update(users: Users) {
        userDao.update(users)
    }

    suspend fun delete(user: Users) {
        userDao.delete(user)
    }
}