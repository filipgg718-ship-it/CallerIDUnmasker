package com.callerid.unmasker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class MonitorForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "call_monitor_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.callerid.unmasker.START_MONITOR"
        const val ACTION_STOP = "com.callerid.unmasker.STOP_MONITOR"
    }

    private val dbHelper by lazy { CallDatabaseHelper(this) }
    private val callLogReader by lazy { CallLogReader(this) }
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Call Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitoring incoming calls for unmasked caller IDs"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    isRunning = true
                    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("CallerID Unmasker")
                        .setContentText("Monitoring for #31# calls...")
                        .setSmallIcon(android.R.drawable.ic_menu_call)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setOngoing(true)
                        .build()
                    startForeground(NOTIFICATION_ID, notification)
                    Log.d("MonitorService", "Started")
                    scanCallLog()
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun scanCallLog() {
        try {
            val entries = callLogReader.readCallLog()
            val unmasked = entries.filter { it.wasUnmasked }
            if (unmasked.isNotEmpty()) {
                Log.i("MonitorService", "Found ${unmasked.size} unmasked calls")
                for (entry in unmasked) {
                    dbHelper.insertCall(entry)
                }
                val intent = Intent("CALL_LOG_SCANNED")
                intent.putExtra("unmasked_count", unmasked.size)
                sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e("MonitorService", "Error scanning", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { isRunning = false; super.onDestroy() }
}
