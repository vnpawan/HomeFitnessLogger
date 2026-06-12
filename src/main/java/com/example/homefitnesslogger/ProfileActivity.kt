package com.example.homefitnesslogger

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ProfileActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: return

        val etAge = findViewById<EditText>(R.id.etAge)
        val etGender = findViewById<EditText>(R.id.etGender)
        val etWeight = findViewById<EditText>(R.id.etWeight)
        val etHeight = findViewById<EditText>(R.id.etHeight)
        val etSuperGoal = findViewById<EditText>(R.id.etSuperGoal)
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveProfile)

        // 1. Load current profile data
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                etAge.setText(doc.getLong("age")?.toString() ?: "30")
                etGender.setText(doc.getString("gender") ?: "male")
                etWeight.setText(doc.getDouble("weight_kg")?.toString() ?: "75.0")
                etHeight.setText(doc.getDouble("height_cm")?.toString() ?: "175.0")
                etSuperGoal.setText(doc.getString("super_goal") ?: "Get stronger and healthier")
            }
        }

        // 2. Save profile data securely
        btnSave.setOnClickListener {
            val profileMap = hashMapOf(
                "age" to (etAge.text.toString().toLongOrNull() ?: 30L),
                "gender" to etGender.text.toString().lowercase().trim(),
                "weight_kg" to (etWeight.text.toString().toDoubleOrNull() ?: 75.0),
                "height_cm" to (etHeight.text.toString().toDoubleOrNull() ?: 175.0),
                "super_goal" to etSuperGoal.text.toString().trim()
            )

            // We use SetOptions.merge() so it doesn't accidentally erase your current_streak or total_xp!
            db.collection("users").document(userId).set(profileMap, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile Saved!", Toast.LENGTH_SHORT).show()
                    finish() // Closes the screen and goes back to Dashboard
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show()
                }
        }
    }
}