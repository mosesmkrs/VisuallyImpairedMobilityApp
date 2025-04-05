package com.example.newapp.SQL

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.newapp.SQL.PC.PcDao
import com.example.newapp.SQL.PC.PrimaryContact
import com.example.newapp.SQL.users.UserDao
import com.example.newapp.SQL.users.Users

@Database(entities = [Users::class, PrimaryContact::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun pContactDao(): PcDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "visualimpaired"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}

//@Database(entities = [User::class], version = 1, exportSchema = false)
//abstract class AppDatabase : RoomDatabase() {
//    abstract fun userDao(): UserDao()
//
//    companion object {
//        @Volatile
//        private var INSTANCE: AppDatabase? = null
//
//        fun getDatabase(context: Context): AppDatabase {
//            return INSTANCE ?: synchronized(this) {
//                val instance = Room.databaseBuilder(
//                    context.applicationContext,
//                    AppDatabase::class.java,
//                    "user_database"
//                ).build()
//                INSTANCE = instance
//                instance
//            }
//        }
//    }
//}