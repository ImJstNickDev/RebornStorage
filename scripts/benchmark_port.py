#!/usr/bin/env python3
"""Launch serial, isolated Forge benchmarks; never modifies production source or Prism worlds."""

import argparse, datetime, json, os, pathlib, subprocess, time, signal

ROOT = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument("--sizes", nargs="+", type=int, default=[4, 8, 16])
p.add_argument("--ticks", type=int, default=240)
p.add_argument("--repeats", type=int, default=3)
p.add_argument("--heap", default="6G")
p.add_argument(
    "--controller-capacity",
    type=int,
    help="Override RS buffer in this disposable world only (creative controllers also need sufficient capacity)",
)
p.add_argument(
    "--timeout", type=int, default=1200, help="Wall seconds allowed per isolated case"
)
p.add_argument("--jfr", action="store_true", help="Record Java Flight Recorder profile; profiled timings are diagnostic")
p.add_argument("--property", action="append", default=[], help="Extra bench.name=value property (diagnostic workloads)")
p.add_argument("--tag", default=datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
a = p.parse_args()
assert all(3 <= s <= 64 for s in a.sizes)
subprocess.run(
    [
        "bash",
        "gradlew",
        "runServer",
        "-Pbenchmark",
        *(["-PwithExtraStorage"] if "bench.extraStorageDisks=true" in a.property else []),
        "-PexportLaunch",
        "--console=plain",
    ],
    cwd=ROOT,
    check=True,
)
launch = json.loads((ROOT / "build/runServer-launch.json").read_text())
base = ROOT / "run/benchmarks" / a.tag
base.mkdir(parents=True, exist_ok=False)
metadata = {
    "sizes": a.sizes,
    "ticks": a.ticks,
    "repeats": a.repeats,
    "heap": a.heap,
    "controller_capacity_override": a.controller_capacity,
    "jfr": a.jfr,
    "benchmark_properties": a.property,
    "source_commit": subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip(),
    "date": datetime.datetime.now().astimezone().isoformat(),
    "cpu": next(
        l.split(":", 1)[1].strip()
        for l in pathlib.Path("/proc/cpuinfo").read_text().splitlines()
        if l.startswith("model name")
    ),
    "logical_cpus": os.cpu_count(),
    "initial_load": os.getloadavg(),
}
(base / "host.json").write_text(json.dumps(metadata, indent=2))
print("CAMPAIGN", base, flush=True)
for size in a.sizes:
    work = base / f"size-{size}"
    if (work / "skipped.json").exists():
        print(f"SIZE {size}: skipped by explicit case marker", flush=True)
        continue
    work.mkdir()
    if a.controller_capacity is not None:
        assert a.controller_capacity > 0
        defaults = work / "defaultconfigs"
        defaults.mkdir()
        (defaults / "refinedstorage-server.toml").write_text(
            f"[controller]\ncapacity = {a.controller_capacity}\n"
        )
    (work / "eula.txt").write_text((ROOT / "run/integration/eula.txt").read_text())
    (work / "server.properties").write_text(
        'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=flat\nlevel-seed=12345\ngenerate-structures=false\nview-distance=3\nmax-tick-time=0\nspawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains","structures":{"structures":{}}}\n'
    )
    cmd = [
        x
        for x in launch["command"]
        if not x.startswith("-Xmx") and not x.startswith("-Xms")
    ]
    cmd[1:1] = [
        "-Xms1G",
        "-Xmx" + a.heap,
        "-Dbench.size=" + str(size),
        "-Dbench.ticks=" + str(a.ticks),
        "-Dbench.repeats=" + str(a.repeats),
    ]
    for prop in a.property:
        assert "=" in prop and (prop.startswith("bench.") or prop.startswith("reborn.craftingExperiment="))
        cmd.insert(1, "-D" + prop)
    if a.jfr:
        cmd[1:1] = ["-XX:+FlightRecorder", "-XX:FlightRecorderOptions=stackdepth=128",
                    "-XX:StartFlightRecording=settings=profile,dumponexit=true,filename=" + str(work / "profile.jfr")]
    samples = []
    last = ""
    start = time.monotonic()
    reason = ""
    peak = 0
    with (work / "server.log").open("w") as log:
        proc = subprocess.Popen(
            cmd,
            cwd=work,
            env={**os.environ, **launch["environment"]},
            stdout=log,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        while proc.poll() is None:
            elapsed = time.monotonic() - start
            try:
                status = pathlib.Path(f"/proc/{proc.pid}/status").read_text()
                rss = (
                    int(
                        next(
                            x.split()[1]
                            for x in status.splitlines()
                            if x.startswith("VmRSS:")
                        )
                    )
                    * 1024
                )
                peak = max(peak, rss)
                mem = pathlib.Path("/proc/meminfo").read_text()
                available = (
                    int(
                        next(
                            x.split()[1]
                            for x in mem.splitlines()
                            if x.startswith("MemAvailable:")
                        )
                    )
                    * 1024
                )
                state = {}
                try:
                    state = json.loads((work / "benchmark.json").read_text())
                except (OSError, ValueError):
                    pass
                phase = state.get("phase", "starting")
                samples.append(
                    {
                        "elapsed": elapsed,
                        "rss_bytes": rss,
                        "available_bytes": available,
                        "load1": os.getloadavg()[0],
                        "phase": phase,
                    }
                )
                if phase != last:
                    print(
                        f"SIZE {size}: {phase}, elapsed={elapsed:.1f}s RSS={rss / 2**20:.0f}MiB",
                        flush=True,
                    )
                    last = phase
                if available < 1024**3:
                    reason = "host_available_memory_below_1GiB"
            except (OSError, StopIteration):
                pass
            if elapsed > a.timeout:
                reason = "wall_timeout"
            if reason:
                os.killpg(proc.pid, signal.SIGTERM)
                try:
                    proc.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    os.killpg(proc.pid, signal.SIGKILL)
                    proc.wait()
                break
            time.sleep(1)
        code = proc.wait()
    outcome = {
        "exit_code": code,
        "runner_stop_reason": reason,
        "elapsed_seconds": time.monotonic() - start,
        "rss_peak_bytes": peak,
        "samples": samples,
    }
    (work / "process.json").write_text(json.dumps(outcome, indent=2))
    state = {}
    try:
        state = json.loads((work / "benchmark.json").read_text())
    except (OSError, ValueError):
        pass
    print(
        f"SIZE {size}: exit={code}, status={state.get('status')}, stop={reason}",
        flush=True,
    )
print("DONE", base, flush=True)
