// Item persistence check: phase "equip" (obtain + equip, then quit) and phase "check" (rejoin after a restart).
const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const ctl = process.argv[2], phase = process.argv[3], name = 'Persist1';
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const logFrom = (n) => fs.readFileSync(LOG, 'utf8').split('\n').slice(n);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('messagestr', (m) => { if (/→|тайлагд|алдаа|SÜLD|холбогд|хүчингүй/.test(m)) console.log('CHAT ' + m); });
async function inspect(tag) {
  const n = fs.readFileSync(LOG, 'utf8').split('\n').length;
  cmd(`itemsadmin inspect ${name}`); await sleep(1500);
  const out = logFrom(n).filter(l => /тоноглол|HEAD|CHEST|LEGS|FEET|MAIN_HAND|OFF_HAND|ACCESSORY|RELIC|статистик|хохирол|цүнхэнд/.test(l)).map(l => l.replace(/^\[[\d:]+\] \[[^\]]+\]: /, ''));
  console.log('== ' + tag + '\n' + out.join('\n'));
  fs.writeFileSync(`/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/persist_${tag}.txt`, out.join('\n'));
}
async function holdById(id) {
  for (const it of bot.inventory.items()) {
    const nbt = JSON.stringify(it.componentMap ? [...it.componentMap.values()] : it.nbt || '');
    if (nbt.includes(id)) { await bot.equip(it, 'hand'); await sleep(500); return true; }
  }
  return false;
}
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1500); }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(3000);
  if (phase === 'equip') {
    cmd(`itemsadmin give ${name} item.talyn_tuvshin uncommon 1`);
    cmd(`itemsadmin give ${name} armor.esgii.chestplate common 1`);
    cmd(`itemsadmin give ${name} armor.esgii.helmet common 1`);
    await sleep(2000);
    for (const id of ['item.talyn_tuvshin', 'armor.esgii.chestplate', 'armor.esgii.helmet']) {
      console.log('hold ' + id + ' ' + await holdById(id));
      bot.chat('/item equip'); await sleep(1500);
    }
    await sleep(2500);
    await inspect('before');
    console.log('maxHealth attr ' + bot.entity.attributes?.['minecraft:max_health']?.value + ' health ' + bot.health);
  } else {
    await sleep(3000);
    await inspect('after');
  }
  bot.quit();
});
bot.on('end', () => process.exit(0));
setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 90000);
