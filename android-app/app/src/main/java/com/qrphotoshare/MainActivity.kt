package com.qrphotoshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.MobileAds
import com.qrphotoshare.api.ApiClient
import com.qrphotoshare.api.UriRequestBody
import com.qrphotoshare.api.ProgressRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import android.widget.FrameLayout
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnSelect: Button
    private lateinit var btnUpload: Button
    private lateinit var tvSelectedInfo: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var adContainerView: FrameLayout
    private lateinit var adContainerViewMiddle: FrameLayout
    private lateinit var adContainerViewTop: FrameLayout

    private var selectedUris = listOf<Uri>()
    private val MAX_SIZE_BYTES = 150L * 1024 * 1024 // 150 MB
    private var adView: AdView? = null
    private var interstitialAd: InterstitialAd? = null
    private var autoShareAction: String? = null // "WHATSAPP", "INSTAGRAM", "TELEGRAM", "MESSENGER", "SNAPCHAT", "LINK", "QR", "COPY", null

    private val CHANNEL_ID = "upload_notifications"
    private val NOTIFICATION_ID = 101

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(this, "Permissions are required to share photos.", Toast.LENGTH_LONG).show()
        }
    }

    // Photo picker launcher
    private val pickImages = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            handleSelectedUris(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AdMob
        MobileAds.initialize(this) {}
        // loadBannerAd()
        // loadInterstitialAd()
        checkAndRequestPermissions()
        createNotificationChannel()

        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        btnSelect = findViewById(R.id.btnSelect)
        btnUpload = findViewById(R.id.btnUpload)
        tvSelectedInfo = findViewById(R.id.tvSelectedInfo)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)

        btnSelect.setOnClickListener {
            // Launch photo picker
            pickImages.launch("image/*")
        }

        btnUpload.setOnClickListener {
            if (selectedUris.isNotEmpty()) {
                showAutoShareSelection()
            }
        }
    }

    private fun showAutoShareSelection() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_auto_share_sheet, null)
        dialog.setContentView(view)

        // Social Icons
        view.findViewById<android.widget.ImageButton>(R.id.ibAutoWhatsApp).setOnClickListener {
            autoShareAction = "WHATSAPP"
            dialog.dismiss()
            uploadPhotos()
        }
        view.findViewById<android.widget.ImageButton>(R.id.ibAutoInstagram).setOnClickListener {
            autoShareAction = "INSTAGRAM"
            dialog.dismiss()
            uploadPhotos()
        }
        view.findViewById<android.widget.ImageButton>(R.id.ibAutoTelegram).setOnClickListener {
            autoShareAction = "TELEGRAM"
            dialog.dismiss()
            uploadPhotos()
        }
        view.findViewById<android.widget.ImageButton>(R.id.ibAutoMessenger).setOnClickListener {
            autoShareAction = "MESSENGER"
            dialog.dismiss()
            uploadPhotos()
        }
        view.findViewById<android.widget.ImageButton>(R.id.ibAutoSnapchat).setOnClickListener {
            autoShareAction = "SNAPCHAT"
            dialog.dismiss()
            uploadPhotos()
        }

        // Action Buttons
        view.findViewById<android.widget.Button>(R.id.btnAutoShareLink).setOnClickListener {
            autoShareAction = "LINK"
            dialog.dismiss()
            uploadPhotos()
        }
        view.findViewById<android.widget.Button>(R.id.btnAutoShareQr).setOnClickListener {
            autoShareAction = "QR"
            dialog.dismiss()
            uploadPhotos()
        }
        view.findViewById<android.widget.Button>(R.id.btnAutoCopyLink).setOnClickListener {
            autoShareAction = "COPY"
            dialog.dismiss()
            uploadPhotos()
        }
        view.findViewById<android.widget.Button>(R.id.btnNoAutoShare).setOnClickListener {
            autoShareAction = null
            dialog.dismiss()
            uploadPhotos()
        }

        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                shareApp()
                true
            }
            R.id.action_rate -> {
                rateApp()
                true
            }
            R.id.action_privacy -> {
                openPrivacyPolicy()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out QR PhotoShare to share your photos instantly via QR: https://play.google.com/store/apps/details?id=$packageName")
        }
        startActivity(Intent.createChooser(shareIntent, "Share App via..."))
    }

    private fun rateApp() {
        val uri = Uri.parse("market://details?id=$packageName")
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(goToMarket)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }

    private fun openPrivacyPolicy() {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qr-photoshare.onrender.com/privacy.html"))
        startActivity(browserIntent)
    }

    private fun loadBannerAd() {
        adContainerView = findViewById(R.id.adContainerView)
        adContainerViewMiddle = findViewById(R.id.adContainerViewMiddle)
        adContainerViewTop = findViewById(R.id.adContainerViewTop)

        // Top Ad
        val adViewTop = AdView(this).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = "ca-app-pub-8703883257933057/2072537369"
        }
        adContainerViewTop.addView(adViewTop)
        adViewTop.loadAd(AdRequest.Builder().build())

        // Bottom Ad
        val adViewBottom = AdView(this).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = "ca-app-pub-8703883257933057/2072537369"
        }
        adContainerView.addView(adViewBottom)
        adViewBottom.loadAd(AdRequest.Builder().build())

        // Middle Ad
        val adViewMiddle = AdView(this).apply {
            setAdSize(AdSize.MEDIUM_RECTANGLE)
            adUnitId = "ca-app-pub-8703883257933057/2072537369"
        }
        adContainerViewMiddle.addView(adViewMiddle)
        adViewMiddle.loadAd(AdRequest.Builder().build())
        
        this.adView = adViewBottom // Keep reference for cleanup if needed
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        // Production Interstitial ID
        InterstitialAd.load(this, "ca-app-pub-8703883257933057/9244779946", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                interstitialAd = null
            }
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
            }
        })
    }

    private fun handleSelectedUris(uris: List<Uri>) {
        var totalSize = 0L
        for (uri in uris) {
            totalSize += getFileSize(uri)
        }

        if (totalSize > MAX_SIZE_BYTES) {
            Toast.makeText(this, "Total size exceeds 100MB!", Toast.LENGTH_LONG).show()
            selectedUris = emptyList()
            tvSelectedInfo.text = "Selected size too large."
            btnUpload.isEnabled = false
        } else {
            selectedUris = uris
            tvSelectedInfo.text = "${uris.size} photos selected (${formatBytes(totalSize)})"
            btnUpload.isEnabled = true
        }
    }

    private fun getFileSize(uri: Uri): Long {
        var size: Long = 0
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return size
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.2f MB", mb)
    }

    private fun getFileName(uri: Uri): String {
        var name = "photo.jpg"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name
    }

    private fun uploadPhotos() {
        if (selectedUris.isEmpty()) return

        // 1. Calculate total size
        var totalSizeBytes = 0L
        for (uri in selectedUris) {
            totalSizeBytes += getFileSize(uri)
        }

        // 2. Setup Progress UI
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = false
        progressBar.max = 100
        progressBar.progress = 0
        tvProgress.visibility = View.VISIBLE
        tvProgress.text = "Starting upload... 0%"
        btnSelect.isEnabled = false
        btnUpload.isEnabled = false

        var totalBytesWritten = 0L

        val parts = mutableListOf<MultipartBody.Part>()
        for (uri in selectedUris) {
            val fileName = getFileName(uri)
            val uriRequestBody = UriRequestBody(contentResolver, uri)
            
            // Wrap in ProgressRequestBody
            val progressRequestBody = ProgressRequestBody(uriRequestBody) { bytesWritten ->
                totalBytesWritten += bytesWritten
                val progress = if (totalSizeBytes > 0) {
                    (totalBytesWritten * 100 / totalSizeBytes).toInt()
                } else 0
                
                runOnUiThread {
                    progressBar.progress = progress
                    tvProgress.text = "Uploading photos... $progress%"
                }
            }
            
            parts.add(MultipartBody.Part.createFormData("photos", fileName, progressRequestBody))
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.apiService.uploadPhotos(parts)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.success) {
                            showUploadSuccessNotification(body.downloadUrl ?: "")
                            navigateToResult(body.downloadUrl, body.qrCode, body.expiresAt ?: "")
                        } else {
                            showError("Upload failed: ${body.error}")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        showError("Server error: ${response.code()} \n$errorBody")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Network error: ${e.localizedMessage}")
                }
            }
        }
    }
    

    private fun navigateToResult(downloadUrl: String?, qrCode: String?, expiresAt: String) {
        
        fun doNav() {
            progressBar.visibility = View.GONE
            tvProgress.visibility = View.GONE
            btnSelect.isEnabled = true
            
            // Clear selections
            selectedUris = emptyList()
            tvSelectedInfo.text = "No photos selected"
            btnUpload.isEnabled = false

            val intent = Intent(this@MainActivity, ResultActivity::class.java).apply {
                putExtra("EXTRA_DOWNLOAD_URL", downloadUrl)
                putExtra("EXTRA_QR_CODE", qrCode)
                putExtra("EXTRA_EXPIRES_AT", expiresAt)
                putExtra("EXTRA_AUTO_SHARE_ACTION", autoShareAction)
            }
            startActivity(intent)
        }

        // Show ad if loaded before navigating
        if (interstitialAd != null) {
            interstitialAd?.show(this)
            // Reload for next time
            loadInterstitialAd()
            doNav() 
        } else {
            doNav()
        }
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        tvProgress.visibility = View.GONE
        btnSelect.isEnabled = true
        btnUpload.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // For downloading QR code (saving to gallery)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Android 9 and below need WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Upload Status"
            val descriptionText = "Notifications for photo upload results"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showUploadSuccessNotification(downloadUrl: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Upload Successful!")
            .setContentText("Your photos are ready to share. QR code generated.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(this)) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33) {
                    notify(NOTIFICATION_ID, builder.build())
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
