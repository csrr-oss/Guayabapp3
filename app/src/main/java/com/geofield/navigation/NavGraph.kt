package com.geofield.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.geofield.ui.*
import com.geofield.data.GeoFieldDatabase

// ================================================================================
// ─── DEFINICIÓN DE RUTAS CON IDENTIDAD CORPORATIVA (GUAYABAPP) ──────────────────
// ================================================================================

sealed class Ruta(val path: String) {
    object Splash        : Ruta("splash")
    object Proyectos     : Ruta("proyectos")
    object NuevoProyecto : Ruta("nuevo_proyecto")
    object Visor         : Ruta("visor/{proyectoId}") { fun conId(id: Long) = "visor/$id" }
    object Configuracion : Ruta("configuracion/{proyectoId}") { fun conId(id: Long) = "configuracion/$id" }
    object Offline       : Ruta("offline")
}

// ================================================================================
// ─── NAVGRAPH UNIFICADO Y CORREGIDO EN FLUJO DE OPERACIONES ─────────────────────
// ================================================================================

@Composable
fun GuayabappNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Ruta.Splash.path) {

        // 1. Pantalla de Presentación (Splash) - Validador de flujo seguro
        composable(Ruta.Splash.path) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val repo = remember { com.geofield.location.LocationRepository(context) }
            
            SplashScreen(onListo = {
                // El Splash corre sus 2 segundos reglamentarios y evalúa permisos en caliente
                if (repo.tienePermisos()) {
                    navController.navigate(Ruta.Proyectos.path) {
                        popUpTo(Ruta.Splash.path) { inclusive = true }
                    }
                } else {
                    // Si no hay permisos, saltamos de inmediato a la pantalla adaptativa
                    navController.navigate(Ruta.Offline.path) {
                        popUpTo(Ruta.Splash.path) { inclusive = true }
                    }
                }
            })
        }

        // 2. Puente Seguro de Permisos Técnicos de Hardware
        composable(Ruta.Offline.path) {
            PantallaPermisos(onPermisosConcedidos = {
                navController.navigate(Ruta.Proyectos.path) {
                    popUpTo(Ruta.Offline.path) { inclusive = true }
                }
            })
        }

        // 3. Panel de Selección de Proyectos Independientes
        composable(Ruta.Proyectos.path) {
            ProyectosScreen(
                onAbrirProyecto = { id -> navController.navigate(Ruta.Visor.conId(id)) },
                onNuevoProyecto = { navController.navigate(Ruta.NuevoProyecto.path) }
            )
        }

        // 4. Formulario de Creación de Proyectos
        composable(Ruta.NuevoProyecto.path) {
            NuevoProyectoScreen(
                onCrear = { _, _ ->
                    navController.navigate(Ruta.Proyectos.path) {
                        popUpTo(Ruta.NuevoProyecto.path) { inclusive = true }
                    }
                },
                onCancelar = { navController.popBackStack() }
            )
        }

        // 5. ARCHIVO CENTRAL: Visor Cartográfico (Con Inyección Segura de ViewModel)
        composable(
            route = Ruta.Visor.path,
            arguments = listOf(navArgument("proyectoId") { type = NavType.LongType })
        ) { backStack ->
            val proyectoId = backStack.arguments?.getLong("proyectoId") ?: return@composable
            val context = androidx.compose.ui.platform.LocalContext.current
            
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

            MapaVisorScreen(
                viewModel = viewModel,
                onNavegaConfiguracion = { navController.navigate(Ruta.Configuracion.conId(proyectoId)) }
            )
        }

        // 6. Ajustes y Configuración Técnica del Proyecto
        composable(
            route = Ruta.Configuracion.path,
            arguments = listOf(navArgument("proyectoId") { type = NavType.LongType })
        ) {
            ConfiguracionScreen(onCerrar = { navController.popBackStack() })
        }
    }
}
