package com.example.fastvideocutter.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.fastvideocutter.data.VideoSegment
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectVideo(context, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "חותך וידאו מהיר AI",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1B4B),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFEEF2FF)
                )
            )
        },
        containerColor = Color(0xFFF9FAFB)
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        text = error,
                        color = Color(0xFF991B1B),
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Right
                    )
                }
            }

            if (selectedVideo == null) {
                // Upload placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(2.dp, Color(0xFFC7D2FE), RoundedCornerShape(24.dp))
                        .clickable { filePickerLauncher.launch("video/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFEEF2FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "בחר קובץ",
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "גרור או בחר סרטון מהגלריה",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF1F2937)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "תומך ב-MP4, MOV, WEBM ועוד",
                            fontSize = 14.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }
            } else {
                val video = selectedVideo!!
                
                // Video info card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1F2937)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "אורך: ${video.formattedDuration}",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearVideo() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFF3F4F6))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "נקה",
                                tint = Color(0xFF4B5563)
                            )
                        }
                    }
                }

                // Video Preview Player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    var isPlaying by remember { mutableStateOf(false) }
                    
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(video.uri)
                                setMediaController(MediaController(ctx).apply {
                                    setAnchorView(this@apply)
                                })
                                setOnPreparedListener {
                                    // seek to start to show first frame
                                    seekTo(1)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions & Progress
                if (!isProcessing && segments.isEmpty()) {
                    Button(
                        onClick = { viewModel.startCutting(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(
                            text = "חתוך עכשיו ל-10 שניות",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isProcessing,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { progress / 100f },
                            color = Color(0xFF6366F1),
                            trackColor = Color(0xFFEEF2FF)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "מכין את הסרטון... $progress%",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xFF4B5563)
                        )
                    }
                }

                // Result segments list
                if (segments.isNotEmpty()) {
                    Text(
                        text = "הושלם! ${segments.size} חלקים מוכנים",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1E1B4B),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                        textAlign = TextAlign.Right
                    )
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        itemsIndexed(segments) { index, segment ->
                            SegmentItem(
                                segment = segment,
                                index = index,
                                onSaveClick = {
                                    viewModel.saveSegment(
                                        context = context,
                                        segment = segment,
                                        onSuccess = { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                onShareClick = {
                                    shareSegment(context, segment)
                                }
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun SegmentItem(
    segment: VideoSegment,
    index: Int,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEEF2FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6366F1)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "חלק ${index + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1F2937)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatTimeRange(segment.startMs, segment.endMs),
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Share button
                IconButton(
                    onClick = onShareClick,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFEEF2FF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "שתף",
                        tint = Color(0xFF6366F1)
                    )
                }
                // Save button
                Button(
                    onClick = onSaveClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "שמור לגלריה",
                        color = Color(0xFF6366F1),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun shareSegment(context: Context, segment: VideoSegment) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "com.example.fastvideocutter.fileprovider",
            segment.file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "שתף חלק ${segment.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "שגיאה בשיתוף הסרטון", Toast.LENGTH_SHORT).show()
    }
}

private fun formatTimeRange(startMs: Long, endMs: Long): String {
    fun format(ms: Long): String {
        val seconds = ms / 1000
        val m = (seconds / 60).toString().padStart(2, '0')
        val s = (seconds % 60).toString().padStart(2, '0')
        return "$m:$s"
    }
    return "${format(startMs)} - ${format(endMs)}"
}
