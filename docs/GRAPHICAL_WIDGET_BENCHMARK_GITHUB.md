# Auditoría gráfica y benchmark de widgets de calendario Android

Fecha: 2026-10-27. Rama examinada: `ics-subscriptions`.

## Resultado ejecutivo

El benchmark recomienda mantener el motor de Etar y rediseñar primero la presentación del widget de agenda, después el mensual. Las referencias de mayor valor práctico son MinCal para cuadrícula mensual, Todo Agenda para agenda configurable, y Calander/Asala para enfoque adaptable y visual moderno. No todos los repositorios examinados son widgets completos; las librerías se incluyen como referencias parciales. No se afirma que sean los 20 mejores por popularidad: son 20 proyectos/puntos de comparación útiles para la decisión de diseño.

## 1. Inventario del widget actual de Etar

En la rama inspeccionada existen tres familias de widget: agenda, mes y semana. Archivos principales:

- `app/src/main/res/layout/appwidget.xml`: cabecera con día y fecha, botón de añadir, lista de eventos.
- `app/src/main/res/layout/widget_item.xml`: cada evento tiene título, hora/fecha y ubicación, con tamaño de texto declarado de 12sp y título de hasta dos líneas.
- `app/src/main/res/layout/appwidget_loading.xml` y `appwidget_no_events.xml`: estados de carga y sin eventos.
- `app/src/main/res/layout/month_appwidget.xml` y `month_appwidget_cell.xml`: cuadrícula mensual.
- `app/src/main/res/layout/week_appwidget.xml` y `week_appwidget_cell.xml`: cuadrícula semanal.
- `app/src/main/res/xml/appwidget_info.xml`: el widget de agenda se puede redimensionar horizontal y verticalmente; `updatePeriodMillis=0`, por lo que la actualización depende del mecanismo del proveedor, no de un temporizador periódico declarado en ese XML.
- `app/src/main/java/com/android/calendar/widget/CalendarAppWidgetProvider.java`, `MonthAppWidgetProvider.java`, `WeekAppWidgetProvider.java`: proveedores separados por tipo.

Diagnóstico visual inicial del código: la estructura ya tiene piezas útiles, pero la agenda hereda una presentación clásica. La fila coloca título, cuándo y dónde en vertical; esto puede consumir demasiada altura en widgets bajos. La cabecera mínima de 48dp es relativamente alta para una superficie compacta. Los cambios deben ser graduales y contrastarse en el dispositivo real: no es posible inferir apariencia final únicamente leyendo XML.

## 2. Benchmark de 20 repositorios/proyectos

| # | Proyecto | Qué aporta al benchmark | Uso recomendado |
|---:|---|---|---|
| 1 | [Etar fork](https://github.com/Manolobernales/Etar-Calendar/tree/ics-subscriptions) | Base real, tres widgets y datos del proveedor Android | Mantener como base |
| 2 | [Etar upstream](https://github.com/Etar-Group/Etar-Calendar) | Comparación de cambios y decisiones de origen | Control de regresiones |
| 3 | [MinCal Widget](https://github.com/mvmike/min-cal-widget) | Mes 3×2 redimensionable, marcas de eventos, foco en semana actual, selección de calendarios, tema, transparencia y texto | Referencia principal para mes |
| 4 | [Todo Agenda](https://github.com/andstatus/todoagenda) | Agenda desplazable, varios widgets configurables, filtros, colores, transparencia, tamaño de texto y alternativas de layout | Referencia principal para agenda |
| 5 | [Todo Agenda fork CostCost](https://github.com/CostCost/todoagenda) | Contraste de variante/fork | Consultar diferencias antes de copiar |
| 6 | [Calendar Widget / plusonelabs](https://github.com/plusonelabs/calendar-widget) | Widget dedicado y opciones de presentación | Referencia secundaria de agenda |
| 7 | [Calander](https://github.com/rollounden/Calander) | Widget Glance adaptable: 2×2 con fecha y próximo evento; formato grande con agenda de siete días; observa cambios del proveedor | Referencia de diseño responsivo |
| 8 | [Asala Calendar](https://github.com/Arishawke/asala-calendar) | Agenda y mes, temas claros/oscuros/AMOLED, colores por evento y ajuste tipográfico | Referencia de coherencia visual |
| 9 | [Android Month Calendar Widget](https://github.com/romannurik/Android-MonthCalendarWidget) | Ejemplo de cuadrícula mensual como widget | Patrones de tamaño |
| 10 | [Fossify Calendar](https://github.com/FossifyOrg/Calendar) | Diseño general de calendario open source | Referencia de tipografía/colores; confirmar widget actual |
| 11 | [Simple Calendar](https://github.com/SimpleMobileTools/Simple-Calendar) | Patrones de calendario clásico | Referencia secundaria; revisar mantenimiento |
| 12 | [Material Calendar View](https://github.com/Applandeo/Material-Calendar-View) | Estados y selección de días en una vista de calendario | Referencia parcial, no asumir widget completo |
| 13 | [mCalendarView](https://github.com/SpongeBobSun/mCalendarView) | Composición de calendario | Referencia parcial |
| 14 | [calendar_view](https://github.com/laserwave/calendar_view) | Distribución de días | Referencia parcial |
| 15 | [Tripadvisor android-calendarview](https://github.com/tripadvisor/android-calendarview) | Patrones de vista/selección | Referencia parcial |
| 16 | [karabaralex android-calendar-view](https://github.com/karabaralex/android-calendar-view) | Vista de calendario auxiliar | Referencia parcial |
| 17 | [dharmin007 Android Calendar Widget](https://github.com/dharmin007/Android-Calendar-Widget) | Implementación de widget histórica | Solo comparación histórica |
| 18 | [Jetpack Glance samples](https://github.com/android/compose-samples) | Enfoque moderno de widgets declarativos; revisar ejemplos y compatibilidad exactos | Ideas de implementación, no copiar sin adaptar |
| 19 | [Android App Widgets docs](https://developer.android.com/develop/ui/views/appwidgets) | Límites de RemoteViews, tamaños y actualizaciones | Restricciones de plataforma |
| 20 | [Jetpack Glance docs](https://developer.android.com/develop/ui/compose/glance) | Alternativa técnica para widgets | Evaluar solo si el rediseño lo justifica |

### Calidad de la evidencia

Los README de MinCal, Todo Agenda, Calander y Asala describen funciones concretas citadas arriba. Los proyectos de vista de calendario, documentación y muestras no se consideran equivalentes a widgets completos. La actividad, compatibilidad con versiones actuales, licencia y capturas deben verificarse individualmente antes de portar código. La auditoría no copia código ni recursos de terceros; extrae patrones de diseño.

## 3. Comparación funcional y visual

| Criterio | MinCal | Todo Agenda | Calander | Asala | Etar actual |
|---|---|---|---|---|---|
| Mes minimalista | Fuerte | No es su foco principal | Sí, en la app; widget orientado a agenda | Sí | Widget mensual ya existe |
| Agenda desplazable | No es el foco | Fuerte | Sí, 7 días en tamaño grande | Widget de agenda | Agenda existente |
| Varios calendarios | Sí | Sí, selección por widget | Usa proveedor del sistema | Usa proveedor del sistema | Sí, depende de calendarios visibles en proveedor |
| Personalización de tamaño/tema | Alta para mes | Alta | Menos énfasis en ajustes; adaptable | Temas y tamaño de texto | Debe verificarse por configuración actual |
| Respuesta a cambios del proveedor | Comprobar implementación | README declara actualización automática | README describe observación de cambios | Sistema compartido; verificar widget | Revisar providers/servicios en pruebas |
| Recomendación | Inspirar mensual | Inspirar agenda | Inspirar tamaño pequeño/grande | Inspirar estilo | Base a mejorar, no reemplazar |

## 4. Diseño propuesto para nuestro widget

### Agenda

- Cabecera compacta con día y fecha, sin ocupar altura innecesaria.
- Hora en una columna estable; título con mayor jerarquía tipográfica.
- Indicador fino de color del calendario, sin colorear bloques enteros salvo que el usuario lo elija.
- Ubicación como segunda línea opcional; omitirla si está vacía o no cabe.
- Eventos de día completo etiquetados como tales, nunca con hora inventada.
- Separación por espacio y líneas sutiles; reducir bordes y cajas pesadas.
- Estados visuales distintos: cargando, vacío, permisos denegados y error.

### Mes

- Número del día como foco visual, puntos discretos para eventos.
- Hoy, día seleccionado y día con eventos deben tener tratamientos diferentes.
- Fin de semana sutil, semana actual opcionalmente resaltada.
- Máximo limitado de marcas por día; cuando haya muchos eventos, usar indicador agregado.
- Toque en un día abre esa fecha en Etar; la cabecera abre el calendario.

### Tamaños

- Pequeño: fecha de hoy y próximo evento.
- Mediano: agenda de hoy/mañana con títulos legibles.
- Grande: agenda de siete días agrupada por fecha.
- Mes: cuadrícula de seis semanas cuando haga falta, con marcas compactas.
- Mantener compatibilidad con redimensionamiento horizontal y vertical, con texto legible en vez de escalar todos los elementos proporcionalmente.

### Apariencia y accesibilidad

- Tema claro, oscuro y transparente con contraste probado sobre fondos variados.
- Usar unidades sp para texto y dp para espaciado; comprobar tamaño de fuente del sistema.
- No usar solo color para indicar calendario: combinar color con hora/título y, si resulta útil, una etiqueta corta.
- Área táctil suficiente para abrir evento o día; no añadir controles pequeños que no sean esenciales.
- Probar con títulos largos, nombres de calendario extensos, eventos solapados y ubicaciones largas.

## 5. Riesgos y restricciones técnicas

1. Los widgets Android usan un entorno de renderizado más limitado que una pantalla normal; no conviene asumir que cualquier componente de Compose puede trasladarse directamente.
2. Cambiar a Jetpack Glance sería una decisión arquitectónica, no un simple retoque de estilos. Primero conviene mejorar los XML/RemoteViews existentes.
3. No depender de refresco periódico demasiado frecuente. Verificar el mecanismo actual de actualización, cambios del proveedor, cambio de fecha, zona horaria y reinicio del dispositivo.
4. Los eventos recurrentes deben venir expandidos correctamente por Calendar Provider; no volver a implementar recurrencias en la capa gráfica.
5. No confundir feed ICS con calendario autenticado/bidireccional: la apariencia no debe ocultar la fuente o frescura de los datos.
6. Las licencias de los repositorios son distintas. Copiar código requiere revisión de licencia y atribuciones; adaptar ideas visuales no implica copiar assets.

## 6. Plan de evaluación reproducible

### Matriz de pruebas

| Dimensión | Casos mínimos | Criterio de aceptación |
|---|---|---|
| Tamaño | pequeño, mediano, grande; ancho y alto redimensionados | Sin solapamientos ni huecos inexplicables |
| Tema | claro, oscuro, transparente sobre fondo claro y oscuro | Contraste y legibilidad adecuados |
| Datos | 0, 1, 5 y muchos eventos; título/ubicación largos | No se cortan datos esenciales; truncamiento coherente |
| Tipos | día completo, multidía, recurrente, evento con ubicación | Hora y fecha correctas; etiqueta de día completo |
| Calendarios | Google, Outlook sincronizado, calendario local, oculto | Solo aparecen los seleccionados y sus colores se distinguen |
| Estados | sin permisos, carga, error de proveedor, sin eventos | Mensajes distintos y recuperables |
| Ciclo de vida | cambio de evento, cambio de día, reinicio, modo ahorro | Actualización eventual sin quedarse obsoleto indefinidamente |
| Accesibilidad | fuente grande, contraste, idioma español, inicio de semana configurable | No se pierde información crítica |

### Puntuación visual sugerida

Evaluar cada variante del 1 al 5 en: legibilidad a primera vista (25%), jerarquía visual (20%), adaptación al tamaño (20%), contraste/accesibilidad (15%), personalización (10%) y coherencia con el sistema Android (10%). Registrar puntuación por tamaño y tema, no solo una nota global. La puntuación final debe salir de pruebas con capturas reales, no de una impresión del README.

## 7. Orden de implementación recomendado

1. Obtener capturas reales del widget actual de agenda, mes y semana en el teléfono y registrar tamaño del lanzador.
2. Rediseñar primero `widget_item.xml` y `appwidget.xml`, manteniendo IDs y contratos usados por RemoteViews para reducir riesgo.
3. Ajustar recursos de color/dimensiones/estilos; evitar colores hardcodeados donde haya recursos existentes.
4. Actualizar preview del widget y los estados de carga/sin eventos.
5. Compilar en GitHub Actions y revisar logs; no considerar éxito hasta ver una ejecución verde.
6. Instalar y validar en el teléfono, especialmente redimensionamiento y fondos.
7. Rediseñar mes y semana en cambios separados.
8. Solo después evaluar si un widget Glance nuevo aporta suficiente valor como para justificar una segunda arquitectura.

## Fuentes principales

- Etar fork: https://github.com/Manolobernales/Etar-Calendar/tree/ics-subscriptions
- Etar upstream: https://github.com/Etar-Group/Etar-Calendar
- MinCal README: https://github.com/mvmike/min-cal-widget
- Todo Agenda README: https://github.com/andstatus/todoagenda
- Calander README: https://github.com/rollounden/Calander
- Asala README: https://github.com/Arishawke/asala-calendar
- PlusOneLabs: https://github.com/plusonelabs/calendar-widget
- Android Month Calendar Widget: https://github.com/romannurik/Android-MonthCalendarWidget
- Fossify Calendar: https://github.com/FossifyOrg/Calendar
- Simple Calendar: https://github.com/SimpleMobileTools/Simple-Calendar
- Material Calendar View: https://github.com/Applandeo/Material-Calendar-View
- mCalendarView: https://github.com/SpongeBobSun/mCalendarView
- Tripadvisor calendarview: https://github.com/tripadvisor/android-calendarview
- Android widgets: https://developer.android.com/develop/ui/views/appwidgets
- Jetpack Glance: https://developer.android.com/develop/ui/compose/glance

## Estado

Esta auditoría deja documentados el inventario del código de Etar, las referencias, la decisión de diseño y el plan de validación. No implica que el rediseño esté ya programado ni que la compilación se haya verificado.