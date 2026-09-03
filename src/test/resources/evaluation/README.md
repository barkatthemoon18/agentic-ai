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
    -Dtest=GraniteUtteranceCorpusTest `
    -Devaluation.corpus=development `
    -Devaluation.report-only=true test
```

Validación de desarrollo aplicando los umbrales:

```powershell
mvn -Pmodel-evaluation `
    -Dtest=GraniteUtteranceCorpusTest `
    -Devaluation.corpus=development test
```

Validación final del holdout:

```powershell
mvn -Pmodel-evaluation `
    -Dtest=GraniteUtteranceCorpusTest `
    -Devaluation.corpus=holdout test
```

Propiedades opcionales:

- `evaluation.base-url`, por defecto `http://localhost:1234/v1`.
- `evaluation.api-key`, por defecto `lm-studio`.
- `evaluation.minimum-cases`, por defecto `30`.
- `evaluation.minimum-macro-f1`, por defecto `0.90`.
- `evaluation.minimum-recall`, por defecto `0.85`.
- `evaluation.maximum-other-false-activation`, por defecto `0.02`.
- `evaluation.report-only=true` genera el reporte sin fallar por umbrales.

El reporte incluye accuracy, macro-F1, precision/recall/F1 por etiqueta,
matriz de confusión, falsas activaciones desde `OTHER`, errores del modelo,
casos fallidos y latencias p50/p95.
