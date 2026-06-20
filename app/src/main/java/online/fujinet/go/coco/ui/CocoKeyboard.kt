package online.fujinet.go.coco.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KeyRow {
            for (c in "1234567890") Key(c.toString(), 1f, Coco.scanForDigit(c), ::press, ::release)
            Key("-", 1f, Coco.SCAN_MINUS, ::press, ::release)
        }
        KeyRow {
            for (c in "QWERTYUIOP") Key(c.toString(), 1f, Coco.scanForLetter(c), ::press, ::release)
        }
        KeyRow {
            for (c in "ASDFGHJKL") Key(c.toString(), 1f, Coco.scanForLetter(c), ::press, ::release)
            Key(";", 1f, Coco.SCAN_SEMICOLON, ::press, ::release)
            Key("ENTER", 1.8f, Coco.SCAN_RETURN, ::press, ::release)
        }
        KeyRow {
            for (c in "ZXCVBNM") Key(c.toString(), 1f, Coco.scanForLetter(c), ::press, ::release)
            Key(",", 1f, Coco.SCAN_COMMA, ::press, ::release)
            Key(".", 1f, Coco.SCAN_PERIOD, ::press, ::release)
            Key("/", 1f, Coco.SCAN_SLASH, ::press, ::release)
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
        KeyRow {
            Key("BREAK", 1.5f, Coco.SCAN_ESCAPE, ::press, ::release)
            Key("CLEAR", 1.5f, Coco.SCAN_HOME, ::press, ::release)
            Key("DEL", 1.5f, Coco.SCAN_BACKSPACE, ::press, ::release)
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
@Composable
private fun RowScope.Key(
    label: String,
    weight: Float,
    scancode: Int,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
) {
    KeyBox(label, weight, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, scancode) {
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
    Box(
        modifier = Modifier
            .weight(weight)
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .pointerInput(key, bg) { gestures() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}
