// A sale survives a crash without a dupe (PlayerDataSaves.itemsForCoins, its javadoc), dev server only:
//   node sellcrash.js <srvctl.sh> [port] [killAfterMs]
// A fresh player is given a sellable sword and sells it; the server is killed with SIGKILL killAfterMs after the
// confirm (default 60 ms: between the item leaving the player file and the coins reaching the database); after the
// restart the same player joins. The contract is ITEMS → COINS: the sword is written off disk first, the coins after,
// so a crash in between loses the sale (the player keeps neither) and never duplicates it (the player never keeps
// both). The bot reports which of the three honest outcomes happened and fails only on a dupe or an item that came
// back without the coins having been refused. KILLS THE SERVER: never run it against a server with players.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const [ctl, port = '25572', killAfterMs = '60'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'S' + Math.floor(Math.random() * 90000 + 10000);
const psql = (q) => execFileSync('runuser', ['-u', 'postgres', '--', 'psql', '-d', process.env.SULD_DB || 'suldit', '-At', '-c', q], { cwd: '/tmp' }).toString().trim();
const srvDir = path.join(path.dirname(ctl), 'srv3');
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
function join() {
  return new Promise((res) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    b.on('error', () => {});
    b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
    b.chat2 = []; b.on('messagestr', m => b.chat2.push(m));
    b.once('spawn', async () => { await sleep(3500); res(b); });
  });
}
const swords = (b) => b.inventory.slots.filter(s => s && s.name.endsWith('_sword')).length;
const coins = () => Number(psql(`select currency from suld_profiles where name='${name}'`) || 0);
function killServer() {
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
  const before = swords(b);
  cmd(`itemsadmin give ${name} weapon.talyn_ild rare`); await sleep(2000);
  const slot = b.inventory.slots.findIndex((it, i) => i >= 36 && i <= 44 && it && it.name.endsWith('_sword') && JSON.stringify(it).includes('talyn'));
  check(slot >= 0 && swords(b) === before + 1, 'the player holds one sellable Талын илд', 'swords ' + before + ' -> ' + swords(b));
  if (slot < 0) { console.log('1 FAILED'); b.quit(); process.exit(1); }
  b.setQuickBarSlot(slot - 36); await sleep(600);
  const c0 = coins();
  b.chat('/item sell'); await sleep(1200);
  check(b.chat2.some(l => l.includes('/item sell confirm')), 'the sale asks for a confirm', b.chat2.slice(-1).join('').slice(0, 120));
  b.chat('/item sell confirm');
  await sleep(Number(killAfterMs));
  const pid = killServer();
  check(!!pid, 'the server is killed with SIGKILL ' + killAfterMs + ' ms after the confirm', 'pid ' + pid);
  await sleep(4000);
  try { execFileSync('tmux', ['kill-session', '-t', 'wb3']); } catch (e) {}
  execFileSync(ctl, ['start'], { timeout: 240000 });
  b = await join();
  await sleep(4000);
  const sword = swords(b) > before, paid = coins() > c0;
  const outcome = sword && paid ? 'DUPE: the sword is back AND the coins were paid' : !sword && paid ? 'completed: sold and paid'
    : !sword ? 'lost between the two writes: the sword is gone, the coins were not paid (the documented cost of ITEMS -> COINS)'
      : 'nothing happened: the sword is back, no coins';
  check(!(sword && paid), 'after the crash the player never has both the sword and the coins', outcome + ' (coins ' + c0 + ' -> ' + coins() + ')');
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})();
