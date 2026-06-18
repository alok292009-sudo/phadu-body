package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class SharedPrefsIronLogRepository(context: Context) : IronLogRepository {
    private val prefs = context.getSharedPreferences("ironlog_db", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val templatesState = MutableStateFlow<List<Template>>(emptyList())
    private val workoutsState = MutableStateFlow<List<Workout>>(emptyList())
    private val exercisesState = MutableStateFlow<List<Exercise>>(emptyList())
    private val prsState = MutableStateFlow<List<PersonalRecord>>(emptyList())
    private val activeProgramStateFlow = MutableStateFlow<ActiveProgramState?>(null)

    init {
        loadData()
    }

    private fun loadData() {
        val tJson = prefs.getString("templates", "[]")
        val wJson = prefs.getString("workouts", "[]")
        val eJson = prefs.getString("exercises", "[]")
        val pJson = prefs.getString("prs", "[]")
        val apJson = prefs.getString("active_program", null)
        val upJson = prefs.getString("user_profile", null)

        templatesState.value = try {
            moshi.adapter<List<Template>>(Types.newParameterizedType(List::class.java, Template::class.java)).fromJson(tJson ?: "[]") ?: emptyList()
        } catch (e: Exception) {
            Log.e("SharedPrefsRepo", "Error deserializing templates", e)
            emptyList()
        }

        workoutsState.value = try {
            moshi.adapter<List<Workout>>(Types.newParameterizedType(List::class.java, Workout::class.java)).fromJson(wJson ?: "[]") ?: emptyList()
        } catch (e: Exception) {
            Log.e("SharedPrefsRepo", "Error deserializing workouts", e)
            emptyList()
        }

        exercisesState.value = try {
            moshi.adapter<List<Exercise>>(Types.newParameterizedType(List::class.java, Exercise::class.java)).fromJson(eJson ?: "[]") ?: emptyList()
        } catch (e: Exception) {
            Log.e("SharedPrefsRepo", "Error deserializing exercises", e)
            emptyList()
        }

        prsState.value = try {
            moshi.adapter<List<PersonalRecord>>(Types.newParameterizedType(List::class.java, PersonalRecord::class.java)).fromJson(pJson ?: "[]") ?: emptyList()
        } catch (e: Exception) {
            Log.e("SharedPrefsRepo", "Error deserializing prs", e)
            emptyList()
        }

        if (apJson != null) {
            try {
                activeProgramStateFlow.value = moshi.adapter(ActiveProgramState::class.java).fromJson(apJson)
            } catch (e: Exception) {
                Log.e("SharedPrefsRepo", "Error deserializing active_program", e)
            }
        }
        if (upJson != null) {
            try {
                userProfileState.value = moshi.adapter(UserProfile::class.java).fromJson(upJson)
            } catch (e: Exception) {
                Log.e("SharedPrefsRepo", "Error deserializing user_profile", e)
            }
        }
    }

    private fun saveTemplates() = prefs.edit().putString("templates", moshi.adapter<List<Template>>(Types.newParameterizedType(List::class.java, Template::class.java)).toJson(templatesState.value)).apply()
    private fun saveWorkouts() = prefs.edit().putString("workouts", moshi.adapter<List<Workout>>(Types.newParameterizedType(List::class.java, Workout::class.java)).toJson(workoutsState.value)).apply()
    private fun saveExercises() = prefs.edit().putString("exercises", moshi.adapter<List<Exercise>>(Types.newParameterizedType(List::class.java, Exercise::class.java)).toJson(exercisesState.value)).apply()
    private fun savePrs() = prefs.edit().putString("prs", moshi.adapter<List<PersonalRecord>>(Types.newParameterizedType(List::class.java, PersonalRecord::class.java)).toJson(prsState.value)).apply()
    private fun saveActiveProgram() {
        val state = activeProgramStateFlow.value
        if (state == null) {
            prefs.edit().remove("active_program").apply()
        } else {
            prefs.edit().putString("active_program", moshi.adapter(ActiveProgramState::class.java).toJson(state)).apply()
        }
    }

    override fun getExercises(): Flow<List<Exercise>> = exercisesState
    override suspend fun addExercise(exercise: Exercise) {
        val list = exercisesState.value.toMutableList()
        val id = if (exercise.id.isEmpty()) UUID.randomUUID().toString() else exercise.id
        val newEx = exercise.copy(id = id)
        list.removeAll { it.id == id }
        list.add(newEx)
        exercisesState.value = list
        saveExercises()
    }

    override suspend fun seedInitialExercises() {
        if (exercisesState.value.isNotEmpty()) return
        val defaultExercises = listOf(
            Exercise(id = UUID.randomUUID().toString(), name = "Bench Press", muscleGroup = "Chest", isCustom = false),
            Exercise(id = UUID.randomUUID().toString(), name = "Incline Dumbbell Press", muscleGroup = "Chest", isCustom = false),
            Exercise(id = UUID.randomUUID().toString(), name = "Squat", muscleGroup = "Legs", isCustom = false),
            Exercise(id = UUID.randomUUID().toString(), name = "Leg Press", muscleGroup = "Legs", isCustom = false),
            Exercise(id = UUID.randomUUID().toString(), name = "Deadlift", muscleGroup = "Back", isCustom = false),
            Exercise(id = UUID.randomUUID().toString(), name = "Lat Pulldown", muscleGroup = "Back", isCustom = false),
            Exercise(id = UUID.randomUUID().toString(), name = "Overhead Press", muscleGroup = "Shoulders", isCustom = false),
            Exercise(id = UUID.randomUUID().toString(), name = "Lateral Raise", muscleGroup = "Shoulders", isCustom = false),
            Exercise(id = UUID.randomUUID().toString(), name = "Barbell Curl", muscleGroup = "Arms", isCustom = false),
            Exercise(id = UUID.randomUUID().toString(), name = "Triceps Extension", muscleGroup = "Arms", isCustom = false)
        )
        exercisesState.value = defaultExercises
        saveExercises()
    }

    override fun getTemplates(): Flow<List<Template>> = templatesState
    override suspend fun saveTemplate(template: Template) {
        val list = templatesState.value.toMutableList()
        val id = if (template.id.isEmpty()) UUID.randomUUID().toString() else template.id
        val newT = template.copy(id = id)
        list.removeAll { it.id == id }
        list.add(newT)
        templatesState.value = list
        saveTemplates()
    }

    override suspend fun deleteTemplate(templateId: String) {
        val list = templatesState.value.toMutableList()
        list.removeAll { it.id == templateId }
        templatesState.value = list
        saveTemplates()
    }

    override suspend fun clearAllTemplates() {
        templatesState.value = emptyList()
        saveTemplates()
    }

    override fun getWorkouts(): Flow<List<Workout>> = workoutsState
    override fun getActiveWorkout(): Flow<Workout?> = workoutsState.map { it.find { w -> w.status == "in_progress" } }

    override suspend fun saveWorkout(workout: Workout) {
        val list = workoutsState.value.toMutableList()
        val id = if (workout.id.isEmpty()) UUID.randomUUID().toString() else workout.id
        val newW = workout.copy(id = id)
        list.removeAll { it.id == id }
        list.add(newW)
        workoutsState.value = list
        saveWorkouts()
    }

    override suspend fun deleteWorkout(workoutId: String) {
        val list = workoutsState.value.toMutableList()
        list.removeAll { it.id == workoutId }
        workoutsState.value = list
        saveWorkouts()
    }

    override suspend fun finishWorkout(workout: Workout) {
        var totalVolume = 0.0
        workout.loggedExercises.forEach { ex ->
            ex.sets.filter { !it.isWarmup }.forEach { set ->
                totalVolume += set.weight * set.reps
            }
        }
        val id = if (workout.id.isEmpty()) UUID.randomUUID().toString() else workout.id
        val completedWorkout = workout.copy(id = id, status = "completed", totalVolume = totalVolume)
        saveWorkout(completedWorkout)

        // Update local PRs
        val prsList = prsState.value.toMutableList()
        for (ex in completedWorkout.loggedExercises) {
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
                val currentPr = prsList.find { it.exerciseId == ex.exerciseId } ?: PersonalRecord(exerciseId = ex.exerciseId)
                var newPr = currentPr
                if (exBestWeight > (currentPr.bestWeight?.value ?: 0.0)) {
                    newPr = newPr.copy(bestWeight = RecordDetail(exBestWeight, exBestWeightReps, completedWorkout.date, id))
                }
                if (exBestVolume > (currentPr.bestVolume?.value ?: 0.0)) {
                    newPr = newPr.copy(bestVolume = RecordDetail(exBestVolume, exBestVolumeReps, completedWorkout.date, id))
                }
                if (exBestE1rm > (currentPr.bestEstimated1RM?.value ?: 0.0)) {
                    newPr = newPr.copy(bestEstimated1RM = RecordDetail(exBestE1rm, exBestE1rmReps, completedWorkout.date, id))
                }
                if (newPr != currentPr) {
                    prsList.removeAll { it.exerciseId == ex.exerciseId }
                    prsList.add(newPr)
                }
            }
        }
        prsState.value = prsList
        savePrs()
        
        // Update active program state if this workout belongs to a template
        if (workout.templateId != null) {
            val state = activeProgramStateFlow.value
            if (state != null) {
                val newCompletedMap = state.completedWorkoutsMap.toMutableMap()
                newCompletedMap[workout.templateId] = true

                val parts = workout.templateId.split("_")
                val dayIndex = if (parts.size == 2) parts[1].toIntOrNull() else null
                
                val nextDayIndex = if (dayIndex != null) {
                    if (dayIndex == state.currentDayIndex) {
                        (dayIndex + 1).coerceAtMost(6)
                    } else {
                        state.currentDayIndex
                    }
                } else {
                    state.currentDayIndex
                }

                val newState = state.copy(
                    completedWorkoutsMap = newCompletedMap,
                    currentDayIndex = nextDayIndex,
                    workoutsCompletedThisWeek = state.workoutsCompletedThisWeek + 1
                )
                if (newState.workoutsCompletedThisWeek >= newState.totalWorkoutsThisWeek) {
                    saveActiveProgramState(newState.copy(isWeekCompletedMessageShown = false))
                } else {
                    saveActiveProgramState(newState)
                }
            }
        }
    }

    override fun getPersonalRecords(): Flow<List<PersonalRecord>> = prsState
    override fun getPersonalRecord(exerciseId: String): Flow<PersonalRecord?> = prsState.map { it.find { pr -> pr.exerciseId == exerciseId } }
    
    override fun getActiveProgramState(): Flow<ActiveProgramState?> = activeProgramStateFlow
    
    override suspend fun saveActiveProgramState(state: ActiveProgramState?) {
        activeProgramStateFlow.value = state
        saveActiveProgram()
    }
    
    private val userProfileState = MutableStateFlow<UserProfile?>(null)
    
    override fun getUserProfile(): Flow<UserProfile?> = userProfileState
    override suspend fun saveUserProfile(profile: UserProfile) {
        userProfileState.value = profile
        prefs.edit().putString("user_profile", moshi.adapter(UserProfile::class.java).toJson(profile)).apply()
    }
    
    override suspend fun signOut() {
        try {
            prefs.edit().clear().apply()
            templatesState.value = emptyList()
            workoutsState.value = emptyList()
            exercisesState.value = emptyList()
            prsState.value = emptyList()
            activeProgramStateFlow.value = null
            userProfileState.value = null
        } catch (e: Exception) {
            Log.e("SharedPrefsRepo", "Error resetting local state on signOut", e)
        }
    }
}
