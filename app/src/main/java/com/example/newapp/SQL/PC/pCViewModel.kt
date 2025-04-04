package com.example.newapp.SQL.PC

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.newapp.SQL.AppDatabase
import kotlinx.coroutines.launch

class pCViewModel(application: Application): AndroidViewModel(application) {
    private val repository : pContactRepository
    val allContacts: LiveData<List<primaryContact>>

    init{
        val pContactDao = AppDatabase.Companion.getDatabase(application).pContactDao()
        repository = pContactRepository(pContactDao)
        allContacts = repository.allContacts
    }
    fun insert(pcontact: primaryContact) = viewModelScope.launch {
        repository.insert(pcontact)
    }
    fun update(pcontact: primaryContact) = viewModelScope.launch {
        repository.update(pcontact)
    }
    fun delete(pcontact: primaryContact) = viewModelScope.launch {
        repository.delete(pcontact)
    }

}