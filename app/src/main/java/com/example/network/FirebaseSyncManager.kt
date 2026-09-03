package com.example.network

import android.content.Context
import android.util.Log
import com.example.crypto.CryptoEngine
import com.example.data.db.AppDao
import com.example.data.model.ChatEntity
import com.example.data.model.MediaType
import com.example.data.model.MessageEntity
import com.example.data.model.MessageStatus
import com.example.utils.NotificationHelper
import com.example.utils.SettingsManager
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class FirebaseStatus {
    NOT_INITIALIZED,
    CONNECTING,
    CONNECTED,
    OFFLINE_STANDBY,
    ERROR
}

data class CloudSyncState(
    val status: FirebaseStatus = FirebaseStatus.NOT_INITIALIZED,
    val statusMessage: String = "Mode Standby Offline (Menunggu Konfigurasi Firebase)",
    val isOnline: Boolean = false,
    val connectedUsersCount: Int = 1,
    val lastSyncTime: Long = 0L
)

class FirebaseSyncManager(
    private val context: Context,
    private val dao: AppDao,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "FirebaseSync"
        private const val COLLECTION_MESSAGES = "vault_encrypted_messages"
        private const val COLLECTION_USERS = "vault_math_users"
    }

    private val _syncState = MutableStateFlow(CloudSyncState())
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private var messageListener: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        initializeFirebase()
    }

    fun initializeFirebase() {
        scope.launch {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    _syncState.value = CloudSyncState(
                        status = FirebaseStatus.NOT_INITIALIZED,
                        statusMessage = "Firebase belum dikonfigurasi (google-services.json belum ada). Berjalan dalam mode Offline-First.",
                        isOnline = false
                    )
                    return@launch
                }

                _syncState.value = _syncState.value.copy(
                    status = FirebaseStatus.CONNECTING,
                    statusMessage = "Menghubungkan ke Firebase Cloud Firestore..."
                )

                auth = FirebaseAuth.getInstance()
                firestore = FirebaseFirestore.getInstance()

                // Anonymous sign-in for zero-knowledge auth
                try {
                    if (auth?.currentUser == null) {
                        auth?.signInAnonymously()?.await()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Anonymous auth note: ${e.message}")
                }

                _syncState.value = CloudSyncState(
                    status = FirebaseStatus.CONNECTED,
                    statusMessage = "Terhubung ke Firebase Cloud. Siap bertukar pesan lintas internet.",
                    isOnline = true,
                    lastSyncTime = System.currentTimeMillis()
                )

                // Register user math formula on cloud directory
                val myMathUsername = settingsManager.settings.value.mathUsername
                if (myMathUsername.isNotBlank()) {
                    registerMathUserOnCloud(myMathUsername)
                    startListeningForIncomingMessages(myMathUsername)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Firebase initialization error", e)
                _syncState.value = CloudSyncState(
                    status = FirebaseStatus.OFFLINE_STANDBY,
                    statusMessage = "Mode Lokal Offline aktif (${e.localizedMessage ?: "Standby"}).",
                    isOnline = false
                )
            }
        }
    }

    fun registerMathUserOnCloud(mathUsername: String) {
        val db = firestore ?: return
        scope.launch {
            try {
                val normalized = com.example.utils.MathUsernameGenerator.normalizeFormula(mathUsername)
                val userData = hashMapOf(
                    "mathUsername" to normalized,
                    "displayName" to settingsManager.settings.value.userDisplayName,
                    "lastSeen" to System.currentTimeMillis(),
                    "isOnline" to true
                )
                db.collection(COLLECTION_USERS).document(normalized).set(userData).await()
                Log.d(TAG, "Registered math user online: $normalized")
            } catch (e: Exception) {
                Log.w(TAG, "Could not register user to cloud: ${e.message}")
            }
        }
    }

    fun startListeningForIncomingMessages(myMathUsername: String) {
        val db = firestore ?: return
        val normalizedMyId = com.example.utils.MathUsernameGenerator.normalizeFormula(myMathUsername)
        if (normalizedMyId.isBlank()) return

        messageListener?.remove()

        try {
            messageListener = db.collection(COLLECTION_MESSAGES)
                .whereEqualTo("receiverMathUsername", normalizedMyId)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Listen failed", error)
                        return@addSnapshotListener
                    }

                    if (snapshots != null && !snapshots.isEmpty) {
                        val pendingDocs = snapshots.documents
                            .filter { it.getBoolean("delivered") != true }
                            .sortedBy { it.getLong("timestamp") ?: 0L }

                        if (pendingDocs.isEmpty()) return@addSnapshotListener

                        scope.launch {
                            for (doc in pendingDocs) {
                                try {
                                    val rawSender = doc.getString("senderMathUsername") ?: continue
                                    val senderMathUsername = com.example.utils.MathUsernameGenerator.normalizeFormula(rawSender)
                                    val ciphertext = doc.getString("ciphertext") ?: continue
                                    val iv = doc.getString("iv") ?: ""
                                    val salt = doc.getString("salt") ?: ""
                                    val checksum = doc.getString("checksum") ?: ""
                                    val mediaTypeStr = doc.getString("mediaType") ?: MediaType.TEXT.name
                                    val mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (_: Exception) { MediaType.TEXT }
                                    val mediaFileName = doc.getString("mediaFileName")
                                    val mediaFileSize = doc.getString("mediaFileSizeFormatted")
                                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                    val senderDisplayName = doc.getString("senderDisplayName") ?: "Kontak #$senderMathUsername"

                                    // Find or create local chat for this math contact
                                    val standardChatId = "user_" + senderMathUsername.replace("+", "p").replace("-", "m").replace("×", "x").replace("÷", "d")
                                    var chat = dao.getChatByMathUsernameDirect(senderMathUsername) ?: dao.getChatByIdDirect(standardChatId)
                                    val targetChatId = if (chat == null) {
                                        val newChat = ChatEntity(
                                            id = standardChatId,
                                            contactName = senderDisplayName,
                                            contactAvatarColor = 0xFF6366F1,
                                            contactRole = "Kontak Terenkripsi",
                                            mathUsername = senderMathUsername,
                                            isOnline = true,
                                            isSupport = false,
                                            isChannel = false,
                                            lastMessage = "Pesan baru terenkripsi masuk",
                                            lastTimestamp = timestamp,
                                            unreadCount = 1,
                                            disappearingTimerSeconds = 0,
                                            securityFingerprint = CryptoEngine.generateFingerprint(standardChatId)
                                        )
                                        dao.insertOrUpdateChat(newChat)
                                        chat = newChat
                                        standardChatId
                                    } else {
                                        chat.id
                                    }

                                    // Decrypt preview or show encrypted note
                                    val decryptedText = try {
                                        CryptoEngine.decrypt(ciphertext, iv, salt)
                                    } catch (e: Exception) {
                                        "Pesan terenkripsi diterima"
                                    }

                                    val incomingMsg = MessageEntity(
                                        chatId = targetChatId,
                                        senderId = senderMathUsername,
                                        senderName = senderDisplayName,
                                        ciphertext = ciphertext,
                                        iv = iv,
                                        salt = salt,
                                        checksum = checksum,
                                        mediaType = mediaType,
                                        mediaFileName = mediaFileName,
                                        mediaFileSizeFormatted = mediaFileSize,
                                        timestamp = timestamp,
                                        isRead = false,
                                        status = MessageStatus.DELIVERED,
                                        isEncrypted = true
                                    )

                                    val currentUnread = chat?.unreadCount ?: 0
                                    dao.insertMessage(incomingMsg)
                                    dao.updateChatPreview(targetChatId, decryptedText, timestamp, currentUnread + 1)

                                    // Mark as delivered on cloud so it's not fetched multiple times
                                    doc.reference.update("delivered", true)

                                    // Trigger discreet notification
                                    val settings = settingsManager.settings.value
                                    NotificationHelper.showNotification(
                                        context = context,
                                        notificationId = targetChatId.hashCode(),
                                        isDisguised = settings.isStealthNotificationEnabled,
                                        realTitle = senderDisplayName,
                                        realBody = decryptedText,
                                        disguiseTitle = settings.disguiseNotificationTitle,
                                        disguiseBody = settings.disguiseNotificationBody
                                    )

                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing incoming message doc", e)
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up snapshot listener: ${e.message}")
        }
    }

    suspend fun sendEncryptedMessageToCloud(
        senderMathUsername: String,
        receiverMathUsername: String,
        senderDisplayName: String,
        ciphertext: String,
        iv: String,
        salt: String,
        checksum: String,
        mediaType: MediaType,
        mediaFileName: String? = null,
        mediaFileSizeFormatted: String? = null
    ): Boolean {
        val db = firestore ?: return false
        return try {
            val normalizedSender = com.example.utils.MathUsernameGenerator.normalizeFormula(senderMathUsername)
            val normalizedReceiver = com.example.utils.MathUsernameGenerator.normalizeFormula(receiverMathUsername)

            val payload = hashMapOf(
                "senderMathUsername" to normalizedSender,
                "receiverMathUsername" to normalizedReceiver,
                "senderDisplayName" to senderDisplayName,
                "ciphertext" to ciphertext,
                "iv" to iv,
                "salt" to salt,
                "checksum" to checksum,
                "mediaType" to mediaType.name,
                "mediaFileName" to (mediaFileName ?: ""),
                "mediaFileSizeFormatted" to (mediaFileSizeFormatted ?: ""),
                "timestamp" to System.currentTimeMillis(),
                "delivered" to false
            )

            db.collection(COLLECTION_MESSAGES).add(payload).await()
            _syncState.value = _syncState.value.copy(
                lastSyncTime = System.currentTimeMillis()
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message to cloud", e)
            false
        }
    }

    fun stopListening() {
        messageListener?.remove()
        messageListener = null
    }
}
