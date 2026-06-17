package com.example.data

import com.example.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import android.util.Log

class FirebaseIronLogRepository : IronLogRepository {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    
    private val uid: String
        get() = auth.currentUser?.uid ?: "local_test_user"
        
    private fun exercisesCollection() = firestore.collection("users").document(uid).collection("exercises")
    private fun templatesCollection() = firestore.collection("users").document(uid).collection("templates")
    private fun workoutsCollection() = firestore.collection("users").document(uid).collection("workouts")
    private fun prsCollection() = firestore.collection("users").document(uid).collection("personalRecords")

    override fun getExercises(): Flow<List<Exercise>> = callbackFlow {
        val col = exercisesCollection()
        val listener = col.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("FirebaseRepo", "Error", e)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val exercises = snapshot.documents.mapNotNull { it.toObject(Exercise::class.java) }
                trySend(exercises.sortedBy { it.name })
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun addExercise(exercise: Exercise) {
        try {
            val col = exercisesCollection()
            val id = if (exercise.id.isEmpty()) UUID.randomUUID().toString() else exercise.id
            col.document(id).set(exercise.copy(id = id)).await()
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error adding exercise", e)
        }
    }

    override suspend fun seedInitialExercises() {
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
        val col = templatesCollection()
        val listener = col.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("FirebaseRepo", "Listen error", e)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val templates = snapshot.documents.mapNotNull { it.toObject(Template::class.java) }
                trySend(templates.sortedBy { it.name })
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun saveTemplate(template: Template) {
        try {
            val col = templatesCollection()
            val id = if (template.id.isEmpty()) UUID.randomUUID().toString() else template.id
            col.document(id).set(template.copy(id = id)).await()
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error saving template", e)
        }
    }

    override suspend fun deleteTemplate(templateId: String) {
        try {
            templatesCollection().document(templateId).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error deleting template", e)
        }
    }

    override fun getWorkouts(): Flow<List<Workout>> = callbackFlow {
        val col = workoutsCollection()
        val listener = col.orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("FirebaseRepo", "Listen error", e)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val workouts = snapshot.documents.mapNotNull { it.toObject(Workout::class.java) }
                trySend(workouts)
            }
        }
        awaitClose { listener.remove() }
    }

    override fun getActiveWorkout(): Flow<Workout?> = callbackFlow {
        val col = workoutsCollection()
        val listener = col.whereEqualTo("status", "in_progress")
            .limit(1)
            .addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("FirebaseRepo", "Listen error", e)
                return@addSnapshotListener
            }
            if (snapshot != null && !snapshot.isEmpty) {
                trySend(snapshot.documents.first().toObject(Workout::class.java))
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun saveWorkout(workout: Workout) {
        try {
            val col = workoutsCollection()
            val id = if (workout.id.isEmpty()) UUID.randomUUID().toString() else workout.id
            col.document(id).set(workout.copy(id = id)).await()
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error saving workout", e)
        }
    }

    override suspend fun deleteWorkout(workoutId: String) {
        try {
            workoutsCollection().document(workoutId).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error deleting workout", e)
        }
    }

    override suspend fun finishWorkout(workout: Workout) {
        try {
            val col = workoutsCollection()
            val prsCol = prsCollection()
            val id = if (workout.id.isEmpty()) UUID.randomUUID().toString() else workout.id
            
            // Compute volume
            var totalVolume = 0.0
            workout.loggedExercises.forEach { ex ->
                ex.sets.filter { !it.isWarmup }.forEach { set ->
                    totalVolume += set.weight * set.reps
                }
            }
            val finishedWorkout = workout.copy(id = id, status = "completed", totalVolume = totalVolume)
            
            // We will update workout and PRs together. For simplicity, just consecutive awaits
            col.document(id).set(finishedWorkout).await()
            
            // Update PRs
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
                    val snapshot = try { prDoc.get().await() } catch(e:Exception) { null }
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
            
            // Update active program state
            if (workout.templateId != null) {
                val stateDoc = firestore.collection("users").document(uid).collection("settings").document("activeProgramState")
                val stateSnapshot = try { stateDoc.get().await() } catch(e:Exception) { null }
                val state = stateSnapshot?.toObject(ActiveProgramState::class.java)
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
            
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error finishing workout", e)
        }
    }

    override fun getPersonalRecords(): Flow<List<PersonalRecord>> = callbackFlow {
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

    override fun getPersonalRecord(exerciseId: String): Flow<PersonalRecord?> = callbackFlow {
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
    
    override fun getActiveProgramState(): Flow<ActiveProgramState?> = callbackFlow {
        val doc = firestore.collection("users").document(uid).collection("settings").document("activeProgramState")
        val listener = doc.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("FirebaseRepo", "Listen error", e)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                trySend(snapshot.toObject(ActiveProgramState::class.java))
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun saveActiveProgramState(state: ActiveProgramState?) {
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

    override suspend fun signOut() {
        auth.signOut()
    }
}
