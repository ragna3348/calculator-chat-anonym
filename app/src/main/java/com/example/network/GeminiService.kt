package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @param:Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @param:Json(name = "parts") val parts: List<GeminiPart>,
    @param:Json(name = "role") val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @param:Json(name = "contents") val contents: List<GeminiContent>,
    @param:Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @param:Json(name = "content") val content: GeminiContent?
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @param:Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun askCustomerSupport(userMessage: String, chatHistory: List<Pair<String, String>> = emptyList()): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Intelligent fallback when API key is unconfigured / offline
            return@withContext getOfflineSupportResponse(userMessage)
        }

        val historyContents = chatHistory.takeLast(6).map { (sender, text) ->
            GeminiContent(
                role = if (sender == "me") "user" else "model",
                parts = listOf(GeminiPart(text = text))
            )
        }

        val currentContent = GeminiContent(
            role = "user",
            parts = listOf(GeminiPart(text = userMessage))
        )

        val systemInstruction = GeminiContent(
            parts = listOf(
                GeminiPart(
                    text = "Anda adalah Customer Support Agent resmi untuk aplikasi Kalkulator Vault terenkripsi. " +
                            "Jawab dalam bahasa Indonesia dengan ramah, profesional, ringkas, dan jelas. " +
                            "Pandu pengguna mengenai fitur: 1) Membuka chat rahasia via kalkulator '99+99=', 2) Enkripsi end-to-end AES-256-GCM, 3) Pesan hapus otomatis (self-destruct timer), 4) Pengiriman media dan file besar terenkripsi, 5) Cadangan cloud dan sinkronisasi multi-perangkat, 6) Kunci biometrik/PIN, 7) Notifikasi tersamar untuk privasi."
                )
            )
        )

        val request = GeminiRequest(
            contents = historyContents + currentContent,
            systemInstruction = systemInstruction
        )

        try {
            val response = service.generateContent(apiKey, request)
            val reply = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            reply?.trim() ?: getOfflineSupportResponse(userMessage)
        } catch (e: Exception) {
            Log.w("GeminiClient", "API call fallback: ${e.message}")
            getOfflineSupportResponse(userMessage)
        }
    }

    private fun getOfflineSupportResponse(query: String): String {
        val lower = query.lowercase()
        return when {
            "99" in lower || "buka" in lower || "rahasia" in lower || "kalkulator" in lower -> {
                "💡 **Cara Membuka Chat Rahasia:**\nKetik `99+99` pada layar kalkulator lalu tekan tombol sama dengan (`=`). Anda akan langsung dialihkan ke brankas obrolan terenkripsi. Jika mengaktifkan kunci biometrik/PIN, Anda akan diminta verifikasi sidik jari/sandi terlebih dahulu."
            }
            "enkripsi" in lower || "e2ee" in lower || "aman" in lower || "kunci" in lower -> {
                "🔒 **Keamanan Enkripsi End-to-End (AES-256-GCM):**\nSetiap pesan teks, foto, audio, dan file besar dienkripsi secara lokal di perangkat Anda menggunakan kunci simetris 256-bit dan vektor inisialisasi (IV) unik. Hanya penerima sah dengan kunci privat yang dapat mendekripsinya."
            }
            "hapus" in lower || "bakar" in lower || "otomatis" in lower || "timer" in lower || "destruct" in lower -> {
                "⏱️ **Fitur Hapus Pesan Otomatis (Self-Destruct):**\nDi dalam ruang obrolan, tekan ikon jam/timer di bar bagian atas untuk memilih durasi (5 detik, 10 detik, 30 detik, atau 1 menit). Pesan akan otomatis terbakar dan musnah dari database setelah dibaca oleh penerima."
            }
            "cadangan" in lower || "backup" in lower || "cloud" in lower || "awan" in lower -> {
                "☁️ **Pencadangan Cloud Terenkripsi:**\nMasuk ke tab 'Cadangan & Sinkronisasi'. Tekan 'Buat Cadangan Cloud'. Semua data dienkripsi dengan kata sandi utama Anda sebelum diunggah ke penyimpanan awan privat. Anda dapat memulihkannya (restore) kapan saja."
            }
            "multi" in lower || "perangkat" in lower || "sync" in lower || "sinkron" in lower -> {
                "📱 **Sinkronisasi Multi-Perangkat:**\nAplikasi mendukung sinkronisasi data antar gadget (Smartphone, Tablet, PC) dengan protokol P2P mesh terenkripsi. Data pesan dan status terbaca akan tersinkronisasi secara instan tanpa jeda."
            }
            "notifikasi" in lower || "tersamar" in lower || "samar" in lower || "privasi" in lower -> {
                "🎭 **Sistem Notifikasi Tersamar (Camouflage):**\nNotifikasi masuk dapat disamarkan menjadi peringatan sistem (misal: 'Pembersihan Memori Selesai' atau 'Kalkulasi Disimpan') sehingga orang lain di sekitar Anda tidak mengetahui bahwa Anda menerima pesan rahasia."
            }
            "ekspor" in lower || "csv" in lower || "pdf" in lower -> {
                "📄 **Ekspor Data CSV & PDF:**\nBuka menu opsi pada obrolan (ikon titik tiga di kanan atas) lalu pilih 'Ekspor Chat'. Anda dapat mengunduh log percakapan dalam format file CSV atau dokumen PDF terenkripsi."
            }
            else -> {
                "Terima kasih telah menghubungi Dukungan Pelanggan Kalkulator Vault! 🛡️\n\nSistem kami siap membantu Anda seputar:\n• Kunci Rahasia Kalkulator (99+99=)\n• Enkripsi End-to-End AES-256\n• Pesan Musnah Otomatis\n• Berbagi Media & File Besar Terenkripsi\n• Cadangan Cloud & Multi-Perangkat\n• Ekspor CSV / PDF\n• Notifikasi Tersamar\n\nSilakan tanyakan detail fitur yang ingin Anda ketahui!"
            }
        }
    }
}
