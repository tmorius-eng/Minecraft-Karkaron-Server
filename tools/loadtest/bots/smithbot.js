const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const name = 'Sm' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const log = []; const names = {};
bot._client.on('system_chat', (pk) => { const s = flat(pk.content); if (/Зэрэг|хэрэгтэй|Зоос|Ховор/.test(s)) log.push(s); });
bot._client.on('entity_metadata', (pk) => { for (const m of pk.metadata) if (m.key === 2 && m.value) { const t = flat(m.value); if (t) names[pk.entityId] = t; } });
bot.on('windowOpen', (w) => log.push('windowOpen ' + JSON.stringify(w.title).slice(60, 140)));
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(1500);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  cmd('op ' + name); cmd('suld exp ' + name + ' 20000'); cmd('effect give ' + name + ' minecraft:resistance 600 4 true'); cmd('effect give ' + name + ' minecraft:slow_falling 60 0 true');
  cmd('tp ' + name + ' 660 100 40'); await sleep(4000);
  for (let i = 0; i < 20; i++) {
    bot.chat('/suld spawnmob'); await sleep(500);
    cmd('execute as ' + name + ' at @s run damage @e[type=wolf,limit=1,sort=nearest] 1000 minecraft:player_attack by ' + name); await sleep(900);
    const saber = bot.inventory.items().some(it => it.name === 'iron_sword' && JSON.stringify(it.customName || '').includes('Талын'));
    const pelts = bot.inventory.items().filter(it => it.name === 'leather').reduce((n, it) => n + it.count, 0);
    if (saber && pelts >= 2) break;
  }
  const saberSlot = bot.inventory.items().find(it => it.name === 'iron_sword' && JSON.stringify(it.customName || '').includes('Талын'));
  log.push('saber? ' + !!saberSlot + ' pelts ' + bot.inventory.items().filter(it => it.name === 'leather').reduce((n, it) => n + it.count, 0));
  if (!saberSlot) { console.log(log.join('\n')); process.exit(0); }
  await bot.equip(saberSlot, 'hand'); await sleep(500);
  cmd('suld exp ' + name + ' 10'); cmd('suld coins ' + name + ' 500');
  cmd('tp ' + name + ' -15 74 -45'); await sleep(4000);
  const smith = Object.values(bot.entities).find(e => (names[e.id] || '').includes('Дархан'));
  log.push('smith ' + (smith ? smith.name + ' @' + smith.position.distanceTo(bot.entity.position).toFixed(0) : 'none') + ' names=' + Object.values(names).filter(n => !n.includes('Чоно')).slice(0, 8).join('|'));
  if (smith) {
    cmd('tp ' + name + ' ' + (smith.position.x + 1.5) + ' ' + smith.position.y + ' ' + (smith.position.z + 1.5)); await sleep(2000);
    const sm2 = bot.entities[smith.id] || smith; log.push('dist ' + sm2.position.distanceTo(bot.entity.position).toFixed(1));
    await bot.lookAt(sm2.position.offset(0, 1.6, 0)); bot.activateEntity(sm2); await sleep(2000);
    const w = bot.currentWindow; log.push('GUI ' + (w ? JSON.stringify(w.title).slice(60, 150) : 'none'));
    if (w) { const up = [15, 16].find(s => w.slots[s] && w.slots[s].name === 'smithing_table'); log.push('upgrade slot ' + up);
      if (up) { await bot.clickWindow(up, 0, 0); await sleep(1500); } }
    const after = bot.heldItem; log.push('held now: ' + JSON.stringify(after && after.customName).slice(0, 200));
  }
  console.log(log.join('\n')); bot.quit(); process.exit(0);
});
setTimeout(() => { console.log(log.join('\n')); process.exit(1); }, 120000);
