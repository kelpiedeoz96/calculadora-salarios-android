package co.com.calculadoradesalarios.ui.liquidacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.ui.comun.CampoConfirmable
import co.com.calculadoradesalarios.ui.comun.DialogoHora
import co.com.calculadoradesalarios.ui.comun.FilaInterruptor
import co.com.calculadoradesalarios.ui.empleados.ChipCategoria

class AccionesDia(
    val confirmar: (CampoDia, String) -> Unit = { _, _ -> },
    val descanso: (Boolean) -> Unit = {},
    val turno: (String) -> Unit = {},
    val limpiar: () -> Unit = {},
)

/** Registro de un día (una fila de la tabla del escritorio), en una hoja inferior. */
@Composable
fun EditorDiaContenido(d: DiaFila, turnos: List<String>, errores: Map<CampoDia, String>, acciones: AccionesDia, modifier: Modifier = Modifier) {
    var reloj by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(d.etiqueta, style = MaterialTheme.typography.titleLarge)
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) { DistintivosDia(d) }
        }
        FilaInterruptor("Día de descanso", d.descanso, acciones.descanso)
        if (turnos.isNotEmpty()) {
            Text("Turnos rápidos", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                turnos.forEach { t -> AssistChip(onClick = { acciones.turno(t) }, label = { Text(t.replace("-", " – ")) }) }
            }
        }
        val valores = mapOf(CampoDia.Entrada1 to d.in1, CampoDia.Salida1 to d.out1, CampoDia.Entrada2 to d.in2, CampoDia.Salida2 to d.out2)
        listOf(CampoDia.Entrada1 to CampoDia.Salida1, CampoDia.Entrada2 to CampoDia.Salida2).forEach { (a, b) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(a, b).forEach { campo ->
                    CampoConfirmable(
                        etiqueta = campo.etiqueta,
                        valor = valores.getValue(campo),
                        onConfirmar = { acciones.confirmar(campo, it) },
                        error = errores[campo],
                        teclado = KeyboardType.Number,
                        marcador = "08:00",
                        modifier = Modifier.weight(1f),
                        iconoFinal = {
                            IconButton(onClick = { reloj = campo.name }) { Icon(Icons.Outlined.Schedule, contentDescription = "Elegir ${campo.etiqueta.lowercase()}") }
                        },
                    )
                }
            }
        }
        CampoConfirmable(
            etiqueta = CampoDia.Descanso.etiqueta,
            valor = d.descansoMin,
            onConfirmar = { acciones.confirmar(CampoDia.Descanso, it) },
            error = errores[CampoDia.Descanso],
            teclado = KeyboardType.Number,
            ayuda = "Minutos de pausa que no se pagan; se descuentan primero de lo diurno.",
        )
        Text("Escribe la hora como 8:30, 0830 o 14.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (d.horas.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${d.horas} h", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(d.monto.ifEmpty { "$ 0" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.categorias.forEach { (c, t) -> ChipCategoria(c, "${Engine.CatShort.getValue(c)} $t") }
            }
        }
        TextButton(onClick = acciones.limpiar) { Text("Limpiar día") }
    }
    reloj?.let { nombre ->
        val campo = CampoDia.valueOf(nombre)
        DialogoHora(campo.etiqueta, valores(d)[campo], onAceptar = { acciones.confirmar(campo, it); reloj = null }, onCancelar = { reloj = null })
    }
}

private fun valores(d: DiaFila) = mapOf(CampoDia.Entrada1 to d.in1, CampoDia.Salida1 to d.out1, CampoDia.Entrada2 to d.in2, CampoDia.Salida2 to d.out2)
