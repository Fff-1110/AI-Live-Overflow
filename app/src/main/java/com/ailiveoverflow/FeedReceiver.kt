package com.ailiveoverflow
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.io.File
/**
 * 广播接收器：喂食(FEED) + 通用命令(CMD)。
 * FEED: action=com.ailiveoverflow.FEED, extra "food"=<食物id> -> 写 food:<id>
 * CMD:  action=com.ailiveoverflow.CMD, extra "cmd"=<命令> -> 原样写入 cmd.txt（如 say:你好 / music:on）
 * 均不启动 Activity、不打断当前界面。
 */
class FeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action ?: return
            val text = when {
                action == "com.ailiveoverflow.FEED" -> {
                    val food = intent.getStringExtra("food") ?: "default"
                    Toast.makeText(context, "刁刁吃到 " + food + " 啦 🐟", Toast.LENGTH_SHORT).show()
                    "food:" + food
                }
                action == "com.ailiveoverflow.CMD" -> {
                    intent.getStringExtra("cmd") ?: return
                }
                else -> return
            }
            File(context.filesDir, "cmd.txt").writeText(text)
        } catch (e: Exception) {
        }
    }
}