package com.example.benchmarkandroid

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

data class TestRun(
    val cpuSingleTimes: List<Double?>,
    val cpuMultiTimes: List<Double?>,
    val gpuAvgFps: List<Double?>,
    val memoryBandwidths: List<Double?>,
    val storageThroughputs: List<Double?>
)

class TestsFragment : Fragment() {
    companion object {
        init {
            System.loadLibrary("benchmarkandroid")
        }
        private const val PREFS_NAME = "latest_test_run"
        private const val KEY_TEST_RUN = "test_run_data"
    }

    external fun computeSinglecoreBenchmark(): Double
    external fun computeMulticoreBenchmark(): Double
    external fun getCoreCount(): Int
    external fun computeMemoryBenchmark(): Double
    external fun computeStorageBenchmark(filePath: String): Double

    private lateinit var progressDialog: AlertDialog
    private lateinit var dialogMessage: TextView

    private fun showProgressDialog(tests: List<String>): AlertDialog {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
        builder.setView(dialogView)
        builder.setCancelable(false)
        builder.setPositiveButton("OK", null)

        dialogMessage = dialogView.findViewById(R.id.progress_message)

        val message = tests.joinToString("\n\n") { "$it: Running..." }
        dialogMessage.text = message

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false

        return dialog
    }

    private fun updateProgress(completedTests: Map<String, String>) {
        requireActivity().runOnUiThread {
            dialogMessage.text = completedTests.entries.joinToString("\n\n") { (test, result) ->
                "$test: $result"
            }
        }
    }

    private fun saveLatestTestRun(testRun: TestRun) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val json = gson.toJson(testRun)
        prefs.edit().putString(KEY_TEST_RUN, json).apply()
    }

    private fun getLatestTestRun(): TestRun? {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TEST_RUN, null) ?: return null
        val gson = com.google.gson.Gson()
        return gson.fromJson(json, TestRun::class.java)
    }

    private fun showGraphs() {
        val testRun = getLatestTestRun()
        if (testRun == null) {
            AlertDialog.Builder(requireContext())
                .setTitle("No Data")
                .setMessage("No test run data available. Please run tests first.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Create and show graphs
        val graphsFragment = GraphsFragment.newInstance(testRun)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, graphsFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showPreviousResults() {
        val results = TestResultsStorage.getResults(requireContext())

        if (results.isEmpty()) {
            return
        }

        val averageScore = TestResultsStorage.getAverageOverallScore(requireContext())

        val message = buildString {
            averageScore?.let { score ->
                val borderLength = 31
                val border = "=".repeat(borderLength)
                val scoreString = score.toString()
                val centerLine: (String) -> String = { text ->
                    val padding = borderLength - text.length
                    val leftPadding = padding / 2
                    val rightPadding = padding - leftPadding
                    " ".repeat(leftPadding) + text + " ".repeat(rightPadding)
                }
                append("$border\n")
                append(centerLine("") + "\n")
                append(centerLine("AVERAGE OVERALL SCORE") + " $scoreString\n")
                append(centerLine("") + "\n")
                append("$border\n\n")
            }

            results.forEachIndexed { index, result ->
                append("═══ Test ${index + 1} ═══\n")
                append("${result.date}\n\n")

                result.cpuSingleScore?.let {
                    append("✓ CPU Single-Core: ${String.format("%.3f", result.cpuSingleTime)} seconds\n")
                    append("   Score: $it\n\n")
                }

                result.cpuMultiScore?.let {
                    append("✓ CPU Multi-Core: ${String.format("%.3f", result.cpuMultiTime)} seconds (${result.cpuMultiThreads} threads)\n")
                    append("   Score: $it\n\n")
                }

                result.gpuScore?.let {
                    append("✓ GPU: Avg ${String.format("%.1f", result.gpuAvgFps)} FPS\n")
                    append("   P95: ${String.format("%.1f", result.gpuP95Fps)}, P99: ${String.format("%.1f", result.gpuP99Fps)}\n")
                    append("   Score: $it\n\n")
                }

                result.memoryScore?.let {
                    append("✓ Memory: ${String.format("%.2f", result.memoryBandwidth)} GB/s\n")
                    append("   Score: $it\n\n")
                }

                result.storageScore?.let {
                    append("✓ Storage: ${String.format("%.2f", result.storageThroughput)} MB/s\n")
                    append("   Score: $it\n\n")
                }

                result.overallScore?.let {
                    append("Overall Score: $it\n")
                } ?: run {
                    append("Overall Score: N/A (incomplete test)\n")
                }

                if (index < results.size - 1) {
                    append("\n")
                }
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Previous Test Results")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tests, container, false)

        val checkboxSelectAll = view.findViewById<CheckBox>(R.id.checkbox_select_all)
        val checkboxCpuSingle = view.findViewById<CheckBox>(R.id.checkbox_cpu_single)
        val checkboxCpuMulti = view.findViewById<CheckBox>(R.id.checkbox_cpu_multi)
        val checkboxGpu = view.findViewById<CheckBox>(R.id.checkbox_gpu)
        val checkboxMemory = view.findViewById<CheckBox>(R.id.checkbox_memory)
        val checkboxStorage = view.findViewById<CheckBox>(R.id.checkbox_storage)
        val buttonStart = view.findViewById<Button>(R.id.button_start_test)
        val buttonShowPrevTests = view.findViewById<Button>(R.id.button_show_prev_tests)
        val buttonShowGraphs = view.findViewById<Button>(R.id.button_graphs)

        buttonShowPrevTests.isEnabled = TestResultsStorage.hasResults(requireContext())
        buttonShowGraphs.isEnabled = getLatestTestRun() != null

        buttonShowPrevTests.setOnClickListener {
            showPreviousResults()
        }

        buttonShowGraphs.setOnClickListener {
            showGraphs()
        }

        val allCheckboxes = listOf(
            checkboxCpuSingle,
            checkboxCpuMulti,
            checkboxGpu,
            checkboxMemory,
            checkboxStorage
        )

        val updateButtonState = {
            val isAnyChecked = allCheckboxes.any { it.isChecked }
            buttonStart.isEnabled = isAnyChecked

            checkboxSelectAll.setOnCheckedChangeListener(null)
            checkboxSelectAll.isChecked = allCheckboxes.all { it.isChecked }
            checkboxSelectAll.setOnCheckedChangeListener { _, isChecked ->
                allCheckboxes.forEach { it.isChecked = isChecked }
            }
        }

        checkboxSelectAll.setOnCheckedChangeListener { _, isChecked ->
            allCheckboxes.forEach { it.isChecked = isChecked }
        }

        allCheckboxes.forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { _, _ -> updateButtonState() }
        }

        buttonStart.setOnClickListener {
            val selectedTests = mutableListOf<String>()
            if (checkboxCpuSingle.isChecked) selectedTests.add("CPU Single-Core")
            if (checkboxCpuMulti.isChecked) selectedTests.add("CPU Multi-Core")
            if (checkboxGpu.isChecked) selectedTests.add("GPU")
            if (checkboxMemory.isChecked) selectedTests.add("Memory")
            if (checkboxStorage.isChecked) selectedTests.add("Storage")

            val allTestsSelected = allCheckboxes.all { it.isChecked }

            progressDialog = showProgressDialog(selectedTests)

            Thread {
                val results = mutableMapOf<String, String>()
                val testScores = mutableListOf<Int>()

                // Arrays to store 5 runs of each test
                val cpuSingleTimes = mutableListOf<Double?>()
                val cpuMultiTimes = mutableListOf<Double?>()
                val gpuAvgFpsList = mutableListOf<Double?>()
                val memoryBandwidths = mutableListOf<Double?>()
                val storageThroughputs = mutableListOf<Double?>()

                // Average values for display and scoring
                var cpuSingleTime: Double? = null
                var cpuSingleScore: Int? = null
                var cpuMultiTime: Double? = null
                var cpuMultiScore: Int? = null
                var cpuMultiThreads: Int? = null
                var gpuAvgFps: Double? = null
                var gpuP95Fps: Double? = null
                var gpuP99Fps: Double? = null
                var gpuScore: Int? = null
                var memoryBandwidth: Double? = null
                var memoryScore: Int? = null
                var storageThroughput: Double? = null
                var storageScore: Int? = null

                // Run CPU Single-Core Test 5 times
                if (checkboxCpuSingle.isChecked) {
                    results["CPU Single-Core"] = "Running... (Run 1/5)"
                    updateProgress(results)

                    for (run in 1..5) {
                        results["CPU Single-Core"] = "Running... (Run $run/5)"
                        updateProgress(results)

                        val time = computeSinglecoreBenchmark()
                        cpuSingleTimes.add(time)
                    }

                    cpuSingleTime = cpuSingleTimes.filterNotNull().average()
                    cpuSingleScore = BenchmarkScoring.scoreCpuSingleCore(cpuSingleTime!!)

                    testScores.add(cpuSingleScore)
                    results.remove("CPU Single-Core")
                    results["✓ CPU Single-Core"] = "%.3f seconds \n Score: %d".format(cpuSingleTime, cpuSingleScore)
                    updateProgress(results)
                } else {
                    repeat(5) { cpuSingleTimes.add(null) }
                }

                // Run CPU Multi-Core Test 5 times
                if (checkboxCpuMulti.isChecked) {
                    results["CPU Multi-Core"] = "Running... (Run 1/5)"
                    updateProgress(results)

                    cpuMultiThreads = getCoreCount()

                    for (run in 1..5) {
                        results["CPU Multi-Core"] = "Running... (Run $run/5)"
                        updateProgress(results)

                        val time = computeMulticoreBenchmark()
                        cpuMultiTimes.add(time)
                    }

                    cpuMultiTime = cpuMultiTimes.filterNotNull().average()
                    cpuMultiScore = BenchmarkScoring.scoreCpuMultiCore(cpuMultiTime!!)
                    testScores.add(cpuMultiScore)
                    results.remove("CPU Multi-Core")
                    results["✓ CPU Multi-Core"] = "%.3f seconds (%d threads) \n Score: %d".format(cpuMultiTime, cpuMultiThreads, cpuMultiScore)
                    updateProgress(results)
                } else {
                    repeat(5) { cpuMultiTimes.add(null) }
                }

                // Run GPU Test for Duration
                if (checkboxGpu.isChecked) {
                    results["GPU"] = "Running..."
                    updateProgress(results)

                    requireActivity().runOnUiThread {
                        (requireActivity() as? AppCompatActivity)?.supportActionBar?.hide()
                    }

                    val gpuBenchmark = GpuBenchmark(requireActivity())
                    val gpuResults = gpuBenchmark.runBenchmark(10)

                    if (gpuResults.frameCount > 0) {
                        gpuAvgFps = gpuResults.averageFps
                        gpuP95Fps = gpuResults.p95Fps
                        gpuP99Fps = gpuResults.p99Fps
                        gpuScore = BenchmarkScoring.scoreGpu(gpuAvgFps!!)
                        testScores.add(gpuScore!!)

                        // Get per-second FPS data
                        val perSecondFps = gpuBenchmark.getPerSecondFps()
                        gpuAvgFpsList.clear()
                        gpuAvgFpsList.addAll(perSecondFps)

                        results.remove("GPU")
                        results["✓ GPU"] = "Avg: %.1f FPS \n P95: %.1f \n P99: %.1f \n Score: %d".format(
                            gpuAvgFps, gpuP95Fps, gpuP99Fps, gpuScore
                        )
                    } else {
                        results.remove("GPU")
                        results["✓ GPU"] = "Failed to initialize (returned 0 frames)"
                        repeat(10) { gpuAvgFpsList.add(null) }
                    }

                    requireActivity().runOnUiThread {
                        (requireActivity() as? AppCompatActivity)?.supportActionBar?.show()
                    }

                    updateProgress(results)
                } else {
                    repeat(10) { gpuAvgFpsList.add(null) }
                }

                // Run Memory Test 5 times
                if (checkboxMemory.isChecked) {
                    results["Memory"] = "Running... (Run 1/5)"
                    updateProgress(results)

                    for (run in 1..5) {
                        results["Memory"] = "Running... (Run $run/5)"
                        updateProgress(results)

                        val bandwidth = computeMemoryBenchmark()
                        memoryBandwidths.add(bandwidth)
                    }

                    memoryBandwidth = memoryBandwidths.filterNotNull().average()
                    memoryScore = BenchmarkScoring.scoreMemory(memoryBandwidth!!)
                    testScores.add(memoryScore!!)
                    results.remove("Memory")
                    results["✓ Memory"] = "%.2f GB/s (Memory Bandwidth) \n Score: %d".format(memoryBandwidth, memoryScore)
                    updateProgress(results)
                } else {
                    repeat(5) { memoryBandwidths.add(null) }
                }

                // Run Storage Test 5 times
                if (checkboxStorage.isChecked) {
                    results["Storage"] = "Running... (Run 1/5)"
                    updateProgress(results)

                    val tempFile = "${requireContext().cacheDir}/benchmark_temp.bin"

                    for (run in 1..5) {
                        results["Storage"] = "Running... (Run $run/5)"
                        updateProgress(results)

                        val throughput = computeStorageBenchmark(tempFile)
                        storageThroughputs.add(throughput)
                    }

                    storageThroughput = storageThroughputs.filterNotNull().average()
                    storageScore = BenchmarkScoring.scoreStorage(storageThroughput!!)
                    testScores.add(storageScore)
                    results.remove("Storage")
                    results["✓ Storage"] = "%.2f MB/s (Storage Throughput)\n Score: %d".format(storageThroughput, storageScore)
                    updateProgress(results)
                } else {
                    repeat(5) { storageThroughputs.add(null) }
                }

                // Save test data for graphs
                val testRun = TestRun(
                    cpuSingleTimes = cpuSingleTimes,
                    cpuMultiTimes = cpuMultiTimes,
                    gpuAvgFps = gpuAvgFpsList,
                    memoryBandwidths = memoryBandwidths,
                    storageThroughputs = storageThroughputs
                )
                saveLatestTestRun(testRun)

                // Calculate overall score
                val overallScore = if (allTestsSelected) {
                    BenchmarkScoring.calculateOverallScore(testScores)
                } else {
                    null
                }

                if (overallScore != null) {
                    results["Overall Score"] = "$overallScore"
                }

                updateProgress(results)

                // Save test result
                val testResult = TestResult(
                    timestamp = System.currentTimeMillis(),
                    date = TestResultsStorage.formatDate(System.currentTimeMillis()),
                    cpuSingleScore = cpuSingleScore,
                    cpuSingleTime = cpuSingleTime,
                    cpuMultiScore = cpuMultiScore,
                    cpuMultiTime = cpuMultiTime,
                    cpuMultiThreads = cpuMultiThreads,
                    gpuScore = gpuScore,
                    gpuAvgFps = gpuAvgFps,
                    gpuP95Fps = gpuP95Fps,
                    gpuP99Fps = gpuP99Fps,
                    memoryScore = memoryScore,
                    memoryBandwidth = memoryBandwidth,
                    storageScore = storageScore,
                    storageThroughput = storageThroughput,
                    overallScore = overallScore,
                    allTestsRun = allTestsSelected
                )

                TestResultsStorage.saveResult(requireContext(), testResult)

                requireActivity().runOnUiThread {
                    progressDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    buttonShowPrevTests.isEnabled = true
                    buttonShowGraphs.isEnabled = true
                }
            }.start()
        }

        return view
    }
}