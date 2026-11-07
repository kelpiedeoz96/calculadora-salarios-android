package co.com.calculadoradesalarios.spike

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class SpikeCapturaTest {
    @Test
    fun claroYOscuro() {
        for ((nombre, esquema) in listOf("claro" to lightColorScheme(), "oscuro" to darkColorScheme())) {
            captureRoboImage("build/capturas/spike_$nombre.png") {
                MaterialTheme(colorScheme = esquema) {
                    Surface {
                        Card(Modifier.padding(16.dp)) { Text("Neto a pagar: $ 1.111.213", Modifier.padding(16.dp)) }
                    }
                }
            }
        }
    }
}
