package com.example.fastvideocutter.ui.main

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fastvideocutter.data.VideoCutter
import com.example.fastvideocutter.data.VideoInfo
import com.example.fastvideocutter.data.VideoSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
                _segments.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Cutting failed", e)
                _errorMessage.value = "החיתוך נכשל: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun clearVideo() {
        _selectedVideo.value = null
        _segments.value = emptyList()
        _progress.value = 0
        _errorMessage.value = null
    }

    fun saveSegment(context: Context, segment: VideoSegment, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedUri = VideoCutter.saveSegmentToGallery(context, segment)
            if (savedUri != null) {
                onSuccess("נשמר בגלריה בהצלחה!")
            } else {
                onError("שגיאה בשמירת הסרטון")
            }
        }
    }
}
