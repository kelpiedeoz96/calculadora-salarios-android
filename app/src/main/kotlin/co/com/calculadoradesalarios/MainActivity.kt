package co.com.calculadoradesalarios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.com.calculadoradesalarios.ui.AppNomina

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = (application as NominaApp).repo
        setContent { AppNomina(repo) }
    }
}
