package com.ipjump.alerter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ipjump.alerter.R
import com.ipjump.alerter.data.Prefs
import com.ipjump.alerter.databinding.ActivityMainBinding
import com.ipjump.alerter.monitor.IpChecker
import com.ipjump.alerter.monitor.MonitorScheduler
import com.ipjump.alerter.monitor.NetworkSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var checkJob: Job? = null
    private var lastUiKey: String? = null

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs.get(this)
        requestNotifyPermission()
        bindClicks()
        refreshUi()
        if (prefs.lastKnownIp.isBlank()) {
            runCheck(showChecking = true)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        showBatteryHintIfNeeded()
    }

    private fun bindClicks() {
        binding.toggleButton.setOnClickListener {
            if (prefs.monitoringEnabled) {
                MonitorScheduler.stop(this)
            } else {
                requestNotifyPermission()
                MonitorScheduler.start(this)
            }
            refreshUi()
        }
        binding.checkNowButton.setOnClickListener {
            runCheck(showChecking = true)
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.batteryButton.setOnClickListener { openBatterySettings() }
    }

    private fun runCheck(showChecking: Boolean) {
        if (checkJob?.isActive == true) return
        if (showChecking) {
            binding.currentIp.text = getString(R.string.checking)
        }
        binding.checkNowButton.isEnabled = false
        checkJob = lifecycleScope.launch {
            try {
                IpChecker.check(this@MainActivity, IpChecker.REASON_MANUAL)
                refreshUi()
            } finally {
                binding.checkNowButton.isEnabled = true
            }
        }
    }

    private fun refreshUi() {
        val running = prefs.monitoringEnabled
        val network = NetworkSnapshot.capture(this).label()
        val key = "${prefs.lastKnownIp}|${prefs.baselineIp}|${prefs.lastLocation}|$network|${prefs.lastChangeAt}|$running"
        if (key == lastUiKey) return
        lastUiKey = key
        binding.currentIp.text = prefs.lastKnownIp.ifBlank { getString(R.string.unknown) }
        binding.baselineIp.text = prefs.baselineIp.ifBlank { getString(R.string.unknown) }
        binding.locationText.text = prefs.lastLocation.ifBlank { getString(R.string.unknown) }
        binding.networkType.text = network
        binding.lastChange.text = IpChecker.formatTime(prefs.lastChangeAt).replace(" ", "\n")
        binding.statusChip.text = getString(if (running) R.string.status_running else R.string.status_stopped)
        binding.statusChip.setBackgroundResource(
            if (running) R.drawable.bg_chip_ok else R.drawable.bg_chip_stop
        )
        binding.statusChip.setTextColor(
            ContextCompat.getColor(this, if (running) R.color.ok else R.color.danger)
        )
        binding.toggleButton.text = getString(if (running) R.string.stop_monitor else R.string.start_monitor)
    }

    private fun requestNotifyPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun showBatteryHintIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            binding.batteryHint.visibility = View.GONE
            binding.batteryButton.visibility = View.GONE
            return
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
        val visible = if (ignoring) View.GONE else View.VISIBLE
        binding.batteryHint.visibility = visible
        binding.batteryButton.visibility = visible
    }

    private fun openBatterySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
