const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const name = 'Qg' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(1500);
  execFileSync(ctl, ['cmd', 'suld quest ' + name + ' 7']); await sleep(1000);
  bot.chat('/quest'); await sleep(1500);
  const w = bot.currentWindow;
  const t = w && (typeof w.title === 'string' ? w.title : JSON.stringify(w.title));
  console.log('TITLE', t);
  if (w) for (const s of [10, 11, 15, 16, 19, 24]) { const it = w.slots[s]; const nm = it && it.customName; console.log(s, it && it.name, nm ? String(nm).slice(0, 160) : ''); }
  bot.quit(); process.exit(0);
});
setTimeout(() => process.exit(1), 40000);
