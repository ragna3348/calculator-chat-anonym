package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val contactName: String,
    val contactAvatarColor: Long, // Hex color for avatar
    val contactRole: String = "Kontak Terenkripsi",
    val mathUsername: String = "", // e.g. "1+1", "10-3", "3×3", "12÷4"
    val isOnline: Boolean = true,
    val isSupport: Boolean = false,
    val isChannel: Boolean = false,
    val lastMessage: String = "",
    val lastTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val disappearingTimerSeconds: Int = 0, // 0 = disabled, 5, 10, 30, 60, etc.
    val securityFingerprint: String = "",
    val isMuted: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

enum class MediaType {
    TEXT,
    IMAGE,
    AUDIO,
    FILE,
    SYSTEM
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String,
    val senderId: String, // "me" or contactId
    val senderName: String,
    val ciphertext: String,
    val iv: String,
    val salt: String,
    val checksum: String = "",
    val mediaType: MediaType = MediaType.TEXT,
    val mediaUriOrData: String? = null, // Base64 or local cached URI
    val mediaFileName: String? = null,
    val mediaFileSizeFormatted: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val readTimestamp: Long? = null,
    val burnTimestamp: Long? = null, // When it will self-destruct
    val isSelfDestructing: Boolean = false,
    val status: MessageStatus = MessageStatus.SENT,
    val isEncrypted: Boolean = true
)

@Entity(tableName = "linked_devices")
data class LinkedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val deviceType: String, // "Smartphone", "Laptop", "Tablet"
    val lastActive: Long = System.currentTimeMillis(),
    val isCurrentDevice: Boolean = false,
    val ipLocation: String = "192.168.1.10 (Lokal Terproteksi)",
    val syncStatus: String = "Tersinkronisasi"
)

@Entity(tableName = "daily_reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val timeHour: Int,
    val timeMinute: Int,
    val isEnabled: Boolean = true,
    val isStealthNotification: Boolean = true
)

@Entity(tableName = "calc_history")
data class CalculationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)
