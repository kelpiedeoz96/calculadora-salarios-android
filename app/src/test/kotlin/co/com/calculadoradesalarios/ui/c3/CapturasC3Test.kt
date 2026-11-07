package co.com.calculadoradesalarios.ui.c3

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.engine.UiState
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.ui.HoyPrueba
import co.com.calculadoradesalarios.ui.calendario.AccionesCalendario
import co.com.calculadoradesalarios.ui.calendario.CalendarioContenido
import co.com.calculadoradesalarios.ui.calendario.CalendarioViewModel
import co.com.calculadoradesalarios.ui.historial.AccionesHistorial
import co.com.calculadoradesalarios.ui.historial.HistorialContenido
import co.com.calculadoradesalarios.ui.historial.HistorialViewModel
import co.com.calculadoradesalarios.ui.panel.AccionesPanel
import co.com.calculadoradesalarios.ui.panel.PanelContenido
import co.com.calculadoradesalarios.ui.panel.PanelViewModel
import co.com.calculadoradesalarios.ui.principal.Pestana
import co.com.calculadoradesalarios.ui.principal.PrincipalContenido
import co.com.calculadoradesalarios.ui.principal.PrincipalEstado
import co.com.calculadoradesalarios.ui.repoFalso
import co.com.calculadoradesalarios.ui.tema.NominaTema
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** PNG de la entrega 3c en app/build/capturas/3c/ (`./gradlew :app:recordRoborazziDebug`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class CapturasC3Test {
    @get:Rule val main = ReglaMain()

    private fun capturar(nombre: String, contenido: @Composable () -> Unit) {
        for ((modo, sufijo) in listOf("light" to "claro", "dark" to "oscuro")) {
            captureRoboImage("build/capturas/3c/${nombre}_$sufijo.png") { NominaTema(modo) { Surface { contenido() } } }
        }
    }

    /** Respaldo sintético con nombres inventados, quincena 1 de octubre de 2026. */
    private fun ejemplo() = AlmacenFalso().apply {
        val st = Oraculo.estadoSintetico()
        val nombres = listOf("Laura Gómez", "Andrés Ruiz", "Camila Ortiz", "Julián Pérez", "Sofía Herrera", "Mateo Castro")
        st.emps.forEachIndexed { i, e -> e.name = nombres[i] }
        inicial = DatosNomina(st, UiState(month = "2026-10", mode = "q1", sel = st.emps[1].id), emptyList())
    }

    @Composable
    private fun Marco(p: Pestana, periodo: String, contenido: @Composable (Modifier) -> Unit) =
        PrincipalContenido(PrincipalEstado(cargando = false, pestana = p, etiquetaPeriodo = periodo), {}, {}, {}, {}, SnackbarHostState()) { _, m -> contenido(m) }

    @Test
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    fun panelCompleto() = runTest {
        val e = PanelViewModel(repoFalso(ejemplo()), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler)).estado.value
        capturar("16_panel_completo") { Marco(Pestana.Panel, "01/10/2026 – 15/10/2026") { PanelContenido(e, AccionesPanel(), it) } }
    }

    @Test
    fun panelCelular() = runTest {
        val e = PanelViewModel(repoFalso(ejemplo()), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler)).estado.value
        capturar("17_panel_celular") { Marco(Pestana.Panel, "01/10/2026 – 15/10/2026") { PanelContenido(e, AccionesPanel(), it) } }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun panelTableta() = runTest {
        val e = PanelViewModel(repoFalso(ejemplo()), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler)).estado.value
        capturar("18_panel_tableta") { Marco(Pestana.Panel, "01/10/2026 – 15/10/2026") { PanelContenido(e, AccionesPanel(), it) } }
    }

    @Test
    fun calendario() = runTest {
        val e = CalendarioViewModel(repoFalso(ejemplo()), { LocalDate.of(2026, 10, 7) }, UnconfinedTestDispatcher(testScheduler)).estado.value
        capturar("19_calendario") { Marco(Pestana.Calendario, "01/10/2026 – 15/10/2026") { CalendarioContenido(e, AccionesCalendario(), it) } }
    }

    @Test
    fun historial() = runTest {
        var ahora = 1_791_000_000_000L
        val repo = repoFalso(ejemplo(), reloj = { ahora })
        repo.editar("Empleado añadido") { st, _ -> st.emps.add(Employee(name = "Nuevo empleado")); true }
        ahora += 3 * 3_600_000; repo.editar("Edición de datos") { st, _ -> st.cfg.nit = "900.000.000-1"; true }
        ahora += 26 * 3_600_000; repo.editar("Horas borradas") { _, _ -> true }
        ahora += 2 * 3_600_000; repo.editar("Empleado eliminado") { st, _ -> st.emps.removeLast(); true }
        val e = HistorialViewModel(repo, { ZoneId.of("America/Bogota") }, UnconfinedTestDispatcher(testScheduler)).estado.value
        capturar("20_historial") { Marco(Pestana.Historial, "") { HistorialContenido(e, AccionesHistorial(), it) } }
    }

    @Test
    fun vacios() = runTest {
        val vacio = AlmacenFalso().apply { inicial = inicial.copy(state = inicial.state.clone().apply { emps.clear() }) }
        val repo = repoFalso(vacio)
        val panel = PanelViewModel(repo, { HoyPrueba }, UnconfinedTestDispatcher(testScheduler)).estado.value
        val hist = HistorialViewModel(repo, { ZoneId.of("America/Bogota") }, UnconfinedTestDispatcher(testScheduler)).estado.value
        capturar("21_panel_vacio") { Marco(Pestana.Panel, "01/11/2026 – 15/11/2026") { PanelContenido(panel, AccionesPanel(), it) } }
        capturar("22_historial_vacio") { Marco(Pestana.Historial, "") { HistorialContenido(hist, AccionesHistorial(), it) } }
    }
}
