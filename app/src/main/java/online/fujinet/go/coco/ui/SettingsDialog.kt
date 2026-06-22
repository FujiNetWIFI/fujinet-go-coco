package online.fujinet.go.coco.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import online.fujinet.go.coco.input.Coco

private val MACHINE_LABELS = listOf("CoCo 2" to Coco.MACHINE_COCO2, "CoCo 3" to Coco.MACHINE_COCO3)

// TV input / artifact mode. RGB is CoCo 3 only (filtered out for the CoCo 2).
private val DISPLAY_LABELS = listOf(
    "S-Video (no artifacts)" to Coco.TV_SVIDEO,
    "Composite (blue-red)" to Coco.TV_COMPOSITE_BR,
    "Composite (red-blue)" to Coco.TV_COMPOSITE_RB,
    "RGB" to Coco.TV_RGB,
)

// Composite cross-colour (artifact) renderer.
private val CCR_LABELS = listOf(
    "None" to Coco.CCR_NONE,
    "Simple (2-bit)" to Coco.CCR_SIMPLE,
    "5-bit" to Coco.CCR_5BIT,
    "Partial NTSC" to Coco.CCR_PARTIAL,
    "Simulated" to Coco.CCR_SIMULATED,
)

/**
 * Machine + artifact settings. Switching the machine (CoCo 2 <-> CoCo 3) restarts
 * the emulated machine in place (FujiNet stays connected, HDB-DOS reboots into
 * CONFIG). The Display row picks the video signal / artifact phase (S-Video has
 * no artifact colours; the two composite phases produce them; RGB is the CoCo 3's
 * digital output). The Artifact row picks the composite cross-colour renderer.
 * A Reset button hard-resets the current machine.
 */
@Composable
fun SettingsDialog(
    machine: Int,
    tvInput: Int,
    ccr: Int,
    onApply: (machine: Int, tvInput: Int, ccr: Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draftMachine by remember { mutableIntStateOf(machine) }
    var draftTv by remember { mutableIntStateOf(tvInput) }
    var draftCcr by remember { mutableIntStateOf(ccr) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (draftMachine != machine || draftTv != tvInput || draftCcr != ccr) {
                    onApply(draftMachine, draftTv, draftCcr)
                }
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OptionRow("Machine", MACHINE_LABELS, draftMachine) {
                    draftMachine = it
                    // RGB is CoCo 3 only; fall back when switching to the CoCo 2.
                    if (it != Coco.MACHINE_COCO3 && draftTv == Coco.TV_RGB) {
                        draftTv = Coco.TV_COMPOSITE_BR
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Artifact / video", style = MaterialTheme.typography.titleSmall)

                // RGB only applies to the CoCo 3.
                val displayOptions =
                    if (draftMachine == Coco.MACHINE_COCO3) DISPLAY_LABELS
                    else DISPLAY_LABELS.filter { it.second != Coco.TV_RGB }
                OptionRow("Display", displayOptions, draftTv) { draftTv = it }
                OptionRow("Artifact", CCR_LABELS, draftCcr) { draftCcr = it }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedButton(
                    onClick = { onReset(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reset") }
            }
        },
    )
}

@Composable
private fun OptionRow(
    label: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.second == selected }?.first ?: options.first().first
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedLabel, textAlign = TextAlign.End)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (optLabel, optValue) ->
                    DropdownMenuItem(
                        text = { Text(optLabel) },
                        onClick = { onSelect(optValue); expanded = false },
                    )
                }
            }
        }
    }
}
