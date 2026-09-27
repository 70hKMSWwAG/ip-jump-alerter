package com.ipjump.alerter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ip_changes")
data class IpChangeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val oldIp: String,
    val newIp: String,
    val location: String,
    val reason: String,
    val networkType: String,
    val changedAt: Long
)
