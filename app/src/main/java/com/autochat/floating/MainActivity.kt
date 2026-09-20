package com.autochat.floating

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {

    private val REQUEST_OVERLAY_CODE = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnGrantOverlay = findViewById<Button>(R.id.btnGrantOverlay)
        val btnGrantAccessibility = findViewById<Button>(R.id.btnGrantAccessibility)
        val btnLaunch = findViewById<Button>(R.id.btnLaunchFloating)

        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, REQUEST_OVERLAY_CODE)
                } else {
                    Toast.makeText(this, "Izin overlay sudah aktif", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnLaunch.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Aktifkan izin overlay terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Jalankan widget mengambang
            val serviceIntent = Intent(this, FloatingWidgetService::class.java)
            startService(serviceIntent)
            finish()
        }
    }
}
