package com.qrphotoshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Base64
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.FrameLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import java.io.OutputStream

class ResultActivity : AppCompatActivity() {

    private lateinit var tvLink: TextView
    private lateinit var tvExpiry: TextView
    private lateinit var ivQrCode: ImageView
    private lateinit var btnCopy: Button
    private lateinit var btnShare: Button
    private lateinit var btnShareQr: Button
    private lateinit var btnSaveQr: Button
    private lateinit var adContainerViewResult: FrameLayout
    private var adView: AdView? = null

    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        tvLink = findViewById(R.id.tvLink)
        tvExpiry = findViewById(R.id.tvExpiry)
        ivQrCode = findViewById(R.id.ivQrCode)
        btnCopy = findViewById(R.id.btnCopy)
        btnShare = findViewById(R.id.btnShare)
        btnShareQr = findViewById(R.id.btnShareQr)
        btnSaveQr = findViewById(R.id.btnSaveQr)

        // loadBannerAd()

        val downloadUrl = intent.getStringExtra("EXTRA_DOWNLOAD_URL") ?: ""
        val qrCodeData = intent.getStringExtra("EXTRA_QR_CODE") ?: ""
        val expiresAtString = intent.getStringExtra("EXTRA_EXPIRES_AT") ?: ""

        tvLink.text = downloadUrl

        // Decode Base64 QR Code
        try {
            if (qrCodeData.startsWith("data:image/png;base64,")) {
                val base64Image = qrCodeData.split(",")[1]
                val decodedString = Base64.decode(base64Image, Base64.DEFAULT)
                val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                ivQrCode.setImageBitmap(decodedByte)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Setup Expiry Timer
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            // Note: Server returns UTC ISO string, need to handle timezone
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val expiresAtDate = sdf.parse(expiresAtString)
            
            if (expiresAtDate != null) {
                startCountdown(expiresAtDate.time)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tvExpiry.text = "Expires in 12 hours"
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Download Link", downloadUrl)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnShare.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Here are some photos for you! Download them before they expire: $downloadUrl")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Share Link via..."))
        }

        btnShareQr.setOnClickListener {
            shareQrCode()
        }

        btnSaveQr.setOnClickListener {
            saveQrCode()
        }
    }

    private fun shareQrCode() {
        val drawable = ivQrCode.drawable as? android.graphics.drawable.BitmapDrawable ?: return
        val bitmap = drawable.bitmap

        try {
            val cachePath = java.io.File(cacheDir, "shared_images")
            cachePath.mkdirs()
            val file = java.io.File(cachePath, "qr_code.png")
            val fileOutStream = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fileOutStream)
            fileOutStream.flush()
            fileOutStream.close()

            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

            if (contentUri != null) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setDataAndType(contentUri, contentResolver.getType(contentUri))
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    type = "image/png"
                }
                startActivity(Intent.createChooser(shareIntent, "Share QR Code via..."))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to share QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveQrCode() {
        val drawable = ivQrCode.drawable as? android.graphics.drawable.BitmapDrawable ?: return
        val bitmap = drawable.bitmap

        val filename = "QR_Share_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        try {
            imageUri?.let { uri ->
                fos = resolver.openOutputStream(uri)
                fos?.let {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    it.close()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                Toast.makeText(this, "QR Code saved to Gallery!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBannerAd() {
        adContainerViewResult = findViewById(R.id.adContainerViewResult)
        adView = AdView(this).apply {
            setAdSize(AdSize.BANNER)
            // Test Ad Unit ID
            adUnitId = "ca-app-pub-3940256099942544/6300978111"
        }
        adContainerViewResult.addView(adView)
        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }

    private fun startCountdown(expiresAtTimeMillis: Long) {
        val now = System.currentTimeMillis()
        val timeToExpiry = expiresAtTimeMillis - now

        if (timeToExpiry <= 0) {
            tvExpiry.text = "Link Expired"
            return
        }

        countDownTimer = object : CountDownTimer(timeToExpiry, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / (1000 * 60 * 60)
                val mins = (millisUntilFinished / (1000 * 60)) % 60
                val secs = (millisUntilFinished / 1000) % 60
                tvExpiry.text = String.format(Locale.getDefault(), "Expires in: %02d:%02d:%02d", hours, mins, secs)
            }

            override fun onFinish() {
                tvExpiry.text = "Link Expired"
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        adView?.destroy()
        countDownTimer?.cancel()
    }
}
