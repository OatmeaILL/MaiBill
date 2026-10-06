package com.maibill.app.auto

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maibill.app.MainActivity
import com.maibill.app.util.Money
import java.math.BigDecimal

/** 自动记账总开关（SharedPreferences 持久化） */
object AutoPrefs {
    private const val FILE = "auto_record"
    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun setEnabled(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("enabled", v).apply()
    }

    /** 诊断：记录最近 3 条微信/支付宝通知原文（仅存本机，用于排查监听是否生效） */
    fun logNotification(ctx: Context, text: String) {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val old = sp.getString("recent_logs", "") ?: ""
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())
        val list = (listOf("$time $text") + old.split("\n").filter { it.isNotBlank() }).take(3)
        sp.edit().putString("recent_logs", list.joinToString("\n")).apply()
    }

    fun recentLogs(ctx: Context): List<String> =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString("recent_logs", "")
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
}

/**
 * 支付气泡点击后携带的预填数据。
 * request 变化会触发 AppRoot 跳转记账页；RecordViewModel 初始化时消费并清除。
 */
object PendingAutoRecord {
    var request by mutableStateOf(0L)
        private set
    var amountFen: Long? = null
        private set
    var source: String? = null
        private set

    fun post(fen: Long?, source: String?) {
        amountFen = fen
        this.source = source
        request = System.currentTimeMillis()
    }

    fun clear() {
        amountFen = null
        source = null
        request = 0L
    }
}

/** 从通知文本里解析支付金额（要求带 ¥/￥ 前缀或"元"后缀，减少误判） */
object PaymentParser {
    private val AMOUNT = Regex("""[¥￥]\s*(\d{1,9}(?:,\d{3})*(?:\.\d{1,2})?)|(\d{1,9}(?:,\d{3})*(?:\.\d{1,2})?)\s*元""")
    private val HIT = listOf("支付", "付款")
    private val SKIP = listOf("红包", "收款", "退款", "提现", "余额", "转账", "账单")

    fun shouldHandle(text: String): Boolean {
        val t = if (text.length > 300) text.take(300) else text
        return HIT.any { t.contains(it) } && SKIP.none { t.contains(it) }
    }

    fun parseFen(text: String): Long? {
        val m = AMOUNT.find(text) ?: return null
        val num = (m.groupValues[1].ifEmpty { m.groupValues[2] }).replace(",", "")
        return try {
            BigDecimal(num).movePointRight(2).longValueExact().takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    // ---- 支付成功页面（无障碍识别）----
    // 页面文本里有"账单管理""支付时间 2026-09-30"等词，通知用的 SKIP 词表在这里会误杀，
    // 所以用更窄的排除表；金额允许 "-24.44" 负数形式（账单页样式），但要求带小数点
    // 以避开日期里的 "-09""-30"
    private val SKIP_SCREEN = listOf("退款", "收款", "提现", "红包")
    private val SCREEN_AMOUNT = Regex(
        """[¥￥]\s*(\d{1,9}(?:,\d{3})*(?:\.\d{1,2})?)""" +
            """|(\d{1,9}(?:,\d{3})*(?:\.\d{1,2})?)\s*元""" +
            """|[-−](\d{1,6}\.\d{1,2})(?!\d)"""
    )

    fun shouldHandleScreen(text: String): Boolean {
        val t = if (text.length > 600) text.take(600) else text
        return HIT.any { t.contains(it) } && SKIP_SCREEN.none { t.contains(it) }
    }

    fun parseScreenFen(text: String): Long? {
        val m = SCREEN_AMOUNT.find(text) ?: return null
        val num = (m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } }).replace(",", "")
        return try {
            BigDecimal(num).movePointRight(2).longValueExact().takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }
}

/** 悬浮记账气泡：显示 8 秒自动消失，点按带出记账页 */
object AutoBubble {
    private var view: LinearLayout? = null
    private var wm: WindowManager? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoDismiss = Runnable { hide() }
    private var lastShown = 0L
    private var lastKey = ""

    fun show(ctx: Context, amountFen: Long?, source: String) {
        if (!android.provider.Settings.canDrawOverlays(ctx)) return
        if (view != null) return
        val now = System.currentTimeMillis()
        if (now - lastShown < 2500) return
        // 同一分钟内同来源同金额只弹一次（通知与页面两条通道可能先后触发同一笔支付）
        val minuteKey = "$source|$amountFen|${now / 60000}"
        if (minuteKey == lastKey) return
        lastKey = minuteKey
        lastShown = now

        val dp = { v: Int -> (v * ctx.resources.displayMetrics.density).toInt() }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xEE1B2A21.toInt())
            }
            elevation = dp(8).toFloat()
            addView(
                TextView(ctx).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setLineSpacing(0f, 1.15f)
                    text = if (amountFen != null) {
                        "✓ 记一笔 ¥${Money.txt(amountFen)}\n$source · 点按记录"
                    } else {
                        "✓ 有笔支付待记录\n$source · 点按补填"
                    }
                }
            )
            setOnClickListener {
                hide()
                val intent = Intent(ctx, MainActivity::class.java).apply {
                    putExtra("auto_record", true)
                    amountFen?.let { putExtra("auto_amount_fen", it) }
                    putExtra("auto_source", source)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                ctx.startActivity(intent)
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(14)
            y = dp(96)
        }
        wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm?.addView(box, params)
        view = box
        handler.postDelayed(autoDismiss, 8000)
    }

    fun hide() {
        handler.removeCallbacks(autoDismiss)
        view?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        view = null
        wm = null
    }
}

/** 通知监听：识别微信/支付宝的支付通知，弹出记账气泡 */
class AutoRecordListener : NotificationListenerService() {
    private val seen = LinkedHashSet<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (!AutoPrefs.enabled(this)) return
            val pkg = sbn.packageName
            if (pkg != "com.tencent.mm" && pkg != "com.eg.android.AlipayGphone") return
            if (sbn.isOngoing) return
            if (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return

            val ex = sbn.notification.extras
            val title = ex.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = ex.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            val big = ex.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val lines = ex.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
                ?.joinToString(" ") { it?.toString() ?: "" } ?: ""
            val all = listOf(title, text, big, lines).filter { it.isNotBlank() }.joinToString(" ")
            if (all.isBlank()) return

            // 诊断：无论后面是否命中规则，先把收到的原文记下来（仅本机最近 3 条）
            val srcName = if (pkg == "com.tencent.mm") "微信" else "支付宝"
            AutoPrefs.logNotification(this, "[$srcName] $all")

            // 同一条通知更新（同 key 同内容）只处理一次
            val dedupeKey = sbn.key + "|" + all.hashCode()
            synchronized(seen) {
                if (!seen.add(dedupeKey)) return
                if (seen.size > 40) {
                    val it = seen.iterator()
                    seen.remove(it.next())
                }
            }

            if (!PaymentParser.shouldHandle(all)) return
            val amount = PaymentParser.parseFen(all)
            val source = if (pkg == "com.tencent.mm") "微信支付" else "支付宝"
            AutoBubble.show(this, amount, source)
        } catch (_: Exception) {
            // 通知解析失败不能影响系统通知流程
        }
    }
}
