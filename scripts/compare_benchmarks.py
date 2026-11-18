#!/usr/bin/env python3
"""Compare two analyzed campaigns; write data and optionally a standalone figure."""

import argparse
import csv
import json
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument("before", type=Path)
p.add_argument("after", type=Path)
p.add_argument("--plot", action="store_true")
a = p.parse_args()
before = {
    r["size"]: r
    for r in json.loads((a.before / "summary.json").read_text())
    if r["status"] == "pass"
}
after = {
    r["size"]: r
    for r in json.loads((a.after / "summary.json").read_text())
    if r["status"] == "pass"
}
metrics = [
    "nodes",
    "connection_s",
    "idle_mspt",
    "simple_mspt",
    "nested_mspt",
    "simple_rate",
    "nested_rate",
    "nested_p95",
    "nested_tps",
    "heap_added_mib",
]
rows = []
for size in sorted(before.keys() & after.keys()):
    old, new = before[size], after[size]
    assert (
        old["cpus"] == new["cpus"]
        and old["controller_capacity"] == new["controller_capacity"]
    ), "Different workload/power configurations"
    row = {"size": size}
    for metric in metrics:
        row["before_" + metric] = old[metric]
        row["after_" + metric] = new[metric]
    row["connection_speedup"] = old["connection_s"] / new["connection_s"]
    row["nested_tick_reduction_percent"] = 100 * (
        1 - new["nested_mspt"] / old["nested_mspt"]
    )
    rows.append(row)
assert rows, "No completed comparable cases"
(a.after / "comparison.json").write_text(json.dumps(rows, indent=2) + "\n")
with (a.after / "comparison.csv").open("w") as f:
    w = csv.DictWriter(f, fieldnames=list(rows[0]), lineterminator="\n")
    w.writeheader()
    w.writerows(rows)
for r in rows:
    print(
        r["size"],
        "connection",
        round(r["before_connection_s"], 3),
        "->",
        round(r["after_connection_s"], 3),
        "nested ms",
        round(r["before_nested_mspt"], 2),
        "->",
        round(r["after_nested_mspt"], 2),
        "TPS",
        round(r["after_nested_tps"], 2),
    )
if a.plot:
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    plt.rcParams.update(
        {"font.size": 10, "axes.spines.top": False, "axes.spines.right": False}
    )
    fig, axes = plt.subplots(2, 2, figsize=(12, 8), layout="constrained")
    specs = [
        ("connection_s", "Collegamento a RS", "Secondi (scala log)", True),
        ("idle_mspt", "Crafter inattivo", "Millisecondi/tick (scala log)", True),
        ("nested_mspt", "Crafting annidato", "Millisecondi/tick", False),
        (
            "heap_added_mib",
            "Heap aggiuntivo dopo GC",
            "MiB rispetto alla baseline",
            False,
        ),
    ]
    x = list(range(len(rows)))
    for ax, (metric, title, unit, log) in zip(axes.flat, specs):
        for prefix, offset, color, label in [
            ("before", -0.19, "#bd6850", "Prima: 0.1.0"),
            ("after", 0.19, "#267e83", "Dopo: 0.1.1"),
        ]:
            ax.bar(
                [v + offset for v in x],
                [r[prefix + "_" + metric] for r in rows],
                width=0.36,
                color=color,
                label=label,
            )
        if log:
            ax.set_yscale("log")
        if metric == "nested_mspt":
            ax.axhline(50, color="#9c2830", ls="--", label="Budget 20 TPS: 50 ms")
        ax.set_xticks(x, [str(r["size"]) + "³" for r in rows])
        ax.set_title(title)
        ax.set_ylabel(unit)
        ax.grid(axis="y", alpha=0.2)
        ax.set_axisbelow(True)
        ax.legend(fontsize=8)
    fig.suptitle(
        "Reborn Storage 1.16.5 — effetto delle quattro ottimizzazioni\n64³ non avviato",
        fontsize=14,
    )
    fig.savefig(a.after / "comparison.png", dpi=180)
    svg = a.after / "comparison.svg"
    fig.savefig(svg)
    svg.write_text(
        "\n".join(line.rstrip() for line in svg.read_text().splitlines()) + "\n"
    )
