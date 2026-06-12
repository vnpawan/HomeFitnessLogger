package com.example.homefitnesslogger

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlin.math.roundToInt

// Helper class for our local math
data class WorkoutMetrics(val calories: Float, val recovery: Float)

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var fabWorkout: ExtendedFloatingActionButton
    private lateinit var fabFood: ExtendedFloatingActionButton
    private lateinit var btnTrends: MaterialButton
    private lateinit var tvHeader: TextView
    private lateinit var tvRecoveryPercentage: TextView
    private lateinit var pbRecovery: ProgressBar

    // Coach UI Elements
    private lateinit var tvCoachMessage: TextView
    private lateinit var btnRefreshCoach: ImageButton

    // Gamification UI Elements
    private lateinit var tvStreak: TextView
    private lateinit var tvXp: TextView

    private var currentSystemRecovery: Float = 100f
    private var currentUserId: String = ""
    private var currentLogType: String = "workout"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        currentUserId = currentUser.uid

        fabWorkout = findViewById(R.id.fabWorkout)
        fabFood = findViewById(R.id.fabFood)
        btnTrends = findViewById(R.id.btnTrends)
        tvHeader = findViewById(R.id.tvHeader)
        tvRecoveryPercentage = findViewById(R.id.tvRecoveryPercentage)
        pbRecovery = findViewById(R.id.pbRecovery)

        tvCoachMessage = findViewById(R.id.tvCoachMessage)
        btnRefreshCoach = findViewById(R.id.btnRefreshCoach)

        tvStreak = findViewById(R.id.tvStreak)
        tvXp = findViewById(R.id.tvXp)

        pbRecovery.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        db = FirebaseFirestore.getInstance()

        fetchUserGamificationStats()
        fetchCurrentStateOnBoot()
        fetchLatestCoachInsight()
        setupSpeechRecognizer()

        fabWorkout.setOnClickListener {
            currentLogType = "workout"
            checkAudioPermissionAndListen()
        }

        fabFood.setOnClickListener {
            currentLogType = "food"
            checkAudioPermissionAndListen()
        }

        btnTrends.setOnClickListener {
            startActivity(Intent(this, TrendsActivity::class.java))
        }

        val btnProfile = findViewById<ImageButton>(R.id.btnProfile)
        btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        btnRefreshCoach.setOnClickListener {
            requestCoachInsight()
        }
    }

    private fun checkAudioPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            startListening()
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                val action = if (currentLogType == "food") "FOOD" else "WORKOUT"
                tvHeader.text = "LISTENING FOR $action..."
                tvHeader.setTextColor(Color.parseColor("#00E676"))
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    tvHeader.text = "SENDING TO CLOUD..."
                    tvHeader.setTextColor(Color.parseColor("#00B0FF"))
                    sendTextToFirebase(matches[0])
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { tvHeader.text = "PROCESSING..." }
            override fun onError(error: Int) {
                tvHeader.text = "SYSTEM READY"
                tvHeader.setTextColor(Color.parseColor("#888888"))
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    // NEW: Fetch and apply gamification metrics
    private fun fetchUserGamificationStats() {
        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val streak = document.getLong("current_streak") ?: 0L
                    val xp = document.getLong("total_xp") ?: 0L

                    tvStreak.text = "🔥 $streak Day"
                    tvXp.text = "🌟 $xp XP"
                } else {
                    tvStreak.text = "🔥 0 Day"
                    tvXp.text = "🌟 0 XP"
                }
            }
            .addOnFailureListener {
                // Keep default text on error
            }
    }

    private fun fetchCurrentStateOnBoot() {
        db.collection("logs")
            .whereEqualTo("user_id", currentUserId)
            .whereEqualTo("status", "confirmed")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    currentSystemRecovery = 100f
                    updateDashboard(currentSystemRecovery)
                } else {
                    val sortedDocs = documents.sortedByDescending { it.getLong("timestamp") ?: 0L }
                    val latestWorkoutLog = sortedDocs.firstOrNull { it.getString("log_type") == "workout" || it.getString("log_type") == null }

                    if (latestWorkoutLog != null) {
                        val parsedData = latestWorkoutLog.data["parsed_data"] as? Map<String, Any>
                        currentSystemRecovery = parsedData?.get("post_workout_recovery_pct")?.toString()?.toFloatOrNull() ?: 100f
                    } else {
                        currentSystemRecovery = 100f
                    }
                    updateDashboard(currentSystemRecovery)
                }
            }
            .addOnFailureListener {
                currentSystemRecovery = 100f
                updateDashboard(currentSystemRecovery)
            }
    }

    private fun fetchLatestCoachInsight() {
        db.collection("logs")
            .whereEqualTo("user_id", currentUserId)
            .whereEqualTo("log_type", "coach")
            .whereEqualTo("status", "confirmed")
            .get()
            .addOnSuccessListener { documents ->
                val sortedDocs = documents.sortedByDescending { it.getLong("timestamp") ?: 0L }
                if (sortedDocs.isNotEmpty()) {
                    val latestLog = sortedDocs.first()
                    val parsedData = latestLog.data["parsed_data"] as? Map<String, Any>
                    val message = parsedData?.get("coach_message")?.toString() ?: "Tap the refresh icon to get your daily insight!"
                    tvCoachMessage.text = message
                }
            }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        speechRecognizer.startListening(intent)
    }

    private fun sendTextToFirebase(text: String) {
        val logData = hashMapOf(
            "raw_text" to text,
            "status" to "pending",
            "timestamp" to System.currentTimeMillis(),
            "user_id" to currentUserId,
            "log_type" to currentLogType
        )
        db.collection("logs").add(logData).addOnSuccessListener { docRef ->
            listenForMacResponse(docRef.id)
        }
    }

    private fun requestCoachInsight() {
        tvCoachMessage.text = "Analyzing your last 14 days..."
        tvCoachMessage.setTextColor(Color.parseColor("#00B0FF"))

        val logData = hashMapOf(
            "status" to "pending",
            "timestamp" to System.currentTimeMillis(),
            "user_id" to currentUserId,
            "log_type" to "coach"
        )

        db.collection("logs").add(logData).addOnSuccessListener { docRef ->
            listenForMacResponse(docRef.id)
        }
    }

    private fun listenForMacResponse(documentId: String) {
        db.collection("logs").document(documentId).addSnapshotListener { snapshot, e ->
            if (snapshot != null && snapshot.exists() && snapshot.getString("status") == "confirmed") {
                val logType = snapshot.getString("log_type") ?: "workout"
                if (logType == "coach") {
                    val parsedData = snapshot.get("parsed_data") as? Map<String, Any>
                    val insight = parsedData?.get("coach_message")?.toString() ?: "Keep up the great work!"

                    tvCoachMessage.text = insight
                    tvCoachMessage.setTextColor(Color.parseColor("#FFFFFF"))
                }
            } else if (snapshot != null && snapshot.exists() && snapshot.getString("status") == "parsed") {
                val parsedData = snapshot.get("parsed_data") as? Map<String, Any>
                val logType = snapshot.getString("log_type") ?: "workout"

                if (parsedData != null) {
                    if (logType == "workout") {
                        showValidationDialog(documentId, parsedData)
                    } else if (logType == "food") {
                        showFoodValidationDialog(documentId, parsedData)
                    }
                }
            }
        }
    }

    private fun showValidationDialog(documentId: String, originalData: Map<String, Any>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_validation, null)
        val etActivity = dialogView.findViewById<EditText>(R.id.etActivity)
        val etDuration = dialogView.findViewById<EditText>(R.id.etDuration)

        etActivity.setText(originalData["exercise_name"]?.toString() ?: "")
        etDuration.setText(originalData["duration_minutes"]?.toString() ?: "")

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("CONFIRM") { _, _ ->

                val correctedActivity = etActivity.text.toString()
                val correctedDuration = etDuration.text.toString().toFloatOrNull() ?: 1f

                val newMetrics = recalculateLocally(correctedActivity, correctedDuration)
                currentSystemRecovery = newMetrics.recovery

                val updatedParsedData = mapOf(
                    "exercise_name" to correctedActivity,
                    "duration_minutes" to correctedDuration,
                    "calories_burnt" to newMetrics.calories,
                    "post_workout_recovery_pct" to newMetrics.recovery
                )

                updateDashboard(newMetrics.recovery)

                db.collection("logs").document(documentId).update(
                    mapOf(
                        "status" to "confirmed",
                        "parsed_data" to updatedParsedData
                    )
                ).addOnSuccessListener {
                    Toast.makeText(this, "Log updated and saved!", Toast.LENGTH_SHORT).show()
                    fetchUserGamificationStats() // Refresh XP immediately
                }
            }
            .setNegativeButton("RE-TRANSCRIBE") { _, _ ->
                db.collection("logs").document(documentId).delete()
                tvHeader.text = "DELETED. TAP MIC AGAIN."
                tvHeader.setTextColor(Color.parseColor("#FF1744"))
            }
            .show()
    }

    private fun showFoodValidationDialog(documentId: String, originalData: Map<String, Any>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_food_validation, null)
        val etFoodItems = dialogView.findViewById<EditText>(R.id.etFoodItems)
        val etCalories = dialogView.findViewById<EditText>(R.id.etCalories)
        val etProtein = dialogView.findViewById<EditText>(R.id.etProtein)
        val etCarbs = dialogView.findViewById<EditText>(R.id.etCarbs)
        val etFats = dialogView.findViewById<EditText>(R.id.etFats)

        val foodItems = originalData["food_items"]
        if (foodItems is List<*>) {
            etFoodItems.setText(foodItems.joinToString(", "))
        } else {
            etFoodItems.setText(foodItems?.toString() ?: "")
        }

        etCalories.setText(originalData["calories_consumed"]?.toString() ?: "0")
        etProtein.setText(originalData["protein_g"]?.toString() ?: "0")
        etCarbs.setText(originalData["carbs_g"]?.toString() ?: "0")
        etFats.setText(originalData["fats_g"]?.toString() ?: "0")

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("CONFIRM") { _, _ ->
                val updatedParsedData = mapOf(
                    "food_items" to etFoodItems.text.toString().split(",").map { it.trim() },
                    "calories_consumed" to (etCalories.text.toString().toFloatOrNull() ?: 0f),
                    "protein_g" to (etProtein.text.toString().toFloatOrNull() ?: 0f),
                    "carbs_g" to (etCarbs.text.toString().toFloatOrNull() ?: 0f),
                    "fats_g" to (etFats.text.toString().toFloatOrNull() ?: 0f)
                )

                db.collection("logs").document(documentId).update(
                    mapOf(
                        "status" to "confirmed",
                        "parsed_data" to updatedParsedData
                    )
                ).addOnSuccessListener {
                    tvHeader.text = "FOOD LOG SAVED"
                    tvHeader.setTextColor(Color.parseColor("#00E676"))
                    Toast.makeText(this, "Macros Analyzed & Saved!", Toast.LENGTH_SHORT).show()
                    fetchUserGamificationStats() // Refresh XP immediately
                }
            }
            .setNegativeButton("RE-TRANSCRIBE") { _, _ ->
                db.collection("logs").document(documentId).delete()
                tvHeader.text = "DELETED. TAP MIC AGAIN."
                tvHeader.setTextColor(Color.parseColor("#FF1744"))
            }
            .show()
    }

    private fun recalculateLocally(activity: String, durationMins: Float): WorkoutMetrics {
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

        val fatigueDrop = (met * durationMins * 0.1f)
        var newRecovery = currentSystemRecovery - fatigueDrop
        if (newRecovery < 0f) newRecovery = 0f

        return WorkoutMetrics(calories, newRecovery)
    }

    private fun updateDashboard(recoveryFloat: Float) {
        val recoveryScore = recoveryFloat.roundToInt()

        val zoneColor = when {
            recoveryScore >= 80 -> Color.parseColor("#00E676")
            recoveryScore >= 50 -> Color.parseColor("#FFEA00")
            recoveryScore >= 25 -> Color.parseColor("#FF9100")
            else -> Color.parseColor("#FF1744")
        }

        tvHeader.text = "DASHBOARD READY"
        tvHeader.setTextColor(Color.parseColor("#888888"))
        tvRecoveryPercentage.text = "$recoveryScore%"
        tvRecoveryPercentage.setTextColor(zoneColor)

        pbRecovery.progress = recoveryScore
        pbRecovery.progressTintList = android.content.res.ColorStateList.valueOf(zoneColor)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}