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

        templatesState.value = moshi.adapter<List<Template>>(Types.newParameterizedType(List::class.java, Template::class.java)).fromJson(tJson ?: "[]") ?: emptyList()
        workoutsState.value = moshi.adapter<List<Workout>>(Types.newParameterizedType(List::class.java, Workout::class.java)).fromJson(wJson ?: "[]") ?: emptyList()
        exercisesState.value = moshi.adapter<List<Exercise>>(Types.newParameterizedType(List::class.java, Exercise::class.java)).fromJson(eJson ?: "[]") ?: emptyList()
        prsState.value = moshi.adapter<List<PersonalRecord>>(Types.newParameterizedType(List::class.java, PersonalRecord::class.java)).fromJson(pJson ?: "[]") ?: emptyList()
        if (apJson != null) {
            activeProgramStateFlow.value = moshi.adapter(ActiveProgramState::class.java).fromJson(apJson)
        }
        if (upJson != null) {
            userProfileState.value = moshi.adapter(UserProfile::class.java).fromJson(upJson)
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
        val completedWorkout = workout.copy(status = "completed")
        saveWorkout(completedWorkout)
        
        // Update active program state if this workout belongs to a template
        if (workout.templateId != null) {
            val state = activeProgramStateFlow.value
            if (state != null) {
                val newState = state.copy(
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
    
    override suspend fun signOut() {}
}
