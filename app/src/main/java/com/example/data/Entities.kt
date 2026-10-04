package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class FrequencyPreset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val frequency1: Float,
    val frequency2: Float,
    val isBinaural: Boolean = false,
    val vowelShapeName: String = "PURE_SINE",
    val isCustom: Boolean = true
)

@Entity(tableName = "sessions")
data class ListeningSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val presetName: String,
    val frequency1: Float,
    val frequency2: Float,
    val isBinaural: Boolean,
    val durationSeconds: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "session_notes")
data class SessionNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val colorHex: String = "#1E293B",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ramping_sequences")
data class SavedRampingSequence(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val stepsEncoded: String,
    val timestamp: Long = System.currentTimeMillis()
)

