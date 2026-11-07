package co.com.calculadoradesalarios.ui.liquidacion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.WeekGaugeData
import co.com.calculadoradesalarios.ui.comun.AvatarEmpleado
import co.com.calculadoradesalarios.ui.comun.EstadoVacio
import co.com.calculadoradesalarios.ui.comun.FilaInterruptor
import co.com.calculadoradesalarios.ui.comun.Seccion
import co.com.calculadoradesalarios.ui.empleados.ChipCategoria
import co.com.calculadoradesalarios.ui.tema.colorCategoria

class AccionesLiquidacion(
    val seleccionar: (String) -> Unit = {},
    val inclSueldo: (Boolean) -> Unit = {},
    val abrirDia: (String) -> Unit = {},
    val limpiarPeriodo: () -> Unit = {},
    val agregarEmpleado: () -> Unit = {},
)

@Composable
fun LiquidacionContenido(
    estado: LiquidacionEstado,
    acciones: AccionesLiquidacion,
    textoConfirmarLimpiar: String,
    modifier: Modifier = Modifier,
) {
    if (!estado.cargando && !estado.hayEmpleados) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EstadoVacio(
                Icons.AutoMirrored.Outlined.ReceiptLong, "Sin empleados", "Agrega un empleado para comenzar.",
                boton = "Añadir empleado", onBoton = acciones.agregarEmpleado,
            )
        }
        return
    }
    var confirmarLimpiar by rememberSaveable { mutableStateOf(false) }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SelectorEmpleado(estado.chips, acciones.seleccionar) }
        item { Resumen(estado) }
        if (estado.alertas.isNotEmpty()) item { Alertas(estado.alertas) }
        item { Semanas(estado) }
        item {
            Column {
                Text("Registro de jornada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Toca un día para registrar sus horas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(estado.dias, key = { it.clave }) { d -> TarjetaDia(d) { acciones.abrirDia(d.clave) } }
        item {
            OutlinedButton(onClick = { confirmarLimpiar = true }) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                Text("Limpiar horas del periodo", Modifier.padding(start = 8.dp))
            }
        }
        item { Auditoria(estado) }
        item { Liquidacion(estado, acciones.inclSueldo) }
    }

    if (confirmarLimpiar) {
        AlertDialog(
            onDismissRequest = { confirmarLimpiar = false },
            title = { Text("Limpiar horas") },
            text = { Text(textoConfirmarLimpiar) },
            confirmButton = {
                TextButton(onClick = { confirmarLimpiar = false; acciones.limpiarPeriodo() }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarLimpiar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SelectorEmpleado(chips: List<ChipEmpleadoLiq>, onElegir: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips, key = { it.id }) { c ->
            FilterChip(
                selected = c.seleccionado,
                onClick = { onElegir(c.id) },
                leadingIcon = { AvatarEmpleado(c.iniciales, c.tono, c.foto, tamano = 24.dp) },
                label = {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(c.nombre, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        Text(c.pago, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        }
    }
}

@Composable
private fun Resumen(e: LiquidacionEstado) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(e.empleadoNombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Recargos y horas extra", style = MaterialTheme.typography.labelMedium)
                    Text(e.totalRecargos, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Horas", style = MaterialTheme.typography.labelMedium)
                    Text(e.totalHoras, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (e.inclSueldo) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Neto a pagar", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(e.nomina.neto, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(e.valorHora, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Avisos del motor (sección 22): informativos, no cambian el cálculo. Ámbar, como WarnBrush del escritorio. */
@Composable
private fun Alertas(alertas: List<String>) {
    val c = colorCategoria(Cat.HED)
    Card(colors = CardDefaults.cardColors(containerColor = c.fondo, contentColor = c.texto)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = c.texto)
                Text("Avisos", style = MaterialTheme.typography.titleSmall, color = c.texto, fontWeight = FontWeight.SemiBold)
            }
            alertas.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = c.texto) }
            Text("Son informativos: no cambian el cálculo.", style = MaterialTheme.typography.bodySmall, color = c.texto)
        }
    }
}

@Composable
private fun Semanas(e: LiquidacionEstado) = Seccion("Semanas") {
    e.semanas.forEach { s ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(s.rango, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(s.horas, style = MaterialTheme.typography.bodyMedium)
            }
            MedidorSemana(s.medidor, Modifier.fillMaxWidth().semantics { contentDescription = "Semana ${s.rango}: ${s.horas}" })
        }
    }
    Text(e.textoTope, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** WeekGauge del escritorio: ordinarias, extra y la marca del tope semanal. */
@Composable
fun MedidorSemana(m: WeekGaugeData, modifier: Modifier = Modifier) {
    val pista = MaterialTheme.colorScheme.surfaceContainerHighest
    val ordinarias = MaterialTheme.colorScheme.primary
    val extra = colorCategoria(Cat.HED).texto
    val marca = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.height(16.dp)) {
        val w = size.width
        val r = CornerRadius(5.dp.toPx())
        val y = 3.dp.toPx()
        val h = 10.dp.toPx()
        drawRoundRect(pista, Offset(0f, y), Size(w, h), r)
        val ordW = (m.ordRatio * w).toFloat().coerceIn(0f, w)
        if (ordW > 0) drawRoundRect(ordinarias, Offset(0f, y), Size(ordW, h), r)
        val extW = (m.extraRatio * w).toFloat().coerceIn(0f, w - ordW)
        if (extW > 0) drawRoundRect(extra, Offset(ordW, y), Size(extW, h), r)
        val markX = (m.capRatio * w).toFloat().coerceIn(0f, w - 2.dp.toPx())
        drawRoundRect(marca, Offset(markX, 0f), Size(2.dp.toPx(), size.height), CornerRadius(1.dp.toPx()))
    }
}

@Composable
private fun Distintivo(texto: String, fondo: androidx.compose.ui.graphics.Color, color: androidx.compose.ui.graphics.Color) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(fondo).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun DistintivosDia(d: DiaFila) {
    val dom = colorCategoria(Cat.ODom)
    if (d.festivo) Distintivo("festivo", dom.fondo, dom.texto)
    else if (d.dominical && !d.descanso) Distintivo("domingo", dom.fondo, dom.texto)
    if (d.estado.isNotEmpty()) {
        val c = if (d.trabajoDescanso) colorCategoria(Cat.HED) else colorCategoria(Cat.ODiu)
        Distintivo(d.estado, c.fondo, c.texto)
    }
    if (!d.enPeriodo) Distintivo("fuera del periodo", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TarjetaDia(d: DiaFila, onAbrir: () -> Unit) {
    val turnos = listOf(d.in1 to d.out1, d.in2 to d.out2).filter { it.first.isNotEmpty() || it.second.isNotEmpty() }
        .joinToString("  ·  ") { "${it.first.ifEmpty { "?" }} – ${it.second.ifEmpty { "?" }}" }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().alpha(if (d.enPeriodo) 1f else 0.6f)
            .clickable(onClickLabel = "Registrar horas del ${d.etiqueta}", role = Role.Button, onClick = onAbrir),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.etiqueta, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp))
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) { DistintivosDia(d) }
                Column(horizontalAlignment = Alignment.End) {
                    if (d.monto.isNotEmpty()) Text(d.monto, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (d.horas.isNotEmpty()) Text("${d.horas} h", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (turnos.isNotEmpty() || d.descansoMin.isNotEmpty()) {
                Text(
                    turnos + if (d.descansoMin.isNotEmpty()) "  ·  descanso ${d.descansoMin} min" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (d.estado.isEmpty()) {
                Text("Sin registro", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (d.categorias.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.categorias.forEach { (c, t) -> ChipCategoria(c, "${Engine.CatShort.getValue(c)} $t") }
                }
            }
        }
    }
}

@Composable
private fun Auditoria(e: LiquidacionEstado) = Seccion("Auditoría") {
    Text(e.valorHora, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    e.auditoria.forEach { f ->
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(colorCategoria(f.cat).texto))
            Column(Modifier.weight(1f)) {
                Text(f.concepto, style = MaterialTheme.typography.bodyMedium)
                Text("${f.horas} h · ${f.valorHora}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(f.total, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (f.porcentaje.isNotEmpty()) Text(f.porcentaje, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider()
    Row {
        Text("Total", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text("${e.totalHoras} h", Modifier.padding(end = 16.dp))
        Text(e.totalRecargos, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun Liquidacion(e: LiquidacionEstado, onIncl: (Boolean) -> Unit) = Seccion("Liquidación") {
    Text(e.periodo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FilaInterruptor("Incluir sueldo, auxilio y descuentos", e.inclSueldo, onIncl)
    if (e.inclSueldo) {
        val n = e.nomina
        Linea("Salario básico quincenal", n.basico)
        Linea("Auxilio de transporte", n.auxilio)
        Linea("Recargos y horas extra", n.recargos)
        Linea("Total devengado", n.devengado, negrita = true)
        Linea(n.saludEtiqueta, n.salud)
        Linea(n.pensionEtiqueta, n.pension)
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Neto a pagar", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(n.neto, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Text(n.nota, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(e.totalRecargos, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text("Solo recargos y horas extra. El sueldo básico se paga aparte.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(0.dp))
}

@Composable
private fun Linea(etiqueta: String, valor: String, negrita: Boolean = false) {
    Row {
        Text(etiqueta, Modifier.weight(1f), fontWeight = if (negrita) FontWeight.SemiBold else null)
        Text(valor, fontWeight = if (negrita) FontWeight.SemiBold else null)
    }
}
