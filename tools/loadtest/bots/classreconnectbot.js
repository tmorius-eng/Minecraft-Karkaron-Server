// Class change survives a reconnect, also without resource-pack metadata (docs/CLASS_CHANGE.md):
//   node classreconnectbot.js <srvctl.sh> <pack: nil|none> [port]
// pick Баатар -> change to Мэргэн -> quit -> rejoin: still Мэргэн with the bow -> back to Баатар -> quit -> rejoin:
// Баатар with the sword. pack none: the client never answers the pack prompt (the class system must not care).
const mineflayer = require('mineflayer');
const [ctl, pack = 'nil', port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + '[' + pack + '] ' + what + ' :: ' + info); };
const name = 'R' + pack[0] + Math.floor(Math.random() * 90000 + 10000);

function join() {
  return new Promise((res) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    b.on('error', () => {});
    b.on('resourcePack', () => { if (pack === 'nil') { try { b.acceptResourcePack(); } catch (e) {} } });
    b.chatLog = [];
    b.on('messagestr', m => b.chatLog.push(m));
    b.once('spawn', async () => { await sleep(3000); res(b); });
  });
}
const texts = (b) => Object.values(b.entities).filter(e => e.name === 'text_display' && e.position.y > 300).map(e => ({ e, t: JSON.stringify(e.metadata) }));
const nl = (x) => (x.t.match(/\\n/g) || []).length;
const find = (b, ...all) => texts(b).filter(x => all.every(s => x.t.includes(s))).sort((a, c) => nl(a) - nl(c))[0];
async function clickOn(b, ...label) {
  const x = find(b, ...label), title = find(b, 'SÜLD');
  if (!x || !title) return false;
  const eye = title.e.position.offset(0, -2.78, 0.08 - 3.2), t = x.e.position;
  const dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
  b._client.write('look', { yaw: -Math.atan2(dx, dz) * 180 / Math.PI, pitch: -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI, flags: { onGround: false, hasHorizontalCollision: false } });
  await sleep(500); b.swingArm('right'); await sleep(1800);
  return true;
}
async function change(b, cls) {
  b.chat('/class'); await sleep(3500);
  await clickOn(b, cls, 'Үүрэг');
  const k = b.chatLog.length;
  await clickOn(b, 'Батлах'); await sleep(1500);
  b._client.write('player_input', { inputs: { shift: true } }); await sleep(600); b._client.write('player_input', { inputs: {} }); await sleep(1500);
  return b.chatLog.slice(k).some(l => l.includes(cls + ' анги боллоо'));
}
const items = (b) => b.inventory.items().map(i => i.name);
async function quit(b) { b.quit(); await sleep(4000); }

(async () => {
  let b = await join();
  for (let i = 0; i < 100 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(700); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(2000);
  check(items(b).some(n => n.includes('sword')), 'Баатар picked', items(b).join(','));
  check(await change(b, 'Мэргэн'), 'changed to Мэргэн', items(b).join(','));
  await quit(b);
  b = await join(); await sleep(2000);
  check(items(b).includes('bow') && !items(b).some(n => n.includes('sword')), 'after a reconnect: still Мэргэн with the bow', items(b).join(','));
  b.chat('/class'); await sleep(3500);
  check(!!find(b, 'АНГИЙН АКАДЕМИ'), 'the academy opens after the reconnect', texts(b).length + ' texts');
  b._client.write('player_input', { inputs: { shift: true } }); await sleep(600); b._client.write('player_input', { inputs: {} }); await sleep(1500);
  check(await change(b, 'Баатар'), 'back to the earlier class after the reconnect', items(b).join(','));
  await quit(b);
  b = await join(); await sleep(2000);
  check(items(b).some(n => n.includes('sword')) && !items(b).includes('bow'), 'after a second reconnect: Баатар with the same kind of sword, no bow', items(b).join(','));
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})();
