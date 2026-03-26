package com.example.bottomsheetdialogexample

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.bottomsheetdialogexample.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentDownloadId: Long = -1

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == currentDownloadId) {
                onDownloadComplete()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        requestPermissionsIfNeeded()
        registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                RECEIVER_NOT_EXPORTED else 0
        )

        // Handle shared intent
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {}
    }

    private fun setupUI() {
        // Download button
        binding.btnDownload.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                processUrl(url)
            } else {
                Toast.makeText(this, "URL을 입력해주세요", Toast.LENGTH_SHORT).show()
            }
        }

        // Paste button
        binding.btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString() ?: ""
                binding.etUrl.setText(pastedText)

                // Auto-process if it contains an Instagram URL
                val igUrl = InstagramExtractor.extractInstagramUrl(pastedText)
                if (igUrl != null) {
                    processUrl(pastedText)
                }
            } else {
                Toast.makeText(this, "클립보드가 비어있습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            binding.etUrl.setText(sharedText)
            processUrl(sharedText)
        }
    }

    private fun processUrl(text: String) {
        val instagramUrl = InstagramExtractor.extractInstagramUrl(text)
        if (instagramUrl == null) {
            showStatus("인스타그램 링크를 찾을 수 없습니다", isError = true)
            return
        }

        showStatus("동영상 정보를 가져오는 중...")
        setLoading(true)

        lifecycleScope.launch {
            val result = InstagramExtractor.getVideoDownloadUrl(instagramUrl)
            result.onSuccess { videoUrl ->
                startDownload(videoUrl, instagramUrl)
            }.onFailure { error ->
                showStatus(error.message ?: "다운로드 실패", isError = true)
                setLoading(false)
            }
        }
    }

    private fun startDownload(videoUrl: String, originalUrl: String) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Reels_${timestamp}.mp4"

            val request = DownloadManager.Request(Uri.parse(videoUrl))
                .setTitle("릴스 다운로드 중")
                .setDescription(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "ReelsDownloader/$fileName")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            currentDownloadId = downloadManager.enqueue(request)

            showStatus("다운로드 시작! 알림바에서 진행률을 확인하세요")
            binding.etUrl.text?.clear()
        } catch (e: Exception) {
            showStatus("다운로드 시작 실패: ${e.message}", isError = true)
        } finally {
            setLoading(false)
        }
    }

    private fun onDownloadComplete() {
        showStatus("다운로드 완료! Movies/ReelsDownloader 폴더를 확인하세요")
        Toast.makeText(this, "릴스 다운로드 완료!", Toast.LENGTH_LONG).show()
    }

    private fun showStatus(message: String, isError: Boolean = false) {
        binding.tvStatus.text = message
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.error_red else R.color.success_green
            )
        )
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnDownload.isEnabled = !loading
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }
}
