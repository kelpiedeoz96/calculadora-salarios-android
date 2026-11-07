package co.com.calculadoradesalarios.ui

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import co.com.calculadoradesalarios.R
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ícono adaptativo como lo recorta Android (72 dp visibles de la capa de 108 dp), con varias máscaras. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w600dp-h220dp-xxhdpi", application = Application::class)
class IconoTest {
    @Composable
    private fun Icono(forma: Shape, mono: Color? = null, fondo: Color? = null) {
        val escala = 1.6f
        Box(Modifier.size((72 * escala).dp).clip(forma).then(if (fondo != null) Modifier.background(fondo) else Modifier), contentAlignment = Alignment.Center) {
            if (mono == null) Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize((108 * escala).dp))
            Image(
                painterResource(if (mono == null) R.drawable.ic_launcher_foreground else R.drawable.ic_launcher_monochrome), null,
                Modifier.requiredSize((108 * escala).dp), colorFilter = mono?.let { ColorFilter.tint(it) },
            )
        }
    }

    @Test
    fun icono() {
        captureRoboImage("build/capturas/fase6/icono.png") {
            Row(Modifier.background(Color(0xFFE8ECEC)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icono(CircleShape)
                Icono(RoundedCornerShape(28))
                Icono(CircleShape, mono = Color(0xFF0E5A61), fondo = Color(0xFFCDEEF0))
                Icono(CircleShape, mono = Color(0xFFCDEEF0), fondo = Color(0xFF1B2020))
            }
        }
    }
}
