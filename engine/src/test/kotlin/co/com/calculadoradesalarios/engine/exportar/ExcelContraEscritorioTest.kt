package co.com.calculadoradesalarios.engine.exportar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * El Excel de Android contra el que genera el escritorio (ClosedXML) con los mismos datos: mismas
 * hojas, mismas celdas con el mismo tipo y valor, anchos de columna y encabezado en negrita.
 */
class ExcelContraEscritorioTest {
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3, 4])
    fun mismoLibroQueElEscritorio(i: Int) {
        val e = Escenarios.todos[i]
        val nuestro = LectorXlsx(ExcelNomina.xlsx(e.st, e.incl, e.st.emps, e.desde, e.hasta))
        val escritorio = LectorXlsx(Escenarios.recurso("TestData/exportaciones/${e.nombre}.xlsx"))

        assertEquals(escritorio.hojas.map { it.first }, nuestro.hojas.map { it.first })
        for ((hoja, _) in escritorio.hojas) {
            val a = escritorio.celdas(hoja)
            val b = nuestro.celdas(hoja)
            assertEquals(a.keys, b.keys, "$hoja: mismas celdas")
            for ((ref, va) in a) {
                val vb = b.getValue(ref)
                when (va) {
                    is Double -> {
                        if (vb !is Double) fail<Unit>("${e.nombre} $hoja!$ref: número esperado, hay $vb")
                        assertEquals(va, vb as Double, 1e-9, "${e.nombre} $hoja!$ref")
                    }
                    else -> assertEquals(va, vb, "${e.nombre} $hoja!$ref")
                }
            }
            val anchos = escritorio.anchos(hoja)
            nuestro.anchos(hoja).forEach { (col, w) -> anchos[col]?.let { assertEquals(it, w, 0.01, "$hoja ancho col $col") } }
            assertTrue(nuestro.negrita(hoja, "A1"), "$hoja: encabezado en negrita")
            assertTrue(escritorio.negrita(hoja, "A1"))
        }
    }
}
