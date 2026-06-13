package com.example.fastvideocutter.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.math.ceil

data class VideoInfo(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val formattedDuration: String
)

data class VideoSegment(
    val startMs: Long,
    val endMs: Long,
    val file: File,
    val name: String,
    val isDownloaded: Boolean = false
)

object VideoCutter {
    private const val TAG = "VideoCutter"

    fun getVideoInfo(context: Context, uri: Uri): VideoInfo? {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            
            // Get duration
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLong() ?: 0L
            
            // Get display name
            var name = "video.mp4"
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex) ?: "video.mp4"
                    }
                }
            }
            
            // Format duration
            val seconds = durationMs / 1000
            val m = (seconds / 60).toString().padStart(2, '0')
            val s = (seconds % 60).toString().padStart(2, '0')
            val formatted = "$m:$s"
            
            return VideoInfo(uri, name, durationMs, formatted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video info", e)
            return null
        } finally {
            retriever?.release()
        }
    }

    fun splitVideo(
        context: Context,
        videoUri: Uri,
        durationMs: Long,
        segmentDurationMs: Long = 10000L,
        numParts: Int? = null,
        onProgress: (Int) -> Unit
    ): List<VideoSegment> {
        val segments = mutableListOf<VideoSegment>()
        val numSegments = if (numParts != null && numParts > 0) {
            numParts
        } else {
            ceil(durationMs.toDouble() / segmentDurationMs.toDouble()).toInt()
        }
        val cacheDir = File(context.cacheDir, "segments").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        
        for (i in 0 until numSegments) {
            val startMs = if (numParts != null && numParts > 0) {
                (i * durationMs) / numParts
            } else {
                i * segmentDurationMs
            }
            val endMs = if (numParts != null && numParts > 0) {
                ((i + 1) * durationMs) / numParts
            } else {
                minOf((i + 1) * segmentDurationMs, durationMs)
            }
            val segmentName = "חלק ${i + 1} (${formatTime(startMs)} - ${formatTime(endMs)})"
            val segmentFile = File(cacheDir, "part_${i + 1}.mp4")
            
            try {
                cutVideo(context, videoUri, segmentFile, startMs * 1000L, endMs * 1000L)
                segments.add(VideoSegment(startMs, endMs, segmentFile, segmentName))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cut segment $i", e)
                throw e
            }
            
            val progress = ((i + 1) * 100) / numSegments
            onProgress(progress)
        }
        return segments
    }

    private fun cutVideo(context: Context, videoUri: Uri, outputFile: File, startUs: Long, endUs: Long) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, videoUri, null)
        
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val trackCount = extractor.trackCount
        val trackIndices = HashMap<Int, Int>()
        
        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                extractor.selectTrack(i)
                val newTrackIndex = muxer.addTrack(format)
                trackIndices[i] = newTrackIndex
            }
        }
        
        if (trackIndices.isEmpty()) {
            extractor.release()
            muxer.release()
            throw IllegalArgumentException("No video/audio tracks found in the source file.")
        }
        
        muxer.start()
        
        val bufferSize = 1024 * 1024 // 1MB buffer
        val byteBuffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()
        val lastPresentationTimeUs = HashMap<Int, Long>()
        
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        
        while (true) {
            val trackIndex = extractor.sampleTrackIndex
            if (trackIndex < 0) break
            
            val sampleTime = extractor.sampleTime
            if (sampleTime > endUs) break
            
            val sampleSize = extractor.readSampleData(byteBuffer, 0)
            if (sampleSize < 0) break
            
            val relativeTimeUs = sampleTime - startUs
            val presentationTimeUs = maxOf(0L, relativeTimeUs)
            
            val lastTime = lastPresentationTimeUs[trackIndex] ?: -1L
            if (presentationTimeUs <= lastTime) {
                bufferInfo.presentationTimeUs = lastTime + 9600L // Ensure increasing timestamp by adding a default frame duration
            } else {
                bufferInfo.presentationTimeUs = presentationTimeUs
            }
            
            lastPresentationTimeUs[trackIndex] = bufferInfo.presentationTimeUs
            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.flags = extractor.sampleFlags
            
            val muxerTrackIndex = trackIndices[trackIndex]
            if (muxerTrackIndex != null) {
                muxer.writeSampleData(muxerTrackIndex, byteBuffer, bufferInfo)
            }
            extractor.advance()
        }
        
        try {
            muxer.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop muxer (could be due to short tracks)", e)
        } finally {
            muxer.release()
            extractor.release()
        }
    }

    fun saveSegmentToGallery(context: Context, segment: VideoSegment): Uri? {
        val resolver = context.contentResolver
        val displayName = "fast_video_cutter_${System.currentTimeMillis()}_${segment.file.name}"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FastVideoCutter")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        
        val uri = resolver.insert(collectionUri, contentValues) ?: return null
        
        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(segment.file).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Log.e(TAG, "Failed to save segment to gallery", e)
            return null
        }
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val m = (seconds / 60).toString().padStart(2, '0')
        val s = (seconds % 60).toString().padStart(2, '0')
        return "$m:$s"
    }
}
