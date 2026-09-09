package com.aiko.lingo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Compact type scale so content fits small phone screens (default M3 runs large).
private val CompactTypography = Typography(
    displayLarge = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Normal),
    displayMedium = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Normal),
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Normal),
    headlineLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
)

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
        typography = CompactTypography,
        content = content
    )
}
