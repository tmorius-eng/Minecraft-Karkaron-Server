const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: 'Prof' + Math.floor(Math.random() * 1000), version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(2500);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  bot.chat('/profile'); await sleep(1500);
  const w = bot.currentWindow;
  console.log(w ? [13, 22, 28, 29, 30, 31, 32, 33, 34, 36].map(s => s + ':' + (w.slots[s] ? w.slots[s].name : '-')).join(' ') : 'no window');
  bot.quit(); process.exit(0);
});
setTimeout(() => process.exit(1), 40000);
