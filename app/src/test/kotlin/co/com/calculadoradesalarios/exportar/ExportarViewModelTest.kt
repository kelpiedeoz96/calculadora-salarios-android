package co.com.calculadoradesalarios.exportar

import androidx.lifecycle.SavedStateHandle
import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.engine.exportar.ExportadorNomina
import co.com.calculadoradesalarios.ui.HoyPrueba
import co.com.calculadoradesalarios.ui.c3.Oraculo
import co.com.calculadoradesalarios.ui.empleados.EmpleadosViewModel
import co.com.calculadoradesalarios.ui.liquidacion.EjemploResuelto
import co.com.calculadoradesalarios.ui.liquidacion.LiquidacionViewModel
import co.com.calculadoradesalarios.ui.repoFalso
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.zip.ZipInputStream

class ExportarViewModelTest {
    @get:Rule val main = ReglaMain()

    private fun esPdf(b: ByteArray) = String(b, 0, 8, Charsets.ISO_8859_1) == "%PDF-1.7"
    private fun partesZip(b: ByteArray) = ZipInputStream(b.inputStream()).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }

    @Test
    fun liquidacion_ExportaElEmpleadoSeleccionado_ConLosNombresDelEscritorio() = runTest {
        val vm = LiquidacionViewModel(repoFalso(EjemploResuelto.almacen()), SavedStateHandle(), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        val pdf = vm.exportar(TipoExportacion.Pdf)
        assertEquals("comprobante_Laura_Gómez_2026-10-12_2026-10-18.pdf", pdf.nombre)
        assertEquals(ExportadorNomina.MimePdf, pdf.mime)
        assertTrue(esPdf(pdf.bytes))
        val xlsx = vm.exportar(TipoExportacion.Excel)
        assertEquals("nomina_recargos_2026-10-12_2026-10-18.xlsx", xlsx.nombre)
        assertEquals(ExportadorNomina.MimeXlsx, xlsx.mime)
        assertTrue("xl/workbook.xml" in partesZip(xlsx.bytes))
    }

    @Test
    fun empleados_ExportaTodos_YSinEmpleadosAvisaComoElEscritorio() = runTest {
        val vm = EmpleadosViewModel(repoFalso(Oraculo.almacen(Oraculo.escenarios[0])), SavedStateHandle(), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        val pdf = vm.exportarTodos(TipoExportacion.Pdf)
        assertEquals("comprobantes_todos_2026-10-01_2026-10-15.pdf", pdf.nombre)
        assertTrue(esPdf(pdf.bytes))
        assertEquals("nomina_recargos_2026-10-01_2026-10-15.xlsx", vm.exportarTodos(TipoExportacion.Excel).nombre)

        val vacio = AlmacenFalso().apply { inicial = inicial.copy(state = inicial.state.clone().apply { emps.clear() }) }
        val vm2 = EmpleadosViewModel(repoFalso(vacio), SavedStateHandle(), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        val e = assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { vm2.exportarTodos(TipoExportacion.Pdf) } }
        assertEquals("No hay empleados para exportar.", e.message)
    }

    private fun conLogo(bytes: ByteArray) = EjemploResuelto.almacen().apply {
        imagenes["empresa_logo.png"] = bytes
        inicial = inicial.copy(state = inicial.state.clone().apply { cfg.logoCustomRelPath = "empresa_logo.png" })
    }

    @Test
    fun elLogoDeAjustesVaEnElPdf_ConTransparencia_YUnLogoDañadoNoRompeNada() = runTest {
        val jvm = { FuentesLato.exportador(DecodificadorJvm) }
        val vm = LiquidacionViewModel(repoFalso(conLogo(DecodificadorJvm.pngTransparente())), SavedStateHandle(), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler), jvm)
        val conLogo = String(vm.exportar(TipoExportacion.Pdf).bytes, Charsets.ISO_8859_1)
        assertTrue(conLogo.contains("/Subtype /Image") && conLogo.contains("/SMask"))

        val vm2 = LiquidacionViewModel(repoFalso(conLogo(byteArrayOf(1, 2, 3))), SavedStateHandle(), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler), jvm)
        val sinLogo = vm2.exportar(TipoExportacion.Pdf).bytes
        assertTrue(esPdf(sinLogo) && !String(sinLogo, Charsets.ISO_8859_1).contains("/Subtype /Image"))
    }
}
