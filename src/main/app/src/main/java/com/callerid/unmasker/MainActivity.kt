package com.callerid.unmasker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.callerid.unmasker.models.CallEntry
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.appbar.MaterialToolbar

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: CallDatabaseHelper
    private lateinit var callLogReader: CallLogReader
    private lateinit var adapter: UnmaskedCallAdapter

    private lateinit var tvMonitorStatus: MaterialTextView
    private lateinit var tvUnmaskedCount: MaterialTextView
    private lateinit var tvLastUnmasked: MaterialTextView
    private lateinit var btnToggleMonitor: MaterialButton
    private lateinit var btnScanLog: MaterialButton
    private lateinit var rvCallEntries: RecyclerView

    private var isMonitoring = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Snackbar.make(findViewById(android.R.id.content), "All permissions granted", Snackbar.LENGTH_LONG).show()
        } else {
            val denied = permissions.filter { !it.value }.keys.joinToString(", ")
            Snackbar.make(findViewById(android.R.id.content), "Permissions denied: $denied", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = CallDatabaseHelper(this)
        callLogReader = CallLogReader(this)

        tvMonitorStatus = findViewById(R.id.tv_monitor_status)
        tvUnmaskedCount = findViewById(R.id.tv_unmasked_count)
        tvLastUnmasked = findViewById(R.id.tv_last_unmasked)
        btnToggleMonitor = findViewById(R.id.btn_toggle_monitor)
        btnScanLog = findViewById(R.id.btn_scan_log)
        rvCallEntries = findViewById(R.id.rv_call_entries)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        adapter = UnmaskedCallAdapter(this, emptyList())
        rvCallEntries.adapter = adapter

        btnToggleMonitor.setOnClickListener {
            if (isMonitoring) {
                stopMonitoring()
            } else {
                startMonitoring()
            }
        }

        btnScanLog.setOnClickListener {
            scanCallLog()
        }

        registerBroadcastReceiver()
        checkAndRequestPermissions()
        loadCallHistory()
    }

    private fun startMonitoring() {
        if (!hasRequiredPermissions()) {
            Snackbar.make(findViewById(android.R.id.content), "Please grant all permissions first", Snackbar.LENGTH_LONG).show()
            checkAndRequestPermissions()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Snackbar.make(findViewById(android.R.id.content), "Please enable notification permission", Snackbar.LENGTH_LONG).show()
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                return
            }
        }

        val intent = Intent(this, MonitorForegroundService::class.java)
        intent.action = MonitorForegroundService.ACTION_START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        isMonitoring = true
        updateUIForState()
        Snackbar.make(findViewById(android.R.id.content), "Monitoring started", Snackbar.LENGTH_LONG).show()
    }

    private fun stopMonitoring() {
        val intent = Intent(this, MonitorForegroundService::class.java)
        intent.action = MonitorForegroundService.ACTION_STOP
        stopService(intent)
        isMonitoring = false
        updateUIForState()
        Snackbar.make(findViewById(android.R.id.content), "Monitoring stopped", Snackbar.LENGTH_LONG).show()
    }

    private fun scanCallLog() {
        if (!hasRequiredPermissions()) {
            Snackbar.make(findViewById(android.R.id.content), "Need READ_CALL_LOG permission", Snackbar.LENGTH_LONG).show()
            return
        }

        btnScanLog.isEnabled = false
        btnScanLog.text = "Scanning..."

        try {
            val entries = callLogReader.readCallLog()
            var unmaskedCount = 0
            for (entry in entries) {
                if (entry.wasUnmasked) {
                    dbHelper.insertCall(entry)
                    unmaskedCount++
                }
            }
            loadCallHistory()
            Snackbar.make(findViewById(android.R.id.content), "Found $unmaskedCount unmasked calls", Snackbar.LENGTH_LONG).show()
            if (unmaskedCount > 0) {
                Toast.makeText(this, "Found $unmaskedCount unmasked caller(s)!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Snackbar.make(findViewById(android.R.id.content), "Error: ${e.message}", Snackbar.LENGTH_LONG).show()
        } finally {
            btnScanLog.isEnabled = true
            btnScanLog.text = "Scan Call Log Now"
        }
    }

    private fun loadCallHistory() {
        val allCalls = dbHelper.getAllCalls()
        adapter.updateData(allCalls)
        val unmasked = allCalls.filter { it.wasUnmasked }
        tvUnmaskedCount.text = "Unmasked calls: ${unmasked.size}"
        if (unmasked.isNotEmpty()) {
            val last = unmasked.first()
            tvLastUnmasked.text = "Last unmasked: ${last.realNumber ?: last.displayNumber}"
        }
    }

    private fun updateUIForState() {
        if (isMonitoring) {
            tvMonitorStatus.text = "\u25CF Monitoring Active"
            tvMonitorStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnToggleMonitor.text = "Stop Monitoring"
        } else {
            tvMonitorStatus.text = "\u25CF Monitoring Stopped"
            tvMonitorStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnToggleMonitor.text = "Start Monitoring"
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_CALL_LOG)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_OWN_CALLS) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.MANAGE_OWN_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(CallMonitorService.ACTION_CALL_UNMASKED)
            addAction("CALL_LOG_SCANNED")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(callReceiver, filter)
    }

    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                CallMonitorService.ACTION_CALL_UNMASKED -> {
                    val realNumber = intent.getStringExtra("real_number")
                    val wasUnmasked = intent.getBooleanExtra("was_unmasked", false)
                    if (wasUnmasked && realNumber != null) {
                        Toast.makeText(context, "\uD83D\uDD13 UNMASKED: $realNumber", Toast.LENGTH_LONG).show()
                    }
                    loadCallHistory()
                }
                "CALL_LOG_SCANNED" -> {
                    loadCallHistory()
                }
            }
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(callReceiver)
        super.onDestroy()
    }
}
