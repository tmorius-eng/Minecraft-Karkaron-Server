const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl, port] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const name = 'Pc' + Math.floor(Math.random() * 900 + 100);
const b = mineflayer.createBot({ host: '127.0.0.1', port: +port, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
b.once('spawn', async () => {
  await sleep(4000);
  for (let i = 0; i < 8 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(1000); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  cmd('gamemode survival ' + name);
  const [X, Z] = [-36, -141];
  cmd(`tp ${name} ${X + 7} 120 ${Z}`); await sleep(7000);
  let bar = 0, ys = new Set();
  for (let dx = -4; dx <= 4; dx++) for (let dz = -4; dz <= 4; dz++) for (let y = 50; y < 110; y++) { const bl = b.blockAt(b.entity.position.offset(0,0,0).floored().offset(X + 7 - b.entity.position.floored().x, 0, 0).set(X + dx, y, Z + dz)); if (bl && bl.name === 'barrier') { bar++; ys.add(y); } }
  console.log('barriers near silver tree:', bar, 'y range', Math.min(...ys), Math.max(...ys));
  // walk west into the tree
  await sleep(1500); const p0 = b.entity.position.clone(); await b.lookAt(b.entity.position.offset(-5, 1.6, 0));
  b.setControlState('forward', true); await sleep(4000); b.setControlState('forward', false);
  const p1 = b.entity.position; console.log('walked from x', p0.x.toFixed(2), 'to x', p1.x.toFixed(2), '(tree centre x', X + 0.5, ')');
  b.quit(); setTimeout(() => process.exit(0), 300);
});
setTimeout(() => process.exit(1), 60000);
