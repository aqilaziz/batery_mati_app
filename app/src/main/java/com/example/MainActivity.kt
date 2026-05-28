package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF121212) // Eye-safe pitch dark
                ) { innerPadding ->
                    ParentControlDashboard(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentControlDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(TimerService.PREFS_NAME, Context.MODE_PRIVATE) }

    // State bindings
    var pin by remember { mutableStateOf(prefs.getString(TimerService.KEY_PIN, "1234") ?: "1234") }
    var selectedMode by remember { mutableStateOf(prefs.getInt(TimerService.KEY_MODE, 0)) }
    var showStopPinDialog by remember { mutableStateOf(false) }
    var stopPinInput by remember { mutableStateOf("") }
    var stopPinError by remember { mutableStateOf(false) }
    var showStopPin by remember { mutableStateOf(false) }
    var showParentPin by remember { mutableStateOf(false) }
    
    // Countdown states
    var isTimerActive by remember { mutableStateOf(prefs.getBoolean(TimerService.KEY_IS_RUNNING, false)) }
    var secondsRemaining by remember { mutableStateOf(prefs.getLong(TimerService.KEY_TIME_LEFT, 0L)) }
    
    // Custom duration states
    var selectedMinutesStr by remember { mutableStateOf("15") }
    var selectedSecondsStr by remember { mutableStateOf("0") }
    
    // Permissions states
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    // Polling effect to live-update remaining timer seconds and state
    LaunchedEffect(isTimerActive) {
        while (true) {
            val currentActive = prefs.getBoolean(TimerService.KEY_IS_RUNNING, false)
            isTimerActive = currentActive
            if (currentActive) {
                val endStamp = prefs.getLong(TimerService.KEY_ACTIVE_END_TIMESTAMP, 0L)
                val currentStamp = System.currentTimeMillis()
                val delta = (endStamp - currentStamp) / 1000
                secondsRemaining = if (delta > 0) delta else 0L
                if (secondsRemaining == 0L) {
                    isTimerActive = false
                }
            } else {
                secondsRemaining = 0L
            }
            delay(1000)
        }
    }

    // Active lifecycle check for permissions
    LaunchedEffect(Unit) {
        while (true) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
            delay(2000)
        }
    }

    // Permission launcher for Android 13+ Notifications
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    if (showStopPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showStopPinDialog = false
                stopPinInput = ""
                stopPinError = false
                showStopPin = false
            },
            title = {
                Text(
                    text = "Masukkan PIN Orang Tua",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Timer hanya bisa dihentikan oleh orang tua.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = stopPinInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                stopPinInput = input
                                stopPinError = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PIN") },
                        singleLine = true,
                        isError = stopPinError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = if (showStopPin) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showStopPin = !showStopPin }) {
                                Icon(
                                    imageVector = if (showStopPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showStopPin) "Sembunyikan PIN" else "Tampilkan PIN",
                                    tint = Color.LightGray
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color(0xFF555555),
                            errorBorderColor = Color.Red
                        )
                    )
                    if (stopPinError) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "PIN salah.",
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentPin = prefs.getString(TimerService.KEY_PIN, "1234") ?: "1234"
                        if (stopPinInput == currentPin) {
                            val intent = Intent(context, TimerService::class.java).apply {
                                action = TimerService.ACTION_STOP
                            }
                            context.startService(intent)
                            isTimerActive = false
                            showStopPinDialog = false
                            stopPinInput = ""
                            stopPinError = false
                            showStopPin = false
                        } else {
                            stopPinError = true
                            stopPinInput = ""
                        }
                    },
                    enabled = stopPinInput.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Hentikan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStopPinDialog = false
                        stopPinInput = ""
                        stopPinError = false
                        showStopPin = false
                    }
                ) {
                    Text("Batal")
                }
            },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Lock Logo",
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Simulasi Baterai Habis",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Solusi Pintar Batasi Screen Time Tanpa Anak Menangis",
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // If Timer is Active, Show Live Glowing Card
        if (isTimerActive) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1000)) // Crimson glow
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SIMULASI BERJALAN",
                        color = Color(0xFFFF3D00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val mins = secondsRemaining / 60
                    val secs = secondsRemaining % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "HP anak akan 'mati otomatis' saat hitungan mundur selesai.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showStopPinDialog = true
                            stopPinInput = ""
                            stopPinError = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Batalkan")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Batalkan & Hentikan")
                    }
                }
            }
        }

        // Section 1: Permissions Check (Mandatory for functioning overlay window)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Langkah 1: Izin Aplikasi (Wajib)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Overlay Permission
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasOverlayPermission) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = "Status",
                        tint = if (hasOverlayPermission) Color.Green else Color(0xFFFFCC00),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tampil Di Atas Aplikasi Lain",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dibutuhkan agar bisa memotong layar game anak saat waktu habis.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    if (!hasOverlayPermission) {
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Izinkan", fontSize = 12.sp)
                        }
                    } else {
                        Text("Aktif", color = Color.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Notification Permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasNotificationPermission) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = "Status",
                            tint = if (hasNotificationPermission) Color.Green else Color(0xFFFFCC00),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Izin Notifikasi",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dibutuhkan untuk menjaga penghitung waktu berjalan stabil di latar belakang.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        if (!hasNotificationPermission) {
                            Button(
                                onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Izinkan", fontSize = 12.sp)
                            }
                        } else {
                            Text("Aktif", color = Color.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(15.dp))

        // Section 2: Durasi Bermain (Play Duration Settings)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Langkah 2: Atur Waktu Bermain Anak",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Presets
                Text(
                    text = "Pilihan Cepat (Preset):",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        Triple("10 Dtk", "0", "10"),
                        Triple("30 Dtk", "0", "30"),
                        Triple("1 Mnt", "1", "0"),
                        Triple("5 Mnt", "5", "0"),
                        Triple("15 Mnt", "15", "0"),
                        Triple("30 Mnt", "30", "0")
                    )
                    presets.forEach { (label, minVal, secVal) ->
                        val isSelected = selectedMinutesStr == minVal && selectedSecondsStr == secVal
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Color(0xFFFF9800) else Color(0xFF2D2D2D)
                                )
                                .clickable {
                                    selectedMinutesStr = minVal
                                    selectedSecondsStr = secVal
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Custom Input Fields (Menit & Detik)
                Text(
                    text = "Sesuaikan Durasi Waktu:",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Minutes input field
                    OutlinedTextField(
                        value = selectedMinutesStr,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                selectedMinutesStr = input
                            }
                        },
                        label = { Text("Menit") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("0") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color(0xFF444444)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Seconds input field
                    OutlinedTextField(
                        value = selectedSecondsStr,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                selectedSecondsStr = input
                            }
                        },
                        label = { Text("Detik") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("0") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color(0xFF444444)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(15.dp))

        // Section 3: Layar Simulasi Mode (Simulation Effect Style Selection)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Langkah 3: Pilih Efek Mati HP",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                val modes = listOf(
                    Triple(0, "Klasik: Baterai 0% Flashing", "Baterai merah tipis kedip-kedip lalu layar mati hitam total (sangat realistis)."),
                    Triple(1, "Seketika Mati (Blackout)", "Layar langsung blackout total seakan HP kehabisan baterai tanpa peringatan."),
                    Triple(2, "Eror Sistem (Glitch)", "Mengedipkan tulisan log eror sistem warna hijau cerah sebentar lalu mati total.")
                )

                modes.forEach { (modeIndex, title, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedMode = modeIndex
                                prefs.edit().putInt(TimerService.KEY_MODE, modeIndex).apply()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selectedMode == modeIndex,
                            onClick = {
                                selectedMode = modeIndex
                                prefs.edit().putInt(TimerService.KEY_MODE, modeIndex).apply()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF9800))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(desc, color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                    if (modeIndex < 2) {
                        Divider(color = Color(0xFF2E2E2E), modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(15.dp))

        // Section 4: Parent PIN configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = "PIN", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Atur PIN Keamanan Orang Tua",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isTimerActive) {
                        "PIN tidak bisa diubah saat timer berjalan."
                    } else {
                        "Ingat PIN ini untuk membuka blokir layar HP Anda saat layar menggelap dan menghentikan timer aktif."
                    },
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        if (!isTimerActive && input.all { it.isDigit() } && input.length <= 4) {
                            pin = input
                            prefs.edit().putString(TimerService.KEY_PIN, input).apply()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTimerActive,
                    label = { Text("4-Digit PIN") },
                    placeholder = { Text("Masukkan 4 digit...") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (showParentPin) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showParentPin = !showParentPin }) {
                            Icon(
                                imageVector = if (showParentPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showParentPin) "Sembunyikan PIN" else "Tampilkan PIN",
                                tint = if (isTimerActive) Color.Gray else Color.LightGray
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.Gray,
                        focusedBorderColor = Color(0xFFFF9800),
                        unfocusedBorderColor = Color(0xFF444444),
                        disabledBorderColor = Color(0xFF333333),
                        disabledLabelColor = Color.Gray,
                        disabledPlaceholderColor = Color.DarkGray
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Guide Instructions (FAQ / Cara Membuka)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🚨 CARA INGIN MEMBUKA LAYAR:",
                    color = Color(0xFFFFCC00),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Ketika waktu habis, layar akan mati hitam total mati suri (anak mengira baterai habis).\n" +
                           "2. Untuk menyalakan kembali, ketuk POJOK KIRI ATAS layar sebanyak 7 KALI berturut-turut.\n" +
                           "3. Pada ketukan ke-6, layar akan memunculkan petunjuk jumlah ketukan yang kurang.\n" +
                           "4. Papan PIN keypad rahasia akan muncul setelah ketukan ke-7.\n" +
                           "5. Masukkan PIN '${pin}' Anda untuk kembali memakai HP secara normal.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Start Button
        Button(
            onClick = {
                val mins = selectedMinutesStr.toLongOrNull() ?: 0L
                val secs = selectedSecondsStr.toLongOrNull() ?: 0L
                val durationSeconds = (mins * 60) + secs
                if (durationSeconds <= 0) return@Button
                
                // Fire intent to start background TimerService
                val intent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                    putExtra(TimerService.EXTRA_DURATION_SECONDS, durationSeconds)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                
                isTimerActive = true
            },
            enabled = !isTimerActive && hasOverlayPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF9800),
                disabledContainerColor = Color(0xFF2A2A2A)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Mulai",
                tint = if (!isTimerActive && hasOverlayPermission) Color.Black else Color.Gray
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Mulai Timer Bermain Anak",
                color = if (!isTimerActive && hasOverlayPermission) Color.Black else Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (!hasOverlayPermission) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "*Izinkan hak akses di atas terlebih dahulu untuk memulai timer",
                color = Color(0xFFFF5252),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Divider(color = Color(0xFF2E2E2E), thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            Text(
                text = "Created by",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Aqil Aziz",
                color = Color(0xFFFF9800),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Guru IT MAM 1 Paciran",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
