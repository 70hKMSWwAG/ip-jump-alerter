package com.ipjump.alerter.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ipjump.alerter.data.AppDatabase
import com.ipjump.alerter.databinding.ActivityHistoryDetailBinding
import com.ipjump.alerter.monitor.IpChecker
import kotlinx.coroutines.launch

class HistoryDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0L) {
            finish()
            return
        }
        lifecycleScope.launch {
            val record = AppDatabase.get(this@HistoryDetailActivity).ipChangeDao().getById(id) ?: run {
                finish()
                return@launch
            }
            binding.detailTime.text = IpChecker.formatTime(record.changedAt)
            binding.detailOldIp.text = record.oldIp
            binding.detailNewIp.text = record.newIp
            binding.detailLocation.text = record.location.ifBlank { "未知" }
            binding.detailReason.text = "${IpChecker.reasonLabel(record.reason)} · ${record.networkType}"
        }
    }

    companion object {
        const val EXTRA_ID = "record_id"
    }
}
