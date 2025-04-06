package com.example.newapp.SQL.SC

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.newapp.SQL.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class sCViewModel(application: Application): AndroidViewModel(application) {
    private val repository : sContactRepository
    val _allContacts: LiveData<List<SecondaryContact>>

    init{
        val sContactDao = AppDatabase.Companion.getDatabase(application).sContactDao()
        repository = sContactRepository(sContactDao)
        _allContacts = repository.allContacts
    }
    
    /**
     * Get contacts for a specific user
     */
    fun getContactsByUserId(userId: Int): LiveData<List<SecondaryContact>> {
        return repository.getContactsByUserId(userId)
    }
    
    /**
     * Check if a secondary contact exists for a user
     */
    suspend fun contactExists(userId: Int): Boolean = withContext(Dispatchers.IO) {
        return@withContext repository.contactExists(userId)
    }
    
    /**
     * Get a secondary contact for a user
     */
    suspend fun getSecondaryContact(userId: Int): SecondaryContact? = withContext(Dispatchers.IO) {
        return@withContext repository.getSecondaryContact(userId)
    }
    
    /**
     * Insert or update a contact
     * If a secondary contact already exists for the user, it will be updated
     * Otherwise, a new contact will be inserted
     */
    suspend fun insertOrUpdateContact(scontact: SecondaryContact): Long = withContext(Dispatchers.IO) {
        return@withContext repository.insertOrUpdateContact(scontact)
    }
    
    /**
     * Insert a new contact
     */
    fun insert(scontact: SecondaryContact) = viewModelScope.launch {
        repository.insert(scontact)
    }
    
    /**
     * Update an existing contact
     */
    fun update(scontact: SecondaryContact) = viewModelScope.launch {
        repository.update(scontact)
    }
    
    /**
     * Delete a contact
     */
    fun delete(scontact: SecondaryContact) = viewModelScope.launch {
        repository.delete(scontact)
    }
}
