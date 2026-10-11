// Client-QA preparation: drives the real skill map over the protocol and checks every slot against an independent
// re-implementation of the rules (JS, written from the data files, not from the Java code).
const mineflayer = require('mineflayer'); const fs = require('fs'); const http = require('http'); const crypto = require('crypto');
const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const cls = process.argv[3] || 'baatar';
const SLOT = { baatar: 10, mergen: 11, boo: 12, darkhan: 13, khulegchin: 14 }[cls];
const DATA = '/home/user/Minecraft-Karkaron-Server/suld-api/src/main/resources/skills/';
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const name = 'Mb' + cls.slice(0, 3) + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const results = []; let failures = 0;
const ok = (id, pass, detail) => { results.push({ id, pass, detail }); if (!pass) { failures++; console.log('FAIL ' + id + ' :: ' + detail); } };
const bars = []; for (const ev of ['action_bar', 'set_action_bar_text']) bot._client.on(ev, (pk) => { const s = flat(pk.text); if (bars[bars.length - 1] !== s) bars.push(s); });
const chat = []; bot.on('messagestr', (m) => { if (m.trim()) chat.push(m); });
let pack = null; bot.on('resourcePack', (...args) => { const hash = (args.map(String).join(' ').match(/[0-9a-f]{40}/i) || [])[0]; pack = { url: args[0], hash, args: args.map(String) }; try { bot.acceptResourcePack(); } catch (e) {} });
async function con(c, wait = 1700) { const n = fs.readFileSync(LOG, 'utf8').split('\n').length - 1; cmd(c); await sleep(wait); return fs.readFileSync(LOG, 'utf8').split('\n').slice(n).map(l => l.replace(/^\[[\d:]+\] \[[^\]]+\]: /, '')).join(' | '); }
const title = () => { const w = bot.currentWindow; if (!w) return 'none'; return [...JSON.stringify(w.title).matchAll(/"text":\{"type":"string","value":"([^"]*)"/g)].map(x => x[1]).join('|'); };
const itemName = (s) => { try { return s.customName ? flat(s.customName) : s.displayName; } catch (e) { return s.displayName; } };
const nbtText = (c) => { const v = c.value || {}; let s = ''; if (v.text) s += v.text.value; if (v.extra && v.extra.value && Array.isArray(v.extra.value.value)) v.extra.value.value.forEach(x => { s += nbtText({ value: x }); }); return s; };
const lore = (s) => { const c = s && s.components && s.components.find(c => c.type === 'lore'); if (!c || !Array.isArray(c.data)) return []; return c.data.map(e => nbtText(e)); };
const INTRINSIC_GLINT = new Set(['experience_bottle', 'nether_star', 'enchanted_golden_apple', 'enchanted_book', 'end_crystal']);
// what the client draws: the explicit override when there is one, otherwise the item's own glint
const hasGlint = (s) => { if (!s) return false; const c = s.components && s.components.find(c => c.type === 'enchantment_glint_override'); if (c) { const v = c.data; return typeof v === 'object' && v !== null ? !!v.value : !!v; } return INTRINSIC_GLINT.has(s.name); };
const modelOf = (s) => { const c = s && s.components && s.components.find(c => c.type === 'item_model'); return c ? String(c.data) : null; };
const slotAt = (i) => bot.currentWindow.slots[i];
const click = async (i, mode = 0, btn = 0) => { await bot.clickWindow(i, btn, mode); await sleep(700); };

// ---------------------------------------------------------------- the rules, independently written
function loadTree(c) {
  const own = JSON.parse(fs.readFileSync(DATA + c + '.json', 'utf8')); const uni = JSON.parse(fs.readFileSync(DATA + 'universal.json', 'utf8'));
  const nodes = own.nodes.concat(uni.nodes); const byId = {}; nodes.forEach((n, i) => { n.index = i; n.maxRank = n.maxRank || 1; byId[n.id] = n; });
  const edges = own.edges.concat(uni.edges || []); const nb = {}; nodes.forEach(n => nb[n.id] = new Set());
  edges.forEach(([a, b]) => { nb[a].add(b); nb[b].add(a); });
  const excl = {}; nodes.forEach(n => excl[n.id] = new Set()); (own.exclusive || []).forEach(([a, b]) => { excl[a].add(b); excl[b].add(a); });
  return { nodes, byId, edges, nb, excl, maxX: Math.max(...nodes.map(n => n.x)), maxY: Math.max(...nodes.map(n => n.y)) };
}
function spellUnlockLevel(n) { let need = n.level; for (const e of n.effects) if (e.type === 'mod') need = Math.max(need, { 1: 1, 2: 10, 3: 20, 4: 35 }[SLOTOF[e.spell]]); return need; }
const SPELLSLOT = ['TENGER_TSAVCHILT', 'DAINY_KHASHGIRAAN', 'DOVTLOKH_USRELT', 'KHAAN_KHAMGAALALT', 'CHONYN_NUD', 'OLON_SUM', 'UKHRAKH_USRELT', 'TENGERIIN_SUM', 'SUNSNII_ZALBIRAL', 'ONGONY_DUUDLAGA', 'KHENGERGIIN_DUU', 'TENGERIIN_KHAALGA', 'GALYN_DAVTALT', 'GAN_BAMBAI', 'KHAILSAN_TUMUR', 'DARKHANY_DARANGUI', 'KHURDAN_DOVTOLGOO', 'SALKHINY_KHURD', 'ZHADNY_SHIDELT', 'KHULGIIN_DAIRALT'];
const SLOTOF = {}; SPELLSLOT.forEach((s, i) => SLOTOF[s] = (i % 4) + 1);
function stateOf(T, ranks, n, level, avail) {
  const rank = n.id === 'root' ? 1 : (ranks[n.id] || 0);
  if (n.id === 'root' || rank >= n.maxRank) return 'MAXED'; if (rank > 0) return 'UNLOCKED';
  const reachable = [...T.nb[n.id]].some(o => o === 'root' || (ranks[o] || 0) > 0);
  if (!reachable) return 'LOCKED';
  if ([...T.excl[n.id]].some(o => (ranks[o] || 0) > 0)) return 'EXCLUDED';
  if ((n.requires || []).some(r => (ranks[r.node] || 0) < r.rank)) return 'PREREQUISITE_MISSING';
  if (level < spellUnlockLevel(n)) return 'LEVEL_LOCKED'; if (avail < n.cost) return 'NEEDS_POINTS'; return 'AVAILABLE';
}
const STATE_TEXT = { MAXED: ['Дээд түвшинд хүрсэн', 'Эхлэл'], UNLOCKED: ['Нээгдсэн'], AVAILABLE: ['Нээж болно'], NEEDS_POINTS: ['Оноо хүрэлцэхгүй'], LEVEL_LOCKED: ['Түвшин хүрэхгүй'], PREREQUISITE_MISSING: ['Шаардлага биелээгүй'], EXCLUDED: ['Өөр сонголт хийсэн'], LOCKED: ['Хөрш нодыг эхлээд нээ'] };
function visible(T, ranks, n) { if (!n.hidden) return true; const r = ranks[n.id] || 0; return r > 0 || [...T.nb[n.id]].some(o => o === 'root' || (ranks[o] || 0) > 0); }
function parseInspect(text) {
  const m = text.match(/нод: ([^|]*)/); const ranks = {}; if (m) m[1].split(';').forEach(p => { const [k, v] = p.trim().split('='); if (k && v) ranks[k] = +v; });
  const lv = +(text.match(/түвшин (\d+)/) || [])[1]; const av = +(text.match(/оноо: (\d+) чөлөөтэй/) || [])[1]; return { ranks, level: lv, avail: av };
}
const MATERIAL_OK = (icon) => icon.toLowerCase();

bot.once('spawn', async () => {
  try {
    await sleep(5000);
    if (bot.currentWindow) await bot.clickWindow(SLOT, 0, 0).catch(() => {}); await sleep(1500);
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    const T = loadTree(cls);
    // ------------------------------------------------------------ 1. fresh player experience
    await con(`skillsadmin qakit ${name} fresh`);
    bot.chat('/skills'); await sleep(1800);
    ok('1.open', title().includes('Чадварын Газрын Зураг') && title().includes({ baatar: 'Баатар', mergen: 'Мэргэн', boo: 'Бөө', darkhan: 'Дархан', khulegchin: 'Хүлэгчин' }[cls]), 'title=' + title());
    ok('7.points-fresh', (itemName(slotAt(52)) || '').includes('Чадварын оноо: 0'), 'panel=' + itemName(slotAt(52)));
    bot.closeWindow(bot.currentWindow); await sleep(500);
    // ------------------------------------------------------------ states scenario
    const needsCostTwo = T.nodes.some(n => stateOf(T, { l1: 2, m1: 3, l2b: 2, l2: 1, l3: 1, l4: 1, l5b: 1, l6: 1, l7: 1 }, n, 14, 1) === 'NEEDS_POINTS');
    const zeroFree = !needsCostTwo; // classes without a cost-2 node there use the no-free-point fixture and gain the point later
    const kit = await con(`skillsadmin qakit ${name} ${zeroFree ? 'states0' : 'states'}`);
    const insp = parseInspect(await con(`skillsadmin inspect ${name}`));
    insp.level = +(kit.match(/level (\d+)/) || [])[1] || insp.level;
    ok('kit.applied', insp.level === 14 && insp.avail === (zeroFree ? 0 : 1) && Object.keys(insp.ranks).length >= 9, JSON.stringify(insp));
    const { ranks, level } = insp; let avail = insp.avail;
    bot.chat('/skills'); await sleep(1800);
    ok('7.points-states', (itemName(slotAt(52)) || '').includes('Чадварын оноо: ' + avail), 'panel=' + itemName(slotAt(52)) + ' expected ' + avail);
    // ------------------------------------------------------------ 2/3/5. states, connectors, tooltips in the detailed view at several pans
    const seenStates = new Set(); let nodesChecked = 0, connectorsChecked = 0, tooltipsChecked = 0;
    async function verifyView(ox, oy, label) {
      const w = bot.currentWindow;
      const slotOf = (n) => { const cx = n.x - ox, cy = n.y - oy; return (cx < 0 || cx > 4 || cy < 0 || cy > 2) ? -1 : 2 * cy * 9 + 2 * cx; };
      const expectedNodeSlots = new Set();
      for (const n of T.nodes) {
        const s = slotOf(n); if (s < 0) continue; const vis = visible(T, ranks, n);
        const it = slotAt(s);
        if (!vis) { ok(`2.${label}.hidden.${n.id}`, !it, 'a hidden node must not be drawn, found ' + (it && itemName(it))); continue; }
        expectedNodeSlots.add(s); nodesChecked++;
        const st = stateOf(T, ranks, n, level, avail); seenStates.add(st);
        if (!it) { ok(`2.${label}.${n.id}`, false, 'missing item'); continue; }
        const nm = itemName(it); const lr = lore(it); const txt = lr.join('\n');
        const wantMat = st === 'LOCKED' ? 'gray_stained_glass_pane' : st === 'EXCLUDED' ? 'red_stained_glass_pane' : MATERIAL_OK(n.icon);
        const matOk = it.name === wantMat || (st !== 'LOCKED' && st !== 'EXCLUDED' && it.name === 'paper');
        const nameOk = nm.includes(n.name);
        const glintOk = hasGlint(it) === (st === 'UNLOCKED' || st === 'MAXED');
        const countOk = (ranks[n.id] || 0) > 1 ? it.count === ranks[n.id] : it.count === 1;
        const stateOk = (STATE_TEXT[st] || []).some(t => txt.includes(t));
        ok(`2.${label}.${n.id}.${st}`, matOk && nameOk && glintOk && countOk && stateOk, `material ${it.name} (want ${wantMat}) name '${nm}' glint ${hasGlint(it)} count ${it.count} stateText ${stateOk} :: ${lr.slice(-3).join(' / ')}`);
        // tooltip contents (item 5)
        if (n.id !== 'root') {
          tooltipsChecked++;
          const costOk = txt.includes('Үнэ:') && txt.includes(String(n.cost));
          const lvlOk = n.level <= 1 || txt.includes('Шаардлагатай түвшин:');
          const reqOk = (n.requires || []).every(r => txt.includes(T.byId[r.node].name));
          const exOk = T.excl[n.id].size === 0 || (txt.includes('Хамт авч болохгүй') && [...T.excl[n.id]].every(o => txt.includes(T.byId[o].name)));
          const rankOk = n.maxRank <= 1 || txt.includes('Түвшин: ') || lr.some(l => l.includes('Түвшин') && l.includes('/' + n.maxRank));
          const hintOk = txt.includes('Зүүн товш');
          ok(`5.${label}.${n.id}.tooltip`, costOk && lvlOk && reqOk && exOk && rankOk && hintOk, `cost ${costOk} level ${lvlOk} requires ${reqOk} exclusive ${exOk} rank ${rankOk} hint ${hintOk}`);
        }
      }
      // connectors
      const place = (a, b) => {
        const dx = Math.abs(a.x - b.x), dy = Math.abs(a.y - b.y); const left = a.x <= b.x ? a : b, top = a.y <= b.y ? a : b; let row, col, kind;
        if (dy === 0) { row = 2 * (a.y - oy); col = 2 * (left.x - ox) + 1; kind = 'h'; } else if (dx === 0) { row = 2 * (top.y - oy) + 1; col = 2 * (a.x - ox); kind = 'v'; }
        else { row = 2 * (top.y - oy) + 1; col = 2 * (left.x - ox) + 1; kind = left.y < (left === a ? b : a).y ? 'd1' : 'd2'; }
        return row < 0 || row > 4 || col < 0 || col > 8 ? null : { slot: row * 9 + col, kind };
      };
      const reachable = (n) => (ranks[n.id] || 0) > 0 || [...T.nb[n.id]].some(o => o === 'root' || (ranks[o] || 0) > 0) || n.id === 'root';
      const learned = (n) => n.id === 'root' || (ranks[n.id] || 0) > 0;
      const rivalLearned = (n) => [...T.excl[n.id]].some(o => (ranks[o] || 0) > 0);
      const expected = new Map();
      for (const x of T.nodes) for (const yid of T.nb[x.id]) {
        const y = T.byId[yid]; if (y.index < x.index) continue; if (!visible(T, ranks, x) || !visible(T, ranks, y)) continue;
        const pl = place(x, y); if (!pl) continue; const xu = learned(x), yu = learned(y);
        const color = xu && yu ? 'on' : ((xu && reachable(y) && !rivalLearned(y)) || (yu && reachable(x) && !rivalLearned(x))) ? 'next' : 'off';
        expected.set(pl.slot, `suld:tree_${pl.kind}_${color}`);
      }
      for (const x of T.nodes) for (const eid of T.excl[x.id]) {
        const y = T.byId[eid]; if (y.index < x.index) continue; if (Math.abs(x.x - y.x) > 1 || Math.abs(x.y - y.y) > 1 || !visible(T, ranks, x) || !visible(T, ranks, y)) continue;
        const pl = place(x, y); if (pl && !expected.has(pl.slot)) expected.set(pl.slot, `suld:tree_${pl.kind}_red`);
      }
      for (const [s, m] of expected) { connectorsChecked++; const it = slotAt(s); ok(`3.${label}.slot${s}`, !!it && modelOf(it) === m, `expected ${m}, got ${it ? modelOf(it) : 'empty'}`); }
      for (let s = 0; s < 45; s++) if (!expected.has(s) && !expectedNodeSlots.has(s)) { const it = slotAt(s); if (it) ok(`3.${label}.stray${s}`, false, 'unexpected item ' + it.name + ' ' + itemName(it)); }
    }
    await verifyView(0, 0, 'v00');
    // ------------------------------------------------------------ 4. pan and zoom
    await click(48); await verifyView(1, 0, 'pan-right');
    await click(48); await verifyView(1, 0, 'pan-right-clamped');
    await click(47); await click(47); await verifyView(1, 2, 'pan-down2');
    await click(45); await verifyView(0, 2, 'pan-left-down2'); await click(48); await verifyView(1, 2, 'pan-back-right');
    for (let i = 0; i < 6; i++) await click(47); await verifyView(1, 6, 'pan-down-end');
    await click(46); await click(46); await click(46); await click(46); await click(46); await click(46); await click(46); await click(46);
    await click(45); await verifyView(0, 0, 'pan-home');
    if (zeroFree) {
      const np = T.byId.r1; bars.length = 0; await click(2 * np.y * 9 + 2 * np.x); ok('6.needs-points-click', bars.join(' ').includes('оноо хүрэлцэхгүй'), bars.slice(-4).join(' / '));
      await con(`skillsadmin grantpoints ${name} 1`); avail = 1; bot.closeWindow(bot.currentWindow); await sleep(400); bot.chat('/skills'); await sleep(1800);
      await verifyView(0, 0, 'after-grant');
    }
    ok('4.states-seen', ['UNLOCKED', 'MAXED', 'AVAILABLE', 'NEEDS_POINTS', 'LEVEL_LOCKED', 'PREREQUISITE_MISSING', 'EXCLUDED', 'LOCKED'].every(s => seenStates.has(s)), 'seen ' + [...seenStates].join(','));
    // zoom 2: relative positions must match the data file, every visible node exactly one slot
    await click(49);
    const cells = []; for (let s = 0; s < 45; s++) { const it = slotAt(s); if (!it) continue; const n = T.nodes.find(n => itemName(it).includes(n.name)); if (n) cells.push({ n, col: s % 9, row: Math.floor(s / 9) }); }
    const o = cells[0]; let zoomOk = cells.length > 0; for (const c of cells) if (c.col - o.col !== c.n.x - o.n.x || c.row - o.row !== c.n.y - o.n.y) zoomOk = false;
    ok('4.zoom-overview', zoomOk && cells.length >= 20, `${cells.length} nodes placed one per slot, relative geometry matches: ${zoomOk}`);
    let conn = 0; for (let s = 0; s < 45; s++) if (slotAt(s) && modelOf(slotAt(s)) && modelOf(slotAt(s)).includes('tree_')) conn++;
    ok('4.zoom-no-connectors', conn === 0, conn + ' connector items in the overview');
    await click(49);
    // filter
    await click(51); const f1 = itemName(slotAt(51)); ok('4.filter', f1.includes('Хамгаалалт'), f1);
    const dimmed = []; for (const n of T.nodes) { const cx = n.x, cy = n.y; if (cx > 4 || cy > 2) continue; const it = slotAt(2 * cy * 9 + 2 * cx); if (it && n.category !== 'DEFENSE' && n.id !== 'root' && visible(T, ranks, n)) dimmed.push(it.name === 'black_stained_glass_pane'); }
    ok('4.filter-dims-others', dimmed.length > 0 && dimmed.every(Boolean), `${dimmed.filter(Boolean).length}/${dimmed.length} other-branch nodes dimmed`);
    await click(51, 0, 1); await click(51, 0, 1); await click(51, 1, 0); ok('4.filter-cleared', itemName(slotAt(51)).includes('Бүгд'), itemName(slotAt(51)));
    // ------------------------------------------------------------ 6. interactions (view is back at 0,0: zoom remembers its place)
    const msgs = () => bars.slice(-4).join(' / ') + ' ' + chat.slice(-2).join(' / ');
    const posOf = (n) => 2 * n.y * 9 + 2 * n.x; // valid on the first screen (x 0..4, y 0..2)
    await verifyView(0, 0, 'after-zoom-roundtrip');
    bars.length = 0; await click(posOf(T.byId.m2)); ok('6.excluded-click', bars.join(' ').includes('хамт байж болохгүй'), msgs());
    if (!zeroFree) {
    await click(47); // pan down one row: l3b (0,3) is now on the third node row
    bars.length = 0; await click(2 * (T.byId.l3b.y - 1) * 9 + 2 * T.byId.l3b.x); ok('6.needs-points-click', bars.join(' ').includes('оноо хүрэлцэхгүй'), msgs());
    await click(46);
    }
    bars.length = 0; await click(posOf(T.byId.r1)); await sleep(500);
    const afterUnlock = parseInspect(await con(`skillsadmin inspect ${name}`));
    ok('6.unlock', afterUnlock.ranks.r1 === 1 && afterUnlock.avail === 0, JSON.stringify(afterUnlock.ranks) + ' avail ' + afterUnlock.avail);
    ok('6.unlock-ui', hasGlint(slotAt(posOf(T.byId.r1))) && (itemName(slotAt(52)) || '').includes('Чадварын оноо: 0'), 'glint ' + hasGlint(slotAt(posOf(T.byId.r1))) + ' panel ' + itemName(slotAt(52)));
    await click(posOf(T.byId.r1), 0, 1); const afterRefund = parseInspect(await con(`skillsadmin inspect ${name}`));
    ok('6.refund', !afterRefund.ranks.r1 && afterRefund.avail === 1, JSON.stringify(afterRefund.ranks));
    // rank up: l1 is 2/3, one more point makes it 3/3, a further click is refused
    const l1s = posOf(T.byId.l1);
    await click(l1s); const r3 = parseInspect(await con(`skillsadmin inspect ${name}`));
    ok('6.rank-up', r3.ranks.l1 === 3 && itemName(slotAt(l1s)).includes(T.byId.l1.name) && slotAt(l1s).count === 3, 'l1=' + r3.ranks.l1 + ' count ' + slotAt(l1s).count);
    bars.length = 0; await click(l1s); ok('6.maxed-click', bars.join(' ').includes('дээд түвшин'), msgs());
    await click(l1s, 0, 1); ok('6.rank-down', slotAt(l1s).count === 2, 'count ' + slotAt(l1s).count);
    chat.length = 0; await click(posOf(T.byId.r1), 1, 0); ok('6.shift-details', chat.some(c => c.includes(T.byId.r1.name)), chat.slice(-3).join(' | '));
    // ------------------------------------------------------------ 8. builds via the GUI
    await click(53); ok('8.respec-menu', title().includes('Дахин тохируулах'), 'title ' + title());
    await click(18); await sleep(500); ok('8.save-build', chat.some(c => c.includes('Амжилттай: build1')), chat.slice(-2).join(' | '));
    ok('8.build-listed', itemName(slotAt(18)).includes('build1'), itemName(slotAt(18)));
    await click(10); ok('8.confirm-screen', title().includes('Баталгаажуулах'), 'title ' + title());
    await click(15); // cancel
    await click(11); // confirm reset? slot 11 is the green confirm on the confirm screen; from respec menu slot 11 is empty
    bot.closeWindow(bot.currentWindow); await sleep(400);
    // ------------------------------------------------------------ 9. class-specific tree
    bot.chat('/skills'); await sleep(1500);
    const others = ['baatar', 'mergen', 'boo', 'darkhan', 'khulegchin'].filter(c => c !== cls).map(loadTree);
    const own = new Set(T.nodes.filter(n => !(n.tags || []).includes('universal')).map(n => n.name));
    let foreign = 0, mine = 0; const foreignNames = []; for (let s = 0; s < 45; s++) { const it = slotAt(s); if (!it) continue; const nm = itemName(it); for (const n of T.nodes) if (nm.includes(n.name) && own.has(n.name)) mine++; for (const T2 of others) for (const n of T2.nodes) if (!(n.tags || []).includes('universal') && !own.has(n.name) && ![...own].some(o => o.includes(n.name)) && nm.includes(n.name)) { foreign++; foreignNames.push(n.name + ' in ' + nm); } }
    ok('9.class-tree', mine >= 5 && foreign === 0, `${mine} nodes of ${cls}'s own tree on screen, ${foreign} nodes of other classes ${foreignNames.join('; ')}`);
    ok('9.root-name', itemName(slotAt(4)).includes(T.byId.root.name), 'root slot: ' + itemName(slotAt(4)));
    bot.closeWindow(bot.currentWindow);
    // ------------------------------------------------------------ 10. resource pack delivery
    if (cls === 'baatar') {
      ok('10.pack-offered', !!pack, JSON.stringify(pack));
      if (pack) {
        const buf = await new Promise((res, rej) => { http.get(pack.url, (r) => { const ch = []; r.on('data', d => ch.push(d)); r.on('end', () => res(Buffer.concat(ch))); }).on('error', rej); });
        const sha1 = crypto.createHash('sha1').update(buf).digest('hex'); fs.writeFileSync('/tmp/claude-0/pack_dl.zip', buf);
        ok('10.pack-hash', sha1 === pack.hash, `args ${JSON.stringify(pack.args)} offered ${pack.hash} downloaded ${sha1} (${buf.length} bytes)`);
        const list = execFileSync('unzip', ['-Z1', '/tmp/claude-0/pack_dl.zip']).toString().split('\n');
        const need = []; for (const k of ['h', 'v', 'd1', 'd2']) for (const c of ['on', 'next', 'off', 'red']) need.push(`assets/suld/items/tree_${k}_${c}.json`, `assets/suld/models/item/tree_${k}_${c}.json`, `assets/suld/textures/item/tree_${k}_${c}.png`);
        need.push('assets/suld/items/orb_oblivion.json', 'assets/suld/textures/item/orb_oblivion.png', 'assets/suld/textures/font/gui/skillmap.png', 'assets/suld/font/ui.json', 'pack.mcmeta');
        const missing = need.filter(f => !list.includes(f)); ok('10.pack-contents', missing.length === 0, 'missing: ' + missing.join(', '));
        const forbidden = list.filter(f => /\.(glb|fbx|obj|blend|psd)$/i.test(f)); ok('10.pack-no-source-meshes', forbidden.length === 0, forbidden.join(','));
      }
    }
    console.log(`SUMMARY class=${cls} checks=${results.length} failures=${failures} nodes=${nodesChecked} connectors=${connectorsChecked} tooltips=${tooltipsChecked}`);
    fs.writeFileSync(`/tmp/claude-0/mapbot_${cls}.json`, JSON.stringify({ cls, results }, null, 1));
  } catch (e) { console.log('ERROR ' + e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0));
setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 280000);
