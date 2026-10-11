const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl, name, phase] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.once('spawn', async () => {
  await sleep(5000);
  console.log('classMenuOpen', !!bot.currentWindow, bot.currentWindow ? bot.currentWindow.title : '');
  if (phase === 'first' && bot.currentWindow) { await bot.clickWindow(11, 0, 0).catch(() => {}); await sleep(2500); }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  if (phase === 'first') { cmd(`suld exp ${name} 3000`); await sleep(2000); }
  cmd(`skillsadmin inspect ${name}`); await sleep(1500);
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 40000);
