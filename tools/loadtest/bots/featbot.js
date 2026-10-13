// Functional checks of tonight's systems on the dev server (offline test bots, local only).
//   node featbot.js <srv2ctl.sh> <logfile>
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, logfile] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
// the last element after the final newline is '' (or a half-written line): never count it, or slice(n) skips a line
const lines = () => { const a = fs.readFileSync(logfile, 'utf8').split('\n'); a.pop(); return a; };
const results = [];
const check = (name, ok, detail) => { results.push({ name, ok: !!ok, detail }); console.error((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' :: ' + detail : '')); };
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };

function join(name) {
  return new Promise((resolve) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(process.env.PORT || 25571), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    bot.chatLog = []; bot.bars = new Map();
    bot.on('messagestr', (m) => bot.chatLog.push(m));
    bot._client.on('boss_bar', (pk) => {
      if (!pk.title) return;
      const t = flat(pk.title);
      bot.bars.set(String(pk.entityUUID), t);
      if (!bot.firstTutorial && t.includes('Заавар')) bot.firstTutorial = t; // the first tutorial bar ever shown
    });
    bot.on('error', () => {});
    // accept the pack like a player does (PackGate holds the class menu while a pack answer is missing)
    bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
    bot.once('spawn', async () => { await sleep(3500); resolve(bot); });
    setTimeout(() => resolve(null), 30000);
  });
}
async function pickClass(bot, slot) {
  for (let i = 0; i < 10 && !bot.currentWindow; i++) await sleep(500);
  if (bot.currentWindow) { await bot.clickWindow(slot, 0, 0).catch(() => {}); await sleep(600); }
  if (bot.currentWindow) { await bot.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); } // «Батлах» (ONB-006 confirm page)
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
}
const heard = (bot, text) => bot.chatLog.some(l => l.includes(text));

(async () => {
  const tag = 'F' + Math.floor(Math.random() * 90000 + 10000);
  // ---------------------------------------------------------------- tutorial (fresh player)
  const A = await join(tag + 'a');
  // the tutorial begins 120 ticks after a first join (longer on a busy server): wait for its bar before the class pick
  for (let i = 0; i < 40 && !A.firstTutorial; i++) await sleep(500);
  // the first tutorial bar a fresh player sees is step 1 (a snapshot after a fixed wait races the class pick)
  const bars0 = A.firstTutorial || [...A.bars.values()].join(' | ');
  check('tutorial starts on first join', /Заавар 1\/7/.test(bars0), bars0);
  await pickClass(A, 10);
  await sleep(2500);
  const bars1 = [...A.bars.values()].join(' | ');
  check('tutorial advances after the class pick', /Заавар [23]\/7/.test(bars1), bars1);
  A.chat('/skills chest'); await sleep(2500); if (A.currentWindow) A.closeWindow(A.currentWindow);
  // ---------------------------------------------------------------- chat channels
  const B = await join(tag + 'b'); await pickClass(B, 11);
  const C = await join(tag + 'c'); await pickClass(C, 12);
  cmd(`tp ${tag}a -16 75 -46`); cmd(`tp ${tag}b -10 75 -46`); cmd(`tp ${tag}c 400 120 -46`);
  await sleep(2500);
  A.chat('/ch ойр'); await sleep(800);
  A.chat('local-hello-' + tag); await sleep(1500);
  check('local chat reaches a player nearby', heard(B, 'local-hello-' + tag));
  check('local chat does not reach a player 400 blocks away', !heard(C, 'local-hello-' + tag));
  A.chat('!global-hello-' + tag); await sleep(1500);
  check('!text goes to everyone', heard(C, 'global-hello-' + tag));
  A.chat('/party invite ' + tag + 'b'); await sleep(1000); B.chat('/party accept'); await sleep(1500);
  A.chat('/pc party-hello-' + tag); await sleep(1500);
  check('party chat reaches the party', heard(B, 'party-hello-' + tag));
  check('party chat does not reach others', !heard(C, 'party-hello-' + tag));
  A.chat('/g /help'); await sleep(1200);
  check('a chat line starting with / is not run as a command', heard(B, '/help') || heard(C, '/help'));
  const tagLine = B.chatLog.find(l => l.includes('party-hello-' + tag)) || '';
  check('channel tag in front of the line', /\[Б\]/.test(tagLine), tagLine);
  // ---------------------------------------------------------------- lock-on (Q with the class weapon)
  // outside Хархорум: the city sanctuary takes every hostile mob away at once
  cmd(`spreadplayers 1500 1500 1 4 false ${tag}c`); await sleep(4000);
  cmd(`execute at ${tag}c run summon minecraft:zombie ~ ~ ~5 {NoAI:1b,Tags:["featlock"]}`); await sleep(800);
  await C.lookAt(C.entity.position.offset(0, 1.6, 5)).catch(() => {});
  const weapon = C.inventory.items().find(i => /sword|bow|stick|axe|trident|mace|spear|blaze_rod/.test(i.name)) || C.heldItem;
  if (weapon) await C.equip(weapon, 'hand').catch(() => {});
  await sleep(500);
  const before = C.inventory.items().length;
  const spawned = []; C.on('entitySpawn', (e) => { if (e.name === 'item_display') spawned.push(e); });
  C._client.write('block_dig', { status: 4, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
  await sleep(1500);
  check('Q with the class weapon does not drop it', C.inventory.items().length === before, before + ' -> ' + C.inventory.items().length);
  check('lock-on shows a reticle to the locker', spawned.length > 0, spawned.length + ' displays');
  cmd("minecraft:kill @e[tag=featlock]"); await sleep(600);
  // ---------------------------------------------------------------- dungeon hall
  const n0 = lines().length;
  cmd(`suld exp ${tag}c 30000`); cmd(`effect give ${tag}c minecraft:resistance 600 4 true`); cmd(`effect give ${tag}c minecraft:slow_falling 60 0 true`);
  cmd('suldworld halls'); await sleep(1200);
  const gate = lines().filter(l => /gate khasar_den: -?\d+/.test(l)).pop() || '';
  const m = gate.match(/khasar_den: (-?\d+) (-?\d+) (-?\d+)/);
  if (m) { cmd(`tp ${tag}c ${+m[1] + 0.5} ${+m[2] + 2} ${+m[3] + 3.5}`); }
  check('gate of Хасарын Агуй known', !!m, gate.slice(-80));
  await sleep(3000);
  // «Саарлын Агуй» opens after story chapter 4 (PROG-004): put the bot at chapter 5 first
  cmd(`suld quest ${tag}c 5`); await sleep(1000);
  const titles = []; C._client.on('set_title_text', (pk) => titles.push(JSON.stringify(pk.text)));
  C.chat('/dungeon enter khasar_den'); await sleep(6000);
  const gp = C.entity && m ? Math.hypot(C.entity.position.x - +m[1], C.entity.position.z - +m[3]) : 0;
  // in the run: the halls world (a dimension change) or far from the gate
  const inHall = !!(C.game && /halls/.test(String(C.game.dimension))) || gp > 50 || lines().slice(n0).some(l => /built den slot/.test(l));
  check('run starts in a hall', inHall, (C.entity && C.entity.position.toString()) + ' dim ' + (C.game && C.game.dimension) + ' ' + C.chatLog.slice(-2).join(' | ').slice(0, 200));
  for (let w = 0; w < 10; w++) { cmd('execute in minecraft:suld_halls run minecraft:kill @e[tag=suld_dungeon]'); await sleep(3000); }
  await sleep(9000);
  const p = C.entity.position;
  const near = m ? Math.hypot(p.x - +m[1], p.z - +m[3]) : -1;
  check('after the run the party is back at the gate', inHall && m && near < 20, 'distance ' + near.toFixed(1));
  check('dungeon completed', titles.some(t => t.includes('АГУЙ ДУУСЛАА')), titles.slice(-2).join(' / ').slice(0, 160));
  // ---------------------------------------------------------------- models on mobs
  cmd(`execute at ${tag}b run summon minecraft:zombie ~ ~ ~`); // vanilla: no rig
  const modelLines = lines().filter(l => /model [a-z_]+: \d+ bones/.test(l));
  check('mob rigs loaded at start', modelLines.length >= 30, modelLines.length + ' rigs');
  console.log(JSON.stringify(results, null, 1));
  [A, B, C].forEach(b => b && b.quit());
  await sleep(1500); process.exit(0);
})();
setTimeout(() => { console.log(JSON.stringify(results, null, 1)); process.exit(0); }, 240000);
