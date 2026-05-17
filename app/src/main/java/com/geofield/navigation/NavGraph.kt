package com.geofield.navigation

import androidx.compose.runtime.*
import androidx.navigation.*
import androidx.navigation.compose.*
import com.geofield.ui.*

sealed class Ruta(val path: String) {
    object Splash        : Ruta("splash")
    object Proyectos     : Ruta("proyectos")
    object NuevoProyecto : Ruta("nuevo_proyecto")
    object Visor         : Ruta("visor/{proyectoId}") { fun conId(id: Long) = "visor/$id" }
    object Configuracion : Ruta("configuracion/{proyectoId}") { fun conId(id: Long) = "configuracion/$id" }
    object Offline       : Ruta("offline") }

@Composable
fun GeoFieldNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Ruta.Splash.path) {

        composable(Ruta.Splash.path) {
            SplashScreen(onListo = {
                navController.navigate(Ruta.Proyectos.path) {
                    popUpTo(Ruta.Splash.path) { inclusive = true }
                }
            })
        }

        composable(Ruta.Proyectos.path) {
            ProyectosScreen(
                onAbrirProyecto = { id -> navController.navigate(Ruta.Visor.conId(id)) },
                onNuevoProyecto = { navController.navigate(Ruta.NuevoProyecto.path) }
            )
        }

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

        composable(
            route = Ruta.Visor.path,
            arguments = listOf(navArgument("proyectoId") { type = NavType.LongType })
        ) { backStack ->
            val proyectoId = backStack.arguments?.getLong("proyectoId") ?: return@composable
            val context = androidx.compose.ui.platform.LocalContext.current
            val db = remember { com.geofield.data.GeoFieldDatabase.getInstance(context) }
            val viewModel = remember(proyectoId) {
                MapaViewModel(db = db, proyectoId = proyectoId, context = context)
            }
            MapaVisorScreen(
                viewModel = viewModel,
                onNavegaConfiguracion = { navController.navigate(Ruta.Configuracion.conId(proyectoId)) }
            )
        }

        composable(Ruta.Offline.path) {
            PantallaGestionOffline(
                onDescargar = { _, _, _, _, _ -> },
                onCargarMbtiles = { },
                onCerrar = { navController.popBackStack() }
            )
        }

        composable(
            route = Ruta.Configuracion.path,
            arguments = listOf(navArgument("proyectoId") { type = NavType.LongType })
        ) {
            ConfiguracionScreen(onCerrar = { navController.popBackStack() })
        }
    }
}
