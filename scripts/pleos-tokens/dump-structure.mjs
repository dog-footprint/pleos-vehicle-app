import fs from 'fs';
import { parseFig, nodeId } from 'openfig-core';
const files = { guide: '/Users/seungje/Desktop/Pleos_Connect_display_guide_215.fig', assets: '/Users/seungje/Desktop/Pleos_Connect_design_assets_215.fig' };
const hex = c => c ? '#' + [c.r, c.g, c.b].map(v => Math.round(v * 255).toString(16).padStart(2, '0')).join('') + (c.a < 1 ? ` a=${c.a.toFixed(2)}` : '') : null;
for (const [tag, path] of Object.entries(files)) {
  const doc = parseFig(new Uint8Array(fs.readFileSync(path)));
  const nodes = doc.nodes;
  const parentOf = n => n.parentIndex && doc.nodeMap.get(`${n.parentIndex.guid.sessionID}:${n.parentIndex.guid.localID}`);
  const pageOf = n => { let p = n; for (let i = 0; i < 60 && p; i++) { if (p.type === 'CANVAS') return p.name; p = parentOf(p); } return null; };
  const types = {}; nodes.forEach(n => types[n.type] = (types[n.type] || 0) + 1);
  const pages = nodes.filter(n => n.type === 'CANVAS').map(n => n.name);
  const frames = nodes.filter(n => ['FRAME','COMPONENT','COMPONENT_SET','INSTANCE','SECTION'].includes(n.type)).map(n => ({ page: pageOf(n), parent: parentOf(n)?.name, ptype: parentOf(n)?.type, type: n.type, name: n.name, w: n.size && Math.round(n.size.x), h: n.size && Math.round(n.size.y), r: n.cornerRadius, fill: hex(n.fillPaints?.[0]?.color), x: n.transform && Math.round(n.transform.m02), y: n.transform && Math.round(n.transform.m12) }));
  const fonts = {}; const texts = [];
  nodes.filter(n => n.type === 'TEXT').forEach(n => { const f = `${n.fontName?.family} ${n.fontName?.style} ${n.fontSize}`; fonts[f] = (fonts[f] || 0) + 1;
    texts.push({ page: pageOf(n), parent: parentOf(n)?.name, text: n.textData?.characters, font: n.fontName?.family, style: n.fontName?.style, size: n.fontSize, lh: n.lineHeight, color: hex(n.fillPaints?.[0]?.color) }); });
  const styles = nodes.filter(n => n.styleType).map(n => ({ styleType: n.styleType, name: n.name, desc: n.description, fills: (n.fillPaints || []).map(p => ({ type: p.type, color: hex(p.color), opacity: p.opacity })), font: n.fontName && `${n.fontName.family} ${n.fontName.style}`, size: n.fontSize, lh: n.lineHeight, ls: n.letterSpacing }));
  const swatches = nodes.filter(n => ['RECTANGLE','ELLIPSE','FRAME'].includes(n.type) && n.fillPaints?.length === 1 && n.fillPaints[0].type === 'SOLID' && n.name && /[\/]|\d{2,3}$/.test(n.name)).map(n => ({ page: pageOf(n), name: n.name, color: hex(n.fillPaints[0].color), opacity: n.fillPaints[0].opacity }));
  const msgKeys = Object.keys(doc.message).filter(k => k !== 'nodeChanges');
  fs.writeFileSync(`${tag}.summary.json`, JSON.stringify({ header: doc.header, types, pages, fonts, styles, swatches, msgKeys }, null, 1));
  fs.writeFileSync(`${tag}.texts.json`, JSON.stringify(texts)); fs.writeFileSync(`${tag}.frames.json`, JSON.stringify(frames));
  console.log(`== ${tag}: v${doc.header.version}, ${nodes.length} nodes; msg keys: ${msgKeys.join(',')}`);
  console.log('  types', JSON.stringify(types));
  console.log('  pages:', pages.join(' | '));
  console.log('  frames', frames.length, '| styles', styles.length, '| swatches', swatches.length, '| texts', texts.length);
  console.log('  fonts:', Object.entries(fonts).sort((a, b) => b[1] - a[1]).slice(0, 16).map(([k, v]) => `${k}×${v}`).join(' ; '));
}
