package online.fujinet.go.coco.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the Android-keycode -> XRoar hkbd (USB HID) scancode mapping a hardware
 * keyboard sends to the CoCo. The KEYCODE_* values are compile-time constants
 * (inlined), so this runs under plain JUnit without an Android device.
 */
class HardwareKeyboardTest {

    @Test
    fun lettersAndDigitsMapPositionally() {
        assertEquals(Coco.SCAN_A, cocoScancode(KeyEvent.KEYCODE_A))
        assertEquals(Coco.SCAN_A + 25, cocoScancode(KeyEvent.KEYCODE_Z))
        assertEquals(Coco.SCAN_1, cocoScancode(KeyEvent.KEYCODE_1))
        assertEquals(Coco.SCAN_1 + 8, cocoScancode(KeyEvent.KEYCODE_9))
        assertEquals(Coco.SCAN_0, cocoScancode(KeyEvent.KEYCODE_0))
    }

    @Test
    fun specialAndModifierKeysMap() {
        assertEquals(Coco.SCAN_RETURN, cocoScancode(KeyEvent.KEYCODE_ENTER))
        assertEquals(Coco.SCAN_RETURN, cocoScancode(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertEquals(Coco.SCAN_ESCAPE, cocoScancode(KeyEvent.KEYCODE_ESCAPE))      // BREAK
        assertEquals(Coco.SCAN_BACKSPACE, cocoScancode(KeyEvent.KEYCODE_DEL))
        assertEquals(Coco.SCAN_SPACE, cocoScancode(KeyEvent.KEYCODE_SPACE))
        assertEquals(Coco.SCAN_HOME, cocoScancode(KeyEvent.KEYCODE_MOVE_HOME))     // CLEAR
        // XRoar's keymap is positional, so modifiers are forwarded as scancodes.
        assertEquals(Coco.SCAN_SHIFT_L, cocoScancode(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertEquals(Coco.SCAN_SHIFT_L, cocoScancode(KeyEvent.KEYCODE_SHIFT_RIGHT))
        assertEquals(Coco.SCAN_CONTROL_L, cocoScancode(KeyEvent.KEYCODE_CTRL_LEFT))
        assertEquals(Coco.SCAN_ALT_L, cocoScancode(KeyEvent.KEYCODE_ALT_LEFT))
    }

    @Test
    fun cursorKeysMapAndUnknownKeysAreNotForwarded() {
        // Cursor keys typed on a keyboard reach the CoCo (e.g. the FujiNet CONFIG
        // selection bar). isDpadNavigation() routes a TV remote's D-pad (SOURCE_DPAD)
        // to focus navigation before this keycode lookup is consulted.
        assertEquals(Coco.SCAN_LEFT, cocoScancode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(Coco.SCAN_RIGHT, cocoScancode(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(Coco.SCAN_UP, cocoScancode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(Coco.SCAN_DOWN, cocoScancode(KeyEvent.KEYCODE_DPAD_DOWN))
        // DPAD_CENTER has no CoCo equivalent; unknown keys aren't forwarded either.
        assertNull(cocoScancode(KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(cocoScancode(KeyEvent.KEYCODE_VOLUME_UP))
    }
}
