package co.com.calculadoradesalarios.ui

import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.datos.NominaRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.time.LocalDate

val HoyPrueba: LocalDate = LocalDate.of(2026, 11, 7)

/** Repositorio sobre el almacenamiento falso, ya cargado. Todo corre sin esperas. */
suspend fun TestScope.repoFalso(almacen: AlmacenFalso = AlmacenFalso(), reloj: () -> Long = { 1_790_000_000_000L }): NominaRepository =
    NominaRepository(almacen, backgroundScope, UnconfinedTestDispatcher(testScheduler), relojMs = reloj, hoy = { HoyPrueba }).also { it.cargar() }
