// Grim measurement: each step is bracketed by a console "say GRIMMARK <step>" so the server log can be cut per step.
const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl, port, tag] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const name = (tag || 'Gm') + Math.floor(Math.random() * 900 + 100);
const b = mineflayer.createBot({ host: '127.0.0.1', port: +port, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
b._client.on('entity_velocity', pk => { if (b.entity && pk.entityId === b.entity.id && pk.velocity) b.entity.velocity.set(pk.velocity.x, pk.velocity.y, pk.velocity.z); });
const mark = async (s) => { cmd('suld grim'); await sleep(200); cmd('suld grim reset'); cmd('say GRIMMARK ' + name + ' ' + s); await sleep(300); };
const cast = async (spell, n = 3) => { for (let i = 0; i < n; i++) { cmd('skillsadmin cast ' + name + ' ' + spell); await sleep(3500); } };
b.once('spawn', async () => {
  await sleep(4000);
  for (let i = 0; i < 8 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(1000); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  cmd('suld exp ' + name + ' 9000000'); cmd('gamemode survival ' + name);
  cmd('effect give ' + name + ' minecraft:resistance 600 4 true'); cmd('effect give ' + name + ' minecraft:regeneration 600 4 true');
  cmd('tp ' + name + ' -16 90 -440'); await sleep(6000);
  await mark('idle'); await sleep(5000);
  await mark('walk'); b.setControlState('forward', true); await sleep(4000); b.setControlState('forward', false); await sleep(1000);
  await mark('sprintjump'); b.setControlState('forward', true); b.setControlState('sprint', true); b.setControlState('jump', true); await sleep(4000);
  b.clearControlStates(); await sleep(1500);
  await mark('dodge');
  for (let i = 0; i < 3; i++) { b.setControlState('sneak', true); await sleep(300); b.setControlState('jump', true); await sleep(250); b.setControlState('jump', false); b.setControlState('sneak', false); await sleep(2500); }
  await mark('leap'); await cast('DOVTLOKH_USRELT');
  await mark('disengage'); await cast('UKHRAKH_USRELT');
  await mark('charge'); await cast('KHURDAN_DOVTOLGOO');
  await mark('windhorse'); await cast('KHIIMORIIN_DAVKHILT');
  await mark('speedbuff'); await cast('SALKHINY_KHURD', 1); b.setControlState('forward', true); b.setControlState('sprint', true); await sleep(5000); b.clearControlStates(); await sleep(1500);
  await mark('skysky'); b.chat('/skills tree'); await sleep(6000); b.setControlState('sneak', true); await sleep(600); b.setControlState('sneak', false); await sleep(3000);
  await mark('end'); b.quit(); setTimeout(() => process.exit(0), 500);
});
setTimeout(() => process.exit(1), 180000);
