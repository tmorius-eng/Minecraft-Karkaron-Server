const mineflayer = require('mineflayer');
const [name] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const msgs = []; bot.on('messagestr', m => msgs.push(m));
bot.once('spawn', async () => {
  await sleep(4000);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  const swords = bot.inventory.slots.filter(it => it && it.name === 'iron_sword' && JSON.stringify(it).includes('talyn')).length;
  bot.chat('/balance'); await sleep(800);
  console.log('after: talyn swords', swords, 'balance', msgs.filter(m => /₮/.test(m)).slice(-1)[0]);
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 40000);
