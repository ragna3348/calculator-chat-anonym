package com.example.data.repository

import android.content.Context
import com.example.crypto.CryptoEngine
import com.example.data.db.AppDao
import com.example.data.db.AppDatabase
import com.example.data.model.CalculationHistoryEntity
import com.example.data.model.ChatEntity
import com.example.data.model.LinkedDeviceEntity
import com.example.data.model.MediaType
import com.example.data.model.MessageEntity
import com.example.data.model.MessageStatus
import com.example.data.model.ReminderEntity
import com.example.network.FirebaseSyncManager
import com.example.network.GeminiClient
import com.example.utils.NotificationHelper
import com.example.utils.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val dao: AppDao,
    private val settingsManager: SettingsManager
) {
    val firebaseSyncManager = FirebaseSyncManager(context, dao, settingsManager)
    val cloudSyncState = firebaseSyncManager.syncState

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existing = dao.getAllExistingMathUsernames()
                if (existing.isEmpty()) {
                    AppDatabase.seedInitialData(dao)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Automatically start real-time listener for incoming messages on startup & ID change
        CoroutineScope(Dispatchers.IO).launch {
            settingsManager.settings.collect { s ->
                val mathId = s.mathUsername
                if (mathId.isNotBlank()) {
                    firebaseSyncManager.registerMathUserOnCloud(mathId)
                    firebaseSyncManager.startListeningForIncomingMessages(mathId)
                }
            }
        }
    }

    val allChats: Flow<List<ChatEntity>> = dao.getAllChats()
    val allDevices: Flow<List<LinkedDeviceEntity>> = dao.getAllDevices()
    val allReminders: Flow<List<ReminderEntity>> = dao.getAllReminders()
    val calcHistory: Flow<List<CalculationHistoryEntity>> = dao.getCalculationHistory()

    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>> =
        dao.getMessagesForChat(chatId)

    fun getChatById(chatId: String): Flow<ChatEntity?> =
        dao.getChatById(chatId)

    suspend fun saveCalculation(expression: String, result: String) {
        dao.insertCalculationHistory(
            CalculationHistoryEntity(
                expression = expression,
                result = result,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearCalculationHistory() {
        dao.clearCalculationHistory()
    }

    suspend fun markChatAsRead(chatId: String) {
        dao.markChatAsRead(chatId)
        val chat = dao.getChatByIdDirect(chatId)
        val timer = chat?.disappearingTimerSeconds ?: 0

        // If self-destruct is enabled on this chat, schedule burn timestamp for incoming unread messages
        if (timer > 0) {
            val messages = dao.getMessagesForChatDirect(chatId)
            val now = System.currentTimeMillis()
            messages.forEach { msg ->
                if (!msg.isRead && msg.burnTimestamp == null) {
                    val updated = msg.copy(
                        isRead = true,
                        readTimestamp = now,
                        burnTimestamp = now + (timer * 1000L),
                        isSelfDestructing = true,
                        status = MessageStatus.READ
                    )
                    dao.updateMessage(updated)
                }
            }
        }
    }

    suspend fun updateDisappearingTimer(chatId: String, seconds: Int) {
        dao.updateDisappearingTimer(chatId, seconds)
        // Insert a system notice
        val notice = if (seconds == 0) {
            "Timer pesan musnah otomatis dinonaktifkan."
        } else {
            "Pesan musnah otomatis diatur ke $seconds detik setelah dibaca."
        }
        val encNotice = CryptoEngine.encrypt(notice)
        dao.insertMessage(
            MessageEntity(
                chatId = chatId,
                senderId = "system",
                senderName = "Sistem Vault",
                ciphertext = encNotice.ciphertext,
                iv = encNotice.iv,
                salt = encNotice.salt,
                checksum = encNotice.checksum,
                mediaType = MediaType.SYSTEM,
                timestamp = System.currentTimeMillis(),
                isRead = true,
                status = MessageStatus.READ
            )
        )

        // Sync timer update to cloud peer
        val chat = dao.getChatByIdDirect(chatId)
        val receiverMath = chat?.mathUsername
        val myMath = settingsManager.settings.value.mathUsername
        if (!receiverMath.isNullOrBlank() && myMath.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                firebaseSyncManager.sendTimerUpdateToCloud(myMath, receiverMath, seconds)
            }
        }
    }

    suspend fun sendMessage(
        chatId: String,
        plainText: String,
        mediaType: MediaType = MediaType.TEXT,
        mediaUriOrData: String? = null,
        mediaFileName: String? = null,
        mediaFileSizeFormatted: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val enc = CryptoEngine.encrypt(plainText)

        val chat = dao.getChatByIdDirect(chatId)
        val disappearingSeconds = chat?.disappearingTimerSeconds ?: 0

        val message = MessageEntity(
            chatId = chatId,
            senderId = "me",
            senderName = settingsManager.settings.value.userDisplayName,
            ciphertext = enc.ciphertext,
            iv = enc.iv,
            salt = enc.salt,
            checksum = enc.checksum,
            mediaType = mediaType,
            mediaUriOrData = mediaUriOrData,
            mediaFileName = mediaFileName,
            mediaFileSizeFormatted = mediaFileSizeFormatted,
            timestamp = now,
            isRead = false,
            burnTimestamp = if (disappearingSeconds > 0) now + (disappearingSeconds * 1000L) else null,
            isSelfDestructing = disappearingSeconds > 0,
            status = MessageStatus.SENT,
            isEncrypted = true
        )

        val messageId = dao.insertMessage(message)
        val preview = if (mediaType == MediaType.TEXT) plainText else "[${mediaType.name}] $plainText"
        dao.updateChatPreview(chatId, preview, now, 0)

        // Send to Firebase Cloud Firestore for real-time internet delivery with burnSeconds
        val receiverMath = chat?.mathUsername
        val myMath = settingsManager.settings.value.mathUsername
        if (!receiverMath.isNullOrBlank() && myMath.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                firebaseSyncManager.sendEncryptedMessageToCloud(
                    senderMathUsername = myMath,
                    receiverMathUsername = receiverMath,
                    senderDisplayName = settingsManager.settings.value.userDisplayName,
                    ciphertext = enc.ciphertext,
                    iv = enc.iv,
                    salt = enc.salt,
                    checksum = enc.checksum,
                    mediaType = mediaType,
                    mediaFileName = mediaFileName,
                    mediaFileSizeFormatted = mediaFileSizeFormatted,
                    burnSeconds = disappearingSeconds
                )
            }
        }

        // Trigger peer or AI response in background
        if (chat?.isSupport == true) {
            handleAiSupportResponse(chatId, plainText)
        } else if (chat?.isChannel == false && (chatId == "alice_e2ee" || chatId == "bob_crypto")) {
            handlePeerResponse(chatId, chat.contactName, plainText)
        }

        return messageId
    }

    private fun handleAiSupportResponse(chatId: String, userPrompt: String) {
        CoroutineScope(Dispatchers.IO).launch {
            delay(1200) // Realistic processing delay
            val directMsgs = dao.getMessagesForChatDirect(chatId)
            val history = directMsgs.map {
                val decrypted = CryptoEngine.decrypt(it.ciphertext, it.iv, it.salt)
                Pair(it.senderId, decrypted)
            }

            val replyText = GeminiClient.askCustomerSupport(userPrompt, history)
            val encReply = CryptoEngine.encrypt(replyText)
            val now = System.currentTimeMillis()

            val replyMessage = MessageEntity(
                chatId = chatId,
                senderId = chatId,
                senderName = "Live Support Agent (AI)",
                ciphertext = encReply.ciphertext,
                iv = encReply.iv,
                salt = encReply.salt,
                checksum = encReply.checksum,
                mediaType = MediaType.TEXT,
                timestamp = now,
                isRead = false,
                status = MessageStatus.DELIVERED,
                isEncrypted = true
            )
            dao.insertMessage(replyMessage)
            dao.updateChatPreview(chatId, replyText, now, 1)

            // Trigger notification
            val settings = settingsManager.settings.value
            NotificationHelper.showNotification(
                context = context,
                notificationId = chatId.hashCode(),
                isDisguised = settings.isStealthNotificationEnabled,
                realTitle = "Live Support Agent",
                realBody = replyText,
                disguiseTitle = settings.disguiseNotificationTitle,
                disguiseBody = settings.disguiseNotificationBody
            )
        }
    }

    private fun handlePeerResponse(chatId: String, contactName: String, userText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            delay(1800) // Simulate peer reading & typing

            val responses = when {
                chatId == "alice_e2ee" -> listOf(
                    "Pesan terenkripsi Anda telah diterima dengan selamat. Verifikasi handshake AES-GCM cocok! 🔐",
                    "Saya telah memverifikasi checksum paket. Semua transmisi data aman dan bebas tamper.",
                    "Sip! Jangan lupa aktifkan timer pesan musnah jika membagikan kredensial sensitif.",
                    "File dan kunci enkripsi kita tersinkronisasi sempurna di semua gadget."
                )
                chatId == "bob_sec" -> listOf(
                    "Menerima kiriman. Jalur koneksi P2P stabil tanpa ada kebocoran paket.",
                    "Sistem keamanan berjalan optimal. Cadangan cloud privat siap kapan saja.",
                    "Konfirmasi diterima. Saya akan update log keamanan terenkripsi."
                )
                else -> listOf("Pesan terenkripsi diterima dengan baik.")
            }

            val reply = responses.random()
            val encReply = CryptoEngine.encrypt(reply)
            val now = System.currentTimeMillis()

            val chat = dao.getChatByIdDirect(chatId)
            val disappearingSeconds = chat?.disappearingTimerSeconds ?: 0

            val replyMessage = MessageEntity(
                chatId = chatId,
                senderId = chatId,
                senderName = contactName,
                ciphertext = encReply.ciphertext,
                iv = encReply.iv,
                salt = encReply.salt,
                checksum = encReply.checksum,
                mediaType = MediaType.TEXT,
                timestamp = now,
                isRead = false,
                burnTimestamp = if (disappearingSeconds > 0) now + (disappearingSeconds * 1000L) else null,
                isSelfDestructing = disappearingSeconds > 0,
                status = MessageStatus.DELIVERED,
                isEncrypted = true
            )

            dao.insertMessage(replyMessage)
            dao.updateChatPreview(chatId, reply, now, 1)

            // Notification with camouflage
            val settings = settingsManager.settings.value
            NotificationHelper.showNotification(
                context = context,
                notificationId = chatId.hashCode(),
                isDisguised = settings.isStealthNotificationEnabled,
                realTitle = contactName,
                realBody = reply,
                disguiseTitle = settings.disguiseNotificationTitle,
                disguiseBody = settings.disguiseNotificationBody
            )
        }
    }

    suspend fun purgeExpiredMessages(): Int {
        val now = System.currentTimeMillis()
        val expired = dao.getExpiredSelfDestructMessages(now)
        if (expired.isNotEmpty()) {
            val myMath = settingsManager.settings.value.mathUsername
            for (msg in expired) {
                val chat = dao.getChatByIdDirect(msg.chatId)
                val receiverMath = chat?.mathUsername
                if (!receiverMath.isNullOrBlank() && myMath.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        firebaseSyncManager.sendDeleteMessageToCloud(myMath, receiverMath, msg.checksum, msg.timestamp)
                    }
                }
            }
        }
        return dao.purgeExpiredSelfDestructMessages(now)
    }

    suspend fun deleteMessage(message: MessageEntity, syncBothSides: Boolean = true) {
        dao.deleteMessageById(message.id)
        if (syncBothSides) {
            val chat = dao.getChatByIdDirect(message.chatId)
            val receiverMath = chat?.mathUsername
            val myMath = settingsManager.settings.value.mathUsername
            if (!receiverMath.isNullOrBlank() && myMath.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    firebaseSyncManager.sendDeleteMessageToCloud(
                        senderMathUsername = myMath,
                        receiverMathUsername = receiverMath,
                        checksum = message.checksum,
                        timestamp = message.timestamp
                    )
                }
            }
        }
    }

    suspend fun clearChatMessages(chatId: String, syncBothSides: Boolean = true) {
        val chat = dao.getChatByIdDirect(chatId)
        dao.clearMessagesForChat(chatId)
        dao.updateChatPreview(chatId, "", System.currentTimeMillis(), 0)

        if (syncBothSides && chat != null) {
            val receiverMath = chat.mathUsername
            val myMath = settingsManager.settings.value.mathUsername
            if (receiverMath.isNotBlank() && myMath.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    firebaseSyncManager.sendClearChatToCloud(myMath, receiverMath)
                }
            }
        }
    }

    suspend fun deleteChatRoom(chatId: String, syncBothSides: Boolean = true) {
        val chat = dao.getChatByIdDirect(chatId)
        dao.clearMessagesForChat(chatId)
        dao.deleteChatById(chatId)

        if (syncBothSides && chat != null) {
            val receiverMath = chat.mathUsername
            val myMath = settingsManager.settings.value.mathUsername
            if (receiverMath.isNotBlank() && myMath.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    firebaseSyncManager.sendClearChatToCloud(myMath, receiverMath)
                }
            }
        }
    }

    suspend fun performCloudBackup(): String = withContext(Dispatchers.IO) {
        val allMessages = dao.getAllMessagesDirect()
        val allChats = dao.getAllChats().firstOrNull() ?: emptyList()
        val now = System.currentTimeMillis()

        // Create raw payload
        val rawData = buildString {
            append("VAULT_BACKUP_V1\n")
            append("TIMESTAMP:$now\n")
            append("CHATS_COUNT:${allChats.size}\n")
            append("MSGS_COUNT:${allMessages.size}\n")
            allMessages.forEach { m ->
                append("MSG|${m.chatId}|${m.senderId}|${m.ciphertext}|${m.iv}|${m.salt}|${m.timestamp}\n")
            }
        }

        val encryptedBackup = CryptoEngine.encrypt(rawData)
        settingsManager.updateSettings {
            it.copy(lastCloudBackupTimestamp = now)
        }

        // Update current device sync
        dao.updateDeviceSync("dev_current_01", now, "Cadangan Cloud Baru Selesai")
        encryptedBackup.ciphertext
    }

    suspend fun syncAllDevices(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        delay(900) // Simulated sync round-trip
        dao.updateDeviceSync("dev_current_01", now, "Sinkronisasi Aktif")
        dao.updateDeviceSync("dev_laptop_02", now, "Tersinkronisasi")
        dao.updateDeviceSync("dev_tablet_03", now, "Tersinkronisasi")
        true
    }

    suspend fun addReminder(title: String, description: String, hour: Int, minute: Int) {
        dao.insertOrUpdateReminder(
            ReminderEntity(
                title = title,
                description = description,
                timeHour = hour,
                timeMinute = minute,
                isEnabled = true,
                isStealthNotification = true
            )
        )
    }

    suspend fun getExistingMathUsernames(): Set<String> = withContext(Dispatchers.IO) {
        val list = dao.getAllExistingMathUsernames().toMutableSet()
        // Include user's own math username
        list.add(settingsManager.settings.value.mathUsername)
        list
    }

    suspend fun findChatByMathUsername(formula: String): ChatEntity? = withContext(Dispatchers.IO) {
        val normalized = com.example.utils.MathUsernameGenerator.normalizeFormula(formula)
        dao.getChatByMathUsernameDirect(normalized)
    }

    suspend fun createOrGetChatWithMathUser(
        mathUsername: String,
        customContactName: String? = null
    ): ChatEntity = withContext(Dispatchers.IO) {
        val normalized = com.example.utils.MathUsernameGenerator.normalizeFormula(mathUsername)
        val existing = dao.getChatByMathUsernameDirect(normalized)
        if (existing != null) {
            return@withContext existing
        }

        val name = customContactName?.ifBlank { null } ?: "Kontak ($normalized)"
        val newChatId = "user_" + normalized.replace("+", "p").replace("-", "m").replace("×", "x").replace("÷", "d")
        val avatarColors = listOf(0xFFEC4899, 0xFF3B82F6, 0xFF10B981, 0xFF8B5CF6, 0xFFF59E0B, 0xFF06B6D4)
        val color = avatarColors[Math.abs(normalized.hashCode()) % avatarColors.size]

        val newChat = ChatEntity(
            id = newChatId,
            contactName = name,
            contactAvatarColor = color,
            contactRole = "Kontak Terenkripsi",
            mathUsername = normalized,
            isOnline = true,
            isSupport = false,
            isChannel = false,
            lastMessage = "Kunci sesi AES-256 dibuat. Ketuk untuk mulai percakapan terenkripsi.",
            lastTimestamp = System.currentTimeMillis(),
            unreadCount = 0,
            disappearingTimerSeconds = 0,
            securityFingerprint = CryptoEngine.generateFingerprint(newChatId)
        )

        dao.insertOrUpdateChat(newChat)

        // Seed welcome greeting
        val welcomeEnc = CryptoEngine.encrypt("Sesi obrolan aman terhubung via ID Matematika [$normalized]. Semua pesan dienkripsi secara end-to-end.")
        dao.insertMessage(
            MessageEntity(
                chatId = newChatId,
                senderId = newChatId,
                senderName = name,
                ciphertext = welcomeEnc.ciphertext,
                iv = welcomeEnc.iv,
                salt = welcomeEnc.salt,
                checksum = welcomeEnc.checksum,
                mediaType = MediaType.TEXT,
                timestamp = System.currentTimeMillis(),
                isRead = true,
                status = MessageStatus.READ
            )
        )

        newChat
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        dao.deleteReminder(reminder)
    }

    suspend fun clearAllChatsAndMessages() = withContext(Dispatchers.IO) {
        dao.deleteAllMessages()
        dao.deleteAllChats()
    }
}
