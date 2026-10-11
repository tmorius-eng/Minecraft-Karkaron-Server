const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const name = 'Dm' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const displays = new Set(); let lastName = '';
bot._client.on('spawn_entity', (pk) => { if (bot.registry.entities[pk.type] && bot.registry.entities[pk.type].name === 'text_display') displays.add(pk.entityId); });
bot._client.on('entity_metadata', (pk) => { for (const m of pk.metadata) { if (displays.has(pk.entityId) && m.value && typeof m.value === 'object') { const t = flat(m.value); if (t) console.log('DISPLAY', t); } if (m.key === 2 && m.value) { const t = flat(m.value); if (t.includes('Чоно')) lastName = t; } } });
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(1500);
  cmd('op ' + name); cmd('effect give ' + name + ' minecraft:resistance 600 4 true'); cmd('effect give ' + name + ' minecraft:slow_falling 60 0 true');
  cmd('tp ' + name + ' 640 100 640'); await sleep(4000);
  bot.chat('/suld spawnmob'); await sleep(1500);
  cmd('execute positioned ' + bot.entity.position.x + ' ' + bot.entity.position.y + ' ' + bot.entity.position.z + ' run data get entity @e[type=wolf,limit=1,sort=nearest] Health');
  for (let i = 0; i < 2; i++) { cmd('execute as ' + name + ' at @s run damage @e[type=wolf,limit=1,sort=nearest] 5 minecraft:player_attack by ' + name); await sleep(1200); }
  console.log('NAME', lastName); bot.quit(); process.exit(0);
});
setTimeout(() => process.exit(1), 50000);
