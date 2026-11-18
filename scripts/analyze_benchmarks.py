#!/usr/bin/env python3
"""Summarize a benchmark campaign; optional --plot uses matplotlib."""

import argparse, csv, json, math, statistics, shutil, tomllib
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("campaign", type=Path)
p.add_argument("output", type=Path)
p.add_argument("--plot", action="store_true")
p.add_argument(
    "--replace-case",
    type=Path,
    action="append",
    default=[],
    help="Use a separately rerun case, retaining its host metadata",
)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
host = json.loads((a.campaign / "host.json").read_text())
rows = []
runs = []
folders = {int(f.name.split("-")[1]): f for f in a.campaign.glob("size-*")}
for f in a.replace_case:
    folders[int(f.name.split("-")[1])] = f
    shutil.copy2(f.parent / "host.json", a.output / f"host-{f.name}.json")
for size, folder in sorted(folders.items()):
    if (folder / "skipped.json").exists():
        skip = json.loads((folder / "skipped.json").read_text())
        shutil.copy2(folder / "skipped.json", a.output / f"skipped-{size}.json")
        rows.append({"size": size, "status": "skipped", "reason": skip["reason"]})
        continue
    if not (folder / "benchmark.json").exists():
        continue
    d = json.loads((folder / "benchmark.json").read_text())
    process = (
        json.loads((folder / "process.json").read_text())
        if (folder / "process.json").exists()
        else {}
    )
    if d.get("status") == "running" and "exit_code" in process:
        d["status"] = "interrupted"
    if d.get("status") == "pass" and not any(
        m["produced"] > 0 for m in d.get("measurements", [])
    ):
        d["status"] = "non_operational"
    shutil.copy2(folder / "benchmark.json", a.output / f"raw-{d['size']}.json")
    # Exclude per-second machine memory/load details from the public report; retain compact provenance.
    compact = {k: v for k, v in process.items() if k != "samples"}
    samples = process.get("samples", [])
    compact["host_load1_min"] = min((s["load1"] for s in samples), default=None)
    compact["host_load1_max"] = max((s["load1"] for s in samples), default=None)
    (a.output / f"process-{d['size']}.json").write_text(
        json.dumps(compact, indent=2) + "\n"
    )
    row = {
        "size": d["size"],
        "status": d["status"],
        "blocks": d["blocks"],
        "cpus": d["cpus"],
        "nodes": d.get("network_nodes"),
        "placement_s": d.get("bulk_placement_ms", 0) / 1000,
        "assembly_s": d.get("assembly_ms", 0) / 1000,
        "connection_s": d["network_connection_ms"] / 1000
        if "network_connection_ms" in d
        else None,
        "save_s": d["explicit_save_ms"] / 1000 if "explicit_save_ms" in d else None,
        "retained_heap_mib": d["idle_retained_heap_bytes"] / 2**20
        if "idle_retained_heap_bytes" in d
        else None,
        "baseline_heap_mib": d.get("baseline_retained_heap_bytes", 0) / 2**20,
        "rss_peak_mib": process.get("rss_peak_bytes", 0) / 2**20,
        "fe_tick": d.get("configured_fe_per_tick"),
        "last_phase": d["phase"],
        "stop_reason": process.get("runner_stop_reason", ""),
        "elapsed_s": process.get("elapsed_seconds"),
        "campaign": folder.parent.name,
    }
    costs = tomllib.loads((folder / "config/rebornstorage-common.toml").read_text())[
        "general"
    ]
    rs = tomllib.loads(
        (folder / "world/serverconfig/refinedstorage-server.toml").read_text()
    )
    n = d["size"]
    row["controller_capacity"] = rs["controller"]["capacity"]
    row["fe_tick"] = (
        d["cpus"] * costs["multiblock_crafter_cpu_cost"]
        + costs["multiblock_crafter_storage_cost"]
        + (12 * n - 16) * costs["multiblock_crafter_frame_cost"]
        + 6 * (n - 2) ** 2 * costs["multiblock_crafter_heat_cost"]
        + rs["controller"]["baseUsage"]
        + rs["diskDrive"]["usage"]
        + rs["diskDrive"]["diskUsage"]
    )
    row["heap_added_mib"] = (
        row["retained_heap_mib"] - row["baseline_heap_mib"]
        if row["retained_heap_mib"] is not None
        else None
    )
    connecting = [s["elapsed"] for s in samples if s["phase"] == "connecting"]
    row["connecting_observed_s"] = (
        max(connecting) - min(connecting) if connecting else None
    )
    for phase in ["baseline", "idle", "simple", "nested"]:
        ms = [m for m in d.get("measurements", []) if m["phase"] == phase]
        if not ms:
            continue
        ticks = sorted(t for m in ms for t in m.get("tick_ms_samples", []))
        row[phase + "_mspt"] = (
            statistics.mean(ticks)
            if ticks
            else statistics.mean(m["tick_ms"]["mean"] for m in ms)
        )
        row[phase + "_p95"] = (
            ticks[min(len(ticks) - 1, math.ceil(len(ticks) * 0.95) - 1)]
            if ticks
            else statistics.median(m["tick_ms"]["p95"] for m in ms)
        )
        row[phase + "_max"] = max(m["tick_ms"]["max"] for m in ms)
        row[phase + "_rate"] = sum(m["produced"] for m in ms) / sum(
            m["seconds"] for m in ms
        )
        row[phase + "_tps"] = sum(m["ticks"] for m in ms) / sum(
            m["seconds"] for m in ms
        )
        row[phase + "_gc_ms"] = sum(m["gc_ms"] for m in ms)
        row[phase + "_core_equivalents"] = statistics.mean(
            m["process_cpu_core_equivalents"] for m in ms
        )
        row[phase + "_rate_min"] = min(m["items_per_second"] for m in ms)
        row[phase + "_rate_max"] = max(m["items_per_second"] for m in ms)
        for m in ms:
            runs.append(
                {
                    "size": d["size"],
                    "phase": phase,
                    "repeat": m["repeat"],
                    "seconds": m["seconds"],
                    "ticks": m["ticks"],
                    "mspt": m["tick_ms"]["mean"],
                    "p95_ms": m["tick_ms"]["p95"],
                    "max_ms": m["tick_ms"]["max"],
                    "items_s": m["items_per_second"],
                    "produced": m["produced"],
                    "tps": m["observed_tps"],
                    "heap_peak_mib": m["heap_peak_bytes"] / 2**20,
                    "gc_ms": m["gc_ms"],
                    "planning_ms": m["planning_ms"],
                    "process_cpu_cores": m["process_cpu_core_equivalents"],
                    "rs_network_ms": m["rs_network_ms"].get("mean", 0),
                }
            )
    rows.append(row)
for name, data in [("summary.csv", rows), ("rounds.csv", runs)]:
    keys = list(dict.fromkeys(k for row in data for k in row))
    with (a.output / name).open("w") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        w.writerows(data)
(a.output / "host.json").write_text(json.dumps(host, indent=2) + "\n")
(a.output / "summary.json").write_text(json.dumps(rows, indent=2) + "\n")
for row in rows:
    if row["status"] == "skipped":
        print(row["size"], "skipped")
        continue
    print(
        row["size"],
        row["status"],
        "idle",
        round(row.get("idle_mspt", 0), 3),
        "simple",
        round(row.get("simple_rate", 0)),
        round(row.get("simple_mspt", 0), 2),
        "nested",
        round(row.get("nested_rate", 0)),
        round(row.get("nested_mspt", 0), 2),
        "heap+",
        round(row["heap_added_mib"]) if row["heap_added_mib"] is not None else "N/D",
    )
if a.plot:
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    good = [r for r in rows if r["status"] == "pass" and r.get("nested_rate", 0) > 0]
    x = list(range(len(good)))
    labels = [str(r["size"]) + "³" for r in good]
    plt.rcParams.update(
        {"font.size": 10, "axes.spines.top": False, "axes.spines.right": False}
    )
    fig, axs = plt.subplots(2, 2, figsize=(12, 8), layout="constrained")
    for phase, label, color in [
        ("idle", "Inattivo", "#7b8794"),
        ("simple", "Semplice", "#247ba0"),
        ("nested", "Annidato", "#c45c36"),
    ]:
        axs[0, 0].plot(
            x, [r[phase + "_mspt"] for r in good], marker="o", label=label, color=color
        )
    axs[0, 0].axhline(50, color="#ba1b1d", ls="--", lw=1, label="Budget 20 TPS: 50 ms")
    axs[0, 0].set_yscale("log")
    axs[0, 0].set_ylabel("Tempo medio del tick (ms, scala log)")
    axs[0, 0].legend(fontsize=8)
    for ax, phase, title, color in [
        (axs[0, 1], "simple", "Crafting semplice: assi di quercia/s", "#247ba0"),
        (axs[1, 0], "nested", "Crafting annidato: carrelli con tramoggia/s", "#c45c36"),
    ]:
        y = [r[phase + "_rate"] for r in good]
        err = [
            [v - r[phase + "_rate_min"] for r, v in zip(good, y)],
            [r[phase + "_rate_max"] - v for r, v in zip(good, y)],
        ]
        ax.bar(x, y, color=color, yerr=err, capsize=4)
        ax.set_yscale("log")
        ax.set_title(title)
        ax.set_ylabel("Oggetti finali/s (scala log)")
    axs[1, 1].bar(x, [max(0, r["heap_added_mib"]) for r in good], color="#665191")
    axs[1, 1].set_title("Memoria aggiuntiva rispetto al server senza struttura")
    axs[1, 1].set_ylabel("Heap trattenuto dopo GC (MiB)")
    for ax in axs.flat:
        ax.set_xticks(x, labels)
        ax.set_xlabel("Dimensione esterna del crafter")
        ax.grid(axis="y", alpha=0.2)
        ax.set_axisbelow(True)
    missing = [
        str(r["size"]) + "³" for r in rows if r["status"] not in ("pass", "skipped")
    ]
    skipped = [str(r["size"]) + "³" for r in rows if r["status"] == "skipped"]
    title = "Reborn Storage 1.16.5 — benchmark del commit " + host["source_commit"][:7]
    if missing:
        title += "\nSenza misure a regime: " + ", ".join(missing)
    if skipped:
        title += "\nNon avviato: " + ", ".join(skipped)
    fig.suptitle(title, fontsize=14)
    fig.savefig(a.output / "performance.png", dpi=180)
    fig.savefig(a.output / "performance.svg")
