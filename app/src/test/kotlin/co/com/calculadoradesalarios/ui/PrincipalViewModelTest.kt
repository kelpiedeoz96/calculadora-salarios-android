package co.com.calculadoradesalarios.ui

import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.ui.principal.Pestana
import co.com.calculadoradesalarios.ui.principal.PrincipalViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrincipalViewModelTest {
    @get:Rule val main = ReglaMain()

    @Test
    fun estadoInicial_LeeTabTemaYPeriodoDeData() = runTest {
        val almacen = AlmacenFalso().apply { inicial = inicial.copy(ui = inicial.ui.copy(tab = "all", themeMode = "dark", mode = "q2")) }
        val vm = PrincipalViewModel(repoFalso(almacen)) { HoyPrueba }
        val e = vm.estado.value
        assertFalse(e.cargando)
        assertEquals(Pestana.Empleados, e.pestana)
        assertEquals("dark", e.tema)
        assertEquals("16/10/2026 – 31/10/2026", e.etiquetaPeriodo)
    }

    @Test
    fun tabDesconocido_VaAlPanel() = runTest {
        val almacen = AlmacenFalso().apply { inicial = inicial.copy(ui = inicial.ui.copy(tab = "otra")) }
        assertEquals(Pestana.Panel, PrincipalViewModel(repoFalso(almacen)) { HoyPrueba }.estado.value.pestana)
    }

    @Test
    fun navegar_GuardaElTabConElIdDelEscritorio_YAtrasDesdeAjustesVuelve() = runTest {
        val almacen = AlmacenFalso()
        val vm = PrincipalViewModel(repoFalso(almacen)) { HoyPrueba }
        vm.navegar(Pestana.Empleados)
        assertEquals("all", almacen.ultimoGuardado!!.ui.tab)
        vm.navegar(Pestana.Ajustes)
        assertEquals("cfg", almacen.ultimoGuardado!!.ui.tab)
        assertTrue(vm.atras())
        assertEquals(Pestana.Empleados, vm.estado.value.pestana)
        assertFalse("fuera de Ajustes, atrás no hace nada", vm.atras())
    }

    @Test
    fun desplazarPeriodo_CambiaDeQuincena_YNoVaAlHistorial() = runTest {
        val repo = repoFalso()
        val vm = PrincipalViewModel(repo) { HoyPrueba }
        vm.desplazarPeriodo(1)
        assertEquals("16/10/2026 – 31/10/2026", vm.estado.value.etiquetaPeriodo)
        vm.desplazarPeriodo(1)
        assertEquals("01/11/2026 – 15/11/2026", vm.estado.value.etiquetaPeriodo)
        assertEquals(0, repo.datos.value!!.history.size)
    }

    @Test
    fun aplicarPeriodo_ValidaYAplica() = runTest {
        val vm = PrincipalViewModel(repoFalso()) { HoyPrueba }
        assertEquals("Define un rango válido: AAAA-MM-DD en Desde y Hasta.", vm.aplicarPeriodo("2026-10", "custom", "", ""))
        assertNull(vm.aplicarPeriodo("2026-10", "custom", "2026-10-05", "2026-10-18"))
        val e = vm.estado.value
        assertEquals("05/10/2026 – 18/10/2026", e.etiquetaPeriodo)
        assertFalse("en rango personalizado no se desplaza", e.puedeDesplazar)
    }
}
