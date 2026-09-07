# Corpus de clasificación de utterances

Los archivos JSONL contienen un objeto JSON por línea. Las líneas vacías se
ignoran. El corpus de desarrollo se usa durante los ajustes del prompt; el
holdout se reserva para validar el candidato final.

## Contrato

Caso sin contexto:

```json
{"id":"new-001","currentText":"¿Qué hora es?","contextAvailable":false,"expected":"new_request","tags":["direct"],"rationale":"Petición independiente"}
```

Caso con contexto:

```json
{"id":"follow-001","currentText":"¿Y para qué se usa?","contextAvailable":true,"previousUserText":"Explícame RSA","previousAssistantText":"RSA es un sistema criptográfico asimétrico.","owner":"general","expected":"follow_up","tags":["critical","pronoun"],"rationale":"La referencia depende del intercambio anterior"}
```

Campos obligatorios para todos los casos:

- `id`: identificador único dentro del archivo.
- `currentText`: transcripción que se clasificará.
- `contextAvailable`: indica si existe un turno anterior.
- `expected`: `new_request`, `follow_up` u `other`.
- `tags`: lista no nula; `critical` activa la puerta de exactitud del 100 %.
- `rationale`: justificación humana de la etiqueta esperada.

Cuando `contextAvailable` es `true`, también son obligatorios
`previousUserText`, `previousAssistantText` y `owner`. El owner acepta los
identificadores de `Capability`, por ejemplo `general` u `os-command`.

Cuando `contextAvailable` es `false`, los campos del contexto deben omitirse.
Un caso esperado como `follow_up` siempre requiere contexto.

## Ejecución

Baseline informativo sobre desarrollo, sin aplicar umbrales:

```powershell
mvn -Pmodel-evaluation `
    -Dtest=LocalUtteranceCorpusTest `
    -Devaluation.corpus=development `
    -Devaluation.report-only=true test
```

Validación de desarrollo aplicando los umbrales:

```powershell
mvn -Pmodel-evaluation `
    -Dtest=LocalUtteranceCorpusTest `
    -Devaluation.corpus=development test
```

Validación final del holdout:

```powershell
mvn -Pmodel-evaluation `
    -Dtest=LocalUtteranceCorpusTest `
    -Devaluation.corpus=holdout test
```

Propiedades opcionales:

- `evaluation.mode=hybrid|model-only|both`, por defecto `hybrid`.
- `evaluation.repetitions`, entero positivo, por defecto `1`.

- `evaluation.base-url`, por defecto `http://localhost:1234/v1`.
- `evaluation.api-key`, por defecto `lm-studio`.
- `evaluation.model`, por defecto el modelo local configurado en `AppConfig`: `phi-router` (Phi-3.5 Mini Instruct), salvo override mediante `ares.local-model`. El servidor local debe exponer ese identificador.
- `evaluation.minimum-cases`, por defecto `30`.
- `evaluation.minimum-macro-f1`, por defecto `0.90`.
- `evaluation.minimum-recall`, por defecto `0.85`.
- `evaluation.maximum-other-false-activation`, por defecto `0.02`.
- `evaluation.report-only=true` genera el reporte sin fallar por umbrales.

El reporte incluye accuracy, macro-F1, precision/recall/F1 por etiqueta,
matriz de confusión, falsas activaciones desde `OTHER`, errores del modelo,
casos fallidos y latencias p50/p95.

## Comparación reproducible de variantes

```powershell
mvn -Pmodel-evaluation `
    -Dtest=LocalUtteranceCorpusTest `
    -Devaluation.corpus=development `
    -Devaluation.mode=both `
    -Devaluation.repetitions=3 `
    -Devaluation.report-only=true test
```

Cada ejecución crea un directorio único en `target/model-evaluation`. Conserva el
prompt exacto, sus huellas SHA-256 y las del corpus, modelo, fecha, parámetros,
resultados por caso y repetición, errores, métricas y estabilidad. El reporte
separa las rutas `rules` y `model`; la latencia global del híbrido incluye los
casos resueltos por Java. Sus aciertos no deben atribuirse al modelo.

`model-only` omite únicamente `UtteranceShapeDetector`: conserva el parser de
salida y la protección que convierte FOLLOW_UP sin contexto en OTHER. Ambos
modos comparten prompt, temperatura cero y límite de ocho tokens de salida.
La estabilidad se expresa como proporción de IDs cuyo resultado cambia entre
repeticiones; un error es un resultado distinto de cualquier etiqueta válida.
Con una sola repetición se informa que no se midió estabilidad.

Antes del corpus se comprueba el modelo expuesto y se registra una llamada de
calentamiento por variante, fuera de las métricas. Fallos de disponibilidad o
calentamiento detienen la evaluación con un archivo separado. Las llamadas usan
un timeout de sesenta segundos y no reintentan; los errores durante el corpus
se registran por caso.

Para validar el candidato congelado, usar `evaluation.corpus=holdout`, ambos
modos y tres repeticiones, omitiendo `evaluation.report-only=true`. Los umbrales
se exigen en cada repetición híbrida; model-only es diagnóstico. Todos los
reportes se guardan antes de comprobar los umbrales. Ajustar exclusivamente con
development, como máximo tres revisiones del prompt; no editar corpus ni reglas
para mejorar estas mediciones y no reajustar a partir de holdout.

## Benchmarks del modelo local

Routing semántico protegido:

```powershell
mvn -Pmodel-evaluation `
    -Dtest=LocalSemanticRouterCorpusTest `
    -Devaluation.model=phi-router test
```

Para medir el modelo puro, añade `-Devaluation.semantic-guarded=false`.
Wake, parser OS y parser de audio se evalúan juntos con:

```powershell
mvn -Pmodel-evaluation `
    -Dtest=LocalStructuredClassifiersBenchmarkTest `
    -Devaluation.model=phi-router test
```
