package com.example.newapp.SQL.SC

import androidx.lifecycle.LiveData

class sContactRepository(private val sContactDao: ScDao) {
    val allContacts: LiveData<List<SecondaryContact>> = sContactDao.getAllContacts()

    fun getContactsByUserId(userId: Int): LiveData<List<SecondaryContact>> {
        return sContactDao.getContactsByUserId(userId)
    }

    suspend fun getSecondaryContact(userId: Int): SecondaryContact? {
        return sContactDao.getSecondaryContact(userId)
    }

    suspend fun contactExists(userId: Int): Boolean {
        return sContactDao.contactExists(userId)
    }

    suspend fun insert(scontact: SecondaryContact): Long {
        return sContactDao.insert(scontact)
    }

    suspend fun update(scontact: SecondaryContact) {
        sContactDao.update(scontact)
    }

    suspend fun delete(scontact: SecondaryContact) {
        sContactDao.delete(scontact)
    }

    suspend fun insertOrUpdateContact(scontact: SecondaryContact): Long {
        val existingContact = sContactDao.getSecondaryContact(scontact.userID.toInt())
        return if (existingContact != null) {
            // Update existing contact but keep the same ID
            val updatedContact = scontact.copy(scID = existingContact.scID)
            sContactDao.update(updatedContact)
            updatedContact.scID.toLong()
        } else {
            // Insert new contact
            sContactDao.insert(scontact)
        }
    }
}