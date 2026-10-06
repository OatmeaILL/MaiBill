package com.maibill.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 成员应援色主题（MyGO!!!!! / Ave Mujica） */
data class MemberTheme(val key: String, val name: String, val light: Color, val dark: Color)

object MemberThemes {
    val all = listOf(
        MemberTheme("mutsumi", "小睦 · 薄荷绿", Color(0xFF2E9C74), Color(0xFF4FC08D)),
        MemberTheme("tomori", "灯 · 晴空蓝", Color(0xFF4F7CC9), Color(0xFF8FB3F0)),
        MemberTheme("anon", "爱音 · 樱粉", Color(0xFFE0598C), Color(0xFFF58FB0)),
        MemberTheme("rana", "乐奈 · 蜜柑", Color(0xFFC8951F), Color(0xFFF0C04A)),
        MemberTheme("taki", "立希 · 蔷薇红", Color(0xFFC24B63), Color(0xFFE87A92)),
        MemberTheme("soyo", "祥子 · 雾紫", Color(0xFF8A7FC0), Color(0xFFB0A6E0)),
    )

    fun byKey(key: String): MemberTheme = all.firstOrNull { it.key == key } ?: all[0]
}

/** 外观模式三档：跟随系统 / 固定白天 / 固定黑夜 */
object ThemeMode {
    const val AUTO = "auto"
    const val LIGHT = "light"
    const val DARK = "dark"

    val all = listOf(
        AUTO to "自动",
        LIGHT to "白天",
        DARK to "黑夜",
    )

    fun label(key: String): String = all.firstOrNull { it.first == key }?.second ?: "自动"

    /** 设置行副标题用的一句话摘要 */
    fun sub(key: String): String = when (key) {
        LIGHT -> "始终浅色"
        DARK -> "始终深色"
        else -> "跟随系统"
    }

    /** 对话框内的完整说明 */
    fun detail(key: String): String = when (key) {
        LIGHT -> "不管系统怎么设置，都使用浅色界面"
        DARK -> "不管系统怎么设置，都使用深色界面，夜间更护眼"
        else -> "跟随系统的深色模式，日落后自动变深色"
    }
}

/** 当前主题（全局状态，切换即时生效） */
object ThemeState {
    var member by mutableStateOf("mutsumi")
    var mode by mutableStateOf(ThemeMode.AUTO)
    val theme: MemberTheme get() = MemberThemes.byKey(member)
}

object ThemePrefs {
    private const val FILE = "theme"

    fun load(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("member", "mutsumi") ?: "mutsumi"

    fun save(ctx: Context, key: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("member", key).apply()
    }

    fun loadMode(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("mode", ThemeMode.AUTO)
            ?: ThemeMode.AUTO

    fun saveMode(ctx: Context, mode: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("mode", mode).apply()
    }
}

/**
 * 全局统一的"当前是否深色"判断：由「外观模式」设置决定，而非直接看系统。
 * 任何需要区分深浅色配色的地方都应调用它，保证与主题设置一致。
 */
@Composable
fun appIsDark(): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (ThemeState.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> systemDark
    }
}

private val BaseLight = lightColorScheme(
    primary = Color(0xFF2E9C74),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFF2E8),
    onPrimaryContainer = Color(0xFF0C3B27),
    secondary = Color(0xFF426553),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFF2E8),
    onSecondaryContainer = Color(0xFF0C3B27),
    background = Color(0xFFF4F6F1),
    onBackground = Color(0xFF1B2A21),
    surface = Color.White,
    onSurface = Color(0xFF1B2A21),
    surfaceVariant = Color(0xFFEDF0EA),
    onSurfaceVariant = Color(0xFF79857C),
    outline = Color(0xFFC9D1C9),
    outlineVariant = Color(0xFFE8ECE6),
    error = Color(0xFFE0574A),
    onError = Color.White,
)

private val BaseDark = darkColorScheme(
    primary = Color(0xFF4FC08D),
    onPrimary = Color(0xFF06281A),
    primaryContainer = Color(0xFF1E3B2D),
    onPrimaryContainer = Color(0xFFB9F0D4),
    secondary = Color(0xFF9CCDB4),
    onSecondary = Color(0xFF0C3B27),
    secondaryContainer = Color(0xFF24352C),
    onSecondaryContainer = Color(0xFFB9F0D4),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE9EEE9),
    surface = Color(0xFF1A211B),
    onSurface = Color(0xFFE9EEE9),
    surfaceVariant = Color(0xFF263027),
    onSurfaceVariant = Color(0xFF9AA69C),
    outline = Color(0xFF3C463C),
    outlineVariant = Color(0xFF242D24),
    error = Color(0xFFFF8A7D),
    onError = Color(0xFF4A0E06),
)

/** 在基础配色上替换主色系（primary / secondary / container 家族），其余保持主题底色 */
private fun themed(base: ColorScheme, m: MemberTheme, dark: Boolean): ColorScheme {
    val p = if (dark) m.dark else m.light
    val container = lerp(if (dark) Color(0xFF1E3B2D) else Color.White, p, if (dark) 0.38f else 0.16f)
    val onContainer = if (dark) lerp(Color.White, p, 0.45f) else lerp(Color(0xFF0C3B27), Color.Black, 0.35f)
    return base.copy(
        primary = p,
        onPrimary = if (dark) Color(0xFF06281A) else Color.White,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = p,
        onSecondary = if (dark) Color(0xFF06281A) else Color.White,
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
    )
}

@Composable
fun MaiBillTheme(darkTheme: Boolean = appIsDark(), content: @Composable () -> Unit) {
    val m = MemberThemes.byKey(ThemeState.member)
    val scheme = if (darkTheme) themed(BaseDark, m, true) else themed(BaseLight, m, false)
    MaterialTheme(colorScheme = scheme) {
        // 根 Surface：提供 LocalContentColor，否则未显式指定颜色的 Text 在深色下仍是黑色
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.background,
            contentColor = scheme.onBackground,
        ) {
            ApplySystemAppearance(dark = darkTheme, background = scheme.background)
            content()
        }
    }
}

/** 状态栏/导航栏图标明暗 + 窗口底色跟随外观模式（系统栏透明时图标必须与之反色，否则看不清） */
@Composable
private fun ApplySystemAppearance(dark: Boolean, background: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val bgArgb = background.toArgb()
    LaunchedEffect(dark, bgArgb) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        window.setBackgroundDrawable(ColorDrawable(bgArgb))
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 跟随主题的主渐变（Hero 卡 / FAB 用）：纵向「墨绿 → 薄荷」。
 * 三档都压在深色区间内——白字落在最浅一档也有 3.4:1 对比度。
 */
@Composable
fun heroGradient(): Brush {
    val p = MaterialTheme.colorScheme.primary
    return Brush.verticalGradient(
        listOf(lerp(p, Color.Black, 0.42f), lerp(p, Color.Black, 0.12f), p)
    )
}

/** 主题之外的少量语义色（跟随深浅模式，不随应援色变化） */
object MaiColors {
    val amber: Color @Composable get() = if (appIsDark()) AmberDark else Amber
    val amberContainer: Color @Composable get() = if (appIsDark()) AmberContainerDark else AmberContainer
    val trackBar: Color @Composable get() = if (appIsDark()) TrackBarDark else TrackBarLight
}
