package com.example.data

import com.google.firebase.firestore.ListenerRegistration
import com.example.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.UUID
import android.util.Log

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.flow

class FirebaseIronLogRepository(private val context: Context) : IronLogRepository {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    
    init {
        try {
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            firestore.firestoreSettings = settings
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Failed to configure Firestore offline persistence", e)
        }
    }
    
    private val localFallback by lazy {
        SharedPrefsIronLogRepository(context)
    }
    
    private val uid: String
        get() {
            val currentUid = auth.currentUser?.uid
            if (currentUid == null) {
                Log.w("FirebaseRepo", "Warning: UID property accessed while auth.currentUser is NULL. Falling back to local_test_user.")
            }
            return currentUid ?: "local_test_user"
        }
        
    private fun exercisesCollection() = firestore.collection("users").document(uid).collection("exercises")
    private fun templatesCollection() = firestore.collection("users").document(uid).collection("templates")
    private fun workoutsCollection() = firestore.collection("users").document(uid).collection("workouts")
    private fun prsCollection() = firestore.collection("users").document(uid).collection("personalRecords")
    private fun sessionDoc() = firestore.collection("users").document(uid).collection("settings").document("activeSession")
    private fun programMetaDoc() = firestore.collection("users").document(uid).collection("programState").document("active")
    private fun programWeekDoc(weekNumber: Int) = firestore.collection("users").document(uid).collection("program").document("week$weekNumber").also {
        Log.v("FirebaseRepo", "Target Week Path: ${it.path}")
    }

    override fun getExercises(): Flow<List<Exercise>> {
        if (auth.currentUser == null) return localFallback.getExercises()
        return callbackFlow {
            val col = exercisesCollection()
            val listener = col.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseRepo", "Error", e)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val exercises = snapshot.documents.mapNotNull {
                        try {
                            it.toObject(Exercise::class.java)
                        } catch (ex: Exception) {
                            Log.e("FirebaseRepo", "Error deserializing exercise document", ex)
                            null
                        }
                    }
                    trySend(exercises.sortedBy { it.name })
                }
            }
            awaitClose { listener.remove() }
        }
    }

    override suspend fun addExercise(exercise: Exercise) {
        val id = if (exercise.id.isEmpty()) UUID.randomUUID().toString() else exercise.id
        val finalExercise = exercise.copy(id = id)
        localFallback.addExercise(finalExercise)
        if (auth.currentUser == null) {
            return
        }
        try {
            val col = exercisesCollection()
            col.document(id).set(finalExercise).await()
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error adding exercise", e)
        }
    }

    override suspend fun seedInitialExercises() {
        if (auth.currentUser == null) {
            localFallback.seedInitialExercises()
            return
        }
        try {
            val col = exercisesCollection()
            val current = col.get().await()
            if (!current.isEmpty) return // already seeded
            
            val defaultExercises = listOf(
                Exercise(id = UUID.randomUUID().toString(), name = "Bench Press", muscleGroup = "Chest", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Incline Dumbbell Press", muscleGroup = "Chest", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Back Squat", muscleGroup = "Legs", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Deadlift", muscleGroup = "Back", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Overhead Press", muscleGroup = "Shoulders", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Barbell Row", muscleGroup = "Back", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Pull-up", muscleGroup = "Back", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Lat Pulldown", muscleGroup = "Back", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Leg Press", muscleGroup = "Legs", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Romanian Deadlift", muscleGroup = "Legs", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Lateral Raise", muscleGroup = "Shoulders", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Bicep Curl", muscleGroup = "Arms", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Tricep Extension", muscleGroup = "Arms", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Leg Extension", muscleGroup = "Legs", isCustom = false),
                Exercise(id = UUID.randomUUID().toString(), name = "Plank", muscleGroup = "Core", isCustom = false)
            )
            
            firestore.runBatch { batch ->
                for (ex in defaultExercises) {
                    batch.set(col.document(ex.id), ex)
                }
            }.await()
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error seeding exercises", e)
        }
    }

    override fun getTemplates(): Flow<List<Template>> = callbackFlow {
        val localJob = launch {
            localFallback.getTemplates().collect {
                trySend(it)
            }
        }
        var registration: ListenerRegistration? = null
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            registration?.remove()
            val user = firebaseAuth.currentUser
            if (user != null) {
                val col = templatesCollection()
                registration = col.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("FirebaseRepo", "Firestore templates listen error", e)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        try {
                            val templates = snapshot.documents.mapNotNull { doc ->
                                try {
                                    doc.toObject(Template::class.java)
                                } catch (ex: Exception) {
                                    Log.e("FirebaseRepo", "toObject(Template) failed, trying manual fallback", ex)
                                    try {
                                        val id = doc.getString("id") ?: doc.id
                                        val name = doc.getString("name") ?: ""
                                        val exercisesRaw = doc.get("exercises") as? List<*> ?: emptyList<Any>()
                                        val exercises = exercisesRaw.map { item ->
                                            val map = item as? Map<*, *>
                                            TemplateExercise(
                                                exerciseId = map?.get("exerciseId") as? String ?: "",
                                                exerciseName = map?.get("exerciseName") as? String ?: "",
                                                targetSets = (map?.get("targetSets") as? Number)?.toInt() ?: 3,
                                                targetReps = (map?.get("targetReps") as? Number)?.toInt() ?: 10,
                                                order = (map?.get("order") as? Number)?.toInt() ?: 0,
                                                videoUrl = map?.get("videoUrl") as? String
                                            )
                                        }
                                        Template(id = id, name = name, exercises = exercises)
                                    } catch (fallbackEx: Exception) {
                                        Log.e("FirebaseRepo", "Fallback manual mapping also failed", fallbackEx)
                                        null
                                    }
                                }
                            }
                            launch {
                                localFallback.clearAllTemplates()
                                for (t in templates) {
                                    localFallback.saveTemplate(t)
                                }
                            }
                            trySend(templates.sortedBy { it.name })
                        } catch (exAll: Exception) {
                            Log.e("FirebaseRepo", "Error mapping templates list", exAll)
                        }
                    }
                }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
            registration?.remove()
            localJob.cancel()
        }
    }

    override suspend fun saveTemplate(template: Template) {
        val id = if (template.id.isEmpty()) UUID.randomUUID().toString() else template.id
        val finalTemplate = template.copy(id = id)
        localFallback.saveTemplate(finalTemplate)
        if (auth.currentUser == null) {
            return
        }
        try {
            val col = templatesCollection()
            col.document(id).set(finalTemplate).await()
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error saving template", e)
        }
    }

    override suspend fun deleteTemplate(templateId: String) {
        localFallback.deleteTemplate(templateId)
        if (auth.currentUser == null) {
            return
        }
        try {
            templatesCollection().document(templateId).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error deleting template", e)
        }
    }

    override suspend fun clearAllTemplates() {
        localFallback.clearAllTemplates()
        if (auth.currentUser == null) {
            return
        }
        try {
            val col = templatesCollection()
            val snapshot = col.get().await()
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error clearing templates", e)
        }
    }

    override fun getWorkouts(): Flow<List<Workout>> = callbackFlow {
        val localJob = launch {
            localFallback.getWorkouts().collect {
                trySend(it)
            }
        }
        var registration: ListenerRegistration? = null
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            registration?.remove()
            val user = firebaseAuth.currentUser
            if (user != null) {
                val col = workoutsCollection()
                registration = col.orderBy("date", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.e("FirebaseRepo", "Firestore list listen error", e)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            try {
                                val workouts = snapshot.documents.mapNotNull { doc ->
                                    try {
                                        val workout = doc.toObject(Workout::class.java)
                                        if (workout != null && workout.loggedExercises.isNotEmpty()) {
                                            workout
                                        } else {
                                            Log.d("FirebaseRepo", "getWorkouts: toObject yielded empty exercises, using manual mapping for ${doc.id}")
                                            val id = doc.getString("id") ?: doc.id
                                            val date = doc.getLong("date") ?: 0L
                                            val templateId = doc.getString("templateId")
                                            val templateName = doc.getString("templateName")
                                            val status = doc.getString("status") ?: "in_progress"
                                            val durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 0
                                            val totalVolume = doc.getDouble("totalVolume") ?: 0.0
                                            
                                            val loggedRaw = doc.get("loggedExercises") as? List<*> ?: emptyList<Any>()
                                            val loggedExercises = loggedRaw.map { item ->
                                                val exMap = item as? Map<*, *>
                                                val exerciseId = exMap?.get("exerciseId") as? String ?: ""
                                                val exerciseName = exMap?.get("exerciseName") as? String ?: ""
                                                val videoUrl = exMap?.get("videoUrl") as? String
                                                
                                                val setsRaw = exMap?.get("sets") as? List<*> ?: emptyList<Any>()
                                                val sets = setsRaw.map { setItem ->
                                                    val setMap = setItem as? Map<*, *>
                                                    WorkoutSet(
                                                        setNumber = (setMap?.get("setNumber") as? Number)?.toInt() ?: 1,
                                                        weight = (setMap?.get("weight") as? Number)?.toDouble() ?: 0.0,
                                                        reps = (setMap?.get("reps") as? Number)?.toInt() ?: 0,
                                                        isWarmup = (setMap?.get("warmup") as? Boolean ?: setMap?.get("isWarmup") as? Boolean ?: false),
                                                        completedAt = (setMap?.get("completedAt") as? Number)?.toLong(),
                                                        rpe = (setMap?.get("rpe") as? Number)?.toFloat()
                                                    )
                                                }
                                                LoggedExercise(
                                                    exerciseId = exerciseId,
                                                    exerciseName = exerciseName,
                                                    videoUrl = videoUrl,
                                                    sets = sets
                                                )
                                            }
                                            Workout(
                                                id = id,
                                                date = date,
                                                templateId = templateId,
                                                templateName = templateName,
                                                status = status,
                                                durationMinutes = durationMinutes,
                                                loggedExercises = loggedExercises,
                                                totalVolume = totalVolume
                                            )
                                        }
                                    } catch (ex: Exception) {
                                        Log.e("FirebaseRepo", "toObject(Workout) failed, manual fallback", ex)
                                        try {
                                            val id = doc.getString("id") ?: doc.id
                                            val date = doc.getLong("date") ?: 0L
                                            val templateId = doc.getString("templateId")
                                            val templateName = doc.getString("templateName")
                                            val status = doc.getString("status") ?: "in_progress"
                                            val durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 0
                                            val totalVolume = doc.getDouble("totalVolume") ?: 0.0
                                            
                                            val loggedRaw = doc.get("loggedExercises") as? List<*> ?: emptyList<Any>()
                                            val loggedExercises = loggedRaw.map { item ->
                                                val exMap = item as? Map<*, *>
                                                val exerciseId = exMap?.get("exerciseId") as? String ?: ""
                                                val exerciseName = exMap?.get("exerciseName") as? String ?: ""
                                                val videoUrl = exMap?.get("videoUrl") as? String
                                                
                                                val setsRaw = exMap?.get("sets") as? List<*> ?: emptyList<Any>()
                                                val sets = setsRaw.map { setItem ->
                                                    val setMap = setItem as? Map<*, *>
                                                    WorkoutSet(
                                                        setNumber = (setMap?.get("setNumber") as? Number)?.toInt() ?: 1,
                                                        weight = (setMap?.get("weight") as? Number)?.toDouble() ?: 0.0,
                                                        reps = (setMap?.get("reps") as? Number)?.toInt() ?: 0,
                                                        isWarmup = (setMap?.get("warmup") as? Boolean ?: setMap?.get("isWarmup") as? Boolean ?: false),
                                                        completedAt = (setMap?.get("completedAt") as? Number)?.toLong(),
                                                        rpe = (setMap?.get("rpe") as? Number)?.toFloat()
                                                    )
                                                }
                                                LoggedExercise(
                                                    exerciseId = exerciseId,
                                                    exerciseName = exerciseName,
                                                    videoUrl = videoUrl,
                                                    sets = sets
                                                )
                                            }
                                            Workout(
                                                id = id,
                                                date = date,
                                                templateId = templateId,
                                                templateName = templateName,
                                                status = status,
                                                durationMinutes = durationMinutes,
                                                loggedExercises = loggedExercises,
                                                totalVolume = totalVolume
                                            )
                                        } catch (fallbackEx: Exception) {
                                            Log.e("FirebaseRepo", "Fallback manual mapping failed for Workout", fallbackEx)
                                            null
                                        }
                                    }
                                }
                                launch {
                                    for (w in workouts) {
                                        localFallback.saveWorkout(w)
                                    }
                                }
                                trySend(workouts)
                            } catch (exAll: Exception) {
                                Log.e("FirebaseRepo", "Error mapping workouts", exAll)
                            }
                        }
                    }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
            registration?.remove()
            localJob.cancel()
        }
    }

    override fun getActiveWorkout(): Flow<Workout?> = callbackFlow {
        val localJob = launch {
            localFallback.getActiveWorkout().collect {
                trySend(it)
            }
        }
        
        var authRegistration: FirebaseAuth.AuthStateListener? = null
        var sessionRegistration: ListenerRegistration? = null
        var workoutRegistration: ListenerRegistration? = null
        var fallbackRegistration: ListenerRegistration? = null

        fun cleanupListeners() {
            sessionRegistration?.remove()
            workoutRegistration?.remove()
            fallbackRegistration?.remove()
            sessionRegistration = null
            workoutRegistration = null
            fallbackRegistration = null
        }

        authRegistration = FirebaseAuth.AuthStateListener { firebaseAuth ->
            cleanupListeners()
            val user = firebaseAuth.currentUser
            if (user != null) {
                // Primary: Listen to explicit session document
                sessionRegistration = sessionDoc().addSnapshotListener { sessionSnapshot, sessionError ->
                    workoutRegistration?.remove()
                    workoutRegistration = null
                    
                    if (sessionError != null) {
                        Log.e("FirebaseRepo", "Session listen error", sessionError)
                        return@addSnapshotListener
                    }
                    
                    val activeSession = sessionSnapshot?.toObject(ActiveWorkoutSession::class.java)
                    if (activeSession != null && activeSession.workoutId.isNotEmpty()) {
                        // Nested: Listen to the EXACT workout document
                        fallbackRegistration?.remove()
                        fallbackRegistration = null
                        
                        workoutRegistration = workoutsCollection().document(activeSession.workoutId)
                            .addSnapshotListener { workoutDoc, workoutError ->
                                if (workoutError != null) {
                                    Log.e("FirebaseRepo", "Workout listen error", workoutError)
                                    return@addSnapshotListener
                                }
                                
                                if (workoutDoc != null && workoutDoc.exists()) {
                                    try {
                                        val workout = workoutDoc.toObject(Workout::class.java)
                                        // Use manual mapping if fields are missing or if toObject failed partially
                                        val finalWorkout = if (workout != null && workout.loggedExercises.isNotEmpty()) {
                                            workout
                                        } else {
                                            Log.d("FirebaseRepo", "toObject yielded empty exercises, using manual mapping")
                                            val id = workoutDoc.getString("id") ?: workoutDoc.id
                                            val date = workoutDoc.getLong("date") ?: 0L
                                            val templateId = workoutDoc.getString("templateId")
                                            val templateName = workoutDoc.getString("templateName")
                                            val status = workoutDoc.getString("status") ?: "in_progress"
                                            val durationMinutes = workoutDoc.getLong("durationMinutes")?.toInt() ?: 0
                                            val totalVolume = workoutDoc.getDouble("totalVolume") ?: 0.0
                                            
                                            val loggedRaw = workoutDoc.get("loggedExercises") as? List<*> ?: emptyList<Any>()
                                            val loggedExercises = loggedRaw.map { item ->
                                                val exMap = item as? Map<*, *>
                                                val exerciseId = exMap?.get("exerciseId") as? String ?: ""
                                                val exerciseName = exMap?.get("exerciseName") as? String ?: ""
                                                val videoUrl = exMap?.get("videoUrl") as? String
                                                
                                                val setsRaw = exMap?.get("sets") as? List<*> ?: emptyList<Any>()
                                                val sets = setsRaw.map { setItem ->
                                                    val setMap = setItem as? Map<*, *>
                                                    WorkoutSet(
                                                        setNumber = (setMap?.get("setNumber") as? Number)?.toInt() ?: 1,
                                                        weight = (setMap?.get("weight") as? Number)?.toDouble() ?: 0.0,
                                                        reps = (setMap?.get("reps") as? Number)?.toInt() ?: 0,
                                                        isWarmup = (setMap?.get("warmup") as? Boolean ?: setMap?.get("isWarmup") as? Boolean ?: false),
                                                        completedAt = (setMap?.get("completedAt") as? Number)?.toLong(),
                                                        rpe = (setMap?.get("rpe") as? Number)?.toFloat(),
                                                        targetWeight = (setMap?.get("targetWeight") as? Number)?.toDouble(),
                                                        targetReps = (setMap?.get("targetReps") as? Number)?.toInt()
                                                    )
                                                }
                                                LoggedExercise(
                                                    exerciseId = exerciseId, 
                                                    exerciseName = exerciseName, 
                                                    sets = sets, 
                                                    videoUrl = videoUrl,
                                                    muscleGroup = exMap?.get("muscleGroup") as? String ?: "GENERAL"
                                                )
                                            }
                                            Workout(id = id, date = date, templateId = templateId, templateName = templateName, status = status, durationMinutes = durationMinutes, loggedExercises = loggedExercises, totalVolume = totalVolume)
                                        }
                                        
                                        if (finalWorkout.status == "in_progress") {
                                            launch { localFallback.saveWorkout(finalWorkout) }
                                            trySend(finalWorkout)
                                        } else {
                                             trySend(null)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("FirebaseRepo", "Error parsing workout from doc", e)
                                        trySend(null)
                                    }
                                } else {
                                    trySend(null)
                                }
                            }
                    } else {
                        // Secondary Fallback: Use status-based query if no session context exists
                        fallbackRegistration = workoutsCollection()
                            .whereEqualTo("status", "in_progress")
                            .orderBy("date", Query.Direction.DESCENDING)
                            .limit(1)
                            .addSnapshotListener { snapshot, fallbackError ->
                                if (fallbackError != null) {
                                    Log.e("FirebaseRepo", "Fallback listen error", fallbackError)
                                    return@addSnapshotListener
                                }
                                
                                if (snapshot != null && !snapshot.isEmpty) {
                                    val doc = snapshot.documents.first()
                                    try {
                                        val workout = doc.toObject(Workout::class.java)
                                        val finalWorkout = if (workout != null && workout.loggedExercises.isNotEmpty()) {
                                            workout
                                        } else {
                                            val id = doc.getString("id") ?: doc.id
                                            val date = doc.getLong("date") ?: 0L
                                            val templateId = doc.getString("templateId")
                                            val templateName = doc.getString("templateName")
                                            val status = doc.getString("status") ?: "in_progress"
                                            val durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 0
                                            val totalVolume = doc.getDouble("totalVolume") ?: 0.0
                                            
                                            val loggedRaw = doc.get("loggedExercises") as? List<*> ?: emptyList<Any>()
                                            val loggedExercises = loggedRaw.map { item ->
                                                val exMap = item as? Map<*, *>
                                                val exerciseId = exMap?.get("exerciseId") as? String ?: ""
                                                val exerciseName = exMap?.get("exerciseName") as? String ?: ""
                                                val videoUrl = exMap?.get("videoUrl") as? String
                                                
                                                val setsRaw = exMap?.get("sets") as? List<*> ?: emptyList<Any>()
                                                val sets = setsRaw.map { setItem ->
                                                    val setMap = setItem as? Map<*, *>
                                                    WorkoutSet(
                                                        setNumber = (setMap?.get("setNumber") as? Number)?.toInt() ?: 1,
                                                        weight = (setMap?.get("weight") as? Number)?.toDouble() ?: 0.0,
                                                        reps = (setMap?.get("reps") as? Number)?.toInt() ?: 0,
                                                        isWarmup = (setMap?.get("warmup") as? Boolean ?: setMap?.get("isWarmup") as? Boolean ?: false),
                                                        completedAt = (setMap?.get("completedAt") as? Number)?.toLong(),
                                                        rpe = (setMap?.get("rpe") as? Number)?.toFloat(),
                                                        targetWeight = (setMap?.get("targetWeight") as? Number)?.toDouble(),
                                                        targetReps = (setMap?.get("targetReps") as? Number)?.toInt()
                                                    )
                                                }
                                                LoggedExercise(
                                                    exerciseId = exerciseId, 
                                                    exerciseName = exerciseName, 
                                                    sets = sets, 
                                                    videoUrl = videoUrl,
                                                    muscleGroup = exMap?.get("muscleGroup") as? String ?: "GENERAL"
                                                )
                                            }
                                            Workout(id = id, date = date, templateId = templateId, templateName = templateName, status = status, durationMinutes = durationMinutes, loggedExercises = loggedExercises, totalVolume = totalVolume)
                                        }
                                        launch { localFallback.saveWorkout(finalWorkout) }
                                        trySend(finalWorkout)
                                    } catch (e: Exception) {
                                        Log.e("FirebaseRepo", "Fallback mapping failed", e)
                                        trySend(null)
                                    }
                                } else {
                                    trySend(null)
                                }
                            }
                    }
                }
            }
        }
        
        auth.addAuthStateListener(authRegistration)
        awaitClose {
            auth.removeAuthStateListener(authRegistration)
            cleanupListeners()
            localJob.cancel()
        }
    }

    private fun validateWorkoutIntegrity(workout: Workout): Boolean {
        // A workout is considered "potentially corrupt" if it's in progress but has 0 exercises or 0 sets 
        // when it definitely should have some.
        if (workout.status == "in_progress" && workout.loggedExercises.isEmpty()) {
            Log.w("FirebaseRepo", "Validation: Active workout has 0 exercises. Status: Suspicious")
            return false
        }
        
        // Check if any exercise has 0 sets (which shouldn't happen in a healthy save)
        workout.loggedExercises.forEach { ex ->
            if (ex.sets.isEmpty()) {
                Log.w("FirebaseRepo", "Validation: Exercise ${ex.exerciseName} has 0 sets. Status: Suspicious")
                return false
            }
        }
        
        Log.d("FirebaseRepo", "Validation: Workout integrity check passed (${workout.loggedExercises.size} exercises)")
        return true
    }

    override suspend fun saveWorkout(workout: Workout) {
        val id = if (workout.id.isEmpty()) UUID.randomUUID().toString() else workout.id
        val workoutCopy = if (workout.date == 0L) {
            workout.copy(id = id, date = System.currentTimeMillis())
        } else {
            workout.copy(id = id)
        }
        
        // Sanity check BEFORE saving
        if (workoutCopy.status == "in_progress" && workoutCopy.loggedExercises.isEmpty()) {
             Log.e("FirebaseRepo", "CRITICAL: Attempting to save an empty active workout. This might overwrite healthy data.")
        }

        localFallback.saveWorkout(workoutCopy)
        if (auth.currentUser == null) {
            return
        }
        try {
            val col = workoutsCollection()
            col.document(id).set(workoutCopy).await()
            
            if (workoutCopy.status == "in_progress") {
                // Update sessionDoc with latest activity time to ensure listeners trigger
                sessionDoc().set(ActiveWorkoutSession(workoutId = id, timestamp = System.currentTimeMillis())).await()
            }
            
            // Post-save verification: Cross-check set counts and integrity
            val verificationDoc = col.document(id).get().await()
            if (verificationDoc.exists()) {
                val remoteExercises = verificationDoc.get("loggedExercises") as? List<*>
                if (remoteExercises?.size != workoutCopy.loggedExercises.size) {
                    Log.e("FirebaseRepo", "PERSISTENCE VALIDATION FAILED (EXERCISE COUNT): Local ${workoutCopy.loggedExercises.size} vs Remote ${remoteExercises?.size}")
                } else {
                    // Deep check first exercise set count if it exists
                    if (workoutCopy.loggedExercises.isNotEmpty()) {
                        val localFirstSetCount = workoutCopy.loggedExercises.first().sets.size
                        val remoteFirstEx = (remoteExercises?.first() as? Map<*, *>)
                        val remoteSets = remoteFirstEx?.get("sets") as? List<*>
                        if (remoteSets?.size != localFirstSetCount) {
                             Log.e("FirebaseRepo", "PERSISTENCE VALIDATION FAILED (SET COUNT): Local $localFirstSetCount vs Remote ${remoteSets?.size}")
                        } else {
                             Log.d("FirebaseRepo", "PERSISTENCE VALIDATION SUCCESS: Data verified in Firestore")
                        }
                    } else {
                        Log.d("FirebaseRepo", "PERSISTENCE VALIDATION SUCCESS: Verified (empty exercise list but intentional)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error saving workout", e)
        }
    }

    override suspend fun deleteWorkout(workoutId: String) {
        localFallback.deleteWorkout(workoutId)
        if (auth.currentUser == null) {
            return
        }
        try {
            val snapshot = sessionDoc().get().await()
            if (snapshot.exists()) {
                val session = snapshot.toObject(ActiveWorkoutSession::class.java)
                if (session?.workoutId == workoutId) {
                    sessionDoc().delete().await()
                }
            }
            workoutsCollection().document(workoutId).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error deleting workout", e)
        }
    }

    override suspend fun finishWorkout(workout: Workout) {
        var totalVolume = 0.0
        workout.loggedExercises.forEach { ex ->
            ex.sets.filter { !it.isWarmup }.forEach { set ->
                totalVolume += set.weight * set.reps
            }
        }
        val id = if (workout.id.isEmpty()) UUID.randomUUID().toString() else workout.id
        val finishedWorkout = workout.copy(id = id, status = "completed", totalVolume = totalVolume)
        
        localFallback.finishWorkout(finishedWorkout)
        if (auth.currentUser == null) {
            return
        }
        try {
            val col = workoutsCollection()
            val prsCol = prsCollection()
            
            col.document(id).set(finishedWorkout).await()
            sessionDoc().delete().await()
            
            // Update PRs & Active Program State in background safely
            for (ex in finishedWorkout.loggedExercises) {
                var exBestWeight = 0.0
                var exBestWeightReps = 0
                var exBestVolume = 0.0
                var exBestVolumeReps = 0
                var exBestE1rm = 0.0
                var exBestE1rmReps = 0
                
                ex.sets.filter { !it.isWarmup }.forEach { set ->
                    val setVolume = set.weight * set.reps
                    val e1rm = set.weight * (1.0 + set.reps / 30.0)
                    
                    if (set.weight > exBestWeight) { exBestWeight = set.weight; exBestWeightReps = set.reps }
                    if (setVolume > exBestVolume) { exBestVolume = setVolume; exBestVolumeReps = set.reps }
                    if (e1rm > exBestE1rm) { exBestE1rm = e1rm; exBestE1rmReps = set.reps }
                }
                
                if (exBestWeight > 0) {
                    val prDoc = prsCol.document(ex.exerciseId)
                    val snapshot = prDoc.get().await()
                    val currentPr = snapshot?.toObject(PersonalRecord::class.java) ?: PersonalRecord(exerciseId = ex.exerciseId)
                    var newPr = currentPr
                    if (exBestWeight > (currentPr.bestWeight?.value ?: 0.0)) {
                        newPr = newPr.copy(bestWeight = RecordDetail(exBestWeight, exBestWeightReps, finishedWorkout.date, id))
                    }
                    if (exBestVolume > (currentPr.bestVolume?.value ?: 0.0)) {
                        newPr = newPr.copy(bestVolume = RecordDetail(exBestVolume, exBestVolumeReps, finishedWorkout.date, id))
                    }
                    if (exBestE1rm > (currentPr.bestEstimated1RM?.value ?: 0.0)) {
                        newPr = newPr.copy(bestEstimated1RM = RecordDetail(exBestE1rm, exBestE1rmReps, finishedWorkout.date, id))
                    }
                    
                    if (newPr != currentPr) {
                        prDoc.set(newPr).await()
                    }
                }
            }
            
            if (finishedWorkout.templateId != null) {
                val stateSnapshot = programMetaDoc().get().await()
                if (stateSnapshot.exists()) {
                    val state = stateSnapshot.toObject(ActiveProgramState::class.java)
                    if (state != null) {
                        val newCompletedMap = state.completedWorkoutsMap.toMutableMap()
                        newCompletedMap[finishedWorkout.templateId!!] = true
                        
                        var nextDaySlot = state.currentDaySlot + 1
                        var nextWeek = state.currentWeek
                        
                        if (nextDaySlot >= 7) {
                            nextDaySlot = 0
                            nextWeek += 1
                        }

                        val newState = state.copy(
                            completedWorkoutsMap = newCompletedMap,
                            currentDaySlot = nextDaySlot,
                            currentWeek = nextWeek,
                            lastCompletedDate = finishedWorkout.date
                        )
                        saveActiveProgramState(newState)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error finishing workout", e)
        }
    }

    override fun getPersonalRecords(): Flow<List<PersonalRecord>> {
        if (auth.currentUser == null) return localFallback.getPersonalRecords()
        return callbackFlow {
            val col = prsCollection()
            val listener = col.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseRepo", "Listen error", e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val prs = snapshot.documents.mapNotNull {
                        try {
                            it.toObject(PersonalRecord::class.java)
                        } catch (ex: Exception) {
                            Log.e("FirebaseRepo", "Error parsing personal record", ex)
                            null
                        }
                    }
                    trySend(prs)
                }
            }
            awaitClose { listener.remove() }
        }
    }

    override fun getPersonalRecord(exerciseId: String): Flow<PersonalRecord?> {
        if (auth.currentUser == null) return localFallback.getPersonalRecord(exerciseId)
        return callbackFlow {
            val doc = prsCollection().document(exerciseId)
            val listener = doc.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseRepo", "Listen error", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        trySend(snapshot.toObject(PersonalRecord::class.java))
                    } catch (ex: Exception) {
                        Log.e("FirebaseRepo", "Error parsing specific personal record", ex)
                        trySend(null)
                    }
                } else {
                    trySend(null)
                }
            }
            awaitClose { listener.remove() }
        }
    }
    
    override fun getActiveProgramState(): Flow<ActiveProgramState?> {
        val local = localFallback.getActiveProgramState()
        val uid = auth.currentUser?.uid ?: return local
        
        val remote = callbackFlow {
            Log.d("FirebaseRepo", "Starting remote getActiveProgramState for $uid")
            val listener = programMetaDoc().addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseRepo", "Error fetching meta: ${e.message}", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        trySend(snapshot.toObject(ActiveProgramState::class.java))
                    } catch (ex: Exception) {
                        Log.e("FirebaseRepo", "Failed to deserialize program state", ex)
                    }
                }
            }
            awaitClose { listener.remove() }
        }
        
        return merge(local, remote).distinctUntilChanged()
    }

    override suspend fun saveActiveProgramState(state: ActiveProgramState?) {
        Log.d("FirebaseRepo", "saveActiveProgramState called with state: $state")
        // Always save locally first for immediate responsiveness and offline support
        localFallback.saveActiveProgramState(state)
        Log.d("FirebaseRepo", "Local state saved")
        
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d("FirebaseRepo", "No user logged in, skipping Firestore save")
            return
        }
        
        try {
            Log.d("FirebaseRepo", "Saving to Firestore for user: $uid")
            if (state == null) {
                programMetaDoc().delete().await()
                Log.d("FirebaseRepo", "Firestore document deleted")
            } else {
                programMetaDoc().set(state, SetOptions.merge()).await()
                Log.d("FirebaseRepo", "Firestore document set/merged")
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error saving program state to Firestore", e)
        }
    }

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private var cachedProgram: Program? = null

    private fun loadProgramFromAssets(): Program? {
        if (cachedProgram != null) return cachedProgram
        return try {
            val json = context.assets.open("jeff_nippard.json").bufferedReader().use { it.readText() }
            Log.d("DiagnosticLog", "[JSON INIT] Raw String Length: ${json.length} characters")
            
            val adapter = moshi.adapter(Program::class.java)
            val parsedResult = adapter.fromJson(json)
            
            if (parsedResult != null) {
                logProgramDiagnostic(parsedResult)
            } else {
                Log.e("DiagnosticLog", "[FATAL] Moshi returned NULL during parsing of program_data_v4.json")
            }
            
            cachedProgram = parsedResult
            cachedProgram
        } catch (e: Exception) {
            Log.e("DiagnosticLog", "[JSON INIT ERROR] Failed to load or parse JSON", e)
            Log.e("FirebaseRepo", "Failed to load local program data: ${e.message}", e)
            null
        }
    }

    private fun logProgramDiagnostic(p: Program) {
        val schema = p._meta?.schema
        Log.i("DiagnosticLog", "====================================================")
        Log.i("DiagnosticLog", "IRONLOG DIAGNOSTIC: PROGRAM INITIALIZATION")
        Log.i("DiagnosticLog", "----------------------------------------------------")
        Log.i("DiagnosticLog", "Name: ${p.programName}")
        Log.i("DiagnosticLog", "Author: ${p.program?.author}")
        Log.i("DiagnosticLog", "Weeks Count: ${p.weeks.size}")
        Log.i("DiagnosticLog", "Schema Detected: ${schema != null}")
        
        schema?.let {
            Log.i("DiagnosticLog", "Schema - Total Weeks: ${it.totalWeeks}")
            Log.i("DiagnosticLog", "Schema - Training Days: ${it.trainingDaysPerWeek}")
            Log.i("DiagnosticLog", "Schema - Techniques: ${it.techniques.joinToString()}")
            Log.i("DiagnosticLog", "Schema - Experience: ${it.experienceLevel}")
        }
        
        val samples = p.weeks.keys.take(2)
        Log.i("DiagnosticLog", "Week Keys Sample: $samples")
        
        val firstWeek = p.weeks["week1"]
        Log.i("DiagnosticLog", "Week 1 - Day Count: ${firstWeek?.days?.size ?: 0}")
        
        Log.i("DiagnosticLog", "====================================================")
    }

    override fun getActiveProgram(): Flow<Program?> = flow {
        emit(loadProgramFromAssets())
    }

    override fun getProgramWeek(weekNumber: Int): Flow<ProgramWeek?> = flow {
        val program = loadProgramFromAssets()
        val weekKey = "week$weekNumber"
        emit(program?.weeks?.get(weekKey))
    }

    override fun getUserProfile(): Flow<com.example.model.UserProfile?> = callbackFlow {
        val localJob = launch {
            localFallback.getUserProfile().collect {
                trySend(it)
            }
        }
        var registration: ListenerRegistration? = null
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            registration?.remove()
            val user = firebaseAuth.currentUser
            if (user != null) {
                val docRef = firestore.collection("users").document(user.uid)
                registration = docRef.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("FirebaseRepo", "Listen user profile error", e)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        try {
                            val profile = snapshot.toObject(com.example.model.UserProfile::class.java)
                            if (profile != null) {
                                launch {
                                    localFallback.saveUserProfile(profile)
                                }
                                trySend(profile)
                            }
                        } catch (ex: Exception) {
                            Log.e("FirebaseRepo", "Deserializing user profile failed", ex)
                        }
                    } else {
                        // Fallback attempt to subcollection userProfile path
                        val fallbackDocRef = firestore.collection("users").document(user.uid).collection("settings").document("userProfile")
                        fallbackDocRef.get().addOnSuccessListener { fbSnap ->
                            if (fbSnap != null && fbSnap.exists()) {
                                try {
                                    val profile = fbSnap.toObject(com.example.model.UserProfile::class.java)
                                    if (profile != null) {
                                        launch {
                                            localFallback.saveUserProfile(profile)
                                        }
                                        trySend(profile)
                                    }
                                } catch (ex: Exception) {
                                    Log.e("FirebaseRepo", "Deserializing fallback profile failed", ex)
                                }
                            }
                        }.addOnFailureListener {
                            Log.e("FirebaseRepo", "Fallback profile get failed", it)
                        }
                    }
                }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
            registration?.remove()
            localJob.cancel()
        }
    }

    override suspend fun saveUserProfile(profile: com.example.model.UserProfile) {
        localFallback.saveUserProfile(profile)
        if (auth.currentUser == null) {
            return
        }
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(uid).set(profile).await()
            firestore.collection("users").document(uid).collection("settings").document("userProfile")
                .set(profile).await()
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error saving user profile", e)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        localFallback.signOut()
    }
}
