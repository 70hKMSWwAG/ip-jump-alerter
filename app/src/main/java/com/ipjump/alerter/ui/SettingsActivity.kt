package com.ipjump.alerter.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.ipjump.alerter.R
import com.ipjump.alerter.data.Prefs
import com.ipjump.alerter.databinding.ActivitySettingsBinding
import com.ipjump.alerter.monitor.IpChecker
import com.ipjump.alerter.monitor.MonitorScheduler
import com.ipjump.alerter.network.IpLookup
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)
        bindValues()
        bindEvents()
    }

    private fun bindValues() {
        binding.intervalInput.setText(prefs.intervalSeconds.toString())
        binding.darkModeSwitch.isChecked = prefs.darkMode
        binding.ignoreVpnSwitch.isChecked = prefs.ignoreVpn
        binding.wifiCellularOnlySwitch.isChecked = prefs.wifiCellularOnly
        binding.quietHoursSwitch.isChecked = prefs.quietHoursEnabled
        binding.quietStart.setText(prefs.quietStartHour.toString())
        binding.quietEnd.setText(prefs.quietEndHour.toString())
        binding.manualBaseline.setText(prefs.baselineIp)
    }

    private fun bindEvents() {
        binding.saveIntervalButton.setOnClickListener { saveInterval() }
        binding.darkModeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.darkMode = checked
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        binding.ignoreVpnSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.ignoreVpn = checked
            toastSaved()
        }
        binding.wifiCellularOnlySwitch.setOnCheckedChangeListener { _, checked ->
            prefs.wifiCellularOnly = checked
            toastSaved()
        }
        binding.quietHoursSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.quietHoursEnabled = checked
            persistQuietHours()
            toastSaved()
        }
        binding.quietStart.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) persistQuietHours()
        }
        binding.quietEnd.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) persistQuietHours()
        }
        binding.saveBaselineButton.setOnClickListener {
            val value = binding.manualBaseline.text?.toString()?.trim().orEmpty()
            if (!IpLookup.looksLikeIp(value)) {
                Toast.makeText(this, R.string.invalid_ip, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.baselineIp = value
            prefs.lastKnownIp = value
            Toast.makeText(this, R.string.baseline_updated, Toast.LENGTH_SHORT).show()
        }
        binding.resetBaselineButton.setOnClickListener {
            lifecycleScope.launch {
                val result = IpChecker.check(this@SettingsActivity, IpChecker.REASON_PERIODIC)
                val ip = result.ip.ifBlank { prefs.lastKnownIp }
                if (ip.isNotBlank()) {
                    prefs.baselineIp = ip
                    prefs.lastKnownIp = ip
                    runOnUiThread {
                        binding.manualBaseline.setText(ip)
                        Toast.makeText(this@SettingsActivity, R.string.baseline_updated, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun saveInterval() {
        val value = binding.intervalInput.text?.toString()?.trim()?.toIntOrNull()
        if (value == null || value < Prefs.MIN_INTERVAL || value > Prefs.MAX_INTERVAL) {
            Toast.makeText(this, R.string.invalid_interval, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.intervalSeconds = value
        binding.intervalInput.setText(prefs.intervalSeconds.toString())
        MonitorScheduler.restart(this)
        toastSaved()
    }

    private fun persistQuietHours() {
        prefs.quietStartHour = binding.quietStart.text?.toString()?.toIntOrNull()?.coerceIn(0, 23) ?: 2
        prefs.quietEndHour = binding.quietEnd.text?.toString()?.toIntOrNull()?.coerceIn(0, 23) ?: 6
    }

    private fun toastSaved() {
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }
}
