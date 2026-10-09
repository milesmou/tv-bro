package com.phlox.tvwebbrowser.model.dao

import androidx.room.*
import com.phlox.tvwebbrowser.model.HostConfig

@Dao
interface HostsDao {
    @Query("SELECT * FROM hosts WHERE popup_block_level IS NOT NULL")
    suspend fun popupOverrides(): List<HostConfig>

    @Query("SELECT * FROM hosts WHERE host_name = :name")
    fun findByHostName(name: String): HostConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HostConfig): Long

    @Update
    suspend fun update(item: HostConfig)
}
