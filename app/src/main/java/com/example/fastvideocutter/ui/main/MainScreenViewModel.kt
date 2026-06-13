package com.example.fastvideocutter.ui.main

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fastvideocutter.data.AuthManager
import com.example.fastvideocutter.data.CloudSyncManager
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

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

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
                val newSessionId = "session_${System.currentTimeMillis()}"
                val savedSegments = result.map { seg ->
                    SavedSegment(
                        startMs = seg.startMs,
                        endMs = seg.endMs,
                        filePath = seg.file.absolutePath,
                        name = seg.name,
                        isDownloaded = seg.isDownloaded
                    )
                }
                val session = SavedSession(
                    id = newSessionId,
                    fileName = video.name,
                    durationMs = video.durationMs,
                    formattedDuration = video.formattedDuration,
                    timestamp = System.currentTimeMillis(),
                    segments = savedSegments
                )
                HistoryManager.saveSession(context, session)
                
                _activeSessionId.value = newSessionId
                _segments.value = result
                loadHistory(context) // Reload history list

                val email = AuthManager.getLoggedInEmail(context)
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (email != null && currentUser != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        CloudSyncManager.uploadSessionToCloud(context, currentUser.uid, session)
                        loadHistory(context)
                    }
                }
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
            val local = HistoryManager.loadSessions(context)
            val email = AuthManager.getLoggedInEmail(context)
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (email != null && currentUser != null) {
                val cloud = CloudSyncManager.fetchCloudSessions(currentUser.uid)
                val merged = (local + cloud).groupBy { it.id }.map { entry ->
                    entry.value.find { !it.isCloud } ?: entry.value.first()
                }.sortedWith(
                    compareByDescending<SavedSession> { it.isPinned }
                        .thenByDescending { it.timestamp }
                )
                _historyList.value = merged
            } else {
                _historyList.value = local.sortedWith(
                    compareByDescending<SavedSession> { it.isPinned }
                        .thenByDescending { it.timestamp }
                )
            }
        }
    }

    fun toggleSessionPinned(context: Context, sessionId: String, isPinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            HistoryManager.toggleSessionPinned(context, sessionId, isPinned)
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val sessions = HistoryManager.loadSessions(context)
                val session = sessions.find { it.id == sessionId }
                if (session != null) {
                    CloudSyncManager.uploadSessionToCloud(context, currentUser.uid, session)
                }
            }
            loadHistory(context)
        }
    }

    fun deleteHistoryItem(context: Context, sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            HistoryManager.deleteSession(context, sessionId)
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                CloudSyncManager.deleteSessionFromCloud(currentUser.uid, sessionId)
            }
            loadHistory(context)
        }
    }

    fun restoreSession(context: Context, session: SavedSession) {
        viewModelScope.launch(Dispatchers.Main) {
            val needsDownload = session.segments.any { it.filePath.isBlank() || !File(it.filePath).exists() }
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            
            if (needsDownload && currentUser != null) {
                _isProcessing.value = true
                _progress.value = 0
                _errorMessage.value = null
                
                val downloadedSegments = withContext(Dispatchers.IO) {
                    val list = mutableListOf<VideoSegment>()
                    val total = session.segments.size
                    session.segments.forEachIndexed { index, seg ->
                        val cacheFile = File(context.cacheDir, "segments/cloud_${session.id}_part_${index + 1}.mp4")
                        val success = CloudSyncManager.downloadSegmentFromCloud(
                            context = context,
                            userId = currentUser.uid,
                            sessionId = session.id,
                            chunkIndex = index,
                            destFile = cacheFile
                        )
                        if (success) {
                            list.add(
                                VideoSegment(
                                    startMs = seg.startMs,
                                    endMs = seg.endMs,
                                    file = cacheFile,
                                    name = seg.name,
                                    isDownloaded = seg.isDownloaded
                                )
                            )
                        }
                        val prog = ((index + 1) * 100) / total
                        _progress.value = prog
                    }
                    list
                }
                
                _isProcessing.value = false
                if (downloadedSegments.size == session.segments.size) {
                    _activeSessionId.value = session.id
                    _selectedVideo.value = VideoInfo(
                        uri = Uri.parse(downloadedSegments.first().file.absolutePath),
                        name = session.fileName,
                        durationMs = session.durationMs,
                        formattedDuration = session.formattedDuration
                    )
                    _segments.value = downloadedSegments
                } else {
                    _errorMessage.value = "שגיאה בהורדת קבצי הוידאו מהענן"
                }
            } else {
                val restoredSegments = session.segments.map { seg ->
                    VideoSegment(
                        startMs = seg.startMs,
                        endMs = seg.endMs,
                        file = File(seg.filePath),
                        name = seg.name,
                        isDownloaded = seg.isDownloaded
                    )
                }
                _activeSessionId.value = session.id
                _selectedVideo.value = VideoInfo(
                    uri = Uri.parse(restoredSegments.first().file.absolutePath),
                    name = session.fileName,
                    durationMs = session.durationMs,
                    formattedDuration = session.formattedDuration
                )
                _segments.value = restoredSegments
            }
        }
    }

    fun clearVideo() {
        _activeSessionId.value = null
        _selectedVideo.value = null
        _segments.value = emptyList()
        _progress.value = 0
        _errorMessage.value = null
    }

    // Fixed Toast background thread crash
    fun saveSegment(context: Context, segmentIndex: Int, segment: VideoSegment, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.Main) { // Execute callback trigger on Main thread
            val savedUri = withContext(Dispatchers.IO) {
                VideoCutter.saveSegmentToGallery(context, segment)
            }
            if (savedUri != null) {
                toggleSegmentChecked(context, segmentIndex, true)
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

    fun loginWithGoogle(context: Context, email: String): Boolean {
        val success = AuthManager.loginOrRegisterGoogleUser(context, email)
        if (success) {
            _userEmail.value = email
            loadHistory(context)
        }
        return success
    }

    fun logoutUser(context: Context) {
        AuthManager.logout(context)
        _userEmail.value = null
        clearVideo()
    }

    // History Renaming and Checkmarks
    fun renameSession(context: Context, sessionId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            HistoryManager.renameSession(context, sessionId, newName)
            loadHistory(context)
            
            // If the active session is the renamed one, update the displayed video info name!
            if (_activeSessionId.value == sessionId) {
                val currentVideo = _selectedVideo.value
                if (currentVideo != null) {
                    _selectedVideo.value = currentVideo.copy(name = newName)
                }
            }
        }
    }

    fun renameSegment(context: Context, segmentIndex: Int, newName: String) {
        val currentSegments = _segments.value.toMutableList()
        if (segmentIndex in currentSegments.indices) {
            val oldSeg = currentSegments[segmentIndex]
            currentSegments[segmentIndex] = oldSeg.copy(name = newName)
            _segments.value = currentSegments
            
            val sessionId = _activeSessionId.value
            if (sessionId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    HistoryManager.renameSegment(context, sessionId, segmentIndex, newName)
                    loadHistory(context)
                }
            }
        }
    }

    fun toggleSegmentChecked(context: Context, segmentIndex: Int, checked: Boolean) {
        val currentSegments = _segments.value.toMutableList()
        if (segmentIndex in currentSegments.indices) {
            val oldSeg = currentSegments[segmentIndex]
            currentSegments[segmentIndex] = oldSeg.copy(isDownloaded = checked)
            _segments.value = currentSegments
            
            val sessionId = _activeSessionId.value
            if (sessionId != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    HistoryManager.toggleSegmentChecked(context, sessionId, segmentIndex, checked)
                    loadHistory(context)
                }
            }
        }
    }
}
