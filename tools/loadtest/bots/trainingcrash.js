// The training grounds survive a crash (docs/TRAINING_GROUNDS.md), dev server only:  node trainingcrash.js <srvctl.sh> [port]
// A Баатар trains as Мэргэн; the server process is killed with SIGKILL (no shutdown, no plugin disable); after the
// restart the same player joins with their own bag and on their own spot, and the database still has Баатар at level 1.
// KILLS THE SERVER: never run it against a server with players.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'K' + Math.floor(Math.random() * 90000 + 10000);
const psql = (q) => execFileSync('runuser', ['-u', 'postgres', '--', 'psql', '-d', process.env.SULD_DB || 'suldit', '-At', '-c', q], { cwd: '/tmp' }).toString().trim();
const srvDir = path.join(path.dirname(ctl), 'srv3');
function join() {
  return new Promise((res) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    b.on('error', () => {});
    b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
    b.chat2 = []; b.on('messagestr', m => b.chat2.push(m));
    b.once('spawn', async () => { await sleep(3500); res(b); });
  });
}
const bag = (b) => b.inventory.slots.filter(Boolean).map(s => s.name).sort().join(',');
function killServer() {
  // the java of this server (its working directory is the server folder), killed without any shutdown
  for (const pid of execFileSync('pgrep', ['-f', 'paper.jar']).toString().trim().split('\n')) {
    try { if (fs.readlinkSync('/proc/' + pid + '/cwd') === srvDir) { process.kill(Number(pid), 'SIGKILL'); return pid; } } catch (e) {}
  }
  return null;
}

(async () => {
  let b = await join();
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(800); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(2500); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(2000);
  const before = bag(b), at = b.entity.position.clone();
  b.chat('/dasgal mergen'); await sleep(4500);
  const kit = bag(b);
  check(kit.includes('bow') && kit.includes('barrier') && kit !== before, 'training as Мэргэн: the bag is the kit', kit);
  await sleep(1500);
  const pid = killServer();
  check(!!pid, 'the server is killed with SIGKILL during training', 'pid ' + pid);
  await sleep(4000);
  try { execFileSync('tmux', ['kill-session', '-t', 'wb3']); } catch (e) {}
  execFileSync(ctl, ['start'], { timeout: 240000 });
  b = await join();
  await sleep(4000);
  const after = bag(b);
  check(after === before, 'after the crash the player has their own bag back (no kit item, nothing lost)', '[' + after + '] vs [' + before + ']');
  check(b.entity.position.distanceTo(at) < 3, 'and stands where they started training', b.entity.position.distanceTo(at).toFixed(1));
  check(b.chat2.some(l => l.includes('Өмнөх дасгал дууссан')), 'they are told the training ended', b.chat2.filter(l => l.length > 3).slice(-3).join(' | ').slice(0, 160));
  const row = psql(`select coalesce(class_id,'-'), level from suld_profiles where name='${name}'`);
  check(row === 'baatar|1', 'the database has the real Баатар at level 1 (the stand-in was never written)', row);
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})();
