const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const name = 'Dn' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const log = [];
bot._client.on('system_chat', (pk) => { if (!(pk.isActionBar || pk.overlay)) { const s = flat(pk.content); if (!/SkinsRestorer|Дүрс|Тавтай|ирлээ|Ховор/.test(s) && s.trim()) log.push(s); } });
bot._client.on('set_title_text', (pk) => log.push('TITLE ' + flat(pk.text)));
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(1500);
  cmd('suld exp ' + name + ' 300000'); cmd('effect give ' + name + ' minecraft:resistance 600 4 true'); cmd('effect give ' + name + ' minecraft:slow_falling 60 0 true');
  cmd('suld quest ' + name + ' 9'); cmd('tp ' + name + ' 40 140 700'); await sleep(7000);
  log.push('### list'); bot.chat('/dungeon list'); await sleep(1000);
  log.push('### enter'); bot.chat('/dungeon enter govi_bulsh'); await sleep(5000);
  for (let w = 0; w < 6; w++) { cmd('minecraft:kill @e[type=cave_spider]'); cmd('minecraft:kill @e[type=husk,tag=!suld_boss_probe]'); await sleep(5000); }
  log.push('### boss'); cmd('minecraft:kill @e[type=husk]'); await sleep(4000);
  console.log(log.join('\n')); bot.quit(); process.exit(0);
});
setTimeout(() => { console.log(log.join('\n')); process.exit(0); }, 120000);
