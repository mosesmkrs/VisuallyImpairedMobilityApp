package com.example.newapp.SQL.PC

import androidx.lifecycle.LiveData

class pContactRepository(private val pContactDao: PcDao) {
    val allContacts: LiveData<List<PrimaryContact>> = pContactDao.getAllContacts()

    suspend fun insert(pcontact: PrimaryContact){
        pContactDao.insert(pcontact)
    }
    suspend fun update(pcontact: PrimaryContact){
        pContactDao.update(pcontact)
    }
    suspend fun delete(pcontact: PrimaryContact){
        pContactDao.delete(pcontact)
    }

}