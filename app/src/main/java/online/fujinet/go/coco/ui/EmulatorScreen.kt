package online.fujinet.go.coco.ui

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.fujinet.go.coco.SessionController
import online.fujinet.go.coco.input.Coco

/**
 * The main app screen: the CoCo video surface, a thin control bar (toggle
 * keyboard/joystick, switch CoCo 2 <-> CoCo 3, switch RGB/Composite on the
 * CoCo 3, Reset, open the FujiNet web UI, shut down), and the on-screen keyboard
 * or analog joystick. Lays out for both portrait and landscape.
 */
@Composable
fun EmulatorScreen(
    session: SessionController,
    onOpenFujiNet: () -> Unit,
    onShutdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // At most one input overlay is shown so the emulator surface keeps room.
    var overlay by remember { mutableStateOf(Overlay.KEYBOARD) }
    var machine by remember { mutableIntStateOf(session.machine) }
    var tvInput by remember { mutableIntStateOf(session.tvInput) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(modifier = modifier.fillMaxSize()) {
        ControlBar(
            keyboardActive = overlay == Overlay.KEYBOARD,
            joystickActive = overlay == Overlay.JOYSTICK,
            machine = machine,
            tvInput = tvInput,
            onToggleKeyboard = {
                overlay = if (overlay == Overlay.KEYBOARD) Overlay.NONE else Overlay.KEYBOARD
            },
            onToggleJoystick = {
                overlay = if (overlay == Overlay.JOYSTICK) Overlay.NONE else Overlay.JOYSTICK
            },
            onToggleMachine = {
                val next = if (machine == Coco.MACHINE_COCO3) Coco.MACHINE_COCO2 else Coco.MACHINE_COCO3
                session.setMachine(next)
                machine = session.machine
                tvInput = session.tvInput
            },
            onToggleDisplay = {
                val next = if (tvInput == Coco.TV_RGB) Coco.TV_COMPOSITE else Coco.TV_RGB
                session.setTvInput(next)
                tvInput = session.tvInput
            },
            onReset = session::reset,
            onOpenFujiNet = onOpenFujiNet,
            onShutdown = onShutdown,
        )

        if (landscape && overlay == Overlay.JOYSTICK) {
            // Landscape: flank the screen with the stick (left) and fire buttons
            // (right) so the surface fills the full height between them.
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                JoystickPad(
                    onAxis = { x, y -> session.joystick(x, y) },
                    modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 12.dp),
                )
                EmulatorSurface(
                    session = session,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                FireButtons(
                    session,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 12.dp),
                )
            }
        } else {
            // Portrait (and keyboard): the surface fills the area above a stacked
            // bottom overlay.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                EmulatorSurface(session = session, modifier = Modifier.fillMaxSize())
            }
            when (overlay) {
                Overlay.KEYBOARD -> CocoKeyboard(session = session)
                Overlay.JOYSTICK -> JoystickView(session = session)
                Overlay.NONE -> {}
            }
        }
    }
}

private enum class Overlay { NONE, KEYBOARD, JOYSTICK }

@Composable
private fun ControlBar(
    keyboardActive: Boolean,
    joystickActive: Boolean,
    machine: Int,
    tvInput: Int,
    onToggleKeyboard: () -> Unit,
    onToggleJoystick: () -> Unit,
    onToggleMachine: () -> Unit,
    onToggleDisplay: () -> Unit,
    onReset: () -> Unit,
    onOpenFujiNet: () -> Unit,
    onShutdown: () -> Unit,
) {
    // Horizontally scrollable so all controls fit even on narrow portrait screens.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BarButton("⌨", active = keyboardActive, onClick = onToggleKeyboard)
        BarButton("Joy", active = joystickActive, onClick = onToggleJoystick)
        BarButton(if (machine == Coco.MACHINE_COCO3) "CoCo 3" else "CoCo 2", onClick = onToggleMachine)
        if (machine == Coco.MACHINE_COCO3) {
            BarButton(if (tvInput == Coco.TV_RGB) "RGB" else "Comp", onClick = onToggleDisplay)
        }
        BarButton("Reset", onClick = onReset)
        BarButton("FujiNet", onClick = onOpenFujiNet)
        BarButton("Power", onClick = onShutdown)
    }
}

@Composable
private fun BarButton(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        )
    }
}
