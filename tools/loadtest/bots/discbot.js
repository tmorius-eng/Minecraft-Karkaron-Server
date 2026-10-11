const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const name = 'Dc' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const log = [];
bot._client.on('set_title_text', (pk) => log.push('TITLE ' + flat(pk.text)));
bot._client.on('system_chat', (pk) => { if (!(pk.isActionBar || pk.overlay)) { const s = flat(pk.content); if (/нутаг|Эрэл|EXP/.test(s)) log.push(s); } });
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(1500);
  cmd('effect give ' + name + ' minecraft:slow_falling 120 0 true');
  for (const [tag, pos] of [['gobi', '0 140 600'], ['city', '-15 80 -45'], ['gobi again', '0 140 600'], ['altai', '-600 160 0']]) {
    log.push('### ' + tag); cmd('tp ' + name + ' ' + pos); await sleep(5000);
  }
  console.log(log.join('\n')); bot.quit(); process.exit(0);
});
setTimeout(() => { console.log(log.join('\n')); process.exit(0); }, 60000);
