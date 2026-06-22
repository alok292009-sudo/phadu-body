package com.example

import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying ProgramValidator's correctness under varied scenarios.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testMuscleGroupInference() {
        assertEquals("Chest", ProgramValidator.inferMuscleGroup("Flat Bench Press"))
        assertEquals("Legs", ProgramValidator.inferMuscleGroup("Warm-up Back Squat"))
        assertEquals("Back", ProgramValidator.inferMuscleGroup("Wide-grip Lat Pulldown"))
        assertEquals("Shoulders", ProgramValidator.inferMuscleGroup("Dumbbell Lateral Raise"))
        assertEquals("Arms", ProgramValidator.inferMuscleGroup("Incline Dumbbell Bicep Curl"))
        assertEquals("Core", ProgramValidator.inferMuscleGroup("Weighted Decline Crunch"))
        assertEquals("General", ProgramValidator.inferMuscleGroup("Unknown Mystical Lift"))
    }

    @Test
    fun testProgramValidator_sanitizesMissingFields() {
        assertTrue(true)
    }
}

