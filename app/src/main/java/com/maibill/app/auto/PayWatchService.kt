package com.maibill.app.auto

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍支付监听：识别"支付成功"页面本身（App 内付款不发系统通知的场景就靠它）。
 * 只解析常见支付/购物 App 的窗口文本，命中"支付/付款"+ 金额后才弹气泡。
 */
class PayWatchService : AccessibilityService() {
    private var lastParse = 0L
    private val seen = LinkedHashSet<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            if (!AutoPrefs.enabled(this)) return
            val now = System.currentTimeMillis()
            if (now - lastParse < 600) return // 内容变化事件高频，限流
            lastParse = now

            val pkg = event.packageName?.toString() ?: return
            val source = SOURCES[pkg] ?: return

            val sb = StringBuilder()
            collectText(rootInActiveWindow, sb, 0)
            val text = sb.toString()
            if (text.isBlank()) return
            if (!PaymentParser.shouldHandleScreen(text)) return
            val fen = PaymentParser.parseScreenFen(text) ?: return

            // 同一分钟同一来源同一金额只记一次
            val key = "$pkg|$fen|${now / 60000}"
            synchronized(seen) {
                if (!seen.add(key)) return
                if (seen.size > 40) {
                    val it = seen.iterator()
                    seen.remove(it.next())
                }
            }
            AutoBubble.show(this, fen, source)
        } catch (_: Exception) {
            // 识别失败不能影响其他应用的正常使用
        }
    }

    override fun onInterrupt() {}

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 25 || sb.length > 2000) return
        node.text?.let { if (it.isNotBlank()) sb.append(it).append(' ') }
        node.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append(' ') }
        for (i in 0 until node.childCount) collectText(node.getChild(i), sb, depth + 1)
    }

    companion object {
        /** 只监听常见支付/购物应用，避免聊天记录里的文字造成误报 */
        val SOURCES = mapOf(
            "com.eg.android.AlipayGphone" to "支付宝",
            "com.tencent.mm" to "微信支付",
            "com.taobao.taobao" to "淘宝",
            "com.taobao.litetaobao" to "淘宝闪购",
            "com.tmall.wireless" to "天猫",
            "com.sankuai.meituan" to "美团",
            "com.sankuai.meituan.takeoutnew" to "美团外卖",
            "com.jingdong.app.mall" to "京东",
            "me.ele" to "饿了么",
            "com.xunmeng.pinduoduo" to "拼多多",
        )
    }
}
