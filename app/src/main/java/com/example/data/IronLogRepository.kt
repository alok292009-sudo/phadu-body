package com.example.data

import com.example.model.Exercise
import com.example.model.PersonalRecord
import com.example.model.Template
import com.example.model.Workout
import com.example.model.ActiveProgramState
import kotlinx.coroutines.flow.Flow

interface IronLogRepository {
    fun getExercises(): Flow<List<Exercise>>
    suspend fun addExercise(exercise: Exercise)
    suspend fun seedInitialExercises()
    
    fun getTemplates(): Flow<List<Template>>
    suspend fun saveTemplate(template: Template)
    suspend fun deleteTemplate(templateId: String)
    suspend fun clearAllTemplates()
    
    fun getWorkouts(): Flow<List<Workout>>
    fun getActiveWorkout(): Flow<Workout?>
    suspend fun saveWorkout(workout: Workout)
    suspend fun deleteWorkout(workoutId: String)
    suspend fun finishWorkout(workout: Workout)
    
    fun getPersonalRecords(): Flow<List<PersonalRecord>>
    fun getPersonalRecord(exerciseId: String): Flow<PersonalRecord?>
    
    fun getActiveProgramState(): Flow<ActiveProgramState?>
    suspend fun saveActiveProgramState(state: ActiveProgramState?)
    
    
    fun getUserProfile(): Flow<com.example.model.UserProfile?>
    suspend fun saveUserProfile(profile: com.example.model.UserProfile)
    
    suspend fun signOut()
}
