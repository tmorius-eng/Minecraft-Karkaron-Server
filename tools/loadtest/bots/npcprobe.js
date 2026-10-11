const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl, name] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.once('spawn', async () => { await new Promise(r => setTimeout(r, 6000));
  execFileSync(ctl, ['cmd', `spawn ${name}`]); await new Promise(r => setTimeout(r, 6000));
  execFileSync(ctl, ['cmd', 'execute as @e[type=minecraft:mannequin] run data get entity @s Pos']);
  execFileSync(ctl, ['cmd', 'execute as @e[type=minecraft:mannequin] run data get entity @s BukkitValues']);
  await new Promise(r => setTimeout(r, 2000)); bot.quit(); });
bot.on('end', () => process.exit(0));
