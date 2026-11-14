import fs from 'fs';
import { parseFig } from 'openfig-core';
const files = { guide: '/Users/seungje/Desktop/Pleos_Connect_display_guide_215.fig', assets: '/Users/seungje/Desktop/Pleos_Connect_design_assets_215.fig' };
const hex = c => c ? '#' + [c.r, c.g, c.b].map(v => Math.round(v * 255).toString(16).padStart(2, '0')).join('') + (c.a < 1 ? `@${c.a.toFixed(2)}` : '') : null;
const all = {};
for (const [tag, path] of Object.entries(files)) {
  const doc = parseFig(new Uint8Array(fs.readFileSync(path)));
  const sets = doc.nodes.filter(n => n.type === 'VARIABLE_SET');
  const byGuid = new Map(sets.map(s => [`${s.guid.sessionID}:${s.guid.localID}`, s]));
  const byKey = new Map(sets.map(s => [s.key, s]));
  const varByGuid = new Map(doc.nodes.filter(n => n.type === 'VARIABLE').map(v => [`${v.guid.sessionID}:${v.guid.localID}`, v]));
  const varByKey = new Map(doc.nodes.filter(n => n.type === 'VARIABLE').map(v => [v.key, v]));
  const resolveSet = v => v.variableSetID?.guid ? byGuid.get(`${v.variableSetID.guid.sessionID}:${v.variableSetID.guid.localID}`) : byKey.get(v.variableSetID?.assetRef?.key);
  const modeNames = set => Object.fromEntries((set?.variableSetModes || []).map(m => [`${m.id.sessionID}:${m.id.localID}`, m.name]));
  const val = d => { const x = d?.value || {}; if (x.colorValue) return hex(x.colorValue); if (x.floatValue !== undefined) return x.floatValue; if (x.textValue !== undefined) return x.textValue; if (x.boolValue !== undefined) return x.boolValue; if (x.alias) { const a = x.alias.guid ? varByGuid.get(`${x.alias.guid.sessionID}:${x.alias.guid.localID}`) : varByKey.get(x.alias.assetRef?.key); return '→' + (a?.name || '?'); } return JSON.stringify(d).slice(0, 60); };
  const out = [];
  for (const v of doc.nodes.filter(n => n.type === 'VARIABLE')) {
    const set = resolveSet(v); const mn = modeNames(set); const modes = {};
    for (const e of (v.variableDataValues?.entries || [])) modes[mn[`${e.modeID.sessionID}:${e.modeID.localID}`] || `${e.modeID.sessionID}:${e.modeID.localID}`] = val(e.variableData);
    out.push({ set: set?.name || '?', name: v.name, type: v.variableResolvedType, modes });
  }
  all[tag] = out;
  const bySet = {}; out.forEach(o => (bySet[o.set] = bySet[o.set] || []).push(o));
  console.log(`== ${tag}: ${out.length} variables in ${Object.keys(bySet).length} sets`);
  for (const [s, list] of Object.entries(bySet)) console.log(`   [${s}] ${list.length} vars, modes: ${Object.keys(list[0].modes).join('/')} · ${list[0].name}=${Object.values(list[0].modes).join(' | ')}`);
}
fs.writeFileSync('variables.json', JSON.stringify(all, null, 1));
// theme-ish tokens with light/dark colour values, resolved through aliases where possible
const tokens = all.assets.concat(all.guide).filter(o => o.type === 'COLOR' && (o.set.toLowerCase().includes('theme') || /^(text|icon|surface|background|bg|border|button|accent|primary|informative|driving|switch|dim|overlay|controller)/i.test(o.name)));
console.log(`== colour tokens (${tokens.length}); first 140:`);
for (const t of tokens.slice(0, 140)) console.log(`   ${t.set} · ${t.name} · ${Object.entries(t.modes).map(([m, v]) => `${m}=${v}`).join('  ')}`);
