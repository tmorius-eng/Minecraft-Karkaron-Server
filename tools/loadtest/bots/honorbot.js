// Тэнгэрийн Зэрэг IV–X: what the shrine promises is in the game (AscensionHonor), two offline bots, A and B:
//   node honorbot.js <srvctl.sh> [port]
// The back aura (VII–IX): B sees gold motes behind A at VII and none at VI, and the white glow at IX. The duel guard
// (IV): in a duel the same hit takes 3 % less of A's health at IV than at III. The sky's lightning
// (VI): A, set to rank V, hits frozen high-level mobs and no lightning's sparks show; at rank VI some hits call it. The
// monument (X): A is given the gates of X (staff commands), takes the rite in /ascend, and A's name stands on the
// plaque before the Khan's monument and in the Chronicle; the bot then hides that entry (/chronicle hide) and the
// plaque lets the name go. Ranks set by /suld endgame are staff settings: only the real rite makes an envoy.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const tag = Math.floor(Math.random() * 9000 + 1000);
const nameA = 'Ha' + tag, nameB = 'Hb' + tag;
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const STAR = 4569840;
// the particle packet names its kind (mineflayer 1.21.11: {"particle":{"type":"end_rod"}})
const DUST = 'dust', END_ROD = 'end_rod', SPARK = 'electric_spark';
// the server log (for the console's /chronicle lines): two levels over the control script's server directory
const LOG = (() => { const t = fs.readFileSync(ctl, 'utf8').match(/SRV=(\S+)/); return t ? path.join(t[1], 'logs', 'latest.log') : null; })();

function join(name) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
  b.on('error', () => {});
  b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
  b.gone = null; // why the server let the bot go (a kick names the cause: the checks after it say so)
  b.on('kicked', (r) => { b.gone = b.gone || 'kicked: ' + JSON.stringify(r).slice(0, 160); });
  b.on('end', (r) => { b.gone = b.gone || 'disconnected: ' + r; });
  b.chatLog = [];
  b.on('messagestr', (m) => b.chatLog.push(m));
  b.deaths = 0; // a soul takes no rite: a death on the way says why the window stayed shut
  b.on('death', () => { b.deaths++; });
  b.particles = []; // {id, x, y, z, at}
  b._client.on('world_particles', (pk) => {
    const id = pk.particle && pk.particle.type;
    b.particles.push({ id, x: pk.x, y: pk.y, z: pk.z, at: Date.now() });
  });
  return new Promise((r, j) => { b.once('spawn', () => r(b)); setTimeout(() => j(new Error('no spawn ' + name)), 60000); });
}
async function pickClass(b) {
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
}
// particles of a kind within r blocks of an entity since a time
const seen = (b, id, ent, r, since) => b.particles.filter(p => p.id === id && p.at >= since
  && Math.hypot(p.x - ent.position.x, p.z - ent.position.z) <= r).length;

(async () => {
  const a = await join(nameA);
  const b = await join(nameB);
  await sleep(3000);
  await Promise.all([pickClass(a), pickClass(b)]);
  await sleep(2500);
  const home = a.entity.position.clone(); // the city's spawn, by the monument
  // --- the back aura: both floating over the steppe, B 4 blocks from A
  const x = Math.floor(a.entity.position.x) + 400, z = Math.floor(a.entity.position.z) + 400;
  for (const n of [nameA, nameB]) cmd(`effect give ${n} slow_falling 300 0 true`);
  cmd(`tp ${nameA} ${x} 220 ${z} 0 0`);
  cmd(`tp ${nameB} ${x} 220 ${z + 4} 180 0`);
  await sleep(2500);
  const ea = () => b.players[nameA] && b.players[nameA].entity;
  cmd(`suld endgame ${nameA} rank 6`); await sleep(1500);
  let t0 = Date.now(); await sleep(3000);
  const at6 = ea() ? seen(b, DUST, ea(), 3, t0) : -1;
  cmd(`suld endgame ${nameA} rank 7`); await sleep(1000);
  t0 = Date.now(); await sleep(3000);
  const at7 = ea() ? seen(b, DUST, ea(), 3, t0) : -1;
  check(at6 === 0 && at7 >= 4, 'rank VII: gold motes on A\'s back (none at VI)', 'dust packets VI ' + at6 + ', VII ' + at7);
  cmd(`suld endgame ${nameA} rank 9`); await sleep(1000);
  t0 = Date.now(); await sleep(3000);
  const glow = ea() ? seen(b, END_ROD, ea(), 3, t0) : -1;
  check(glow >= 3, 'rank IX: the white glow among them', 'end rod packets ' + glow);
  // --- IV: a consented fight's hits land 3 % lighter on the bearer. A and B duel; the same staff hit "by B" (/damage)
  // lands on A at rank III and at rank IV. A's food is brought under 18 first (no natural regeneration), each drop is
  // read from the health update the hit sends, and the largest of three is kept (a heal in the same tick only shrinks it)
  let k = b.chatLog.length;
  a.chat('/duel ' + nameB); await sleep(1500);
  b.chat('/duel accept'); await sleep(2000);
  const dueling = b.chatLog.slice(k).some(l => l.includes('тулаанд орлоо'));
  cmd(`effect give ${nameA} minecraft:hunger 3 255 true`); await sleep(3500);
  const drop = async () => {
    let prev = a.health, d = null;
    const on = () => { const h = a.health; if (d === null && h < prev - 1e-6) d = prev - h; prev = h; };
    a.on('health', on);
    cmd(`damage ${nameA} 4 minecraft:player_attack by ${nameB}`);
    for (let i = 0; i < 20 && d === null; i++) await sleep(100);
    a.removeListener('health', on);
    await sleep(900); // past the hurt time
    return d === null ? 0 : d;
  };
  const series = async (rank) => {
    cmd(`suld endgame ${nameA} rank ${rank}`);
    cmd(`effect give ${nameA} minecraft:instant_health 1 10 true`); await sleep(1500);
    return [await drop(), await drop(), await drop()];
  };
  const d3 = await series(3), d4 = await series(4);
  const ratio = Math.max(...d3) > 0 ? Math.max(...d4) / Math.max(...d3) : 0;
  check(dueling && Math.abs(ratio - 0.97) < 0.004, 'rank IV: a duel\'s hits land 3 % lighter (none at III)',
    'duel ' + dueling + '; health drops III ' + d3.map(x => x.toFixed(4)).join('/') + ', IV ' + d4.map(x => x.toFixed(4)).join('/')
    + ' -> IV/III ' + ratio.toFixed(4));
  a.chat('/duel end'); await sleep(1000);
  // --- the sky's lightning: A (level 1, creative so nothing hunts it) hits frozen mobs of level 55, on dry ground
  // (spreadplayers finds a surface that is not water within 40 blocks; over a lake it fails and A stays in the air)
  cmd(`gamemode creative ${nameA}`);
  for (let k = 0; k < 6 && a.entity.position.y > 200; k++) {
    cmd(`spreadplayers ${x + 40 + 150 * k} ${z} 0 40 false ${nameA}`); await sleep(3000);
  }
  const ground = a.entity.position.y < 200;
  cmd(`suld spawnmob ${nameA} mob.tengeriin_tsereg 4`); await sleep(1500);
  cmd(`execute as ${nameA} at ${nameA} run effect give @e[type=!player,distance=..10] slowness 600 255 true`);
  const hits = async (n) => {
    const t = Date.now();
    let done = 0;
    for (let i = 0; i < n * 3 && done < n && !a.gone; i++) {
      // living hostile mobs only: an experience orb, a dropped item or an arrow is not a target (the server drops a
      // client that attacks one)
      const mob = a.nearestEntity(e => e.type === 'hostile' && e.isValid !== false
        && e.position.distanceTo(a.entity.position) < 4.5);
      if (!mob) {
        cmd(`suld spawnmob ${nameA} mob.tengeriin_tsereg 2`); await sleep(1200);
        cmd(`execute as ${nameA} at ${nameA} run effect give @e[type=!player,distance=..10] slowness 600 255 true`); await sleep(300);
        continue;
      }
      await a.lookAt(mob.position.offset(0, 1, 0), true);
      a.attack(mob);
      done++;
      await sleep(560); // the mob's hurt time: a quicker hit would not land
    }
    // a proc spawns a column of sparks at once: count the bursts (sparks more than 0.4 s apart)
    const sparks = a.particles.filter(p => p.id === SPARK && p.at >= t).map(p => p.at).sort((u, v) => u - v);
    let bursts = 0, last = -1e9;
    for (const s of sparks) { if (s - last > 400) bursts++; last = s; }
    return { done, bursts, sparks: sparks.length };
  };
  cmd(`suld endgame ${nameA} rank 5`); await sleep(500);
  const r5 = await hits(80);
  cmd(`suld endgame ${nameA} rank 6`); await sleep(500);
  const r6 = await hits(300); // 2 %: no strike in 300 hits is a 0.2 % chance
  check(!a.gone && ground && r5.done === 80 && r6.done === 300 && r5.bursts === 0 && r6.bursts >= 1 && r6.bursts <= 30,
    'rank VI: some hits on a mob call the sky\'s lightning (none at V)',
    (a.gone ? a.gone + '; ' : '') + (ground ? '' : 'A never reached dry ground; ') + 'rank V ' + r5.done + ' hits, ' + r5.bursts
    + ' strikes; rank VI ' + r6.done + ' hits, ' + r6.bursts + ' strikes (' + r6.sparks + ' spark packets; 2 % expects ~' + Math.round(r6.done * 0.02) + ')');
  // the frozen archers and their arrows go (a stray knocked out of reach still shoots: a survival A would die there)
  cmd(`execute as ${nameA} at ${nameA} run kill @e[type=stray,distance=..64]`);
  cmd(`execute as ${nameA} at ${nameA} run kill @e[type=arrow,distance=..64]`); await sleep(500);
  // --- the monument: the real rite to X, back on the plaza by the monument (the city keeps mobs out; the plaque's
  // chunk is loaded there), and only there out of creative
  cmd(`tp ${nameA} ${home.x.toFixed(1)} ${(home.y + 0.5).toFixed(1)} ${home.z.toFixed(1)}`); await sleep(3500);
  cmd(`gamemode survival ${nameA}`);
  cmd(`effect clear ${nameA}`);
  cmd(`suld exp ${nameA} 90000000`); await sleep(1500);
  cmd(`suld endgame ${nameA} rank 9`);
  cmd(`suld endgame ${nameA} points ${13 * STAR + 5}`);
  cmd(`suld heroic ${nameA} dungeon.khasar_den 18 18`);
  cmd(`suld mastery ${nameA} SCIMITAR 50`);
  cmd(`suld coins ${nameA} 300000`); await sleep(1500);
  a.chat('/ascend'); await sleep(2500);
  const opened = !!a.currentWindow;
  if (a.currentWindow) { await a.clickWindow(31, 0, 0).catch(() => {}); await sleep(1500); }
  if (a.currentWindow) { await a.clickWindow(31, 0, 0).catch(() => {}); await sleep(2500); }
  if (a.currentWindow) a.closeWindow(a.currentWindow);
  await sleep(4000); // the Chronicle write, the plaque redrawn (and its 5 s keeper)
  const plaques = () => Object.values(a.entities).filter(e => e.name === 'text_display' && JSON.stringify(e.metadata).includes('МӨНХ ТЭНГЭРИЙН ЭЛЧ'));
  const named = () => plaques().filter(e => JSON.stringify(e.metadata).includes(nameA)).length;
  check(!a.gone && opened && plaques().length === 1 && named() === 1, 'rank X by the rite: A\'s name stands on Kharkhorum\'s monument',
    (a.gone ? a.gone + '; ' : '') + (a.deaths ? 'A died ' + a.deaths + ' time(s); ' : '') + 'window ' + opened + ', plaques ' + plaques().length + ', with A ' + named());
  // the Chronicle has it; the bot hides the entry again (the dev server's monument keeps no test names)
  let id = null;
  if (LOG) {
    const before = fs.readFileSync(LOG, 'utf8').length;
    cmd('chronicle envoy'); await sleep(2000);
    const lines = fs.readFileSync(LOG, 'utf8').slice(before).split('\n').filter(l => l.includes(nameA) && l.includes('Мөнх Тэнгэрийн Элч'));
    const m = lines.length ? lines[0].match(/#(\d+) /) : null;
    id = m ? m[1] : null;
    check(!!id, 'the Chronicle holds A as «Мөнх Тэнгэрийн Элч»', lines.length ? lines[0].slice(0, 160) : 'no line');
  }
  if (id) { cmd(`chronicle hide ${id}`); await sleep(3000); }
  check(!!id && named() === 0, 'a hidden entry leaves the monument too', 'plaques with A ' + named());
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  a.quit(); b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})().catch((e) => { console.log('FAIL crashed :: ' + e.stack); process.exit(1); });
