package com.example.benchmarkandroid

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.io.BufferedReader
import java.io.FileReader
import java.io.RandomAccessFile
import android.util.DisplayMetrics
import android.view.WindowManager

class SpecificationsFragment : Fragment() {

    private var updateHandler: android.os.Handler? = null
    private var cpuFrequencyTextView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_specifications, container, false)

        val deviceModel = view.findViewById<TextView>(R.id.device_model)
        val socModel = view.findViewById<TextView>(R.id.soc_model)
        val cpuArchitecture = view.findViewById<TextView>(R.id.cpu_architecture)
        val cpuCores = view.findViewById<TextView>(R.id.cpu_cores)
        cpuFrequencyTextView = view.findViewById<TextView>(R.id.cpu_frequency)
        val gpuVendor = view.findViewById<TextView>(R.id.gpu_vendor)
        val gpuRenderer = view.findViewById<TextView>(R.id.gpu_renderer)
        val ramInfo = view.findViewById<TextView>(R.id.ram_info)
        val storageInfo = view.findViewById<TextView>(R.id.storage_info)
        val displayResolution = view.findViewById<TextView>(R.id.display_resolution)
        val androidVersion = view.findViewById<TextView>(R.id.android_version)

        deviceModel.text = "${Build.MANUFACTURER} ${Build.MODEL}"
        socModel.text = getSoCModel()
        cpuArchitecture.text = getCpuArchitecture()
        cpuCores.text = "${Runtime.getRuntime().availableProcessors()} cores"

        val gpuInfo = getGpuInfo()
        gpuVendor.text = gpuInfo.first
        gpuRenderer.text = gpuInfo.second

        ramInfo.text = getRamInfo()
        storageInfo.text = getStorageInfo()
        displayResolution.text = context?.let { getDisplayResolution(it) }
        androidVersion.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        startCpuFrequencyUpdates()

        return view
    }

    private fun startCpuFrequencyUpdates() {
        updateHandler = android.os.Handler(android.os.Looper.getMainLooper())

        val updateRunnable = object : Runnable {
            override fun run() {
                cpuFrequencyTextView?.text = getCpuFrequencies()
                updateHandler?.postDelayed(this, 500)
            }
        }

        updateHandler?.post(updateRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateHandler?.removeCallbacksAndMessages(null)
        updateHandler = null
        cpuFrequencyTextView = null
    }

    private fun getSoCModel(): String {
        return try {
            val socInfo = getSoCFromSystemProperties()
            if (socInfo != "Unknown") return socInfo

            val cpuInfo = getSoCFromCpuInfo()
            if (cpuInfo != "Unknown") return cpuInfo

            getSoCFromBuildInfo()
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getSoCFromSystemProperties(): String {
        val propertiesToTry = listOf(
            "ro.soc.model" to "ro.soc.manufacturer",
            "ro.board.platform" to null,
            "ro.hardware" to null,
            "ro.product.board" to null,
            "ro.chipname" to null,
            "ro.mediatek.platform" to null,
            "ro.arch" to null
        )

        for ((primary, secondary) in propertiesToTry) {
            val primaryValue = getSystemProperty(primary)
            if (!primaryValue.isNullOrEmpty()) {
                val secondaryValue = if (secondary != null) getSystemProperty(secondary) else null
                val combined = if (!secondaryValue.isNullOrEmpty()) {
                    "$secondaryValue $primaryValue"
                } else {
                    primaryValue
                }
                return mapHardwareToSOC(combined)
            }
        }
        return "Unknown"
    }

    private fun getSoCFromCpuInfo(): String {
        return try {
            BufferedReader(FileReader("/proc/cpuinfo")).use { reader ->
                var line: String?
                var hardware = ""
                var implementer = ""
                var architecture = ""
                var variant = ""
                var part = ""
                var revision = ""

                while (reader.readLine().also { line = it } != null) {
                    when {
                        line!!.startsWith("Hardware") -> {
                            hardware = line!!.split(":").getOrNull(1)?.trim() ?: ""
                        }
                        line.startsWith("CPU implementer") -> {
                            implementer = line.split(":").getOrNull(1)?.trim() ?: ""
                        }
                        line.startsWith("CPU architecture") -> {
                            architecture = line.split(":").getOrNull(1)?.trim() ?: ""
                        }
                        line.startsWith("CPU variant") -> {
                            variant = line.split(":").getOrNull(1)?.trim() ?: ""
                        }
                        line.startsWith("CPU part") -> {
                            part = line.split(":").getOrNull(1)?.trim() ?: ""
                        }
                        line.startsWith("CPU revision") -> {
                            revision = line.split(":").getOrNull(1)?.trim() ?: ""
                        }
                    }
                }

                when {
                    hardware.isNotEmpty() -> mapHardwareToSOC(hardware)
                    implementer.isNotEmpty() -> identifySoCFromCPU(implementer, architecture, variant, part, revision)
                    else -> "Unknown"
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getSoCFromBuildInfo(): String {
        val buildInfo = listOf(
            Build.BOARD,
            Build.HARDWARE,
            Build.PRODUCT,
            Build.DEVICE,
            Build.MANUFACTURER + " " + Build.MODEL
        ).filter { !it.isNullOrEmpty() }.joinToString(" ")

        return mapHardwareToSOC(buildInfo)
    }

    private fun identifySoCFromCPU(implementer: String, architecture: String, variant: String, part: String, revision: String): String {
        // ARM CPU implementer codes
        return when (implementer) {
            "0x41" -> { // ARM
                when (part) {
                    "0xd05" -> "Cortex-A55"
                    "0xd04" -> "Cortex-A35"
                    "0xd03" -> "Cortex-A53"
                    "0xd07" -> "Cortex-A57"
                    "0xd08" -> "Cortex-A72"
                    "0xd09" -> "Cortex-A73"
                    "0xd0a" -> "Cortex-A75"
                    "0xd0b" -> "Cortex-A76"
                    "0xd0c" -> "Neoverse-N1"
                    "0xd0d" -> "Cortex-A77"
                    "0xd0e" -> "Cortex-A76AE"
                    "0xd44" -> "Cortex-X1"
                    "0xd46" -> "Cortex-A510"
                    "0xd47" -> "Cortex-A710"
                    "0xd48" -> "Cortex-X2"
                    "0xd49" -> "Neoverse-N2"
                    "0xd4a" -> "Cortex-A715"
                    "0xd4b" -> "Cortex-X3"
                    "0xd4c" -> "Cortex-A520"
                    "0xd4d" -> "Cortex-A720"
                    "0xd4e" -> "Cortex-X4"
                    else -> "ARM Processor"
                }
            }
            "0x51" -> "Qualcomm"
            "0x53" -> "Samsung"
            else -> "Unknown CPU"
        }
    }

    private fun mapHardwareToSOC(hardware: String?): String {
        if (hardware.isNullOrEmpty()) return "Unknown"

        val hardwareLower = hardware.lowercase()

        // Qualcomm Snapdragon
        return when {
            // Snapdragon 8 Gen series
            hardwareLower.contains("sm8650") || hardwareLower.contains("pineapple") -> "Qualcomm Snapdragon 8 Gen 3"
            hardwareLower.contains("sm8550") || hardwareLower.contains("kalama") -> "Qualcomm Snapdragon 8 Gen 2"
            hardwareLower.contains("sm8475") || hardwareLower.contains("taro") -> "Qualcomm Snapdragon 8+ Gen 1"
            hardwareLower.contains("sm8450") || hardwareLower.contains("waipio") -> "Qualcomm Snapdragon 8 Gen 1"
            hardwareLower.contains("sm8350") || hardwareLower.contains("lahaina") -> "Qualcomm Snapdragon 888"
            hardwareLower.contains("sm8250") || hardwareLower.contains("kona") -> "Qualcomm Snapdragon 865"
            hardwareLower.contains("sm8150") || hardwareLower.contains("msmnile") -> "Qualcomm Snapdragon 855"

            // Snapdragon 7 Gen series
            hardwareLower.contains("sm7550") || hardwareLower.contains("pineapple") -> "Qualcomm Snapdragon 7 Gen 3"
            hardwareLower.contains("sm7475") || hardwareLower.contains("cedros") -> "Qualcomm Snapdragon 7+ Gen 2"
            hardwareLower.contains("sm7450") || hardwareLower.contains("cortina") -> "Qualcomm Snapdragon 7 Gen 1"
            hardwareLower.contains("sm7325") || hardwareLower.contains("monaco") -> "Qualcomm Snapdragon 778G"
            hardwareLower.contains("sm7350") || hardwareLower.contains("cedros") -> "Qualcomm Snapdragon 7 Gen 1"

            // Snapdragon 6 Gen series
            hardwareLower.contains("sm6450") || hardwareLower.contains("waipio") -> "Qualcomm Snapdragon 6 Gen 1"
            hardwareLower.contains("sm6375") || hardwareLower.contains("holi") -> "Qualcomm Snapdragon 695"
            hardwareLower.contains("sm6225") || hardwareLower.contains("cedros") -> "Qualcomm Snapdragon 680"

            // Snapdragon 4 Gen series
            hardwareLower.contains("sm6225") || hardwareLower.contains("cedros") -> "Qualcomm Snapdragon 4 Gen 1"
            hardwareLower.contains("sm4375") || hardwareLower.contains("holi") -> "Qualcomm Snapdragon 4 Gen 2"

            // General Qualcomm patterns
            hardwareLower.contains("sm8") -> "Qualcomm Snapdragon 8 Series"
            hardwareLower.contains("sm7") -> "Qualcomm Snapdragon 7 Series"
            hardwareLower.contains("sm6") -> "Qualcomm Snapdragon 6 Series"
            hardwareLower.contains("sm4") -> "Qualcomm Snapdragon 4 Series"
            hardwareLower.contains("msm") || hardwareLower.contains("qsd") -> "Qualcomm Snapdragon"

            // Samsung Exynos
            hardwareLower.contains("exynos") || hardwareLower.contains("s5e") -> {
                when {
                    hardwareLower.contains("2200") -> "Samsung Exynos 2200"
                    hardwareLower.contains("2100") -> "Samsung Exynos 2100"
                    hardwareLower.contains("990") -> "Samsung Exynos 990"
                    hardwareLower.contains("9820") -> "Samsung Exynos 9820"
                    hardwareLower.contains("9810") -> "Samsung Exynos 9810"
                    hardwareLower.contains("8895") -> "Samsung Exynos 8895"
                    hardwareLower.contains("8890") -> "Samsung Exynos 8890"
                    else -> "Samsung Exynos"
                }
            }

            // MediaTek Dimensity
            hardwareLower.contains("dimensity") || hardwareLower.contains("mt69") -> {
                when {
                    hardwareLower.contains("9200") -> "MediaTek Dimensity 9200"
                    hardwareLower.contains("9000") -> "MediaTek Dimensity 9000"
                    hardwareLower.contains("8100") -> "MediaTek Dimensity 8100"
                    hardwareLower.contains("8000") -> "MediaTek Dimensity 8000"
                    hardwareLower.contains("1200") -> "MediaTek Dimensity 1200"
                    hardwareLower.contains("1100") -> "MediaTek Dimensity 1100"
                    hardwareLower.contains("1000") -> "MediaTek Dimensity 1000"
                    else -> "MediaTek Dimensity"
                }
            }

            // MediaTek Helio
            hardwareLower.contains("helio") || hardwareLower.contains("mt67") || hardwareLower.contains("mt68") -> {
                when {
                    hardwareLower.contains("g99") -> "MediaTek Helio G99"
                    hardwareLower.contains("g96") -> "MediaTek Helio G96"
                    hardwareLower.contains("g95") -> "MediaTek Helio G95"
                    hardwareLower.contains("g90") -> "MediaTek Helio G90"
                    hardwareLower.contains("g85") -> "MediaTek Helio G85"
                    hardwareLower.contains("g80") -> "MediaTek Helio G80"
                    hardwareLower.contains("g70") -> "MediaTek Helio G70"
                    hardwareLower.contains("g35") -> "MediaTek Helio G35"
                    hardwareLower.contains("g25") -> "MediaTek Helio G25"
                    hardwareLower.contains("p70") -> "MediaTek Helio P70"
                    hardwareLower.contains("p65") -> "MediaTek Helio P65"
                    hardwareLower.contains("p60") -> "MediaTek Helio P60"
                    else -> "MediaTek Helio"
                }
            }

            // General MediaTek
            hardwareLower.contains("mediatek") || hardwareLower.contains("mt") -> "MediaTek"

            // Google Tensor
            hardwareLower.contains("tensor") || hardwareLower.contains("gs101") -> {
                when {
                    hardwareLower.contains("g3") -> "Google Tensor G3"
                    hardwareLower.contains("g2") -> "Google Tensor G2"
                    else -> "Google Tensor"
                }
            }

            // HiSilicon Kirin
            hardwareLower.contains("kirin") || hardwareLower.contains("hi") -> {
                when {
                    hardwareLower.contains("9000") -> "HiSilicon Kirin 9000"
                    hardwareLower.contains("990") -> "HiSilicon Kirin 990"
                    hardwareLower.contains("980") -> "HiSilicon Kirin 980"
                    hardwareLower.contains("970") -> "HiSilicon Kirin 970"
                    hardwareLower.contains("960") -> "HiSilicon Kirin 960"
                    hardwareLower.contains("950") -> "HiSilicon Kirin 950"
                    else -> "HiSilicon Kirin"
                }
            }

            hardwareLower.contains("unisoc") || hardwareLower.contains("spreadtrum") || hardwareLower.contains("sc") -> "Unisoc"

            else -> hardware
        }
    }

    private fun getCpuArchitecture(): String {
        val abis = Build.SUPPORTED_ABIS
        val primaryArch = when {
            abis.any { it.contains("arm64-v8a") } -> "ARM64"
            abis.any { it.contains("armeabi-v7a") } -> "ARMv7"
            abis.any { it.contains("x86_64") } -> "x86_64"
            abis.any { it.contains("x86") } -> "x86"
            else -> "Unknown"
        }
        return "$primaryArch (${abis.first()})"
    }

    private fun getGpuInfo(): Pair<String, String> {
        return try {
            val boardPlatform = (Build.BOARD ?: "").lowercase()
            val hardware = (Build.HARDWARE ?: "").lowercase()
            val device = (Build.DEVICE ?: "").lowercase()
            val socModel = getSystemProperty("ro.soc.model")?.lowercase() ?: ""
            val boardPlatformProp = getSystemProperty("ro.board.platform")?.lowercase() ?: ""

            val allInfo = "$boardPlatform $hardware $device $socModel $boardPlatformProp"

            val vendor = when {
                allInfo.contains("msm") ||
                        allInfo.contains("qcom") ||
                        allInfo.contains("lahaina") ||
                        allInfo.contains("taro") ||
                        allInfo.contains("kalama") ||
                        allInfo.contains("pineapple") -> "Qualcomm"

                allInfo.contains("exynos") -> "ARM (Samsung Exynos)"
                allInfo.contains("mt") || allInfo.contains("mediatek") -> "ARM (MediaTek)"
                allInfo.contains("kirin") -> "ARM (HiSilicon)"
                allInfo.contains("tensor") -> "ARM (Google Tensor)"
                else -> "Unknown"
            }

            val renderer = when {
                allInfo.contains("msm") || allInfo.contains("qcom") ||
                        allInfo.contains("lahaina") || allInfo.contains("taro") ||
                        allInfo.contains("kalama") || allInfo.contains("pineapple") -> {
                    // Adreno version
                    when {
                        allInfo.contains("sm8650") || allInfo.contains("pineapple") -> "Adreno 750"
                        allInfo.contains("sm8550") || allInfo.contains("kalama") -> "Adreno 740"
                        allInfo.contains("sm8475") || allInfo.contains("sm8450") ||
                                allInfo.contains("taro") -> "Adreno 730"
                        allInfo.contains("sm8350") || allInfo.contains("lahaina") -> "Adreno 660"
                        allInfo.contains("sm8250") || allInfo.contains("kona") -> "Adreno 650"
                        allInfo.contains("sm8150") || allInfo.contains("msmnile") -> "Adreno 640"
                        allInfo.contains("sm7") -> "Adreno 6xx Series"
                        allInfo.contains("sm6") -> "Adreno 6xx Series"
                        else -> "Adreno GPU"
                    }
                }
                allInfo.contains("exynos") || allInfo.contains("mt") ||
                        allInfo.contains("kirin") || allInfo.contains("tensor") -> "Mali GPU"
                else -> "Unknown GPU"
            }

            Pair(vendor, renderer)
        } catch (e: Exception) {
            Pair("Unknown", "Unknown")
        }
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            process.inputStream.bufferedReader().use {
                it.readLine()?.trim()?.takeIf { line -> line.isNotEmpty() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getHardwareFromCpuInfo(): String? {
        return try {
            BufferedReader(FileReader("/proc/cpuinfo")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("Hardware")) {
                        return line!!.split(":").getOrNull(1)?.trim()
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getRamInfo(): String {
        return try {
            BufferedReader(FileReader("/proc/meminfo")).use { reader ->
                var totalMem = 0L
                var availableMem = 0L

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    when {
                        line!!.startsWith("MemTotal:") -> {
                            totalMem = line.split("\\s+".toRegex())[1].toLong()
                        }
                        line.startsWith("MemAvailable:") -> {
                            availableMem = line.split("\\s+".toRegex())[1].toLong()
                        }
                    }
                    if (totalMem > 0 && availableMem > 0) break
                }

                val totalGB = String.format("%.1f", totalMem / 1024.0 / 1024.0)
                val availableGB = String.format("%.1f", availableMem / 1024.0 / 1024.0)
                "$totalGB GB total, $availableGB GB available"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getStorageInfo(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes

            val totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val availableGB = availableBytes / (1024.0 * 1024.0 * 1024.0)

            "${String.format("%.1f", totalGB)} GB total, ${String.format("%.1f", availableGB)} GB available"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getCpuFrequencies(): String {
        val numCores = Runtime.getRuntime().availableProcessors()
        val coreInfo = mutableListOf<String>()

        for (i in 0 until numCores) {
            try {
                val maxPath = "/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq"
                val curPath = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"

                val maxFreq = try {
                    RandomAccessFile(maxPath, "r").use { file ->
                        file.readLine().toLong()
                    }
                } catch (e: Exception) {
                    null
                }

                val curFreq = try {
                    RandomAccessFile(curPath, "r").use { file ->
                        file.readLine().toLong()
                    }
                } catch (e: Exception) {
                    null
                }

                val info = when {
                    maxFreq != null && curFreq != null -> {
                        val maxGHz = String.format("%.2f", maxFreq / 1_000_000.0)
                        val curGHz = String.format("%.2f", curFreq / 1_000_000.0)
                        "Core $i: $curGHz GHz (max: $maxGHz GHz)"
                    }
                    maxFreq != null -> {
                        val maxGHz = String.format("%.2f", maxFreq / 1_000_000.0)
                        "Core $i: max $maxGHz GHz"
                    }
                    else -> "Core $i: Unknown"
                }

                coreInfo.add(info)
            } catch (e: Exception) {
                coreInfo.add("Core $i: Unknown")
            }
        }

        return if (coreInfo.isNotEmpty()) {
            coreInfo.joinToString("\n")
        } else {
            "Unknown"
        }
    }

    private fun getDisplayResolution(context: Context): String {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return "${bounds.width()} x ${bounds.height()} pixels"
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            return "${displayMetrics.widthPixels} x ${displayMetrics.heightPixels} pixels"
        }
    }
}
