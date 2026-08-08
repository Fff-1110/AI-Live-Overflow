package com.ailiveoverflow

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var toggleBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handleFeedIntent(intent)) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "🐾 Kuro Neko"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#ff8899"))
            gravity = Gravity.CENTER
        }

        toggleBtn = Button(this).apply {
            textSize = 18f
        }

        toggleBtn.setOnClickListener {
            if (isServiceRunning()) {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                refresh()
            } else {
                startForegroundService(Intent(this@MainActivity, OverlayService::class.java))
                refresh()
            }
        }

        root.addView(title)
        root.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 })
        root.addView(toggleBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 36 })

        setContentView(root)
        refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleFeedIntent(intent)) return
        setIntent(intent)
    }

    private fun handleFeedIntent(i: Intent?): Boolean {
        if (i == null) return false
        val data = i.data ?: return false
        if ("ailive" == data.scheme && "feed" == data.host) {
            val food = (data.path ?: "/default").trim('/').ifEmpty { "default" }
            try {
                File(filesDir, "cmd.txt").writeText("food:" + food)
                Toast.makeText(this, "刁刁吃到 " + food + " 啦 🐟", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
            }
            finish()
            return true
        }
        return false
    }

    private fun refresh() {
        val running = isServiceRunning()
        statusText.text = if (running) "状态：● 运行中" else "状态：○ 已停止"
        toggleBtn.text = if (running) "关闭小黑猫" else "开启小黑猫"
    }

    private fun isServiceRunning(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.getRunningServices(100).any { it.service.className == OverlayService::class.java.name }
    }
}
