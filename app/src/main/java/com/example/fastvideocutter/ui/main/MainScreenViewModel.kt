package com.example.fastvideocutter.ui.main

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fastvideocutter.data.AuthManager
import com.example.fastvideocutter.data.HistoryManager
import com.example.fastvideocutter.data.SavedSegment
import com.example.fastvideocutter.data.SavedSession
import com.example.fastvideocutter.data.VideoCutter
import com.example.fastvideocutter.data.VideoInfo
import com.example.fastvideocutter.data.VideoSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainScreenViewModel : ViewModel() {
    private val TAG = "MainScreenViewModel"

    private val _selectedVideo = MutableStateFlow<VideoInfo?>(null)
    val selectedVideo: StateFlow<VideoInfo?> = _selectedVideo.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _segments = MutableStateFlow<List<VideoSegment>>(emptyList())
    val segments: StateFlow<List<VideoSegment>> = _segments.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Auth States
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    // History States
    private val _historyList = MutableStateFlow<List<SavedSession>>(emptyList())
    val historyList: StateFlow<List<SavedSession>> = _historyList.asStateFlow()

    fun init(context: Context) {
        _userEmail.value = AuthManager.getLoggedInEmail(context)
        loadHistory(context)
    }

    fun selectVideo(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _errorMessage.value = null
            _segments.value = emptyList()
            val info = VideoCutter.getVideoInfo(context, uri)
            if (info != null) {
                _selectedVideo.value = info
            } else {
                _errorMessage.value = "שגיאה בטעינת נתוני הוידאו"
            }
        }
    }

    fun startCutting(context: Context) {
        val video = _selectedVideo.value ?: return
        _isProcessing.value = true
        _progress.value = 0
        _errorMessage.value = null
        _segments.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = VideoCutter.splitVideo(
                    context = context,
                    videoUri = video.uri,
                    durationMs = video.durationMs,
                    onProgress = { p ->
                        _progress.value = p
                    }
                )
                
                // Save to History
                val savedSegments = result.map { seg ->
                    SavedSegment(seg.startMs, seg.endMs, seg.file.absolutePath, seg.name)
                }
                val session = SavedSession(
                    id = "session_${System.currentTimeMillis()}",
                    fileName = video.name,
                    durationMs = video.durationMs,
                    formattedDuration = video.formattedDuration,
                    timestamp = System.currentTimeMillis(),
                    segments = savedSegments
                )
                HistoryManager.saveSession(context, session)
                
                _segments.value = result
                loadHistory(context) // Reload history list
            } catch (e: Exception) {
                Log.e(TAG, "Cutting failed", e)
                _errorMessage.value = "החיתוך נכשל: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun loadHistory(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _historyList.value = HistoryManager.loadSessions(context)
        }
    }

    fun deleteHistoryItem(context: Context, sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            HistoryManager.deleteSession(context, sessionId)
            loadHistory(context)
        }
    }

    fun restoreSession(session: SavedSession) {
        val restoredSegments = session.segments.map { seg ->
            VideoSegment(
                startMs = seg.startMs,
                endMs = seg.endMs,
                file = File(seg.filePath),
                name = seg.name
            )
        }
        _selectedVideo.value = VideoInfo(
            uri = Uri.parse(restoredSegments.first().file.absolutePath), // Mock uri for display
            name = session.fileName,
            durationMs = session.durationMs,
            formattedDuration = session.formattedDuration
        )
        _segments.value = restoredSegments
    }

    fun clearVideo() {
        _selectedVideo.value = null
        _segments.value = emptyList()
        _progress.value = 0
        _errorMessage.value = null
    }

    // Fixed Toast background thread crash
    fun saveSegment(context: Context, segment: VideoSegment, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.Main) { // Execute callback trigger on Main thread
            val savedUri = withContext(Dispatchers.IO) {
                VideoCutter.saveSegmentToGallery(context, segment)
            }
            if (savedUri != null) {
                onSuccess("נשמר בגלריה בהצלחה!")
            } else {
                onError("שגיאה בשמירת הסרטון")
            }
        }
    }

    // Auth actions
    fun registerUser(context: Context, email: String, pass: String): Boolean {
        val success = AuthManager.register(context, email, pass)
        if (success) {
            _userEmail.value = email
        }
        return success
    }

    fun loginUser(context: Context, email: String, pass: String): Boolean {
        val success = AuthManager.login(context, email, pass)
        if (success) {
            _userEmail.value = email
        }
        return success
    }

    fun logoutUser(context: Context) {
        AuthManager.logout(context)
        _userEmail.value = null
        clearVideo()
    }
}
