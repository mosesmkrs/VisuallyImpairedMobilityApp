package com.example.newapp.SQL.PC

import androidx.lifecycle.LiveData

class pContactRepository(private val pContactDao: PcDao) {
    val allContacts: LiveData<List<primaryContact>> = pContactDao.getAllContacts()

    suspend fun insert(pcontact: primaryContact){
        pContactDao.insert(pcontact)
    }
    suspend fun update(pcontact: primaryContact){
        pContactDao.update(pcontact)
    }
    suspend fun delete(pcontact: primaryContact){
        pContactDao.delete(pcontact)
    }

}