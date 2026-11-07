package co.com.calculadoradesalarios.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import co.com.calculadoradesalarios.engine.Cat

val LocalColoresCategoria = staticCompositionLocalOf { ColoresCategoria.claro }

/** Modo del tema tal como se guarda en data.json (ui.themeMode): "system", "light" o "dark". */
@Composable
fun esOscuro(modo: String): Boolean = when (modo) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun NominaTema(modo: String = "system", contenido: @Composable () -> Unit) {
    val oscuro = esOscuro(modo)
    CompositionLocalProvider(LocalColoresCategoria provides if (oscuro) ColoresCategoria.oscuro else ColoresCategoria.claro) {
        MaterialTheme(colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro, content = contenido)
    }
}

@Composable
fun colorCategoria(cat: Cat): ParColor = LocalColoresCategoria.current.getValue(cat)
