package com.ipjump.alerter.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ipjump.alerter.data.AppDatabase
import com.ipjump.alerter.data.IpChangeRecord
import com.ipjump.alerter.databinding.ActivityHistoryBinding
import com.ipjump.alerter.databinding.ItemHistoryBinding
import com.ipjump.alerter.monitor.IpChecker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private val adapter = HistoryAdapter { record ->
        startActivity(
            Intent(this, HistoryDetailActivity::class.java)
                .putExtra(HistoryDetailActivity.EXTRA_ID, record.id)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = adapter
        binding.historyList.setHasFixedSize(true)
        lifecycleScope.launch {
            AppDatabase.get(this@HistoryActivity).ipChangeDao().observeAll().collectLatest { items ->
                adapter.submitList(items)
                binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}

class HistoryAdapter(
    private val onClick: (IpChangeRecord) -> Unit
) : ListAdapter<IpChangeRecord, HistoryAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class Holder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: IpChangeRecord, onClick: (IpChangeRecord) -> Unit) {
            binding.itemTime.text = IpChecker.formatTime(record.changedAt)
            binding.itemIps.text = "${record.oldIp}  →  ${record.newIp}"
            binding.itemMeta.text = listOf(
                IpChecker.reasonLabel(record.reason),
                record.networkType,
                record.location
            ).filter { it.isNotBlank() }.joinToString(" · ")
            binding.root.setOnClickListener { onClick(record) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<IpChangeRecord>() {
        override fun areItemsTheSame(oldItem: IpChangeRecord, newItem: IpChangeRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: IpChangeRecord, newItem: IpChangeRecord): Boolean {
            return oldItem == newItem
        }
    }
}
