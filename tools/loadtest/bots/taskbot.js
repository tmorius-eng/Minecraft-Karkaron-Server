const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const name = 'Tk' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const log = [];
bot._client.on('system_chat', (pk) => { const s = flat(pk.content); if (/даалгавар|Даалгавар/.test(s)) log.push((pk.overlay ? 'BAR ' : '') + s); });
const flatName = (it) => { try { return it && it.customName ? flat(JSON.parse(it.customName)) : ''; } catch (e) { return String(it && it.customName); } };
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(1500);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  cmd('op ' + name); cmd('effect give ' + name + ' minecraft:resistance 600 4 true'); cmd('effect give ' + name + ' minecraft:slow_falling 60 0 true');
  cmd('tp ' + name + ' 640 100 40'); await sleep(4000);
  bot.chat('/tasks'); await sleep(1500);
  const w = bot.currentWindow; log.push('GUI ' + JSON.stringify(w && w.title).slice(0, 160));
  if (w) for (const s of [11, 13, 15]) log.push('slot' + s + ' ' + (w.slots[s] && w.slots[s].name) + ' ' + JSON.stringify(w.slots[s] && w.slots[s].customName).slice(0, 120));
  if (w) bot.closeWindow(w);
  for (let i = 0; i < 13 && !log.some(l => l.includes('биелэв')); i++) {
    bot.chat('/suld spawnmob'); await sleep(700);
    cmd('execute as ' + name + ' at @s run damage @e[type=wolf,limit=1,sort=nearest] 1000 minecraft:player_attack by ' + name); await sleep(700);
  }
  console.log(log.join('\n')); bot.quit(); process.exit(0);
});
setTimeout(() => { console.log(log.join('\n')); process.exit(1); }, 90000);
