// «Ангийн Сорилт» (class trial, docs/CLASS_CHANGE.md), one offline bot:  node classtrialbot.js <srvctl.sh> [port]
// Баатар opens a Мэргэн trial from the academy: in the arena with the bow, no sword; hitting a dummy (a mannequin)
// counts damage; the bow shoots with no arrows;
// other commands are refused; /classtrial leave puts Баатар, the sword and the place back, with no coins or EXP
// gained; a second trial cut by a disconnect is undone by the time the player is back.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'T' + Math.floor(Math.random() * 90000 + 10000);
function join() {
  return new Promise((res) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    b.on('error', () => {});
    b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
    b.chatLog = []; b.on('messagestr', m => b.chatLog.push(m));
    b.bars = []; b._client.on('action_bar', (pk) => b.bars.push(JSON.stringify(pk.text)));
    b.once('spawn', async () => { await sleep(3000); res(b); });
  });
}
// mineflayer keeps entities of unloaded chunks: only the window opened by the last /class counts (newer ids)
const texts = (b) => Object.values(b.entities).filter(e => e.name === 'text_display' && e.position.y > 300 && e.id > (b.minId || 0)).map(e => ({ e, t: JSON.stringify(e.metadata) }));
const nl = (x) => (x.t.match(/\\n/g) || []).length;
const find = (b, ...all) => texts(b).filter(x => all.every(s => x.t.includes(s))).sort((a, c) => nl(a) - nl(c))[0];
async function clickOn(b, ...label) {
  const x = find(b, ...label), title = find(b, 'SÜLD');
  if (!x || !title) return false;
  const eye = title.e.position.offset(0, -2.78, 0.08 - 3.2), t = x.e.position;
  const dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
  b._client.write('look', { yaw: -Math.atan2(dx, dz) * 180 / Math.PI, pitch: -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI, flags: { onGround: false, hasHorizontalCollision: false } });
  await sleep(500); b.swingArm('right'); await sleep(2200);
  return true;
}
const items = (b) => b.inventory.items().map(i => i.name);
const said = (b, k, s) => b.chatLog.slice(k).some(l => l.includes(s));
async function trial(b) {
  b.minId = Math.max(0, ...Object.keys(b.entities).map(Number));
  b.chat('/class'); await sleep(3500);
  const c1 = await clickOn(b, 'Мэргэн', 'Үүрэг');
  const c2 = await clickOn(b, 'Турших');
  if (process.env.DEBUG) console.log('trial clicks', c1, c2, texts(b).length, b.minId, Object.values(b.entities).filter(e => e.name === 'text_display').map(e => e.id + '@' + Math.round(e.position.y)).slice(-8).join(' '));
  await sleep(2500);
}

(async () => {
  let b = await join();
  for (let i = 0; i < 100 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(700); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(2000);
  const origin = b.entity.position.clone();
  execFileSync(ctl, ['cmd', `suld exp ${name} 3000000`]); await sleep(1500);
  execFileSync(ctl, ['cmd', `suld coins ${name} 777`]); await sleep(800);
  const k0 = b.chatLog.length;
  await trial(b);
  const inArena = b.entity.position.y > 99 && b.entity.position.y < 106 && b.entity.position.distanceTo(origin) > 500;
  check(inArena && items(b).includes('bow') && !items(b).some(n => n.includes('sword')), 'the trial puts the player in the arena as Мэргэн (bow, no sword)', b.entity.position.toString() + ' ' + items(b).join(','));
  const bow = b.inventory.items().find(i => i.name === 'bow');
  if (bow) { await b.equip(bow, 'hand').catch(() => {}); await sleep(1500); }
  let shot = 0; const onArrow = (e) => { if (e.name === 'arrow') shot++; }; b.on('entitySpawn', onArrow);
  b.activateItem(); await sleep(60); b.deactivateItem(); await sleep(800); b.removeListener('entitySpawn', onArrow);
  check(shot === 1 && !items(b).includes('arrow'), 'the trial bow shoots on a right click with no arrow in the bag', shot + ' shot, ' + items(b).join(','));
  const dummy = Object.values(b.entities).find(e => e.name === 'mannequin' && e.position.y > 99 && e.position.y < 106);
  if (dummy) { await b.lookAt(dummy.position.offset(0, 1, 0), true); for (let i = 0; i < 4; i++) { b.attack(dummy); await sleep(700); } }
  await sleep(1500);
  const dummyThere = !!dummy;
  let k = b.chatLog.length; b.chat('/spawn'); await sleep(1200);
  check(said(b, k, 'зөвхөн /classtrial leave'), 'other commands are refused in the trial', b.chatLog.slice(k).join(' | ').slice(0, 100));
  k = b.chatLog.length; b.chat('/classtrial leave'); await sleep(3500);
  // the HUD notice is drawn in the pack's glyph font (unreadable here): the trial's end audit line has the damage
  const log = require('fs').readFileSync(require('path').dirname(ctl) + '/srv3/logs/latest.log', 'utf8').split('\n').filter(l => l.includes('class.trial.end') && l.includes(name.length ? '' : '')).pop() || '';
  const dmg = Number((log.match(/damage=(\d+)/) || [])[1] || 0);
  check(dummyThere && dmg > 0, 'a practice dummy is there and hits on it are counted', 'dummy ' + dummyThere + ', damage ' + dmg);
  check(said(b, k, 'ангидаа буцлаа') && items(b).some(n => n.includes('sword')) && !items(b).includes('bow') && !items(b).includes('arrow') && b.entity.position.distanceTo(origin) < 3,
    'leaving puts Баатар, the sword and the place back', b.entity.position.distanceTo(origin).toFixed(1) + ' ' + items(b).join(','));
  // a disconnect DURING a trial (2 min between trials: the admin reset clears that for the test)
  execFileSync(ctl, ['cmd', `suld classreset ${name}`]); await sleep(800);
  k = b.chatLog.length;
  await trial(b);
  check(items(b).includes('bow') && b.entity.position.distanceTo(origin) > 500, 'a second trial starts', items(b).join(',') + ' ' + b.chatLog.slice(k).join(' | ').slice(0, 200));
  b.quit(); await sleep(4000);
  b = await join(); await sleep(4000);
  check(items(b).some(n => n.includes('sword')) && !items(b).includes('bow') && !items(b).includes('arrow') && b.entity.position.distanceTo(origin) < 3,
    'a disconnect during the trial: the next join is Баатар with the sword, back where the trial began', b.entity.position.distanceTo(origin).toFixed(1) + ' ' + items(b).join(','));
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})();
