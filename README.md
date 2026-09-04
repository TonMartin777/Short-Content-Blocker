# Short Content Blocker

*   Este proyecto es una aplicación móvil desarrollada en Android Studio con el lenguaje Kotlin. Permite al usuario limitar el uso diario tanto de Instagram como de los Reels únicamente, mejorando así su bienestar digital.
## Características Principales

*   **Límite de tiempo:** Se puede poner un límite tanto a Instagram en sí, como al contenido específico de Reels. De forma que el usuario puede usar Instagram siempre que no exceda ninguno de los dos límites.
*   **Detección de Reels:** Para poder detectar si se está viendo Reel, la aplicación detecta palabras clave. El contador sigue aumentando por mucho que se abran los comentarios del Reel.
*   **Bloqueo de la app:** Una vez configurado el límite diario, la app se cierra y no deja al usuario volver a entrar hasta que sean las 00:00 de ese mismo día. Si el usuario pudiera volver a entrar, podria cerrar la aplicación y terminar con el bloqueo.
*   **Español e Inglés:** La detección de palabras funciona únicamente con Instagram en inglés o castellano.
*   **Capas del dispositivo:** Diseñada para sobrevivir a los agresivos gestores de memoria y batería de capas de personalización como MIUI o HyperOS. Evitar que el AccessibilityService sea finalizado de forma abrupta.

## Desafíos Técnicos Resueltos

*   **Menús superpuestos:** El contador específico de reels daba ciertos problemas, como que se detenía al abrir los comentarios o que seguia aumentando pese haber salido de los reels. Para solucionarlo, se implementó una máquina de estados que memoriza el contexto del usuario junto con un búfer de tolerancia para las animaciones de la interfaz.
*   **Eliminación de falsos positivos:** La heurística daba errores al navegar por la Feed o los mensajes. Para solucionarlo la aplicación busca identificadores exclusivos de otras pantallas para evitar falsos positivos.
*   **Prevención de cierres forzados:** Dada la asincronía del segundo plano, había unos milisegundos donde el usuario puede abrir la aplicación bloqueada y forzar su cierre desde la multitarea para evadir la restricción. Se solucionó implementando sincronía al inicio de la actividad (onCreate() de MainActivity), de forma que la app se cierre automáticamente antes de llegar a abrirse.

## Próximos pasos

- [x] Heurística de la UI de Instagram y detección de Reels
- [ ] Implementación de bloqueos y reconocimiento de interfaz para YouTube Shorts.
- [ ] Desarrollo de un Widget de pantalla de inicio para poder ver los tiempos consumidos.
