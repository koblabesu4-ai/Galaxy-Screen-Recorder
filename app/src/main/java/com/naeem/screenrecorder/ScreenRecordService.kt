package com.naeem.screenrecorder

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ScreenRecordService : Service() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_PAUSE_RESUME = "PAUSE_RESUME"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val CHANNEL_ID = "recording_channel"
        const val NOTIF_ID = 1001

        var isRecording = false
        var isPaused = false
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null
    private var touchOverlay: TouchOverlayView? = null
    private var outputPath: String = ""

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopRecordingInternal()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (data != null) startRecording(resultCode, data)
            }
            ACTION_PAUSE_RESUME -> togglePauseResume()
            ACTION_STOP -> stopRecordingInternal()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Recording…"))

        val prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val quality = prefs.getString(MainActivity.KEY_QUALITY, "MEDIUM") ?: "MEDIUM"
        val soundMode = prefs.getString(MainActivity.KEY_SOUND, "SYSTEM") ?: "SYSTEM"
        val hideBtn = prefs.getBoolean(MainActivity.KEY_HIDE_BTN, false)

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(projectionCallback, null)

        val metrics = DisplayMetrics()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager!!.defaultDisplay.getRealMetrics(metrics)

        val (width, height, bitrate) = when (quality) {
            "HIGH" -> Triple(metrics.widthPixels, metrics.heightPixels, 12_000_000)
            "LOW" -> Triple(metrics.widthPixels / 2, metrics.heightPixels / 2, 3_000_000)
            else -> Triple(
                (metrics.widthPixels * 0.75).toInt(),
                (metrics.heightPixels * 0.75).toInt(),
                6_000_000
            )
        }

        val publicMovies = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "ScreenRecorder"
        )
        if (!publicMovies.exists()) publicMovies.mkdirs()
        val fileName = "Recording_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        outputPath = File(publicMovies, fileName).absolutePath

        mediaRecorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            if (soundMode != "MUTE") setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (soundMode != "MUTE") setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(width, height)
            setVideoEncodingBitRate(bitrate)
            setVideoFrameRate(30)
            setOutputFile(outputPath)
            prepare()
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder",
            width, height, metrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder!!.surface, null, null
        )

        mediaRecorder?.start()
        isRecording = true
        isPaused = false

        if (!hideBtn) showFloatingButton()
        showTouchOverlayIfEnabled()
    }

    private fun togglePauseResume() {
        if (!isRecording) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isPaused) {
                mediaRecorder?.resume()
                isPaused = false
                updateNotification("Recording…")
            } else {
                mediaRecorder?.pause()
                isPaused = true
                updateNotification("Paused")
            }
        }
    }

    private fun stopRecordingInternal() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            // ছোট রেকর্ডিং-এ stop-এর আগেই ফেইল করতে পারে, নিরাপদে ইগনোর
        }
        virtualDisplay?.release()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()

        isRecording = false
        isPaused = false
        removeFloatingButton()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showFloatingButton() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        val button = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(android.graphics.Color.parseColor("#88000000"))
            setOnClickListener { stopRecordingInternal() }
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(v, params)
                    true
                }
                else -> false
            }
        }

        floatingButton = button
        windowManager?.addView(button, params)
    }

    private fun showTouchOverlayIfEnabled() {
        val prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("record_touches", false)) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        touchOverlay = TouchOverlayView(this)
        windowManager?.addView(touchOverlay, params)
    }

    private fun removeFloatingButton() {
        floatingButton?.let { windowManager?.removeView(it) }
        floatingButton = null
        touchOverlay?.let { windowManager?.removeView(it) }
        touchOverlay = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseIntent = Intent(this, ScreenRecordService::class.java).apply { action = ACTION_PAUSE_RESUME }
        val pausePending = PendingIntent.getService(
            this, 1, pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recorder")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .addAction(0, "Pause/Resume", pausePending)
            .addAction(0, "Stop", stopPending)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingButton()
    }
}
