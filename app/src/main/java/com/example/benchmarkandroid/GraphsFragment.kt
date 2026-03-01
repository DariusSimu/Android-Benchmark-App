package com.example.benchmarkandroid

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.gson.Gson

class GraphsFragment : Fragment() {

    companion object {
        private const val ARG_TEST_RUN = "test_run"

        fun newInstance(testRun: TestRun): GraphsFragment {
            val fragment = GraphsFragment()
            val args = Bundle()
            val gson = Gson()
            args.putString(ARG_TEST_RUN, gson.toJson(testRun))
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var testRun: TestRun

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val gson = Gson()
            testRun = gson.fromJson(it.getString(ARG_TEST_RUN), TestRun::class.java)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_graphs, container, false)

        val textColorPrimary = getColorFromTheme(android.R.attr.textColorPrimary)
        val gridLineColor = getGridLineColor(textColorPrimary)

        view.findViewById<TextView>(R.id.title_main).setTextColor(textColorPrimary)

        setupChartWithTitle(
            view.findViewById(R.id.chart_cpu_single),
            view.findViewById(R.id.title_cpu_single),
            testRun.cpuSingleTimes,
            Color.rgb(255, 99, 71),
            textColorPrimary,
            gridLineColor
        )

        setupChartWithTitle(
            view.findViewById(R.id.chart_cpu_multi),
            view.findViewById(R.id.title_cpu_multi),
            testRun.cpuMultiTimes,
            Color.rgb(75, 192, 192),
            textColorPrimary,
            gridLineColor
        )

        setupChartWithTitle(
            view.findViewById(R.id.chart_gpu),
            view.findViewById(R.id.title_gpu),
            testRun.gpuAvgFps,
            Color.rgb(153, 102, 255),
            textColorPrimary,
            gridLineColor
        )

        setupChartWithTitle(
            view.findViewById(R.id.chart_memory),
            view.findViewById(R.id.title_memory),
            testRun.memoryBandwidths,
            Color.rgb(255, 206, 86),
            textColorPrimary,
            gridLineColor
        )

        setupChartWithTitle(
            view.findViewById(R.id.chart_storage),
            view.findViewById(R.id.title_storage),
            testRun.storageThroughputs,
            Color.rgb(54, 162, 235),
            textColorPrimary,
            gridLineColor
        )

        return view
    }

    private fun getColorFromTheme(attrId: Int): Int {
        return if (context != null) {
            val typedValue = TypedValue()
            val theme = requireContext().theme
            theme.resolveAttribute(attrId, typedValue, true)

            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else {
                ContextCompat.getColor(requireContext(), typedValue.resourceId)
            }
        } else {
            if (isSystemInDarkMode()) Color.WHITE else Color.BLACK
        }
    }

    private fun isSystemInDarkMode(): Boolean {
        return if (context != null) {
            val nightModeFlags = requireContext().resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
            nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        } else {
            false
        }
    }

    private fun getGridLineColor(textColor: Int): Int {
        val alpha = 100
        val red = Color.red(textColor)
        val green = Color.green(textColor)
        val blue = Color.blue(textColor)
        return Color.argb(alpha, red, green, blue)
    }

    private fun setupChartWithTitle(
        chart: LineChart,
        titleView: TextView,
        data: List<Double?>,
        lineColor: Int,
        textColor: Int,
        gridColor: Int
    ) {
        val entries = data.mapIndexedNotNull { index, value ->
            value?.let { Entry((index + 1).toFloat(), it.toFloat()) }
        }

        if (entries.isEmpty()) {
            chart.visibility = View.GONE
            titleView.visibility = View.GONE
            return
        }

        titleView.setTextColor(textColor)

        setupChart(chart, entries, lineColor, textColor, gridColor)
    }

    private fun setupChart(
        chart: LineChart,
        entries: List<Entry>,
        lineColor: Int,
        textColor: Int,
        gridColor: Int
    ) {
        val dataSet = LineDataSet(entries, "").apply {
            this.color = lineColor
            setCircleColor(lineColor)
            lineWidth = 3f
            circleRadius = 6f
            setDrawValues(true)
            valueTextSize = 12f
            valueTextColor = textColor
            setDrawCircleHole(false)
            mode = LineDataSet.Mode.LINEAR
        }

        chart.apply {
            this.data = LineData(dataSet)
            description.isEnabled = false

            setBackgroundColor(Color.TRANSPARENT)
            setDrawGridBackground(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                setGridColor(gridColor)
                gridLineWidth = 1f
                granularity = 1f
                axisMinimum = 0.5f
                axisMaximum = entries.size.toFloat() + 0.5f
                labelCount = entries.size
                setTextColor(textColor)
                textSize = 12f
                setDrawLabels(true)
                setDrawAxisLine(true)
                axisLineColor = gridColor
                axisLineWidth = 1f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                setGridColor(gridColor)
                gridLineWidth = 1f
                setTextColor(textColor)
                textSize = 12f
                setDrawAxisLine(true)
                axisLineColor = gridColor
                axisLineWidth = 1f
                setDrawZeroLine(false)
            }

            axisRight.apply {
                isEnabled = false
            }

            legend.apply {
                isEnabled = false
            }

            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            setExtraOffsets(10f, 10f, 10f, 10f)

            description.isEnabled = false

            notifyDataSetChanged()
            invalidate()
        }
    }
}