package com.geofield.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.geofield.ui.*
import com.geofield.data.GeoFieldDatabase

enum class ModoCapaBase { OSM_ESTANDAR, ESRI_SATELITE, GEO_PDF }

sealed class Ruta(val path: String) {
    object Splash        : Ruta("splash")
    object Proyectos     : Ruta("proyectos")
    object NuevoProyecto : Ruta("nuevo_proyecto")
    object Visor         : Ruta("visor/{proyectoId}") { fun conId(id: Long) = "visor/$id" }
    object Offline       : Ruta("offline")
}

@Composable
fun GuayabappNavGraph(navController: NavHostController) {
    // CORRECCIÓN PUNTO 3: Precargar los proyectos base directamente en la lista reactiva inicial
    val proyectosGlobales = remember { 
        mutableStateListOf(
            ProyectoResumen(1, "Cuenca Caño Limón", 14, 27, System.currentTimeMillis() - 3600000, ModoCapaBase.ESRI_SATELITE),
            ProyectoResumen(2, "Sector Arauca Norte", 6, 8, System.currentTimeMillis() - 86400000, ModoCapaBase.GEO_PDF),
            ProyectoResumen(3, "Levantamiento Vichada", 0, 0, System.currentTimeMillis() - 172800000, ModoCapaBase.OSM_ESTANDAR)
        )
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    NavHost(navController = navController, startDestination = Ruta.Splash.path) {
        composable(Ruta.Splash.path) {
            SplashScreen(onListo = {
                val repo = com.geofield.location.LocationRepository(context)
                if (repo.tienePermisos()) {
                    navController.navigate(Ruta.Proyectos.path) { popUpTo(Ruta.Splash.path) { inclusive = true } }
                } else {
                    navController.navigate(Ruta.Offline.path) { popUpTo(Ruta.Splash.path) { inclusive = true } }
                }
            })
        }

        composable(Ruta.Offline.path) {
            PantallaPermisos(onPermisosConcedidos = {
                navController.navigate(Ruta.Proyectos.path) { popUpTo(Ruta.Offline.path) { inclusive = true } }
            })
        }

        composable(Ruta.Proyectos.path) {
            ProyectosScreen(
                proyectos = proyectosGlobales,
                onAbrirProyecto = { id -> navController.navigate(Ruta.Visor.conId(id)) },
                onNuevoProyecto = { navController.navigate(Ruta.NuevoProyecto.path) }
            )
        }

        composable(Ruta.NuevoProyecto.path) {
            NuevoProyectoScreen(
                onCrear = { nombre, modo ->
                    val nuevoId = (proyectosGlobales.size + 1).toLong()
                    proyectosGlobales.add(ProyectoResumen(nuevoId, nombre, 0, 0, System.currentTimeMillis(), modo))
                    navController.navigate(Ruta.Proyectos.path) { popUpTo(Ruta.NuevoProyecto.path) { inclusive = true } }
                },
                onCancelar = { navController.popBackStack() }
            )
        }

        composable(
            route = Ruta.Visor.path,
            arguments = listOf(navArgument("proyectoId") { type = NavType.LongType })
        ) { backStack ->
            val proyectoId = backStack.arguments?.getLong("proyectoId") ?: 1L
            val db = remember { GeoFieldDatabase.getInstance(context) }
            val factory = remember(proyectoId) {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return MapaViewModel(db = db, proyectoId = proyectoId, context = context) as T
                    }
                }
            }
            val viewModel: MapaViewModel = viewModel(factory = factory)
            MapaVisorScreen(viewModel = viewModel, onNavegaConfiguracion = { navController.popBackStack() })
        }
    }
}
