// A brand-new player picks Мэргэн: a right click shoots at once with no arrows in the bag (one shot a second), and
// the L-R-x combos still cast (a drawn bow would eat the left clicks); also after a relog.
//   node mergenbowbot.js <srvctl.sh> [port]
const mineflayer = require('mineflayer');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'M' + Math.floor(Math.random() * 90000 + 10000);
function join() {
  return new Promise((res, rej) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline' });
    b.on('error', rej); b.on('kicked', (r) => console.log('kicked', JSON.stringify(r)));
    b.on('resource_pack', () => { try { b.acceptResourcePack(); } catch (e) {} });
    b.once('spawn', async () => { await sleep(3000); res(b); });
  });
}
const { execFileSync } = require('child_process');
const items = (b) => b.inventory.items().map(i => i.name);
let arrows = 0;
const R = async (b) => { b.activateItem(); await sleep(60); b.deactivateItem(); };
const L = async (b) => { b.swingArm('right'); };
(async () => {
  let b = await join();
  b.on('entitySpawn', (e) => { if (e.name === 'arrow') arrows++; });
  for (let i = 0; i < 100 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(700); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(1500);
  execFileSync(ctl, ['cmd', `suld exp ${name} 1500000`]); await sleep(2500);
  const bow = b.inventory.items().find(i => i.name === 'bow');
  check(!!bow && !items(b).includes('arrow'), 'Мэргэн gets the bow and needs no arrow', items(b).join(','));
  if (bow) await b.equip(bow, 'hand').catch(() => {});
  await sleep(2500);
  let a0 = arrows; await R(b); await sleep(600);
  check(arrows - a0 === 1, 'a right click shoots one arrow at once', (arrows - a0) + ' arrows');
  a0 = arrows; await R(b); await sleep(300);
  check(arrows - a0 === 0, 'a second click within the second does not', (arrows - a0) + ' arrows');
  await sleep(1200); a0 = arrows; await R(b); await sleep(600);
  check(arrows - a0 === 1, 'after the second it shoots again', (arrows - a0) + ' arrows');
  await sleep(1500);
  // L-R-R = Ухрах Үсрэлт (a leap back): the R clicks in the combo cast, they do not shoot
  const p0 = b.entity.position.clone(); a0 = arrows;
  let kicks = 0; const onVel = (pk) => { if (pk.entityId === b.entity.id) kicks++; }; b._client.on('entity_velocity', onVel);
  await L(b); await sleep(250); await R(b); await sleep(250); await R(b); await sleep(1200);
  const moved = b.entity.position.distanceTo(p0);
  b._client.removeListener('entity_velocity', onVel);
  // the leap is a velocity the server sends (a bot's physics may not carry it out the way a client does)
  check(kicks > 0 || moved > 1.5, 'L-R-R casts the leap back', 'moved ' + moved.toFixed(2) + ', velocity packets ' + kicks);
  check(arrows - a0 === 0, 'the combo R clicks shoot nothing', (arrows - a0) + ' arrows');
  await sleep(2500);
  // L-L-L = Олон Сум: five arrows
  a0 = arrows; await L(b); await sleep(250); await L(b); await sleep(250); await L(b); await sleep(1200);
  check(arrows - a0 >= 5, 'L-L-L casts Олон Сум (five arrows)', (arrows - a0) + ' arrows');
  b.quit(); await sleep(3000);
  b = await join(); await sleep(4000);
  check(items(b).includes('bow') && !items(b).includes('arrow'), 'after a relog the bow is there, still no arrow needed', items(b).join(','));
  b.quit();
  const bad = results.filter(x => !x).length;
  console.log(bad ? bad + ' FAILED' : 'ALL ' + results.length + ' PASSED');
  process.exit(bad ? 1 : 0);
})().catch(e => { console.log('ERR', e); process.exit(2); });
