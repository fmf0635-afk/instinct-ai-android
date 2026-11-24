package com.instinctai.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 48, 32, 48)
            setBackgroundColor(0xFF101827.toInt())
        }

        val title = TextView(this).apply {
            text = "INSTINCT AI"
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        val avatar = TextView(this).apply {
            text = "✦"
            textSize = 88f
            gravity = Gravity.CENTER
            setTextColor(0xFF73D7FF.toInt())
            contentDescription = "Instinct AI avatar placeholder"
        }
        val subtitle = TextView(this).apply {
            text = "مساعدك الذكي — النسخة التجريبية"
            textSize = 18f
            setTextColor(0xFFD6E2F0.toInt())
            gravity = Gravity.CENTER
        }
        val status = TextView(this).apply {
            text = "واجهة البداية جاهزة. سيتم توصيل المحادثة والصوت والتحكم بالتطبيقات في الخطوات التالية."
            textSize = 15f
            setTextColor(0xFFB8C7D9.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        val action = Button(this).apply {
            text = "ابدأ المحادثة"
            isEnabled = false
            contentDescription = "Chat feature is not connected yet"
        }

        root.addView(title)
        root.addView(avatar)
        root.addView(subtitle)
        root.addView(status)
        root.addView(action)
        setContentView(root)
    }
}
