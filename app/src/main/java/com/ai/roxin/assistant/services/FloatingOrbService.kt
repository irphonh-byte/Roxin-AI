package com.ai.roxin.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.ai.roxin.assistant.R
import com.ai.roxin.assistant.agent.RoxinAgentEngine
import com.ai.roxin.assistant.voice.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatingOrbService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var voiceEngine: VoiceEngine? = null
    private var agentEngine: RoxinAgentEngine? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()

        val prefs = getSharedPreferences("RoxinPrefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        agentEngine = RoxinAgentEngine(this, apiKey)

        voiceEngine = VoiceEngine(
            this,
            onSpeechRecognized = { text ->
                updateStatus("روکسن سوچ رہا ہے...")
                scope.launch {
                    val reply = agentEngine?.processUserSpeech(text) ?: "کوئی جواب موصول نہیں ہوا۔"
                    updateStatus("بول رہا ہے...")
                    voiceEngine?.speak(reply)
                }
            },
            onStateChange = { st -> updateStatus(st) }
        )

        setupFloatingOrb()
    }

    private fun setupFloatingOrb() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 250
        }

        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_orb, null)

        val orbIcon = floatingView?.findViewById<ImageView>(R.id.orbIcon)
        orbIcon?.setOnClickListener {
            voiceEngine?.startListening()
        }

        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(floatingView, params)
    }

    private fun updateStatus(status: String) {
        floatingView?.findViewById<TextView>(R.id.orbStatus)?.text = status
    }

    private fun startForegroundService() {
        val channelId = "RoxinOrbChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Roxin AI Active",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Roxin AI اسسٹنٹ فعال ہے")
            .setContentText("روکسن آپ کے فون کے تمام کام کرنے کے لیے تیار ہے۔")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(2001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceEngine?.destroy()
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
        }
    }
}
