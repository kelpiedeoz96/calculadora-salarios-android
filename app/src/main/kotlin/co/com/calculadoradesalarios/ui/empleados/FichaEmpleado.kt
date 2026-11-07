package co.com.calculadoradesalarios.ui.empleados

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.com.calculadoradesalarios.ui.comun.AvatarEmpleado
import co.com.calculadoradesalarios.ui.comun.CampoConfirmable
import co.com.calculadoradesalarios.ui.comun.FilaInterruptor

class AccionesFicha(
    val cerrar: () -> Unit = {},
    val confirmar: (CampoEmpleado, String) -> Unit = { _, _ -> },
    val diaDescanso: (Int?) -> Unit = {},
    val auxilio: (Boolean) -> Unit = {},
    val elegirFoto: () -> Unit = {},
    val quitarFoto: () -> Unit = {},
    val eliminar: () -> Unit = {},
)

/** Mismo orden que el ComboBox del escritorio: índice 0 = "Sin definir", luego domingo = 0 … sábado = 6. */
val OpcionesDescanso: List<Pair<Int?, String>> =
    listOf(null to "Sin definir") + listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado").mapIndexed { i, d -> i to d }

/** "Datos del empleado" (en el escritorio vivía dentro de Liquidación). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FichaEmpleadoContenido(ficha: FichaEmpleado, acciones: AccionesFicha, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = acciones.cerrar) { Icon(Icons.Outlined.Close, contentDescription = "Cerrar") } },
                title = { Text("Datos del empleado") },
                actions = { IconButton(onClick = acciones.eliminar) { Icon(Icons.Outlined.Delete, contentDescription = "Eliminar empleado") } },
            )
        },
    ) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AvatarEmpleado(ficha.iniciales, ficha.tono, ficha.foto, tamano = 72.dp, descripcion = "Foto de ${ficha.nombre}")
                Column {
                    Text("Foto del empleado (opcional)", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = acciones.elegirFoto) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                            Text("Subir foto…", Modifier.padding(start = 8.dp))
                        }
                        if (ficha.foto != null) TextButton(onClick = acciones.quitarFoto) { Text("Quitar") }
                    }
                }
            }
            CampoConfirmable("Nombre", ficha.nombre, { acciones.confirmar(CampoEmpleado.Nombre, it) })
            CampoConfirmable("Cédula", ficha.cedula, { acciones.confirmar(CampoEmpleado.Cedula, it) }, teclado = KeyboardType.Number)
            CampoConfirmable("Cargo", ficha.cargo, { acciones.confirmar(CampoEmpleado.Cargo, it) })
            CampoConfirmable(
                "Salario mensual (vacío = mínimo)", ficha.salarioTexto, { acciones.confirmar(CampoEmpleado.Salario, it) },
                teclado = KeyboardType.Number, marcador = ficha.placeholderSalario,
                error = ficha.errores[CampoEmpleado.Salario],
            )
            if (ficha.avisoSalario != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(ficha.avisoSalario, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            SelectorDescanso(ficha.diaDescanso, acciones.diaDescanso)
            FilaInterruptor("Recibe auxilio de transporte", ficha.auxilio, acciones.auxilio)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorDescanso(dia: Int?, onElegir: (Int?) -> Unit) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
        OutlinedTextField(
            value = OpcionesDescanso.first { it.first == dia }.second,
            onValueChange = {},
            readOnly = true,
            label = { Text("Día de descanso semanal") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            OpcionesDescanso.forEach { (valor, texto) ->
                DropdownMenuItem(text = { Text(texto) }, onClick = { onElegir(valor); abierto = false })
            }
        }
    }
}
