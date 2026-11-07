package co.com.calculadoradesalarios.ui.principal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.com.calculadoradesalarios.engine.PeriodoCalc
import co.com.calculadoradesalarios.ui.comun.DialogoFecha
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

val LocaleCO: Locale = Locale.forLanguageTag("es-CO")

fun nombreMes(mes: String): String {
    val ym = runCatching { YearMonth.parse(mes) }.getOrNull() ?: return mes
    return ym.month.getDisplayName(TextStyle.FULL_STANDALONE, LocaleCO).replaceFirstChar { it.titlecase(LocaleCO) } + " de " + ym.year
}

fun desplazarMes(mes: String, dir: Int): String =
    runCatching { YearMonth.parse(mes).plusMonths(dir.toLong()).toString() }.getOrDefault(mes)

/** Contenido del selector de periodo (el "flyout" del escritorio), dentro de una hoja inferior. */
@Composable
fun SelectorPeriodoContenido(
    estado: PrincipalEstado,
    onAplicar: (mes: String, modo: String, desde: String, hasta: String) -> String?,
    modifier: Modifier = Modifier,
) {
    var mes by rememberSaveable { mutableStateOf(estado.mes) }
    var modo by rememberSaveable { mutableStateOf(estado.modo) }
    var desde by rememberSaveable { mutableStateOf(estado.desde) }
    var hasta by rememberSaveable { mutableStateOf(estado.hasta) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var eligiendo by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Periodo", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { mes = desplazarMes(mes, -1) }) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Mes anterior") }
            Text(nombreMes(mes), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { mes = desplazarMes(mes, 1) }) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Mes siguiente") }
        }
        val opciones = listOf("q1" to "1.ª quincena", "q2" to "2.ª quincena", "custom" to "Rango")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            opciones.forEachIndexed { i, (id, texto) ->
                SegmentedButton(
                    selected = modo == id,
                    onClick = { modo = id },
                    shape = SegmentedButtonDefaults.itemShape(i, opciones.size),
                ) { Text(texto, maxLines = 1) }
            }
        }
        if (modo == "custom") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { eligiendo = "desde" }, modifier = Modifier.weight(1f)) {
                    Text("Desde: " + (PeriodoCalc.ParseFecha(desde)?.let(::dmy) ?: "—"))
                }
                OutlinedButton(onClick = { eligiendo = "hasta" }, modifier = Modifier.weight(1f)) {
                    Text("Hasta: " + (PeriodoCalc.ParseFecha(hasta)?.let(::dmy) ?: "—"))
                }
            }
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { error = onAplicar(mes, modo, desde, hasta) }, modifier = Modifier.fillMaxWidth()) { Text("Aplicar") }
    }

    eligiendo?.let { cual ->
        val inicial = PeriodoCalc.ParseFecha(if (cual == "desde") desde else hasta)
            ?: runCatching { YearMonth.parse(mes).atDay(1) }.getOrElse { LocalDate.now() }
        DialogoFecha(
            inicial = inicial,
            onAceptar = { f -> if (cual == "desde") desde = f.toString() else hasta = f.toString(); eligiendo = null },
            onCancelar = { eligiendo = null },
        )
    }
}

private fun dmy(d: LocalDate) = "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)
