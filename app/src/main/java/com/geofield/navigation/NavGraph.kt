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
// ─── NAVGRAPH UNIFICADO Y CORREGIDO (SOPORTE COMPLEMENTARIO DE CICLO DE VÍA) ────
// ================================================================================

@Composable
fun GuayabappNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Ruta.Splash.path) {

        // 1. Pantalla de Presentación (Splash)
        composable(Ruta.Splash.path) {
            SplashScreen(onListo = {
                navController.navigate(Ruta.Proyectos.path) {
                    popUpTo(Ruta.Splash.path) { inclusive = true }
                }
            })
        }

        // 2. Panel de Selección de Proyectos Independientes
        composable(Ruta.Proyectos.path) {
            ProyectosScreen(
                onAbrirProyecto = { id -> navController.navigate(Ruta.Visor.conId(id)) },
                onNuevoProyecto = { navController.navigate(Ruta.NuevoProyecto.path) }
            )
        }

        // 3. Formulario de Creación de Proyectos
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

        // 4. ARCHIVO CENTRAL: Visor Cartográfico (Con Inyección Segura de ViewModel)
        composable(
            route = Ruta.Visor.path,
            arguments = listOf(navArgument("proyectoId") { type = NavType.LongType })
        ) { backStack ->
            val proyectoId = backStack.arguments?.getLong("proyectoId") ?: return@composable
            val context = androidx.compose.ui.platform.LocalContext.current
            
            // Instanciación persistente y segura de la Base de Datos Room
            val db = remember { GeoFieldDatabase.getInstance(context) }
            
            // CORRECCIÓN CRÍTICA: Factory nativa para inyectar dependencias sin romper el ciclo de vida al rotar
            val factory = remember(proyectoId) {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return MapaViewModel(db = db, proyectoId = proyectoId, context = context) as T
                    }
                }
            }
            
            // Vinculamos de manera estable el ViewModel al ciclo del backStack activo
            val viewModel: MapaViewModel = viewModel(factory = factory)

            MapaVisorScreen(
                viewModel = viewModel,
                onNavegaConfiguracion = { navController.navigate(Ruta.Configuracion.conId(proyectoId)) }
            )
        }

        // 5. Gestión Cartográfica Offline (.mbtiles / Regiones)
        composable(Ruta.Offline.path) {
            PantallaGestionOffline(
                onDescargar = { _, _, _, _, _ -> },
                onCargarMbtiles = { },
                onCerrar = { navController.popBackStack() }
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
