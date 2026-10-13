// «Тэнгэрийн сорил» on the dev server (docs/DEATH_AND_RECOVERY.md), one offline bot:
//   node trialbot.js <srvctl.sh> [port]
// A living player is refused, a level-20 soul (30-min-capped lock) outside the city starts the trial, three
// «Сүнсний чоно» spawn and are killed by the bot, the lock ends (no boss bar, «Тэнгэр ивээлээ»), the wolves are gone.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'T' + Math.floor(Math.random() * 90000 + 10000);
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

b.once('spawn', async () => {
  await sleep(3500);
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  cmd(`suld exp ${name} 200000`); await sleep(1000);
  let k = chat.length; b.chat('/trial'); await sleep(1500);
  check(said(k, 'та амьд байна'), 'a living player is refused', chat.slice(k).join(' | ').slice(0, 120));

  cmd(`execute in minecraft:overworld run tp ${name} 900 200 900`); await sleep(4000);
  cmd(`kill ${name}`); await sleep(1500);
  if (b.health <= 0 || b.isAlive === false) { b.respawn(); }
  await sleep(4000);
  cmd(`execute in minecraft:overworld run tp ${name} 900 200 900`); await sleep(5000);
  cmd(`minecraft:give ${name} netherite_sword`); cmd(`effect give ${name} strength 300 4 true`); await sleep(1500);
  const sword = b.inventory.items().find(i => i.name === 'netherite_sword');
  if (sword) await b.equip(sword, 'hand').catch(() => {});

  k = chat.length; b.chat('/trial'); await sleep(2500);
  let ws = wolves();
  check(ws.length === 3, 'a soul outside the city starts the trial: three spirit wolves spawn', 'wolves ' + ws.length + ' :: ' + chat.slice(k).join(' | ').slice(0, 100));
  k = chat.length; b.chat('/trial'); await sleep(800);
  check(said(k, 'аль хэдийн эхэлсэн'), 'a second /trial during the run is refused', chat.slice(k).join(' | ').slice(0, 100));

  k = chat.length;
  const t0 = Date.now();
  while (Date.now() - t0 < 150000 && !said(k, 'Тэнгэр ивээлээ')) {
    ws = wolves();
    if (ws.length) {
      const w = ws.sort((a, c) => a.position.distanceTo(b.entity.position) - c.position.distanceTo(b.entity.position))[0];
      await b.lookAt(w.position.offset(0, 0.4, 0), true);
      if (w.position.distanceTo(b.entity.position) > 3) cmd(`tp ${name} ${w.position.x.toFixed(1)} ${w.position.y.toFixed(1)} ${w.position.z.toFixed(1)}`);
      b.attack(w);
    }
    await sleep(650);
  }
  await sleep(2000);
  check(said(k, 'Тэнгэр ивээлээ'), 'killing the three wolves wins the trial and ends the lock', ((Date.now() - t0) / 1000).toFixed(0) + ' s :: ' + chat.slice(k).filter(l => l.length > 3).slice(-2).join(' | ').slice(0, 140));
  check(wolves().length === 0, 'no trial wolf is left in the world', 'wolves ' + wolves().length);
  k = chat.length; b.chat('/trial'); await sleep(1500);
  check(said(k, 'та амьд байна'), 'after the win the player is alive again (/trial refused)', chat.slice(k).join(' | ').slice(0, 100));

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
