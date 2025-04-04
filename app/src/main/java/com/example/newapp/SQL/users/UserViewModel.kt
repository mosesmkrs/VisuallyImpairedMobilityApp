package com.example.newapp.SQL.users

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.newapp.SQL.AppDatabase
import kotlinx.coroutines.launch

class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: UserRepository
    val allUsers: LiveData<List<Users>>

    init{
        val userDao = AppDatabase.Companion.getDatabase(application).userDao()
        repository = UserRepository(userDao)
        allUsers = repository.allUsers
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