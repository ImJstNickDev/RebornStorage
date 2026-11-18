#!/usr/bin/env python3
"""Summarize JFR JSON execution samples by recorded benchmark phase (server thread only)."""
import argparse, bisect, collections, datetime, json
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('events',type=Path); p.add_argument('timeline',type=Path); p.add_argument('output',type=Path)
a=p.parse_args()
timeline=[json.loads(line) for line in a.timeline.read_text().splitlines()]
times=[r['epoch_ms'] for r in timeline]
def phase(ts):
    ms=datetime.datetime.fromisoformat(ts.replace('Z','+00:00')).timestamp()*1000
    i=bisect.bisect_right(times,ms)-1
    return timeline[i]['phase'] if i>=0 else 'startup'
def frame(f):
    m=f['method'];return m['type']['name'].replace('/','.')+'.'+m['name']
branches=['NodeRequirements.getSingleItemRequirementSet','IoUtil.extractFromInternalItemStorage','CraftingPattern.getOutput','CraftingPattern.getByproducts','Network.insertItem','CraftingTask.onSingleDone','PortBenchmark.keepBusy','PortBenchmark.clearOutput']
counts=collections.Counter(); inclusive=collections.defaultdict(collections.Counter); leaf=collections.defaultdict(collections.Counter); exclusive=collections.defaultdict(collections.Counter);alloc=collections.defaultdict(collections.Counter)
for event in json.loads(a.events.read_text())['recording']['events']:
    v=event['values']; t=v.get('sampledThread',v.get('eventThread')) or {}
    if t.get('javaName')!='Server thread':continue
    ph=phase(v['startTime']); stack=[frame(f) for f in (v.get('stackTrace') or {}).get('frames',[])]
    if event['type']=='jdk.ExecutionSample':
        counts[ph]+=1
        for m in dict.fromkeys(stack):inclusive[ph][m]+=1
        if stack:leaf[ph][stack[0]]+=1
        branch='other'
        for m in stack:
            found=next((b for b in branches if m.endswith(b)),None)
            if found:branch=found;break
        exclusive[ph][branch]+=1
    elif event['type'] in ('jdk.ObjectAllocationInNewTLAB','jdk.ObjectAllocationOutsideTLAB'):
        alloc[ph]['tlab_or_outside_bytes']+=v.get('tlabSize',v.get('allocationSize',0))
        alloc[ph]['sampled_events']+=1
out={ph:{'execution_samples':n,'exclusive_branches':dict(exclusive[ph]),'inclusive_top':inclusive[ph].most_common(45),'leaf_top':leaf[ph].most_common(25),'allocation':dict(alloc[ph])} for ph,n in counts.items()}
a.output.write_text(json.dumps(out,indent=2)+'\n')
for ph in ['idle','simple','nested']:
    r=out.get(ph,{})
    print(ph,r.get('execution_samples'),r.get('exclusive_branches'),r.get('allocation'))
