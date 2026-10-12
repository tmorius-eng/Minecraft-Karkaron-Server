// At the cap the /ascend checklist and the stars after rank III announce every rise (AscensionMenu.watch).
//   node ascendstarsbot.js <srvctl.sh> [port]
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'A' + Math.floor(Math.random() * 90000 + 10000);
const STAR = 4569840;
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
(async () => {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline' });
  b.on('error', () => {});
  b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
  const log = []; b.on('messagestr', m => log.push(m));
  await new Promise(r => b.once('spawn', r)); await sleep(3000);
  for (let i = 0; i < 100 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(700); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  cmd(`suld exp ${name} 90000000`); await sleep(1500);
  cmd(`suld endgame ${name} rank 1`); await sleep(300);
  cmd(`suld endgame ${name} palace 0`); await sleep(11000); // first look: not announced
  let k = log.length;
  cmd(`suld endgame ${name} palace 1`); await sleep(11000);
  check(log.slice(k).some(l => l.includes('нөхцөл:') && l.includes('/ascend')), 'a palace clear at the cap announces the gate step', log.slice(k).join(' | ').slice(0, 160));
  cmd(`suld endgame ${name} rank 3`); await sleep(500);
  cmd(`suld endgame ${name} points ${2 * STAR}`); await sleep(11000); // a new rank: first look again
  k = log.length;
  cmd(`suld endgame ${name} points ${3 * STAR + 5}`); await sleep(11000);
  check(log.slice(k).some(l => l.includes('Тэнгэрийн Од 3')), 'at rank III a new «Тэнгэрийн Од» is announced', log.slice(k).join(' | ').slice(0, 160));
  k = log.length; await sleep(11000);
  check(!log.slice(k).some(l => l.includes('Тэнгэрийн Од')), 'no repeat while nothing rises', log.slice(k).length + ' lines');
  b.quit();
  const bad = results.filter(x => !x).length;
  console.log(bad ? bad + ' FAILED' : 'ALL ' + results.length + ' PASSED');
  process.exit(bad ? 1 : 0);
})().catch(e => { console.log('ERR', e); process.exit(2); });
