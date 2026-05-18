package com.geofield

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.geofield.location.LocationForegroundService
import com.geofield.location.LocationRepository
import com.geofield.navigation.GuayabappNavGraph // CORRECCIÓN: Enlace al nuevo sistema unificado
import com.geofield.theme.GuayabappTheme // Importación del nuevo tema corporativo Nunito
import com.geofield.ui.PantallaPermisos

class MainActivity : ComponentActivity() {

    private lateinit var locationRepo: LocationRepository
    private var onPermisosResultado: ((Boolean) -> Unit)? = null

    // Lanzador procedural para solicitudes múltiples en caliente (Ubicación, Cámara y Audio)
    private val permisosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        val gpsOk = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    resultados[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val camaraOk = resultados[Manifest.permission.CAMERA] == true
        
        // Se valida el éxito conjunto de permisos críticos para la operación de campo
        onPermisosResultado?.invoke(gpsOk && camaraOk)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Asignación del gancho estático para dar soporte de hilos a CameraX (.await())
        contextInstance = this
        
        locationRepo = LocationRepository(applicationContext)
        setContent {
            GuayabappTheme { // Sistema Nunito unificado [cite: 12]
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GuayabappApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Si el operador concede permisos, el ForegroundService se levanta inmediatamente al reanudar la app
        if (locationRepo.tienePermisos()) {
            LocationForegroundService.iniciar(this)
        }
    }

    fun solicitarPermisos(onResultado: (Boolean) -> Unit) {
        onPermisosResultado = onResultado
        permisosLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        ))
    }

    override fun onDestroy() {
        super.onDestroy()
        // Destrucción limpia del gancho estático para evitar Memory Leaks
        if (contextInstance == this) {
            contextInstance = null
        }
    }

    // CORRECCIÓN ACTIONS: Expone la instancia para destrabar la compilación KSP de la cámara
    companion object {
        @Volatile
        var contextInstance: Context? = null
            private set
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CONTENEDOR RAÍZ DE ENTRADA (SOPORTE TOTALMENTE ADAPTATIVO)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun GuayabappApp() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? MainActivity
    
    // Instanciación estable del repositorio de validación
    val repo = remember { LocationRepository(context) }
    var permisosOk by remember { mutableStateOf(repo.tienePermisos()) }

    if (!permisosOk) {
        // Si faltan permisos, bloqueamos el acceso al visor forzando el diálogo técnico [cite: 11]
        PantallaPermisos(onSolicitarPermisos = {
            activity?.solicitarPermisos { ok -> permisosOk = ok }
        })
    } else {
        // CORRECCIÓN: Invocación estricta de la firma corregida sin parámetros obsoletos 
        GuayabappNavGraph(navController = navController)
    }
}
