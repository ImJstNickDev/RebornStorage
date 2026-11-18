#!/usr/bin/env python3
"""Standalone figures for the two ms/tick investigations (requires matplotlib)."""
import argparse,json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
p=argparse.ArgumentParser(description=__doc__);p.add_argument('report',type=Path);a=p.parse_args()
r=a.report
samples=json.loads((r/'jfr-summary.json').read_text())['nested']
branches=samples['exclusive_branches'];n=samples['execution_samples']
labels=['Estrazioni simulate/reali','Preparazione ingredienti','Residui delle ricette','Calcolo output','Inserimento nella rete','Notifiche senza UI','Altro']
keys=['IoUtil.extractFromInternalItemStorage','NodeRequirements.getSingleItemRequirementSet','CraftingPattern.getByproducts','CraftingPattern.getOutput','Network.insertItem','CraftingTask.onSingleDone']
values=[branches[k]*100/n for k in keys];values.append(100-sum(values))
data=json.loads((r/'measurements.json').read_text())
plt.rcParams.update({'font.size':10,'axes.spines.top':False,'axes.spines.right':False})
fig,axes=plt.subplots(1,2,figsize=(13,5),layout='constrained')
ax=axes[0];bars=ax.barh(labels,values,color='#267e83');ax.invert_yaxis();ax.set_xlim(0,42)
ax.bar_label(bars,fmt='%.1f%%',padding=4);ax.set_xlabel('Quota dei campioni del thread server (%)')
ax.set_title('Prima indagine: 32³, crafting annidato\n2.452 campioni JFR, rami disgiunti')
ax=axes[1]
for order,offset,color in [(1,-.19,'#267e83'),(4,.19,'#bd6850')]:
 vals=[next(v['mspt'] for v in data if v['orders']==order and v['phase']==phase and not v['jfr']) for phase in ['simple','nested']]
 bars=ax.bar([offset,1+offset],vals,width=.36,color=color,label=f'{order} '+('ordine' if order==1 else 'ordini'))
 ax.bar_label(bars,fmt='%.2f',padding=4)
ax.set_xticks([0,1],['Semplice','Annidato']);ax.set_ylim(0,30);ax.set_ylabel('Tempo medio del tick (ms)');ax.legend()
ax.set_title('Seconda indagine: stesso crafter 16³\nListener di conteggio; nessun pacchetto inviato')
fig.suptitle('Reborn Storage 0.1.1 — diagnosi, non risultati di nuove ottimizzazioni',fontsize=14)
fig.savefig(r/'findings.png',dpi=180)
fig.savefig(r/'findings.svg')
f=r/'findings.svg';f.write_text('\n'.join(line.rstrip() for line in f.read_text().splitlines())+'\n')
