const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const name = 'Kb' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const log = [];
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
bot._client.on('system_chat', (pk) => log.push((pk.isActionBar || pk.overlay ? 'BAR ' : '') + flat(pk.content)));
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(2000);
  cmd('op ' + name); cmd('suld exp ' + name + ' 2000000'); cmd('effect give ' + name + ' minecraft:resistance 600 4 true');
  cmd('tp ' + name + ' ' + (process.argv[3] || '160 90 0')); await sleep(4000);
  const sw = bot.inventory.slots.findIndex(i => i && /sword/.test(i.name)); if (sw >= 36) bot.setQuickBarSlot(sw - 36);
  for (let i = 0; i < 3; i++) {
    bot.chat('/suld spawnmob'); await sleep(1000);
    const near = Object.values(bot.entities).filter(e => e !== bot.entity && e.position.distanceTo(bot.entity.position) < 6);
    log.push('near: ' + near.map(e => e.name + '#' + e.id + '@' + e.position.distanceTo(bot.entity.position).toFixed(1)).join(','));
    for (let k = 0; k < 12; k++) {
      const w = near.find(e => e.name === 'wolf' && bot.entities[e.id]);
      if (!w) break;
      await bot.lookAt(w.position.offset(0, 0.4, 0), true); bot.attack(w); await sleep(650);
    }
  }
  if (process.argv[4]) { cmd('suld quest ' + name + ' 2'); await sleep(1000);
    for (let i = 0; i < 6; i++) { bot.chat('/suld spawnmob'); await sleep(800); cmd('execute as ' + name + ' at @s run damage @e[type=wolf,limit=1,sort=nearest] 1000 minecraft:player_attack by ' + name); await sleep(1200);
      for (let k = 0; k < 10; k++) { const it = Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(bot.entity.position) < 12)[0]; if (!it) break;
        await bot.lookAt(it.position, true); bot.setControlState('forward', true); await sleep(300); bot.setControlState('forward', false); }
      if (log.some(l => l.includes('Чонын Арьс  ('))) break; } }
  await sleep(1500); bot.chat('/quest'); await sleep(1500);
  console.log(log.join('\n')); bot.quit(); process.exit(0);
});
setTimeout(() => { console.log(log.join('\n')); process.exit(0); }, 90000);
