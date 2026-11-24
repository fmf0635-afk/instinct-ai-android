package com.instinctai.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val prefs by lazy { getSharedPreferences("instinct", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var speech: TextToSpeech
    private lateinit var status: TextView
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var avatar: TextView
    private var listening = false
    private var female = false
    private val navy = 0xFF0D1422.toInt()
    private val panel = 0xFF182438.toInt()
    private val cyan = 0xFF70D8EF.toInt()
    private val white = 0xFFF4F7FC.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speech = TextToSpeech(this, this)
        female = prefs.getBoolean("female", false)
        showHome()
    }

    private fun baseLayout(title: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 18)
            setBackgroundColor(navy)
        }
        val header = TextView(this).apply {
            text = "✦  INSTINCT AI"
            textSize = 22f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 12)
        }
        val heading = TextView(this).apply {
            text = title
            textSize = 24f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 18)
        }
        root.addView(header)
        root.addView(heading)
        return root
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply { text = label; setOnClickListener { action() } }

    private fun showHome() {
        val root = baseLayout("مساعدك الشخصي الذكي")
        val avatar = AvatarView(this, female).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 240)
        }
        root.addView(avatar)
        val greeting = TextView(this).apply {
            text = "أنا Instinct AI\nاتكلم معايا أو اكتب طلبك."
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(white)
            setPadding(0, 8, 0, 24)
        }
        root.addView(greeting)
        root.addView(button("ابدأ المحادثة", ::showChat))
        root.addView(button("🎙 التحدث بالصوت", ::startListening))
        root.addView(button("📱 فتح تطبيق", ::showApps))
        root.addView(button("⚙ الإعدادات والشخصية", ::showSettings))
        root.addView(button("🧠 الذاكرة والمحادثات", ::showMemory))
        status = TextView(this).apply {
            text = "جاهز للعمل"
            setTextColor(0xFF9FB2C9.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        root.addView(status)
        setContentView(root)
    }

    private fun showChat() {
        val root = baseLayout("المحادثة")
        val scroll = ScrollView(this)
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6, 8, 6, 8)
        }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "اكتب رسالتك هنا..."
            setTextColor(white); setHintTextColor(0xFF9FB2C9.toInt())
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setBackgroundColor(panel)
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("إرسال") { sendMessage() })
        root.addView(row)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("🎙") { startListening() }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button("إيقاف الصوت") { speech.stop() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)
        root.addView(button("رجوع للرئيسية") { showHome() })
        addMessage("assistant", "أهلًا! أنا معاك. اكتب سؤالك أو اضغط الميكروفون.")
        setContentView(root)
    }

    private fun addMessage(role: String, text: String) {
        val bubble = TextView(this).apply {
            this.text = (if (role == "user") "أنت: " else "Instinct: ") + text
            textSize = 16f
            setTextColor(white)
            setBackgroundColor(if (role == "user") 0xFF24445C.toInt() else panel)
            setPadding(14, 12, 14, 12)
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 5, 0, 5) }
        messages.addView(bubble, lp)
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        addMessage("user", text)
        val local = handleLocalCommand(text)
        if (local != null) {
            addMessage("assistant", local)
            speak(local)
            saveTurn("user", text); saveTurn("assistant", local)
            return
        }
        addMessage("assistant", "لحظة، بفكر في الرد...")
        val last = messages.getChildAt(messages.childCount - 1)
        executor.execute {
            val answer = askBackend(text)
            runOnUiThread {
                if (last != null) messages.removeView(last)
                addMessage("assistant", answer)
                speak(answer)
                saveTurn("user", text); saveTurn("assistant", answer)
            }
        }
    }

    private fun askBackend(prompt: String): String {
        val endpoint = prefs.getString("endpoint", "")?.trim().orEmpty()
        val token = prefs.getString("token", "")?.trim().orEmpty()
        if (endpoint.isBlank()) return "المحادثة الذكية تحتاج إعداد خادم آمن. افتح الإعدادات وأدخل رابط خادمك HTTPS ورمز الدخول. تقدر تستخدم أوامر فتح التطبيقات والصوت حاليًا."
        return try {
            val url = URL(endpoint.trimEnd('/') + "/chat")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            val payload = JSONObject().put("message", prompt).put("history", backendHistory()).toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (conn.responseCode !in 200..299) "الخادم رجّع خطأ: $body"
            else JSONObject(body).optString("reply", "لم يصل رد مفهوم من الخادم.")
        } catch (e: Exception) {
            "تعذر الاتصال بالخادم: ${e.localizedMessage ?: "تحقق من الإنترنت ورابط الخادم"}"
        }
    }

    private fun backendHistory(): JSONArray {
        val stored = try { JSONArray(prefs.getString("history", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        val result = JSONArray()
        val start = (stored.length() - 12).coerceAtLeast(0)
        for (i in start until stored.length()) {
            val item = stored.optJSONObject(i) ?: continue
            val role = item.optString("role")
            if (role == "user" || role == "assistant") {
                result.put(JSONObject().put("role", role).put("content", item.optString("text").take(4000)))
            }
        }
        return result
    }

    private fun handleLocalCommand(raw: String): String? {
        val q = raw.lowercase(Locale.ROOT)
        val app = when {
            q.contains("واتساب") || q.contains("whatsapp") -> "com.whatsapp"
            q.contains("يوتيوب") || q.contains("youtube") -> "com.google.android.youtube"
            q.contains("خرائط") || q.contains("maps") -> "com.google.android.apps.maps"
            q.contains("كاميرا") || q.contains("camera") -> "camera"
            q.contains("إعدادات") || q.contains("settings") -> "settings"
            q.contains("متصفح") || q.contains("chrome") || q.contains("browser") -> "com.android.chrome"
            else -> null
        }
        if (app != null) {
            launchApp(app)
            return "حاضر، بحاول أفتح التطبيق المطلوب."
        }
        if (q.contains("الوقت") || q.contains("الساعة")) {
            return "الوقت الآن: " + java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(java.util.Date())
        }
        return null
    }

    private fun showApps() {
        val root = baseLayout("التحكم في التطبيقات")
        root.addView(TextView(this).apply {
            text = "اختار التطبيق لفتحه. الأوامر الصوتية مدعومة لبعض التطبيقات."
            setTextColor(white); textSize = 15f
        })
        val apps = listOf(
            "واتساب" to "com.whatsapp", "YouTube" to "com.google.android.youtube",
            "Google Maps" to "com.google.android.apps.maps", "Chrome" to "com.android.chrome",
            "الكاميرا" to "camera", "إعدادات الهاتف" to "settings"
        )
        apps.forEach { (name, id) -> root.addView(button(name) { launchApp(id) }) }
        root.addView(button("رجوع") { showHome() })
        setContentView(root)
    }

    private fun launchApp(id: String) {
        try {
            val intent = when (id) {
                "camera" -> Intent("android.media.action.IMAGE_CAPTURE")
                "settings" -> Intent(android.provider.Settings.ACTION_SETTINGS)
                else -> packageManager.getLaunchIntentForPackage(id)
            }
            if (intent == null) Toast.makeText(this, "التطبيق غير مثبت", Toast.LENGTH_SHORT).show()
            else startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر فتح التطبيق", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "التعرف على الصوت غير متاح على هذا الجهاز", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "اتكلم مع Instinct AI")
        }
        try {
            startActivityForResult(intent, 42)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر تشغيل الميكروفون", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated by Android but retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (result.isNotBlank()) {
                if (::input.isInitialized) { input.setText(result); sendMessage() }
                else { showChat(); input.setText(result); sendMessage() }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else if (requestCode == 41) {
            Toast.makeText(this, "لازم تسمح باستخدام الميكروفون لتفعيل الصوت", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSettings() {
        val root = baseLayout("الإعدادات والشخصية")
        root.addView(TextView(this).apply {
            text = "شكل الشخصية"
            setTextColor(white); textSize = 16f
        })
        root.addView(button(if (female) "الشخصية الحالية: بنت — اضغط للتغيير" else "الشخصية الحالية: ولد — اضغط للتغيير") {
            female = !female
            prefs.edit().putBoolean("female", female).apply()
            showSettings()
        })
        val endpoint = EditText(this).apply {
            hint = "رابط الخادم HTTPS مثل https://example.com"
            setText(prefs.getString("endpoint", ""))
            setTextColor(white); setHintTextColor(0xFF9FB2C9.toInt())
        }
        root.addView(endpoint)
        val token = EditText(this).apply {
            hint = "رمز دخول الخادم (اختياري حسب إعداد الخادم)"
            setText(prefs.getString("token", ""))
            setTextColor(white); setHintTextColor(0xFF9FB2C9.toInt())
        }
        root.addView(token)
        root.addView(button("حفظ الإعدادات") {
            val value = endpoint.text.toString().trim()
            if (value.isNotBlank() && !value.startsWith("https://") && !value.startsWith("http://10.0.2.2")) {
                Toast.makeText(this, "استخدم رابط HTTPS آمن", Toast.LENGTH_LONG).show()
            } else {
                prefs.edit().putString("endpoint", value).putString("token", token.text.toString().trim()).apply()
                Toast.makeText(this, "تم حفظ الإعدادات", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(button("رجوع") { showHome() })
        setContentView(root)
    }

    private fun showMemory() {
        val root = baseLayout("الذاكرة المحلية")
        val history = prefs.getString("history", "[]") ?: "[]"
        val list = try { JSONArray(history) } catch (_: Exception) { JSONArray() }
        if (list.length() == 0) root.addView(TextView(this).apply {
            text = "لسه مفيش محادثات محفوظة."; setTextColor(white); gravity = Gravity.CENTER
        })
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            root.addView(TextView(this).apply {
                text = item.optString("role") + ": " + item.optString("text")
                setTextColor(white); setPadding(0, 6, 0, 6)
            })
        }
        root.addView(button("مسح الذاكرة المحلية") {
            prefs.edit().putString("history", "[]").apply()
            Toast.makeText(this, "تم مسح المحفوظ محليًا", Toast.LENGTH_SHORT).show()
            showMemory()
        })
        root.addView(button("رجوع") { showHome() })
        setContentView(root)
    }

    private fun saveTurn(role: String, text: String) {
        try {
            val arr = JSONArray(prefs.getString("history", "[]") ?: "[]")
            arr.put(JSONObject().put("role", role).put("text", text).put("time", System.currentTimeMillis()))
            while (arr.length() > 100) arr.remove(0)
            prefs.edit().putString("history", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun speak(text: String) {
        if (::speech.isInitialized) speech.speak(text.take(2500), TextToSpeech.QUEUE_FLUSH, null, "instinct-reply")
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) speech.language = Locale("ar")
    }

    override fun onDestroy() {
        if (::speech.isInitialized) speech.shutdown()
        executor.shutdown()
        super.onDestroy()
    }
}
