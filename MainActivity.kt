package com.instinctai.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.ActivityCompat
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private val prefs by lazy { getSharedPreferences("instinct", MODE_PRIVATE) }
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var pendingVoiceLang: String = "ar"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        textToSpeech = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) textToSpeech?.language = Locale("ar", "EG") }
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest) =
                assetLoader.shouldInterceptRequest(request.url)
        }
        webView.addJavascriptInterface(AndroidBridge(), "AndroidInstinct")
        setContentView(webView)
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    inner class AndroidBridge {
        @JavascriptInterface fun isAndroid(): Boolean = true
        @JavascriptInterface fun getApiBaseUrl(): String = prefs.getString("api_base_url", "") ?: ""

        @JavascriptInterface fun getAccessToken(): String = prefs.getString("access_token", "") ?: ""

        @JavascriptInterface fun speakText(text: String, language: String) {
            runOnUiThread {
                val tts = textToSpeech ?: return@runOnUiThread
                val locale = if (language == "ar") Locale("ar", "EG") else Locale.US
                val result = tts.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.language = Locale.getDefault()
                }
                tts.setSpeechRate(1.0f)
                tts.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "instinct-reply")
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { runOnUiThread { webView.evaluateJavascript("window.onInstinctTtsDone && window.onInstinctTtsDone()", null) } }
                    @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { runOnUiThread { webView.evaluateJavascript("window.onInstinctTtsDone && window.onInstinctTtsDone()", null) } }
                })
            }
        }

        @JavascriptInterface fun startVoiceInput(language: String) {
            pendingVoiceLang = language
            runOnUiThread {
                if (!SpeechRecognizer.isRecognitionAvailable(this@MainActivity)) {
                    webView.evaluateJavascript("window.onInstinctSpeechError && window.onInstinctSpeechError('Speech recognition is unavailable on this device')", null)
                    return@runOnUiThread
                }
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), 702)
                    Toast.makeText(this@MainActivity, "Allow microphone access, then tap Speak again", Toast.LENGTH_LONG).show()
                    webView.evaluateJavascript("window.onInstinctSpeechError && window.onInstinctSpeechError('Microphone permission is required')", null)
                    return@runOnUiThread
                }
                try {
                    speechRecognizer?.destroy()
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@MainActivity).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onError(error: Int) { webView.evaluateJavascript("window.onInstinctSpeechError && window.onInstinctSpeechError('Could not recognize speech. Please try again.')", null) }
                            override fun onResults(results: Bundle?) {
                                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                                val safe = org.json.JSONObject.quote(spoken)
                                webView.evaluateJavascript("window.onInstinctSpeechResult && window.onInstinctSpeechResult($safe)", null)
                            }
                            override fun onPartialResults(partialResults: Bundle?) {}
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "ar") "ar-EG" else "en-US")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, if (language == "ar") "ar-EG" else "en-US")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    }
                    speechRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    val safe = org.json.JSONObject.quote(e.message ?: "Voice input failed")
                    webView.evaluateJavascript("window.onInstinctSpeechError && window.onInstinctSpeechError($safe)", null)
                }
            }
        }

        @JavascriptInterface fun configureBackend() {
            runOnUiThread {
                val layout = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(48, 8, 48, 0)
                }
                val urlField = EditText(this@MainActivity).apply {
                    hint = "https://api.your-domain.com"
                    setText(getApiBaseUrl())
                    setSingleLine(true)
                }
                val tokenField = EditText(this@MainActivity).apply {
                    hint = "App access token"
                    setText(getAccessToken())
                    setSingleLine(true)
                }
                layout.addView(TextView(this@MainActivity).apply { text = "Backend HTTPS URL" })
                layout.addView(urlField)
                layout.addView(TextView(this@MainActivity).apply { text = "App access token (if configured)" })
                layout.addView(tokenField)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Connect Instinct AI server")
                    .setMessage("Enter your deployed backend address. Your AI provider key stays on the server and must never be entered here.")
                    .setView(layout)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save") { _, _ ->
                        val value = urlField.text.toString().trim().trimEnd('/')
                        val token = tokenField.text.toString().trim()
                        if (value.isNotEmpty() && value.startsWith("https://")) {
                            prefs.edit().putString("api_base_url", value).putString("access_token", token).apply()
                            Toast.makeText(this@MainActivity, "Server settings saved", Toast.LENGTH_SHORT).show()
                        } else if (value.isEmpty()) {
                            prefs.edit().remove("api_base_url").putString("access_token", token).apply()
                        } else {
                            Toast.makeText(this@MainActivity, "Use a valid HTTPS address", Toast.LENGTH_LONG).show()
                        }
                    }.show()
            }
        }

        // The bridge calls the user's own backend. Provider credentials remain server-side.
        @JavascriptInterface fun chat(messagesJson: String): String {
            val base = getApiBaseUrl()
            if (base.isBlank()) return JSONObject().put("error", "Connect your HTTPS backend in Android settings first.").toString()
            var connection: HttpURLConnection? = null
            return try {
                val messages = JSONArray(messagesJson)
                connection = (URL("$base/api/chat").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val token = getAccessToken()
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                }
                connection.outputStream.use { it.write(JSONObject().put("messages", messages).toString().toByteArray(Charsets.UTF_8)) }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                body
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Network error").toString()
            } finally { connection?.disconnect() }
        }

        @JavascriptInterface fun showInstalledApps() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = packageManager.queryIntentActivities(intent, 0)
                    .filter { it.activityInfo.packageName != packageName }
                    .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
                val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
                val entries = apps.toTypedArray()
                if (apps.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No launchable apps found", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Choose an installed app")
                    .setItems(labels) { _, which ->
                        val selected = entries[which]
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Open ${labels[which]}?")
                            .setMessage("Instinct AI will open this app. It will not read or change its contents.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Open") { _, _ ->
                                val launch = packageManager.getLaunchIntentForPackage(selected.activityInfo.packageName)
                                if (launch != null) startActivity(launch)
                            }.show()
                    }.setNegativeButton("Close", null).show()
            }
        }

        @JavascriptInterface fun openWebUrl(raw: String): Boolean {
            return try {
                val uri = Uri.parse(raw)
                if (uri.scheme != "https" && uri.scheme != "http") return false
                runOnUiThread { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                true
            } catch (_: Exception) { false }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
