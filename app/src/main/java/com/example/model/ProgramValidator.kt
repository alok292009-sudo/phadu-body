package com.example.model

import android.util.Log

object ProgramValidator {

    /**
     * Infer the muscle group for an exercise based on its name keywords.
     */
    fun inferMuscleGroup(name: String): String {
        val n = name.lowercase().trim()
        return when {
            n.contains("bench") || n.contains("press") && (n.contains("incline") || n.contains("decline") || n.contains("chest") || n.contains("barbell") || n.contains("dumbbell") || n.contains("pec") || n.contains("fly") || n.contains("crossover")) || n.contains("pushup") || n.contains("dips") -> "Chest"
            n.contains("squat") || n.contains("leg") || n.contains("ham") || n.contains("quad") || n.contains("calf") || n.contains("calves") || n.contains("thigh") || n.contains("lunge") || n.contains("nordic") || n.contains("hack") || n.contains("extension") && n.contains("leg") || n.contains("rdl") || n.contains("romanian") -> "Legs"
            n.contains("pull") || n.contains("row") || n.contains("lat") || n.contains("chin") || n.contains("deadlift") || n.contains("shrug") || n.contains("rear delt") -> "Back"
            n.contains("shoulder") || n.contains("overhead") || n.contains("military") || n.contains("lateral") || n.contains("raise") || n.contains("delt") || n.contains("arnold") -> "Shoulders"
            n.contains("curl") || n.contains("bicep") || n.contains("tricep") || n.contains("skull") || n.contains("extension") && (n.contains("triceps") || n.contains("overhead")) || n.contains("pushdown") -> "Arms"
            n.contains("crunch") || n.contains("situp") || n.contains("plank") || n.contains("abs") || n.contains("core") || n.contains("hanging") -> "Core"
            n.contains("cardio") || n.contains("treadmill") || n.contains("run") || n.contains("bike") || n.contains("walk") -> "Cardio"
            else -> "General"
        }
    }

    /**
     * Returns a pair of sensible fallback exercise names for substitutions based on the primary name and muscle group.
     */
    fun getDefaultSubstitutions(name: String, muscleGroup: String): Pair<String, String> {
        val n = name.lowercase()
        return when (muscleGroup) {
            "Chest" -> {
                if (n.contains("incline")) {
                    Pair("45° Incline DB Press", "45° Incline Machine Press")
                } else if (n.contains("fly") || n.contains("crossover")) {
                    Pair("Pec Deck Fly", "Low-to-High Cable Fly")
                } else {
                    Pair("Dumbbell Flat Bench Press", "Machine Chest Press")
                }
            }
            "Legs" -> {
                if (n.contains("squat")) {
                    Pair("Leg Press", "Goblet Squat")
                } else if (n.contains("curl")) {
                    Pair("Seated Leg Curl", "Lying Leg Curl")
                } else if (n.contains("rdl") || n.contains("deadlift")) {
                    Pair("Dumbbell Romanian Deadlift", "Snatch-Grip RDL")
                } else {
                    Pair("Bulgarian Split Squat", "Leg Press")
                }
            }
            "Back" -> {
                if (n.contains("pull") || n.contains("chin")) {
                    Pair("Neutral-Grip Pull-Up", "Wide-Grip Lat Pulldown")
                } else if (n.contains("row")) {
                    Pair("Chest-Supported Dumbbell Row", "Single-Arm Dumbbell Row")
                } else {
                    Pair("Seated Cable Row", "Barbell Row")
                }
            }
            "Shoulders" -> {
                if (n.contains("lateral") || n.contains("raise")) {
                    Pair("Cable Lateral Raise", "Chest-Supported Dumbbell Lateral Raise")
                } else if (n.contains("rear")) {
                    Pair("Reverse Pec Deck", "Face Pull")
                } else {
                    Pair("Seated Dumbbell Shoulder Press", "Smith Machine Shoulder Press")
                }
            }
            "Arms" -> {
                if (n.contains("curl") || n.contains("bicep")) {
                    Pair("Incline Dumbbell Curl", "EZ-Bar Preacher Curl")
                } else {
                    Pair("Overhead Cable Triceps Extension", "Dumbbell Triceps Kickback")
                }
            }
            "Core" -> {
                Pair("Hanging Leg Raise", "Decline Weighted Crunch")
            }
            else -> {
                Pair("Bodyweight Push-Up", "Bodyweight Squat")
            }
        }
    }

    /**
     * Helper to map Foundation Block exercises to Ramping Block exercises.
     */
    fun getRampingExercise(name: String): String {
        return when {
            name.contains("Incline Barbell Press", true) -> "45° Incline DB Press"
            name.contains("Cable Crossover Ladder", true) -> "Pec Deck"
            name.contains("Wide-Grip Lat Pulldown", true) || name.contains("Wide-Grip Pull-Up", true) -> "Dual-Handle Lat Pulldown"
            name.contains("Pendlay Deficit Row", true) || name.contains("Smith Machine Row", true) -> "Smith Machine Row"
            name.contains("Seated Leg Curl", true) -> "Lying Leg Curl"
            name.contains("Bulgarian Split Squat", true) -> "Smith Machine Static Lunge"
            name.contains("Barbell RDL", true) -> "45° Hyperextension"
            name.contains("Leg Press", true) -> "Hack Squat"
            name.contains("Neutral-Grip Lat Pulldown", true) -> "Lean-Back Lat Pulldown"
            name.contains("Chest-Supported Machine Row", true) -> "Chest-Supported T-Bar Row"
            name.contains("Neutral-Grip Seated Cable Row", true) -> "Dual-Handle Elbows-Out Cable Row"
            name.contains("Barbell Bench Press", true) -> "Machine Chest Press"
            name.contains("Machine Shoulder Press", true) -> "Seated DB Shoulder Press"
            name.contains("Bottom-Half DB Flye", true) -> "Bottom-Half Seated Cable Flye"
            name.contains("EZ-Bar Cable Curl", true) -> "Cable Rope Hammer Curl"
            name.contains("Machine Preacher Curl", true) -> "DB Concentration Curl"
            name.contains("Cable Triceps Kickback", true) -> "Triceps Pressdown (Bar)"
            name.contains("Roman Chair Leg Raise", true) -> "Ab Wheel Rollout"
            else -> name
        }
    }

    /**
     * Fills any missing weeks in the 12-week layout programmatically, using logical rules based on week1 and week2.
     */
    fun fillMissingWeeks(program: Program): Program {
        val existingWeeks = program.weeks.toMutableMap()
        val w1 = existingWeeks["week1"] ?: return program
        val w2 = existingWeeks["week2"] ?: w1

        // Propagate Weeks 3-5 (Foundation Block based on Week 2)
        for (i in 3..5) {
            val key = "week$i"
            if (!existingWeeks.containsKey(key)) {
                existingWeeks[key] = w2.copy(
                    block = "Foundation Block"
                )
            }
        }

        // Propagate Weeks 6-12 (Ramping / peaking blocks)
        for (i in 6..12) {
            val key = "week$i"
            if (!existingWeeks.containsKey(key)) {
                val rampingDays = w2.days.map { dayObj ->
                    val modifiedExercises = dayObj.exercises.map { ex ->
                        val rampingName = getRampingExercise(ex.name)
                        val rampingSub1 = ex.substitution1?.let { s -> s.copy(name = getRampingExercise(s.name)) }
                        val rampingSub2 = ex.substitution2?.let { s -> s.copy(name = getRampingExercise(s.name)) }

                        val (sets, reps) = when (i) {
                            11 -> Pair("3", "4-6")
                            12 -> Pair("2", "10-12")
                            else -> Pair(ex.workingSets ?: "3", ex.reps ?: "8-10")
                        }

                        ex.copy(
                            name = rampingName,
                            workingSets = sets,
                            reps = reps,
                            lastSetTechnique = if (i == 12) "N/A" else "Myo-reps",
                            substitution1 = rampingSub1,
                            substitution2 = rampingSub2
                        )
                    }
                    dayObj.copy(exercises = modifiedExercises)
                }
                existingWeeks[key] = ProgramWeek(
                    block = when (i) {
                        in 6..10 -> "Ramping Block"
                        11 -> "Max Strength/Peaking"
                        else -> "Active Recovery/Deload"
                    },
                    days = rampingDays
                )
            }
        }
        return program.copy(weeks = existingWeeks)
    }

    /**
     * Validates and sanitizes a complete Program structure, correcting missing or malformed fields recursively.
     */
    fun validateAndSanitize(program: Program?): Program {
        val baseProgram = program ?: Program(programName = "The Bodybuilding Transformation System - Intermediate-Advanced", author = "Jeff Nippard")
        val filledProgram = fillMissingWeeks(baseProgram)

        val sanitizedWeeks = filledProgram.weeks.mapValues { (weekKey, weekObj) ->
            val sanitizedDays = weekObj.days.map { dayObj ->
                val sanitizedExercises = dayObj.exercises.mapIndexed { index, exObj ->
                    val resolvedName = exObj.name.takeIf { it.isNotBlank() } ?: "Exercise ${index + 1}"
                    val resolvedGroup = exObj.muscleGroup?.takeIf { it.isNotBlank() } ?: inferMuscleGroup(resolvedName)

                    val s1 = run {
                        val existing = exObj.substitution1
                        if (existing == null || existing.name.isBlank()) {
                            val subName = getDefaultSubstitutions(resolvedName, resolvedGroup).first
                            ProgramExercise(
                                name = subName,
                                muscleGroup = resolvedGroup,
                                warmupSets = exObj.warmupSets ?: "1",
                                workingSets = exObj.workingSets ?: "3",
                                repRange = exObj.repRange ?: "8-10",
                                reps = exObj.reps ?: "10",
                                earlySetRPE = exObj.earlySetRPE ?: "~7",
                                lastSetRPE = exObj.lastSetRPE ?: "~8-9",
                                rest = exObj.rest ?: "2 min",
                                lastSetTechnique = exObj.lastSetTechnique ?: "N/A",
                                notes = "Alternative substitution option for $resolvedName"
                            )
                        } else {
                            // Sanitize existing substitution options
                            existing.copy(
                                name = existing.name.takeIf { it.isNotBlank() } ?: "Substitution Option 1",
                                muscleGroup = existing.muscleGroup?.takeIf { it.isNotBlank() } ?: resolvedGroup,
                                warmupSets = existing.warmupSets?.takeIf { it.isNotBlank() } ?: exObj.warmupSets ?: "1",
                                workingSets = existing.workingSets?.takeIf { it.isNotBlank() } ?: exObj.workingSets ?: "3",
                                repRange = existing.repRange?.takeIf { it.isNotBlank() } ?: exObj.repRange ?: "8-10",
                                reps = existing.reps?.takeIf { it.isNotBlank() } ?: exObj.reps ?: "10",
                                earlySetRPE = existing.earlySetRPE?.takeIf { it.isNotBlank() } ?: exObj.earlySetRPE ?: "~7",
                                lastSetRPE = existing.lastSetRPE ?: exObj.lastSetRPE ?: "~8-9",
                                rest = existing.rest?.takeIf { it.isNotBlank() } ?: exObj.rest ?: "2 min",
                                lastSetTechnique = existing.lastSetTechnique?.takeIf { it.isNotBlank() } ?: "N/A"
                            )
                        }
                    }

                    val s2 = run {
                        val existing = exObj.substitution2
                        if (existing == null || existing.name.isBlank()) {
                            val subName = getDefaultSubstitutions(resolvedName, resolvedGroup).second
                            ProgramExercise(
                                name = subName,
                                muscleGroup = resolvedGroup,
                                warmupSets = exObj.warmupSets ?: "1",
                                workingSets = exObj.workingSets ?: "3",
                                repRange = exObj.repRange ?: "8-10",
                                reps = exObj.reps ?: "10",
                                earlySetRPE = exObj.earlySetRPE ?: "~7",
                                lastSetRPE = exObj.lastSetRPE ?: "~8-9",
                                rest = exObj.rest ?: "2 min",
                                lastSetTechnique = exObj.lastSetTechnique ?: "N/A",
                                notes = "Alternative substitution option for $resolvedName"
                            )
                        } else {
                            // Sanitize existing substitution options
                            existing.copy(
                                name = existing.name.takeIf { it.isNotBlank() } ?: "Substitution Option 2",
                                muscleGroup = existing.muscleGroup?.takeIf { it.isNotBlank() } ?: resolvedGroup,
                                warmupSets = existing.warmupSets?.takeIf { it.isNotBlank() } ?: exObj.warmupSets ?: "1",
                                workingSets = existing.workingSets?.takeIf { it.isNotBlank() } ?: exObj.workingSets ?: "3",
                                repRange = existing.repRange?.takeIf { it.isNotBlank() } ?: exObj.repRange ?: "8-10",
                                reps = existing.reps?.takeIf { it.isNotBlank() } ?: exObj.reps ?: "10",
                                earlySetRPE = existing.earlySetRPE?.takeIf { it.isNotBlank() } ?: exObj.earlySetRPE ?: "~7",
                                lastSetRPE = existing.lastSetRPE ?: exObj.lastSetRPE ?: "~8-9",
                                rest = existing.rest?.takeIf { it.isNotBlank() } ?: exObj.rest ?: "2 min",
                                lastSetTechnique = existing.lastSetTechnique?.takeIf { it.isNotBlank() } ?: "N/A"
                            )
                        }
                    }

                    exObj.copy(
                        name = resolvedName,
                        muscleGroup = resolvedGroup,
                        warmupSets = exObj.warmupSets?.takeIf { it.isNotBlank() } ?: "1",
                        workingSets = exObj.workingSets?.takeIf { it.isNotBlank() } ?: "3",
                        repRange = exObj.repRange?.takeIf { it.isNotBlank() } ?: "8-10",
                        reps = exObj.reps?.takeIf { it.isNotBlank() } ?: "10",
                        earlySetRPE = exObj.earlySetRPE?.takeIf { it.isNotBlank() } ?: "~7",
                        rest = exObj.rest?.takeIf { it.isNotBlank() } ?: "2 min",
                        lastSetTechnique = exObj.lastSetTechnique?.takeIf { it.isNotBlank() } ?: "N/A",
                        substitution1 = s1,
                        substitution2 = s2
                    )
                }
                dayObj.copy(exercises = sanitizedExercises)
            }
            weekObj.copy(days = sanitizedDays)
        }

        return filledProgram.copy(
            programName = filledProgram.programName.takeIf { it.isNotBlank() } ?: "The Bodybuilding Transformation System - Intermediate-Advanced",
            author = filledProgram.author.takeIf { it.isNotBlank() } ?: "Jeff Nippard",
            weeks = sanitizedWeeks
        )
    }

    /**
     * Strictly validates a Program structure for incomplete keys.
     * Checks if all expected keys ('muscleGroup', 'substitution1', and 'substitution2')
     * are explicitly present, non-null, and non-blank on exercises belonging to active training days.
     * Returns a list of validation error descriptions. If the list is empty, the program is valid.
     */
    fun validateStrict(program: Program?): List<String> {
        val errors = mutableListOf<String>()
        if (program == null) {
            errors.add("Program JSON root is empty or invalid.")
            return errors
        }

        if (program.programName.isBlank()) {
            errors.add("Missing expected key: 'programName' should not be blank.")
        }
        if (program.author.isBlank()) {
            errors.add("Missing expected key: 'author' should not be blank.")
        }
        if (program.weeks.isEmpty()) {
            errors.add("Missing expected key: 'weeks' map is empty or not defined.")
            return errors
        }

        program.weeks.forEach { (weekKey, weekObj) ->
            if (weekObj.block.isBlank() || weekObj.block == "Block") {
                errors.add("[$weekKey] Missing or invalid expected key: 'block' is not specified.")
            }
            if (weekObj.days.isEmpty()) {
                errors.add("[$weekKey] Missing expected key: 'days' list is empty or not defined.")
            } else {
                weekObj.days.forEachIndexed { dayIdx, dayObj ->
                    val dayLabel = dayObj.dayName.takeIf { it.isNotBlank() } ?: "Day ${dayIdx + 1}"
                    if (dayObj.dayName.isBlank()) {
                        errors.add("[$weekKey, Day $dayIdx] Missing expected key: 'dayName' is empty.")
                    }
                    if (!dayObj.isRestDay) {
                        if (dayObj.exercises.isEmpty()) {
                            errors.add("[$weekKey, $dayLabel] Non-rest day contains zero exercises.")
                        } else {
                            dayObj.exercises.forEachIndexed { exIdx, exObj ->
                                val exLabel = exObj.name.takeIf { it.isNotBlank() } ?: "Exercise ${exIdx + 1}"
                                if (exObj.name.isBlank()) {
                                    errors.add("[$weekKey, $dayLabel, Exercise $exIdx] Missing or empty exercise 'name'.")
                                }
                                if (exObj.muscleGroup == null || exObj.muscleGroup.isBlank()) {
                                    errors.add("[$weekKey, $dayLabel, $exLabel] Missing expected key: 'muscleGroup' is null or blank.")
                                }
                                if (exObj.substitution1 == null) {
                                    errors.add("[$weekKey, $dayLabel, $exLabel] Missing expected key: 'substitution1' is missing or null.")
                                } else {
                                    if (exObj.substitution1.name.isBlank()) {
                                        errors.add("[$weekKey, $dayLabel, $exLabel -> substitution1] Missing expected key: 'name' is empty.")
                                    }
                                    if (exObj.substitution1.muscleGroup == null || exObj.substitution1.muscleGroup.isBlank()) {
                                        errors.add("[$weekKey, $dayLabel, $exLabel -> substitution1] Missing expected key: 'muscleGroup' is null or blank.")
                                    }
                                }
                                if (exObj.substitution2 == null) {
                                    errors.add("[$weekKey, $dayLabel, $exLabel] Missing expected key: 'substitution2' is missing or null.")
                                } else {
                                    if (exObj.substitution2.name.isBlank()) {
                                        errors.add("[$weekKey, $dayLabel, $exLabel -> substitution2] Missing expected key: 'name' is empty.")
                                    }
                                    if (exObj.substitution2.muscleGroup == null || exObj.substitution2.muscleGroup.isBlank()) {
                                        errors.add("[$weekKey, $dayLabel, $exLabel -> substitution2] Missing expected key: 'muscleGroup' is null or blank.")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return errors
    }
}
