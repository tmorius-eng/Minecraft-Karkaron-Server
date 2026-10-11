const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const name = 'Dg' + Math.floor(Math.random() * 900 + 100);
const b = mineflayer.createBot({ host: '127.0.0.1', port: +port, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const log = []; b.on('messagestr', m => log.push(m));
b._client.on('entity_velocity', pk => { if (b.entity && pk.entityId === b.entity.id) { console.log('velocity packet', JSON.stringify(pk)); if (pk.velocity) b.entity.velocity.set(pk.velocity.x, pk.velocity.y, pk.velocity.z); } });
b.once('spawn', async () => {
  await sleep(4000);
  for (let i = 0; i < 8 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(1000); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(2000);
  const p0 = b.entity.position.clone(); console.log('onGround', b.entity.onGround, 'y', p0.y.toFixed(2), 'window', !!b.currentWindow);
  b.setControlState('sneak', true); b._client.write('player_input', { inputs: { shift: true } }); await sleep(400);
  b.setControlState('jump', true); let maxY = 0; for (let i = 0; i < 6; i++) { await sleep(50); maxY = Math.max(maxY, b.entity.position.y - p0.y); } b.setControlState('jump', false);
  console.log('jump height', maxY.toFixed(2)); b.setControlState('sneak', false);
  await sleep(1200);
  const moved = b.entity.position.distanceTo(p0);
  console.log((moved > 2 ? 'PASS' : 'FAIL') + ' sneak-jump dashes the player :: moved ' + moved.toFixed(2) + ' blocks');
  b.setControlState('jump', true); await sleep(150); b.setControlState('jump', false); await sleep(400);
  b.quit(); setTimeout(() => process.exit(0), 300);
});
