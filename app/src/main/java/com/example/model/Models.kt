package com.example.model

import com.squareup.moshi.JsonClass
import kotlin.jvm.JvmSuppressWildcards

@JsonClass(generateAdapter = true)
data class Exercise(
    val id: String = "",
    val name: String = "",
    val muscleGroup: String = "",
    val unit: String = "kg",
    val isCustom: Boolean = false,
    val createdAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class Template(
    val id: String = "",
    val name: String = "",
    val exercises: List<@JvmSuppressWildcards TemplateExercise> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TemplateExercise(
    val exerciseId: String = "",
    val exerciseName: String = "",
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val order: Int = 0,
    val videoUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class Workout(
    val id: String = "",
    val date: Long = 0L,
    val templateId: String? = null,
    val templateName: String? = null,
    val status: String = "in_progress",
    val durationMinutes: Int = 0,
    val loggedExercises: List<@JvmSuppressWildcards LoggedExercise> = emptyList(),
    val totalVolume: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class LoggedExercise(
    val exerciseId: String = "",
    val exerciseName: String = "",
    val muscleGroup: String = "GENERAL",
    val videoUrl: String? = null,
    val sets: List<@JvmSuppressWildcards WorkoutSet> = emptyList(),
    val targetRestStr: String? = null,
    val techniqueRequirements: String? = null,
    val note: String? = null,
    val technique: ExerciseTechnique? = null,
    val isSubstitution: Boolean = false,
    val substitutionOpts: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkoutSet(
    val setNumber: Int = 1,
    val weight: Double = 0.0,
    val reps: Int = 0,
    val isWarmup: Boolean = false,
    val completedAt: Long? = null,
    val rpe: Float? = null,
    val targetWeight: Double? = null,
    val targetReps: Int? = null,
    val targetRpe: String? = null,
    val notes: String? = null,
    val restTimeSeconds: Int? = null,
    val percentOfWorking: Double? = null,
    val instruction: String? = null
)

@JsonClass(generateAdapter = true)
data class PersonalRecord(
    val exerciseId: String = "",
    val bestWeight: RecordDetail? = null,
    val bestVolume: RecordDetail? = null,
    val bestEstimated1RM: RecordDetail? = null
)

@JsonClass(generateAdapter = true)
data class RecordDetail(
    val value: Double = 0.0,
    val reps: Int = 0,
    val date: Long = 0L,
    val workoutId: String = ""
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    val name: String = "",
    val age: Int = 0,
    val weightKg: Double = 0.0,
    val heightCm: Double = 0.0,
    val gender: String = "Not specified",
    val progressPhotos: List<@JvmSuppressWildcards ProgressPhoto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ProgressPhoto(
    val id: String = "",
    val date: Long = 0L,
    val localUri: String = "",
    val weightKg: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class ActiveProgramState(
    val programName: String = "",
    val author: String = "",
    val startDate: Long = 0L,
    val currentWeek: Int = 1,     // 1-12
    val currentDaySlot: Int = 0,   // 0-Slot index
    val lastCompletedDate: Long = 0L,
    val totalWeeks: Int = 0,
    val warmupProtocol: WarmupProtocol? = null,
    val completedWorkoutsMap: Map<String, Boolean> = emptyMap() // "week1_0" -> true
)

@JsonClass(generateAdapter = true)
data class ActiveWorkoutSession(
    val workoutId: String = "",
    val timestamp: Long = 0L
)
