package com.example.whatsapptranslate

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ActivationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activation)

        findViewById<Button>(R.id.btn_open_whatsapp).setOnClickListener {
            openWhatsAppOrPlayStore()
        }
    }

    private fun openWhatsAppOrPlayStore() {
        val whatsappPackageName = "com.whatsapp"
        val intent = packageManager.getLaunchIntentForPackage(whatsappPackageName)

        if (intent != null) {
            // WhatsApp está instalado, lo abrimos
            startActivity(intent)
            // Aquí puedes añadir el código para mostrar la burbuja flotante
            Toast.makeText(this, "Abriendo WhatsApp", Toast.LENGTH_SHORT).show()
        } else {
            // WhatsApp no está instalado, redirigimos a la Play Store
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$whatsappPackageName")))
            } catch (e: android.content.ActivityNotFoundException) {
                // Si la Play Store no está instalada, abrimos en el navegador
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$whatsappPackageName")))
            }
            Toast.makeText(this, "WhatsApp no está instalado. Redirigiendo a la Play Store.", Toast.LENGTH_LONG).show()
        }
    }
}
