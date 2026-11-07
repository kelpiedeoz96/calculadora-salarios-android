package co.com.calculadoradesalarios.ui.calendario

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.ui.comun.AvatarEmpleado
import co.com.calculadoradesalarios.ui.comun.EstadoVacio
import co.com.calculadoradesalarios.ui.principal.nombreMes
import co.com.calculadoradesalarios.ui.tema.colorCategoria

class AccionesCalendario(val seleccionar: (String) -> Unit = {}, val verDia: (String) -> Unit = {})

private val DiasSemana = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")

@Composable
fun CalendarioContenido(e: CalendarioEstado, acciones: AccionesCalendario, modifier: Modifier = Modifier) {
    if (!e.cargando && !e.hayEmpleado) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EstadoVacio(Icons.Outlined.CalendarMonth, "Sin empleados", "Agrega un empleado para ver su calendario.")
        }
        return
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(e.empleados, key = { it.id }) { emp ->
                FilterChip(
                    selected = emp.seleccionado,
                    onClick = { acciones.seleccionar(emp.id) },
                    leadingIcon = { AvatarEmpleado(emp.iniciales, emp.tono, emp.foto, tamano = 24.dp) },
                    label = { Text(emp.nombre, maxLines = 1) },
                )
            }
        }
        Column {
            Text(nombreMes(e.mes), style = MaterialTheme.typography.titleLarge)
            Text(e.titulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DiasSemana.forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val casillas: List<CeldaDia?> = List(e.relleno) { null } + e.celdas
            casillas.chunked(7).forEach { semana ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    semana.forEach { c ->
                        Box(Modifier.weight(1f)) { if (c != null) Celda(c) { acciones.verDia(c.clave) } }
                    }
                    repeat(7 - semana.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Leyenda()
        Text("Toca un día para ver solo ese día en Liquidación.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Celda(c: CeldaDia, onTocar: () -> Unit) {
    val dom = colorCategoria(Cat.ODom)
    val extra = colorCategoria(Cat.HED).texto
    val forma = RoundedCornerShape(10.dp)
    val fondo = if (c.dominical || c.festivo) dom.fondo else MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        Modifier
            .aspectRatio(0.78f)
            .alpha(if (c.fuera) 0.45f else 1f)
            .clip(forma)
            .background(fondo)
            .then(if (c.hoy) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), forma) else Modifier)
            .clickable(onClickLabel = "Ver solo este día en Liquidación", role = Role.Button, onClick = onTocar)
            .semantics { contentDescription = c.detalle }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            c.dia.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (c.hoy) FontWeight.Bold else FontWeight.Medium,
            color = if (c.dominical || c.festivo) dom.texto else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (c.horas.isNotEmpty()) Text(c.horas, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            if (c.fraccion > 0) {
                Box(
                    Modifier.fillMaxWidth(c.fraccion.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (c.barraExtra) extra else MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun Leyenda() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ItemLeyenda(MaterialTheme.colorScheme.primary, "Día trabajado")
        ItemLeyenda(colorCategoria(Cat.HED).texto, "Con horas extra")
        ItemLeyenda(colorCategoria(Cat.ODom).fondo, "Dominical / festivo / descanso", borde = colorCategoria(Cat.ODom).texto)
    }
}

@Composable
private fun ItemLeyenda(color: Color, texto: String, borde: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(color)
                .then(if (borde != null) Modifier.border(1.dp, borde, RoundedCornerShape(4.dp)) else Modifier),
        )
        Text(texto, style = MaterialTheme.typography.bodySmall)
    }
}
