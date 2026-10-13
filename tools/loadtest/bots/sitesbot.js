// A historic site is built once and remembered across a restart, one offline bot:
//   node sitesbot.js <srvctl.sh> <server latest.log> [port]
// sites.yml nests the dotted site ids («site.aurag»); the old reader saw only «site», so after every restart each
// site lost its guard and its height and was built again as soon as its chunk loaded. The bot stands at Аураг (the
// site is built if this server never built it), restarts the server, stands there again: no second build.
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, log, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'W' + Math.floor(Math.random() * 90000 + 10000);
const SITE = { id: 'site.aurag', x: 525, z: 99 };
const lines = () => fs.readFileSync(log, 'utf8').split('\n');
const builds = () => lines().filter(l => l.includes('[sites] built ' + SITE.id + ' ')).length;

function join() {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
  b.on('error', () => {});
  b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
  return new Promise((r, j) => { b.once('spawn', () => r(b)); setTimeout(() => j(new Error('no spawn')), 60000); });
}

async function visit(b) {
  execFileSync(ctl, ['cmd', `effect give ${name} slow_falling 60 0 true`]);
  execFileSync(ctl, ['cmd', `tp ${name} ${SITE.x + 6} 140 ${SITE.z + 6}`]);
  // the site is built (or found built) within a few of its 5 s checks once its chunk is loaded
  await sleep(16000);
}

(async () => {
  let b = await join();
  await sleep(3500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await visit(b);
  const first = builds();
  check(true, 'standing at the site (built now if this server never built it)', first + ' build line(s) in this log');
  b.quit(); await sleep(2000);
  execFileSync(ctl, ['stop'], { stdio: 'ignore' });
  await sleep(1000);
  execFileSync(ctl, ['start'], { stdio: 'ignore', timeout: 240000 });
  await sleep(3000);
  b = await join();
  await sleep(3000);
  await visit(b);
  const after = builds();
  check(after === 0, 'after a restart the site is remembered: not built again', after + ' build line(s) since the restart');
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})().catch((e) => { console.log('FAIL crashed :: ' + e.stack); process.exit(1); });
