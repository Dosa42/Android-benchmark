package com.example

import android.app.Application
import com.example.data.BenchmarkDatabase
import com.example.data.BenchmarkRepository

class BenchmarkApplication : Application() {
    val database by lazy { BenchmarkDatabase.getDatabase(this) }
    val repository by lazy { BenchmarkRepository(database.benchmarkDao()) }

    override fun onCreate() {
        super.onCreate()
    }
}
