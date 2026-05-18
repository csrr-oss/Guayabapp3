package com.geofield

import android.Manifest
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
import com.geofield.navigation.GuayabappNavGraph // Importación corregida 
import com.geofield.ui.PantallaPermisos

class MainActivity : ComponentActivity() {

    private lateinit var locationRepo: LocationRepository
    private var onPermisosResultado: ((Boolean) -> Unit)? = null

    private val permisosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        val gpsOk = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    resultados[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val camaraOk = resultados[Manifest.permission.CAMERA] == true
        onPermisosResultado?.invoke(gpsOk && camaraOk)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationRepo = LocationRepository(applicationContext)
        setContent {
            // Usamos MaterialTheme directo para evitar conflictos si el archivo GeoFieldTheme no se ha renombrado
            MaterialTheme { 
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
}

@Composable
fun GuayabappApp() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? MainActivity
    
    val repo = remember { LocationRepository(context) }
    var permisosOk by remember { mutableStateOf(repo.tienePermisos()) }

    if (!permisosOk) {
        PantallaPermisos(onSolicitarPermisos = {
            activity?.solicitarPermisos { ok -> permisosOk = ok }
        })
    } else {
        GuayabappNavGraph(navController = navController) // Llamado corregido sin parámetros obsoletos 
    }
}
