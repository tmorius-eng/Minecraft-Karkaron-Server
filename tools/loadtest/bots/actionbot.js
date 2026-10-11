// /item sell|bind|salvage|destroy|craft live: each needs "confirm"; coins and inventory are read back.
const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2], name = 'Act' + Math.floor(Math.random() * 1000);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('messagestr', (m) => { if (!/ирлээ|Тавтай|Даалгавар|HUD|^\s*$/.test(m)) console.log('CHAT ' + m.slice(0, 160)); });
const has = (id) => bot.inventory.items().filter(it => JSON.stringify(it.componentMap ? [...it.componentMap.values()] : '').includes(id)).reduce((a, it) => a + it.count, 0);
async function hold(id) { for (const it of bot.inventory.items()) if (JSON.stringify(it.componentMap ? [...it.componentMap.values()] : '').includes(id)) { await bot.equip(it, 'hand'); await sleep(400); return true; } return false; }
async function step(label, id, action) {
  console.log('--- ' + label + ' (has ' + id + ': ' + has(id) + ')');
  await hold(id); bot.chat('/item ' + action); await sleep(1000); bot.chat('/item ' + action + ' confirm'); await sleep(1500);
  console.log('    after: ' + id + ' x' + has(id));
}
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1500); }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(3000);
  bot.chat('/balance'); await sleep(800);
  cmd(`itemsadmin give ${name} weapon.talyn_ild rare 5`); cmd(`itemsadmin give ${name} armor.esgii.chestplate uncommon 1`);
  cmd(`itemsadmin give ${name} armor.esgii.helmet common 1`); cmd(`itemsadmin give ${name} jewel.mungun_bugj uncommon 10`);
  cmd(`itemsadmin give ${name} item.chonon_arisan`); await sleep(2000);
  await step('sell rare saber', 'weapon.talyn_ild', 'sell'); bot.chat('/balance'); await sleep(800);
  await step('bind ring', 'jewel.mungun_bugj', 'bind'); await hold('jewel.mungun_bugj'); bot.chat('/item inspect'); await sleep(1200);
  await step('salvage chestplate', 'armor.esgii.chestplate', 'salvage');
  console.log('    materials: kheltarkhii x' + has('item.tumriin_kheltarkhii') + ' altan_toos x' + has('item.altan_toos'));
  await step('destroy helmet', 'armor.esgii.helmet', 'destroy');
  console.log('--- craft recipe.esgii_dugaj (wolf pelts x' + has('item.chonon_arisan') + ')');
  bot.chat('/item craft recipe.esgii_dugaj'); await sleep(1500);
  console.log('    helmet x' + has('armor.esgii.helmet') + ' pelts x' + has('item.chonon_arisan')); bot.chat('/balance'); await sleep(800);
  bot.quit();
});
bot.on('end', () => process.exit(0));
setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 80000);
