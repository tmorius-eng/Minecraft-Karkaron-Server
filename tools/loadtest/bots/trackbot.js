const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const name = 'Tr' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
let last = '';
bot._client.on('boss_bar', (pk) => { if (pk.title) { const t = flat(pk.title); if (t.includes('✦')) last = t; } });
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(3000);
  console.log('city, facing default:', last);
  await bot.look(-Math.PI / 2, 0, true); await sleep(1500); // face east (mineflayer yaw -pi/2 = east)
  console.log('city, facing east:', last);
  cmd('effect give ' + name + ' minecraft:slow_falling 60 0 true'); cmd('tp ' + name + ' 600 150 30'); await sleep(4000);
  console.log('in kherlen:', last);
  cmd('suld quest ' + name + ' 6'); await sleep(1500);
  console.log('gobi road chapter:', last);
  bot.chat('/quest track'); await sleep(1500);
  console.log('after toggle (stale ok):', last);
  bot.quit(); process.exit(0);
});
setTimeout(() => process.exit(1), 60000);
