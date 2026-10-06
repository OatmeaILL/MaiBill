package com.maibill.app.ui.auto

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maibill.app.ui.theme.appIsDark
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.core.app.NotificationManagerCompat

private fun listenerGranted(ctx: android.content.Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

private fun a11yGranted(ctx: android.content.Context): Boolean {
    val am = ctx.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
        as? android.view.accessibility.AccessibilityManager ?: return false
    return am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
        .any { it.resolveInfo.serviceInfo.packageName == ctx.packageName }
}

@Composable
fun AutoRecordScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var listenerOk by remember { mutableStateOf(listenerGranted(ctx)) }
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var a11yOk by remember { mutableStateOf(a11yGranted(ctx)) }
    var enabled by remember { mutableStateOf(com.maibill.app.auto.AutoPrefs.enabled(ctx)) }
    val dark = appIsDark()
    var logs by remember { mutableStateOf(com.maibill.app.auto.AutoPrefs.recentLogs(ctx)) }

    // 从系统设置授权返回后刷新状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        listenerOk = listenerGranted(ctx)
        overlayOk = Settings.canDrawOverlays(ctx)
        a11yOk = a11yGranted(ctx)
        logs = com.maibill.app.auto.AutoPrefs.recentLogs(ctx)
    }

    val allGranted = listenerOk && overlayOk && a11yOk

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                "自动记账", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text("它做什么", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "在微信、支付宝支付成功时（支付通知或支付成功页面，任一识别到即可），自动弹出记账气泡。\n点一下气泡 → 金额已填好，选个分类保存即可。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp)
        ) {
            PermRow(
                emoji = "🔔", title = "通知使用权",
                sub = "读取微信/支付宝的支付通知",
                granted = listenerOk,
                onGrant = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            )
            RowDivider()
            PermRow(
                emoji = "🫧", title = "悬浮窗权限",
                sub = "在支付界面上方弹出记账气泡",
                granted = overlayOk,
                onGrant = {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + ctx.packageName),
                        )
                    )
                },
            )
            RowDivider()
            PermRow(
                emoji = "👁️", title = "无障碍监听",
                sub = "识别支付成功页面（App 内付款靠它）",
                granted = a11yOk,
                onGrant = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动记账", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (!allGranted) "先完成上面三项授权" else if (enabled) "已开启 · 支付时弹出气泡" else "已关闭",
                        fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = enabled && allGranted,
                    onCheckedChange = { v ->
                        com.maibill.app.auto.AutoPrefs.setEnabled(ctx, v)
                        enabled = v
                    },
                    enabled = allGranted,
                )
            }
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text("诊断 · 最近收到的支付通知", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "只保留最近 3 条来自微信/支付宝的通知（仅本机）。这里是空的，说明通知监听还没生效。",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp),
            )
            if (logs.isEmpty()) {
                Text(
                    "（暂无记录）", fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Column(
                    Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    logs.forEach { log ->
                        Text(
                            log, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text("用前须知", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "1. 小米/华为等手机请在系统设置里允许本应用「自启动」「后台运行」「后台弹出界面」，否则支付时收不到气泡\n" +
                    "2. 微信/支付宝自身的通知开关需保持打开\n" +
                    "3. 通知里没有金额时，点气泡手动补填\n" +
                    "4. 解析出的数据只存在手机里（本应用无网络权限）\n" +
                    "5. 授权通知监听后，若上面的诊断区一直没有消息，请重启一次手机再试（部分系统需要重启才开始监听）",
                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun PermRow(
    emoji: String,
    title: String,
    sub: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onGrant)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        val dark = appIsDark()
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (granted) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.error.copy(alpha = if (dark) 0.2f else 0.12f)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                if (granted) "已授权 ✓" else "去授权 ›",
                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RowDivider() {
    androidx.compose.material3.HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}
