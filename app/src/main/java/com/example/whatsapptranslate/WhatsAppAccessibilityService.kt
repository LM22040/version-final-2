package com.example.whatsapptranslate

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import android.view.animation.AnimationUtils
import com.google.mlkit.nl.languageid.LanguageIdentification

class WhatsAppAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatingBubble: View? = null
    private var isTranslating = false
    private lateinit var originalText: String
    private lateinit var showBubbleReceiver: BroadcastReceiver

    private val client = OkHttpClient()
    private val apiUrl = "https://api-inference.huggingface.co/models/Helsinki-NLP/opus-mt-en-es"
    private val apiKey = "TU_API_KEY_AQUI" // Reemplaza esto con tu API key de Hugging Face

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    override fun onCreate() {
        super.onCreate()
        registerShowBubbleReceiver()
    }

    private fun registerShowBubbleReceiver() {
        showBubbleReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.whatsapptranslate.SHOW_FLOATING_BUBBLE") {
                    if (Settings.canDrawOverlays(context)) {
                        showFloatingBubble()
                    } else {
                        Toast.makeText(context, "Permiso de ventana flotante no habilitado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        registerReceiver(showBubbleReceiver, IntentFilter("com.example.whatsapptranslate.SHOW_FLOATING_BUBBLE"))
    }

    override fun onServiceConnected() {
        if (floatingBubble == null) {
            createFloatingBubble()
        }
    }

    private fun createFloatingBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingBubble = inflater.inflate(R.layout.floating_bubble_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = 0
        params.y = 100

        floatingBubble?.findViewById<ImageButton>(R.id.floating_bubble_button)?.setOnClickListener {
            toggleTranslation()
        }

        implementBubbleDrag(floatingBubble, params)

        windowManager?.addView(floatingBubble, params)
        floatingBubble?.visibility = View.GONE
    }

    private fun toggleTranslation() {
        isTranslating = !isTranslating
        val bubbleButton = floatingBubble?.findViewById<ImageButton>(R.id.floating_bubble_button)

        if (isTranslating) {
            translateScreen()
            bubbleButton?.setImageResource(R.drawable.ic_translate_active)
            bubbleButton?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_up))
        } else {
            restoreOriginalText()
            bubbleButton?.setImageResource(R.drawable.ic_translate)
            bubbleButton?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_down))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Implementación del método obligatorio, no debe haber duplicados
        if (event.packageName == "com.whatsapp") {
            showFloatingBubble()
        } else {
            hideFloatingBubble()
        }
    }

    private fun showFloatingBubble() {
        if (floatingBubble != null && windowManager != null) {
            floatingBubble?.visibility = View.VISIBLE
            val animation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
            floatingBubble?.startAnimation(animation)
            Toast.makeText(this, "Burbuja flotante activada", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No se pudo activar la burbuja flotante", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideFloatingBubble() {
        floatingBubble?.visibility = View.GONE
    }

    private fun implementBubbleDrag(view: View?, params: WindowManager.LayoutParams) {
        view?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingBubble, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun translateScreen() {
        val rootNode = rootInActiveWindow ?: return
        val screenText = extractTextFromNode(rootNode)
        if (screenText.isNotEmpty()) {
            detectAndTranslateText(screenText)
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        if (node.text != null) {
            sb.append(node.text)
            sb.append(" ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                sb.append(extractTextFromNode(child))
            }
        }
        return sb.toString().trim()
    }

    private fun detectAndTranslateText(text: String) {
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (languageCode != "es" && languageCode != "und") {
                    // Si el idioma no es español y no es indeterminado, tradúcelo al español
                    CoroutineScope(Dispatchers.IO).launch {
                        val translatedText = translateText(text)
                        CoroutineScope(Dispatchers.Main).launch {
                            showTranslationDialog(text, translatedText)
                        }
                    }
                }
            }
            .addOnFailureListener {
                // Manejar el error de detección de idioma
                Toast.makeText(this, "Error en la detección del idioma", Toast.LENGTH_SHORT).show()
            }
    }

    private fun translateText(text: String): String {
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), "{\"inputs\": \"$text\"}")
        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            val jsonArray = JSONArray(responseBody)
            jsonArray.getJSONObject(0).getString("translation_text")
        } catch (e: Exception) {
            e.printStackTrace()
            "Error en la traducción: ${e.message}"
        }
    }

    private fun restoreOriginalText() {
        showTranslationDialog(originalText, originalText)
    }

    override fun onInterrupt() {
        // No es necesario implementar nada aquí por ahora
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingBubble?.let { windowManager?.removeView(it) }
        unregisterReceiver(showBubbleReceiver)
    }

    private fun showTranslationDialog(originalText: String, translatedText: String) {
        val inflater = LayoutInflater.from(this)
        val translationView = inflater.inflate(R.layout.dialog_translation, null)

        // Asignar el texto original y traducido
        val originalTextView = translationView.findViewById<TextView>(R.id.tv_original_message)
        val translatedTextView = translationView.findViewById<TextView>(R.id.tv_translated_message)
        val responseEditText = translationView.findViewById<EditText>(R.id.et_response)
        val sendButton = translationView.findViewById<Button>(R.id.btn_send)

        originalTextView.text = originalText
        translatedTextView.text = translatedText

        // Configurar WindowManager para mostrar la vista flotante
        val dialogParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        dialogParams.gravity = Gravity.CENTER

        // Añadir la vista flotante al WindowManager
        windowManager?.addView(translationView, dialogParams)

        // Acción del botón enviar
        sendButton.setOnClickListener {
            val responseText = responseEditText.text.toString()
            Toast.makeText(this, "Respuesta enviada: $responseText", Toast.LENGTH_SHORT).show()
            // Aquí podrías realizar alguna acción adicional, como enviar el texto a otra aplicación o API

            // Remover la vista flotante
            windowManager?.removeView(translationView)
        }

        // Remover la vista flotante al tocar fuera de ella
        translationView.setOnTouchListener { _, _ ->
            windowManager?.removeView(translationView)
            true
        }
    }
}