package com.ipjump.alerter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IpChangeDao {
    @Query("SELECT * FROM ip_changes ORDER BY changedAt DESC")
    fun observeAll(): Flow<List<IpChangeRecord>>

    @Query("SELECT * FROM ip_changes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): IpChangeRecord?

    @Query("SELECT * FROM ip_changes ORDER BY changedAt DESC LIMIT 1")
    suspend fun latest(): IpChangeRecord?

    @Insert
    suspend fun insert(record: IpChangeRecord): Long
}
