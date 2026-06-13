package com.example.fastvideocutter.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"
    private const val APP_ID = "fast-video-cutter"

    private fun getFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    fun uploadSessionToCloud(context: Context, userId: String, session: SavedSession) {
        try {
            val db = getFirestore()
            val sessionDocRef = db.collection("artifacts")
                .document(APP_ID)
                .collection("users")
                .document(userId)
                .collection("sessions")
                .document(session.id)

            // 1. Create Session Metadata doc
            val cloudChunksList = mutableListOf<Map<String, Any>>()
            
            // Map segments metadata first
            session.segments.forEachIndexed { index, seg ->
                cloudChunksList.add(mapOf(
                    "start" to (seg.startMs / 1000).toInt(),
                    "end" to (seg.endMs / 1000).toInt(),
                    "url" to "firestore",
                    "ext" to "mp4"
                ))
            }

            val sessionData = mapOf(
                "fileName" to session.fileName,
                "duration" to (session.durationMs / 1000).toDouble(),
                "chunksCount" to session.segments.size,
                "timestamp" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(java.util.Date(session.timestamp)),
                "cloudChunks" to cloudChunksList,
                "hasCloudData" to true
            )
            
            Tasks.await(sessionDocRef.set(sessionData))
            Log.d(TAG, "Uploaded session metadata successfully: ${session.id}")

            // 2. Slice and upload chunks to Firestore
            session.segments.forEachIndexed { i, seg ->
                val file = File(seg.filePath)
                if (!file.exists()) return@forEachIndexed

                val fileBytes = file.readBytes()
                val sliceSize = 800 * 1024 // 800KB
                val slicesCount = Math.ceil(fileBytes.size.toDouble() / sliceSize.toDouble()).toInt()

                // Create chunk document
                val chunkDocRef = sessionDocRef.collection("video_chunks").document("chunk_$i")
                val chunkData = mapOf(
                    "idx" to i,
                    "start" to (seg.startMs / 1000).toInt(),
                    "end" to (seg.endMs / 1000).toInt(),
                    "ext" to "mp4",
                    "slicesCount" to slicesCount
                )
                Tasks.await(chunkDocRef.set(chunkData))

                // Upload slices in parallel/sequentially
                for (j in 0 until slicesCount) {
                    val startByte = j * sliceSize
                    val endByte = minOf((j + 1) * sliceSize, fileBytes.size)
                    val sliceBytes = fileBytes.sliceArray(startByte until endByte)

                    val sliceDocRef = chunkDocRef.collection("slices").document("slice_$j")
                    val sliceData = mapOf(
                        "idx" to j,
                        "bytes" to Blob.fromBytes(sliceBytes)
                    )
                    Tasks.await(sliceDocRef.set(sliceData))
                }
                Log.d(TAG, "Uploaded segment chunk $i (slices: $slicesCount) successfully")
            }
            Log.d(TAG, "Session fully uploaded to cloud: ${session.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload session to cloud", e)
        }
    }

    fun fetchCloudSessions(userId: String): List<SavedSession> {
        try {
            val db = getFirestore()
            val sessionsQuery = db.collection("artifacts")
                .document(APP_ID)
                .collection("users")
                .document(userId)
                .collection("sessions")
                .orderBy("timestamp", Query.Direction.DESCENDING)

            val snapshot = Tasks.await(sessionsQuery.get())
            val sessions = mutableListOf<SavedSession>()

            for (doc in snapshot.documents) {
                val id = doc.id
                val fileName = doc.getString("fileName") ?: "video.mp4"
                val durationSec = doc.getDouble("duration") ?: 60.0
                val chunksCount = doc.getLong("chunksCount") ?: 1
                val timestampStr = doc.getString("timestamp") ?: ""
                
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                val timestamp = try {
                    sdf.parse(timestampStr)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                // Parse segments
                val cloudChunks = doc.get("cloudChunks") as? List<Map<String, Any>>
                val segments = mutableListOf<SavedSegment>()
                
                if (cloudChunks != null) {
                    cloudChunks.forEachIndexed { i, cc ->
                        val startSec = (cc["start"] as? Number)?.toLong() ?: (i * 10L)
                        val endSec = (cc["end"] as? Number)?.toLong() ?: ((i + 1) * 10L)
                        val name = "חלק ${i + 1} (${formatTime(startSec * 1000)} - ${formatTime(endSec * 1000)})"
                        
                        // Local path placeholder
                        segments.add(
                            SavedSegment(
                                startMs = startSec * 1000,
                                endMs = endSec * 1000,
                                filePath = "", // Placeholder: will be downloaded on demand
                                name = name,
                                isDownloaded = false
                            )
                        )
                    }
                }

                val durationMs = (durationSec * 1000).toLong()
                val seconds = durationMs / 1000
                val m = (seconds / 60).toString().padStart(2, '0')
                val s = (seconds % 60).toString().padStart(2, '0')
                val formatted = "$m:$s"

                sessions.add(
                    SavedSession(
                        id = id,
                        fileName = fileName,
                        durationMs = durationMs,
                        formattedDuration = formatted,
                        timestamp = timestamp,
                        segments = segments,
                        isCloud = true
                    )
                )
            }
            return sessions
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch cloud sessions", e)
            return emptyList()
        }
    }

    fun downloadSegmentFromCloud(context: Context, userId: String, sessionId: String, chunkIndex: Int, destFile: File): Boolean {
        try {
            val db = getFirestore()
            val slicesQuery = db.collection("artifacts")
                .document(APP_ID)
                .collection("users")
                .document(userId)
                .collection("sessions")
                .document(sessionId)
                .collection("video_chunks")
                .document("chunk_$chunkIndex")
                .collection("slices")
                .orderBy("idx")

            val snapshot = Tasks.await(slicesQuery.get())
            if (snapshot.isEmpty) {
                Log.e(TAG, "No slices found for chunk $chunkIndex")
                return false
            }

            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { outputStream ->
                for (doc in snapshot.documents) {
                    val blob = doc.get("bytes") as? Blob
                    if (blob != null) {
                        outputStream.write(blob.toBytes())
                    }
                }
            }
            Log.d(TAG, "Downloaded segment $chunkIndex to ${destFile.absolutePath} successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download segment $chunkIndex", e)
            return false
        }
    }

    fun deleteSessionFromCloud(userId: String, sessionId: String) {
        try {
            val db = getFirestore()
            val sessionDocRef = db.collection("artifacts")
                .document(APP_ID)
                .collection("users")
                .document(userId)
                .collection("sessions")
                .document(sessionId)

            // Get video chunks
            val chunksRef = sessionDocRef.collection("video_chunks")
            val chunksSnapshot = Tasks.await(chunksRef.get())

            for (chunkDoc in chunksSnapshot.documents) {
                // Get slices
                val slicesRef = chunkDoc.reference.collection("slices")
                val slicesSnapshot = Tasks.await(slicesRef.get())
                for (sliceDoc in slicesSnapshot.documents) {
                    Tasks.await(sliceDoc.reference.delete())
                }
                Tasks.await(chunkDoc.reference.delete())
            }
            Tasks.await(sessionDocRef.delete())
            Log.d(TAG, "Deleted cloud session $sessionId successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session from cloud", e)
        }
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val m = (seconds / 60).toString().padStart(2, '0')
        val s = (seconds % 60).toString().padStart(2, '0')
        return "$m:$s"
    }
}
