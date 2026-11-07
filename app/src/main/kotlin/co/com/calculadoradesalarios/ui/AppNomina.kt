package co.com.calculadoradesalarios.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.com.calculadoradesalarios.datos.NominaRepository
import co.com.calculadoradesalarios.datos.TipoMensaje
import co.com.calculadoradesalarios.engine.PeriodoCalc
import co.com.calculadoradesalarios.ui.ajustes.AccionesAjustes
import co.com.calculadoradesalarios.ui.ajustes.AjustesContenido
import co.com.calculadoradesalarios.ui.ajustes.AjustesViewModel
import co.com.calculadoradesalarios.ui.comun.LocalCargadorImagen
import co.com.calculadoradesalarios.ui.empleados.AccionesEmpleados
import co.com.calculadoradesalarios.ui.empleados.AccionesFicha
import co.com.calculadoradesalarios.ui.empleados.EmpleadosContenido
import co.com.calculadoradesalarios.ui.empleados.EmpleadosViewModel
import co.com.calculadoradesalarios.ui.empleados.FichaEmpleadoContenido
import co.com.calculadoradesalarios.ui.liquidacion.AccionesDia
import co.com.calculadoradesalarios.ui.liquidacion.AccionesLiquidacion
import co.com.calculadoradesalarios.ui.liquidacion.EditorDiaContenido
import co.com.calculadoradesalarios.ui.liquidacion.LiquidacionContenido
import co.com.calculadoradesalarios.ui.liquidacion.LiquidacionViewModel
import co.com.calculadoradesalarios.ui.principal.EnConstruccion
import co.com.calculadoradesalarios.ui.principal.Pestana
import co.com.calculadoradesalarios.ui.principal.PrincipalContenido
import co.com.calculadoradesalarios.ui.principal.PrincipalViewModel
import co.com.calculadoradesalarios.ui.principal.SelectorPeriodoContenido
import co.com.calculadoradesalarios.ui.tema.NominaTema
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Extensión de imagen a partir del tipo MIME; null si no es PNG ni JPG (como el escritorio). */
fun extensionImagen(resolver: ContentResolver, uri: Uri): String? = when (resolver.getType(uri)) {
    "image/png" -> "png"
    "image/jpeg", "image/jpg" -> "jpg"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNomina(repo: NominaRepository) {
    val fabrica = remember(repo) {
        viewModelFactory {
            initializer { PrincipalViewModel(repo) }
            initializer { AjustesViewModel(repo) }
            initializer { EmpleadosViewModel(repo, createSavedStateHandle()) }
            initializer { LiquidacionViewModel(repo, createSavedStateHandle()) }
        }
    }
    val principalVm: PrincipalViewModel = viewModel(factory = fabrica)
    val ajustesVm: AjustesViewModel = viewModel(factory = fabrica)
    val empleadosVm: EmpleadosViewModel = viewModel(factory = fabrica)
    val liquidacionVm: LiquidacionViewModel = viewModel(factory = fabrica)

    val principal by principalVm.estado.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var periodoAbierto by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(repo) {
        repo.mensajes.collect { m ->
            snackbar.showSnackbar(m.texto, duration = if (m.tipo == TipoMensaje.Error) SnackbarDuration.Long else SnackbarDuration.Short)
        }
    }

    CompositionLocalProvider(LocalCargadorImagen provides { rel -> repo.leerImagen(rel) }) {
        NominaTema(principal.tema) {
            Surface(Modifier.fillMaxSize()) {
                if (principal.cargando) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@Surface
                }
                BackHandler(enabled = principal.pestana == Pestana.Ajustes) { principalVm.atras() }
                PrincipalContenido(
                    estado = principal,
                    onNavegar = principalVm::navegar,
                    onAtras = { principalVm.atras() },
                    onDesplazarPeriodo = principalVm::desplazarPeriodo,
                    onAbrirPeriodo = { periodoAbierto = true },
                    snackbar = snackbar,
                ) { pestana, mod ->
                    when (pestana) {
                        Pestana.Empleados -> EmpleadosRuta(empleadosVm, snackbar, mod)
                        Pestana.Liquidacion -> LiquidacionRuta(liquidacionVm, mod)
                        Pestana.Ajustes -> AjustesRuta(ajustesVm, snackbar, mod)
                        else -> EnConstruccion(pestana, mod)
                    }
                }
                if (periodoAbierto) {
                    ModalBottomSheet(onDismissRequest = { periodoAbierto = false }) {
                        SelectorPeriodoContenido(principal, onAplicar = { mes, modo, desde, hasta ->
                            principalVm.aplicarPeriodo(mes, modo, desde, hasta).also { if (it == null) periodoAbierto = false }
                        })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiquidacionRuta(vm: LiquidacionViewModel, modifier: Modifier) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LiquidacionContenido(
        estado,
        AccionesLiquidacion(
            seleccionar = vm::seleccionar,
            inclSueldo = vm::fijarInclSueldo,
            abrirDia = vm::abrirDia,
            limpiarPeriodo = vm::limpiarPeriodo,
            agregarEmpleado = vm::agregarEmpleado,
        ),
        textoConfirmarLimpiar = vm.textoConfirmarLimpiar(),
        modifier = modifier,
    )
    estado.diaAbierto?.let { d ->
        ModalBottomSheet(onDismissRequest = vm::cerrarDia) {
            EditorDiaContenido(
                d, estado.turnosRapidos, estado.erroresDia,
                AccionesDia(
                    confirmar = { campo, texto -> vm.confirmar(d.clave, campo, texto) },
                    descanso = { vm.fijarDescanso(d.clave, it) },
                    turno = { vm.aplicarTurno(d.clave, it) },
                    limpiar = { vm.limpiarDia(d.clave) },
                ),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun EmpleadosRuta(vm: EmpleadosViewModel, snackbar: SnackbarHostState, modifier: Modifier) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver

    fun eliminarConDeshacer(id: String) {
        val nombre = vm.eliminar(id) ?: return
        alcance.launch {
            val r = snackbar.showSnackbar("Se eliminó a $nombre", actionLabel = "Deshacer", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) vm.deshacerEliminacion()
        }
    }

    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = estado.ficha?.id
        if (uri != null && id != null) {
            val ext = extensionImagen(resolver, uri)
            if (ext == null) alcance.launch { snackbar.showSnackbar("Formato de imagen no permitido (usa PNG o JPG).") }
            else vm.elegirFoto(id, { resolver.openInputStream(uri)!! }, ext)
        }
    }

    EmpleadosContenido(
        estado,
        AccionesEmpleados(
            buscar = vm::buscar,
            inclSueldo = vm::fijarInclSueldo,
            abrir = vm::abrirFicha,
            agregar = vm::agregar,
            eliminar = ::eliminarConDeshacer,
        ),
        modifier,
    )

    estado.ficha?.let { ficha ->
        Dialog(onDismissRequest = vm::cerrarFicha, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                FichaEmpleadoContenido(
                    ficha,
                    AccionesFicha(
                        cerrar = vm::cerrarFicha,
                        confirmar = { campo, texto -> vm.confirmar(ficha.id, campo, texto) },
                        diaDescanso = { vm.fijarDiaDescanso(ficha.id, it) },
                        auxilio = { vm.fijarAuxilio(ficha.id, it) },
                        elegirFoto = { elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        quitarFoto = { vm.quitarFoto(ficha.id) },
                        eliminar = { eliminarConDeshacer(ficha.id) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun AjustesRuta(vm: AjustesViewModel, snackbar: SnackbarHostState, modifier: Modifier) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver

    fun deshacer(texto: String, accion: () -> Unit) = alcance.launch {
        if (snackbar.showSnackbar(texto, actionLabel = "Deshacer", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) accion()
    }

    val elegirLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val ext = extensionImagen(resolver, uri)
            if (ext == null) alcance.launch { snackbar.showSnackbar("Formato de imagen no permitido (usa PNG o JPG).") }
            else vm.elegirLogo({ resolver.openInputStream(uri)!! }, ext)
        }
    }
    val restaurar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restaurar { resolver.openInputStream(uri)!! }
    }
    val carpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.elegirCarpetaExtra(uri.toString())
    }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportarDatos { resolver.openOutputStream(uri)!! }
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importarDatos { resolver.openInputStream(uri)!! }
    }

    AjustesContenido(
        estado,
        AccionesAjustes(
            confirmar = vm::confirmar,
            interruptor = vm::fijarInterruptor,
            baseDescuentos = vm::fijarBaseDescuentos,
            tema = vm::fijarTema,
            agregarTurno = { a, b -> vm.agregarTurno(a, b) },
            quitarTurno = { i -> vm.quitarTurno(i)?.let { t -> deshacer("Turno $t eliminado") { vm.reinsertarTurno(i, t) } } },
            agregarFestivo = vm::agregarFestivo,
            quitarFestivo = { f ->
                vm.quitarFestivo(f)
                PeriodoCalc.ParseFecha(f)?.let { fecha -> deshacer("Festivo quitado") { vm.agregarFestivo(fecha) } }
            },
            agregarFestivosAnio = vm::agregarFestivosAnio,
            elegirLogo = { elegirLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            quitarLogo = vm::quitarLogo,
            respaldarAhora = { vm.respaldarAhora() },
            restaurar = { restaurar.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            elegirCarpetaExtra = { carpeta.launch(null) },
            quitarCarpetaExtra = { vm.quitarCarpetaExtra() },
            exportar = { exportar.launch("nomina_datos_${LocalDate.now()}.json") },
            importar = { importar.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        ),
        anioSugerido = vm.anioSugerido(),
        modifier = modifier,
    )
}
