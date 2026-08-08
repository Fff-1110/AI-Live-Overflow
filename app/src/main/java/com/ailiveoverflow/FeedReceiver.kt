package com.ailiveoverflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.io.File

/**
 * 广播喂食接收器：插件/外部通过广播投喂，不启动 Activity、不打断当前界面。
 * 用法：sendBroadcast(action="com.ailiveoverflow.FEED", extra "food"=<食物id>)
 */
class FeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val food = intent.getStringExtra("food") ?: "default"
        try {
            File(context.filesDir, "cmd.txt").writeText("food:" + food)
            Toast.makeText(context, "刁刁吃到 " + food + " 啦 🐟", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
        }
    }
}