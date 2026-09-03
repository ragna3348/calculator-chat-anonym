package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CalculationHistoryEntity
import com.example.data.model.ChatEntity
import com.example.data.model.LinkedDeviceEntity
import com.example.data.model.MessageEntity
import com.example.data.model.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // --- CHATS ---
    @Query("SELECT * FROM chats ORDER BY lastTimestamp DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    fun getChatById(chatId: String): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatByIdDirect(chatId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE mathUsername = :mathUsername LIMIT 1")
    suspend fun getChatByMathUsernameDirect(mathUsername: String): ChatEntity?

    @Query("SELECT mathUsername FROM chats WHERE mathUsername != ''")
    suspend fun getAllExistingMathUsernames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateChat(chat: ChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)

    @Query("UPDATE chats SET lastMessage = :lastMessage, lastTimestamp = :timestamp, unreadCount = unreadCount + :unreadIncrement WHERE id = :chatId")
    suspend fun updateChatPreview(chatId: String, lastMessage: String, timestamp: Long, unreadIncrement: Int = 0)

    @Query("UPDATE chats SET unreadCount = 0 WHERE id = :chatId")
    suspend fun markChatAsRead(chatId: String)

    @Query("UPDATE chats SET disappearingTimerSeconds = :seconds WHERE id = :chatId")
    suspend fun updateDisappearingTimer(chatId: String, seconds: Int)

    @Query("UPDATE chats SET isOnline = :isOnline, lastSeen = :lastSeen WHERE mathUsername = :mathUsername OR id = :mathUsername")
    suspend fun updateChatOnlineStatus(mathUsername: String, isOnline: Boolean, lastSeen: Long)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: String)

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()

    // --- MESSAGES ---
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesDirect(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChatDirect(chatId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM messages WHERE checksum = :checksum OR (chatId = :chatId AND timestamp = :timestamp)")
    suspend fun deleteMessageByChecksumOrTimestamp(chatId: String, checksum: String, timestamp: Long)

    @Query("SELECT * FROM messages WHERE burnTimestamp IS NOT NULL AND burnTimestamp <= :currentTime")
    suspend fun getExpiredSelfDestructMessages(currentTime: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE burnTimestamp IS NOT NULL AND burnTimestamp <= :currentTime")
    suspend fun purgeExpiredSelfDestructMessages(currentTime: Long): Int

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearMessagesForChat(chatId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    // --- LINKED DEVICES ---
    @Query("SELECT * FROM linked_devices ORDER BY lastActive DESC")
    fun getAllDevices(): Flow<List<LinkedDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevice(device: LinkedDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<LinkedDeviceEntity>)

    @Query("DELETE FROM linked_devices WHERE deviceId = :deviceId")
    suspend fun removeDevice(deviceId: String)

    @Query("UPDATE linked_devices SET lastActive = :timestamp, syncStatus = :status WHERE deviceId = :deviceId")
    suspend fun updateDeviceSync(deviceId: String, timestamp: Long, status: String)

    // --- DAILY REMINDERS ---
    @Query("SELECT * FROM daily_reminders ORDER BY timeHour ASC, timeMinute ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateReminder(reminder: ReminderEntity): Long

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    // --- CALCULATION HISTORY ---
    @Query("SELECT * FROM calc_history ORDER BY timestamp DESC LIMIT 30")
    fun getCalculationHistory(): Flow<List<CalculationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculationHistory(history: CalculationHistoryEntity)

    @Query("DELETE FROM calc_history")
    suspend fun clearCalculationHistory()
}
