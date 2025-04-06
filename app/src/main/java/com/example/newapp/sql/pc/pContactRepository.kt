package com.example.newapp.SQL.PC

import androidx.lifecycle.LiveData

class pContactRepository(private val pContactDao: PcDao) {
    val allContacts: LiveData<List<PrimaryContact>> = pContactDao.getAllContacts()

    fun getContactsByUserId(userId: Int): LiveData<List<PrimaryContact>> {
        return pContactDao.getContactsByUserId(userId)
    }
    
    suspend fun getPrimaryContact(userId: Int): PrimaryContact? {
        return pContactDao.getPrimaryContact(userId)
    }
    
    suspend fun contactExists(userId: Int): Boolean {
        return pContactDao.contactExists(userId)
    }
    
    suspend fun insert(pcontact: PrimaryContact): Long {
        return pContactDao.insert(pcontact)
    }
    
    suspend fun update(pcontact: PrimaryContact) {
        pContactDao.update(pcontact)
    }
    
    suspend fun delete(pcontact: PrimaryContact) {
        pContactDao.delete(pcontact)
    }
    
    suspend fun insertOrUpdateContact(pcontact: PrimaryContact): Long {
        val existingContact = pContactDao.getPrimaryContact(pcontact.userID)
        return if (existingContact != null) {
            // Update existing contact but keep the same ID
            val updatedContact = pcontact.copy(pcID = existingContact.pcID)
            pContactDao.update(updatedContact)
            updatedContact.pcID.toLong()
        } else {
            // Insert new contact
            pContactDao.insert(pcontact)
        }
    }
}