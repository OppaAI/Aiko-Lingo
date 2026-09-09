package com.aiko.lingo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ShoujoColorScheme = lightColorScheme(
    primary = ShoujoAccent,
    secondary = ShoujoPink,
    tertiary = PastelBlue,
    background = ShoujoSoftPink,
    surface = ShoujoSoftPink,
    onPrimary = Color.White,
    onSecondary = ShoujoText,
    onTertiary = ShoujoText,
    onBackground = ShoujoText,
    onSurface = ShoujoText,
    error = Color(0xFFEF5350),
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFC62828)
)

private val LavenderGlassColorScheme = darkColorScheme(
    primary = LavenderLight,
    secondary = LavenderMid,
    tertiary = LavenderAccent,
    background = LavenderDeep,
    surface = LavenderMid,
    onPrimary = Color.White,
    onSecondary = LavenderText,
    onTertiary = LavenderText,
    onBackground = LavenderText,
    onSurface = LavenderText
)

@Composable
fun AikoLingoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        LavenderGlassColorScheme
    } else {
        ShoujoColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
