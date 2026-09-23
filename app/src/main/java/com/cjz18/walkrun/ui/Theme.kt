package com.cjz18.walkrun.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1B7F3A)
private val OnGreen = Color.White
private val ErrorRed = Color(0xFFB3261E)

private val Colors = lightColorScheme(
    primary = Green,
    onPrimary = OnGreen,
    error = ErrorRed,
    background = Color(0xFFF4F6F5),
    surface = Color.White,
)

@Composable
fun WalkRunTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content,
    )
}
