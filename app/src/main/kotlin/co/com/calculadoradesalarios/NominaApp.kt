package co.com.calculadoradesalarios

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import co.com.calculadoradesalarios.datos.NominaRepository
import co.com.calculadoradesalarios.storage.AppStorage
import co.com.calculadoradesalarios.storage.CarpetaExtraSaf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NominaApp : Application() {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repo: NominaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = NominaRepository(AppStorage(this, CarpetaExtraSaf(this)), alcance, Dispatchers.IO)
        alcance.launch {
            repo.cargar()
            repo.respaldarSiCorresponde()
        }
        // Al pasar a segundo plano: termina de guardar y respalda si corresponde (como al cerrar en el escritorio).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                alcance.launch { repo.respaldarSiCorresponde() }
            }
        })
    }
}
