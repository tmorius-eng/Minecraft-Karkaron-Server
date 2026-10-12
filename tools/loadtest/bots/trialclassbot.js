// «Тэнгэрийн сорил» fought with the class's own weapon (no cheat items), one offline bot:
//   node trialclassbot.js <srvctl.sh> [port] [classSlot 10=Баатар 11=Мэргэн 12=Бөө 13=Дархан 14=Хүлэгчин]
// The soul's hits (arrows for Мэргэн) take the wolves' health down, the three die and the trial is won.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572', slotArg = '11'] = process.argv.slice(2);
const slot = Number(slotArg);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'W' + Math.floor(Math.random() * 90000 + 10000);
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const chat = [];
b.on('messagestr', m => chat.push(m));
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
const said = (k, s) => chat.slice(k).some(l => l.includes(s));
const wolves = () => Object.values(b.entities).filter(e => e.name === 'wolf' && b.entity && e.position.distanceTo(b.entity.position) < 32);
const hp = (e) => { const m = e.metadata || []; return typeof m[9] === 'number' ? m[9] : null; };

b.once('spawn', async () => {
  await sleep(3500);
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(slot, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  cmd(`suld exp ${name} 200000`); await sleep(1500);
  cmd(`execute in minecraft:overworld run tp ${name} 900 200 900`); await sleep(4000);
  cmd(`kill ${name}`); await sleep(1500);
  if (b.health <= 0 || b.isAlive === false) b.respawn();
  await sleep(4000);
  cmd(`execute in minecraft:overworld run tp ${name} 900 200 900`); await sleep(5000);
  const weapon = b.inventory.items().find(i => /bow|sword|axe|blaze_rod|trident|spear/.test(i.name));
  check(!!weapon, 'the soul still has its class weapon', b.inventory.items().map(i => i.name).join(','));
  if (weapon) await b.equip(weapon, 'hand').catch(() => {});
  const archer = weapon && weapon.name === 'bow';
  let k = chat.length; b.chat('/trial'); await sleep(2500);
  let ws = wolves();
  check(ws.length === 3, 'the trial starts with three wolves', 'wolves ' + ws.length + ' :: ' + chat.slice(k).join(' | ').slice(0, 100));
  const full = ws.length ? hp(ws[0]) : null;
  k = chat.length;
  const t0 = Date.now(); let hurt = false, shots = 0;
  b.on('entitySpawn', (e) => { if (e.name === 'arrow') shots++; });
  const trace = [];
  while (Date.now() - t0 < 170000 && !said(k, 'Тэнгэр ивээлээ') && !said(k, 'бүтсэнгүй')) {
    ws = wolves();
    if (ws.length) {
      const w = ws.sort((a, c) => a.position.distanceTo(b.entity.position) - c.position.distanceTo(b.entity.position))[0];
      if (full != null && hp(w) != null && hp(w) < full) hurt = true;
      if (process.env.DEBUG && trace.length < 40) trace.push(Math.round((Date.now() - t0) / 100) / 10 + 's w' + w.id + '=' + (hp(w) == null ? '?' : hp(w).toFixed(1)) + ' me=' + (b.health || 0).toFixed(1) + ' d=' + w.position.distanceTo(b.entity.position).toFixed(1));
      const d = w.position.distanceTo(b.entity.position);
      await b.lookAt(w.position.offset(0, 0.4, 0), true);
      if (archer) {
        // an archer keeps its distance (kites): step back from a wolf at its heels, then shoot
        if (d < 4) {
          const me = b.entity.position, dx = me.x - w.position.x, dz = me.z - w.position.z, n = Math.hypot(dx, dz) || 1;
          cmd(`tp ${name} ${(w.position.x + dx / n * 6).toFixed(1)} ${(me.y).toFixed(1)} ${(w.position.z + dz / n * 6).toFixed(1)}`);
          await sleep(250);
          await b.lookAt(w.position.offset(0, 0.4, 0), true);
        }
        b.activateItem(); await sleep(60); b.deactivateItem(); await sleep(1000);
      } else {
        if (d > 3) cmd(`tp ${name} ${w.position.x.toFixed(1)} ${w.position.y.toFixed(1)} ${w.position.z.toFixed(1)}`);
        b.attack(w); await sleep(650);
      }
    } else await sleep(500);
  }
  await sleep(2000);
  if (process.env.DEBUG) console.log('shots', shots, '\n' + trace.join('\n'));
  check(hurt, 'the class weapon takes a wolf\'s health down', 'full ' + full);
  check(said(k, 'Тэнгэр ивээлээ'), 'the three wolves die and the trial is won', ((Date.now() - t0) / 1000).toFixed(0) + ' s :: ' + chat.slice(k).filter(l => l.length > 3).slice(-2).join(' | ').slice(0, 160));
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
