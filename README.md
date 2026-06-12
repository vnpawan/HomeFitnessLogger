# 🏋️ Home Fitness AI Logger

An Android fitness app where you log workouts and meals by speaking. No forms, no typing — tap a button, say what you did, and a Python backend powered by Gemini handles the rest.

> **Honest disclaimer:** This was built for one specific person with one very specific workflow. It may be useful to you. It may also be a completely opinionated system that makes no sense outside of my life. Either outcome is valid.

---

## How It Works

The architecture is a three-layer pipeline:

```
Android App  ──►  Firestore  ──►  Python Backend (main.py)  ──►  Gemini API
     ▲                                      │
     └──────────── parsed result ───────────┘
```

1. User taps WORKOUT or FOOD, speaks into the mic
2. Android writes raw text to Firestore with `status: "pending"`
3. `main.py` listens to Firestore in real-time via `on_snapshot`
4. Backend calls Gemini to parse the log, calculates calories/macros, updates XP/streak
5. Firestore document is updated with `status: "confirmed"` + `parsed_data`
6. Android picks up the confirmed result and updates the UI

This async handshake pattern keeps the Android app completely stateless — no API keys on the device.

---

## Features

- **Voice logging** — workouts and meals parsed by Gemini from natural speech
- **Calorie calculation** — MET-based formula using your BMR (Mifflin-St Jeor)
- **Recovery Score** — entropy-decay model that drops based on workout strain and recovers over time
- **AI Coach** — personalized daily insight from Gemini based on your last 14 days of logs and a "Super Goal" you define
- **Gamification** — XP with diminishing returns per session per day, streak with exponential decay on missed days
- **Trends** — recovery %, calorie history (7/30/90 days), workout frequency (15 days)
- **Indian cuisine nutrition** — food parser prompt is specifically tuned for Indian meals

---

## Repo Structure

```
home-fitness-ai-logger/
│
├── android/                          # Android app (Kotlin)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/example/homefitnesslogger/
│   │   │   │   ├── MainActivity.kt         # Core screen: recovery, coach, voice FABs
│   │   │   │   ├── LoginActivity.kt        # Firebase Auth
│   │   │   │   ├── ProfileActivity.kt      # Age, weight, height, Super Goal
│   │   │   │   ├── HistoryActivity.kt      # Last 10 logs
│   │   │   │   ├── TrendsActivity.kt       # Charts dashboard
│   │   │   │   ├── AppLog.kt               # Log data model
│   │   │   │   └── WorkoutMetrics.kt       # Recovery score logic
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── build.gradle
│   │   └── google-services.json            # ← DO NOT COMMIT
│   └── build.gradle
│
├── backend/                          # Python backend (runs locally or on a server)
│   ├── main.py                       # Firestore listener + Gemini pipeline
│   ├── serviceAccountKey.json        # ← DO NOT COMMIT (Firebase admin credentials)
│   └── requirements.txt
│
├── .gitignore
└── README.md
```

---

## Backend: main.py

The Python backend (`main.py`) is the brain of the system. It runs as a persistent process and handles everything the Android app doesn't:

| Function | What it does |
|---|---|
| `parse_workout_with_gemini()` | Extracts exercise name, type, duration from free text |
| `parse_food_with_gemini()` | Estimates calories, protein, carbs, fats from meal description |
| `generate_coach_insight()` | Generates a personalized 3–4 sentence coaching message |
| `calculate_bmr()` | Mifflin-St Jeor BMR formula |
| `calculate_entropy_recovery_decay()` | Recovery % drop based on MET × duration |
| `calculate_gamification()` | XP (diminishing returns) + streak (exponential decay on missed days) |
| `process_log()` | Traffic director: routes pending docs to the right pipeline |
| `on_snapshot()` | Real-time Firestore listener — triggers on every new `"pending"` doc |

### MET Database

`main.py` includes a hand-curated MET (Metabolic Equivalent of Task) database covering 150+ exercises across: core, push, pull, legs, full body, plyometrics, cardio, HIIT, gymnastics, yoga, martial arts, dance, and recovery.

---

## Setup

### Prerequisites
- Android Studio Hedgehog or newer
- Python 3.9+
- A Firebase project
- A Google Gemini API key (get one at [aistudio.google.com](https://aistudio.google.com))

### Android App

1. Clone the repo and open the `android/` folder in Android Studio
2. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
3. Enable Firestore and Authentication (Email/Password + Google Sign-In)
4. Download `google-services.json` → place in `android/app/`
5. Build and run on a physical device (voice recognition needs a real mic)

### Python Backend

1. Install dependencies:
   ```bash
   pip install firebase-admin
   ```

2. Download your Firebase service account key:
   - Firebase Console → Project Settings → Service Accounts → Generate new private key
   - Save as `backend/serviceAccountKey.json`

3. Set your Gemini API key as an environment variable:
   ```bash
   export GEMINI_API_KEY="your_key_here"
   ```
   Then update `main.py` to read it:
   ```python
   import os
   GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
   ```

4. Run the backend:
   ```bash
   python main.py
   ```
   You should see: `☁️ Cloud Brain V1 (All-Gemini Architecture) is online...`

---

## Firestore Data Model

```
users/{userId}
    ├── age, gender, weight_kg, height_cm
    ├── super_goal: string
    ├── current_streak: int
    ├── total_xp: int
    ├── logs_today: int
    └── last_log_date: "YYYY-MM-DD"

logs/{logId}
    ├── user_id: string
    ├── log_type: "workout" | "food" | "coach"
    ├── raw_text: string                  ← written by Android
    ├── status: "pending" → "parsed" → "confirmed"
    └── parsed_data: {
            # workout
            exercise_name, duration_minutes, calories_burnt,
            post_workout_recovery_pct, mets_used, xp_earned
            
            # food
            food_items[], calories_consumed, protein_g, carbs_g, fats_g, xp_earned
            
            # coach
            coach_message: string
        }
```

---


## Known Limitations

- The backend runs as a local Python process, not a cloud function. If your laptop sleeps, logs queue up until it wakes.
- Recovery score is a simplified entropy-decay heuristic, not a validated sports science formula.
- Food parser is tuned for Indian cuisine. Results for other cuisines may be optimistic.
- The app is named `HomeFItnessLogger` with a capital I in the middle. This is a bug that has become a personality trait.

---

## License

MIT — do whatever you want with it.

---

*Built by [Pawan Vedanti](https://linkedin.com/in/pawan-vedanti-c1992)*
