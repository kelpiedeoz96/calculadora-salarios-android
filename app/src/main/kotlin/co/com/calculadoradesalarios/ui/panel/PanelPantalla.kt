package co.com.calculadoradesalarios.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.ui.comun.AvatarEmpleado
import co.com.calculadoradesalarios.ui.comun.EstadoVacio
import co.com.calculadoradesalarios.ui.comun.Seccion
import co.com.calculadoradesalarios.ui.tema.colorCategoria

class AccionesPanel(val abrirEmpleado: (String) -> Unit = {}, val irAEmpleados: () -> Unit = {})

@Composable
fun PanelContenido(e: PanelEstado, acciones: AccionesPanel, modifier: Modifier = Modifier) {
    if (!e.cargando && e.empleados == "0") {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EstadoVacio(Icons.Outlined.Dashboard, "Panel", "Agrega empleados para empezar.", boton = "Ir a Empleados", onBoton = acciones.irAEmpleados)
        }
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Indicador("Empleados", e.empleados, "activos en nómina", Modifier.weight(1f))
                    Indicador("Horas totales", e.horasTotales, "en el periodo", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Indicador("Recargos + extras", e.recargos, "a pagar en la quincena", Modifier.weight(1f), destacado = true)
                    Indicador("Horas extra", e.horasExtra, e.horasExtraPct, Modifier.weight(1f))
                }
            }
        }
        item {
            Seccion("Horas por categoría") {
                if (e.categorias.isEmpty()) Text("Aún no hay horas registradas en este periodo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.categorias.forEach { BarraHoras(it) }
            }
        }
        item {
            Seccion("Top empleados por recargos") {
                if (e.top.isEmpty()) Text("Aún no hay horas registradas en este periodo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.top.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AvatarEmpleado(t.iniciales, t.tono, t.foto, tamano = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(t.nombre, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { acciones.abrirEmpleado(t.id) }) { Text("Abrir") }
                    }
                }
            }
        }
        item { Alertas(e.alertas) }
        item {
            Seccion("Distribución de horas") {
                Linea("Horas ordinarias", e.ordinarias)
                Linea("Horas nocturnas", e.nocturnas)
                Linea("Horas dominicales / festivas", e.dominicales)
                Linea("Horas extra", e.extra)
            }
        }
        item {
            Seccion("Resumen económico") {
                Linea("Total recargos + extras", e.totalRecargos)
                Linea("Empleados con horas extra", e.conExtra)
                Linea("Promedio por empleado", e.promedio)
                Linea("Total a pagar", e.totalPagar, negrita = true)
            }
        }
    }
}

@Composable
private fun Indicador(titulo: String, valor: String, detalle: String, modifier: Modifier, destacado: Boolean = false) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (destacado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Text(titulo.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(valor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun BarraHoras(b: BarraCategoria) {
    val c = colorCategoria(b.cat)
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${b.etiqueta}: ${b.horas} horas" }) {
        Row {
            Text(b.etiqueta, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(b.horas, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.fondo)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(b.fraccion.coerceIn(0f, 1f)).clip(RoundedCornerShape(5.dp)).background(c.texto))
        }
    }
}

@Composable
private fun Alertas(alertas: List<String>) {
    if (alertas.isEmpty()) {
        Seccion("Alertas y avisos") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colorCategoria(Cat.ODiu).texto)
                Text("Sin alertas. Todo en orden.")
            }
        }
        return
    }
    val c = colorCategoria(Cat.HED)
    Card(colors = CardDefaults.cardColors(containerColor = c.fondo, contentColor = c.texto)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = c.texto)
                Text("Alertas y avisos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = c.texto)
            }
            alertas.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = c.texto) }
        }
    }
}

@Composable
private fun Linea(etiqueta: String, valor: String, negrita: Boolean = false) {
    Row {
        Text(etiqueta, Modifier.weight(1f), fontWeight = if (negrita) FontWeight.SemiBold else null)
        Text(valor, fontWeight = if (negrita) FontWeight.SemiBold else FontWeight.Medium)
    }
}
