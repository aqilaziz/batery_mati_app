package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale
import kotlin.math.max

class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_service_channel"
        const val NOTIFICATION_ID = 4224
        
        // Actions
        const val ACTION_START = "com.example.action.START_TIMER"
        const val ACTION_STOP = "com.example.action.STOP_TIMER"
        
        // Extras
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        
        // SharedPreferences keys
        const val PREFS_NAME = "ParentalLockPrefs"
        const val KEY_PIN = "parent_pin"
        const val KEY_MODE = "simulation_mode" // 0: low battery + black, 1: instant black, 2: glitch screen
        const val KEY_TIME_LEFT = "time_left_seconds"
        const val KEY_ACTIVE_END_TIMESTAMP = "active_end_timestamp"
        const val KEY_IS_RUNNING = "is_timer_running"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var handler: Handler? = null
    private var countdownRunnable: Runnable? = null
    
    private var timeLeftSeconds = 0L
    private var targetTimestamp = 0L
    private var isSimulationShowing = false
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        handler = Handler(Looper.getMainLooper())
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (action == ACTION_START) {
            val durationSeconds = intent.getLongExtra(EXTRA_DURATION_SECONDS, 60L)
            timeLeftSeconds = durationSeconds
            targetTimestamp = System.currentTimeMillis() + (durationSeconds * 1000)
            
            prefs.edit()
                .putLong(KEY_TIME_LEFT, timeLeftSeconds)
                .putLong(KEY_ACTIVE_END_TIMESTAMP, targetTimestamp)
                .putBoolean(KEY_IS_RUNNING, true)
                .apply()

            startForegroundServiceWithNotification()
            startTimerCountdown()
        } else if (action == ACTION_STOP) {
            stopTimerAndOverlay()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Lovera Timer"
            val descriptionText = "Menampilkan sisa waktu sebelum simulasi berjalan"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification() {
        val notification = createNotification("Menyiapkan penghitung waktu...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(content: String): Notification {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Baterai Habis - Timer Aktif")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(secondsLeft: Long) {
        val minutes = secondsLeft / 60
        val seconds = secondsLeft % 60
        val content = String.format(Locale.getDefault(), "Waktu tersisa: %02d:%02d sebelum simulasi baterai habis", minutes, seconds)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun startTimerCountdown() {
        // Cancel any existing runnables
        countdownRunnable?.let { handler?.removeCallbacks(it) }
        
        countdownRunnable = object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val isRunning = prefs.getBoolean(KEY_IS_RUNNING, false)
                if (!isRunning) return

                val currentTimestamp = System.currentTimeMillis()
                timeLeftSeconds = max(0L, (targetTimestamp - currentTimestamp) / 1000)
                
                prefs.edit().putLong(KEY_TIME_LEFT, timeLeftSeconds).apply()

                if (timeLeftSeconds <= 0) {
                    // Timer finished, launch full screen overlay blocker
                    updateNotification(0)
                    triggerFakeShutdown()
                } else {
                    updateNotification(timeLeftSeconds)
                    handler?.postDelayed(this, 1000)
                }
            }
        }
        handler?.post(countdownRunnable!!)
    }

    private fun triggerFakeShutdown() {
        // Vibrate to simulate standard phone turning off vibration
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(800)
        }

        // Show Full Screen Overlay Blocker
        handler?.post {
            showOverlayBlocker()
        }
    }

    private fun showOverlayBlocker() {
        if (isSimulationShowing) return
        isSimulationShowing = true

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedMode = prefs.getInt(KEY_MODE, 0) // 0: standard battery screen, 1: instant pitch black, 2: glitch pattern

        val layoutParamsFlags = (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_FULLSCREEN
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            layoutParamsFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Create programmatic overlay FrameLayout with focus intercept to suppress notification pull-down and system navigation
        val rootLayout = object : FrameLayout(this) {
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (!hasWindowFocus) {
                    // Force-close expanded status bars/notification panel if drag down occurs
                    try {
                        @Suppress("DEPRECATION")
                        val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                        sendBroadcast(closeIntent)
                    } catch (e: Exception) {
                        // Suppress exception
                    }

                    // Re-assert complete immersion (full screen, hide navigation / status bars)
                    handler?.postDelayed({
                        @Suppress("DEPRECATION")
                        systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            windowInsetsController?.let { controller ->
                                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                        }
                    }, 50)
                }
            }
        }.apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true

            // Intercept and consume back key press so navigation is blocked
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
                    true
                } else {
                    false
                }
            }
            
            // Hide status bar and navigation bar completely
            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.windowInsetsController?.let { controller ->
                            controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                            controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }
                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }
        }

        // Custom child Views based on simulation mode
        if (selectedMode == 0) {
            // Low battery mode
            // Center Layout containing simulated empty battery icon and shutdown loading
            val centerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }

            // Draw clean Custom Battery View programmatically
            val batteryView = ProgrammaticBatteryView(this).apply {
                layoutParams = LinearLayout.LayoutParams(240, 140).apply {
                    bottomMargin = 40
                }
            }
            centerLayout.addView(batteryView)

            // Text: Shutdown
            val textShutdown = TextView(this).apply {
                text = "Baterai Lemah\nMematikan Daya..."
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 0)
            }
            centerLayout.addView(textShutdown)
            rootLayout.addView(centerLayout)

            // Simulate shutting down: Flash empty battery 3 times then go completely black
            handler?.postDelayed({
                centerLayout.visibility = View.GONE
                // Flash off completely
                rootLayout.setBackgroundColor(Color.BLACK)
            }, 6000)

        } else if (selectedMode == 2) {
            // Glitch Screen mode
            val glitchLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.DKGRAY)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            val textGlitch = TextView(this).apply {
                text = "SYSTEM ERROR #8102\nMEMORY INTEGRITY CRITICAL\nSHUTTING DOWN SYSTEM..."
                setTextColor(Color.GREEN)
                textSize = 16f
                gravity = Gravity.CENTER
            }
            glitchLayout.addView(textGlitch)
            rootLayout.addView(glitchLayout)

            handler?.postDelayed({
                glitchLayout.visibility = View.GONE
                rootLayout.setBackgroundColor(Color.BLACK)
            }, 4000)
        } else {
            // Intentionally completely black from start (Instant screen death)
        }

        // Setup secret gesture to exit: 7 fast taps in the top-left corner triggers Parent PIN input Pad
        var clickCount = 0
        var lastClickTime = 0L

        rootLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val scale = resources.displayMetrics.density
                val limitX = 200f * scale // approx 200dp
                val limitY = 250f * scale // approx 250dp

                // Only register taps when they occur in the top-left corner
                if (event.x < limitX && event.y < limitY) {
                    val currentTime = System.currentTimeMillis()
                    // Allow up to 1200ms between taps to make 7 consecutive taps comfortable for parent
                    if (currentTime - lastClickTime < 1200) {
                        clickCount++
                        if (clickCount == 6) {
                            val remaining = 7 - clickCount
                            Toast.makeText(this@TimerService, "Kurang $remaining ketukan lagi!", Toast.LENGTH_SHORT).show()
                        } else if (clickCount >= 7) {
                            // Secret triggered! Show custom secure PIN overlay keyboard
                            showPinInputPad(rootLayout)
                            clickCount = 0
                        }
                    } else {
                        clickCount = 1
                    }
                    lastClickTime = currentTime
                } else {
                    // Reset click count if clicked outside the top-left parental corner
                    clickCount = 0
                }
            }
            true // Consume all touch events so game/system beneath gets absolutely nothing
        }

        // Add to WindowManager
        overlayView = rootLayout
        windowManager?.addView(rootLayout, params)
    }

    private fun showPinInputPad(rootLayout: FrameLayout) {
        // Remove prior PIN elements if already present
        val existingPinPad = rootLayout.findViewWithTag<View>("pin_pad_tag")
        if (existingPinPad != null) return

        val context = this
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val correctPin = prefs.getString(KEY_PIN, "1234") ?: "1234"

        // Create dark clean keypad panel
        val pinPanel = LinearLayout(context).apply {
            tag = "pin_pad_tag"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212")) // Dark Material design color
            elevation = 50f
            setPadding(60, 60, 60, 60)
            layoutParams = FrameLayout.LayoutParams(
                650,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // Header Label
        val labelHeader = TextView(context).apply {
            text = "KONTROL ORANG TUA"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        val labelSub = TextView(context).apply {
            text = "Masukkan PIN untuk membuka HP"
            setTextColor(Color.GRAY)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        pinPanel.addView(labelHeader)
        pinPanel.addView(labelSub)

        // PIN display indicator (circles or dots)
        val pinIndicatorLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        val indicators = Array(4) {
            TextView(context).apply {
                text = "○" // Empty circle
                setTextColor(Color.WHITE)
                textSize = 28f
                setPadding(15, 0, 15, 0)
            }
        }
        indicators.forEach { pinIndicatorLayout.addView(it) }
        pinPanel.addView(pinIndicatorLayout)

        var enteredPin = ""

        fun updatePinIndicators() {
            for (i in 0 until 4) {
                if (i < enteredPin.length) {
                    indicators[i].text = "●" // Filled circle
                } else {
                    indicators[i].text = "○" // Empty circle
                }
            }
        }

        // Keypad Grid Layout (3x4)
        val gridLayout = GridLayout(context).apply {
            columnCount = 3
            rowCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Input button click listener
        val digitClickListener = View.OnClickListener { v ->
            if (v is TextView) {
                val digit = v.text.toString()
                if (enteredPin.length < 4) {
                    enteredPin += digit
                    updatePinIndicators()
                    
                    if (enteredPin.length == 4) {
                        // Validate PIN
                        if (enteredPin == correctPin) {
                            Toast.makeText(context, "Akses Terbuka", Toast.LENGTH_SHORT).show()
                            stopTimerAndOverlay()
                        } else {
                            // Clear and flash red vibration
                            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(300)
                            }
                            
                            labelSub.text = "PIN SALAH! Coba lagi..."
                            labelSub.setTextColor(Color.RED)
                            enteredPin = ""
                            updatePinIndicators()
                        }
                    }
                }
            }
        }

        // Help build beautiful round buttons
        fun createKeypadButton(value: String, clickListener: View.OnClickListener): TextView {
            return TextView(context).apply {
                text = value
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                isClickable = true
                setBackgroundColor(Color.parseColor("#222222"))
                setOnClickListener(clickListener)
                setPadding(0, 30, 0, 30)
                
                // Set margins
                val params = GridLayout.LayoutParams().apply {
                    width = 150
                    height = 130
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(10, 10, 10, 10)
                }
                layoutParams = params
            }
        }

        // Add 1-9 buttons
        for (i in 1..9) {
            gridLayout.addView(createKeypadButton(i.toString(), digitClickListener))
        }

        // Back button
        val backBtn = createKeypadButton("X", View.OnClickListener {
            // Dismiss PIN keyboard panel, back to dead screen
            rootLayout.removeView(pinPanel)
        }).apply {
            setTextColor(Color.RED)
            textSize = 14f
        }
        gridLayout.addView(backBtn)

        // 0 key
        gridLayout.addView(createKeypadButton("0", digitClickListener))

        // Del button
        val delBtn = createKeypadButton("←", View.OnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.substring(0, enteredPin.length - 1)
                updatePinIndicators()
            }
        }).apply {
            setTextColor(Color.YELLOW)
        }
        gridLayout.addView(delBtn)

        pinPanel.addView(gridLayout)
        rootLayout.addView(pinPanel)

        // Automatically close PIN Pad back to pitch black if parent touches nothing for 10 seconds
        val autoCloseHandler = Handler(Looper.getMainLooper())
        val checkRunnable = object : Runnable {
            override fun run() {
                val activePinPad = rootLayout.findViewWithTag<View>("pin_pad_tag")
                if (activePinPad != null && enteredPin.isEmpty()) {
                    rootLayout.removeView(activePinPad)
                }
            }
        }
        autoCloseHandler.postDelayed(checkRunnable, 15000)
    }

    private fun stopTimerAndOverlay() {
        // Stop timer execution
        countdownRunnable?.let { handler?.removeCallbacks(it) }
        countdownRunnable = null
        
        // Update preference state
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_RUNNING, false)
            .putLong(KEY_TIME_LEFT, 0L)
            .apply()

        // Remove window manager overlay
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore if view was already removed or invalid
            }
        }
        overlayView = null
        isSimulationShowing = false

        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopTimerAndOverlay()
        super.onDestroy()
    }
}

// Inline custom battery drawing View to avoid resource bundle dependencies, making it 100% portable
class ProgrammaticBatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private var flashToggle = true
    private val handler = Handler(Looper.getMainLooper())
    private val flashRunnable = object : Runnable {
        override fun run() {
            flashToggle = !flashToggle
            invalidate()
            handler.postDelayed(this, 800)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(flashRunnable)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(flashRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Battery outer outline
        // Leave space for battery terminal cap on the right
        val capWidth = w * 0.1f
        val bodyRight = w - capWidth - 10f
        val bodyRect = RectF(10f, 10f, bodyRight, h - 10f)
        canvas.drawRoundRect(bodyRect, 15f, 15f, bodyPaint)

        // Battery Cap (Right terminal connector)
        val capHeight = h * 0.35f
        val capTop = (h - capHeight) / 2f
        val capRect = RectF(bodyRight, capTop, w - 5f, capTop + capHeight)
        canvas.drawRoundRect(capRect, 5f, 5f, capPaint)

        // Battery Indicator 0%/Critical Flashing red
        if (flashToggle) {
            val padding = 20f
            val internalWidth = bodyRight - 10f - (padding * 2)
            // Show only a tiny red slice representing 0% / empty
            val redBarWidth = internalWidth * 0.12f 
            val levelRect = RectF(
                10f + padding,
                10f + padding,
                10f + padding + redBarWidth,
                h - 10f - padding
            )
            canvas.drawRect(levelRect, levelPaint)
        }
    }
}
