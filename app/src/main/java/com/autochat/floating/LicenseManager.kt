package com.autochat.floating

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class DongtubeInvoice(
    val invoiceId: String,
    val amount: Int,
    val fee: Int,
    val total: Int,
    val qrisImageUrl: String,
    val expiredAt: String
)

data class LicenseState(
    val isLifetime: Boolean,
    val isTrialActive: Boolean,
    val isTrialExpired: Boolean,
    val isTrialUnused: Boolean,
    val remainingMillis: Long,
    val deviceId: String,
    val registeredIp: String,
    val paidInvoiceId: String
)

class LicenseManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: LicenseManager? = null

        fun getInstance(context: Context): LicenseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LicenseManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        // 3 Hari dalam Milliseconds (3 * 24 * 60 * 60 * 1000)
        const val TRIAL_DURATION_MS: Long = 3L * 24L * 60L * 60L * 1000L
        private const val BASE_URL = "https://payment.dongtube.cyou"

        // Obfuscated API Key (DONGTUBE_20a06f2ab35b44ac)
        private val OBFUSCATED_KEY = byteArrayOf(
            0x44, 0x4F, 0x4E, 0x47, 0x54, 0x55, 0x42, 0x45, 0x5F,
            0x32, 0x30, 0x61, 0x30, 0x36, 0x66, 0x32, 0x61, 0x62,
            0x33, 0x35, 0x62, 0x34, 0x34, 0x61, 0x63
        )

        private fun getApiKey(): String {
            return String(OBFUSCATED_KEY, StandardCharsets.UTF_8)
        }

        private const val HMAC_SALT = "AutoChat_Dongtube_Secure_Salt_2026_xK9#"
    }

    // In-memory Fast Prefetch Cache untuk QRIS instan
    @Volatile
    private var cachedPrefetchedInvoice: DongtubeInvoice? = null
    @Volatile
    private var cachedPrefetchedBitmap: android.graphics.Bitmap? = null
    @Volatile
    private var isPrefetching = false

    private val prefs: SharedPreferences = context.getSharedPreferences("AutoChatLicenseVault", Context.MODE_PRIVATE)
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    val deviceId: String by lazy {
        generateStableDeviceId()
    }

    private var serverTimeOffset: Long = 0L
    private var cachedPublicIp: String = "127.0.0.1"

    init {
        // Pulihkan dari media storage jika app baru diinstall ulang
        restoreFromPersistentStorages()
        syncNetworkTimeAndIp {
            // Prefetch invoice & QRIS di background sejak awal buka aplikasi
            prefetchDongtubeInvoice(10000)
        }
    }

    private fun generateStableDeviceId(): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_ID"
            val raw = "$androidId|${Build.MANUFACTURER}|${Build.MODEL}|${Build.BOARD}|${Build.HARDWARE}"
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(raw.toByteArray(StandardCharsets.UTF_8))
            val hex = StringBuilder()
            for (b in hash) {
                hex.append(String.format("%02x", b))
            }
            // Ambil 16 karakter unik yang mudah dibaca
            "DEV-" + hex.substring(0, 16).uppercase()
        } catch (_: Exception) {
            "DEV-FALLBACK-" + (Build.MODEL.hashCode().toLong() and 0xFFFFFFFFL).toString(16).uppercase()
        }
    }

    private fun signPayload(data: String): String {
        return try {
            val sha256Hmac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec((HMAC_SALT + deviceId).toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
            sha256Hmac.init(secretKey)
            val signed = sha256Hmac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            val hex = StringBuilder()
            for (b in signed) {
                hex.append(String.format("%02x", b))
            }
            hex.toString()
        } catch (_: Exception) {
            ""
        }
    }

    fun getEstimatedServerTime(): Long {
        val local = System.currentTimeMillis()
        val estimated = local + serverTimeOffset
        val lastSaved = prefs.getLong("last_recorded_timestamp", 0L)
        return if (estimated < lastSaved) {
            // Indikasi manipulasi jam lokal
            lastSaved
        } else {
            prefs.edit().putLong("last_recorded_timestamp", estimated).apply()
            estimated
        }
    }

    fun getLicenseState(): LicenseState {
        // Cek dulu dari persistent storage jika ada perubahan eksternal
        restoreFromPersistentStorages()

        val isLifetime = prefs.getBoolean("is_lifetime_paid", false)
        val trialStart = prefs.getLong("trial_start_timestamp", 0L)
        val regIp = prefs.getString("registered_ip", cachedPublicIp) ?: cachedPublicIp
        val invoiceId = prefs.getString("paid_invoice_id", "") ?: ""

        if (isLifetime) {
            return LicenseState(
                isLifetime = true,
                isTrialActive = false,
                isTrialExpired = false,
                isTrialUnused = false,
                remainingMillis = Long.MAX_VALUE,
                deviceId = deviceId,
                registeredIp = regIp,
                paidInvoiceId = invoiceId
            )
        }

        if (trialStart <= 0L) {
            return LicenseState(
                isLifetime = false,
                isTrialActive = false,
                isTrialExpired = false,
                isTrialUnused = true,
                remainingMillis = TRIAL_DURATION_MS,
                deviceId = deviceId,
                registeredIp = regIp,
                paidInvoiceId = ""
            )
        }

        val currentTime = getEstimatedServerTime()
        val elapsed = currentTime - trialStart
        val remaining = TRIAL_DURATION_MS - elapsed

        return if (remaining > 0) {
            LicenseState(
                isLifetime = false,
                isTrialActive = true,
                isTrialExpired = false,
                isTrialUnused = false,
                remainingMillis = remaining,
                deviceId = deviceId,
                registeredIp = regIp,
                paidInvoiceId = ""
            )
        } else {
            LicenseState(
                isLifetime = false,
                isTrialActive = false,
                isTrialExpired = true,
                isTrialUnused = false,
                remainingMillis = 0L,
                deviceId = deviceId,
                registeredIp = regIp,
                paidInvoiceId = ""
            )
        }
    }

    fun activateTrial(onComplete: (success: Boolean, message: String) -> Unit) {
        val currentState = getLicenseState()
        if (currentState.isLifetime) {
            onComplete(true, "Anda sudah memiliki lisensi seumur hidup aktif!")
            return
        }

        if (!currentState.isTrialUnused) {
            if (currentState.isTrialActive) {
                onComplete(true, "Masa trial 3 hari Anda sedang berjalan.")
            } else {
                onComplete(false, "Masa percobaan 3 hari untuk perangkat ini sudah habis. Silakan beli lisensi Rp 10.000 seumur hidup.")
            }
            return
        }

        executor.execute {
            syncNetworkTimeAndIpInternal()

            val now = getEstimatedServerTime()
            val ip = cachedPublicIp

            // Simpan ke SharedPreferences
            prefs.edit()
                .putLong("trial_start_timestamp", now)
                .putString("registered_ip", ip)
                .apply()

            saveToPersistentStorages()

            // Kirim notifikasi log otomatis ke Telegram Owner (Catat IP & Device ID)
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss 'WIB'", Locale("in", "ID"))
            val logMsg = "🎁 [TIKTOK AUTOCHAT - TRIAL AKTIF]\n" +
                    "📱 Device ID: $deviceId\n" +
                    "🌐 IP Pengguna: $ip\n" +
                    "⏰ Mulai: ${sdf.format(Date(now))}\n" +
                    "⏳ Durasi: 3 Hari (72 Jam)"
            notifyOwnerTelegram(logMsg)

            mainHandler.post {
                onComplete(true, "Masa Trial 3 Hari (72 Jam) berhasil diaktifkan!")
            }
        }
    }

    fun markAsLifetimePaid(invoiceId: String) {
        prefs.edit()
            .putBoolean("is_lifetime_paid", true)
            .putString("paid_invoice_id", invoiceId)
            .putString("registered_ip", cachedPublicIp)
            .apply()

        saveToPersistentStorages()

        // Kirim notifikasi sukses lunas ke Telegram Owner
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss 'WIB'", Locale("in", "ID"))
        val logMsg = "👑 [TIKTOK AUTOCHAT - LISENSI LUNAS]\n" +
                "📱 Device ID: $deviceId\n" +
                "🌐 IP Pengguna: $cachedPublicIp\n" +
                "🧾 Invoice Dongtube: $invoiceId\n" +
                "💰 Status: Seumur Hidup (Rp 10.000 Aktif Permanen)\n" +
                "⏰ Waktu Bayar: ${sdf.format(Date())}"
        notifyOwnerTelegram(logMsg)
    }

    private fun notifyOwnerTelegram(message: String) {
        executor.execute {
            try {
                val token = "8647152325:AAGo540o8e0oBd7tALfzPkWIzJRRDPy3GOY"
                val chatId = "5185334850"
                val url = URL("https://api.telegram.org/bot$token/sendMessage")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val postData = "chat_id=$chatId&text=" + java.net.URLEncoder.encode(message, "UTF-8")
                conn.outputStream.use { os ->
                    os.write(postData.toByteArray(StandardCharsets.UTF_8))
                }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    /**
     * Prefetch invoice & gambar QRIS di background agar saat tombol Beli ditekan, QR langsung instan muncul tanpa jeda.
     */
    fun prefetchDongtubeInvoice(amount: Int = 10000) {
        val state = getLicenseState()
        if (state.isLifetime || isPrefetching) return

        // Jika sudah ada cache yang belum kadaluarsa, tidak perlu fetch ulang
        if (cachedPrefetchedInvoice != null && cachedPrefetchedBitmap != null) return

        isPrefetching = true
        executor.execute {
            try {
                fetchInvoiceDirectly(amount) { invoice, bitmap, _ ->
                    if (invoice != null && bitmap != null) {
                        cachedPrefetchedInvoice = invoice
                        cachedPrefetchedBitmap = bitmap
                    }
                    isPrefetching = false
                }
            } catch (_: Exception) {
                isPrefetching = false
            }
        }
    }

    private fun fetchInvoiceDirectly(amount: Int, onResult: (DongtubeInvoice?, android.graphics.Bitmap?, String?) -> Unit) {
        try {
            val apiKey = getApiKey()
            val urlStr = "$BASE_URL/api/v1/invoice?apikey=$apiKey&amount=$amount"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()

                val json = JSONObject(sb.toString())
                if (json.optBoolean("success", false)) {
                    val invId = json.getString("invoice_id")
                    val invAmount = json.getInt("amount")
                    val invFee = json.optInt("fee", 0)
                    val invTotal = json.getInt("total")
                    var qrisPath = json.getString("qris_image")
                    if (!qrisPath.startsWith("http")) {
                        qrisPath = "$BASE_URL$qrisPath"
                    }
                    val expiredAt = json.optString("expired_at", "")

                    val invoice = DongtubeInvoice(
                        invoiceId = invId,
                        amount = invAmount,
                        fee = invFee,
                        total = invTotal,
                        qrisImageUrl = qrisPath,
                        expiredAt = expiredAt
                    )

                    // Langsung unduh bitmap gambar QR secara paralel/sekuensial cepat
                    var bitmap: android.graphics.Bitmap? = null
                    try {
                        val imgUrl = URL(qrisPath)
                        val imgConn = imgUrl.openConnection()
                        imgConn.connectTimeout = 6000
                        imgConn.readTimeout = 6000
                        bitmap = android.graphics.BitmapFactory.decodeStream(imgConn.getInputStream())
                    } catch (_: Exception) {}

                    onResult(invoice, bitmap, null)
                } else {
                    val err = json.optString("error", "Gagal membuat invoice")
                    onResult(null, null, err)
                }
            } else {
                onResult(null, null, "Server Dongtube merespons kode: $responseCode")
            }
        } catch (e: Exception) {
            onResult(null, null, "Koneksi gagal: ${e.localizedMessage ?: "Periksa jaringan Anda"}")
        }
    }

    fun createDongtubeInvoice(amount: Int = 10000, callback: (DongtubeInvoice?, android.graphics.Bitmap?, String?) -> Unit) {
        // Cek apakah sudah ada di prefetch cache (Ultra-fast instant load!)
        val cachedInv = cachedPrefetchedInvoice
        val cachedBmp = cachedPrefetchedBitmap
        if (cachedInv != null && cachedBmp != null) {
            // Gunakan cache dan kosongkan untuk invoice berikutnya
            cachedPrefetchedInvoice = null
            cachedPrefetchedBitmap = null
            mainHandler.post {
                callback(cachedInv, cachedBmp, null)
            }
            // Siapkan prefetch berikutnya di background
            prefetchDongtubeInvoice(amount)
            return
        }

        executor.execute {
            fetchInvoiceDirectly(amount) { inv, bmp, err ->
                mainHandler.post {
                    callback(inv, bmp, err)
                }
            }
        }
    }

    fun checkDongtubeInvoiceStatus(invoiceId: String, callback: (isPaid: Boolean, status: String, error: String?) -> Unit) {
        executor.execute {
            try {
                val apiKey = getApiKey()
                val urlStr = "$BASE_URL/api/v1/invoice/status?apikey=$apiKey&invoice_id=$invoiceId"
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val code = conn.responseCode
                if (code == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    reader.close()

                    val json = JSONObject(sb.toString())
                    val status = json.optString("status", "pending")
                    val isPaid = status.equals("paid", ignoreCase = true)

                    if (isPaid) {
                        markAsLifetimePaid(invoiceId)
                    }

                    mainHandler.post {
                        callback(isPaid, status, null)
                    }
                } else {
                    mainHandler.post {
                        callback(false, "error", "Gagal menghubungi server status ($code)")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, "error", e.localizedMessage)
                }
            }
        }
    }

    fun syncNetworkTimeAndIp(onDone: (() -> Unit)?) {
        executor.execute {
            syncNetworkTimeAndIpInternal()
            if (onDone != null) {
                mainHandler.post { onDone() }
            }
        }
    }

    private fun syncNetworkTimeAndIpInternal() {
        // 1. Ambil Server Time dari HTTP Date header Dongtube
        try {
            val url = URL("$BASE_URL/api/v1/balance?apikey=${getApiKey()}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val dateHeader = conn.getHeaderField("Date")
            if (!dateHeader.isNullOrBlank()) {
                val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                val parsedDate = format.parse(dateHeader)
                if (parsedDate != null) {
                    serverTimeOffset = parsedDate.time - System.currentTimeMillis()
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}

        // 2. Ambil Public IP Address
        try {
            val ipUrl = URL("https://api.ipify.org?format=json")
            val conn = ipUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val content = reader.readLine()
                reader.close()
                val json = JSONObject(content)
                val ip = json.optString("ip")
                if (ip.isNotBlank()) {
                    cachedPublicIp = ip
                    val currentRegIp = prefs.getString("registered_ip", null)
                    if (currentRegIp.isNullOrBlank()) {
                        prefs.edit().putString("registered_ip", ip).apply()
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
    }

    // =========================================================================
    // MULTI-LAYER PERSISTENT STORAGE (SURVIVES UNINSTALL & REINSTALL)
    // =========================================================================

    private fun getStorageFiles(): List<File> {
        val files = mutableListOf<File>()
        try {
            // Lokasi 1: Android Media Directory (Standard shared app media)
            val mediaDir = File(Environment.getExternalStorageDirectory(), "Android/media/com.autochat.floating")
            if (!mediaDir.exists()) mediaDir.mkdirs()
            files.add(File(mediaDir, ".license_vault"))

            // Lokasi 2: Documents Directory
            val docDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!docDir.exists()) docDir.mkdirs()
            files.add(File(docDir, ".autochat_lic_vault.dat"))

            // Lokasi 3: Downloads Directory
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dlDir.exists()) dlDir.mkdirs()
            files.add(File(dlDir, ".autochat_hw_token.dat"))

            // Lokasi 4: Pictures Directory (AutoChatProof metadata)
            val picDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (!picDir.exists()) picDir.mkdirs()
            files.add(File(picDir, ".autochat_device_token.dat"))
        } catch (_: Exception) {}
        return files
    }

    private fun saveToPersistentStorages() {
        executor.execute {
            try {
                val isLifetime = prefs.getBoolean("is_lifetime_paid", false)
                val trialStart = prefs.getLong("trial_start_timestamp", 0L)
                val invoiceId = prefs.getString("paid_invoice_id", "") ?: ""
                val ip = prefs.getString("registered_ip", cachedPublicIp) ?: cachedPublicIp

                val rawPayload = "$deviceId|$isLifetime|$trialStart|$invoiceId|$ip"
                val signature = signPayload(rawPayload)

                val json = JSONObject().apply {
                    put("device_id", deviceId)
                    put("is_lifetime", isLifetime)
                    put("trial_start", trialStart)
                    put("invoice_id", invoiceId)
                    put("registered_ip", ip)
                    put("signature", signature)
                    put("updated_at", System.currentTimeMillis())
                }

                val content = json.toString()

                for (f in getStorageFiles()) {
                    try {
                        f.writeText(content, StandardCharsets.UTF_8)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private fun restoreFromPersistentStorages() {
        try {
            val localLifetime = prefs.getBoolean("is_lifetime_paid", false)
            val localTrialStart = prefs.getLong("trial_start_timestamp", 0L)

            // Jika lokal sudah lifetime, tidak perlu restore
            if (localLifetime) return

            for (f in getStorageFiles()) {
                if (!f.exists() || !f.canRead()) continue
                try {
                    val text = f.readText(StandardCharsets.UTF_8)
                    if (text.isBlank()) continue

                    val json = JSONObject(text)
                    val fileDevId = json.optString("device_id")
                    if (fileDevId != deviceId) continue // Bukan perangkat ini!

                    val isLifetime = json.optBoolean("is_lifetime", false)
                    val trialStart = json.optLong("trial_start", 0L)
                    val invoiceId = json.optString("invoice_id", "")
                    val ip = json.optString("registered_ip", "")
                    val signature = json.optString("signature", "")

                    // Validasi keaslian signature HMAC
                    val expectedSig = signPayload("$fileDevId|$isLifetime|$trialStart|$invoiceId|$ip")
                    if (signature == expectedSig) {
                        // Restore ke SharedPreferences
                        val editor = prefs.edit()
                        if (isLifetime) {
                            editor.putBoolean("is_lifetime_paid", true)
                            editor.putString("paid_invoice_id", invoiceId)
                        }
                        if (trialStart > 0L && localTrialStart == 0L) {
                            editor.putLong("trial_start_timestamp", trialStart)
                        }
                        if (ip.isNotBlank()) {
                            editor.putString("registered_ip", ip)
                        }
                        editor.apply()
                        break
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
