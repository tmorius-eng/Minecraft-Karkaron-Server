// «Тэнгэрийн сорил» when the lock ends during the fight (audit 2026-10-13 BUG-DEATH-01), one offline bot:
//   node trial-lockend.js <srvctl.sh> [port]
// A soul starts /trial, then the lock ends while the wolves are alive (staff /deathrevive: the same recover() path as
// the lock timer running out). The trial must stop at once: the wolves go, no cooldown message, and the living
// player is not bitten any more. Before the fix the wolves stayed angry at a living player, who could die for real.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
// the server log (the console's answers), under the control script's server directory (its SRV= line)
const fs = require('fs'), path = require('path');
const LOG = (() => { const t = fs.readFileSync(ctl, 'utf8').match(/SRV=(\S+)/); return t ? path.join(t[1], 'logs', 'latest.log') : null; })();
const lines = () => { if (!LOG) return []; const a = fs.readFileSync(LOG, 'utf8').split('\n'); a.pop(); return a; };
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'L' + Math.floor(Math.random() * 90000 + 10000);
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const chat = [];
b.on('messagestr', m => chat.push(m));
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
const said = (k, s) => chat.slice(k).some(l => l.includes(s));
// a spirit wolf is named «Сүнсний чоно» and glows; a wild wolf of the steppe (a natural pack spawned at the test spot,
// seen 2026-10-13) is neither, and is never one of the trial's three
const spirit = e => { let n = null; try { n = e.getCustomName && e.getCustomName(); } catch (x) {} return (n != null && String(n).includes('Сүнсний')) || ((e.metadata && e.metadata[0]) & 0x40) !== 0; };
const wolves = () => Object.values(b.entities).filter(e => e.name === 'wolf' && spirit(e) && b.entity && e.position.distanceTo(b.entity.position) < 24);
let deaths = 0;
b.on('death', () => { deaths++; });

b.once('spawn', async () => {
  await sleep(3500);
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  cmd(`suld exp ${name} 200000`); await sleep(1000);
  cmd(`execute in minecraft:overworld run tp ${name} 900 200 900`); await sleep(4000);
  cmd(`minecraft:kill ${name}`); await sleep(1500);
  if (b.health <= 0 || b.isAlive === false) b.respawn();
  await sleep(4000);
  cmd(`execute in minecraft:overworld run tp ${name} 900 200 900`); await sleep(5000);
  const before = deaths;

  let k = chat.length; b.chat('/trial'); await sleep(2500);
  const ws = wolves();
  check(ws.length === 3, 'the soul starts the trial: three spirit wolves', 'wolves ' + ws.length + ' :: ' + chat.slice(k).join(' | ').slice(0, 100));

  k = chat.length;
  cmd(`deathrevive ${name}`); // the lock ends while the trial runs
  await sleep(4000);
  check(said(k, 'сорил зогслоо'), 'the trial stops when the lock ends', chat.slice(k).filter(l => l.length > 3).join(' | ').slice(0, 160));
  check(wolves().length === 0, 'no spirit wolf is left near the living player', 'wolves ' + wolves().length);
  // stand still 12 s: a living player must not be bitten by its spirit wolves. Everything else near is cleared
  // meanwhile, every wolf but a spirit wolf (tag suld_trial) too, so one left behind still bites and fails this
  // check; a stray mob is not the trial (a pillager took 88 -> 57 here once, wolves 0; regress9 2026-10-13 lost
  // 96 -> 30 with no spirit wolf near while other wolves were spared, the cause not seen). The server is asked too:
  // no spirit wolf may be left anywhere in the world (the bot only sees 24 blocks).
  const clearOthers = () => cmd(`execute at ${name} run minecraft:kill @e[type=!player,type=!item,tag=!suld_trial,distance=..48]`);
  clearOthers();
  await sleep(500);
  const hp0 = b.health;
  let anywhere = 0;
  for (let i = 0; i < 6; i++) {
    await sleep(2000);
    clearOthers();
    const n = lines().length;
    cmd('execute if entity @e[tag=suld_trial]'); await sleep(400);
    if (lines().slice(n).some(l => /Test passed/.test(l))) anywhere++;
  }
  check(deaths === before && b.health >= hp0, 'the living player is not hurt after the lock ended', `hp ${hp0} -> ${b.health}, deaths ${deaths - before}`);
  check(!!LOG && anywhere === 0, 'no spirit wolf is left anywhere in the world', LOG ? anywhere + ' of 6 looks found one' : 'no server log to read');
  check(!said(k, 'минутын дараа дахин /trial'), 'no failed-trial cooldown for an ended lock', chat.slice(k).filter(l => l.length > 3).slice(-2).join(' | ').slice(0, 140));

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
setTimeout(() => { console.log('FAIL timeout'); process.exit(2); }, 120000);
