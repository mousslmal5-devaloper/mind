package app.mind.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalMindLanguage = staticCompositionLocalOf { "ar" }

@Composable
fun mindText(arabic: String, english: String): String =
    if (LocalMindLanguage.current == "en") english else arabic
