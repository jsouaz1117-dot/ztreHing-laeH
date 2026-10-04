package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = EmeraldGreen,
    secondary = GoldChakra,
    tertiary = SoftLavender,
    background = SlateDark,
    surface = SlateCard,
    onPrimary = Color(0xFF0F0F12),
    onSecondary = Color(0xFF0F0F12),
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF242E35),
    onSurfaceVariant = Color(0xFFEAEDED)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldGreen,
    secondary = GoldChakra,
    tertiary = SoftLavender,
    background = Color(0xFFF4F6F7),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color(0xFF0F0F12),
    onTertiary = Color.White,
    onBackground = Color(0xFF0F0F12),
    onSurface = Color(0xFF0F0F12)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+ (false by default to keep the custom palette)
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
