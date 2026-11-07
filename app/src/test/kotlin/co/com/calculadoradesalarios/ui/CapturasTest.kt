package co.com.calculadoradesalarios.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.engine.JsonConfig
import co.com.calculadoradesalarios.ui.ajustes.AccionesAjustes
import co.com.calculadoradesalarios.ui.ajustes.AjustesContenido
import co.com.calculadoradesalarios.ui.ajustes.AjustesViewModel
import co.com.calculadoradesalarios.ui.ajustes.CampoAjuste
import co.com.calculadoradesalarios.ui.empleados.AccionesEmpleados
import co.com.calculadoradesalarios.ui.empleados.AccionesFicha
import co.com.calculadoradesalarios.ui.empleados.ChipCategoria
import co.com.calculadoradesalarios.ui.empleados.EmpleadosContenido
import co.com.calculadoradesalarios.ui.empleados.EmpleadosEstado
import co.com.calculadoradesalarios.ui.empleados.EmpleadosViewModel
import co.com.calculadoradesalarios.ui.empleados.FichaEmpleadoContenido
import co.com.calculadoradesalarios.ui.principal.Pestana
import co.com.calculadoradesalarios.ui.principal.PrincipalContenido
import co.com.calculadoradesalarios.ui.principal.PrincipalEstado
import co.com.calculadoradesalarios.ui.principal.SelectorPeriodoContenido
import co.com.calculadoradesalarios.ui.tema.NombreCategoria
import co.com.calculadoradesalarios.ui.tema.NominaTema
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * PNG de cada pantalla de la entrega 3a en claro y oscuro, con datos de ejemplo (sintéticos).
 * Se generan con `./gradlew :app:recordRoborazziDebug` en app/build/capturas/3a/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Celular, application = Application::class)
class CapturasTest {
    @get:Rule val main = ReglaMain()

    private val temas = listOf("light" to "claro", "dark" to "oscuro")

    private fun capturar(nombre: String, contenido: @Composable () -> Unit) {
        for ((modo, sufijo) in temas) {
            captureRoboImage("build/capturas/3a/${nombre}_$sufijo.png") {
                NominaTema(modo) { Surface { contenido() } }
            }
        }
    }

    private fun almacenEjemplo(incl: Boolean = false) = AlmacenFalso().apply {
        val st = JsonConfig.json.decodeFromString(AppState.serializer(), javaClass.classLoader!!.getResource("TestData/respaldo_sintetico.json")!!.readText())
        // Nombres y cargos inventados, solo para que las capturas se lean como en uso real.
        val nombres = listOf("Laura Gómez", "Andrés Ruiz", "Camila Ortiz", "Julián Pérez", "Sofía Herrera", "Mateo Castro")
        val cargos = listOf("Supervisora", "Operario", "Cajera", "Operario", "Auxiliar de bodega", "Vigilante")
        st.emps.forEachIndexed { i, e -> e.name = nombres[i]; e.cargo = cargos[i]; e.cedula = "10000000${i + 1}" }
        inicial = DatosNomina(st, inicial.ui.copy(month = "2026-10", mode = "q1", sel = st.emps[0].id, incl = incl), emptyList())
    }

    private suspend fun TestScope.empleados(incl: Boolean = false): Pair<EmpleadosViewModel, PrincipalEstado> {
        val repo = repoFalso(almacenEjemplo(incl))
        val vm = EmpleadosViewModel(repo, SavedStateHandle(), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        return vm to PrincipalEstado(cargando = false, pestana = Pestana.Empleados, etiquetaPeriodo = vm.estado.value.periodo, mes = "2026-10")
    }

    @Composable
    private fun Principal(principal: PrincipalEstado, estado: EmpleadosEstado) =
        PrincipalContenido(principal, {}, {}, {}, {}, SnackbarHostState()) { _, mod -> EmpleadosContenido(estado, AccionesEmpleados(), mod) }

    @Test
    fun empleados() = runTest {
        val (vm, principal) = empleados()
        capturar("01_empleados") { Principal(principal, vm.estado.value) }
    }

    @Test
    fun empleadosConSueldo() = runTest {
        val (vm, principal) = empleados(incl = true)
        capturar("02_empleados_con_sueldo") { Principal(principal, vm.estado.value) }
    }

    @Test
    fun fichaEmpleado() = runTest {
        val (vm, _) = empleados()
        val id = vm.estado.value.filas[1].id
        vm.abrirFicha(id)
        vm.confirmar(id, co.com.calculadoradesalarios.ui.empleados.CampoEmpleado.Salario, "1500000")
        capturar("03_ficha_empleado") { FichaEmpleadoContenido(vm.estado.value.ficha!!, AccionesFicha()) }
    }

    @Test
    fun empleadosVacio() {
        capturar("04_empleados_vacio") {
            Principal(PrincipalEstado(cargando = false, pestana = Pestana.Empleados, etiquetaPeriodo = "01/11/2026 – 15/11/2026"), EmpleadosEstado(cargando = false, hayEmpleados = false))
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h4600dp-xxhdpi")
    fun ajustes() = runTest {
        val repo = repoFalso(almacenEjemplo())
        val vm = AjustesViewModel(repo, { HoyPrueba })
        vm.confirmar(CampoAjuste.Divisor, "0")   // para ver cómo se muestra un error
        vm.respaldarAhora()
        val principal = PrincipalEstado(cargando = false, pestana = Pestana.Ajustes)
        capturar("05_ajustes") {
            PrincipalContenido(principal, {}, {}, {}, {}, SnackbarHostState()) { _, mod ->
                AjustesContenido(vm.estado.value, AccionesAjustes(), "2027", mod)
            }
        }
    }

    @Test
    fun selectorPeriodo() {
        capturar("06_selector_periodo") {
            SelectorPeriodoContenido(PrincipalEstado(cargando = false, mes = "2026-10", modo = "custom", desde = "2026-10-05", hasta = "2026-10-18"), { _, _, _, _ -> null })
        }
    }

    @Test
    @Config(qualifiers = Tableta)
    fun tabletaConRail() = runTest {
        val (vm, principal) = empleados()
        capturar("07_tableta_rail") { Principal(principal, vm.estado.value) }
    }

    @Test
    @Config(qualifiers = Tableta)
    fun tabletaAjustes() = runTest {
        val repo = repoFalso(almacenEjemplo())
        val vm = AjustesViewModel(repo, { HoyPrueba })
        capturar("08_tableta_ajustes") {
            PrincipalContenido(PrincipalEstado(cargando = false, pestana = Pestana.Ajustes), {}, {}, {}, {}, SnackbarHostState()) { _, mod ->
                AjustesContenido(vm.estado.value, AccionesAjustes(), "2027", mod)
            }
        }
    }

    @Test
    fun coloresDeCategorias() {
        capturar("09_categorias") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Categorías de recargo", style = MaterialTheme.typography.titleMedium)
                Cat.entries.forEach { c ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ChipCategoria(c, c.name)
                        Text(NombreCategoria.getValue(c), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

const val Celular = "w411dp-h891dp-xxhdpi"
const val Tableta = "w1280dp-h800dp-xhdpi"
