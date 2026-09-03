package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BenchmarkDao {
    @Query("SELECT * FROM benchmark_runs ORDER BY timestamp DESC")
    fun getAllRuns(): Flow<List<BenchmarkRunEntity>>

    @Query("SELECT * FROM benchmark_runs WHERE workloadKey = :workloadKey ORDER BY timestamp DESC")
    fun getRunsForWorkload(workloadKey: String): Flow<List<BenchmarkRunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: BenchmarkRunEntity): Long

    @Query("DELETE FROM benchmark_runs WHERE id = :id")
    suspend fun deleteRunById(id: Long)

    @Query("DELETE FROM benchmark_runs")
    suspend fun clearAllRuns()
}
