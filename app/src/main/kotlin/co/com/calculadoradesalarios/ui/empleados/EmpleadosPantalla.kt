package co.com.calculadoradesalarios.ui.empleados

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.ui.comun.AvatarEmpleado
import co.com.calculadoradesalarios.ui.comun.EstadoVacio
import co.com.calculadoradesalarios.ui.comun.FilaDeslizable
import co.com.calculadoradesalarios.ui.comun.FilaInterruptor
import co.com.calculadoradesalarios.ui.tema.colorCategoria

class AccionesEmpleados(
    val buscar: (String) -> Unit = {},
    val inclSueldo: (Boolean) -> Unit = {},
    val abrir: (String) -> Unit = {},
    val agregar: () -> Unit = {},
    val eliminar: (String) -> Unit = {},
)

@Composable
fun EmpleadosContenido(estado: EmpleadosEstado, acciones: AccionesEmpleados, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        if (!estado.cargando && !estado.hayEmpleados) {
            EstadoVacio(
                Icons.Outlined.Groups, "Sin empleados", "Agrega un empleado para comenzar.",
                modifier = Modifier.align(Alignment.Center), boton = "Añadir empleado", onBoton = acciones.agregar,
            )
            return@Box
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = estado.busqueda,
                    onValueChange = acciones.buscar,
                    placeholder = { Text("Buscar por nombre, cédula o cargo…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { FilaInterruptor("Incluir sueldo, auxilio y descuentos", estado.inclSueldo, acciones.inclSueldo) }
            item { TarjetaTotales(estado) }
            if (estado.filas.isEmpty()) {
                item { EstadoVacio(Icons.Outlined.SearchOff, "Sin coincidencias", "Prueba con otro nombre, cédula o cargo.") }
            }
            items(estado.filas, key = { it.id }) { f ->
                val forma = RoundedCornerShape(16.dp)
                FilaDeslizable(onBorrar = { acciones.eliminar(f.id) }, forma = forma, descripcionBorrar = "Eliminar a ${f.nombre}") {
                    TarjetaEmpleado(f, estado.inclSueldo, forma) { acciones.abrir(f.id) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = acciones.agregar,
            icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
            text = { Text("Añadir empleado") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun TarjetaTotales(estado: EmpleadosEstado) {
    val t = estado.totales
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Total · ${estado.filas.size} empleados", style = MaterialTheme.typography.labelLarge)
                    Text(if (estado.inclSueldo) t.neto else t.recargos, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(if (estado.inclSueldo) "Neto a pagar" else "Recargos del periodo", style = MaterialTheme.typography.bodySmall)
                }
                Dato("Horas", t.horas, alinear = TextAlign.End)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Dato("Nocturnas", t.nocturnas, Modifier.weight(1f))
                Dato("Dom/Fest", t.domFest, Modifier.weight(1f))
                Dato("Extra", t.extra, Modifier.weight(1f))
            }
            if (estado.inclSueldo) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Dato("Básico", t.basico, Modifier.weight(1f))
                    Dato("Recargos", t.recargos, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Dato("Auxilio", t.auxilio, Modifier.weight(1f))
                    Dato("Descuentos", t.descuentos, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier, alinear: TextAlign = TextAlign.Start) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = alinear)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = alinear, maxLines = 1)
    }
}

/** Chip con los colores de la categoría de recargo. */
@Composable
fun ChipCategoria(cat: Cat, texto: String, modifier: Modifier = Modifier) {
    val c = colorCategoria(cat)
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        color = c.texto,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.fondo)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun TarjetaEmpleado(f: EmpleadoFila, inclSueldo: Boolean, forma: androidx.compose.ui.graphics.Shape, onAbrir: () -> Unit) {
    Card(
        shape = forma,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Abrir ficha de ${f.nombre}", role = Role.Button, onClick = onAbrir),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarEmpleado(f.iniciales, f.tono, f.foto, descripcion = "Foto de ${f.nombre}")
                Column(Modifier.weight(1f)) {
                    Text(f.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (f.cargo.isNotBlank()) Text(f.cargo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (inclSueldo) f.neto else f.recargos, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (inclSueldo) "Neto" else "Recargos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${f.horas} h", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp))
                ChipCategoria(Cat.ONoc, "Noct. ${f.nocturnas}")
                ChipCategoria(Cat.ODom, "Dom. ${f.domFest}")
                ChipCategoria(Cat.HED, "Extra ${f.extra}")
            }
        }
    }
}
