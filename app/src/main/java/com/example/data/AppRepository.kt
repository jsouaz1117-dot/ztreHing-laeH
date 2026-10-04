package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(private val appDao: AppDao) {
    val allPresets: Flow<List<FrequencyPreset>> = appDao.getAllPresets()
    val allSessions: Flow<List<ListeningSession>> = appDao.getAllSessions()
    val allNotes: Flow<List<SessionNote>> = appDao.getAllNotes()
    val allRampingSequences: Flow<List<SavedRampingSequence>> = appDao.getAllRampingSequences()

    suspend fun insertPreset(preset: FrequencyPreset) {
        appDao.insertPreset(preset)
    }

    suspend fun deletePreset(preset: FrequencyPreset) {
        appDao.deletePreset(preset)
    }

    suspend fun insertSession(session: ListeningSession) {
        appDao.insertSession(session)
    }

    suspend fun clearHistory() {
        appDao.clearHistory()
    }

    suspend fun insertNote(note: SessionNote) {
        appDao.insertNote(note)
    }

    suspend fun deleteNote(note: SessionNote) {
        appDao.deleteNote(note)
    }

    suspend fun insertRampingSequence(sequence: SavedRampingSequence) {
        appDao.insertRampingSequence(sequence)
    }

    suspend fun deleteRampingSequence(sequence: SavedRampingSequence) {
        appDao.deleteRampingSequence(sequence)
    }
}

