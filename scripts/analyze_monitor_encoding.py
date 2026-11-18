#!/usr/bin/env python3
"""Compare immediate/coalesced monitor encoding probes, keeping raw data and provenance."""
import argparse
import csv
import json
import shutil
import statistics
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('immediate', type=Path)
p.add_argument('coalesced', type=Path)
p.add_argument('output', type=Path)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
rows = []
for mode, folder in [('immediate', a.immediate), ('coalesced', a.coalesced)]:
    d = json.loads((folder / 'benchmark.json').read_text())
    process = json.loads((folder / 'process.json').read_text())
    assert d['status'] == 'pass' and process['exit_code'] == 0
    assert d['monitor_encoding'] == mode
    shutil.copy2(folder / 'benchmark.json', a.output / f'{mode}-raw.json')
    shutil.copy2(folder.parent / 'host.json', a.output / f'{mode}-host.json')
    compact = {k: v for k, v in process.items() if k != 'samples'}
    (a.output / f'{mode}-process.json').write_text(json.dumps(compact, indent=2) + '\n')
    for phase in ['simple', 'nested']:
        m = [v for v in d['measurements'] if v['phase'] == phase]
        assert len(m) == 3 and all(v['ticks'] == 120 and v['produced'] > 0 for v in m)
        seconds = sum(v['seconds'] for v in m)
        rows.append({
            'mode': mode, 'phase': phase, 'size': d['size'], 'cpus': d['cpus'],
            'mspt': statistics.mean(t for v in m for t in v['tick_ms_samples']),
            'tps': sum(v['ticks'] for v in m) / seconds,
            'items_s': sum(v['produced'] for v in m) / seconds,
            'snapshots_s': sum(v['monitor_snapshots'] for v in m) / seconds,
            'encoded_bytes_s': sum(v['monitor_encoded_bytes'] for v in m) / seconds,
        })
assert len({(r['size'], r['cpus']) for r in rows}) == 1
(a.output / 'comparison.json').write_text(json.dumps(rows, indent=2) + '\n')
with (a.output / 'comparison.csv').open('w') as f:
    writer = csv.DictWriter(f, fieldnames=list(rows[0]), lineterminator='\n')
    writer.writeheader()
    writer.writerows(rows)
for r in rows:
    print(r)
