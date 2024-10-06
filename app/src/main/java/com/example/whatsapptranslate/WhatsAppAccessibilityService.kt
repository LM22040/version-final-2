package com.example.whatsapptranslate

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

class WhatsAppAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatingBubble: View? = null
    private var isTranslating = false
    private lateinit var showBubbleReceiver: BroadcastReceiver

    private val client = OkHttpClient()
    private val apiUrl = "https://api-inference.huggingface.co/models/Helsinki-NLP/opus-mt-en-es"
    private val apiKey = "TU_API_KEY_AQUI" // Reemplaza esto con tu API key de Hugging Face

    override fun onCreate() {
        super.onCreate()
        registerShowBubbleReceiver()
    }

    private fun registerShowBubbleReceiver() {
        showBubbleReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.whatsapptranslate.SHOW_FLOATING_BUBBLE") {
                    showFloatingBubble()
                }
            }
        }
        registerReceiver(showBubbleReceiver, IntentFilter("com.example.whatsapptranslate.SHOW_FLOATING_BUBBLE"))
    }

    override fun onServiceConnected() {
        createFloatingBubble()
    }

    private fun createFloatingBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingBubble = inflater.inflate(R.layout.floating_bubble_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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
        if (isTranslating) {
            translateScreen()
        } else {
            restoreOriginalText()
        }
    }

    private fun translateScreen() {
        val rootNode = rootInActiveWindow ?: return
        val textToTranslate = extractTextFromNode(rootNode)
        
        if (textToTranslate.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val translatedText = translateText(textToTranslate)
                CoroutineScope(Dispatchers.Main).launch {
                    // Aquí deberías actualizar la UI con el texto traducido
                    Toast.makeText(this@WhatsAppAccessibilityService, "Texto traducido: $translatedText", Toast.LENGTH_LONG).show()
                }
            }
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

    private fun translateText(text: String): String {
        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), "{\"inputs\": \"$text\"}")
        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/Helsinki-NLP/opus-mt-en-es")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            val jsonArray = JSONArray(responseBody)
            return jsonArray.getJSONObject(0).getString("translation_text")
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error en la traducción: ${e.message}"
        }
    }

    private fun restoreOriginalText() {
        // Implementa la lógica para restaurar el texto original
        Toast.makeText(this, "Mostrando texto original", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == "com.whatsapp") {
            showFloatingBubble()
        } else {
            hideFloatingBubble()
        }
    }

    private fun showFloatingBubble() {
        floatingBubble?.visibility = View.VISIBLE
    }

    private fun hideFloatingBubble() {
        floatingBubble?.visibility = View.GONE
    }

    private fun implementBubbleDrag(view: View?, params: WindowManager.LayoutParams) {
        view?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingBubble, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onInterrupt() {
        // No es necesario implementar nada aquí por ahora
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingBubble != null) {
            windowManager?.removeView(floatingBubble)
        }
        unregisterReceiver(showBubbleReceiver)
    }
}