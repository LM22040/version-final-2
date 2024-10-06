package com.example.whatsapptranslate

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Environment

class PermissionSettingsActivity : AppCompatActivity() {

    private lateinit var switchMicrophone: Switch
    private lateinit var switchStorage: Switch
    private lateinit var switchNotification: Switch
    private lateinit var btnAcceptPermissions: Button

    private val MICROPHONE_PERMISSION_CODE = 1
    private val STORAGE_PERMISSION_CODE = 2
    private val NOTIFICATION_PERMISSION_CODE = 3
    private val MANAGE_EXTERNAL_STORAGE_CODE = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_settings)

        switchMicrophone = findViewById(R.id.switch_microphone)
        switchStorage = findViewById(R.id.switch_storage)
        switchNotification = findViewById(R.id.switch_notification)
        btnAcceptPermissions = findViewById(R.id.btn_accept_permissions)

        switchMicrophone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requestMicrophonePermission()
        }

        switchStorage.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requestStoragePermission()
        }

        switchNotification.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requestNotificationPermission()
        }

        btnAcceptPermissions.setOnClickListener {
            if (allPermissionsGranted()) {
                startActivity(Intent(this, ActivationActivity::class.java))
            } else {
                Toast.makeText(this, "Por favor, concede todos los permisos necesarios", Toast.LENGTH_SHORT).show()
            }
        }

        updateSwitchStates()
    }

    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION_CODE)
        } else {
            switchMicrophone.isChecked = true
        }
    }

    private fun requestStoragePermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11 (API 30) y superior
                if (!Environment.isExternalStorageManager()) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_CODE)
                } else {
                    switchStorage.isChecked = true
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6.0 (API 23) a Android 10 (API 29)
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
                } else {
                    switchStorage.isChecked = true
                }
            }
            else -> {
                // Versiones anteriores a Android 6.0
                switchStorage.isChecked = true
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_CODE)
            } else {
                switchNotification.isChecked = true
            }
        } else {
            // Para versiones anteriores a Android 13, no se necesita un permiso especial
            switchNotification.isChecked = true
        }
    }

    private fun allPermissionsGranted(): Boolean {
        val microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return microphoneGranted && storageGranted && notificationGranted
    }

    private fun updateSwitchStates() {
        switchMicrophone.isChecked = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        switchStorage.isChecked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        switchNotification.isChecked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MANAGE_EXTERNAL_STORAGE_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    switchStorage.isChecked = true
                } else {
                    switchStorage.isChecked = false
                    Toast.makeText(this, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MICROPHONE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    switchMicrophone.isChecked = true
                } else {
                    switchMicrophone.isChecked = false
                    Toast.makeText(this, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show()
                }
            }
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    switchStorage.isChecked = true
                } else {
                    switchStorage.isChecked = false
                    Toast.makeText(this, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
                }
            }
            NOTIFICATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    switchNotification.isChecked = true
                } else {
                    switchNotification.isChecked = false
                    Toast.makeText(this, "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show()
                }
            }
        }
        updateSwitchStates()
    }
}