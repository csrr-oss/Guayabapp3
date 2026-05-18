        composable(Ruta.Splash.path) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val repo = remember { com.geofield.location.LocationRepository(context) }
            
            SplashScreen(onListo = {
                // El Splash se ejecuta completo y evalúa la redirección segura en caliente
                if (repo.tienePermisos()) {
                    navController.navigate(Ruta.Proyectos.path) {
                        popUpTo(Ruta.Splash.path) { inclusive = true }
                    }
                } else {
                    navController.navigate(Ruta.Offline.path) { // Se usa temporalmente la ruta offline como el puente seguro de la pantalla de permisos
                        popUpTo(Ruta.Splash.path) { inclusive = true }
                    }
                }
            })
        }

        // CORRECCIÓN DE ENLACE: Reemplaza la ruta offline vieja para albergar la Pantalla de Permisos adaptativa
        composable(Ruta.Offline.path) {
            PantallaPermisos(onPermisosConcedidos = {
                navController.navigate(Ruta.Proyectos.path) {
                    popUpTo(Ruta.Offline.path) { inclusive = true }
                }
            })
        }
