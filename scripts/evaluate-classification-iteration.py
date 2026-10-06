"""Working-tree baseline/candidate evaluation; instrumentation stays in the test harness."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAVEN = "C:/Program Files/JetBrains/IntelliJ IDEA 2024.1/plugins/maven-plugin/lib/maven3/bin/mvn.cmd"
JAVA = "C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot"
UNIT_TESTS = ("LocalGeneralComplexityClassifierTest,OpenAiGeneralBackendInferenceTest,GeneralBackendRoutingTest,GeneralToResearchTransitionTest,"
              "LocalResearchPlanClassifierTest,GuardedSemanticRouterTest,ResearchEscalationDetectorTest,"
              "LocalClassifierContractsTest,StabilityMetricsTest,DecisionEvaluationReportTest,"
              "DecisionCorpusLoaderTest,DecisionCorpusResourcesTest")

# Contract-only exclusions declared before baseline. All historical rows are still measured and reported.
LEGACY_EXCLUSIONS = {
    **{f"research-local-dev-{n:02}": "ambiguous follow-up or model-only switch needs inherited state"
       for n in (6, 7, 8, 9, 10, 11)},
    "research-local-dev-14": "old model preference overrides requested references",
    **{f"research-web-dev-{n:02}": "ambiguous follow-up inherits Web only in the old contract" for n in (10, 11, 12)},
    **{f"research-local-hold-{n:02}": "ambiguous follow-up or model-only switch needs inherited state"
       for n in (5, 6, 7, 8)},
    "research-local-hold-09": "old model preference overrides current price",
    "research-web-hold-02": "old global backend scope without explicit Web or a specified current fact",
}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def hashes(directory: Path) -> dict[str, str]:
    return {p.relative_to(directory).as_posix(): sha(p.read_bytes())
            for p in sorted(directory.rglob("*")) if p.is_file() and "__pycache__" not in p.parts}


def save(path: Path, value) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def invoke(args, directory: Path, log: str, maven: str, java: str) -> int:
    env = os.environ.copy()
    env["JAVA_HOME"] = java
    env["PATH"] = str(Path(java) / "bin") + os.pathsep + env.get("PATH", "")
    command = [maven, "-B", *args]
    save(directory / (log + "-command.json"), {"command": command, "javaHome": java})
    with (directory / (log + ".log")).open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        for line in process.stdout:
            output.write(line)
            output.flush()
            if any(token in line for token in ("PROGRESS ", "EVALUATION ", "BUILD SUCCESS", "BUILD FAILURE", "[ERROR]")):
                print(line.rstrip(), flush=True)
        code = process.wait()
    print(f"EXIT {code}; {directory / (log + '.log')}", flush=True)
    return code


def rows(directory: Path, suite: str):
    return [json.loads(line) for p in sorted(directory.glob(suite + "-r[1-5].jsonl"))
            for line in p.read_text(encoding="utf-8").splitlines()]


def projected_metrics(values, labels):
    correct = sum(x.get("error") is None and x["actual"] == x["expected"] for x in values)
    f1 = 0
    confusion = {}
    for label in labels:
        support = sum(x["expected"] == label for x in values)
        predicted = sum(x.get("actual") == label for x in values)
        tp = sum(x.get("error") is None and x["expected"] == label and x.get("actual") == label for x in values)
        f1 += 2 * tp / (support + predicted) if support + predicted else 0
        confusion[label] = {p: sum(x["expected"] == label and x.get("actual") == p for x in values) for p in labels}
        confusion[label]["ERROR"] = sum(x["expected"] == label and (x.get("error") is not None or x.get("actual") is None)
                                       for x in values)
    return {"cases": len(values), "accuracy": correct / len(values) if values else 0,
            "macroF1": f1 / len(labels), "errors": sum(x.get("error") is not None or x.get("actual") is None for x in values),
            "confusionMatrix": confusion}


def report(run: Path, previous: Path) -> None:
    policy = json.loads((run / 'legacy-current-contract-comparability.json').read_text(encoding='utf-8'))
    exclusions = policy['excluded']
    suites = sorted(p.name.removesuffix("-aggregate.json") for p in (run / "candidate").glob("*-aggregate.json"))
    comparison = {}
    lines = ["# Validación de classifiers: baseline / candidate", "",
             "Cinco repeticiones por caso; phi-router; temperature=0; Java 21. Baseline es el working tree inicial, "
             "incluidos los ajustes locales de General. Los holdouts y expectativas se congelaron antes del baseline.", "",
             "## Métricas y deltas", "",
             "| Corpus | Accuracy B → C | Macro-F1 B → C | Δ F1 | Errores B → C | Inestables B → C | Estrictos C |",
             "|---|---|---|---:|---|---|---|"]
    for suite in suites:
        baseline = json.loads((run / "baseline" / (suite + "-aggregate.json")).read_text(encoding="utf-8"))
        candidate = json.loads((run / "candidate" / (suite + "-aggregate.json")).read_text(encoding="utf-8"))
        bs = json.loads((run / "baseline" / (suite + "-stability.json")).read_text(encoding="utf-8"))
        cs = json.loads((run / "candidate" / (suite + "-stability.json")).read_text(encoding="utf-8"))
        previous_file = previous / "candidate" / (suite + "-aggregate.json")
        old = json.loads(previous_file.read_text(encoding="utf-8")) if previous_file.exists() else None
        comparison[suite] = {"baseline": baseline, "candidate": candidate, "previous": old,
                             "deltaMacroF1": candidate["macroF1"] - baseline["macroF1"]}
        lines.append(f"| {suite} | {baseline['accuracy']:.4f} → {candidate['accuracy']:.4f} | "
                     f"{baseline['macroF1']:.4f} → {candidate['macroF1']:.4f} | "
                     f"{candidate['macroF1']-baseline['macroF1']:+.4f} | {baseline['errors']} → {candidate['errors']} | "
                     f"{baseline['unstableCases']} → {candidate['unstableCases']} | "
                     f"{sum(x['strictPass'] for x in cs)}/{len(cs)} casos 5/5 |")
        if suite.startswith("research-backend"):
            for name in ("baseline", "candidate"):
                comparable = [x for x in rows(run / name, suite) if x['id'] not in exclusions]
                metric = projected_metrics(comparable, ["qwen_local", "gpt_web"])
                comparison[suite][name + "Comparable"] = metric
                save(run / name / (suite + "-comparable.json"), metric)
    lines += ["", "## General frente al informe anterior", "",
              "El development anterior tenía 30 casos; el actual tiene 34. Las comparaciones por ID distinguen "
              "cambios de comportamiento de casos añadidos. General conserva su fuente productiva inicial.", ""]
    for suite in [s for s in suites if s.startswith("general") and comparison[s]["previous"]]:
        old = comparison[suite]["previous"]
        now = comparison[suite]["candidate"]
        lines.append(f"- {suite}: anterior F1 {old['macroF1']:.4f}, actual {now['macroF1']:.4f}, "
                     f"Δ {now['macroF1']-old['macroF1']:+.4f}.")
    lines += ["", "## Research: dimensiones, contrato histórico y follow-ups", "",
              "El corpus histórico de backend se informa completo con su gold original. Sus subconjuntos comparables "
              "usan exclusiones contractuales y la regla solicitada de diagnosticar follow-ups ambiguos; "
              "no se reetiquetó ningún holdout. No se implementó herencia de access ni se agregó contexto conversacional.", ""]
    lines.append("El registro inicial omitió dos IDs con tags inheritance/follow-up; se conservaron tanto ese registro "
                 "como la derivación completa de la política en legacy-current-contract-comparability.json. "
                 "Ambas exclusiones responden al contrato solicitado, no a un ajuste de etiquetas.")
    lines.append('')
    for suite in [s for s in suites if s.startswith("research-plan")]:
        for name in ("baseline", "candidate"):
            metric = comparison[suite][name]
            lines.append(f"- {suite} / {name}: ACCESS accuracy={metric['access']['accuracy']:.4f}, "
                         f"F1={metric['access']['macroF1']:.4f}; DEPTH accuracy={metric['depth']['accuracy']:.4f}, "
                         f"F1={metric['depth']['macroF1']:.4f}; retry rate={metric['retryRate']:.4f}; "
                         f"invalid-output rate={metric['invalidOutputRate']:.4f}; finales inválidos={metric['finalInvalidOutputs']}.")
    for suite in [s for s in suites if s.startswith("research-backend")]:
        for name in ("baseline", "candidate"):
            metric = comparison[suite][name + "Comparable"]
            lines.append(f"- {suite} / {name}, ACCESS comparable: n={metric['cases'] // 5}, "
                         f"accuracy={metric['accuracy']:.4f}, F1={metric['macroF1']:.4f}, errores={metric['errors']}.")
    lines += ['', '## Latencias, retries y salidas inválidas', '',
              '| Corpus | p50 ms B → C | p95 ms B → C | Retry rate B → C | Invalid-output rate B → C | Finales inválidos B → C |',
              '|---|---:|---:|---|---|---|']
    for suite, data in comparison.items():
        b, c = data['baseline'], data['candidate']
        lines.append(f"| {suite} | {b['p50Millis']:.1f} → {c['p50Millis']:.1f} | "
                     f"{b['p95Millis']:.1f} → {c['p95Millis']:.1f} | {b['retryRate']:.4f} → {c['retryRate']:.4f} | "
                     f"{b['invalidOutputRate']:.4f} → {c['invalidOutputRate']:.4f} | "
                     f"{b['finalInvalidOutputs']} → {c['finalInvalidOutputs']} |")
    lines += ['', 'Microsoft terminó en error 5/5 en el baseline y en web_quick 5/5 en el candidato. '
              'Los cinco intentos finales fueron válidos a la primera. El retry limpio se valida también '
              'con respuestas inválidas controladas en tests unitarios; assistant|> sigue siendo rechazado.', '']
    lines += ["", "## Matrices de ResearchPlan", ""]
    plan_labels = ['knowledge_quick', 'knowledge_deep', 'web_quick', 'web_deep']
    for suite in [s for s in suites if s.startswith('research-plan')]:
        for name in ('baseline', 'candidate'):
            matrix = comparison[suite][name]['confusionMatrix']
            lines += [f"### {suite} / {name}", "",
                      '| Expected / actual | ' + ' | '.join(plan_labels + ['ERROR']) + ' |',
                      '|---|' + '---:|' * 5]
            for label in plan_labels:
                lines.append('| ' + label + ' | ' + ' | '.join(str(matrix[label][p]) for p in plan_labels + ['ERROR']) + ' |')
            lines.append('')
    lines += ['La matriz de routing mantiene las cinco capacidades. En boundary sólo hay soporte para General '
              'y CurrentResearch: accuracy 1,0000 corresponde a F1 1,0000 entre esas dos capacidades; '
              'el macro-F1 con las cinco clases fijas es 0,4000. La aceptación estricta usa acierto y estabilidad 5/5.', '']
    lines += ["", "## Resultados por caso y regresiones restantes", "",
              "Las tablas conservan las cinco decisiones. Las matrices completas, latencias y salidas por intento "
              "están en los JSON/JSONL, incluyendo errores y reglas que sobrescriben al modelo."]
    for suite in suites:
        cs = json.loads((run / "candidate" / (suite + "-stability.json")).read_text(encoding="utf-8"))
        bs = {x['id']: x for x in json.loads((run / "baseline" / (suite + "-stability.json")).read_text(encoding="utf-8"))}
        old_file = previous / "candidate" / (suite + "-stability.json")
        old = {x['id']: x for x in json.loads(old_file.read_text(encoding="utf-8"))} if old_file.exists() else {}
        lines += ["", f"### {suite}", "", "| Caso / consulta | Expected | Baseline | Candidate | Diagnóstico / cambio anterior |",
                  "|---|---|---|---|---|"]
        for x in cs:
            changed = x['id'] in old and old[x['id']]['outcomes'] != x['outcomes']
            diagnostic = exclusions.get(x['id'], x['classification'])
            diagnostic += "; cambió frente al informe anterior" if changed else "; añadido" if old and x['id'] not in old else ""
            query = x['query'].replace('|', '\\|').replace('\n', ' ')
            lines.append(f"| {x['id']}: {query} | {x['expected']} | {' / '.join(bs[x['id']]['outcomes'])} | "
                         f"{' / '.join(x['outcomes'])} | {diagnostic} |")
    suite_file = run / "full-suite-failures.json"
    lines += ["", "## Suite completa", ""]
    if suite_file.exists():
        for failure in json.loads(suite_file.read_text(encoding="utf-8")):
            lines.append(f"- {failure['class']}.{failure['test']}: {failure['message']}")
    lines += ["", "## Recomendaciones no implementadas", "",
              "- Definir por separado si un follow-up ambiguo conserva access y qué contexto mínimo requeriría. "
              "La API ResearchPlanClassifier sigue recibiendo sólo la consulta.",
              "- Resolver los fallos de runtime Qwen/LM Studio en una tarea independiente."]
    lines += ["", "## Cambios productivos", "",
              "- General: recordatorio contractual fuera de query en el adaptador de inference, motivado por el baseline; "
              "se conserva el SYSTEM_PROMPT local inicial.",
              "- Research: contrato access/depth, consulta original, retry limpio con corrección user y excepción específica; "
              "disponibilidad conceptual deja de forzar Web.",
              "- Routing: neutralización de Web negada sólo en reglas deterministas; se conserva la consulta para el modelo.",
              "", "## Diagnóstico de infraestructura", "",
              "La prueba de concurrencia cuatro produjo Context size has been exceeded; se archivó aparte. "
              "Baseline y candidate usan concurrencia uno, sin modificar el runtime ni LM Studio."]
    initial = json.loads((run / "initial-state.json").read_text(encoding="utf-8"))['fileHashes']
    lines += ['', 'La ejecución con el prompt de Research mal codificado se conserva en candidate-v3-encoding-partial. '
              'La ejecución anterior a completar las señales genéricas de Web/vigencia está en candidate-v4-access-partial. '
              'Estas correcciones de señales fueron deterministas; el prompt no se ajustó con el holdout. '
              'Sólo se reutilizaron mediciones de General/routing con clases compiladas idénticas; '
              'candidate-unaffected-reuse.json registra archivos, hashes y procedencia. '
              'Todas las mediciones finales de Research se ejecutaron con el candidato final.', '']
    current = {f"{folder}/{key}": value for folder in ('src', 'scripts') for key, value in hashes(ROOT / folder).items()}
    modified = sorted(path for path in set(initial) | set(current) if initial.get(path) != current.get(path))
    save(run / "iteration-modified-files.json", modified)
    lines += ["", "## Archivos modificados durante esta iteración", "", *[f"- `{p}`" for p in modified]]
    save(run / "comparison.json", comparison)
    remaining = []
    strict_results = {}
    for suite in suites:
        old_cases = {r['id']: r for r in json.loads((run / 'baseline' / (suite + '-stability.json')).read_text(encoding='utf-8'))}
        cases = json.loads((run / 'candidate' / (suite + '-stability.json')).read_text(encoding='utf-8'))
        strict_results[suite] = {'cases': len(cases), 'passed5of5': sum(r['strictPass'] for r in cases),
                                'allStable': all(r['stable'] for r in cases)}
        for case in cases:
            if case['strictPass']:
                continue
            remaining.append({'suite': suite, **case, 'baselineOutcomes': old_cases[case['id']]['outcomes'],
                              'newSinceBaseline': old_cases[case['id']]['strictPass'],
                              'diagnosticReason': exclusions.get(case['id'])})
    save(run / 'remaining-regressions.json', remaining)
    save(run / 'strict-result-summary.json', strict_results)
    lines += ['', '## Regresiones nuevas frente al baseline', '']
    for case in remaining:
        if case['newSinceBaseline']:
            reason = f" Diagnóstico contractual: {case['diagnosticReason']}." if case['diagnosticReason'] else ''
            lines.append(f"- {case['suite']}/{case['id']}: {case['query']} → {case['outcomes']}; expected {case['expected']}.{reason}")
    lines.append('Los errores de General corresponden a decisiones válidas gpt para tareas simples que esperaban local, '
                 'sin reglas deterministas que las sobrescriban. Se reportan y no se ajustó el prompt con esos holdouts.')
    lines += ['', 'En holdout-v1, el F1 agregado no cambia: el caso de La Odisea pasó de local a gpt '
              'y el caso adversarial que pide gpt mientras pregunta por la gravedad pasó de gpt a local. '
              'Ambos cambios se conservan en las tablas por caso. La consulta de development '
              '“Busca globalmente quién dirige la empresa” sigue contabilizándose como desacierto de ACCESS '
              'en el subconjunto comparable. El raw router falla de forma estable en '
              '“Verifica si esto sigue vigente”; guarded devuelve CurrentResearch 5/5.', '']
    checks = {}
    for phase in ('candidate-unit-tests', 'full-suite'):
        log = (run / (phase + '.log')).read_text(encoding='utf-8')
        totals = re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)\s*$', log, re.MULTILINE)
        if totals:
            checks[phase] = dict(zip(('tests', 'failures', 'errors', 'skipped'), map(int, totals[-1])))
    save(run / 'test-summary.json', checks)
    lines += ['', '## Tests', '', *[f"- {name}: {value}." for name, value in checks.items()]]
    strict_suites = {'general-contract-regression', 'general-boundary-regression', 'research-plan-regression',
                    'research-plan-diagnostic', 'routing-boundary-guarded', 'follow-up-crossings'}
    acceptance = {}
    for suite, data in comparison.items():
        metric = data['candidate']
        passed = metric['errors'] == 0 and metric['finalInvalidOutputs'] == 0
        if suite in strict_suites:
            passed &= metric['strictAllCasesPass']
        elif suite.startswith('research-backend'):
            passed &= data['candidateComparable']['macroF1'] >= .90
        elif suite.endswith('-raw'):
            acceptance[suite] = {'diagnostic': True, 'errors': metric['errors']}
            continue
        else:
            passed &= metric['macroF1'] >= .90
        for repetition in range(1, 6):
            sample = json.loads((run / 'candidate' / f'{suite}-r{repetition}-metrics.json').read_text(encoding='utf-8'))
            if suite.startswith('research-backend'):
                sample = projected_metrics([x for x in rows(run / 'candidate', suite)
                                            if x['repetition'] == repetition and x['id'] not in exclusions], ['qwen_local', 'gpt_web'])
            if suite not in strict_suites:
                passed &= sample['macroF1'] >= .90 and sample['errors'] == 0
        if suite.startswith('research-plan'):
            passed &= metric['access']['macroF1'] >= .90 and metric['depth']['macroF1'] >= .90
        acceptance[suite] = {'passed': bool(passed)}
    save(run / 'acceptance.json', acceptance)
    initial_production = {p: h for p, h in initial.items() if p.startswith('src/main/')}
    changed_production = [p for p in set(initial_production) | set(current) if p.startswith('src/main/')
                          and initial_production.get(p) != current.get(p)]
    allowed_names = {'OpenAiGeneralBackendInference.java', 'LocalResearchPlanClassifier.java',
                     'InvalidResearchPlanOutputException.java', 'GuardedSemanticRouter.java', 'ResearchEscalationDetector.java'}
    save(run / 'integrity-audit.json', {
        'corporaUnchangedSinceBaseline': json.loads((run / 'frozen-corpus-hashes.json').read_text(encoding='utf-8'))
                                        == hashes(ROOT / 'src/test/resources/evaluation'),
        'generalClassifierPreserved': initial['src/main/java/com/fuad/assistant/skills/general/LocalGeneralComplexityClassifier.java']
                                      == current['src/main/java/com/fuad/assistant/skills/general/LocalGeneralComplexityClassifier.java'],
        'productionChanges': sorted(changed_production),
        'outsideScopeProductionUnchanged': all(Path(p).name in allowed_names for p in changed_production)})
    (run / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"REPORT {run / 'report.md'}", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", required=True, type=Path)
    parser.add_argument("--phase", required=True, choices=("baseline", "candidate", "experiment", "suite", "report"))
    parser.add_argument("--name")
    parser.add_argument("--suites", default="")
    parser.add_argument("--repetitions", type=int, default=5)
    parser.add_argument("--maven", default=MAVEN)
    parser.add_argument("--java-home", default=JAVA)
    parser.add_argument("--previous", type=Path, default=ROOT / "target/model-evaluation/classification-20261006T035226Z-4f80d8ba")
    args = parser.parse_args()
    run = args.run.resolve()
    if args.phase == "report":
        report(run, args.previous)
        shutil.copy2(Path(__file__), run / 'report-runner.py')
        save(run / 'report-manifest.json', {'generatedAt': datetime.now(timezone.utc).isoformat(),
                                          'runnerSha256': sha(Path(__file__).read_bytes())})
        return 0
    if args.phase == "suite":
        started = datetime.now(timezone.utc).timestamp()
        code = invoke(["test"], run, "full-suite", args.maven, args.java_home)
        failures = []
        reports = run / 'full-suite-surefire'
        reports.mkdir(exist_ok=True)
        totals = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
        for path in (ROOT / "target/surefire-reports").glob("TEST-*.xml"):
            if path.stat().st_mtime < started:
                continue
            shutil.copy2(path, reports / path.name)
            xml = ET.parse(path)
            for key in totals:
                totals[key] += int(xml.getroot().get(key, '0'))
            for case in xml.findall("testcase"):
                for kind in ("failure", "error"):
                    failure = case.find(kind)
                    if failure is not None:
                        failures.append({"class": case.get('classname'), "test": case.get('name'),
                                         "kind": kind, "message": failure.get('message')})
        save(run / "full-suite-failures.json", failures)
        save(run / 'full-suite-manifest.json', {'startedAt': datetime.fromtimestamp(started, timezone.utc).isoformat(),
                                              'finishedAt': datetime.now(timezone.utc).isoformat(),
                                              'javaHome': args.java_home, 'exitCode': code, **totals})
        return code
    name = args.name or args.phase
    source_hashes = hashes(ROOT / "src/main")
    corpus_hashes = hashes(ROOT / "src/test/resources/evaluation")
    if args.phase == "baseline":
        save(run / "legacy-comparability.json", LEGACY_EXCLUSIONS)
        save(run / "frozen-corpus-hashes.json", corpus_hashes)
    elif json.loads((run / "frozen-corpus-hashes.json").read_text(encoding="utf-8")) != corpus_hashes:
        raise RuntimeError("Corpus changed after baseline")
    metadata = {"phase": args.phase, "variant": name, "startedAt": datetime.now(timezone.utc).isoformat(),
                "model": "phi-router", "baseUrl": "http://localhost:1234/v1", "temperature": 0,
                "repetitions": args.repetitions, "httpRetries": 0, "timeoutSeconds": 60, "sourceHashes": source_hashes,
                "requestConcurrency": 1, "resumedSerialBaseline": args.phase == 'baseline',
                "corpusHashes": corpus_hashes, "harnessHashes": hashes(ROOT / "src/test/java/com/fuad/evaluation/stability"),
                "runnerSha256": sha(Path(__file__).read_bytes()), "suites": args.suites}
    save(run / (name + "-manifest.json"), metadata)
    if invoke(["-DskipTests", "test-compile"], run, name + "-compile", args.maven, args.java_home):
        return 1
    unit_tests = UNIT_TESTS
    if args.phase == "baseline":
        # New contract tests deliberately expose existing faults; their failures are saved separately.
        unit_tests = ','.join(t for t in UNIT_TESTS.split(',') if t not in {
            'LocalResearchPlanClassifierTest', 'GuardedSemanticRouterTest', 'ResearchEscalationDetectorTest'})
    if invoke(["-Dtest=" + unit_tests, "test"], run, name + "-unit-tests", args.maven, args.java_home):
        return 1
    classes = run / (name + "-classes")
    if classes.exists():
        previous_snapshot = run / (name + "-serial-partial-manifest.json")
        if args.phase != 'baseline' or not previous_snapshot.exists():
            raise RuntimeError("Variant already frozen: " + str(classes))
        original = json.loads(previous_snapshot.read_text(encoding='utf-8'))
        if original['compiledClassHashes'] != hashes(classes) or original['sourceHashes'] != source_hashes:
            raise RuntimeError('Baseline snapshot identity changed')
    else:
        shutil.copytree(ROOT / "target/classes", classes)
    shutil.copytree(ROOT / "src/main", run / (name + "-source"), dirs_exist_ok=True)
    metadata["compiledClassHashes"] = hashes(classes)
    save(run / (name + "-manifest.json"), metadata)
    command = ["-Pmodel-evaluation", "-Dtest=ClassificationStabilityCorpusTest", "-Devaluation.model=phi-router",
               "-Devaluation.variant=" + name, "-Devaluation.classes-directory=" + str(classes),
               "-Devaluation.repetitions=" + str(args.repetitions), "-Devaluation.report-only=true", "-Devaluation.output-directory=" + str(run)]
    command.extend(["-Devaluation.concurrency=1", "-Devaluation.resume=true"])
    if args.suites:
        command.append("-Devaluation.suites=" + args.suites)
    code = invoke([*command, "test"], run, name + "-evaluation", args.maven, args.java_home)
    metadata["finishedAt"] = datetime.now(timezone.utc).isoformat()
    metadata["productionUnchangedDuringEvaluation"] = source_hashes == hashes(ROOT / "src/main")
    metadata["evaluationComplete"] = code == 0
    save(run / (name + "-manifest.json"), metadata)
    if not metadata["productionUnchangedDuringEvaluation"]:
        raise RuntimeError("Production sources changed while evaluation was running")
    return code


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
