package online.fujinet.go.coco.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.fujinet.go.coco.SessionController
import online.fujinet.go.coco.input.Coco

/**
 * On-screen Tandy Color Computer keyboard. Keys are *held* for the duration of
 * the touch (press on finger-down, release on finger-up) so the CoCo's ~60Hz
 * keyboard-matrix scan actually sees them -- a momentary tap that presses and
 * releases within one emulated frame would be invisible to the machine. Keys
 * emit XRoar hkbd (USB HID) scancodes via [SessionController]; XRoar's
 * untranslated keymap maps them positionally to the emulated CoCo keys. SHIFT
 * and CTRL are sticky toggles (CTRL is meaningful on the CoCo 3), cleared after
 * the next real key is released.
 */
@Composable
fun CocoKeyboard(session: SessionController, modifier: Modifier = Modifier) {
    var shift by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }

    fun toggleShift() {
        shift = !shift
        if (shift) session.keyDown(Coco.SCAN_SHIFT_L) else session.keyUp(Coco.SCAN_SHIFT_L)
    }

    fun toggleCtrl() {
        ctrl = !ctrl
        if (ctrl) session.keyDown(Coco.SCAN_CONTROL_L) else session.keyUp(Coco.SCAN_CONTROL_L)
    }

    fun press(scancode: Int) = session.keyDown(scancode)

    fun release(scancode: Int) {
        session.keyUp(scancode)
        // Sticky modifiers clear after the next key, so "tap SHIFT, tap A" types
        // one shifted A and the modifier lets go.
        if (shift) { shift = false; session.keyUp(Coco.SCAN_SHIFT_L) }
        if (ctrl) { ctrl = false; session.keyUp(Coco.SCAN_CONTROL_L) }
    }

    val scope = rememberCoroutineScope()

    // Type a command macro one character at a time, holding each key long enough
    // for the CoCo's ~60Hz matrix scan to register it. Run from a clean modifier
    // state so a sticky SHIFT/CTRL can't alter the typed characters.
    fun typeMacro(text: String) {
        if (shift) { shift = false; session.keyUp(Coco.SCAN_SHIFT_L) }
        if (ctrl) { ctrl = false; session.keyUp(Coco.SCAN_CONTROL_L) }
        scope.launch {
            for (c in text) {
                val stroke = Coco.keyStrokeFor(c) ?: continue
                if (stroke.shift) session.keyDown(Coco.SCAN_SHIFT_L)
                session.keyDown(stroke.scancode)
                delay(MACRO_HOLD_MS)
                session.keyUp(stroke.scancode)
                if (stroke.shift) session.keyUp(Coco.SCAN_SHIFT_L)
                delay(MACRO_GAP_MS)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KeyRow {
            Key("BREAK", 1.5f, Coco.SCAN_ESCAPE, ::press, ::release, bg = CocoBreakRed, fg = Color.White)
            Key("CLEAR", 1.5f, Coco.SCAN_HOME, ::press, ::release)
            Key("DEL", 1.5f, Coco.SCAN_BACKSPACE, ::press, ::release)
        }
        KeyRow {
            MacroKey("RUNM\"", 1.5f) { typeMacro("RUNM\"") }
            MacroKey("DIR", 1.5f) { typeMacro("DIR") }
            MacroKey("DOS", 1.5f) { typeMacro("DOS") }
        }
        KeyRow {
            // CoCo shifted top row: 1! 2" 3# 4$ 5% 6& 7' 8( 9) 0  - =
            val shiftedDigits = "!\"#\$%&'()0"
            "1234567890".forEachIndexed { i, c ->
                val lbl = if (shift) shiftedDigits[i].toString() else c.toString()
                Key(lbl, 1f, Coco.scanForDigit(c), ::press, ::release)
            }
            Key(if (shift) "=" else "-", 1f, Coco.SCAN_MINUS, ::press, ::release)
        }
        KeyRow {
            for (c in "QWERTYUIOP") Key(c.toString(), 1f, Coco.scanForLetter(c), ::press, ::release)
        }
        KeyRow {
            for (c in "ASDFGHJKL") Key(c.toString(), 1f, Coco.scanForLetter(c), ::press, ::release)
            Key(if (shift) "+" else ";", 1f, Coco.SCAN_SEMICOLON, ::press, ::release)
            Key("ENTER", 1.8f, Coco.SCAN_RETURN, ::press, ::release)
        }
        KeyRow {
            for (c in "ZXCVBNM") Key(c.toString(), 1f, Coco.scanForLetter(c), ::press, ::release)
            Key(if (shift) "<" else ",", 1f, Coco.SCAN_COMMA, ::press, ::release)
            Key(if (shift) ">" else ".", 1f, Coco.SCAN_PERIOD, ::press, ::release)
            Key(if (shift) "?" else "/", 1f, Coco.SCAN_SLASH, ::press, ::release)
        }
        KeyRow {
            ModKey("SHIFT", 1.4f, active = shift) { toggleShift() }
            ModKey("CTRL", 1.2f, active = ctrl) { toggleCtrl() }
            Key("SPACE", 3f, Coco.SCAN_SPACE, ::press, ::release)
            Key("←", 1f, Coco.SCAN_LEFT, ::press, ::release)
            Key("↓", 1f, Coco.SCAN_DOWN, ::press, ::release)
            Key("↑", 1f, Coco.SCAN_UP, ::press, ::release)
            Key("→", 1f, Coco.SCAN_RIGHT, ::press, ::release)
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

// A momentary key held for the duration of the touch: press on finger-down,
// release on finger-up (incl. cancel), so the held interval spans many frames.
// The Color Computer's BREAK key is red on the real keyboard.
private val CocoBreakRed = Color(0xFFC62828)

@Composable
private fun RowScope.Key(
    label: String,
    weight: Float,
    scancode: Int,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    bg: Color = MaterialTheme.colorScheme.surface,
    fg: Color = MaterialTheme.colorScheme.onSurface,
) {
    KeyBox(label, weight, bg, fg, scancode) {
        detectTapGestures(onPress = {
            onPress(scancode)
            try {
                awaitRelease()
            } finally {
                onRelease(scancode)
            }
        })
    }
}

// A command macro: tapping it types a fixed string (e.g. a BASIC command) into
// the running CoCo, character by character. See [CocoKeyboard]'s typeMacro.
private const val MACRO_HOLD_MS = 40L  // each key held this long for the matrix scan
private const val MACRO_GAP_MS = 40L   // released this long before the next key

@Composable
private fun RowScope.MacroKey(label: String, weight: Float, onTap: () -> Unit) {
    KeyBox(label, weight, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, label) {
        detectTapGestures(onTap = { onTap() })
    }
}

@Composable
private fun RowScope.ModKey(label: String, weight: Float, active: Boolean, onToggle: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    KeyBox(label, weight, bg, fg, active) {
        detectTapGestures(onTap = { onToggle() })
    }
}

@Composable
private fun RowScope.KeyBox(
    label: String,
    weight: Float,
    bg: Color,
    fg: Color,
    key: Any,
    gestures: suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit,
) {
    val compact = compactKeyboard()
    Box(
        modifier = Modifier
            .weight(weight)
            .height(if (compact) 28.dp else 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .pointerInput(key, bg) { gestures() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = if (compact) 11.sp else 14.sp, textAlign = TextAlign.Center)
    }
}

/**
 * The on-screen keyboard's keys shrink to a compact height on TV and other short
 * screens (e.g. landscape) so it doesn't fill most of the display.
 */
@Composable
private fun compactKeyboard(): Boolean {
    val config = LocalConfiguration.current
    val isTv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    return isTv || config.screenHeightDp < 480
}
