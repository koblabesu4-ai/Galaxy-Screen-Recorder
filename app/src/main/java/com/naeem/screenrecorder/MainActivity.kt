package com.naeem.screenrecorder

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle

class MainActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    companion object {
        const val REQ_CODE_PROJECTION = 1001
        const val PREFS = "recorder_prefs"
        const val KEY_QUALITY = "quality"
        const val KEY_SOUND = "sound"
        const val KEY_HIDE_BTN = "hide_button"
    }

    private val notifPermLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val action = intent?.getStringExtra("action")
        when (action) {
            "settings" -> showQualityDialog()
            "start" -> requestProjection()
            else -> requestProjection()
        }
    }

    private fun requestProjection() {
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQ_CODE_PROJECTION
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CODE_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_START
                    putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
                }
                startForegroundService(serviceIntent)
            }
            finish()
        }
    }

    private fun showQualityDialog() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val qualities = arrayOf("HIGH", "MEDIUM", "LOW")

        AlertDialog.Builder(this)
            .setTitle("Video Quality")
            .setSingleChoiceItems(
                qualities,
                qualities.indexOf(prefs.getString(KEY_QUALITY, "MEDIUM"))
            ) { d, which ->
                prefs.edit().putString(KEY_QUALITY, qualities[which]).apply()
                d.dismiss()
                showSoundDialog(prefs, arrayOf("MUTE", "SYSTEM", "SYSTEM_MIC"))
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showSoundDialog(prefs: android.content.SharedPreferences, sounds: Array<String>) {
        AlertDialog.Builder(this)
            .setTitle("Record Sound")
            .setSingleChoiceItems(
                sounds,
                sounds.indexOf(prefs.getString(KEY_SOUND, "SYSTEM"))
            ) { d, which ->
                prefs.edit().putString(KEY_SOUND, sounds[which]).apply()
                d.dismiss()
                showTogglesDialog(prefs)
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showTogglesDialog(prefs: android.content.SharedPreferences) {
        val items = arrayOf("Record touch interactions", "Hide floating button")
        val checked = booleanArrayOf(
            prefs.getBoolean("record_touches", false),
            prefs.getBoolean(KEY_HIDE_BTN, false)
        )

        AlertDialog.Builder(this)
            .setTitle("Options")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Start Recording") { d, _ ->
                prefs.edit()
                    .putBoolean("record_touches", checked[0])
                    .putBoolean(KEY_HIDE_BTN, checked[1])
                    .apply()
                d.dismiss()
                requestProjection()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
