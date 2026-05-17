# Guayabapp 🗺️

App Android de levantamiento georeferenciado de campo.

## ⚠️ Paso necesario antes de compilar

El archivo `gradle/wrapper/gradle-wrapper.jar` no está incluido en el repositorio
(es un binario que git no debe versionar según las buenas prácticas de Gradle).

**Para generarlo localmente (si tienes Gradle instalado):**
```bash
gradle wrapper --gradle-version 8.9
```

**Para GitHub Actions:** el workflow usa `gradle/actions/setup-gradle` que
descarga Gradle 8.9 automáticamente — no necesitas el JAR.

**Para Android Studio:** al abrir el proyecto, Android Studio descarga
el wrapper automáticamente al hacer Sync.

## Compilar con GitHub Actions

1. Subir este proyecto a un repositorio GitHub
2. Ir a pestaña **Actions**
3. El workflow "Build Guayabapp APK" corre automáticamente
4. Descargar el APK desde **Artifacts**

## Stack

- Kotlin + Jetpack Compose
- Room (SQLite offline-first)
- OSMDroid + OpenStreetMap (gratis, sin API key)
- CameraX (foto + video con GPS en Exif)
- FusedLocationProviderClient (GPS)
- Exportación KML → Google Earth
