package com.example.model
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Program(
    val programName: String = "",
    val author: String = "",
    val weeks: Map<String, ProgramWeek> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class ProgramWeek(
    val block: String = "Block",
    val days: List<ProgramDay> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ProgramDay(
    val dayName: String = "",
    val isRestDay: Boolean = false,
    val exercises: List<ProgramExercise> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ProgramExercise(
    val name: String = "",
    val demoLink: String? = null,
    val link: String? = null,
    val warmupSets: String? = null,
    val workingSets: String? = null,
    val repRange: String? = null,
    val reps: String? = null,
    val earlySetRPE: String? = null,
    val lastSetRPE: Any? = null,
    val rest: String? = null,
    val lastSetTechnique: String? = "N/A",
    val muscleGroup: String? = null,
    val notes: String? = null,
    val substitution1: ProgramExercise? = null,
    val substitution2: ProgramExercise? = null
) {
    val videoUrl: String? get() = demoLink ?: link
    val lastSetRPEStr: String get() = lastSetRPE?.toString() ?: "N/A"
}

fun ProgramDay.toWorkout(weekKey: String, dayIndex: Int): Workout {
    val logged = this.exercises.map { pex ->
        val subNames = mutableListOf<String>()
        pex.substitution1?.name?.let { subNames.add(it) }
        pex.substitution2?.name?.let { subNames.add(it) }

        val totalWorking = run {
            val cleaned = pex.workingSets?.replace("~", "")?.trim() ?: "3"
            "\\d+".toRegex().find(cleaned)?.value?.toIntOrNull() ?: 3
        }

        val totalWarmups = run {
            val cleaned = pex.warmupSets?.replace("~", "")?.trim() ?: "0"
            if (cleaned.equals("n/a", ignoreCase = true) || cleaned.equals("none", ignoreCase = true)) 0
            else "\\d+".toRegex().find(cleaned)?.value?.toIntOrNull() ?: 0
        }

        val targetRepsParsed = run {
            val cleaned = (pex.reps ?: pex.repRange)?.trim() ?: "10"
            "\\d+".toRegex().findAll(cleaned).map { it.value.toIntOrNull() ?: 10 }.lastOrNull() ?: 10
        }

        val setsList = mutableListOf<WorkoutSet>()
        var setNum = 1

        for (i in 0 until totalWarmups) {
            val approxPercent = when (totalWarmups) {
                1 -> "60% of target"
                2 -> if (i == 0) "50% of target" else "75% of target"
                else -> if (i == 0) "45% of target" else if (i == 1) "65% of target" else "85% of target"
            }
            setsList.add(
                WorkoutSet(
                    setNumber = setNum++,
                    isWarmup = true,
                    targetReps = targetRepsParsed,
                    targetRpe = "Warm-up",
                    notes = "Warm-up set ($approxPercent load)",
                    restTimeSeconds = 60
                )
            )
        }

        for (i in 0 until totalWorking) {
            val setRpe = if (i == totalWorking - 1) pex.lastSetRPEStr else (pex.earlySetRPE ?: "7")
            val setNotes = if (i == totalWorking - 1 && pex.lastSetTechnique != "N/A" && pex.lastSetTechnique != null) {
                "Final set technique: ${pex.lastSetTechnique}. ${pex.notes ?: ""}"
            } else {
                pex.notes
            }
            setsList.add(
                WorkoutSet(
                    setNumber = setNum++,
                    isWarmup = false,
                    targetReps = targetRepsParsed,
                    targetRpe = setRpe,
                    notes = setNotes,
                    restTimeSeconds = run {
                        val cleaned = pex.rest?.replace("min", "")?.replace(" ", "")?.trim() ?: "2"
                        val mins = "\\d+".toRegex().find(cleaned)?.value?.toDoubleOrNull() ?: 2.0
                        (mins * 60).toInt()
                    }
                )
            )
        }

        LoggedExercise(
            exerciseId = pex.name,
            exerciseName = pex.name,
            videoUrl = pex.videoUrl,
            sets = setsList,
            targetRestStr = pex.rest,
            techniqueRequirements = pex.lastSetTechnique,
            note = pex.notes,
            substitutionOpts = subNames
        )
    }

    return Workout(
        id = java.util.UUID.randomUUID().toString(),
        date = System.currentTimeMillis(),
        templateId = "${weekKey}_${dayIndex}",
        templateName = this.dayName,
        loggedExercises = logged,
        status = "in_progress"
    )
}
