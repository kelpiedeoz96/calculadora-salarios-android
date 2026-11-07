package co.com.calculadoradesalarios.ui.liquidacion

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.ui.HoyPrueba
import co.com.calculadoradesalarios.ui.principal.Pestana
import co.com.calculadoradesalarios.ui.principal.PrincipalContenido
import co.com.calculadoradesalarios.ui.principal.PrincipalEstado
import co.com.calculadoradesalarios.ui.repoFalso
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

/** PNG de la entrega 3b en app/build/capturas/3b/ (`./gradlew :app:recordRoborazziDebug`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class CapturasLiquidacionTest {
    @get:Rule val main = ReglaMain()

    private fun capturar(nombre: String, contenido: @Composable () -> Unit) {
        for ((modo, sufijo) in listOf("light" to "claro", "dark" to "oscuro")) {
            captureRoboImage("build/capturas/3b/${nombre}_$sufijo.png") { NominaTema(modo) { Surface { contenido() } } }
        }
    }

    private suspend fun TestScope.vm(almacen: AlmacenFalso = EjemploResuelto.almacen()) =
        LiquidacionViewModel(repoFalso(almacen), SavedStateHandle(), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))

    @Composable
    private fun Pantalla(e: LiquidacionEstado) =
        PrincipalContenido(
            PrincipalEstado(cargando = false, pestana = Pestana.Liquidacion, etiquetaPeriodo = e.periodo, modo = "custom"),
            {}, {}, {}, {}, SnackbarHostState(),
        ) { _, mod -> LiquidacionContenido(e, AccionesLiquidacion(), "", mod) }

    /** Ejemplo resuelto de la sección 25: recargos 181.249 y neto 1.111.213. Pantalla completa. */
    @Test
    @Config(qualifiers = "w411dp-h3900dp-xxhdpi")
    fun ejemploResueltoCompleto() = runTest {
        val e = vm().estado.value
        capturar("10_liquidacion_ejemplo_resuelto") { Pantalla(e) }
    }

    @Test
    fun ejemploResueltoArriba() = runTest {
        val e = vm().estado.value
        capturar("11_liquidacion_celular") { Pantalla(e) }
    }

    @Test
    fun editorDelDia() = runTest {
        val v = vm()
        v.abrirDia("2026-10-17")
        v.confirmar("2026-10-17", CampoDia.Entrada2, "25:00")   // para ver el error
        val e = v.estado.value
        capturar("12_liquidacion_editor_dia") { EditorDiaContenido(e.diaAbierto!!, e.turnosRapidos, e.erroresDia, AccionesDia()) }
    }

    @Test
    fun avisosDeDatosFaltantes_YSoloRecargos() = runTest {
        val almacen = EjemploResuelto.almacen(incl = false).apply {
            inicial = inicial.copy(ui = inicial.ui.copy(from = "2026-10-15", to = "2026-10-18"))
            inicial.state.emps[0].days.remove("2026-10-13")
        }
        val e = vm(almacen).estado.value
        capturar("13_liquidacion_avisos") { Pantalla(e) }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tableta() = runTest {
        val e = vm().estado.value
        capturar("14_liquidacion_tableta") { Pantalla(e) }
    }

    @Test
    fun sinEmpleados() = runTest {
        val vacio = AlmacenFalso().apply { inicial = inicial.copy(state = inicial.state.clone().apply { emps.clear() }) }
        val e = vm(vacio).estado.value
        capturar("15_liquidacion_vacio") { Pantalla(e) }
    }
}
