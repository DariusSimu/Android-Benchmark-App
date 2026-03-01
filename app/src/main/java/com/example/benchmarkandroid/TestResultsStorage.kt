package com.example.benchmarkandroid

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class TestResult(
    val timestamp: Long,
    val date: String,
    val cpuSingleScore: Int?,
    val cpuSingleTime: Double?,
    val cpuMultiScore: Int?,
    val cpuMultiTime: Double?,
    val cpuMultiThreads: Int?,
    val gpuScore: Int?,
    val gpuAvgFps: Double?,
    val gpuP95Fps: Double?,
    val gpuP99Fps: Double?,
    val memoryScore: Int?,
    val memoryBandwidth: Double?,
    val storageScore: Int?,
    val storageThroughput: Double?,
    val overallScore: Int?,
    val allTestsRun: Boolean
)

object TestResultsStorage {
    private const val PREFS_NAME = "benchmark_results"
    private const val KEY_RESULTS = "test_results"
    private const val MAX_RESULTS = 5

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    fun saveResult(context: Context, result: TestResult) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val results = getResults(context).toMutableList()

        results.add(0, result)

        if (results.size > MAX_RESULTS) {
            results.subList(MAX_RESULTS, results.size).clear()
        }

        val json = gson.toJson(results)
        prefs.edit().putString(KEY_RESULTS, json).apply()
    }

    fun getResults(context: Context): List<TestResult> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RESULTS, null) ?: return emptyList()

        val type = object : TypeToken<List<TestResult>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun hasResults(context: Context): Boolean {
        return getResults(context).isNotEmpty()
    }

    fun getAverageOverallScore(context: Context): Int? {
        val results = getResults(context)
        val overallScores = results.mapNotNull { it.overallScore }

        return if (overallScores.isNotEmpty()) {
            overallScores.average().toInt()
        } else {
            null
        }
    }

    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
}