package com.example.fastvideocutter.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedSession(
    val id: String,
    val fileName: String,
    val durationMs: Long,
    val formattedDuration: String,
    val timestamp: Long,
    val segments: List<SavedSegment>,
    val isCloud: Boolean = false,
    val isPinned: Boolean = false
)

data class SavedSegment(
    val startMs: Long,
    val endMs: Long,
    val filePath: String,
    val name: String,
    val isDownloaded: Boolean = false
)

object HistoryManager {
    private const val TAG = "HistoryManager"
    private const val FILE_NAME = "history_v1.json"

    private fun getHistoryFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    fun saveSession(context: Context, session: SavedSession) {
        try {
            val sessions = loadSessions(context).toMutableList()
            // Remove existing with same id if any
            sessions.removeAll { it.id == session.id }
            sessions.add(0, session) // Add at beginning (newest first)
            
            writeSessions(context, sessions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session to history", e)
        }
    }

    fun loadSessions(context: Context): List<SavedSession> {
        val file = getHistoryFile(context)
        if (!file.exists()) return emptyList()
        
        try {
            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)
            val sessions = mutableListOf<SavedSession>()
            
            for (i in 0 until jsonArray.length()) {
                val sObj = jsonArray.getJSONObject(i)
                val id = sObj.getString("id")
                val fileName = sObj.getString("fileName")
                val durationMs = sObj.getLong("durationMs")
                val formattedDuration = sObj.getString("formattedDuration")
                val timestamp = sObj.getLong("timestamp")
                val isPinned = sObj.optBoolean("isPinned", false)
                
                val segArray = sObj.getJSONArray("segments")
                val segments = mutableListOf<SavedSegment>()
                for (j in 0 until segArray.length()) {
                    val segObj = segArray.getJSONObject(j)
                    segments.add(
                        SavedSegment(
                            startMs = segObj.getLong("startMs"),
                            endMs = segObj.getLong("endMs"),
                            filePath = segObj.getString("filePath"),
                            name = segObj.getString("name"),
                            isDownloaded = segObj.optBoolean("isDownloaded", false)
                        )
                    )
                }
                
                // Add session directly without filtering missing local files
                sessions.add(SavedSession(id, fileName, durationMs, formattedDuration, timestamp, segments, isPinned = isPinned))
            }
            return sessions
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sessions", e)
            return emptyList()
        }
    }

    fun deleteSession(context: Context, sessionId: String) {
        try {
            val sessions = loadSessions(context).toMutableList()
            val removed = sessions.find { it.id == sessionId }
            if (removed != null) {
                // Delete files
                removed.segments.forEach { seg ->
                    val file = File(seg.filePath)
                    if (file.exists()) file.delete()
                }
                sessions.remove(removed)
                writeSessions(context, sessions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session", e)
        }
    }

    private fun writeSessions(context: Context, sessions: List<SavedSession>) {
        val jsonArray = JSONArray()
        for (session in sessions) {
            val sObj = JSONObject().apply {
                put("id", session.id)
                put("fileName", session.fileName)
                put("durationMs", session.durationMs)
                put("formattedDuration", session.formattedDuration)
                put("timestamp", session.timestamp)
                put("isPinned", session.isPinned)
                
                val segArray = JSONArray()
                for (seg in session.segments) {
                    val segObj = JSONObject().apply {
                        put("startMs", seg.startMs)
                        put("endMs", seg.endMs)
                        put("filePath", seg.filePath)
                        put("name", seg.name)
                        put("isDownloaded", seg.isDownloaded)
                    }
                    segArray.put(segObj)
                }
                put("segments", segArray)
            }
            jsonArray.put(sObj)
        }
        
        getHistoryFile(context).writeText(jsonArray.toString())
    }

    fun renameSession(context: Context, sessionId: String, newName: String) {
        try {
            val sessions = loadSessions(context).toMutableList()
            val index = sessions.indexOfFirst { it.id == sessionId }
            if (index != -1) {
                val oldSession = sessions[index]
                sessions[index] = oldSession.copy(fileName = newName)
                writeSessions(context, sessions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename session", e)
        }
    }

    fun renameSegment(context: Context, sessionId: String, segmentIndex: Int, newName: String) {
        try {
            val sessions = loadSessions(context).toMutableList()
            val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex != -1) {
                val session = sessions[sessionIndex]
                val updatedSegments = session.segments.toMutableList()
                if (segmentIndex in updatedSegments.indices) {
                    val oldSeg = updatedSegments[segmentIndex]
                    updatedSegments[segmentIndex] = oldSeg.copy(name = newName)
                    sessions[sessionIndex] = session.copy(segments = updatedSegments)
                    writeSessions(context, sessions)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename segment in history", e)
        }
    }

    fun toggleSegmentChecked(context: Context, sessionId: String, segmentIndex: Int, checked: Boolean) {
        try {
            val sessions = loadSessions(context).toMutableList()
            val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex != -1) {
                val session = sessions[sessionIndex]
                val updatedSegments = session.segments.toMutableList()
                if (segmentIndex in updatedSegments.indices) {
                    val oldSeg = updatedSegments[segmentIndex]
                    updatedSegments[segmentIndex] = oldSeg.copy(isDownloaded = checked)
                    sessions[sessionIndex] = session.copy(segments = updatedSegments)
                    writeSessions(context, sessions)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle segment checked in history", e)
        }
    }

    fun toggleSessionPinned(context: Context, sessionId: String, isPinned: Boolean) {
        try {
            val sessions = loadSessions(context).toMutableList()
            val index = sessions.indexOfFirst { it.id == sessionId }
            if (index != -1) {
                val oldSession = sessions[index]
                sessions[index] = oldSession.copy(isPinned = isPinned)
                writeSessions(context, sessions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle session pinned", e)
        }
    }
}
