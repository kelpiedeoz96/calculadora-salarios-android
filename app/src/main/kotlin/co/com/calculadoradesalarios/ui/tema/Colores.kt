package co.com.calculadoradesalarios.ui.tema

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import co.com.calculadoradesalarios.engine.Cat

/** Esquemas fijos con el turquesa de marca del escritorio (#56C1C8). Sin color dinámico. */
val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF006A70), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEF0), onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8EA), onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFF4B607C), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3E4FF), onTertiaryContainer = Color(0xFF041C35),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FAFA), onBackground = Color(0xFF191C1C),
    surface = Color(0xFFF7FAFA), onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE4E5), onSurfaceVariant = Color(0xFF3F4849),
    outline = Color(0xFF6F7979), outlineVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F4F4),
    surfaceContainer = Color(0xFFEBEFEF), surfaceContainerHigh = Color(0xFFE5E9E9),
    surfaceContainerHighest = Color(0xFFE0E3E3),
)

val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFF56C1C8), onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F54), onPrimaryContainer = Color(0xFF9DF0F7),
    secondary = Color(0xFFB1CBCE), onSecondary = Color(0xFF1B3437),
    secondaryContainer = Color(0xFF324B4D), onSecondaryContainer = Color(0xFFCCE8EA),
    tertiary = Color(0xFFB3C8E8), onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863), onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1414), onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF0F1414), onSurface = Color(0xFFE0E3E3),
    surfaceVariant = Color(0xFF3F4849), onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF899393), outlineVariant = Color(0xFF3F4849),
    surfaceContainerLowest = Color(0xFF0A0F0F), surfaceContainerLow = Color(0xFF171C1C),
    surfaceContainer = Color(0xFF1B2020), surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF303535),
)

@Immutable
data class ParColor(val fondo: Color, val texto: Color)

/** Nombres cortos para chips y leyendas; mismas siglas que el escritorio. */
val NombreCategoria: Map<Cat, String> = mapOf(
    Cat.ODiu to "Ordinaria diurna",
    Cat.ONoc to "Recargo nocturno",
    Cat.ODom to "Dominical/festivo",
    Cat.ONocDom to "Nocturno dominical",
    Cat.HED to "Extra diurna",
    Cat.HEN to "Extra nocturna",
    Cat.HEDDom to "Extra diurna dominical",
    Cat.HENDom to "Extra nocturna dominical",
)

/**
 * Pares fondo/texto de las 8 categorías (ColorTokens.axaml del escritorio) con tres ajustes para
 * cumplir WCAG AA y no repetir colores:
 * - HEN claro: texto #D84315 -> #A63D17 (3,8:1 -> 5,4:1).
 * - HENDom claro: fondo #FCE4EC -> #F8E1F4 (era igual al de ODom).
 * - HENDom oscuro: texto #F48FB1 -> #E1A3C8 (era igual al de ODom).
 */
object ColoresCategoria {
    val claro: Map<Cat, ParColor> = mapOf(
        Cat.ODiu to ParColor(Color(0xFFE0F2F1), Color(0xFF00695C)),
        Cat.ONoc to ParColor(Color(0xFFEDE7F6), Color(0xFF4527A0)),
        Cat.ODom to ParColor(Color(0xFFFCE4EC), Color(0xFFAD1457)),
        Cat.ONocDom to ParColor(Color(0xFFF3E5F5), Color(0xFF6A1B9A)),
        Cat.HED to ParColor(Color(0xFFFFF3E0), Color(0xFFBF360C)),
        Cat.HEN to ParColor(Color(0xFFFBE9E7), Color(0xFFA63D17)),
        Cat.HEDDom to ParColor(Color(0xFFFFEBEE), Color(0xFFB71C1C)),
        Cat.HENDom to ParColor(Color(0xFFF8E1F4), Color(0xFF880E4F)),
    )
    val oscuro: Map<Cat, ParColor> = mapOf(
        Cat.ODiu to ParColor(Color(0xFF1A353A), Color(0xFF84DFE5)),
        Cat.ONoc to ParColor(Color(0xFF251E38), Color(0xFFB39DDB)),
        Cat.ODom to ParColor(Color(0xFF361D29), Color(0xFFF48FB1)),
        Cat.ONocDom to ParColor(Color(0xFF371E36), Color(0xFFCE93D8)),
        Cat.HED to ParColor(Color(0xFF352413), Color(0xFFFFCC80)),
        Cat.HEN to ParColor(Color(0xFF361E1A), Color(0xFFFFAB91)),
        Cat.HEDDom to ParColor(Color(0xFF38181B), Color(0xFFEF9A9A)),
        Cat.HENDom to ParColor(Color(0xFF35162B), Color(0xFFE1A3C8)),
    )
}

/** Contraste WCAG 2.x entre dos colores opacos. */
fun contraste(a: Color, b: Color): Double {
    fun canal(c: Float): Double = if (c <= 0.03928f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    fun lum(c: Color) = 0.2126 * canal(c.red) + 0.7152 * canal(c.green) + 0.0722 * canal(c.blue)
    val (claro, oscuro) = listOf(lum(a), lum(b)).sortedDescending()
    return (claro + 0.05) / (oscuro + 0.05)
}
