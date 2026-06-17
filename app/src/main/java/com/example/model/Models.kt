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
    val videoUrl: String? = null,
    val sets: List<@JvmSuppressWildcards WorkoutSet> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkoutSet(
    val setNumber: Int = 1,
    val weight: Double = 0.0,
    val reps: Int = 0,
    val isWarmup: Boolean = false,
    val completedAt: Long? = null,
    val rpe: Float? = null
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
    val programKey: String = "", // e.g. the json filename or programName
    val currentWeekIndex: Int = 0,
    val workoutsCompletedThisWeek: Int = 0,
    val totalWorkoutsThisWeek: Int = 0,
    val isWeekCompletedMessageShown: Boolean = false
)
