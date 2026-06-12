

import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import threading
import json
import urllib.request
import urllib.parse
import math
import time
import ssl
import datetime
import os
# ==========================================
# SECRETS & CONFIGURATION
# ==========================================
# (When moving to the cloud, you'll eventually change this to use Environment Variables)
# Line 19 — replace hardcoded path with env var
cred = credentials.Certificate(os.environ.get("FIREBASE_CREDENTIALS_PATH"))

# Line 23 — replace hardcoded key with env var
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
firebase_admin.initialize_app(cred)
db = firestore.client()

# ==========================================
# EXPANDED METs DATABASE (At Home & Outdoor)
# ==========================================
METS_DB = {
    # ==========================================
    # CORE & ABDOMINALS
    # ==========================================
    "plank": 3.0,
    "side plank": 3.0,
    "plank with shoulder tap": 4.0,
    "plank to downward dog": 4.5,
    "situps": 4.0,
    "crunches": 4.0,
    "bicycle crunches": 5.0,
    "russian twists": 4.5,
    "leg raises": 4.0,
    "hanging leg raises": 5.0,
    "flutter kicks": 4.0,
    "scissor kicks": 4.0,
    "dead bug": 3.5,
    "hollow body hold": 3.5,
    "ab wheel rollout": 5.5,
    "dragon flag": 6.0,
    "toe touches": 3.5,
    "v-ups": 5.0,
    "windshield wipers": 5.5,
    "cable crunches": 4.0,

    # ==========================================
    # PUSH-BASED (CHEST, SHOULDERS, TRICEPS)
    # ==========================================
    "pushups": 8.0,
    "wide pushups": 8.0,
    "diamond pushups": 8.5,
    "archer pushups": 9.0,
    "decline pushups": 8.5,
    "incline pushups": 7.0,
    "pike pushups": 7.5,
    "hindu pushups": 8.5,
    "spiderman pushups": 9.0,
    "clap pushups": 10.0,
    "one arm pushups": 11.0,
    "handstand pushups": 10.0,
    "wall handstand hold": 4.0,
    "dips": 8.0,
    "bench dips": 7.5,
    "ring dips": 9.0,
    "overhead press": 6.0,
    "arnold press": 6.0,
    "lateral raises": 4.5,
    "front raises": 4.5,
    "chest flyes": 5.0,
    "chest press": 6.0,

    # ==========================================
    # PULL-BASED (BACK, BICEPS)
    # ==========================================
    "pullups": 8.0,
    "chinups": 8.0,
    "neutral grip pullups": 8.0,
    "wide grip pullups": 8.5,
    "archer pullups": 9.5,
    "one arm pullup": 12.0,
    "muscle ups": 11.0,
    "australian pullups": 6.5,
    "inverted rows": 6.0,
    "ring rows": 6.5,
    "face pulls": 4.5,
    "band pull aparts": 3.5,
    "superman holds": 3.0,
    "reverse snow angels": 3.5,
    "bent over rows": 6.0,
    "single arm rows": 5.5,
    "seated cable rows": 5.0,
    "lat pulldowns": 5.5,
    "bicep curls": 4.5,
    "hammer curls": 4.5,
    "concentration curls": 4.0,

    # ==========================================
    # LEGS & GLUTES
    # ==========================================
    "squats": 5.0,
    "jump squats": 8.5,
    "sumo squats": 5.5,
    "narrow squats": 5.0,
    "goblet squats": 6.0,
    "wall sit": 4.0,
    "pistol squats": 8.0,
    "bulgarian split squats": 7.5,
    "lunges": 5.5,
    "reverse lunges": 5.5,
    "walking lunges": 6.0,
    "lateral lunges": 6.0,
    "curtsy lunges": 6.0,
    "jump lunges": 9.0,
    "step ups": 6.0,
    "glute bridges": 4.5,
    "single leg glute bridge": 5.0,
    "hip thrusts": 5.5,
    "donkey kicks": 4.0,
    "fire hydrants": 3.5,
    "clamshells": 3.0,
    "side lying leg raises": 3.0,
    "good mornings": 5.0,
    "romanian deadlifts": 6.5,
    "deadlifts": 7.0,
    "sumo deadlifts": 7.0,
    "leg press": 5.5,
    "leg extensions": 4.5,
    "leg curls": 4.5,
    "calf raises": 3.5,
    "single leg calf raises": 4.0,
    "nordic curls": 8.0,
    "frog pumps": 4.0,

    # ==========================================
    # FULL BODY / COMPOUND
    # ==========================================
    "burpees": 8.0,
    "burpee box jumps": 10.0,
    "thrusters": 9.0,
    "clean and press": 9.5,
    "kettlebell swings": 9.5,
    "kettlebell snatches": 10.0,
    "kettlebell cleans": 9.0,
    "turkish get ups": 7.0,
    "man makers": 10.0,
    "dumbbell complexes": 9.0,
    "barbell complexes": 9.5,
    "sandbag carries": 8.0,
    "farmer carries": 7.5,
    "suitcase carries": 7.0,
    "bear crawl": 8.0,
    "crab walk": 6.5,
    "inchworm": 5.5,
    "mountain climbers": 8.0,
    "sprawls": 8.5,

    # ==========================================
    # JUMPING & PLYOMETRICS
    # ==========================================
    "jump rope": 10.0,
    "double unders": 12.0,
    "box jumps": 10.0,
    "depth jumps": 10.5,
    "broad jumps": 9.5,
    "tuck jumps": 10.0,
    "star jumps": 8.5,
    "jumping jacks": 8.0,
    "high knees": 8.5,
    "butt kicks": 7.5,
    "lateral bounds": 9.0,
    "single leg hops": 9.0,
    "skipping": 8.5,
    "bounding": 9.5,
    "hurdle jumps": 10.0,
    "trampoline jumping": 7.5,

    # ==========================================
    # CARDIO (OUTDOOR & MACHINE)
    # ==========================================
    "walking": 3.5,
    "brisk walking": 4.5,
    "jogging": 7.0,
    "running": 9.8,
    "sprinting": 14.0,
    "interval running": 11.0,
    "trail running": 11.0,
    "uphill running": 12.0,
    "treadmill walking": 4.0,
    "treadmill running": 9.8,
    "cycling": 7.5,
    "road cycling": 8.0,
    "stationary cycling": 7.0,
    "spinning": 10.5,
    "mountain biking": 10.0,
    "rowing machine": 8.5,
    "rowing": 8.5,
    "elliptical": 6.5,
    "stair climber": 9.0,
    "stairs": 8.0,
    "hiking": 6.0,
    "hiking with pack": 7.5,
    "swimming": 7.0,
    "freestyle swimming": 8.5,
    "breaststroke": 7.0,
    "butterfly stroke": 10.0,
    "backstroke": 7.5,
    "water aerobics": 5.5,
    "skiing": 7.0,
    "cross country skiing": 9.5,
    "speed skating": 10.0,
    "rollerblading": 7.5,

    # ==========================================
    # HIIT & CIRCUIT TRAINING
    # ==========================================
    "hiit": 10.0,
    "circuit training": 8.0,
    "tabata": 10.5,
    "amrap": 9.5,
    "emom": 8.5,
    "crossfit": 10.0,
    "boot camp": 10.0,

    # ==========================================
    # GYMNASTICS & CALISTHENICS SKILLS
    # ==========================================
    "handstand walk": 8.0,
    "handstand balance": 4.0,
    "l-sit": 5.0,
    "v-sit": 6.0,
    "front lever": 8.5,
    "back lever": 8.0,
    "planche": 9.5,
    "iron cross": 10.0,
    "human flag": 10.0,
    "cartwheel": 5.5,
    "round off": 6.5,
    "backflip": 9.0,
    "pistol squat hold": 5.5,
    "ring support hold": 5.0,
    "skin the cat": 7.5,
    "german hang": 4.0,

    # ==========================================
    # YOGA & FLEXIBILITY
    # ==========================================
    "yoga": 3.0,
    "power yoga": 5.0,
    "vinyasa yoga": 4.5,
    "hot yoga": 5.5,
    "ashtanga yoga": 5.5,
    "yin yoga": 2.5,
    "restorative yoga": 2.0,
    "stretching": 2.3,
    "dynamic stretching": 3.0,
    "foam rolling": 2.5,
    "pilates": 4.0,
    "mat pilates": 4.0,
    "reformer pilates": 5.0,
    "barre": 4.5,

    # ==========================================
    # MARTIAL ARTS & COMBAT SPORTS
    # ==========================================
    "boxing": 10.5,
    "shadow boxing": 9.0,
    "heavy bag punching": 10.0,
    "kickboxing": 10.5,
    "muay thai": 10.5,
    "bjj": 9.5,
    "wrestling": 10.0,
    "judo": 10.0,
    "karate": 9.0,
    "taekwondo": 9.5,
    "mma training": 11.0,

    # ==========================================
    # DANCE & RHYTHMIC
    # ==========================================
    "zumba": 8.5,
    "dance": 7.5,
    "hip hop dance": 8.5,
    "aerobics": 7.5,
    "step aerobics": 8.5,
    "jazzercise": 7.5,
    "belly dancing": 5.5,
    "ballroom dancing": 5.5,
    "salsa dancing": 6.5,

    # ==========================================
    # SPORT-SPECIFIC DRILLS
    # ==========================================
    "agility ladder drills": 9.0,
    "cone drills": 9.0,
    "shuttle runs": 10.5,
    "sled push": 11.0,
    "sled pull": 10.5,
    "tire flips": 10.0,
    "battle ropes": 10.0,
    "medicine ball slams": 9.5,
    "medicine ball throws": 8.5,
    "wall ball shots": 9.5,
    "sandbag throws": 9.0,
    "parachute sprints": 13.0,

    # ==========================================
    # RECOVERY & LOW INTENSITY
    # ==========================================
    "walking meditation": 2.5,
    "tai chi": 3.0,
    "qigong": 2.5,
    "breathing exercises": 1.5,
    "cold exposure": 2.0,
    "sauna": 2.5,
    "light housework": 3.0,
    "gardening": 4.0,
}


def calculate_bmr(weight, height, age, gender):
    if gender.lower() == "male":
        return (10 * weight) + (6.25 * height) - (5 * age) + 5
    else:
        return (10 * weight) + (6.25 * height) - (5 * age) - 161


def calculate_entropy_recovery_decay(mets, duration_mins):
    strain_load = mets * duration_mins
    immediate_recovery_drop = max(0, 100 - (strain_load * 0.15))
    return round(immediate_recovery_drop, 1)


# ==========================================
# PURE CLOUD AI PIPELINES
# ==========================================

def parse_workout_with_gemini(text_log):
    """Uses Gemini to extract workout details, replacing the local model for cloud deployment."""
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key={GEMINI_API_KEY}"
    prompt = f"""
    You are a fitness data extractor. The user will provide a workout description.
    Extract the exercise details.
    Return STRICTLY valid JSON. No markdown, no conversational text.

    Required keys: 
    "activity_type" (string), 
    "exercise_name" (string, lowercase), 
    "duration_minutes" (float).

    User Text: {text_log}
    """
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"response_mime_type": "application/json"}
    }
    try:
        data = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
        context = ssl._create_unverified_context()
        with urllib.request.urlopen(req, context=context) as response:
            res_body = json.loads(response.read().decode('utf-8'))
            text_response = res_body['candidates'][0]['content']['parts'][0]['text']
            return json.loads(text_response)
    except Exception as e:
        print(f"❌ Gemini Workout API call failed: {e}")
        return None


def parse_food_with_gemini(text_log):
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key={GEMINI_API_KEY}"
    prompt = f"""
    You are an expert in Indian cuisine and clinical nutrition. Estimate the total nutritional value.
    Return STRICTLY valid JSON. No markdown.
    Keys: "food_items" (list of strings), "calories_consumed" (float), "protein_g" (float), "carbs_g" (float), "fats_g" (float)
    User meal: {text_log}
    """
    payload = {"contents": [{"parts": [{"text": prompt}]}],
               "generationConfig": {"response_mime_type": "application/json"}}
    try:
        data = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
        context = ssl._create_unverified_context()
        with urllib.request.urlopen(req, context=context) as response:
            res_body = json.loads(response.read().decode('utf-8'))
            text_response = res_body['candidates'][0]['content']['parts'][0]['text']
            return json.loads(text_response)
    except Exception as e:
        print(f"❌ Gemini Food API call failed: {e}")
        return None


def generate_coach_insight(user_id, super_goal):
    now = time.time() * 1000
    fourteen_days_ago = now - (14 * 24 * 60 * 60 * 1000)
    logs_ref = db.collection('logs').where("user_id", "==", user_id).where("status", "==", "confirmed").stream()

    log_summaries = []
    for doc in logs_ref:
        data = doc.to_dict()
        ts = data.get("timestamp", 0)
        if ts >= fourteen_days_ago:
            parsed = data.get("parsed_data", {})
            l_type = data.get("log_type", "workout")
            date_str = datetime.datetime.fromtimestamp(ts / 1000.0).strftime('%b %d')
            if l_type == "workout":
                log_summaries.append(
                    f"[{date_str}] Workout: {parsed.get('exercise_name')} for {parsed.get('duration_minutes')} mins. Burnt {parsed.get('calories_burnt')} kcal.")
            elif l_type == "food":
                items = parsed.get("food_items", [])
                item_str = ", ".join(items) if isinstance(items, list) else str(items)
                log_summaries.append(f"[{date_str}] Meal: {item_str}. Consumed {parsed.get('calories_consumed')} kcal.")

    summary_text = "\n".join(log_summaries) if log_summaries else "No logs in 14 days. Fresh start!"

    prompt = f"""
    You are an empathetic, joyful AI health coach tailored for Indian lifestyles. 
    User's 'Super Goal': "{super_goal}".
    Raw 14-day data:\n{summary_text}

    Instructions: Write a short, encouraging message (3-4 sentences). NEVER scold. Provide one gentle tip for today. Return ONLY the text.
    """
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key={GEMINI_API_KEY}"
    payload = {"contents": [{"parts": [{"text": prompt}]}]}
    try:
        data = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
        context = ssl._create_unverified_context()
        with urllib.request.urlopen(req, context=context) as response:
            res_body = json.loads(response.read().decode('utf-8'))
            return res_body['candidates'][0]['content']['parts'][0]['text'].strip()
    except Exception as e:
        print(f"❌ Gemini Coach API call failed: {e}")
        return "Keep going, I'm incredibly proud of your effort today!"


# ==========================================
# GAMIFICATION ENGINE (XP & STREAKS)
# ==========================================
def calculate_gamification(user_ref, profile):
    today = datetime.datetime.now().date()
    today_str = today.strftime("%Y-%m-%d")

    last_log_date_str = profile.get("last_log_date", "")
    logs_today = profile.get("logs_today", 0)
    current_streak = profile.get("current_streak", 0)
    total_xp = profile.get("total_xp", 0)

    if last_log_date_str:
        last_date = datetime.datetime.strptime(last_log_date_str, "%Y-%m-%d").date()
        days_missed = (today - last_date).days
    else:
        days_missed = 0

    if days_missed == 0:
        logs_today += 1
    elif days_missed == 1:
        current_streak += 1
        logs_today = 1
    else:
        decay_factor = math.exp(-0.3 * (days_missed - 1))
        current_streak = max(1, math.floor(current_streak * decay_factor))
        logs_today = 1

    earned_xp = max(1, math.floor(50 * math.pow(0.5, logs_today - 1)))
    total_xp += earned_xp

    user_ref.update({
        "last_log_date": today_str,
        "logs_today": logs_today,
        "current_streak": current_streak,
        "total_xp": total_xp
    })

    return earned_xp, current_streak


# ==========================================
# THE TRAFFIC DIRECTOR
# ==========================================
def process_log(doc_snapshot):
    doc_id = doc_snapshot.id
    data = doc_snapshot.to_dict()
    raw_text = data.get("raw_text", "")
    user_id = data.get("user_id", "")
    log_type = data.get("log_type", "workout").lower()

    print(f"\n[RECEIVED] '{raw_text}' | Type: {log_type.upper()} | User: {user_id}")
    start_time = time.time()

    if not user_id:
        db.collection('logs').document(doc_id).update({"status": "failed"})
        return

    user_ref = db.collection('users').document(user_id)
    user_doc = user_ref.get()

    if user_doc.exists:
        profile = user_doc.to_dict()
        age, gender = profile.get("age", 30), profile.get("gender", "male")
        weight, height = profile.get("weight_kg", 75.0), profile.get("height_cm", 175.0)
        super_goal = profile.get("super_goal", "Get stronger and healthier")
    else:
        age, gender, weight, height = 30, "male", 75.0, 175.0
        super_goal = "Get stronger and healthier"
        profile = {
            "age": age, "gender": gender, "weight_kg": weight, "height_cm": height,
            "super_goal": super_goal, "current_streak": 0, "total_xp": 0, "logs_today": 0
        }
        user_ref.set(profile)

    earned_xp, current_streak = 0, profile.get("current_streak", 0)
    if log_type in ["workout", "food"]:
        earned_xp, current_streak = calculate_gamification(user_ref, profile)
        print(f"[GAMIFICATION] Awarded {earned_xp} XP. Current Streak: {current_streak}")

    if log_type == "workout":
        parsed_json = parse_workout_with_gemini(raw_text)  # UPDATED TO USE GEMINI
        if parsed_json:
            exercise = parsed_json.get("exercise_name", "").lower()
            duration = parsed_json.get("duration_minutes", 0)
            mets = next((v for k, v in METS_DB.items() if k in exercise), 5.0)

            bmr = calculate_bmr(weight, height, age, gender)
            parsed_json["calories_burnt"] = round(mets * (bmr / 24.0) * (duration / 60.0), 2)
            parsed_json["post_workout_recovery_pct"] = calculate_entropy_recovery_decay(mets, duration)
            parsed_json["mets_used"] = mets
            parsed_json["xp_earned"] = earned_xp

            db.collection('logs').document(doc_id).update({"status": "parsed", "parsed_data": parsed_json})
            print(f"[SUCCESS] Workout processed via Gemini in {round(time.time() - start_time, 2)}s.")
        else:
            db.collection('logs').document(doc_id).update({"status": "failed"})

    elif log_type == "food":
        parsed_json = parse_food_with_gemini(raw_text)
        if parsed_json:
            parsed_json["xp_earned"] = earned_xp
            db.collection('logs').document(doc_id).update({"status": "parsed", "parsed_data": parsed_json})
            print(f"[SUCCESS] Food processed via Gemini in {round(time.time() - start_time, 2)}s.")
        else:
            db.collection('logs').document(doc_id).update({"status": "failed"})

    elif log_type == "coach":
        insight = generate_coach_insight(user_id, super_goal)
        db.collection('logs').document(doc_id).update(
            {"status": "confirmed", "parsed_data": {"coach_message": insight}})
        print(f"[SUCCESS] Coach insight generated in {round(time.time() - start_time, 2)}s.")


def on_snapshot(col_snapshot, changes, read_time):
    for change in changes:
        if change.document.to_dict().get("status") == "pending":
            process_log(change.document)


collection_watch = db.collection('logs').on_snapshot(on_snapshot)
print("☁️ Cloud Brain V1 (All-Gemini Architecture) is online...")

event = threading.Event()
event.wait()