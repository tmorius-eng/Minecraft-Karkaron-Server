// The gate markets as content (content/shops.json, content/npcs.json; docs/NPC_DIALOGUE.md) on the dev server:
//   node shopbot.js <srvctl.sh> [port]
// Shift + right-click on the east gate merchant opens shop.grain (the Дэлгүүр page names the shop), its first card is
// «Боов x8» and a click pays 20 coins in the database for eight loaves; a plain right-click talks first (a Paper
// dialog the bot cannot see, so no window opens); /shop is the general shop with its two pages; the north gate's
// saddle refuses a level-1 player and keeps the coins; the west gate sells sheep. PostgreSQL is read through psql.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const psql = (q) => execFileSync('runuser', ['-u', 'postgres', '--', 'psql', '-d', process.env.SULD_DB || 'suldit', '-At', '-c', q], { cwd: '/tmp' }).toString().trim();
const coins = (n) => Number(psql(`select currency from suld_profiles where name='${n}'`) || 0);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const GL = require('./glyphs');
const has = (w, g) => !!w && (JSON.stringify(w.title).includes(g) || JSON.stringify(w.title).includes('\\u' + g.charCodeAt(0).toString(16)));
const titleText = (w) => w ? JSON.stringify(w.title) : 'no window';
const slotText = (w, i) => w && w.slots[i] ? JSON.stringify(w.slots[i].customName || '') + ' ' + JSON.stringify(w.slots[i].customLore || '') : '';
// city-local points (assets/world/kharkhorum/points.json) over the dev city's origin (-16, -64)
const ORIGIN = { x: -16, z: -64 };
const GATES = { east: { x: 156, z: -8 }, north: { x: 96, z: -140 }, west: { x: -157, z: -8 } };

function join(name) {
  return new Promise((res) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    b.chatLog = [];
    b.on('messagestr', m => b.chatLog.push(m));
    b.on('error', () => {});
    b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
    b.once('spawn', async () => {
      await sleep(3500);
      for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
      if (b.currentWindow) {   // the class choice
        await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600);
        await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200);
      }
      res(b);
    });
  });
}

/** Put the bot "in combat" for 8 s (MenuSky.COMBAT_MS): the hub pages then open as the chest window the bot can read. */
async function arm(b) {
  cmd(`execute as ${b.username} at @s run damage @s 0.5 minecraft:generic by @e[type=mannequin,limit=1,sort=nearest]`);
  await sleep(400);
}

/** Stand the bot at a gate merchant's point (spreadplayers finds the ground) and return the mannequin beside it. */
async function atGate(b, gate) {
  const x = ORIGIN.x + GATES[gate].x, z = ORIGIN.z + GATES[gate].z;
  if (b.currentWindow) { b.closeWindow(b.currentWindow); await sleep(400); }
  cmd(`spreadplayers ${x + 1} ${z + 1} 0 2 false ${b.username}`);
  await sleep(4000); // the chunk loads, the NPC of the point spawns on that load (NpcService.onChunkLoad)
  const target = { x: x + 0.5, z: z + 0.5 };
  const near = Object.values(b.entities).filter(e => e.name === 'mannequin')
    .map(e => ({ e, d: Math.hypot(e.position.x - target.x, e.position.z - target.z) }))
    .sort((a, c) => a.d - c.d);
  if (process.env.DEBUG) console.log(gate, 'bot at', b.entity.position.floored(), 'mannequins', near.slice(0, 3).map(n => n.e.position.floored() + '@' + n.d.toFixed(1)).join(' '));
  return near.length && near[0].d < 3.5 ? near[0].e : null;
}

async function clickNpc(b, npc, sneak) {
  if (b.currentWindow) { b.closeWindow(b.currentWindow); await sleep(400); }
  const k = b.chatLog.length;
  await Promise.race([b.lookAt(npc.position.offset(0, 1.6, 0)), sleep(800)]); // lookAt may never settle
  b.setControlState('sneak', sneak); await sleep(400);
  await arm(b); // a window, if the action ran, is then a chest window the bot sees; the plain click shows none (a dialog)
  if (process.env.DEBUG) console.log('click', npc.position.floored(), 'sneak', sneak);
  const p = new Promise((res) => { const t = setTimeout(() => res(null), 3000); b.once('windowOpen', (win) => { clearTimeout(t); res(win); }); });
  // not bot.activateEntity: it awaits a lookAt that may never settle and sends `sneaking: false` whatever the control state
  b._client.write('use_entity', { target: npc.id, mouse: 0, hand: 0, sneaking: sneak });
  const w = await p;
  b.setControlState('sneak', false);
  await sleep(700);
  return { w, chat: b.chatLog.slice(k) };
}

async function open(b, command) {
  if (b.currentWindow) { b.closeWindow(b.currentWindow); await sleep(500); }
  b.chat(command);
  const w = await new Promise((res) => { const t = setTimeout(() => res(null), 5000); b.once('windowOpen', (win) => { clearTimeout(t); res(win); }); });
  await sleep(800);
  return w;
}

async function click(b, slot) {
  const k = b.chatLog.length;
  const p = new Promise((res) => { const t = setTimeout(() => res(null), 4000); b.once('windowOpen', (win) => { clearTimeout(t); res(win); }); });
  await b.clickWindow(slot, 0, 0).catch(() => {});
  const w = await p;
  await sleep(900);
  return { w, chat: b.chatLog.slice(k) };
}

const count = (b, item) => b.inventory.items().filter(i => i.name === item).reduce((s, i) => s + i.count, 0);

(async () => {
  const name = 'S' + Math.floor(Math.random() * 90000 + 10000);
  const b = await join(name);
  cmd(`suld coins ${name} 1000`); await sleep(2500);
  const before = coins(name);

  // 1. the east gate merchant: Shift + click goes straight to shop.grain
  let npc = await atGate(b, 'east');
  let r = npc ? await clickNpc(b, npc, true) : { w: null, chat: [] };
  check(npc && has(r.w, GL.PAGE_SHOP) && titleText(r.w).includes('будаа'), 'Shift+click on the east gate merchant opens the grain market (PAGE_SHOP, the shop\'s name in the row)', npc ? titleText(r.w).slice(0, 160) : 'no mannequin at the east gate point');
  // 2. its first card is the bread
  check(slotText(r.w, 9).includes('Боов') && slotText(r.w, 9).includes('20'), 'card 0 is «Боов x8» for 20 coins', slotText(r.w, 9).slice(0, 120));
  // 3. buying it: the chat line and eight loaves
  const bread0 = count(b, 'bread');
  let c = await click(b, 9);
  await sleep(1500);
  check(c.chat.some(m => m.includes('Авлаа') && m.includes('Боов')) && count(b, 'bread') - bread0 === 8, 'a click buys eight loaves («Авлаа: Боов x8»)', c.chat.join(' | ').slice(0, 120) + ' bread +' + (count(b, 'bread') - bread0));
  // 4. the coins are gone in the database (coins first, goods second: PlayerDataSaves)
  await sleep(1500);
  const after = coins(name);
  check(before - after === 20, 'the database shows 20 coins fewer', before + ' -> ' + after);
  // 5. a plain click talks first: a dialog the bot cannot see, so no window and no error line
  r = await clickNpc(b, npc, false);
  check(r.w === null && !r.chat.some(m => /алга|боломжгүй/.test(m)), 'a plain click opens the conversation (no window, no error)', 'chat ' + JSON.stringify(r.chat).slice(0, 100));

  // 6. /shop is the general shop and keeps its two pages (in combat: the chest window)
  if (b.currentWindow) { b.closeWindow(b.currentWindow); await sleep(400); }
  await arm(b);
  let w = await open(b, '/shop');
  const cards = w ? [9, 14, 18, 23, 27, 32, 36, 41].filter(i => w.slots[i]).length : 0;
  const two = w && w.slots[53] ? await click(b, 53) : { w: null };
  check(has(w, GL.PAGE_SHOP) && cards === 8 && has(two.w, GL.PAGE_SHOP2), '/shop opens the general shop with eight cards and a second page (PAGE_SHOP2)', 'cards ' + cards + ' page2 ' + (two.w ? 'yes' : 'no'));

  // 7. the north gate's saddle (minLevel 10) refuses a level-1 player and keeps the coins
  npc = await atGate(b, 'north');
  r = npc ? await clickNpc(b, npc, true) : { w: null, chat: [] };
  const mid = coins(name);
  const horseTitle = titleText(r.w), saddle = slotText(r.w, 9); // read before the click: the window is rebuilt after it
  c = r.w ? await click(b, 9) : { chat: [] };
  await sleep(1500);
  check(npc && horseTitle.includes('морь') && saddle.includes('Эмээл') && c.chat.some(m => m.includes('10-р түвшнээс')) && coins(name) === mid,
    'the north gate\'s saddle card refuses a level-1 player and the coins stay', (npc ? saddle.slice(0, 80) : 'no mannequin') + ' | ' + c.chat.join(' | ').slice(0, 100) + ' | ' + mid + '=' + coins(name));

  // 8. the west gate sells sheep
  npc = await atGate(b, 'west');
  r = npc ? await clickNpc(b, npc, true) : { w: null, chat: [] };
  check(npc && has(r.w, GL.PAGE_SHOP) && titleText(r.w).includes('хонь') && slotText(r.w, 9).includes('хонины мах'), 'the west gate merchant sells the sheep market', npc ? slotText(r.w, 9).slice(0, 80) : 'no mannequin at the west gate point');

  if (b.currentWindow) b.closeWindow(b.currentWindow);
  b.quit();
  await sleep(800);
  const pass = results.filter(Boolean).length;
  console.log(`${pass === results.length ? 'PASS' : 'FAIL'} shopbot: ${pass}/${results.length}`);
  process.exit(pass === results.length ? 0 : 1);
})();
setTimeout(() => { console.log('FAIL shopbot: timeout'); process.exit(1); }, 180000);
