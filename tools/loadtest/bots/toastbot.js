// Live HUD verification: decodes the SÜLD HUD from the real action-bar / boss-bar packets with the generated glyph
// table and checks it against the game state. Usage: node hudbot.js <ctl> <classIndex 0..4> <name> [phase]
const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const [ctl, clsS, name, phase] = process.argv.slice(2); const cls = Number(clsS || 0);
const MAP = JSON.parse(fs.readFileSync('/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/hud_glyphs.json', 'utf8'));
const G = {};
for (const [n, [ch, adv]] of Object.entries(MAP.glyphs)) G[ch] = { n, adv };
for (const [k, list] of Object.entries(MAP.pieces)) list.forEach(([ch, adv], i) => G[ch] = { n: 'FILL_' + k.toUpperCase() + '[' + i + ']', adv });
for (const [k, cs] of Object.entries(MAP.charsets)) [...cs.chars].forEach((ch, i) => G[ch] = { n: k + ':' + cs.symbols[i], adv: cs.adv[i] });
function decode(str, half) {
  let pen = -half; const out = [];
  for (const ch of str) { const g = G[ch]; if (!g) { out.push({ n: '??' + ch.codePointAt(0).toString(16), x: pen }); continue; }
    if (!g.n.startsWith('NEG_') && !g.n.startsWith('POS_')) out.push({ n: g.n, x: pen, adv: g.adv }); pen += g.adv; }
  return { glyphs: out, total: pen + half,
    has: (p) => out.some(g => g.n.startsWith(p)), count: (p) => out.filter(g => g.n.startsWith(p)).length,
    text: (set) => out.filter(g => g.n.startsWith(set + ':')).map(g => g.n.slice(set.length + 1)).join(''),
    side: (set, right) => out.filter(g => g.n.startsWith(set + ':') && (right ? g.x > 0 : g.x < 0)).map(g => g.n.slice(set.length + 1)).join(''),
    fill: (kind) => out.filter(g => g.n.startsWith('FILL_' + kind + '[')).reduce((a, g) => a + g.adv - 1, 0) };
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
let bar = null, barAt = 0, boss = new Map();
const results = []; const check = (id, ok, detail) => { results.push({ id, ok, detail }); console.log((ok ? 'PASS ' : 'FAIL ') + id + ' — ' + detail); };
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
// raw packets: this mineflayer version raises no event for 1.21.11 action bars; components arrive as NBT
function flat(n) {
  if (n == null) return '';
  if (typeof n === 'string') return n;
  if (n.type === 'string') return n.value;
  if (n.type === 'compound') { const v = n.value; return flat(v.text) + (v.extra ? flat(v.extra) : ''); }
  if (n.type === 'list') return (n.value.value || []).map(x => flat({ type: n.value.type, value: x })).join('');
  return '';
}
bot._client.on('action_bar', (d) => { bar = decode(flat(d.text), 128); barAt = Date.now(); });
bot._client.on('boss_bar', (d) => {
  if (d.action === 1) boss.delete(d.entityUUID);
  else if (d.action === 0 || d.action === 3) boss.set(d.entityUUID, flat(d.title));
});
const targetFrame = () => { for (const t of boss.values()) { const d = decode(t, 96); if (d.has('TGT_FRAME')) return d; } return null; };
async function fresh(ms = 1500) { const t = Date.now(); while (barAt < t) await sleep(100); await sleep(ms > 0 ? 0 : 0); return bar; }
async function holdClassWeapon() { for (const it of bot.inventory.items()) { if (JSON.stringify(it.componentMap ? [...it.componentMap.values()] : '').includes('weapon.class')) { await bot.equip(it, 'hand'); return true; } } return false; }
async function click(c) { if (c === 'L') bot.swingArm('right'); else { bot.activateItem(); await sleep(60); bot.deactivateItem(); } await sleep(220); }

bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1500); }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(3000);
  let b = await fresh(); const idle = b.text('LINE');
  cmd(`minecraft:give ${name} minecraft:dirt 4`); await sleep(800);
  const dirt = bot.inventory.items().find(i => i.name === 'dirt'); if (dirt) await bot.equip(dirt, 'hand');
  let toast = '';
  for (let attempt = 0; attempt < 4 && !toast.includes('ХАРХОРУМ'); attempt++) {
    const below = bot.blockAt(bot.entity.position.offset(attempt % 2 ? -1 : 1, -1, attempt > 1 ? 1 : 0));
    try { await bot.placeBlock(below, new (require('vec3'))(0, 1, 0)); } catch (e) { }
    for (let i = 0; i < 10 && !toast.includes('ХАРХОРУМ'); i++) { await sleep(150); toast = bar.text('LINE'); }
  }
  b = bar;
  check('hud.toast_in_line', toast.includes('ХАРХОРУМ') && b.has('DIAMOND'), 'idle line "' + idle + '" -> "' + toast + '" (panel still drawn: ' + b.has('DIAMOND') + ')');
  await sleep(3200); b = await fresh();
  check('hud.toast_expires', !b.text('LINE').includes('ХАРХОРУМ') && b.text('LINE').startsWith('БААТАР'), 'after 3 s: "' + b.text('LINE') + '"');
  bot.quit();
});
bot.on('end', () => process.exit(results.some(r => !r.ok) ? 2 : 0));
setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 60000);
