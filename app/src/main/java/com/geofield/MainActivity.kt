package com.geofield

import android.content.pm.ActivityInfo
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
        // CORRECCIÓN ATÓMICA 1: Forzar vertical ANTES de que el sistema operativo intente renderizar la ventana
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        
        locationRepo = LocationRepository(applicationContext)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    GuayabappNavGraph(navController = navController)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (locationRepo.tienePermisos()) {
            try { LocationForegroundService.iniciar(this) } catch (_: Exception) {}
        }
    }
}
