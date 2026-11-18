package com.pulse.player.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.pulse.player.data.Prefs

data class AppTheme(
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val bg: Color,
    val card: Color,
    val cardHi: Color,
    val dim: Color,
)

val Themes = listOf(
    AppTheme("Darknes", Color(0xFFB14BFF), Color(0xFF22D3EE), Color(0xFFFF3D9A), Color(0xFF07070D), Color(0xFF13131F), Color(0xFF1D1D2E), Color(0xFF9A9AB5)),
    AppTheme("Okyanus", Color(0xFF3B82F6), Color(0xFF22D3EE), Color(0xFF60A5FA), Color(0xFF050B16), Color(0xFF0D1726), Color(0xFF16243A), Color(0xFF8FA3BF)),
    AppTheme("Gün Batımı", Color(0xFFFF7A45), Color(0xFFFFC048), Color(0xFFFF3D6E), Color(0xFF120806), Color(0xFF1E110D), Color(0xFF2B1913), Color(0xFFB79C90)),
    AppTheme("Orman", Color(0xFF22C55E), Color(0xFFA3E635), Color(0xFF14B8A6), Color(0xFF05100A), Color(0xFF0E1C13), Color(0xFF16291C), Color(0xFF8FB39B)),
    AppTheme("Kızıl", Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFE11D48), Color(0xFF0F0505), Color(0xFF1C0C0C), Color(0xFF2A1212), Color(0xFFB39494)),
    AppTheme("Altın", Color(0xFFEAB308), Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFF0D0B04), Color(0xFF1A170B), Color(0xFF262112), Color(0xFFB8AE8A)),
    AppTheme("Buz", Color(0xFF38BDF8), Color(0xFFE0F2FE), Color(0xFFA5B4FC), Color(0xFF060A10), Color(0xFF0F1620), Color(0xFF18222F), Color(0xFF9AAFC4)),
    AppTheme("Gece Yarısı", Color(0xFF6366F1), Color(0xFF94A3B8), Color(0xFFA855F7), Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF151515), Color(0xFF8A8A8A)),
    AppTheme("Şeker", Color(0xFFF472B6), Color(0xFFC084FC), Color(0xFFFB7185), Color(0xFF12070F), Color(0xFF1F0F1A), Color(0xFF2C1626), Color(0xFFC0A0B8)),
    AppTheme("Matrix", Color(0xFF00E676), Color(0xFF39FF14), Color(0xFF00BFA5), Color(0xFF000500), Color(0xFF06120A), Color(0xFF0C1F12), Color(0xFF7FB38F)),
)

object ThemeState {
    private var idx by mutableIntStateOf(0)
    private var dyn by mutableStateOf(true)
    val index: Int get() = idx
    val dynamicColor: Boolean get() = dyn
    val current: AppTheme get() = Themes[idx.coerceIn(0, Themes.lastIndex)]

    fun load(ctx: Context) {
        val p = Prefs.get(ctx)
        idx = p.getInt("theme", 0).coerceIn(0, Themes.lastIndex)
        dyn = p.getBoolean("dyn_color", true)
    }

    fun set(ctx: Context, i: Int) { idx = i; Prefs.get(ctx).edit().putInt("theme", i).apply() }
    fun setDynamic(ctx: Context, v: Boolean) { dyn = v; Prefs.get(ctx).edit().putBoolean("dyn_color", v).apply() }
}

// Uygulama genelinde kullanılan renkler, seçili temayı takip eder
val Neon: Color get() = ThemeState.current.primary
val Cyan: Color get() = ThemeState.current.secondary
val Pink: Color get() = ThemeState.current.tertiary
val Bg: Color get() = ThemeState.current.bg
val Card: Color get() = ThemeState.current.card
val CardHi: Color get() = ThemeState.current.cardHi
val TextDim: Color get() = ThemeState.current.dim

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    val t = ThemeState.current
    val scheme = darkColorScheme(
        primary = t.primary,
        onPrimary = Color.White,
        secondary = t.secondary,
        tertiary = t.tertiary,
        background = t.bg,
        onBackground = Color.White,
        surface = t.card,
        onSurface = Color.White,
        surfaceVariant = t.cardHi,
        onSurfaceVariant = t.dim,
        surfaceContainer = t.card,
        surfaceContainerHigh = t.cardHi,
        surfaceContainerLow = t.card,
        outline = t.cardHi,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
