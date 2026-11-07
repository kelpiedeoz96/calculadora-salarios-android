package co.com.calculadoradesalarios.ui.ajustes

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.com.calculadoradesalarios.engine.PeriodoCalc
import co.com.calculadoradesalarios.ui.comun.CampoConfirmable
import co.com.calculadoradesalarios.ui.comun.DialogoFecha
import co.com.calculadoradesalarios.ui.comun.DialogoHora
import co.com.calculadoradesalarios.ui.comun.DialogoTurno
import co.com.calculadoradesalarios.ui.comun.FilaDeslizable
import co.com.calculadoradesalarios.ui.comun.FilaInterruptor
import co.com.calculadoradesalarios.ui.comun.Seccion
import co.com.calculadoradesalarios.ui.comun.imagenGuardada
import co.com.calculadoradesalarios.ui.principal.LocaleCO
import java.time.LocalDate
import java.time.format.TextStyle

/** Acciones de la pantalla. Las que abren selectores del sistema las resuelve AjustesRuta. */
class AccionesAjustes(
    val confirmar: (CampoAjuste, String) -> Unit = { _, _ -> },
    val interruptor: (Interruptor, Boolean) -> Unit = { _, _ -> },
    val baseDescuentos: (conRecargos: Boolean) -> Unit = {},
    val tema: (String) -> Unit = {},
    val agregarTurno: (String, String) -> Unit = { _, _ -> },
    val quitarTurno: (Int) -> Unit = {},
    val agregarFestivo: (LocalDate) -> Unit = {},
    val quitarFestivo: (String) -> Unit = {},
    val agregarFestivosAnio: (String) -> Unit = {},
    val elegirLogo: () -> Unit = {},
    val quitarLogo: () -> Unit = {},
    val respaldarAhora: () -> Unit = {},
    val restaurar: () -> Unit = {},
    val elegirCarpetaExtra: () -> Unit = {},
    val quitarCarpetaExtra: () -> Unit = {},
)

private val Grupos = listOf(
    "Jornada" to listOf(CampoAjuste.TopeSemanal, CampoAjuste.JornadaDiariaRef, CampoAjuste.NocturnaDesde, CampoAjuste.NocturnaHasta),
    "Minutos" to listOf(CampoAjuste.MinutosMinimos),
    "Recargos (%)" to listOf(CampoAjuste.RecargoNocturno, CampoAjuste.ExtraDiurna, CampoAjuste.ExtraNocturna, CampoAjuste.Dominical, CampoAjuste.Divisor),
    "Sueldo y descuentos" to listOf(CampoAjuste.Smmlv, CampoAjuste.Auxilio, CampoAjuste.SaludPct, CampoAjuste.PensionPct),
)

private val InterruptoresDe = mapOf(
    "Jornada" to listOf(Interruptor.DailyCapOn),
    "Minutos" to listOf(Interruptor.MinuteRuleOn),
    "Recargos (%)" to listOf(Interruptor.DomAutoOn, Interruptor.RateRound, Interruptor.DomOrdFull),
)

@Composable
fun AjustesContenido(
    estado: AjustesEstado,
    acciones: AccionesAjustes,
    anioSugerido: String,
    modifier: Modifier = Modifier,
) {
    var dialogo by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SeccionApariencia(estado.tema, acciones.tema) }
        item { SeccionEmpresa(estado, acciones) }
        Grupos.forEach { (titulo, campos) ->
            item(key = titulo) {
                Seccion(titulo) {
                    if (titulo == "Minutos") InterruptoresDe[titulo]!!.forEach { FilaDe(it, estado, acciones) }
                    campos.forEach { campo ->
                        if (campo.tipo == TipoCampo.Hora) CampoHora(campo, estado) { dialogo = campo.name }
                        else Campo(campo, estado, acciones)
                    }
                    if (titulo != "Minutos") InterruptoresDe[titulo]?.forEach { FilaDe(it, estado, acciones) }
                    if (titulo == "Sueldo y descuentos") BaseDescuentos(estado.deductBase, acciones.baseDescuentos)
                }
            }
        }
        item { SeccionTurnos(estado.turnos, acciones) { dialogo = "turno" } }
        item { SeccionFestivos(estado.festivos, acciones, anioSugerido) { dialogo = "festivo" } }
        item { SeccionRespaldos(estado, acciones) }
    }

    when (val d = dialogo) {
        null -> {}
        "turno" -> DialogoTurno(onAceptar = { a, b -> acciones.agregarTurno(a, b); dialogo = null }, onCancelar = { dialogo = null })
        "festivo" -> DialogoFecha(inicial = null, onAceptar = { acciones.agregarFestivo(it); dialogo = null }, onCancelar = { dialogo = null })
        else -> {
            val campo = CampoAjuste.valueOf(d)
            DialogoHora(campo.etiqueta, estado.valores[campo], onAceptar = { acciones.confirmar(campo, it); dialogo = null }, onCancelar = { dialogo = null })
        }
    }
}

@Composable
private fun Campo(campo: CampoAjuste, estado: AjustesEstado, acciones: AccionesAjustes) {
    CampoConfirmable(
        etiqueta = campo.etiqueta,
        valor = estado.valores[campo].orEmpty(),
        error = estado.errores[campo],
        teclado = if (campo.tipo == TipoCampo.Numero) KeyboardType.Decimal else KeyboardType.Text,
        onConfirmar = { acciones.confirmar(campo, it) },
    )
}

/** Hora de solo lectura: al tocarla se abre el selector de hora de Material 3. */
@Composable
private fun CampoHora(campo: CampoAjuste, estado: AjustesEstado, onAbrir: () -> Unit) {
    Box {
        OutlinedTextField(
            value = estado.valores[campo].orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(campo.etiqueta) },
            trailingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            isError = estado.errores[campo] != null,
            supportingText = estado.errores[campo]?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(onClickLabel = "Cambiar ${campo.etiqueta.lowercase()}", role = Role.Button, onClick = onAbrir))
    }
}

@Composable
private fun FilaDe(i: Interruptor, estado: AjustesEstado, acciones: AccionesAjustes) =
    FilaInterruptor(i.etiqueta, estado.interruptores[i] == true, { acciones.interruptor(i, it) }, i.ayuda)

@Composable
private fun <T> Segmentos(opciones: List<Pair<T, String>>, seleccion: T, onElegir: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        opciones.forEachIndexed { i, (valor, texto) ->
            SegmentedButton(
                selected = valor == seleccion,
                onClick = { onElegir(valor) },
                shape = SegmentedButtonDefaults.itemShape(i, opciones.size),
            ) { Text(texto, maxLines = 1) }
        }
    }
}

@Composable
private fun SeccionApariencia(tema: String, onTema: (String) -> Unit) = Seccion("Apariencia") {
    Text("Tema", style = MaterialTheme.typography.labelLarge)
    Segmentos(listOf("system" to "Sistema", "light" to "Claro", "dark" to "Oscuro"), tema, onTema)
}

@Composable
private fun BaseDescuentos(base: String, onCambio: (Boolean) -> Unit) {
    Text("Descuentos sobre", style = MaterialTheme.typography.labelLarge)
    Segmentos(listOf(false to "Solo salario básico", true to "Básico + recargos"), base == "con_recargos", onCambio)
}

@Composable
private fun SeccionEmpresa(estado: AjustesEstado, acciones: AccionesAjustes) = Seccion("Datos de la empresa") {
    Campo(CampoAjuste.RazonSocial, estado, acciones)
    Campo(CampoAjuste.Nit, estado, acciones)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val logo = imagenGuardada(estado.logo)
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) Image(logo, contentDescription = "Logo de la empresa", modifier = Modifier.size(56.dp))
            else Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (estado.logo != null) "Logo personalizado" else "Sin logo", Modifier.weight(1f))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = acciones.elegirLogo) { Text("Elegir logo…") }
        if (estado.logo != null) TextButton(onClick = acciones.quitarLogo) { Text("Quitar logo personalizado") }
    }
    Text("El logo y los datos de la empresa se usan en los PDF y Excel.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SeccionTurnos(turnos: List<String>, acciones: AccionesAjustes, onAgregar: () -> Unit) = Seccion("Turnos rápidos") {
    if (turnos.isEmpty()) Text("Sin turnos. Agrega los horarios que más usas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    val forma = RoundedCornerShape(12.dp)
    turnos.forEachIndexed { i, t ->
        FilaDeslizable(onBorrar = { acciones.quitarTurno(i) }, forma = forma, descripcionBorrar = "Eliminar turno $t") {
            ListItem(
                headlineContent = { Text(t.replace("-", " – ")) },
                leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clip(forma),
            )
        }
    }
    OutlinedButton(onClick = onAgregar) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Text("Agregar turno", Modifier.padding(start = 8.dp))
    }
}

private fun textoFestivo(clave: String): String {
    val f = PeriodoCalc.ParseFecha(clave) ?: return clave
    val dia = f.dayOfWeek.getDisplayName(TextStyle.FULL, LocaleCO)
    return "%02d/%02d/%d · %s".format(f.dayOfMonth, f.monthValue, f.year, dia)
}

@Composable
private fun SeccionFestivos(festivos: List<String>, acciones: AccionesAjustes, anioSugerido: String, onAgregar: () -> Unit) =
    Seccion("Festivos") {
        var anio by rememberSaveable { mutableStateOf(anioSugerido) }
        Text(
            "Se agregan solos los festivos del año actual y del siguiente si faltan. Están calculados con la Ley Emiliani; revísalos contra el calendario oficial.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = anio, onValueChange = { anio = it.filter(Char::isDigit).take(4) },
                label = { Text("Año") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.widthIn(max = 110.dp),
            )
            FilledTonalButton(onClick = { acciones.agregarFestivosAnio(anio) }) { Text("Agregar festivos de ese año") }
        }
        val forma = RoundedCornerShape(12.dp)
        festivos.groupBy { it.take(4) }.forEach { (y, delAnio) ->
            var abierto by rememberSaveable(y) { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("$y · ${delAnio.size} festivos", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
                trailingContent = { Icon(if (abierto) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clip(forma).clickable(onClickLabel = if (abierto) "Ocultar festivos de $y" else "Ver festivos de $y", role = Role.Button) { abierto = !abierto },
            )
            if (abierto) delAnio.forEach { f ->
                FilaDeslizable(onBorrar = { acciones.quitarFestivo(f) }, forma = forma, descripcionBorrar = "Quitar festivo $f") {
                    ListItem(
                        headlineContent = { Text(textoFestivo(f)) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clip(forma).heightIn(min = 48.dp),
                    )
                }
            }
        }
        OutlinedButton(onClick = onAgregar) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("Agregar un festivo", Modifier.padding(start = 8.dp))
        }
    }

@Composable
private fun SeccionRespaldos(estado: AjustesEstado, acciones: AccionesAjustes) = Seccion("Respaldos") {
    var confirmarRestaurar by rememberSaveable { mutableStateOf(false) }
    Text(
        "La app guarda sola un .zip con tus datos, ajustes, historial, fotos y logo cuando hay cambios nuevos (al abrir y al salir de la app, máximo uno por hora).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(estado.textoUltimoRespaldo, style = MaterialTheme.typography.bodyMedium)
    Column {
        Text("Carpeta extra (Drive, tarjeta SD, USB…)", style = MaterialTheme.typography.labelLarge)
        Text(estado.carpetaExtra ?: "Ninguna (solo se guarda en este teléfono)", style = MaterialTheme.typography.bodyMedium)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = acciones.respaldarAhora) { BotonIcono(Icons.Outlined.Backup, "Respaldar ahora") }
        OutlinedButton(onClick = { confirmarRestaurar = true }) { BotonIcono(Icons.Outlined.Restore, "Restaurar desde un respaldo…") }
        OutlinedButton(onClick = acciones.elegirCarpetaExtra) { BotonIcono(Icons.Outlined.FolderOpen, "Elegir carpeta extra…") }
        if (estado.carpetaExtra != null) TextButton(onClick = acciones.quitarCarpetaExtra) { Text("Quitar carpeta extra") }
    }
    Text("Para pasar los datos al escritorio o traerlos de allí, usa Historial → Descargar o Cargar respaldo.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (confirmarRestaurar) {
        AlertDialog(
            onDismissRequest = { confirmarRestaurar = false },
            icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
            title = { Text("Restaurar respaldo") },
            text = {
                Text("Se van a reemplazar los datos actuales (empleados, ajustes, historial y fotos) por los del respaldo.\n\nAntes se guarda un respaldo de lo que tienes ahora.")
            },
            confirmButton = { TextButton(onClick = { confirmarRestaurar = false; acciones.restaurar() }) { Text("Elegir respaldo") } },
            dismissButton = { TextButton(onClick = { confirmarRestaurar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun BotonIcono(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String) {
    Icon(icono, contentDescription = null, modifier = Modifier.size(18.dp))
    Text(texto, Modifier.padding(start = 8.dp))
}

