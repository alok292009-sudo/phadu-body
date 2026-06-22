package com.example.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json
import java.util.UUID

@JsonClass(generateAdapter = true)
data class Program(
    val _meta: ProgramMeta? = null,
    val program: ProgramInfo? = null,
    @Json(name = "programName") val directProgramName: String? = null,
    @Json(name = "author") val directAuthor: String? = null,
    @Json(name = "programNotes") val directNotes: String? = null,
    val warmupProtocol: WarmupProtocol? = null,
    val weeks: Map<String, ProgramWeek> = emptyMap()
) {
    val programName: String get() = directProgramName ?: program?.name ?: ""
    val author: String get() = directAuthor ?: program?.author ?: ""
    val durationWeeks: Int get() = _meta?.schema?.totalWeeks ?: program?.durationWeeks ?: weeks.size
}

@JsonClass(generateAdapter = true)
data class ProgramMeta(
    val version: String = "",
    val generatedAt: String = "",
    val source: String = "",
    val schema: ProgramSchema? = null
)

@JsonClass(generateAdapter = true)
data class ProgramSchema(
    val totalWeeks: Int = 0,
    val trainingDaysPerWeek: Int = 0,
    val restDaysPerWeek: Int = 0,
    val blocks: Map<String, String> = emptyMap(),
    val weekdayMap: Map<String, String> = emptyMap(),
    val techniques: List<String> = emptyList(),
    val experienceLevel: String? = "Intermediate-Advanced",
    val trainingStyle: String? = "Bodybuilding / Hypertrophy",
    val primaryFocus: String? = "Systemic Transformation"
)

@JsonClass(generateAdapter = true)
data class ProgramInfo(
    val name: String = "",
    val author: String = "",
    val durationWeeks: Int = 0,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class ProgramWeek(
    val weekNumber: Int = 0,
    val block: String = "",
    val isIntroWeek: Boolean = false,
    val days: List<ProgramDay> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ProgramDay(
    val slot: Int = 0,
    val weekday: String = "",
    @Json(name = "dayName") val directDayName: String? = null,
    val trainingDay: String = "",
    val displayName: String = "",
    val isRestDay: Boolean = false,
    val exercises: List<ProgramExercise> = emptyList(),
    val recovery: RecoveryInstructions? = null
) {
    val effectiveDisplayName: String get() = directDayName ?: displayName.takeIf { it.isNotEmpty() } ?: trainingDay
}

@JsonClass(generateAdapter = true)
data class RecoveryInstructions(
    val instructions: String? = null,
    val suggestions: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ProgramExercise(
    val name: String = "",
    val demoLink: String? = null,
    val muscleGroup: String? = null,
    @Json(name = "reps") val directReps: Any? = null,
    @Json(name = "rest") val directRest: Any? = null,
    @Json(name = "warmupSets") val directWarmupSets: Any? = null,
    @Json(name = "workingSets") val directWorkingSets: Any? = null,
    @Json(name = "earlySetRPE") val directEarlyRPE: Any? = null,
    @Json(name = "lastSetRPE") val directLastRPE: Any? = null,
    @Json(name = "substitution1") val flatSub1: ProgramExercise? = null,
    @Json(name = "substitution2") val flatSub2: ProgramExercise? = null,
    val prescription: ExercisePrescription? = null,
    val technique: ExerciseTechnique? = null,
    @Json(name = "notes") val directNotes: Any? = null,
    val alternatives: ExerciseAlternatives? = null,
    val logging: ExerciseLogging? = null,
    val progression: ExerciseProgression? = null
) {
    val effectiveNotes: ExerciseNotes? get() = when(val n = directNotes) {
        is String -> ExerciseNotes(exerciseNotes = n)
        is Map<*, *> -> ExerciseNotes(
             executionNotes = (n["executionNotes"] as? String),
             exerciseNotes = (n["exerciseNotes"] as? String) ?: (n["notes"] as? String)
        )
        else -> null
    }

    val videoUrl: String? get() = demoLink
}

@JsonClass(generateAdapter = true)
data class ExercisePrescription(
    val warmup: WarmupRampInfo? = null,
    val workingSets: Any? = null,
    val repRange: String? = null,
    val earlySetRPE: String? = null,
    val lastSetRPE: String? = null,
    val restTime: String? = null
) {
    val workingSetsInt: Int get() = when(workingSets) {
        is Number -> workingSets.toInt()
        is String -> workingSets.toIntOrNull() ?: 3
        else -> 3
    }
}

@JsonClass(generateAdapter = true)
data class WarmupRampInfo(
    val setsCount: String? = null,
    val ramp: List<WarmupRampSet> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WarmupRampSet(
    val setNumber: Int = 0,
    val percentOfWorking: Int = 0,
    val reps: String = "",
    val instruction: String = ""
)

@JsonClass(generateAdapter = true)
data class ExerciseTechnique(
    val failure: Boolean = false,
    val myoReps: Boolean = false,
    val lengthenedPartials: Boolean = false,
    val staticStretch: Boolean = false,
    val staticStretchDuration: String? = null
)

@JsonClass(generateAdapter = true)
data class ExerciseNotes(
    val exerciseNotes: String? = null,
    val executionNotes: String? = null
)

@JsonClass(generateAdapter = true)
data class ExerciseAlternatives(
    val substitution1: ProgramExercise? = null,
    val substitution2: ProgramExercise? = null
)

@JsonClass(generateAdapter = true)
data class ExerciseLogging(
    val sets: List<LoggingSet> = emptyList()
)

@JsonClass(generateAdapter = true)
data class LoggingSet(
    val setNumber: Int = 0,
    val isWarmup: Boolean = false,
    val targetWeight: Double? = null,
    val weightUsed: Double? = null,
    val targetReps: String? = null,
    val repsAchieved: Int? = null,
    val targetRPE: String? = null,
    val actualRPE: Float? = null,
    val isCompleted: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ExerciseProgression(
    val weekToWeekChanges: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class WarmupProtocol(
    val general: GeneralWarmup? = null
)

@JsonClass(generateAdapter = true)
data class GeneralWarmup(
    val exercises: List<GeneralWarmupExercise> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GeneralWarmupExercise(
    val name: String = "",
    val amount: String = ""
)

fun ProgramDay.toWorkout(weekKey: String, dayIndex: Int): Workout {
    val logged = this.exercises.map { pex ->
        val subNames = mutableListOf<String>()
        // Use alternatives or direct flat substitutions
        pex.alternatives?.substitution1?.name?.let { subNames.add(it) }
        pex.alternatives?.substitution2?.name?.let { subNames.add(it) }
        pex.flatSub1?.name?.let { if (!subNames.contains(it)) subNames.add(it) }
        pex.flatSub2?.name?.let { if (!subNames.contains(it)) subNames.add(it) }

        val setsList = mutableListOf<WorkoutSet>()
        
        // Add Warmups if they exist in prescription or direct fields
        val warmupSetsStr = (pex.prescription?.warmup?.setsCount ?: pex.directWarmupSets ?: "0").toString()
        val warmupCount = warmupSetsStr.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        
        if (pex.prescription?.warmup?.ramp?.isNotEmpty() == true) {
            pex.prescription.warmup.ramp.forEach { rampSet ->
                val targetRepsVal = rampSet.reps.takeWhile { it.isDigit() || it == ' ' }.trim().toIntOrNull() ?: 10
                setsList.add(WorkoutSet(
                    setNumber = rampSet.setNumber,
                    isWarmup = true,
                    targetReps = targetRepsVal,
                    reps = targetRepsVal,
                    targetRpe = "Warmup",
                    percentOfWorking = rampSet.percentOfWorking?.toDouble(),
                    instruction = rampSet.instruction
                ))
            }
        } else if (warmupCount > 0) {
            for (i in 1..warmupCount) {
                setsList.add(WorkoutSet(
                    setNumber = i,
                    isWarmup = true,
                    targetRpe = "Warmup",
                    targetReps = 10,
                    reps = 10
                ))
            }
        }
        
        // Add Working Sets
        val workingSetsCount = pex.prescription?.workingSetsInt 
            ?: when(val ws = pex.directWorkingSets) {
                is Number -> ws.toInt()
                is String -> ws.toIntOrNull() ?: 2
                else -> 2
            }
            
        val startSetNum = setsList.size + 1
        val repRangeStr = (pex.prescription?.repRange ?: pex.directReps ?: "8-10").toString()
        val targetRepsVal = repRangeStr.takeWhile { it.isDigit() || it == ' ' }.trim().toIntOrNull() ?: 10
        
        for (i in 0 until workingSetsCount) {
            val isLast = i == workingSetsCount - 1
            val targetRpe = if (isLast) {
                (pex.prescription?.lastSetRPE ?: pex.directLastRPE ?: "10").toString()
            } else {
                (pex.prescription?.earlySetRPE ?: pex.directEarlyRPE ?: "8").toString()
            }
            
            setsList.add(WorkoutSet(
                setNumber = startSetNum + i,
                isWarmup = false,
                targetRpe = targetRpe,
                targetReps = targetRepsVal,
                reps = targetRepsVal,
                notes = pex.effectiveNotes?.exerciseNotes
            ))
        }

        LoggedExercise(
            exerciseId = pex.name,
            exerciseName = pex.name,
            muscleGroup = pex.muscleGroup ?: "General",
            videoUrl = pex.videoUrl,
            sets = setsList,
            targetRestStr = (pex.prescription?.restTime ?: pex.directRest)?.toString(),
            techniqueRequirements = if (pex.technique?.failure == true) "Failure" else null,
            note = pex.effectiveNotes?.executionNotes,
            technique = pex.technique,
            substitutionOpts = subNames
        )
    }

    return Workout(
        id = UUID.randomUUID().toString(),
        date = System.currentTimeMillis(),
        templateId = "${weekKey}_${dayIndex}",
        templateName = this.effectiveDisplayName,
        loggedExercises = logged,
        status = "in_progress"
    )
}
