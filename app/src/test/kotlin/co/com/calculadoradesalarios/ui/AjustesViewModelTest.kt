package co.com.calculadoradesalarios.ui

import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.engine.JsonConfig
import co.com.calculadoradesalarios.ui.ajustes.AjustesViewModel
import co.com.calculadoradesalarios.ui.ajustes.CampoAjuste
import co.com.calculadoradesalarios.ui.ajustes.Interruptor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class AjustesViewModelTest {
    @get:Rule val main = ReglaMain()

    private suspend fun kotlinx.coroutines.test.TestScope.crear(almacen: AlmacenFalso = AlmacenFalso()) =
        repoFalso(almacen).let { it to AjustesViewModel(it, { HoyPrueba }, UnconfinedTestDispatcher(testScheduler)) }

    @Test
    fun valores_SeMuestranComoEnElEscritorio() = runTest {
        val (_, vm) = crear()
        val v = vm.estado.value.valores
        assertEquals("42", v[CampoAjuste.TopeSemanal])
        assertEquals("19:00", v[CampoAjuste.NocturnaDesde])
        assertEquals("1750905", v[CampoAjuste.Smmlv])
        assertEquals("Mi Empresa", v[CampoAjuste.RazonSocial])
        assertEquals(CampoAjuste.entries.size, v.size)
    }

    @Test
    fun numeroValido_SeAplica_YDejaCopiaEnElHistorial() = runTest {
        val (repo, vm) = crear()
        vm.confirmar(CampoAjuste.Smmlv, "1.800.000")  // TryParseNumero no acepta separador de miles
        assertEquals("Valor no válido: se mantuvo el anterior.", vm.estado.value.errores[CampoAjuste.Smmlv])
        assertEquals(1750905.0, repo.datos.value!!.state.cfg.smmlv, 0.0)

        vm.confirmar(CampoAjuste.Smmlv, "1800000")
        assertNull(vm.estado.value.errores[CampoAjuste.Smmlv])
        assertEquals(1800000.0, repo.datos.value!!.state.cfg.smmlv, 0.0)
        assertEquals("Edición de datos", repo.datos.value!!.history.single().reason)

        vm.confirmar(CampoAjuste.Smmlv, "1800000.0")  // mismo valor: no cambia nada
        assertEquals(1, repo.datos.value!!.history.size)
    }

    @Test
    fun divisorCero_YHoraInvalida_UsanLosMensajesDelEscritorio() = runTest {
        val (repo, vm) = crear()
        vm.confirmar(CampoAjuste.Divisor, "0")
        assertEquals("El divisor debe ser mayor que 0.", vm.estado.value.errores[CampoAjuste.Divisor])
        vm.confirmar(CampoAjuste.NocturnaDesde, "25:00")
        assertEquals("Hora no válida: usa HH:MM.", vm.estado.value.errores[CampoAjuste.NocturnaDesde])
        vm.confirmar(CampoAjuste.NocturnaDesde, "2130")
        assertEquals("21:30", repo.datos.value!!.state.cfg.nightStart)
        assertNull(vm.estado.value.errores[CampoAjuste.NocturnaDesde])
    }

    @Test
    fun texto_SeRecorta() = runTest {
        val (repo, vm) = crear()
        vm.confirmar(CampoAjuste.Nit, "  900.123.456-7  ")
        assertEquals("900.123.456-7", repo.datos.value!!.state.cfg.nit)
    }

    @Test
    fun interruptores_YBaseDescuentos_YTema() = runTest {
        val (repo, vm) = crear()
        vm.fijarInterruptor(Interruptor.DailyCapOn, true)
        vm.fijarInterruptor(Interruptor.DomAutoOn, false)
        vm.fijarBaseDescuentos(true)
        val c = repo.datos.value!!.state.cfg
        assertTrue(c.dailyCapOn); assertEquals(false, c.domAutoOn); assertEquals("con_recargos", c.deductBase)
        assertEquals("tres cambios con el mismo motivo en < 2 min se fusionan", 1, repo.datos.value!!.history.size)
        vm.fijarTema("dark")
        assertEquals("dark", vm.estado.value.tema)
        assertEquals("dark", repo.datos.value!!.ui.themeMode)
    }

    @Test
    fun turnos_AgregarValidarQuitarYDeshacer() = runTest {
        val (repo, vm) = crear()
        assertTrue(vm.agregarTurno("22:00", "06:00"))
        assertEquals(listOf("08:00-12:00", "08:00-17:00", "14:00-22:00", "22:00-06:00"), vm.estado.value.turnos)
        assertEquals(false, vm.agregarTurno("25:00", "06:00"))
        val quitado = vm.quitarTurno(1)
        assertEquals("08:00-17:00", quitado)
        vm.reinsertarTurno(1, quitado!!)
        assertEquals("08:00-17:00", repo.datos.value!!.state.cfg.turnos[1])
    }

    @Test
    fun festivos_OrdenadosSinDuplicados_YAgregarAnioConMensajes() = runTest {
        val (repo, vm) = crear()
        val antes = vm.estado.value.festivos.size
        vm.agregarFestivo(LocalDate.of(2026, 3, 1))
        vm.agregarFestivo(LocalDate.of(2026, 3, 1))
        val f = vm.estado.value.festivos
        assertEquals(antes + 1, f.size)
        assertEquals(f.sorted(), f)
        vm.quitarFestivo("2026-03-01")
        assertEquals(antes, vm.estado.value.festivos.size)

        val mensajes = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.mensajes.collect { mensajes += it.texto } }
        vm.agregarFestivosAnio("1999")
        vm.agregarFestivosAnio("2027")   // ya están (se completan al cargar)
        vm.agregarFestivosAnio("2028")
        assertEquals(listOf("Escribe un año entre 2000 y 2100.", "Los festivos de 2027 ya estaban en la lista.", "Se agregaron 18 festivos de 2028."), mensajes)
    }

    @Test
    fun logo_RespaldoCarpetaExtra() = runTest {
        val almacen = AlmacenFalso()
        val (repo, vm) = crear(almacen)
        vm.elegirLogo({ ByteArrayInputStream(byteArrayOf(1, 2)) }, "png")
        assertEquals("empresa_logo.png", vm.estado.value.logo)
        vm.quitarLogo()
        assertNull(repo.datos.value!!.state.cfg.logoCustomRelPath)

        assertEquals("Todavía no hay respaldos.", vm.estado.value.textoUltimoRespaldo)
        vm.respaldarAhora()
        assertEquals("Último respaldo: 07/11/2026 09:30  ·  1 guardados (se conservan los últimos 20).", vm.estado.value.textoUltimoRespaldo)

        vm.elegirCarpetaExtra("content://proveedor/tree/Respaldos")
        assertEquals("Respaldos", vm.estado.value.carpetaExtra)
        assertEquals("backupExtraDir del escritorio no se toca", "", repo.datos.value!!.ui.backupExtraDir)
        vm.quitarCarpetaExtra()
        assertNull(vm.estado.value.carpetaExtra)
    }

    @Test
    fun exportarEImportar_DatosDelEscritorio() = runTest {
        val (repo, vm) = crear()
        val salida = ByteArrayOutputStream()
        vm.exportarDatos { salida }
        val exportado = salida.toString(Charsets.UTF_8.name())
        assertEquals(repo.datos.value!!.state.emps[0].id, JsonConfig.json.decodeFromString(co.com.calculadoradesalarios.engine.AppState.serializer(), exportado).emps[0].id)

        val dataEscritorio = javaClass.classLoader!!.getResource("TestData/respaldo_sintetico.json")!!.readText()
        vm.importarDatos { ByteArrayInputStream(dataEscritorio.toByteArray()) }
        assertEquals(6, repo.datos.value!!.state.emps.size)
        vm.importarDatos { ByteArrayInputStream("{\"no\":\"es\"}".toByteArray()) }
        assertEquals("un archivo sin estado no reemplaza nada", 6, repo.datos.value!!.state.emps.size)
    }
}

