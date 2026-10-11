// Live check of Бөх барилдаан: challenge/accept, push out of the ring, no damage, win counted, stats.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = []; const check = (n, ok, d) => { results.push(!!ok); console.error((ok ? 'PASS ' : 'FAIL ') + n + (d ? ' :: ' + d : '')); };
function join(name) {
  return new Promise((res) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: +port, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    b.log = []; b.on('messagestr', (m) => b.log.push(m)); b.on('error', () => {});
    b.once('spawn', async () => {
      await sleep(3500);
      for (let i = 0; i < 8 && !b.currentWindow; i++) await sleep(500);
      if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(1000); }
      if (b.currentWindow) b.closeWindow(b.currentWindow);
      res(b);
    });
  });
}
(async () => {
  const t = 'Bk' + Math.floor(Math.random() * 900 + 100);
  const A = await join(t + 'a'), B = await join(t + 'b');
  // scratch server only: a flat 13x13 platform high in the air (falling off it is also leaving the ring)
  cmd('forceload add 32 32'); await sleep(1500);
  cmd('fill 34 100 34 46 100 46 minecraft:grass_block'); cmd('fill 34 101 34 46 104 46 minecraft:air'); await sleep(800);
  cmd(`minecraft:tp ${t}a 40.5 101 40.5`); cmd(`minecraft:tp ${t}b 43.5 101 40.5`); await sleep(2500);
  A.log.length = 0;
  A.chat(`/barildaan ${t}b`); await sleep(1200);
  const ok = A.log.some(l => /урилаа/.test(l));
  if (!ok) console.error(A.log.slice(-2));
  check('a challenge is sent on open ground', ok);
  if (!ok) process.exit(1);
  B.chat('/barildaan accept'); await sleep(4000); // countdown 3 s
  console.error('A:', A.log.slice(-3).join(' / '), ' B:', B.log.slice(-3).join(' / '));
  const e0 = A.nearestEntity(en => en.type === 'player' && en.username === t + 'b'); console.error('distance', e0 && A.entity.position.distanceTo(e0.position));
  const hp0 = B.health;
  let vel = 0; B._client.on('entity_velocity', (pk) => { if (pk.entityId === B.entity.id) { vel++; if (pk.velocity) B.entity.velocity.set(pk.velocity.x, pk.velocity.y, pk.velocity.z); if (vel < 3) console.error('B velocity packet', JSON.stringify(pk)); } });
  A.setControlState('sprint', false);
  let won = false;
  for (let k = 0; k < 40 && !won; k++) {
    const e = A.nearestEntity(en => en.type === 'player' && en.username === t + 'b');
    if (e) {
      await A.lookAt(e.position.offset(0, 1.6, 0), true);
      const d = A.entity.position.distanceTo(e.position);
      A.setControlState('forward', d > 2.2);
      if (d <= 3) A.attack(e);
      if (k % 5 === 0) console.error('k', k, 'd', d.toFixed(2), 'B at', e.position.x.toFixed(1), e.position.z.toFixed(1));
    }
    await sleep(700);
    won = A.log.some(l => /давлаа/.test(l));
  }
  A.clearControlStates();
  check('pushing the opponent out of the ring wins', won, A.log.filter(l => /давлаа|тэнцлээ/.test(l)).join(' | '));
  check('the opponent took no damage', B.health >= hp0 && B.health > 19, 'health ' + hp0 + ' -> ' + B.health);
  A.log.length = 0; A.chat('/barildaan stats'); await sleep(1000);
  check('the win is counted', A.log.some(l => /1 ялалт/.test(l)), A.log[0]);
  // a hit outside a bout by a wrestler is not touched (only bouts are special): B attacks A now, no bout
  console.error(`${results.filter(Boolean).length}/${results.length} PASS`);
  A.quit(); B.quit(); setTimeout(() => process.exit(0), 500);
})();
