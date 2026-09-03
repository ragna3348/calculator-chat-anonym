package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.crypto.CryptoEngine
import com.example.data.model.CalculationHistoryEntity
import com.example.data.model.ChatEntity
import com.example.data.model.LinkedDeviceEntity
import com.example.data.model.MediaType
import com.example.data.model.MessageEntity
import com.example.data.model.MessageStatus
import com.example.data.model.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        LinkedDeviceEntity::class,
        ReminderEntity::class,
        CalculationHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calculator_vault.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Pre-populate with initial contacts & introductory encrypted messages
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val dao = getInstance(context).appDao()
                                    seedInitialData(dao)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedInitialData(dao: AppDao) {
            val now = System.currentTimeMillis()

            // Seed Linked Device for current smartphone
            val currentDevice = LinkedDeviceEntity(
                deviceId = "dev_current_01",
                deviceName = "Perangkat Ini (Android Device)",
                deviceType = "Smartphone",
                lastActive = now,
                isCurrentDevice = true,
                ipLocation = "127.0.0.1 (Lokal Aman)",
                syncStatus = "Online & Terenkripsi"
            )
            dao.insertDevices(listOf(currentDevice))

            // Seed default security reminder
            dao.insertOrUpdateReminder(
                ReminderEntity(
                    title = "Audit Keamanan Harian",
                    description = "Periksa cadangan terenkripsi dan sinkronisasi multi-perangkat.",
                    timeHour = 20,
                    timeMinute = 0,
                    isEnabled = true,
                    isStealthNotification = true
                )
            )
        }
    }
}
