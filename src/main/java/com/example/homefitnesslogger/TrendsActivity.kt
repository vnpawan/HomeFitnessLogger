package com.example.homefitnesslogger

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TrendsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var currentUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trends)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            finish()
            return
        }
        currentUserId = currentUser.uid

        db = FirebaseFirestore.getInstance()

        db.collection("logs")
            .whereEqualTo("user_id", currentUserId)
            .whereEqualTo("status", "confirmed")
            .get()
            .addOnSuccessListener { documents ->
                val sortedDocs = documents.sortedBy { it.getLong("timestamp") ?: 0L }
                val logs = sortedDocs.mapNotNull { it.data["parsed_data"]?.let { parsed ->
                    val mutableParsed = parsed as MutableMap<String, Any>
                    mutableParsed["timestamp"] = it.getLong("timestamp") ?: 0L
                    // Safely extract log_type, defaulting to workout for older logs
                    mutableParsed["log_type"] = it.getString("log_type") ?: "workout"
                    mutableParsed
                }}

                renderCharts(logs)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load trends: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun renderCharts(logs: List<Map<String, Any>>) {

        // --- 1. Recovery Wave (Filters only workouts) ---
        val chartRecovery = findViewById<LineChart>(R.id.chartRecovery)
        val recoveryEntries = ArrayList<Entry>()
        val workoutLogs = logs.filter { it["log_type"] == "workout" }.takeLast(15)

        workoutLogs.forEachIndexed { index, log ->
            val rec = log["post_workout_recovery_pct"]?.toString()?.toFloatOrNull() ?: 100f
            recoveryEntries.add(Entry(index.toFloat(), rec))
        }

        val recFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                return ""
            }
        }

        val lineDataSet = LineDataSet(recoveryEntries, "Recovery %")
        lineDataSet.color = Color.parseColor("#00B0FF")
        lineDataSet.setCircleColor(Color.parseColor("#00E676"))
        lineDataSet.lineWidth = 3f
        lineDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        chartRecovery.data = LineData(lineDataSet)

        chartRecovery.description.isEnabled = false
        chartRecovery.legend.textColor = Color.WHITE
        chartRecovery.xAxis.valueFormatter = recFormatter
        chartRecovery.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chartRecovery.axisLeft.textColor = Color.WHITE
        chartRecovery.axisLeft.axisMaximum = 105f
        chartRecovery.axisLeft.axisMinimum = 0f
        chartRecovery.axisRight.isEnabled = false
        chartRecovery.invalidate()

        // --- 2. Dual Bar Charts (Calories IN vs Calories OUT) ---
        setupDualBarChart(findViewById(R.id.chartCalWeek), buildDualBuckets(logs, 7), DailyAxisFormatter(7), 7)
        setupDualBarChart(findViewById(R.id.chartCalMonth), buildDualBuckets(logs, 30), DailyAxisFormatter(30), 30)
        setupDualBarChart(findViewById(R.id.chartCal3Months), buildDualBuckets(logs, 90), DailyAxisFormatter(90), 90)

        // --- 3. Single Bar Chart (Total Logs Per Day) ---
        setupSingleBarChart(findViewById(R.id.chartLogs15Days), buildCountBuckets(logs, 15), "Total Actions Logged", "#9C27B0", DailyAxisFormatter(15))
    }

    // --- The Dual-Stream Math Engine ---
    private fun buildDualBuckets(logs: List<Map<String, Any>>, daysBack: Int): Pair<ArrayList<BarEntry>, ArrayList<BarEntry>> {
        val consumedEntries = ArrayList<BarEntry>()
        val burntEntries = ArrayList<BarEntry>()

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -(daysBack - 1))

        for (i in 0 until daysBack) {
            val startOfDay = cal.timeInMillis
            val endOfDay = startOfDay + 86400000L - 1

            var dailyConsumed = 0f
            var dailyBurnt = 0f

            for (log in logs) {
                val timestamp = log["timestamp"] as? Long ?: 0L
                if (timestamp in startOfDay..endOfDay) {
                    val logType = log["log_type"] as? String ?: "workout"
                    if (logType == "food") {
                        dailyConsumed += (log["calories_consumed"]?.toString()?.toFloatOrNull() ?: 0f)
                    } else {
                        dailyBurnt += (log["calories_burnt"]?.toString()?.toFloatOrNull() ?: 0f)
                    }
                }
            }
            consumedEntries.add(BarEntry(i.toFloat(), dailyConsumed))
            burntEntries.add(BarEntry(i.toFloat(), dailyBurnt))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return Pair(consumedEntries, burntEntries)
    }

    // --- Count Engine for Interactions ---
    private fun buildCountBuckets(logs: List<Map<String, Any>>, daysBack: Int): ArrayList<BarEntry> {
        val entries = ArrayList<BarEntry>()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -(daysBack - 1))

        for (i in 0 until daysBack) {
            val startOfDay = cal.timeInMillis
            val endOfDay = startOfDay + 86400000L - 1

            var dailyTotal = 0f
            for (log in logs) {
                val timestamp = log["timestamp"] as? Long ?: 0L
                if (timestamp in startOfDay..endOfDay) {
                    dailyTotal += 1f
                }
            }
            entries.add(BarEntry(i.toFloat(), dailyTotal))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return entries
    }

    private fun setupDualBarChart(chart: BarChart, dataPair: Pair<ArrayList<BarEntry>, ArrayList<BarEntry>>, formatter: ValueFormatter, daysBack: Int) {
        val setConsumed = BarDataSet(dataPair.first, "IN (kcal)")
        setConsumed.color = Color.parseColor("#00E676") // Green for Food

        val setBurnt = BarDataSet(dataPair.second, "OUT (kcal)")
        setBurnt.color = Color.parseColor("#FF9100") // Orange for Burn

        val data = BarData(setConsumed, setBurnt)

        // MPAndroidChart precise grouped bar calculations
        // (barWidth + barSpace) * 2 + groupSpace must equal exactly 1.00
        val groupSpace = 0.20f
        val barSpace = 0.05f
        val barWidth = 0.35f
        data.barWidth = barWidth

        chart.data = data
        chart.description.isEnabled = false
        chart.legend.textColor = Color.WHITE
        chart.setScaleEnabled(false)
        chart.isDragEnabled = false

        chart.xAxis.valueFormatter = formatter
        chart.xAxis.textColor = Color.WHITE
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.setCenterAxisLabels(true) // Crucial for aligning dates with grouped bars
        chart.xAxis.axisMinimum = 0f
        chart.xAxis.axisMaximum = daysBack.toFloat()

        chart.axisLeft.textColor = Color.WHITE
        chart.axisLeft.axisMinimum = 0f
        chart.axisRight.isEnabled = false

        // Group the bars starting at X = 0
        chart.groupBars(0f, groupSpace, barSpace)
        chart.invalidate()
    }

    private fun setupSingleBarChart(chart: BarChart, entries: List<BarEntry>, label: String, colorHex: String, formatter: ValueFormatter) {
        val dataSet = BarDataSet(entries, label)
        dataSet.color = Color.parseColor(colorHex)
        chart.data = BarData(dataSet)

        chart.description.isEnabled = false
        chart.legend.textColor = Color.WHITE
        chart.setScaleEnabled(false)
        chart.isDragEnabled = false

        chart.xAxis.valueFormatter = formatter
        chart.xAxis.textColor = Color.WHITE
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.setLabelCount(5, true)

        chart.axisLeft.textColor = Color.WHITE
        chart.axisLeft.axisMinimum = 0f
        chart.axisRight.isEnabled = false
        chart.setFitBars(true)
        chart.invalidate()
    }

    inner class DailyAxisFormatter(private val daysBack: Int) : ValueFormatter() {
        private val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
            // Because grouped bars shift X values fractionally, we cast to Int to lock onto the correct day index
            val index = value.toInt()
            if (index in 0 until daysBack) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -(daysBack - 1 - index))
                return sdf.format(cal.time)
            }
            return ""
        }
    }
}