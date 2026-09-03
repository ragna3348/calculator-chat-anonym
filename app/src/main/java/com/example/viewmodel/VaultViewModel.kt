package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoEngine
import com.example.data.model.ChatEntity
import com.example.data.model.LinkedDeviceEntity
import com.example.data.model.MediaType
import com.example.data.model.MessageEntity
import com.example.data.model.ReminderEntity
import com.example.data.repository.AppRepository
import com.example.utils.AppThemeMode
import com.example.utils.ExportHelper
import com.example.utils.NotificationHelper
import com.example.utils.SettingsManager
import com.example.utils.UserSecuritySettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class VaultScreen {
    CHATS,
    CONVERSATION,
    CLOUD_SYNC,
    STEALTH_SETTINGS,
    SECURITY_AUDIT
}

sealed class VaultToastEvent {
    data class Success(val message: String) : VaultToastEvent()
    data class Error(val message: String) : VaultToastEvent()
    data class Info(val message: String) : VaultToastEvent()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VaultViewModel(
    private val context: Context,
    private val repository: AppRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _currentScreen = MutableStateFlow(VaultScreen.CHATS)
    val currentScreen: StateFlow<VaultScreen> = _currentScreen.asStateFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilterTab = MutableStateFlow(0) // 0: All, 1: Direct, 2: Channels, 3: Support
    val selectedFilterTab: StateFlow<Int> = _selectedFilterTab.asStateFlow()

    private val _isTransferringFile = MutableStateFlow(false)
    val isTransferringFile: StateFlow<Boolean> = _isTransferringFile.asStateFlow()

    private val _transferProgress = MutableStateFlow(0f)
    val transferProgress: StateFlow<Float> = _transferProgress.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _toastEvents = MutableSharedFlow<VaultToastEvent>()
    val toastEvents: SharedFlow<VaultToastEvent> = _toastEvents.asSharedFlow()

    val settings: StateFlow<UserSecuritySettings> = settingsManager.settings
    val cloudSyncState = repository.cloudSyncState

    val allChats: StateFlow<List<ChatEntity>> = repository.allChats
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allDevices: StateFlow<List<LinkedDeviceEntity>> = repository.allDevices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allReminders: StateFlow<List<ReminderEntity>> = repository.allReminders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeChatMessages: StateFlow<List<MessageEntity>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId == null) flowOf(emptyList()) else repository.getMessagesForChat(chatId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeChatEntity: StateFlow<ChatEntity?> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId == null) flowOf(null) else repository.getChatById(chatId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private var burnTickerJob: Job? = null

    private val _showMathSetupDialog = MutableStateFlow(false)
    val showMathSetupDialog: StateFlow<Boolean> = _showMathSetupDialog.asStateFlow()

    private val _existingMathUsernames = MutableStateFlow<Set<String>>(emptySet())
    val existingMathUsernames: StateFlow<Set<String>> = _existingMathUsernames.asStateFlow()

    init {
        startBurnTicker()
        loadExistingMathUsernames()
    }

    fun loadExistingMathUsernames() {
        viewModelScope.launch {
            val set = repository.getExistingMathUsernames()
            _existingMathUsernames.value = set
        }
    }

    fun openMathSetupDialog() {
        loadExistingMathUsernames()
        _showMathSetupDialog.value = true
    }

    fun dismissMathSetupDialog() {
        _showMathSetupDialog.value = false
    }

    fun setMathUsername(mathUsername: String, displayName: String) {
        val normalized = com.example.utils.MathUsernameGenerator.normalizeFormula(mathUsername)
        settingsManager.updateSettings {
            it.copy(
                mathUsername = normalized,
                userDisplayName = displayName,
                hasSetupMathUsername = true
            )
        }
        _showMathSetupDialog.value = false
        loadExistingMathUsernames()
        repository.firebaseSyncManager.registerMathUserOnCloud(normalized)
        repository.firebaseSyncManager.startListeningForIncomingMessages(normalized)
        viewModelScope.launch {
            _toastEvents.emit(VaultToastEvent.Success("ID Username Matematika berhasil disimpan: [$normalized]"))
        }
    }

    fun addMathContact(mathUsername: String, contactName: String) {
        val normalized = com.example.utils.MathUsernameGenerator.normalizeFormula(mathUsername)
        viewModelScope.launch {
            val newChat = repository.createOrGetChatWithMathUser(normalized, contactName)
            loadExistingMathUsernames()
            selectChat(newChat.id)
            _toastEvents.emit(VaultToastEvent.Success("Kontak [$normalized] (${newChat.contactName}) ditambahkan."))
        }
    }

    private fun startBurnTicker() {
        burnTickerJob?.cancel()
        burnTickerJob = viewModelScope.launch {
            while (isActive) {
                repository.purgeExpiredMessages()
                delay(1000)
            }
        }
    }

    fun navigateTo(screen: VaultScreen) {
        _currentScreen.value = screen
    }

    fun selectChat(chatId: String) {
        _selectedChatId.value = chatId
        _currentScreen.value = VaultScreen.CONVERSATION
        viewModelScope.launch {
            repository.markChatAsRead(chatId)
        }
    }

    fun closeConversation() {
        _selectedChatId.value = null
        _currentScreen.value = VaultScreen.CHATS
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterTab(index: Int) {
        _selectedFilterTab.value = index
    }

    fun sendMessage(text: String) {
        val chatId = _selectedChatId.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            repository.sendMessage(
                chatId = chatId,
                plainText = text.trim(),
                mediaType = MediaType.TEXT
            )
        }
    }

    fun sendVoiceMemo(durationSeconds: Int = 5) {
        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            val audioDesc = "Pesan Suara Terenkripsi ($durationSeconds dtk)"
            repository.sendMessage(
                chatId = chatId,
                plainText = audioDesc,
                mediaType = MediaType.AUDIO,
                mediaFileName = "voice_memo_${System.currentTimeMillis()}.enc",
                mediaFileSizeFormatted = "${durationSeconds * 24} KB"
            )
            _toastEvents.emit(VaultToastEvent.Success("Pesan suara terenkripsi dikirim."))
        }
    }

    fun sendSampleImage(imageTitle: String = "Foto Terenkripsi") {
        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            _isTransferringFile.value = true
            _transferProgress.value = 0.2f
            delay(300)
            _transferProgress.value = 0.7f
            delay(300)
            _transferProgress.value = 1.0f
            _isTransferringFile.value = false

            repository.sendMessage(
                chatId = chatId,
                plainText = "Foto: $imageTitle",
                mediaType = MediaType.IMAGE,
                mediaFileName = "secure_image_${System.currentTimeMillis()}.enc",
                mediaFileSizeFormatted = "2.4 MB"
            )
            _toastEvents.emit(VaultToastEvent.Success("Media foto terenkripsi berhasil dikirim."))
        }
    }

    fun sendLargeEncryptedFile(fileName: String, sizeMb: Int) {
        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            _isTransferringFile.value = true
            for (i in 1..10) {
                _transferProgress.value = i / 10f
                delay(120)
            }
            _isTransferringFile.value = false

            repository.sendMessage(
                chatId = chatId,
                plainText = "Berkas Besar Terenkripsi: $fileName",
                mediaType = MediaType.FILE,
                mediaFileName = fileName,
                mediaFileSizeFormatted = "$sizeMb MB"
            )
            _toastEvents.emit(VaultToastEvent.Success("Berkas $fileName ($sizeMb MB) berhasil diunggah dengan enkripsi chunk."))
        }
    }

    fun setDisappearingTimer(seconds: Int) {
        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            repository.updateDisappearingTimer(chatId, seconds)
            val msg = if (seconds == 0) "Timer musnah otomatis dimatikan" else "Pesan akan otomatis musnah dalam $seconds detik setelah dibaca"
            _toastEvents.emit(VaultToastEvent.Info(msg))
        }
    }

    fun exportChatCsv() {
        val chatId = _selectedChatId.value ?: return
        val chat = activeChatEntity.value ?: return
        val messages = activeChatMessages.value

        viewModelScope.launch {
            try {
                val file = ExportHelper.generateCsv(context, chat.contactName, messages)
                ExportHelper.shareExportFile(context, file, "text/csv")
                _toastEvents.emit(VaultToastEvent.Success("Ekspor CSV ${chat.contactName} berhasil dibuat."))
            } catch (e: Exception) {
                _toastEvents.emit(VaultToastEvent.Error("Gagal mengekspor CSV: ${e.message}"))
            }
        }
    }

    fun exportChatPdf() {
        val chatId = _selectedChatId.value ?: return
        val chat = activeChatEntity.value ?: return
        val messages = activeChatMessages.value

        viewModelScope.launch {
            try {
                val file = ExportHelper.generatePdfDocument(context, chat.contactName, messages)
                ExportHelper.shareExportFile(context, file, "text/plain")
                _toastEvents.emit(VaultToastEvent.Success("Laporan obrolan PDF/Dokumen berhasil dibuat."))
            } catch (e: Exception) {
                _toastEvents.emit(VaultToastEvent.Error("Gagal mengekspor dokumen: ${e.message}"))
            }
        }
    }

    fun triggerCloudBackup() {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            delay(1500)
            val backupCipher = repository.performCloudBackup()
            _isCloudSyncing.value = false
            _toastEvents.emit(VaultToastEvent.Success("Pencadangan Cloud Terenkripsi Berhasil! (Kunci SHA-256: ${backupCipher.take(12)}...)"))
        }
    }

    fun triggerDeviceSync() {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            repository.syncAllDevices()
            _isCloudSyncing.value = false
            _toastEvents.emit(VaultToastEvent.Success("Seluruh gadget & riwayat chat berhasil disinkronkan tanpa jeda."))
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        settingsManager.updateSettings { it.copy(isBiometricEnabled = enabled) }
        viewModelScope.launch {
            _toastEvents.emit(VaultToastEvent.Info(if (enabled) "Kunci sandi & biometrik diaktifkan untuk masuk brankas." else "Kunci biometrik dinonaktifkan."))
        }
    }

    fun updatePin(pin: String) {
        if (pin.length >= 4) {
            settingsManager.updateSettings { it.copy(customPin = pin) }
            viewModelScope.launch {
                _toastEvents.emit(VaultToastEvent.Success("Kunci PIN brankas berhasil diperbarui."))
            }
        }
    }

    fun toggleStealthNotification(enabled: Boolean) {
        settingsManager.updateSettings { it.copy(isStealthNotificationEnabled = enabled) }
    }

    fun updateDisguiseNotificationText(title: String, body: String) {
        settingsManager.updateSettings {
            it.copy(
                disguiseNotificationTitle = title,
                disguiseNotificationBody = body
            )
        }
        viewModelScope.launch {
            _toastEvents.emit(VaultToastEvent.Success("Templat notifikasi tersamar diperbarui."))
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        settingsManager.updateSettings { it.copy(themeMode = mode) }
    }

    fun testCamouflageNotification() {
        val s = settings.value
        NotificationHelper.showNotification(
            context = context,
            notificationId = 8888,
            isDisguised = s.isStealthNotificationEnabled,
            realTitle = "Alice: Pesan Rahasia Terenkripsi",
            realBody = "Pertemuan rahasia dijadwalkan pukul 20:00.",
            disguiseTitle = s.disguiseNotificationTitle,
            disguiseBody = s.disguiseNotificationBody
        )
        viewModelScope.launch {
            _toastEvents.emit(VaultToastEvent.Info("Notifikasi uji coba dikirim dengan mode penyamaran."))
        }
    }

    fun addReminder(title: String, desc: String, hour: Int, min: Int) {
        viewModelScope.launch {
            repository.addReminder(title, desc, hour, min)
            _toastEvents.emit(VaultToastEvent.Success("Pengingat harian berhasil disimpan."))
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
            _toastEvents.emit(VaultToastEvent.Info("Pengingat dihapus."))
        }
    }

    fun clearAllChatsAndMessages() {
        viewModelScope.launch {
            repository.clearAllChatsAndMessages()
            _selectedChatId.value = null
            _toastEvents.emit(VaultToastEvent.Success("Semua obrolan dan kontak dummy telah dibersihkan."))
        }
    }
}
