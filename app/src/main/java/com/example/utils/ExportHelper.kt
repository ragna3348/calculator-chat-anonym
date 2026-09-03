package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.crypto.CryptoEngine
import com.example.data.model.MessageEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    fun generateCsv(context: Context, chatTitle: String, messages: List<MessageEntity>): File {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val fileName = "Export_Chat_${chatTitle.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)

        FileOutputStream(file).bufferedWriter().use { writer ->
            writer.write("ID,Pengirim,Waktu,Tipe Media,Teks Dekripsi,Status Enkripsi,Checksum\n")
            messages.forEach { msg ->
                val decryptedText = CryptoEngine.decrypt(msg.ciphertext, msg.iv, msg.salt).replace("\"", "\"\"")
                val timeStr = dateFormat.format(Date(msg.timestamp))
                val sender = if (msg.senderId == "me") "Saya" else msg.senderName
                writer.write("${msg.id},\"$sender\",\"$timeStr\",\"${msg.mediaType}\",\"$decryptedText\",\"${if (msg.isEncrypted) "E2EE-AES-256" else "Plain"}\",\"${msg.checksum}\"\n")
            }
        }
        return file
    }

    fun generatePdfDocument(context: Context, chatTitle: String, messages: List<MessageEntity>): File {
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale.forLanguageTag("id-ID"))
        val fileName = "Laporan_Obrolan_${chatTitle.replace(" ", "_")}_${System.currentTimeMillis()}.txt"
        val file = File(context.cacheDir, fileName)

        FileOutputStream(file).bufferedWriter().use { writer ->
            writer.write("========================================================================\n")
            writer.write("              LAPORAN LOG OBROLAN RAHASIA TERENKRIPSI                  \n")
            writer.write("========================================================================\n")
            writer.write("Kontak / Ruang: $chatTitle\n")
            writer.write("Protokol      : AES-256-GCM End-to-End Cryptography\n")
            writer.write("Waktu Ekspor  : ${dateFormat.format(Date())}\n")
            writer.write("Total Pesan   : ${messages.size}\n")
            writer.write("Status Sidik  : ${CryptoEngine.generateFingerprint(chatTitle)}\n")
            writer.write("------------------------------------------------------------------------\n\n")

            messages.forEach { msg ->
                val decryptedText = CryptoEngine.decrypt(msg.ciphertext, msg.iv, msg.salt)
                val timeStr = dateFormat.format(Date(msg.timestamp))
                val sender = if (msg.senderId == "me") ">> SAYA" else "<< ${msg.senderName.uppercase()}"

                writer.write("[$timeStr] $sender [${msg.mediaType}]\n")
                writer.write("Pesan: $decryptedText\n")
                if (!msg.mediaFileName.isNullOrBlank()) {
                    writer.write("Lampiran: ${msg.mediaFileName} (${msg.mediaFileSizeFormatted ?: "N/A"})\n")
                }
                writer.write("Verifikasi Checksum: ${msg.checksum}\n")
                writer.write("------------------------------------------------------------------------\n")
            }

            writer.write("\n=== AKHIR DARI LOG TERENKRIPSI ===\n")
        }
        return file
    }

    fun shareExportFile(context: Context, file: File, mimeType: String = "text/plain") {
        try {
            val uri: Uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Ekspor Log Obrolan Rahasia")
                putExtra(Intent.EXTRA_TEXT, "Berikut berkas log obrolan terenkripsi yang diekspor dari Kalkulator Vault.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan Ekspor Chat").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
