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
const RES = ['RES_BAATAR', 'RES_MERGEN', 'RES_BOO', 'RES_DARKHAN', 'RES_KHULEGCHIN'];
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) { await bot.clickWindow(10 + cls, 0, 0).catch(() => {}); await sleep(1500); }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(3000);
  let b = await fresh();
  check('hud.glyph_panel', b.has('DIAMOND') && b.total === 256 && !b.has('??'), 'diamond present, line width ' + b.total + ', unknown glyphs ' + b.count('??'));
  const hpTxt = b.side('ROW_A', false); const hpNum = Number(hpTxt.split('/')[0].split('+')[0]);
  check('hud.hp_matches_player', Math.abs(hpNum - Math.round(bot.health)) <= 1, 'HUD "' + hpTxt + '" vs client health ' + bot.health.toFixed(1));
  check('hud.food_matches_player', b.side('ROW_B', false) === bot.food + '/20', 'HUD row B left "' + b.side('ROW_B', false) + '" vs food ' + bot.food);
  if (phase === 'reconnect') {
    const lv = b.text('LEVEL'); console.log('LEVEL ' + lv);
    check('hud.reconnect_level', lv === process.argv[6], 'level after rejoin ' + lv + ' (before quit ' + process.argv[6] + ')');
    bot.quit(); return;
  }
  // class-specific resource: wait for some regeneration, the fill must be this class's colour set
  await sleep(6000); b = await fresh();
  const own = RES[cls]; const other = RES.filter(r => r !== own).some(r => b.fill(r) > 0);
  check('hud.class_resource', b.fill(own) > 0 && !other, own + ' fill ' + b.fill(own) + 'px, other classes ' + (other ? 'present' : 'none') + ', text "' + b.side('ROW_A', true) + '"');
  // EXP / level change
  const lv0 = Number(b.text('LEVEL')); const exp0 = b.side('ROW_B', true);
  const locked0 = b.count('SLOT_LOCKED');
  cmd(`suld exp ${name} 40000`); await sleep(1500); b = await fresh();
  const lv1 = Number(b.text('LEVEL'));
  check('hud.level_up', lv1 > lv0, 'level ' + lv0 + ' -> ' + lv1 + ' (EXP ' + exp0 + ' -> ' + b.side('ROW_B', true) + ')');
  const unlock = [0, 1, 10, 20, 35]; const expectLocked = [1, 2, 3, 4].filter(s => unlock[s] > lv1).length;
  check('hud.slots_unlock_with_level', b.count('SLOT_LOCKED') === expectLocked && locked0 >= expectLocked, 'locked slots ' + locked0 + ' -> ' + b.count('SLOT_LOCKED') + ' (expected ' + expectLocked + ' at level ' + lv1 + ')');
  // skill cooldown: cast spell I with its combo, slot I must show a countdown, then be ready again
  console.log('weapon in hand: ' + await holdClassWeapon()); await sleep(500);
  const res = () => Number(bar.side('ROW_A', true).split('/')[0]);
  for (let i = 0; i < 40 && !(res() >= 40); i++) await sleep(500);   // enough resource for spell I
  const resBefore = bar.side('ROW_A', true);
  const x = cls === 1 ? 'L' : 'R', y = x === 'R' ? 'L' : 'R';
  for (const c of [x, y, x]) await click(c);
  await sleep(300); b = await fresh();
  const cdSeen = b.count('SLOT_CD') > 0 && b.text('SLOT').length > 0;
  check('hud.cooldown_shown', cdSeen, 'after the combo: SLOT_CD ' + b.count('SLOT_CD') + ', countdown "' + b.text('SLOT') + '", resource "' + resBefore + '" -> "' + b.side('ROW_A', true) + '"');
  await sleep(4000); b = await fresh();
  check('hud.cooldown_cleared', b.count('SLOT_CD') === 0, 'after the cooldown: SLOT_CD ' + b.count('SLOT_CD'));
  // sneak + right click with the class weapon opens nothing (it opened the skill-tree map mid-fight until 2026-10-15)
  bot.setControlState('sneak', true); await sleep(400);
  bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(1500);
  const win = bot.currentWindow; const wt = win ? JSON.stringify(win.title) : '';
  check('hud.shift_right_click_opens_nothing', !win && !bot.vehicle,
    'window ' + (win ? win.slots.length + ' slots, title ' + wt.slice(0, 80) : 'none') + ', seated ' + !!bot.vehicle);
  if (win) bot.closeWindow(win);
  bot.setControlState('sneak', false); await sleep(2500); // the click was a combo R: let it lapse
  // target: a named animal in front of the bot (look → frame), attack, then another target, then gone
  const p = bot.entity.position;
  cmd('minecraft:kill @e[tag=hudtest]'); await sleep(300);   // vanilla kill: plain "kill" is EssentialsX's player command
  cmd(`summon minecraft:cow ${(p.x + 3).toFixed(1)} ${p.y.toFixed(1)} ${p.z.toFixed(1)} {NoAI:1b,CustomName:"Туршилтын Үхэр",Tags:["hudtest"]}`);
  cmd(`summon minecraft:sheep ${(p.x - 3).toFixed(1)} ${p.y.toFixed(1)} ${p.z.toFixed(1)} {NoAI:1b,CustomName:"Хонь Хоёр",Tags:["hudtest"]}`);
  await sleep(1500);
  const find = (n) => Object.values(bot.entities).find(e => e.name === n && e.position.distanceTo(bot.entity.position) < 6);
  const cow = find('cow'), sheep = find('sheep');
  if (cow) { await bot.lookAt(cow.position.offset(0, 1, 0), true); await sleep(1200); }
  let t = targetFrame();
  check('hud.target_on_look', !!t && t.text('LINE').includes('ҮХЭР'), 'boss-bar target "' + (t ? t.text('LINE') : 'none') + '" ' + (t ? t.text('TARGET') : ''));
  const fullFill = t ? t.fill('TGT') : 0;
  cmd(`execute at ${name} run damage @e[tag=hudtest,type=cow,sort=nearest,limit=1] 4 minecraft:generic`); await sleep(300);
  const mid = targetFrame();
  const chipNow = mid ? mid.fill('TGT_CHIP') : 0;
  await sleep(900);
  t = targetFrame();
  const tHp = t ? t.text('TARGET') : '';
  check('hud.target_hp', !!t && tHp === '6/10' && t.fill('TGT') === Math.round(0.6 * 180) && fullFill === 180,
    'after 4 damage: target HP "' + tHp + '", fill ' + fullFill + ' -> ' + (t ? t.fill('TGT') : 0) + 'px');
  check('hud.target_damage_chip', chipNow > 0 && (t ? t.fill('TGT_CHIP') : 1) === 0,
    'pale chip 0.3 s after the hit ' + chipNow + 'px, 1.2 s after ' + (t ? t.fill('TGT_CHIP') : '?') + 'px');
  if (sheep) { await bot.lookAt(sheep.position.offset(0, 1, 0), true); await sleep(1200); }
  t = targetFrame();
  check('hud.target_changes', !!t && t.text('LINE').includes('ХОНЬ'), 'after looking at the other: "' + (t ? t.text('LINE') : 'none') + '"');
  const before = targetFrame();
  cmd('minecraft:kill @e[tag=hudtest]'); await sleep(1000);   // well inside the 3 s look hold: only the death can clear it
  t = targetFrame();
  check('hud.target_cleared', !!before && !t, 'frame before the kill: ' + (before ? '"' + before.text('LINE') + '"' : 'none') + '; 1 s after: ' + (t ? 'still "' + t.text('LINE') + '"' : 'gone'));
  b = await fresh();
  console.log('LEVEL ' + b.text('LEVEL'));
  fs.writeFileSync('/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/hud_' + name + '.json', JSON.stringify(results, null, 1));
  bot.quit();
});
bot.on('kicked', (r) => console.log('KICKED ' + JSON.stringify(r)));
bot.on('end', () => process.exit(results.some(r => !r.ok) ? 2 : 0));
setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 90000);
