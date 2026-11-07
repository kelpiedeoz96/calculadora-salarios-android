package co.com.calculadoradesalarios.engine.exportar

import org.junit.jupiter.api.Test
import java.io.File

/** Deja en engine/build/exportaciones/ el PDF y el Excel de los 5 escenarios (para verificarlos a mano). */
class GenerarExportacionesTest {
    @Test
    fun generar() {
        val dir = File("build/exportaciones").apply { mkdirs() }
        for (e in Escenarios.todos) {
            val (pdf, _) = Escenarios.comprobante().generar(e.st, e.incl, e.st.emps, e.desde, e.hasta, Escenarios.Hoy)
            File(dir, "${e.nombre}.pdf").writeBytes(pdf)
            File(dir, "${e.nombre}.xlsx").writeBytes(ExcelNomina.xlsx(e.st, e.incl, e.st.emps, e.desde, e.hasta))
        }
    }
}
