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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.fastvideocutter.data.SavedSession
import com.example.fastvideocutter.data.VideoSegment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()

    var showGoogleDialog by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf<SavedSession?>(null) }
    var segmentToRenameIndex by remember { mutableStateOf<Int?>(null) }

    // Initialize state on launch
    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectVideo(context, uri)
        }
    }

    if (userEmail == null) {
        // Show Login / Registration Screen
        LoginRegisterScreen(
            onAuthSuccess = { email, pass, isLogin ->
                val success = if (isLogin) {
                    viewModel.loginUser(context, email, pass)
                } else {
                    viewModel.registerUser(context, email, pass)
                }
                if (!success) {
                    Toast.makeText(context, "שגיאה בפרטי הכניסה/הרשמה", Toast.LENGTH_SHORT).show()
                }
            },
            onGoogleSignInClick = {
                showGoogleDialog = true
            }
        )
    } else {
        // Show main app content
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "חותך וידאו מהיר AI",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1B4B),
                                fontSize = 18.sp
                            )
                            Text(
                                text = "שלום, $userEmail",
                                fontSize = 11.sp,
                                color = Color(0xFF6366F1),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.logoutUser(context) }) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "התנתק",
                                tint = Color(0xFFEF4444)
                            )
                        }
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
                    // Selection view and history list
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Select video card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(vertical = 12.dp)
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
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFEEF2FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "בחר קובץ",
                                        tint = Color(0xFF6366F1),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "בחר סרטון מהגלריה",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color(0xFF1F2937)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "תומך ב-MP4, MOV, WEBM ועוד",
                                    fontSize = 13.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }

                        // History section
                        Text(
                            text = "היסטוריית חיתוכים מקומית",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E1B4B),
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Right
                        )

                        if (historyList.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "אין היסטוריית חיתוכים עדיין.\nכל סרטון שתחתוך יישמר במכשיר זה.",
                                    color = Color(0xFF9CA3AF),
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                itemsIndexed(historyList) { _, session ->
                                    HistoryItemCard(
                                        session = session,
                                        onLoadClick = { viewModel.restoreSession(context, session) },
                                        onDeleteClick = { viewModel.deleteHistoryItem(context, session.id) },
                                        onRenameClick = { sessionToRename = session },
                                        onPinClick = { viewModel.toggleSessionPinned(context, session.id, !session.isPinned) }
                                    )
                                }
                            }
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

                    Spacer(modifier = Modifier.height(8.dp))

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
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            itemsIndexed(segments) { index, segment ->
                                SegmentItemWithPlayer(
                                    segment = segment,
                                    index = index,
                                    onSaveClick = {
                                        viewModel.saveSegment(
                                            context = context,
                                            segmentIndex = index,
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
                                    },
                                    onToggleChecked = { checked ->
                                        viewModel.toggleSegmentChecked(context, index, checked)
                                    },
                                    onRenameClick = {
                                        segmentToRenameIndex = index
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

    if (showGoogleDialog) {
        GoogleAccountChooserDialog(
            onAccountSelected = { email ->
                showGoogleDialog = false
                val success = viewModel.loginWithGoogle(context, email)
                if (!success) {
                    Toast.makeText(context, "שגיאה בהתחברות עם Google", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showGoogleDialog = false }
        )
    }

    if (sessionToRename != null) {
        var newName by remember { mutableStateOf(sessionToRename!!.fileName) }
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text("שנה שם היסטוריה", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("שם חדש") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color(0xFF6366F1),
                        unfocusedLabelColor = Color(0xFF4B5563)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renameSession(context, sessionToRename!!.id, newName)
                            sessionToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("אישור")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("ביטול", color = Color(0xFF4B5563))
                }
            },
            containerColor = Color.White
        )
    }

    if (segmentToRenameIndex != null) {
        val segment = segments[segmentToRenameIndex!!]
        var newName by remember { mutableStateOf(segment.name) }
        AlertDialog(
            onDismissRequest = { segmentToRenameIndex = null },
            title = { Text("שנה שם חלק", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("שם חדש") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = Color(0xFF6366F1),
                        unfocusedLabelColor = Color(0xFF4B5563)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renameSegment(context, segmentToRenameIndex!!, newName)
                            segmentToRenameIndex = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("אישור")
                }
            },
            dismissButton = {
                TextButton(onClick = { segmentToRenameIndex = null }) {
                    Text("ביטול", color = Color(0xFF4B5563))
                }
            },
            containerColor = Color.White
        )
    }
}

@Composable
fun LoginRegisterScreen(
    onAuthSuccess: (String, String, Boolean) -> Unit,
    onGoogleSignInClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFEEF2FF)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF2FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "אימייל",
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isLoginMode) "התחברות לאפליקציה" else "הרשמה לאפליקציה",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1F2937)
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("אימייל") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFFD1D5DB),
                        focusedTextColor = Color(0xFF1F2937),
                        unfocusedTextColor = Color(0xFF1F2937),
                        focusedLabelColor = Color(0xFF6366F1),
                        unfocusedLabelColor = Color(0xFF4B5563),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("סיסמה") },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFFD1D5DB),
                        focusedTextColor = Color(0xFF1F2937),
                        unfocusedTextColor = Color(0xFF1F2937),
                        focusedLabelColor = Color(0xFF6366F1),
                        unfocusedLabelColor = Color(0xFF4B5563),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            onAuthSuccess(email, password, isLoginMode)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(
                        text = if (isLoginMode) "התחבר" else "הרשם עכשיו",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE5E7EB))
                    Text(
                        text = "או",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE5E7EB))
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onGoogleSignInClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1F2937)),
                    border = BorderStroke(1.dp, Color(0xFFD1D5DB))
                ) {
                    Text(
                        text = "התחבר עם Google",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF4B5563)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = { isLoginMode = !isLoginMode }) {
                    Text(
                        text = if (isLoginMode) "אין לך משתמש? לחץ להרשמה" else "כבר רשום? לחץ להתחברות",
                        color = Color(0xFF6366F1),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    session: SavedSession,
    onLoadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: () -> Unit,
    onPinClick: () -> Unit
) {
    val date = Date(session.timestamp)
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateString = formatter.format(date)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable { onLoadClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1F2937),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onRenameClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "שנה שם",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onPinClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (session.isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (session.isPinned) "בטל הצמדה" else "הצמד",
                            tint = if (session.isPinned) Color(0xFFFBBF24) else Color(0xFF9CA3AF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$dateString • ${session.segments.size} חלקים • אורך: ${session.formattedDuration}",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
            }
            IconButton(
                onClick = onDeleteClick,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFFEE2E2))
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "מחק היסטוריה",
                    tint = Color(0xFFEF4444)
                )
            }
        }
    }
}

@Composable
fun SegmentItemWithPlayer(
    segment: VideoSegment,
    index: Int,
    onSaveClick: () -> Unit,
    onShareClick: () -> Unit,
    onToggleChecked: (Boolean) -> Unit,
    onRenameClick: () -> Unit
) {
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = segment.isDownloaded,
                    onCheckedChange = onToggleChecked,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF6366F1),
                        uncheckedColor = Color(0xFF9CA3AF)
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = segment.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1F2937)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onRenameClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "שנה שם חלק",
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val durationSec = ((segment.endMs - segment.startMs) / 1000).toInt()
                    Text(
                        text = "${formatTimeRange(segment.startMs, segment.endMs)} ($durationSec שניות)",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Preview Player Toggle
                IconButton(
                    onClick = {
                        if (!isPlayerExpanded) {
                            isPlayerExpanded = true
                            isPlaying = true
                        } else {
                            isPlaying = !isPlaying
                            if (isPlaying) {
                                videoViewInstance?.start()
                            } else {
                                videoViewInstance?.pause()
                            }
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isPlaying) Color(0xFF4338CA) else Color(0xFFEEF2FF)
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "נגן תצוגה מקדימה",
                        tint = if (isPlaying) Color.White else Color(0xFF6366F1)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
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
                Spacer(modifier = Modifier.width(8.dp))
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

            // Expandable Video Player for preview
            AnimatedVisibility(visible = isPlayerExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoPath(segment.file.absolutePath)
                                    val mediaController = MediaController(ctx)
                                    mediaController.setAnchorView(this)
                                    setMediaController(mediaController)
                                    setOnCompletionListener {
                                        isPlaying = false
                                    }
                                    setOnPreparedListener {
                                        seekTo(1) // Show first frame
                                        if (isPlaying) {
                                            start()
                                        }
                                    }
                                    videoViewInstance = this
                                }
                            },
                            update = { view ->
                                if (isPlaying) {
                                    if (!view.isPlaying) view.start()
                                } else {
                                    if (view.isPlaying) view.pause()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Close button overlay
                        IconButton(
                            onClick = {
                                isPlayerExpanded = false
                                isPlaying = false
                                videoViewInstance?.stopPlayback()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "סגור נגן",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
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

@Composable
fun GoogleAccountChooserDialog(
    onAccountSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCustomEmailInput by remember { mutableStateOf(false) }
    var customEmail by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("o", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("g", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("l", color = Color(0xFF34A853), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("e", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
                Text(
                    text = "בחירת חשבון",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1F2937),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "כדי להמשיך אל חותך וידאו מהיר AI",
                    fontSize = 14.sp,
                    color = Color(0xFF4B5563),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!showCustomEmailInput) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAccountSelected("yuvalkarin20@gmail.com") }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF6366F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Y",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "yuvalkarin20",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1F2937)
                            )
                            Text(
                                text = "yuvalkarin20@gmail.com",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                    
                    HorizontalDivider(color = Color(0xFFE5E7EB))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCustomEmailInput = true }
                            .padding(vertical = 16.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "חשבון אחר",
                            tint = Color(0xFF4B5563)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "השתמש בחשבון אחר",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = Color(0xFF4B5563)
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = customEmail,
                            onValueChange = { customEmail = it },
                            label = { Text("כתובת אימייל של Google") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6366F1),
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedLabelColor = Color(0xFF6366F1),
                                unfocusedLabelColor = Color(0xFF4B5563)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showCustomEmailInput = false }) {
                                Text("חזור", color = Color(0xFF4B5563))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (customEmail.isNotBlank() && customEmail.contains("@")) {
                                        onAccountSelected(customEmail)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                            ) {
                                Text("המשך")
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "לפני השימוש באפליקציה זו, Google תשתף את שמך, כתובת האימייל ותמונת הפרופיל שלך. ראה את מדיניות הפרטיות ותנאי השירות.",
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF),
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {}
    )
}
