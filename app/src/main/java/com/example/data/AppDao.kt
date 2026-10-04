package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Custom presets queries
    @Query("SELECT * FROM presets ORDER BY id DESC")
    fun getAllPresets(): Flow<List<FrequencyPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: FrequencyPreset): Long

    @Delete
    suspend fun deletePreset(preset: FrequencyPreset)

    // History log queries
    @Query("SELECT * FROM sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ListeningSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ListeningSession): Long

    @Query("DELETE FROM sessions")
    suspend fun clearHistory()

    // Session Notes queries
    @Query("SELECT * FROM session_notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<SessionNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: SessionNote): Long

    @Delete
    suspend fun deleteNote(note: SessionNote)

    // Saved Ramping Sequences
    @Query("SELECT * FROM ramping_sequences ORDER BY timestamp DESC")
    fun getAllRampingSequences(): Flow<List<SavedRampingSequence>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRampingSequence(sequence: SavedRampingSequence): Long

    @Delete
    suspend fun deleteRampingSequence(sequence: SavedRampingSequence)
}

