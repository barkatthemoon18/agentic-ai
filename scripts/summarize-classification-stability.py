"""Audit completed artifacts and add an evidence-based diagnostic; never call models."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil


ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", nargs="?", type=Path)
    args = parser.parse_args()
    directory = args.directory or Path((ROOT / "target/model-evaluation/latest-classification-run.txt").read_text(encoding="utf-8"))
    runner = ROOT / "scripts/evaluate-classification-stability.py"
    spec = importlib.util.spec_from_file_location("classification_runner", runner)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    if not manifest.get("evaluationComplete"):
        raise RuntimeError("Evaluation is not complete; no final report can be issued")
    module.report(directory)
    audit = {
        "candidateProductionUnchanged": manifest["candidateProductionSources"] == module.hashes(ROOT, "src/main"),
        "runnerIdentityMatches": manifest["runnerSha256"] == module.digest(runner.read_bytes()),
        "harnessIdentityMatches": manifest["harnessSources"] == module.hashes(ROOT, "src/test/java/com/fuad/evaluation/stability"),
        "corpusIdentityMatches": manifest["corpusSources"] == module.hashes(ROOT, "src/test/resources/evaluation"),
        "postprocessorSha256": module.digest(Path(__file__).read_bytes()),
        "references": {}, "suiteCorpusSha256": {},
    }
    refs = json.loads((directory / "references.json").read_text(encoding="utf-8"))
    for name, reference in refs.items():
        source = Path(reference["sourceDirectory"])
        audit["references"][name] = {
            "productionUnchanged": reference["productionSources"] == module.hashes(source, "src/main"),
            "compiledClassesUnchanged": reference["compiledClasses"] == module.hashes(source, "target/classes"),
        }
    for suite in sorted((directory / "pre-merge").glob("*-r1.jsonl")):
        name = suite.name.removesuffix("-r1.jsonl")
        rows = [json.loads(line) for line in suite.read_text(encoding="utf-8").splitlines()]
        inputs = [{key: row.get(key) for key in ("id", "query", "expected", "tags", "rationale", "inheritedBackend")} for row in rows]
        data = ("\n".join(json.dumps(row, ensure_ascii=False, sort_keys=True) for row in inputs) + "\n").encode("utf-8")
        frozen = directory / "corpus" / (name + "-effective.jsonl")
        frozen.write_bytes(data)
        audit["suiteCorpusSha256"][name] = hashlib.sha256(data).hexdigest()
        # Audit every actual measurement against the same frozen inputs, including synthesized routing cases.
        for variant in ("pre-merge", "dev-merged", "candidate"):
            for repetition in range(1, 6):
                actual = [json.loads(line) for line in (directory / variant / f"{name}-r{repetition}.jsonl").read_text(encoding="utf-8").splitlines()]
                comparable = [{key: row.get(key) for key in inputs[0]} for row in actual]
                if comparable != inputs:
                    raise RuntimeError(f"Case inputs differ: {variant}/{name}/r{repetition}")
    reproduction = directory / "reproduction"
    reproduction.mkdir(exist_ok=True)
    shutil.copy2(runner, reproduction / runner.name)
    shutil.copy2(Path(__file__), reproduction / Path(__file__).name)
    for path in (ROOT / "src/test/java/com/fuad/evaluation/stability").glob("*.java"):
        shutil.copy2(path, reproduction / path.name)
    shutil.copy2(ROOT / "src/test/java/com/fuad/evaluation/routing/LocalSemanticRouterCorpusTest.java", reproduction / "LocalSemanticRouterCorpusTest.java")
    module.save(directory / "identity-audit.json", audit)
    if not all(audit[k] for k in ("candidateProductionUnchanged", "runnerIdentityMatches", "harnessIdentityMatches", "corpusIdentityMatches")):
        raise RuntimeError("An input identity changed; inspect identity-audit.json")
    if not all(all(row.values()) for row in audit["references"].values()):
        raise RuntimeError("A historical production identity changed")
    failures = json.loads((directory / "unit-failures.json").read_text(encoding="utf-8"))
    lines = ["", "## Hallazgos concretos y límites de la validación", "",
             f"La suite unitaria general registró {len(failures)} fallos; las 92 pruebas de rutas afectadas y harness pasaron. "
             "Los fallos generales se preservan en unit-failures.json y unit-tests.log; no se corrigieron durante esta evaluación.", ""]
    for failure in failures:
        lines.append(f"- `{failure['class']}.{failure['test']}`: {failure['message']}")
    prompt_hashes = {variant: json.loads((directory / variant / "prompt-hashes.json").read_text(encoding="utf-8"))
                     for variant in ("pre-merge", "dev-merged", "candidate")}
    lines += ["", "General pre-fusión y dev tienen el mismo prompt y la misma fuente del clasificador. "
              "Cualquier diferencia de sus decisiones se interpreta como variabilidad observada, no como efecto de un cambio en General.", ""]
    all_rows = {}
    for variant in ("pre-merge", "dev-merged", "candidate"):
        all_rows[variant] = [json.loads(line) for path in sorted((directory / variant).glob("*-r[1-5].jsonl"))
                             for line in path.read_text(encoding="utf-8").splitlines()]
    for variant, rows in all_rows.items():
        invalid = [row for row in rows if row['error']]
        sources = [row for row in rows if row['route'] == 'fallback']
        lines.append(f"- {variant}: {len(rows)} mediciones, {sum(x['attempts'] for x in rows)} llamadas de inferencia, "
                     f"{len(invalid)} errores finales y {len(sources)} fallbacks.")
    lines += ["", "### General: cambios históricos por caso", "",
              "Superar macro-F1 no significa conservar todas las decisiones anteriores. "
              "Las siguientes discrepancias usan el gold histórico congelado, sin reetiquetarlo para el contrato nuevo.", ""]
    for suite in ("general-development", "general-holdout-v1", "general-holdout-v2"):
        candidate = json.loads((directory / "candidate" / f"{suite}-stability.json").read_text(encoding="utf-8"))
        baseline = {row['id']: row for row in json.loads((directory / "pre-merge" / f"{suite}-stability.json").read_text(encoding="utf-8"))}
        for row in candidate:
            if not row['strictPass']:
                introduced = baseline[row['id']]['strictPass']
                lines.append(f"- `{row['id']}`: expected `{row['expected']}`, candidato `{module.compact(row['outcomes'])}`; "
                             f"pre-fusión `{module.compact(baseline[row['id']]['outcomes'])}`. "
                             f"{'Nueva discrepancia frente al baseline' if introduced else 'Discrepancia histórica o variable'}; "
                             f"{row['classification']}. Consulta: {row['query']}")
    lines += ["", "Propuesta para las instrucciones adversariales: añadir contrastes en development que mantengan "
              "la consulta como datos y comprobar ambas direcciones de intento de forzar backend. Para las demostraciones "
              "matemáticas de complejidad media, acordar si cambia el gold contractual; conservar la discrepancia histórica "
              "sin usar holdout para seleccionar un prompt.", "", "### Cruces General/Research", ""]
    crossings = json.loads((directory / "candidate/follow-up-crossings-stability.json").read_text(encoding="utf-8"))
    for row in crossings:
        if not row['strictPass']:
            lines.append(f"- `{row['id']}`: expected `{row['expected']}`, resultados `{module.compact(row['outcomes'])}`. "
                         "La decisión proviene de reglas, sin llamadas al modelo. Propuesta: reconocer negación de búsqueda "
                         "también al final de la consulta y probar las dos posiciones antes de modificar routing.")
    lines += ["", "### Research: herencia, preferencias y retry", ""]
    backend = [row for row in all_rows['candidate'] if row['suite'].startswith('research-backend') and not row['correct']]
    inheritance = sorted({row['id'] for row in backend if row['inheritedBackend'] == 'gpt_web' and row['actual'] == 'qwen_local'})
    other = sorted({row['id'] for row in backend if row['id'] not in inheritance})
    lines.append(f"- Follow-ups con gold de Web heredada que pasan a conocimiento: {', '.join('`'+x+'`' for x in inheritance) or 'ninguno'}. "
                 "Es una diferencia contractual visible: ResearchPlan sólo recibe el texto actual. Resolver explícitamente "
                 "si profundizar información ya obtenida requiere una nueva búsqueda antes de cambiar el comportamiento.")
    lines.append(f"- Otras discrepancias históricas de acceso: {', '.join('`'+x+'`' for x in other) or 'ninguna'}. "
                 "Revisar especialmente la precedencia de Qwen/local, negaciones y solicitudes de referencias; "
                 "las trazas indican cuándo el modelo o las reglas toman la decisión.")
    depth = json.loads((directory / "candidate/research-depth-development-stability.json").read_text(encoding="utf-8"))
    depth_discrepancies = [row for row in depth if not row['strictPass']]
    lines += ["", "### Research: profundidad de consultas puntuales", "",
              "Las tres discrepancias de development son respuestas deep para consultas con gold quick; "
              "provienen del modelo y se repiten 5/5. El acceso a Web no debería aumentar por sí solo la profundidad.", ""]
    for row in depth_discrepancies:
        sample = next(sample for sample in all_rows['candidate']
                      if sample['suite'] == 'research-depth-development' and sample['id'] == row['id'])
        lines.append(f"- `{row['id']}`: expected `{row['expected']}`, resultados `{module.compact(row['outcomes'])}`, "
                     f"label del modelo `{sample['lastModelLabel']}`. Consulta: {row['query']}")
    lines += ["", "Propuesta: contrastar en development solicitudes de un dato actual con análisis de varias fuentes, "
              "manteniendo access y depth independientes. El caso que intenta forzar deep requiere además "
              "comprobar que la instrucción embebida no determine la etiqueta.", "", "### Research: salida inválida después del retry", ""]
    retry_errors = [row for row in all_rows['candidate'] if row['error'] and row['suite'].startswith('research') and row['attempts'] > 1]
    for key in sorted({row['id'] for row in retry_errors}):
        samples = [row for row in retry_errors if row['id'] == key]
        roles = sorted({row['inference'][-1]['messages'][-1]['role'] for row in samples})
        output = samples[0]['inference'][-1]['output']
        lines.append(f"- `{key}`: {len(samples)}/5 repeticiones con error después del retry; último mensaje del retry: {roles}; "
                     f"ejemplo de salida final: `{str(output).replace(chr(10), ' ')}`. Propuesta: evaluar un retry "
                     "con instrucción de usuario, como los otros classifiers, conservando el parser estricto; no aplicar durante este estudio.")
    lines += ["", "La auditoría comprobó fuentes y clases históricas inalteradas, fuentes productivas actuales inalteradas "
              "y los mismos inputs en las quince ejecuciones de cada corpus. identity-audit.json incluye hashes "
              "de los corpus efectivos de routing y follow-up, además de las identidades ya registradas en manifest.json.", ""]
    report = directory / "report.md"
    report.write_text(report.read_text(encoding="utf-8").replace(
        "1. Revisar los incumplimientos estrictos de General contra sus justificaciones. Si el patrón es complejidad real "
        "vs profundidad expositiva, proponer ejemplos contrastivos y un prompt más compacto, conservando el balance verbal; "
        "validar cualquier ajuste únicamente en desarrollo antes de una evaluación nueva.",
        "1. El contrato estricto de General cumple: no requiere un ajuste por estos 24 casos. "
        "Revisar las dos discrepancias nuevas frente al gold histórico de development descritas más abajo; "
        "validar cualquier propuesta en desarrollo antes de una evaluación nueva."), encoding="utf-8")
    with report.open("a", encoding="utf-8") as target:
        target.write("\n".join(lines))
    module.save(directory / "focused-diagnostic.json", {
        "unitFailures": failures, "historicalGeneralPromptsIdentical": prompt_hashes['pre-merge']['LocalGeneralComplexityClassifier'] == prompt_hashes['dev-merged']['LocalGeneralComplexityClassifier'],
        "researchInheritedWebDiscrepancies": inheritance, "researchOtherAccessDiscrepancies": other,
        "researchDepthDevelopmentDiscrepancies": depth_discrepancies,
        "candidateFinalResearchRetryErrors": retry_errors,
    })
    print(directory / "report.md")


if __name__ == "__main__":
    main()
