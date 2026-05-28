package com.example

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class BlackOverlayActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var immersiveRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isTimerRunning()) {
            finish()
            return
        }

        configureLockscreenWindow()

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        var clickCount = 0
        var lastClickTime = 0L
        rootLayout.setOnTouchListener { _, event ->
            applyImmersiveSystemUi(rootLayout)
            if (event.action == MotionEvent.ACTION_DOWN) {
                val scale = resources.displayMetrics.density
                val limitX = 200f * scale
                val limitY = 250f * scale

                if (event.x < limitX && event.y < limitY) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < 1200) {
                        clickCount++
                        if (clickCount == 6) {
                            Toast.makeText(this, "Kurang 1 ketukan lagi!", Toast.LENGTH_SHORT).show()
                        } else if (clickCount >= 7) {
                            showPinInputPad(rootLayout)
                            clickCount = 0
                        }
                    } else {
                        clickCount = 1
                    }
                    lastClickTime = currentTime
                } else {
                    clickCount = 0
                }
            }
            true
        }

        setContentView(rootLayout)
        rootLayout.requestFocus()
        rootLayout.post {
            applyImmersiveSystemUi(rootLayout)
            rootLayout.requestFocus()
        }
        startImmersiveGuard(rootLayout)
    }

    override fun onResume() {
        super.onResume()
        if (!isTimerRunning()) {
            finish()
            return
        }
        closeSystemDialogs()
        window.decorView.post { applyImmersiveSystemUi(window.decorView) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        applyImmersiveSystemUi(window.decorView)
        if (!hasFocus) {
            closeSystemDialogs()
            handler.postDelayed({ applyImmersiveSystemUi(window.decorView) }, 30)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isBlockedHardwareKey(event.keyCode)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        immersiveRunnable?.let { handler.removeCallbacks(it) }
        immersiveRunnable = null
        super.onDestroy()
    }

    private fun configureLockscreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        window.setBackgroundDrawableResource(android.R.color.black)

        val attrs = window.attributes
        attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        attrs.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = attrs
    }

    private fun showPinInputPad(rootLayout: FrameLayout) {
        applyImmersiveSystemUi(rootLayout)
        val existingPinPad = rootLayout.findViewWithTag<View>("pin_pad_tag")
        if (existingPinPad != null) return

        val correctPin = getSharedPreferences(TimerService.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TimerService.KEY_PIN, "1234") ?: "1234"

        val pinPanel = LinearLayout(this).apply {
            tag = "pin_pad_tag"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            elevation = 50f
            setPadding(60, 60, 60, 60)
            layoutParams = FrameLayout.LayoutParams(650, LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }

        val labelHeader = TextView(this).apply {
            text = "KONTROL ORANG TUA"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        val labelSub = TextView(this).apply {
            text = "Masukkan PIN untuk membuka HP"
            setTextColor(Color.GRAY)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        pinPanel.addView(labelHeader)
        pinPanel.addView(labelSub)

        val pinIndicatorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        val indicators = Array(4) {
            TextView(this).apply {
                text = "○"
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
                indicators[i].text = if (i < enteredPin.length) "●" else "○"
            }
        }

        val gridLayout = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val digitClickListener = View.OnClickListener { v ->
            if (v is TextView && enteredPin.length < 4) {
                enteredPin += v.text.toString()
                updatePinIndicators()

                if (enteredPin.length == 4) {
                    if (enteredPin == correctPin) {
                        Toast.makeText(this, "Akses Terbuka", Toast.LENGTH_SHORT).show()
                        stopTimerService()
                        finishAndRemoveTask()
                    } else {
                        vibrateWrongPin()
                        labelSub.text = "PIN SALAH! Coba lagi..."
                        labelSub.setTextColor(Color.RED)
                        enteredPin = ""
                        updatePinIndicators()
                    }
                }
            }
        }

        fun createKeypadButton(value: String, clickListener: View.OnClickListener): TextView {
            return TextView(this).apply {
                text = value
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                isClickable = true
                setBackgroundColor(Color.parseColor("#222222"))
                setOnClickListener(clickListener)
                setPadding(0, 30, 0, 30)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 150
                    height = 130
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(10, 10, 10, 10)
                }
            }
        }

        for (i in 1..9) {
            gridLayout.addView(createKeypadButton(i.toString(), digitClickListener))
        }
        gridLayout.addView(createKeypadButton("X") { rootLayout.removeView(pinPanel) }.apply {
            setTextColor(Color.RED)
            textSize = 14f
        })
        gridLayout.addView(createKeypadButton("0", digitClickListener))
        gridLayout.addView(createKeypadButton("←") {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.substring(0, enteredPin.length - 1)
                updatePinIndicators()
            }
        }.apply { setTextColor(Color.YELLOW) })

        pinPanel.addView(gridLayout)
        rootLayout.addView(pinPanel)
    }

    private fun stopTimerService() {
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP
        }
        startService(intent)
    }

    private fun vibrateWrongPin() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }

    private fun isTimerRunning(): Boolean {
        return getSharedPreferences(TimerService.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(TimerService.KEY_IS_RUNNING, false)
    }

    private fun startImmersiveGuard(view: View) {
        immersiveRunnable?.let { handler.removeCallbacks(it) }
        immersiveRunnable = object : Runnable {
            override fun run() {
                if (!isTimerRunning()) {
                    finish()
                    return
                }
                closeSystemDialogs()
                applyImmersiveSystemUi(view)
                view.requestFocus()
                handler.postDelayed(this, 200)
            }
        }
        handler.post(immersiveRunnable!!)
    }

    private fun closeSystemDialogs() {
        try {
            @Suppress("DEPRECATION")
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        } catch (e: Exception) {
            // Some Android versions ignore or block this for third-party apps.
        }
    }

    private fun applyImmersiveSystemUi(view: View) {
        @Suppress("DEPRECATION")
        view.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.windowInsetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun isBlockedHardwareKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_FOCUS,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_SLEEP,
            KeyEvent.KEYCODE_WAKEUP,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MUTE,
            KeyEvent.KEYCODE_NOTIFICATION,
            KeyEvent.KEYCODE_BRIGHTNESS_UP,
            KeyEvent.KEYCODE_BRIGHTNESS_DOWN -> true
            else -> false
        }
    }
}
