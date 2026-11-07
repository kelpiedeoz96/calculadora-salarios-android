package co.com.calculadoradesalarios.ui.comun

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun LocalDate.aMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun Long.aFechaUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** "HH:MM" -> (hora, minuto); si no es válida, (8, 0). */
fun partirHora(hhmm: String?): Pair<Int, Int> {
    val p = hhmm?.split(':')
    val h = p?.getOrNull(0)?.toIntOrNull()
    val m = p?.getOrNull(1)?.toIntOrNull()
    return if (h != null && m != null && h in 0..23 && m in 0..59) h to m else 8 to 0
}

fun unirHora(h: Int, m: Int) = "%02d:%02d".format(h, m)

/** Selector de hora de Material 3 en formato 24 h, con teclado numérico. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoHora(titulo: String, inicial: String?, onAceptar: (String) -> Unit, onCancelar: () -> Unit) {
    val (h, m) = partirHora(inicial)
    val estado = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { TimeInput(estado) },
        confirmButton = { TextButton(onClick = { onAceptar(unirHora(estado.hour, estado.minute)) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

/** Dos horas en un mismo diálogo (turno rápido entrada–salida). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoTurno(onAceptar: (String, String) -> Unit, onCancelar: () -> Unit) {
    val entrada = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
    val salida = rememberTimePickerState(initialHour = 17, initialMinute = 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Nuevo turno rápido") },
        text = {
            Column {
                Text("Entrada", style = MaterialTheme.typography.labelLarge)
                TimeInput(entrada)
                Spacer(Modifier.height(8.dp))
                Text("Salida", style = MaterialTheme.typography.labelLarge)
                TimeInput(salida)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAceptar(unirHora(entrada.hour, entrada.minute), unirHora(salida.hour, salida.minute)) }) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoFecha(inicial: LocalDate?, onAceptar: (LocalDate) -> Unit, onCancelar: () -> Unit) {
    val estado = rememberDatePickerState(initialSelectedDateMillis = inicial?.aMillisUtc())
    DatePickerDialog(
        onDismissRequest = onCancelar,
        confirmButton = {
            TextButton(onClick = { estado.selectedDateMillis?.let { onAceptar(it.aFechaUtc()) } ?: onCancelar() }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    ) { DatePicker(estado) }
}
