// Compatibility bridge for the legacy GitHub workflow. The real build runs
// under the checked-in, AGP-compatible Gradle wrapper.
tasks.register<Exec>("assembleDebug") {
  workingDir = rootDir
  commandLine(
    "bash",
    "-c",
    """
      set -o pipefail
      log_file="${rootProject.file(".gradle/nested-build.log").absolutePath}"
      mkdir -p "${rootProject.file(".gradle").absolutePath}"

      if "${rootProject.file("gradlew").absolutePath}" \
          :app:assembleDebug \
          :app:testDebugUnitTest \
          :app:lintDebug \
          --no-daemon \
          --project-cache-dir "${rootProject.file(".gradle/nested").absolutePath}" \
          2>&1 | tee "${'$'}log_file"; then
        exit 0
      fi

      python3 - \
          "${'$'}log_file" \
          "${rootProject.file("app/build/test-results/testDebugUnitTest").absolutePath}" \
          "${rootProject.file("app/build/reports/lint-results-debug.xml").absolutePath}" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

text = pathlib.Path(sys.argv[1]).read_text(errors="replace")
lines = text.splitlines()
needles = (
    "e: file:",
    "error:",
    "FAILURE:",
    "* What went wrong:",
    "Execution failed for task",
    "There were failing tests",
    "Lint found",
)
diagnostics = [line for line in lines if any(needle in line for needle in needles)]

results_dir = pathlib.Path(sys.argv[2])
for result in results_dir.glob("*.xml"):
    try:
        suite = ET.parse(result).getroot()
    except ET.ParseError:
        continue
    for case in suite.findall(".//testcase"):
        failures = list(case.findall("failure")) + list(case.findall("error"))
        for failure in failures:
            name = f"{case.get('classname', '')}.{case.get('name', '')}".strip(".")
            details = (failure.get("message") or failure.text or "Test failed").strip()
            diagnostics.append(f"TEST FAILED: {name}: {details[:2500]}")

lint_report = pathlib.Path(sys.argv[3])
if lint_report.exists():
    try:
        lint_issues = ET.parse(lint_report).getroot().findall("issue")
    except ET.ParseError:
        lint_issues = []
    for issue in lint_issues:
        if issue.get("severity") not in {"Error", "Fatal"}:
            continue
        location = issue.find("location")
        source = location.get("file", "") if location is not None else ""
        line = location.get("line", "") if location is not None else ""
        diagnostics.append(
            f"LINT ERROR [{issue.get('id', 'unknown')}] {source}:{line}: "
            f"{issue.get('message', 'Lint failed')}"
        )

if not diagnostics:
    diagnostics = lines[-80:]

for index, diagnostic in enumerate(diagnostics[:40], 1):
    escaped = diagnostic.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::error title=Gradle failure {index}::{escaped}")
PY
      exit 1
    """.trimIndent()
  )
}
