package com.ai.roxin.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ai.roxin.assistant.services.FloatingOrbService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val btnSaveKey = findViewById<Button>(R.id.btnSaveKey)
        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        val btnOverlay = findViewById<Button>(R.id.btnOverlay)
        val btnStartRoxin = findViewById<Button>(R.id.btnStartRoxin)

        val prefs = getSharedPreferences("RoxinPrefs", Context.MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("gemini_api_key", ""))

        btnSaveKey.setOnClickListener {
            val key = apiKeyInput.text.toString().trim()
            if (key.isNotEmpty()) {
                prefs.edit().putString("gemini_api_key", key).apply()
                Toast.makeText(this, "API Key محفوظ ہو گئی!", Toast.LENGTH_SHORT).show()
            }
        }

        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Roxin AI منتخب کر کے آن کریں", Toast.LENGTH_LONG).show()
        }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay اجازت پہلے سے موجود ہے", Toast.LENGTH_SHORT).show()
            }
        }

        btnStartRoxin.setOnClickListener {
            val serviceIntent = Intent(this, FloatingOrbService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Roxin AI اسسٹنٹ شروع ہو گیا!", Toast.LENGTH_SHORT).show()
        }
    }
}
