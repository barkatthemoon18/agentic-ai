# Evaluación de utterances — 7 de septiembre de 2026

## Método

Servidor local configurado en `http://localhost:1234/v1`, identificador expuesto
`phi-router`. Este identificador es un alias: el benchmark no verifica por sí
solo los pesos, la cuantización ni la configuración interna del servidor.
JDK 21; temperatura 0; máximo de 8 tokens de salida; sin reintentos; timeout de
60 segundos. Tres repeticiones secuenciales por variante y corpus, con una
llamada de calentamiento separada por variante. No se ejecutaron otros tests
durante las mediciones para evitar interferencia adicional de esta tarea.

`hybrid` utiliza las reglas actuales y después el modelo. `model-only` omite
esas reglas, pero mantiene el parser y la protección contra FOLLOW_UP sin
contexto. Se mide desde el texto transcrito; no incluye STT ni latencia de voz.

Los archivos originales de cada ejecución están en `target/model-evaluation`.
Cada directorio conserva el prompt exacto, SHA-256 del prompt y corpus, resultados
por ID, latencias, métricas y estabilidad. Son artefactos locales de build y
pueden eliminarse con `mvn clean`; este documento conserva las conclusiones.

## Development y selección del candidato

Resultados idénticos en las tres repeticiones de cada variante:

| Prompt | Variante | Aciertos | Macro-F1 | Errores por repetición |
|---|---|---:|---:|---:|
| Original | Híbrido | 36/36 | 1,0000 | 0 |
| Original | Sin reglas previas | 27/36 | 0,7630 | 1 |
| Revisión 1, más breve | Híbrido | 33/36 | 0,9164 | 0 |
| Revisión 1, más breve | Sin reglas previas | 22/36 | 0,5953 | 0 |

Con el prompt original, Java resuelve 26 de los 36 casos de development y el
modelo recibe 10. El híbrido tiene exactitud crítica del 100 % y cero falsas
activaciones. Sus p50 globales están entre 0,04 y 0,06 ms, pero sus p95 entre
985 y 1066 ms: el p50 global no representa la latencia del modelo. Sin reglas,
el p50 está entre 958 y 982 ms y el p95 entre 1123 y 1181 ms; la exactitud crítica
es 68,75 % y las falsas activaciones desde OTHER son 33,33 %.

La variante sin reglas falla de forma estable en preguntas con sujeto explícito
y otro tema previo, reformulaciones y ejemplos, planes personales y habla
reportada. Devuelve además una etiqueta inválida (`recommendation`) para una
petición de recomendación. Las reglas ocultan estos fallos en el corpus actual;
no demuestran que el modelo los resuelva.

La revisión 1 redujo el prompt y explicitó la precedencia de habla reportada,
planes, solicitudes independientes y referencias contextuales, usando ejemplos
distintos de los casos del corpus. El híbrido redujo su p95 a 582–597 ms, pero
confundió una corrección de la respuesta y dos descripciones de estado. Su
exactitud crítica bajó a 93,75 % y las falsas activaciones subieron a 16,67 %.
Se descartó por regresión. No se hicieron más revisiones: el original ya cumple
development y no hay evidencia que justifique sustituirlo.

Se congeló el prompt original antes de ejecutar holdout. No se cambiaron reglas,
corpus ni etiquetas, y no se ajustó el prompt a partir de holdout.

Artefactos de selección:

- Original: `target/model-evaluation/development-12096423287295246935`.
- Revisión descartada: `target/model-evaluation/development-15296485760541971322`.

La proporción de casos con cambios entre repeticiones fue cero en ambas variantes
y ambos prompts. La estabilidad incluye errores como un resultado separado: una
respuesta inválida repetida es estable, aunque incorrecta.

## Holdout final

Artefactos: `target/model-evaluation/holdout-10391373880853016373`.
SHA-256 del prompt congelado, coincidente con el original de development:
`b99e48e0eaaeb7d1d3154566c954c849db423f2d76e50f833cb77ad8c521cfcf`.

| Variante | Aciertos en cada repetición | Macro-F1 | Exactitud crítica | Falsas activaciones OTHER | Errores |
|---|---:|---:|---:|---:|---:|
| Híbrido | 36/36 | 1,0000 | 100 % | 0 % | 0 |
| Sin reglas previas | 25/36 | 0,6984 | 66,67 % | 33,33 % | 0 |

El híbrido pasa los umbrales en sus tres repeticiones: recall 1,0 por etiqueta,
cero FOLLOW_UP sin contexto y cero cambios de decisión entre repeticiones.
Java resuelve 15 casos y el modelo los otros 21. El p50 global está entre
939 y 1002 ms y el p95 entre 1131 y 1151 ms. A diferencia de development,
la mayoría de los casos pasan por el modelo, por lo que el p50 ya no queda
dominado por las reglas.

Sin reglas, el p50 está entre 974 y 1012 ms y el p95 entre 1114 y 1180 ms.
Tampoco cambian las decisiones entre repeticiones, pero los once errores de
clasificación persisten. No se usa esta variante como sustituto del híbrido.

## Resultado y límites

Se conserva el prompt original y el comportamiento híbrido de producción.
Se incorpora una sobrecarga del clasificador para evaluar sin reglas y una
infraestructura de reportes por repetición; `SpeechProcessingService` no cambia.
La suite normal pasa con JDK 21, incluyendo pruebas locales sin modelo sobre
selección de variantes, protección contextual, repeticiones, estabilidad y
persistencia de reportes sin sobrescritura.

Los 36 casos por corpus y su balance artificial no representan la distribución
real de un micrófono siempre activo. Los porcentajes describen estos corpus,
no una garantía de precisión en uso general. Con doce casos OTHER por corpus,
una sola falsa activación ya supera el umbral del 2 %. Las latencias son las
observadas en este servidor y sesión, no un benchmark universal del modelo.
La siguiente ampliación útil sería acumular transcripciones reales como un
corpus nuevo, manteniendo estos resultados como referencia.
