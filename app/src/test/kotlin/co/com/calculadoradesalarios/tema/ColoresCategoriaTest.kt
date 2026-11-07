package co.com.calculadoradesalarios.tema

import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.ui.tema.ColoresCategoria
import co.com.calculadoradesalarios.ui.tema.contraste
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColoresCategoriaTest {
    private val temas = mapOf("claro" to ColoresCategoria.claro, "oscuro" to ColoresCategoria.oscuro)

    @Test
    fun las8Categorias_TienenParEnAmbosTemas() {
        temas.values.forEach { assertEquals(Cat.entries.toSet(), it.keys) }
    }

    @Test
    fun los16Pares_CumplenWcagAA() {
        for ((tema, pares) in temas) for ((cat, par) in pares) {
            val c = contraste(par.fondo, par.texto)
            assertTrue("$tema $cat: contraste %.2f < 4.5".format(c), c >= 4.5)
        }
    }

    @Test
    fun ningunaCategoria_RepiteFondoOTextoDentroDelMismoTema() {
        for ((tema, pares) in temas) {
            assertEquals("$tema: fondos repetidos", 8, pares.values.map { it.fondo }.toSet().size)
            assertEquals("$tema: textos repetidos", 8, pares.values.map { it.texto }.toSet().size)
        }
    }

    @Test
    fun contraste_ValoresConocidos() {
        assertEquals(21.0, contraste(Color.White, Color.Black), 0.01)
        assertEquals(1.0, contraste(Color.White, Color.White), 0.0001)
    }
}
