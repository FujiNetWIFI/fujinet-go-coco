package online.fujinet.go.coco.input

import android.view.InputDevice
import android.view.KeyEvent

/**
 * Routes an attached hardware keyboard (USB / Bluetooth) to the Color Computer,
 * mirroring what the on-screen [online.fujinet.go.coco.ui.CocoKeyboard] sends:
 * XRoar hkbd (USB HID) scancodes, pressed and released so the CoCo's ~60Hz
 * keyboard-matrix scan sees them. Because XRoar's untranslated keymap is
 * *positional*, modifier keys are forwarded as their own scancodes (Shift+1
 * becomes the CoCo's "!"), so -- unlike a character-based keyboard -- Shift/Ctrl
 * are mapped here too.
 *
 * Only events from a real *alphabetic* keyboard device are consumed, and the D-pad
 * cluster (arrows + OK) is always left to Compose focus navigation so a TV remote
 * keeps driving the on-screen keyboard rather than typing.
 */
class HardwareKeyboard(
    private val onDown: (scancode: Int) -> Unit,
    private val onUp: (scancode: Int) -> Unit,
) {
    fun onKey(event: KeyEvent): Boolean {
        if (!event.isFromPhysicalKeyboard()) return false
        if (isDpadNavigation(event)) return false
        val scancode = cocoScancode(event.keyCode) ?: return false
        when (event.action) {
            // Hold the key down across the matrix scan; ignore auto-repeat re-downs.
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) onDown(scancode)
            KeyEvent.ACTION_UP -> onUp(scancode)
            else -> return false
        }
        return true
    }

    private fun KeyEvent.isFromPhysicalKeyboard(): Boolean {
        val d = device ?: return false
        return !d.isVirtual &&
            d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
            source and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
    }
}

/**
 * Map an Android keycode to its XRoar hkbd (USB HID) scancode, or null for keys we
 * don't forward. Pure (no [KeyEvent] instance) so it can be unit-tested. The cursor
 * keys are intentionally absent -- they navigate the on-screen keyboard (see
 * isDpadNavigation()); use the on-screen arrow keys to move the CoCo cursor.
 */
internal fun cocoScancode(androidKeyCode: Int): Int? = when (androidKeyCode) {
    in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
        Coco.SCAN_A + (androidKeyCode - KeyEvent.KEYCODE_A)
    KeyEvent.KEYCODE_0 -> Coco.SCAN_0
    in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 ->
        Coco.SCAN_1 + (androidKeyCode - KeyEvent.KEYCODE_1)
    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Coco.SCAN_RETURN
    KeyEvent.KEYCODE_ESCAPE -> Coco.SCAN_ESCAPE           // BREAK
    KeyEvent.KEYCODE_DEL -> Coco.SCAN_BACKSPACE           // Backspace
    KeyEvent.KEYCODE_SPACE -> Coco.SCAN_SPACE
    KeyEvent.KEYCODE_MINUS -> Coco.SCAN_MINUS
    KeyEvent.KEYCODE_EQUALS -> Coco.SCAN_EQUAL
    KeyEvent.KEYCODE_SEMICOLON -> Coco.SCAN_SEMICOLON
    KeyEvent.KEYCODE_APOSTROPHE -> Coco.SCAN_APOSTROPHE
    KeyEvent.KEYCODE_COMMA -> Coco.SCAN_COMMA
    KeyEvent.KEYCODE_PERIOD -> Coco.SCAN_PERIOD
    KeyEvent.KEYCODE_SLASH -> Coco.SCAN_SLASH
    KeyEvent.KEYCODE_MOVE_HOME -> Coco.SCAN_HOME          // CLEAR
    KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> Coco.SCAN_SHIFT_L
    KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> Coco.SCAN_CONTROL_L
    KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> Coco.SCAN_ALT_L
    else -> null
}

/**
 * True for the keys that must navigate/activate the on-screen keyboard rather than
 * type into the emulator. The arrows and DPAD_CENTER are always reserved. A remote's
 * "OK" can arrive as ENTER; it carries a D-pad source, whereas a typing keyboard's
 * Enter does not -- so keyboard Enter still reaches the CoCo as ENTER.
 */
private fun isDpadNavigation(event: KeyEvent): Boolean = when (event.keyCode) {
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER -> true
    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
        event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
    else -> false
}
