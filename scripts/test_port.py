#!/usr/bin/env python3
"""Run isolated Forge smoke tests. Requires JDK 17 for Gradle and an installed JDK 8."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--client", action="store_true", help="Test client screens using a copy of the server test world (requires a display)")
parser.add_argument("--accept-eula", action="store_true", help="Accept the Minecraft EULA for this isolated test server")
args = parser.parse_args()
work = ROOT / "run" / ("client" if args.client else "integration")
work.mkdir(parents=True, exist_ok=True)
if not args.client:
    if args.accept_eula:
        (work / "eula.txt").write_text("eula=true\n")
    if not (work / "eula.txt").exists() or "eula=true" not in (work / "eula.txt").read_text():
        parser.error("Accept the Minecraft EULA in run/integration/eula.txt or use --accept-eula")
    (work / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=flat\ngenerate-structures=false\nmax-tick-time=120000\nview-distance=3\n")
else:
    source = ROOT / "run/integration/world"
    if not source.is_dir():
        parser.error("Run server tests first")
    target = work / "saves/PortTest"
    if target.exists():
        shutil.rmtree(target)  # Only the disposable copy owned by this test.
    shutil.copytree(source, target)
    (work / "options.txt").write_text("tutorialStep:none\npauseOnLostFocus:false\nrenderDistance:3\nmaxFps:60\nguiScale:2\n")
run_task = "runClient" if args.client else "runServer"
prop = "clientTest" if args.client else "integrationTest"
subprocess.run(["bash", "gradlew", run_task, "-P" + prop, "-PexportLaunch", "--console=plain"], cwd=ROOT, check=True)
launch = json.loads((ROOT / f"build/{run_task}-launch.json").read_text())
for iteration in range(1 if args.client else 2):
    result = work / ("port-client-result.txt" if args.client else "port-test-result.txt")
    result.unlink(missing_ok=True)
    log = work / f"test-{iteration + 1}.log"
    with log.open("w") as output:
        process = subprocess.Popen(launch["command"], cwd=launch["directory"], env={**os.environ, **launch["environment"]}, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            code = process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            process.terminate()
            process.wait(timeout=20)
            raise SystemExit(f"Test timed out. See {log}")
    if code or not result.exists() or not result.read_text().startswith("PASS:"):
        raise SystemExit(f"Test failed (exit={code}). See {log}")
    text = log.read_text()
    if any(error in text for error in ("Could not read network node", "Could not deserialize crafting task", "PORT TEST FAILED", "PORT CLIENT FAILED")):
        raise SystemExit(f"Runtime failure. See {log}")
    print(f"Run {iteration + 1}: {result.read_text().strip()} ({log})", flush=True)
