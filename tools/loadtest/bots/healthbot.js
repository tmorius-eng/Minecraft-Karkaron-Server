// A player's health survives a relog and a server restart, one offline bot:
//   node healthbot.js <srvctl.sh> [port]
// The health above the class's base comes from transient modifiers (the class gear, the skill tree) that the game
// does not save, so loading the player file used to clamp it to the bare base (55/55 came back as 46/55); and the
// skill tree's stop() lowered every online player's maximum to 20 before the server saved them, so a restart left
// everyone at 20 HP. Seen on a 1.21.11 client, 2026-10-13.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'H' + Math.floor(Math.random() * 90000 + 10000);

function join() {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
  b.on('error', () => {});
  b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
  return b;
}
const spawned = (b) => new Promise((r, j) => { b.once('spawn', r); setTimeout(() => j(new Error('no spawn')), 60000); });

(async () => {
  let b = join();
  await spawned(b);
  await sleep(3500);
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(3000);
  const h0 = b.health;
  check(h0 > 20, 'a new class starts above the vanilla 20 HP', 'health ' + h0);
  // 1. relog
  b.quit(); await sleep(4000);
  b = join(); await spawned(b); await sleep(3000);
  const h1 = b.health;
  check(Math.abs(h1 - h0) <= 1, 'the health is the same after a relog', h0 + ' -> ' + h1);
  // 2. a restart while online
  execFileSync(ctl, ['stop'], { stdio: 'ignore' });
  await sleep(1000);
  execFileSync(ctl, ['start'], { stdio: 'ignore', timeout: 240000 });
  await sleep(3000);
  b = join(); await spawned(b); await sleep(3000);
  const h2 = b.health;
  check(Math.abs(h2 - h0) <= 1, 'the health is the same after a server restart', h0 + ' -> ' + h2);
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})().catch((e) => { console.log('FAIL crashed :: ' + e.stack); process.exit(1); });
