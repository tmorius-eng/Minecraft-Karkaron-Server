// A fresh world's first boot: what stands by the spawn is built once, by the city's spawn. No player needed:
//   node firstbootbot.js <srvctl.sh> <server dir>
// DELETES the world of <server dir>: it refuses unless the directory holds a file named FIRSTBOOT_SCRATCH (a throwaway
// copy of a dev server with SÜLD's default config, never a real world). Kharkhorum moves the spawn onto its plaza
// when it is built; the dungeon gates were built before that around the first spawn and again by the city's, 18 blocks
// apart, and the ovoos and historic sites kept the first spawn until a restart (map inspection, 2026-10-13). The bot
// boots a new world, waits for the city, and reads the server log: every gate built exactly once and only after the
// city, the sites and ovoos moved with the spawn; then a restart builds nothing again.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const [ctl, dir] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const log = () => fs.readFileSync(path.join(dir, 'logs', 'latest.log'), 'utf8').split('\n');
const GATES = 10;

(async () => {
  if (!dir || !fs.existsSync(path.join(dir, 'FIRSTBOOT_SCRATCH'))) {
    console.log('FAIL refused :: ' + dir + ' has no FIRSTBOOT_SCRATCH file: this bot deletes the world');
    process.exit(1);
  }
  try { execFileSync(ctl, ['stop'], { stdio: 'ignore', timeout: 90000 }); } catch (e) { /* not running */ }
  for (const d of ['world', 'world_nether', 'world_the_end', 'suld_halls']) fs.rmSync(path.join(dir, d), { recursive: true, force: true });
  const data = path.join(dir, 'plugins', 'SULD');
  for (const f of fs.existsSync(data) ? fs.readdirSync(data) : []) {
    if (f !== 'config.yml' && f !== 'content') fs.rmSync(path.join(data, f), { recursive: true, force: true });
  }
  execFileSync(ctl, ['start'], { stdio: 'ignore', timeout: 300000 });
  let city = -1;
  for (let i = 0; i < 90 && city < 0; i++) { await sleep(5000); city = log().findIndex(l => l.includes('[worldbuild] Хархорум') && l.includes('баригдлаа')); }
  check(city >= 0, 'the city is built on the first boot', city >= 0 ? log()[city].slice(0, 120) : 'no «баригдлаа» line in 7.5 min');
  await sleep(40000); // the gates are built a few seconds after the spawn moves
  const lines = log();
  const gateLines = lines.map((l, i) => [l, i]).filter(([l]) => /Dungeon gate \S+ built at/.test(l));
  const per = {};
  for (const [l] of gateLines) { const id = l.match(/Dungeon gate (\S+) built/)[1]; per[id] = (per[id] || 0) + 1; }
  check(Object.keys(per).length === GATES && Object.values(per).every(n => n === 1), 'every dungeon gate is built exactly once',
    JSON.stringify(per));
  check(gateLines.length > 0 && gateLines.every(([, i]) => i > city), 'no gate is built before the city sets the spawn',
    gateLines.filter(([, i]) => i < city).length + ' before the city');
  const moved = lines.find(l => l.includes('World border (spawn moved)')) || '';
  const at = (moved.match(/centred on (-?\d+), (-?\d+)/) || []).slice(1).join(' ');
  check(!!at && lines.some(l => l.includes('[sites] the spawn moved to ' + at)) && lines.some(l => l.includes('[ovoo] the spawn moved to ' + at)),
    'the historic sites and the ovoos move with the spawn', 'spawn ' + (at || '?'));
  const early = lines.slice(0, city).filter(l => l.includes('[sites] built') || l.includes('[ovoo] built')).length;
  check(early === 0, 'no site or ovoo is built by the first spawn', early + ' built before the city');
  // a restart: the spawn is where it stays, nothing is built again
  execFileSync(ctl, ['stop'], { stdio: 'ignore', timeout: 90000 });
  execFileSync(ctl, ['start'], { stdio: 'ignore', timeout: 300000 });
  await sleep(30000);
  const again = log().filter(l => /Dungeon gate \S+ built at|\[sites\] the spawn moved|\[ovoo\] the spawn moved|gates wait/.test(l));
  check(again.length === 0, 'after a restart nothing moves and no gate is built again', again.length + ' line(s): ' + again.slice(0, 2).join(' | ').slice(0, 160));
  try { execFileSync(ctl, ['stop'], { stdio: 'ignore', timeout: 90000 }); } catch (e) { /* already down */ }
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  process.exit(results.every(Boolean) ? 0 : 1);
})().catch((e) => { console.log('FAIL crashed :: ' + e.stack); process.exit(1); });
