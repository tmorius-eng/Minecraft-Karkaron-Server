const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const ctl = process.argv[2], name = process.argv[3];
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const len = () => fs.readFileSync(LOG, 'utf8').split('\n').length;
async function inspect(tag) { const n = len(); cmd(`itemsadmin inspect ${name}`); await sleep(1500);
  console.log('== ' + tag); fs.readFileSync(LOG, 'utf8').split('\n').slice(n).filter(l => /статистик|хохирол|damage|MAIN_HAND|Хохирол/i.test(l)).forEach(l => console.log(l.replace(/^\[[\d:]+\] \[[^\]]+\]: /, ''))); }
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.once('spawn', async () => { await sleep(6000); await inspect('wounded'); cmd(`deathreset ${name}`); await sleep(3000); await inspect('healed'); bot.quit(); });
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 60000);
