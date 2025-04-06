package com.example.newapp.SQL.PC

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.newapp.SQL.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class pCViewModel(application: Application): AndroidViewModel(application) {
    private val repository : pContactRepository
    val _allContacts: LiveData<List<PrimaryContact>>

    init{
        val pContactDao = AppDatabase.Companion.getDatabase(application).pContactDao()
        repository = pContactRepository(pContactDao)
        _allContacts = repository.allContacts
    }
    
    /**
     * Get contacts for a specific user
     */
    fun getContactsByUserId(userId: Int): LiveData<List<PrimaryContact>> {
        return repository.getContactsByUserId(userId)
    }
    
    /**
     * Check if a primary contact exists for a user
     */
    suspend fun contactExists(userId: Int): Boolean = withContext(Dispatchers.IO) {
        return@withContext repository.contactExists(userId)
    }
    
    /**
     * Get a primary contact for a user
     */
    suspend fun getPrimaryContact(userId: Int): PrimaryContact? = withContext(Dispatchers.IO) {
        return@withContext repository.getPrimaryContact(userId)
    }
    
    /**
     * Insert or update a contact
     * If a primary contact already exists for the user, it will be updated
     * Otherwise, a new contact will be inserted
     */
    suspend fun insertOrUpdateContact(pcontact: PrimaryContact): Long = withContext(Dispatchers.IO) {
        return@withContext repository.insertOrUpdateContact(pcontact)
    }
    
    /**
     * Insert a new contact
     */
    fun insert(pcontact: PrimaryContact) = viewModelScope.launch {
        repository.insert(pcontact)
    }
    
    /**
     * Update an existing contact
     */
    fun update(pcontact: PrimaryContact) = viewModelScope.launch {
        repository.update(pcontact)
    }
    
    /**
     * Delete a contact
     */
    fun delete(pcontact: PrimaryContact) = viewModelScope.launch {
        repository.delete(pcontact)
    }
}