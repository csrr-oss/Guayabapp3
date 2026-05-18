package com.geofield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.geofield.location.LocationForegroundService
import com.geofield.location.LocationRepository
import com.geofield.navigation.GuayabappNavGraph

class MainActivity : ComponentActivity() {

    private lateinit var locationRepo: LocationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationRepo = LocationRepository(applicationContext)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // El enrutador ahora toma el control total desde el segundo cero
                    val navController = rememberNavController()
                    GuayabappNavGraph(navController = navController)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Si el usuario ya concedió los permisos en la UI, el servicio se levanta al regresar
        if (locationRepo.tienePermisos()) {
            LocationForegroundService.iniciar(this)
        }
    }
}
