const mineflayer = require('mineflayer');
const [name, secs] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const titles = [];
bot.on('title', (t) => titles.push(String(t)));
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.once('spawn', () => setTimeout(() => {
  const mobs = Object.values(bot.entities).filter(e => e !== bot.entity && e.type !== 'player' && e.position.distanceTo(bot.entity.position) < 45)
    .map(e => (e.name || e.type) + '@' + Math.round(e.position.distanceTo(bot.entity.position)));
  console.log(JSON.stringify({ pos: bot.entity.position, health: bot.health, mobs, titles }));
  bot.quit(); process.exit(0);
}, Number(secs) * 1000));
