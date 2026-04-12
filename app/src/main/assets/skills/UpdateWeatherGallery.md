# Skill: Update Weather Gallery

## Propósito
Esta habilidad instruye a E.M.M.A. (y motores satélite de visión/generación de imágenes) sobre cómo actualizar el fondo termal del clima del Dashboard en tiempo real utilizando imágenes generadas.

## Funcionamiento Técnico
El `ThermalWeatherWidget` lee imágenes estáticas nativas dentro del entorno de la aplicación. Actualmente apuntan a IDs de recursos (`R.drawable.weather_sunny...`). 

Para que la IA actualice esto dinámicamente en futuras fases:
1. Las imágenes generadas por *Stable Diffusion* o similares deben guardarse en el almacenamiento local del dispositivo (ej: `Context.getFilesDir() + "/WeatherPacks/"`).
2. El widget de Compose deberá apuntar a la ruta del archivo con `AsyncImage(model = File(weatherPackDir, "sunny_new.png"))` en lugar de `R.drawable`.
3. Nomenclatura Estricta: Las imágenes generadas deben llevar el prefijo del estado del clima:
   - `weather_sunny_*.png`
   - `weather_rain_*.png`
   - `weather_cloudy_*.png`

## Acciones del Agente
- Cuando el usuario solicite "actualiza la vista del clima a lo que ves por la cámara", el agente debe generar/tomar la foto, renombrarla usando el prefijo correcto y depositarla en el directorio activo del *WeatherPack*. El Widget se re-renderizará automáticamente al detectar cambios de estado.
