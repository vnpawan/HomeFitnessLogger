package com.example.homefitnesslogger

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Unified Data Model
data class AppLog(
    val id: String,
    val logType: String,
    val title: String,
    val durationMins: Float,
    val calories: Float,
    val recoveryPct: Float,
    val proteinG: Float,
    val carbsG: Float,
    val fatsG: Float,
    val timestamp: Long
)

class HistoryActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var rvHistory: RecyclerView
    private lateinit var adapter: HistoryAdapter
    private val logList = mutableListOf<AppLog>()

    private var currentUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            finish()
            return
        }
        currentUserId = currentUser.uid

        db = FirebaseFirestore.getInstance()
        rvHistory = findViewById(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)

        // RESTORED: Passing the click listener into the adapter
        adapter = HistoryAdapter(logList) { clickedLog ->
            showSmartEditDialog(clickedLog)
        }
        rvHistory.adapter = adapter

        fetchRecentLogs()
    }

    private fun fetchRecentLogs() {
        db.collection("logs")
            .whereEqualTo("user_id", currentUserId)
            .whereEqualTo("status", "confirmed")
            .get()
            .addOnSuccessListener { documents ->
                val sortedDocs = documents.sortedByDescending { it.getLong("timestamp") ?: 0L }.take(30)

                logList.clear()
                for (doc in sortedDocs) {
                    val parsedData = doc.data["parsed_data"] as? Map<String, Any> ?: continue
                    val logType = doc.getString("log_type") ?: "workout"

                    val log = if (logType == "workout") {
                        AppLog(
                            id = doc.id,
                            logType = logType,
                            title = parsedData["exercise_name"]?.toString() ?: "Unknown Workout",
                            durationMins = parsedData["duration_minutes"]?.toString()?.toFloatOrNull() ?: 0f,
                            calories = parsedData["calories_burnt"]?.toString()?.toFloatOrNull() ?: 0f,
                            recoveryPct = parsedData["post_workout_recovery_pct"]?.toString()?.toFloatOrNull() ?: 100f,
                            proteinG = 0f, carbsG = 0f, fatsG = 0f,
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    } else {
                        val foodItems = parsedData["food_items"]
                        val titleString = if (foodItems is List<*>) foodItems.joinToString(", ") else foodItems?.toString() ?: "Unknown Meal"

                        AppLog(
                            id = doc.id,
                            logType = logType,
                            title = titleString,
                            durationMins = 0f,
                            calories = parsedData["calories_consumed"]?.toString()?.toFloatOrNull() ?: 0f,
                            recoveryPct = 100f,
                            proteinG = parsedData["protein_g"]?.toString()?.toFloatOrNull() ?: 0f,
                            carbsG = parsedData["carbs_g"]?.toString()?.toFloatOrNull() ?: 0f,
                            fatsG = parsedData["fats_g"]?.toString()?.toFloatOrNull() ?: 0f,
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    }
                    logList.add(log)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show()
            }
    }

    // --- SMART EDIT ROUTING ---
    private fun showSmartEditDialog(log: AppLog) {
        if (log.logType == "workout") {
            showWorkoutEditDialog(log)
        } else {
            showFoodEditDialog(log)
        }
    }

    private fun showWorkoutEditDialog(log: AppLog) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_validation, null)
        val etActivity = dialogView.findViewById<EditText>(R.id.etActivity)
        val etDuration = dialogView.findViewById<EditText>(R.id.etDuration)

        etActivity.setText(log.title)
        etDuration.setText(log.durationMins.toString())

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("UPDATE") { _, _ ->
                val newActivity = etActivity.text.toString()
                val newDuration = etDuration.text.toString().toFloatOrNull() ?: 0f

                val metrics = recalculateHistoricalMetrics(newActivity, newDuration)

                db.collection("logs").document(log.id).update(
                    "parsed_data.exercise_name", newActivity,
                    "parsed_data.duration_minutes", newDuration,
                    "parsed_data.calories_burnt", metrics.first,
                    "parsed_data.post_workout_recovery_pct", metrics.second,
                    "raw_text", "Manually edited workout"
                ).addOnSuccessListener {
                    Toast.makeText(this, "Workout updated!", Toast.LENGTH_SHORT).show()
                    fetchRecentLogs()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showFoodEditDialog(log: AppLog) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_food_validation, null)
        val etFoodItems = dialogView.findViewById<EditText>(R.id.etFoodItems)
        val etCalories = dialogView.findViewById<EditText>(R.id.etCalories)
        val etProtein = dialogView.findViewById<EditText>(R.id.etProtein)
        val etCarbs = dialogView.findViewById<EditText>(R.id.etCarbs)
        val etFats = dialogView.findViewById<EditText>(R.id.etFats)

        etFoodItems.setText(log.title)
        etCalories.setText(log.calories.toString())
        etProtein.setText(log.proteinG.toString())
        etCarbs.setText(log.carbsG.toString())
        etFats.setText(log.fatsG.toString())

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton("UPDATE") { _, _ ->
                val updatedParsedData = mapOf(
                    "food_items" to etFoodItems.text.toString().split(",").map { it.trim() },
                    "calories_consumed" to (etCalories.text.toString().toFloatOrNull() ?: 0f),
                    "protein_g" to (etProtein.text.toString().toFloatOrNull() ?: 0f),
                    "carbs_g" to (etCarbs.text.toString().toFloatOrNull() ?: 0f),
                    "fats_g" to (etFats.text.toString().toFloatOrNull() ?: 0f)
                )

                db.collection("logs").document(log.id).update(
                    "parsed_data", updatedParsedData,
                    "raw_text", "Manually edited meal"
                ).addOnSuccessListener {
                    Toast.makeText(this, "Meal updated!", Toast.LENGTH_SHORT).show()
                    fetchRecentLogs()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    // RESTORED: The local math engine for historical workout edits
    private fun recalculateHistoricalMetrics(activity: String, durationMins: Float): Pair<Float, Float> {
        val actLower = activity.lowercase(Locale.getDefault())
        val met = when {
            actLower.contains("squat") -> 5.0f
            actLower.contains("push") -> 8.0f
            actLower.contains("run") || actLower.contains("jog") -> 9.8f
            actLower.contains("plank") -> 4.0f
            actLower.contains("walk") -> 3.5f
            actLower.contains("jump") -> 8.0f
            else -> 5.0f
        }
        val weightKg = 75f
        val calories = (met * 3.5f * weightKg / 200f) * durationMins

        var newRecovery = 100f - (met * durationMins * 0.1f)
        if (newRecovery < 0f) newRecovery = 0f

        return Pair(calories, newRecovery)
    }

    // --- RECYCLER VIEW ADAPTER ---
    inner class HistoryAdapter(
        private val logs: List<AppLog>,
        private val onItemClick: (AppLog) -> Unit // RESTORED: Click Listener Parameter
    ) : RecyclerView.Adapter<HistoryAdapter.LogViewHolder>() {

        private val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())

        inner class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvLogTypeIcon: TextView = view.findViewById(R.id.tvLogTypeIcon)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvPrimaryMetric: TextView = view.findViewById(R.id.tvPrimaryMetric)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val tvDetails: TextView = view.findViewById(R.id.tvDetails)

            // RESTORED: Binding the click to the specific list item
            init {
                view.setOnClickListener { onItemClick(logs[adapterPosition]) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val log = logs[position]

            holder.tvTitle.text = log.title.uppercase()
            holder.tvDate.text = sdf.format(Date(log.timestamp))

            if (log.logType == "workout") {
                holder.tvLogTypeIcon.text = "W"
                holder.tvLogTypeIcon.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00B0FF"))
                holder.tvDetails.text = "${log.durationMins.roundToInt()} min | ${log.calories.roundToInt()} kcal burnt"

                val recScore = log.recoveryPct.roundToInt()
                holder.tvPrimaryMetric.text = "$recScore%"
                val zoneColor = when {
                    recScore >= 80 -> Color.parseColor("#00E676")
                    recScore >= 50 -> Color.parseColor("#FFEA00")
                    recScore >= 25 -> Color.parseColor("#FF9100")
                    else -> Color.parseColor("#FF1744")
                }
                holder.tvPrimaryMetric.setTextColor(zoneColor)

            } else {
                holder.tvLogTypeIcon.text = "F"
                holder.tvLogTypeIcon.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E676"))

                val p = log.proteinG.roundToInt()
                val c = log.carbsG.roundToInt()
                val f = log.fatsG.roundToInt()
                holder.tvDetails.text = "${p}g Pro | ${c}g Carb | ${f}g Fat"

                holder.tvPrimaryMetric.text = "${log.calories.roundToInt()}\nkcal"
                holder.tvPrimaryMetric.textSize = 16f
                holder.tvPrimaryMetric.setTextColor(Color.parseColor("#00E676"))
            }
        }

        override fun getItemCount() = logs.size
    }
}