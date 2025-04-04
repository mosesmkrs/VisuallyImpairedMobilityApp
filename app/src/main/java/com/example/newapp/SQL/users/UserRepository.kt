package com.example.newapp.SQL.users

import androidx.lifecycle.LiveData

class UserRepository(private val userDao: UserDao) {
    val allUsers: LiveData<List<Users>> = userDao.getAllUsers()

    suspend fun insert(users: Users){
        userDao.insert(users)
    }
    suspend fun update(users: Users){
        userDao.update(users)
    }

    suspend fun delete(user: Users){
        userDao.delete(user)
    }

}