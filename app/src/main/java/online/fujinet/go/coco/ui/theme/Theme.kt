package online.fujinet.go.coco.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Accent derived from the FujiNet Go CoCo launcher icon's green background
// (#8BC34A -- a nod to the Color Computer's iconic green screen), with dark
// cool-neutral surfaces to match.
private val CocoGreen = Color(0xFF8BC34A)
private val CocoDark = Color(0xFF0C140A)
private val CocoPanel = Color(0xFF16240F)

private val DarkColors = darkColorScheme(
    primary = CocoGreen,
    onPrimary = Color.Black,
    background = CocoDark,
    surface = CocoPanel,
    onSurface = Color(0xFFE7ECE3),
)

private val LightColors = lightColorScheme(
    primary = CocoGreen,
    onPrimary = Color.Black,
)

@Composable
fun FujiNetGoCoCoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
