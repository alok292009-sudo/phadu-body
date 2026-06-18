package com.example.data

import com.google.firebase.firestore.ListenerRegistration
import com.example.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.UUID
import android.util.Log

import android.content.Context

class FirebaseIronLogRepository(private val context: Context) : IronLogRepository {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    
    private val localFallback by lazy {
        SharedPrefsIronLogRepository(context)
    }
    
    private val uid: String
        get() = auth.currentUser?.uid ?: "local_test_user"
        
    private fun exercisesCollection() = firestore.collection("users").document(uid).collection("exercises")
    private fun templatesCollection() = firestore.collection("users").document(uid).collection("templates")
    private fun workoutsCollection() = firestore.collection("users").document(uid).collection("workouts")
    private fun prsCollection() = firestore.collection("users").document(uid).collection("personalRecords")

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
                    val exercises = snapshot.documents.mapNotNull { it.toObject(Exercise::class.java) }
                    trySend(exercises.sortedBy { it.name })
                }
            }
            awaitClose { listener.remove() }
        }
    }

    override suspend fun addExercise(exercise: Exercise) {
        if (auth.currentUser == null) {
            localFallback.addExercise(exercise)
            return
        }
        try {
            val col = exercisesCollection()
            val id = if (exercise.id.isEmpty()) UUID.randomUUID().toString() else exercise.id
            col.document(id).set(exercise.copy(id = id)).await()
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
        localFallback.saveTemplate(template)
        if (auth.currentUser == null) {
            return
        }
        try {
            val col = templatesCollection()
            val id = if (template.id.isEmpty()) UUID.randomUUID().toString() else template.id
            col.document(id).set(template.copy(id = id)).await()
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
                                        doc.toObject(Workout::class.java)
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
        var registration: ListenerRegistration? = null
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            registration?.remove()
            val user = firebaseAuth.currentUser
            if (user != null) {
                val col = workoutsCollection()
                registration = col.whereEqualTo("status", "in_progress")
                    .limit(1)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.e("FirebaseRepo", "Firestore active listen error", e)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            try {
                                val workout = if (!snapshot.isEmpty) {
                                    val doc = snapshot.documents.first()
                                    try {
                                        doc.toObject(Workout::class.java)
                                    } catch (ex: Exception) {
                                        Log.e("FirebaseRepo", "toObject(Workout) failed for active, trying manual fallback", ex)
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
                                            Log.e("FirebaseRepo", "Fallback manual mapping failed for active Workout", fallbackEx)
                                            null
                                        }
                                    }
                                } else {
                                    null
                                }
                                launch {
                                    if (workout != null) {
                                        localFallback.saveWorkout(workout)
                                    } else {
                                        val active = localFallback.getActiveWorkout().firstOrNull()
                                        if (active != null) {
                                            localFallback.deleteWorkout(active.id)
                                        }
                                    }
                                }
                                trySend(workout)
                            } catch (exAll: Exception) {
                                Log.e("FirebaseRepo", "Error mapping active workout", exAll)
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

    override suspend fun saveWorkout(workout: Workout) {
        val id = if (workout.id.isEmpty()) UUID.randomUUID().toString() else workout.id
        val workoutCopy = if (workout.date == 0L) {
            workout.copy(id = id, date = System.currentTimeMillis())
        } else {
            workout.copy(id = id)
        }
        localFallback.saveWorkout(workoutCopy)
        if (auth.currentUser == null) {
            return
        }
        try {
            val col = workoutsCollection()
            col.document(id).set(workoutCopy).await()
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
            
            if (workout.templateId != null) {
                val stateDoc = firestore.collection("users").document(uid).collection("settings").document("activeProgramState")
                val stateSnapshot = stateDoc.get().await()
                if (stateSnapshot.exists()) {
                    val state = stateSnapshot.toObject(ActiveProgramState::class.java)
                    if (state != null) {
                        val newState = state.copy(
                            workoutsCompletedThisWeek = state.workoutsCompletedThisWeek + 1
                        )
                        if (newState.workoutsCompletedThisWeek >= newState.totalWorkoutsThisWeek) {
                            stateDoc.set(newState.copy(isWeekCompletedMessageShown = false)).await()
                        } else {
                            stateDoc.set(newState).await()
                        }
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
                    val prs = snapshot.documents.mapNotNull { it.toObject(PersonalRecord::class.java) }
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
                    trySend(snapshot.toObject(PersonalRecord::class.java))
                } else {
                    trySend(null)
                }
            }
            awaitClose { listener.remove() }
        }
    }
    
    override fun getActiveProgramState(): Flow<ActiveProgramState?> = callbackFlow {
        val localJob = launch {
            localFallback.getActiveProgramState().collect {
                trySend(it)
            }
        }
        var registration: ListenerRegistration? = null
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            registration?.remove()
            val user = firebaseAuth.currentUser
            if (user != null) {
                val doc = firestore.collection("users").document(uid).collection("settings").document("activeProgramState")
                registration = doc.addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("FirebaseRepo", "Listen error", e)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val state = snapshot.toObject(ActiveProgramState::class.java)
                        launch {
                            localFallback.saveActiveProgramState(state)
                        }
                        trySend(state)
                    } else {
                        launch {
                            localFallback.saveActiveProgramState(null)
                        }
                        trySend(null)
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

    override suspend fun saveActiveProgramState(state: ActiveProgramState?) {
        localFallback.saveActiveProgramState(state)
        if (auth.currentUser == null) {
            return
        }
        try {
            val doc = firestore.collection("users").document(uid).collection("settings").document("activeProgramState")
            if (state == null) {
                doc.delete().await()
            } else {
                doc.set(state).await()
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error saving active program state", e)
        }
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
                val docRef = firestore.collection("users").document(user.uid).collection("settings").document("userProfile")
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
