// Grim compatibility run 2: every SÜLD movement the first run could not trigger. Counts come from /suld grim per step.
const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl, port, tag] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const name = (tag || 'Gx') + Math.floor(Math.random() * 900 + 100);
const b = mineflayer.createBot({ host: '127.0.0.1', port: +port, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const chat = []; b._client.on('system_chat', pk => { const s = flat(pk.content); if (s.trim()) chat.push(s); });
b._client.on('entity_velocity', pk => { if (b.entity && pk.entityId === b.entity.id && pk.velocity) b.entity.velocity.set(pk.velocity.x, pk.velocity.y, pk.velocity.z); });
const mark = async (s) => { cmd('suld grim'); await sleep(200); cmd('suld grim reset'); cmd('say GRIMMARK ' + name + ' ' + s); console.log('-- ' + s + (chat.length ? ' | chat: ' + chat.splice(0).slice(-3).join(' / ').slice(0, 200) : '')); await sleep(300); };
const cast = async (spell, n = 3, gap = 3500) => { for (let i = 0; i < n; i++) { cmd('skillsadmin cast ' + name + ' ' + spell); await sleep(gap); } };
const sneakJump = async () => { b.setControlState('sneak', true); b._client.write('player_input', { inputs: { shift: true } }); await sleep(400); b.setControlState('jump', true); await sleep(300); b.setControlState('jump', false); await sleep(200); b.setControlState('sneak', false); };
b.once('spawn', async () => {
  await sleep(4000);
  for (let i = 0; i < 8 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(14, 0, 0).catch(() => {}); await sleep(1000); } // Хүлэгчин (dodge cooldown 2 s, charge, windHorse)
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  cmd('suld exp ' + name + ' 9000000'); cmd('gamemode survival ' + name);
  cmd('effect give ' + name + ' minecraft:resistance 900 4 true'); cmd('effect give ' + name + ' minecraft:regeneration 900 4 true');
  cmd('tp ' + name + ' -16 90 -440'); await sleep(7000);
  await mark('idle'); await sleep(4000);
  await mark('dodge'); for (let i = 0; i < 3; i++) { await sneakJump(); await sleep(2600); }
  await mark('lockon'); cmd('suld spawnmob ' + name + ' mob.goviin_chono 1'); await sleep(2000);
  const wolf = Object.values(b.entities).filter(e => e.name === 'wolf').sort((x, y) => x.position.distanceTo(b.entity.position) - y.position.distanceTo(b.entity.position))[0];
  if (wolf) await b.lookAt(wolf.position.offset(0, 0.5, 0), true);
  const y0 = b.entity.yaw; for (let i = 1; i <= 48; i++) { await b.look(y0 + i * Math.PI / 12, b.entity.pitch, true); await sleep(50); } // two full turns: raw yaw now ~720° off
  await sleep(300); b.setQuickBarSlot(0); await sleep(300);
  b._client.write('block_dig', { status: 4, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); await sleep(3000);
  console.log('   wolf:', !!wolf, 'yaw', b.entity.yaw.toFixed(2), 'held', b.heldItem && b.heldItem.name);
  b._client.write('block_dig', { status: 4, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); await sleep(1000);
  cmd('kill @e[type=minecraft:wolf]');
  await mark('end'); b.quit(); setTimeout(() => process.exit(0), 500);
});
setTimeout(() => process.exit(1), 240000);
