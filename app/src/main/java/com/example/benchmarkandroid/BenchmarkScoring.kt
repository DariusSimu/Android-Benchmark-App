package com.example.benchmarkandroid

import kotlin.math.max
import kotlin.math.min

object BenchmarkScoring {

    fun scoreCpuSingleCore(timeSeconds: Double): Int {
        val baseTime = 2.0
        val score = 1000.0 * (baseTime / timeSeconds)
        return min(1000, max(0, score.toInt()))
    }


    fun scoreCpuMultiCore(timeSeconds: Double): Int {
        val baseTime = 2.0
        val score = 1000.0 * (baseTime / timeSeconds)
        return min(1000, max(0, score.toInt()))
    }

    fun scoreMemory(bandwidthGBps: Double): Int {
        val maxBandwidth = 50.0
        val score = 1000.0 * (bandwidthGBps / maxBandwidth)
        return min(1000, max(0, score.toInt()))
    }

    fun scoreStorage(throughputMBps: Double): Int {
        val maxThroughput = 3000.0
        val score = 1000.0 * (throughputMBps / maxThroughput)
        return min(1000, max(0, score.toInt()))
    }

    fun scoreGpu(averageFps: Double): Int {
        val targetFps = 200.0
        val score = 1000.0 * (averageFps / targetFps)
        return min(1000, max(0, score.toInt()))
    }

    fun calculateOverallScore(scores: List<Int>): Int {
        if (scores.isEmpty()) return 0
        return scores.average().toInt()
    }
}