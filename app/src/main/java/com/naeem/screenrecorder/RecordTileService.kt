package com.naeem.screenrecorder

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class RecordTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (ScreenRecordService.isRecording) {
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            val i = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("action", "settings")
            }
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, i,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        updateTile()
    }

    fun updateTile() {
        qsTile?.let { tile ->
            if (ScreenRecordService.isRecording) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = if (ScreenRecordService.isPaused) "Paused" else "Recording…"
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Screen Record"
            }
            tile.updateTile()
        }
    }
}
