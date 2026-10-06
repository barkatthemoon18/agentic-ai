"""Run the frozen three-variant evaluation. All compatibility is test-side.

Usage: python scripts/evaluate-classification-stability.py
Outputs and original reference sources are stored below target/model-evaluation.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
from datetime import datetime, timezone
import uuid
import zipfile
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
GENERAL = "src/main/java/com/fuad/assistant/skills/general/LocalGeneralComplexityClassifier.java"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def save(path: Path, value) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=ROOT)


def hashes(root: Path, directory: str) -> dict[str, str]:
    return {p.relative_to(root).as_posix(): digest(p.read_bytes())
            for p in sorted((root / directory).rglob("*")) if p.is_file()}


def archive(ref: str, directory: Path) -> str:
    commit = git("rev-parse", ref).decode().strip()
    contents = git("archive", "--format=zip", commit)
    directory.mkdir(parents=True)
    with zipfile.ZipFile(io.BytesIO(contents)) as source:
        for entry in source.infolist():
            target = (directory / entry.filename).resolve()
            if not target.is_relative_to(directory.resolve()):
                raise ValueError("Archive entry outside reference directory")
        source.extractall(directory)
    return commit


def maven(executable: Path, cwd: Path, arguments: list[str], log: Path, env: dict[str, str]) -> int:
    print(f"BUILD/RUN {cwd.name}: {' '.join(arguments)}", flush=True)
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen([str(executable), "-B", *arguments], cwd=cwd, env=env,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding="utf-8", errors="replace")
        for line in process.stdout:
            output.write(line)
            output.flush()
            if "EVALUATION " in line or "PROGRESS " in line or "BUILD FAILURE" in line or "Tests run:" in line and "Failures: 0, Errors: 0" not in line:
                print(line.rstrip(), flush=True)
        code = process.wait()
    print(f"EXIT {code}; log={log}", flush=True)
    return code


def compact(outcomes: list[str]) -> str:
    aliases = {"qwen_local": "local", "current-research": "research"}
    return " / ".join(aliases.get(x, x) for x in outcomes)


def report(directory: Path) -> None:
    variants = ["pre-merge", "dev-merged", "candidate"]
    suites = sorted(p.name.removesuffix("-aggregate.json") for p in (directory / "candidate").glob("*-aggregate.json"))
    if not suites:
        return
    summaries = {}
    stability = {}
    lines = ["# Diagnóstico comparativo de clasificaciones", "",
             "Cinco repeticiones por caso y variante; temperatura cero; sólo inferencia local. "
             "Los holdouts existentes se consideran regresiones históricas observadas.", "",
             "Las fuentes productivas se cargaron sin cambios desde cada variante, mediante classloaders separados. "
             "La compatibilidad histórica y la instrumentación viven en el harness. Los modelos no se ajustaron.", "",
             "## Métricas agregadas y deltas", "",
             "F1 combina las cinco repeticiones; los gates se verifican también en cada repetición. "
             "Δ compara candidato con la referencia indicada.", "",
             "| Corpus/ruta | F1 pre | F1 dev | F1 candidato | Δ pre | Δ dev | Inestables pre/dev/cand | Errores candidato |",
             "|---|---:|---:|---:|---:|---:|---|---:|"]
    for suite in suites:
        summaries[suite] = {}
        stability[suite] = {}
        for variant in variants:
            summaries[suite][variant] = json.loads((directory / variant / f"{suite}-aggregate.json").read_text(encoding="utf-8"))
            stability[suite][variant] = json.loads((directory / variant / f"{suite}-stability.json").read_text(encoding="utf-8"))
        pre, dev, cand = (summaries[suite][v] for v in variants)
        lines.append(f"| {suite} | {pre['macroF1']:.4f} | {dev['macroF1']:.4f} | {cand['macroF1']:.4f} | "
                     f"{cand['macroF1']-pre['macroF1']:+.4f} | {cand['macroF1']-dev['macroF1']:+.4f} | "
                     f"{pre['unstableCases']}/{dev['unstableCases']}/{cand['unstableCases']} | {cand['errors']} |")
    strict = stability["general-contract-regression"]["candidate"]
    failed = [row for row in strict if not row["strictPass"]]
    lines += ["", "## Regresión estricta de General", "",
              f"**{'CUMPLE' if not failed else 'INCUMPLE'}**: {len(strict)-len(failed)}/24 casos correctos individualmente en 5/5 ejecuciones. "
              "El corpus contiene 12 local y 12 GPT, con dos casos de cada expresión de profundidad por label.", "",
              "| Caso | Expected | Resultados 1 / 2 / 3 / 4 / 5 | Aciertos | Diagnóstico |",
              "|---|---|---|---:|---|"]
    for row in strict:
        lines.append(f"| {row['id']} | {row['expected']} | {compact(row['outcomes'])} | {row['correctRuns']}/5 | {row['classification']} |")
    lines += ["", "## Gates por repetición", "",
              "Routing directo es diagnóstico. El contrato nuevo de General exige 100% sólo al candidato; "
              "las referencias se muestran como comparación. Research backend conserva su gold original: "
              "el nuevo plan no recibe inheritedBackend y esa diferencia queda visible, sin reetiquetar los archivos.", "",
              "| Variante | Corpus/ruta | Accuracy r1/r2/r3/r4/r5 | Macro-F1 r1/r2/r3/r4/r5 | Errores r1/r2/r3/r4/r5 | Gates |",
              "|---|---|---|---|---|---|"]
    for suite in suites:
        for variant in variants:
            values = [json.loads((directory / variant / f"{suite}-r{r}-metrics.json").read_text(encoding="utf-8")) for r in range(1, 6)]
            fmt = lambda key: " / ".join(f"{value[key]:.4f}" for value in values)
            gates = "diagnóstico" if not values[0]["gateApplies"] else " / ".join("PASS" if x["gatePassed"] else "FAIL" for x in values)
            lines.append(f"| {variant} | {suite} | {fmt('accuracy')} | {fmt('macroF1')} | "
                         f"{' / '.join(str(x['errors']) for x in values)} | {gates} |")
    lines += ["", "## Resultados comparativos por caso", "",
              "Cada celda contiene las cinco decisiones, en orden de repetición. "
              "Los JSONL conservan consulta, gold, salidas por intento, mensajes exactos, intervenciones, errores y latencias."]
    for suite in suites:
        lines += ["", f"### {suite}", "",
                  "| Caso / consulta | Expected | Pre-fusión r1–r5 | Dev r1–r5 | Candidato r1–r5 | Diagnóstico candidato |",
                  "|---|---|---|---|---|---|"]
        tables = {v: {row["id"]: row for row in stability[suite][v]} for v in variants}
        for key, row in tables["candidate"].items():
            text = row["query"].replace("|", "\\|").replace("\n", " ")
            lines.append(f"| {key}: {text} | {row['expected']} | {compact(tables['pre-merge'][key]['outcomes'])} | "
                         f"{compact(tables['dev-merged'][key]['outcomes'])} | {compact(row['outcomes'])} | {row['classification']} |")
    lines += ["", "## Interpretación y propuesta de ajuste", "",
              "Una decisión incorrecta constante es una regresión determinista; repetirla no satisface el contrato. "
              "Las decisiones variables son inestabilidad. Los errores finales y los fallbacks se registran por separado.", "",
              "1. Revisar los incumplimientos estrictos de General contra sus justificaciones. Si el patrón es complejidad real "
              "vs profundidad expositiva, proponer ejemplos contrastivos y un prompt más compacto, conservando el balance verbal; "
              "validar cualquier ajuste únicamente en desarrollo antes de una evaluación nueva.",
              "2. Revisar por separado los cambios de Research relacionados con herencia de backend y preferencias explícitas "
              "de modelo local. El contrato access/depth no es idéntico al backend anterior. Definir el comportamiento deseado "
              "antes de proponer cambios; no convertir pérdidas históricas en aciertos mediante reetiquetado posterior.",
              "3. Si aparece fallback, comprobar la salida original y el finish reason para diferenciar salida inválida, "
              "truncamiento y problemas de disponibilidad. Los aciertos de reglas o fallback no acreditan precisión del modelo.",
              "", "No se aplicaron correcciones productivas. Los cambios propuestos requieren una evaluación posterior independiente."]
    (directory / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    save(directory / "comparison.json", {
        "summaries": summaries,
        "generalStrictFailedCases": failed,
        "deltas": {suite: {ref: {metric: summaries[suite]['candidate'][metric] - summaries[suite][ref][metric]
                                        for metric in ('accuracy', 'macroF1', 'errors', 'retryRate', 'p50Millis', 'p95Millis')}
                            for ref in ('pre-merge', 'dev-merged')} for suite in suites},
        "caseTransitions": {suite: {row['id']: {v: next(x['outcomes'] for x in stability[suite][v] if x['id'] == row['id'])
                                                for v in variants} for row in stability[suite]['candidate']} for suite in suites}
    })


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--maven", default="C:/Program Files/JetBrains/IntelliJ IDEA 2024.1/plugins/maven-plugin/lib/maven3/bin/mvn.cmd")
    parser.add_argument("--java-home", default="C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot")
    parser.add_argument("--report-existing", type=Path)
    args = parser.parse_args()
    if args.report_existing:
        report(args.report_existing.resolve())
        return 0
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output = ROOT / "target/model-evaluation" / ("classification-" + stamp + "-" + uuid.uuid4().hex[:8])
    output.mkdir(parents=True)
    print(f"ARTIFACTS {output}", flush=True)
    (ROOT / "target/model-evaluation/latest-classification-run.txt").write_text(str(output), encoding="utf-8")
    initial_production = hashes(ROOT, "src/main")
    candidate_patch = git("diff", "--binary", "HEAD", "--", GENERAL)
    (output / "candidate-general.patch").write_bytes(candidate_patch)
    (output / "candidate-general.java").write_bytes((ROOT / GENERAL).read_bytes())
    metadata = {
        "startedAt": datetime.now(timezone.utc).isoformat(),
        "candidateBaseHead": git("rev-parse", "HEAD").decode().strip(),
        "candidateGeneralPatchSha256": digest(candidate_patch),
        "candidateGeneralSourceSha256": digest((ROOT / GENERAL).read_bytes()),
        "candidateProductionSources": initial_production,
        "harnessSources": hashes(ROOT, "src/test/java/com/fuad/evaluation/stability"),
        "corpusSources": hashes(ROOT, "src/test/resources/evaluation"),
        "runnerSha256": digest(Path(__file__).read_bytes()),
        "model": "phi-router", "baseUrl": "http://localhost:1234/v1", "temperature": 0,
        "repetitions": 5, "httpRetries": 0, "timeoutSeconds": 60,
        "variantOrder": ["pre-merge", "dev-merged", "candidate"], "ordering": "interleaved by repetition",
        "compatibility": "Original production classes, child-first isolated loaders. No historical application classes substituted.",
        "gitStatusBefore": git("status", "--short").decode(),
    }
    save(output / "manifest.json", metadata)
    env = os.environ.copy()
    env["JAVA_HOME"] = args.java_home
    env["PATH"] = str(Path(args.java_home) / "bin") + os.pathsep + env.get("PATH", "")
    try:
        refs = {}
        for name, ref in [("pre-merge", "6d686ea"), ("dev-merged", "1747e6f")]:
            source = output / "references" / name
            commit = archive(ref, source)
            before = hashes(source, "src/main")
            refs[name] = {"commit": commit, "sourceDirectory": str(source), "productionSources": before}
            save(output / "references.json", refs)
            code = maven(Path(args.maven), source, ["-Dmaven.test.skip=true", "compile"], output / f"{name}-compile.log", env)
            if code:
                raise RuntimeError(f"Reference production compilation failed: {name}")
            if before != hashes(source, "src/main"):
                raise RuntimeError(f"Reference production sources changed: {name}")
            refs[name]["compiledClasses"] = hashes(source, "target/classes")
            save(output / "references.json", refs)
        code = maven(Path(args.maven), ROOT, ["test"], output / "unit-tests.log", env)
        metadata["fullUnitSuitePassed"] = code == 0
        unit_failures = []
        for xml in (ROOT / "target/surefire-reports").glob("TEST-*.xml"):
            tree = ET.parse(xml)
            for case in tree.findall("testcase"):
                for kind in ("failure", "error"):
                    failure = case.find(kind)
                    if failure is not None:
                        unit_failures.append({"class": case.get("classname"), "test": case.get("name"),
                                              "kind": kind, "message": failure.get("message")})
        save(output / "unit-failures.json", unit_failures)
        if code:
            # Preserve unrelated unit failures; never fix production just to unblock measurement.
            relevant = ["LocalResearchPlanClassifierTest", "CurrentResearchSkillTest", "ResearchBackendRoutingTest",
                        "OpenAiResearchEngineTest", "QwenLocalResearchEngineTest", "GeneralToResearchTransitionTest",
                        "LocalGeneralComplexityClassifierTest", "GptAssistantEngineTest", "LocalClassifierContractsTest",
                        "GuardedSemanticRouterTest", "ResearchEscalationDetectorTest", "StabilityMetricsTest",
                        "DecisionCorpusLoaderTest", "DecisionCorpusResourcesTest"]
            targeted_code = maven(Path(args.maven), ROOT, ["-Dtest=" + ",".join(relevant), "test"],
                                  output / "affected-unit-tests.log", env)
            metadata["affectedUnitSuitePassed"] = targeted_code == 0
            if targeted_code:
                raise RuntimeError("Affected unit validation failed; corpus not started")
        else:
            metadata["affectedUnitSuitePassed"] = True
        metadata["candidateCompiledClasses"] = hashes(ROOT, "target/classes")
        save(output / "manifest.json", metadata)
        command = ["-Pmodel-evaluation", "-Dtest=ClassificationStabilityCorpusTest", "-Devaluation.model=phi-router",
                   "-Devaluation.repetitions=5", "-Devaluation.report-only=true",
                   "-Devaluation.output-directory=" + str(output),
                   "-Devaluation.reference-pre=" + str(Path(refs['pre-merge']['sourceDirectory']) / 'target/classes'),
                   "-Devaluation.reference-dev=" + str(Path(refs['dev-merged']['sourceDirectory']) / 'target/classes'), "test"]
        save(output / "evaluation-command.json", {"executable": args.maven, "arguments": command, "javaHome": args.java_home})
        code = maven(Path(args.maven), ROOT, command, output / "evaluation.log", env)
        if code:
            raise RuntimeError("Evaluation harness failed; per-case results already written remain available")
        report(output)
        gates = json.loads((output / "gate-failures.json").read_text(encoding="utf-8"))
        print(f"COMPLETE: {len(gates)} failing repetition gates. Report: {output / 'report.md'}", flush=True)
        metadata["finishedAt"] = datetime.now(timezone.utc).isoformat()
        metadata["evaluationComplete"] = True
        return 0
    except Exception as error:
        save(output / "run-error.json", {"error": repr(error)})
        print(f"FAILED: {error}", flush=True)
        return 1
    finally:
        metadata["productionUnchanged"] = initial_production == hashes(ROOT, "src/main")
        metadata["candidatePatchUnchanged"] = candidate_patch == git("diff", "--binary", "HEAD", "--", GENERAL)
        metadata["gitStatusAfter"] = git("status", "--short").decode()
        save(output / "manifest.json", metadata)
        if not metadata["productionUnchanged"] or not metadata["candidatePatchUnchanged"]:
            raise RuntimeError("Production source identity changed during evaluation")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
