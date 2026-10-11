// Scriptable test bot: node relicbot.js <name> <port> <lifetimeMs> '<plan json>'
// plan: [{"at":ms,"do":"activate","x":..,"y":..,"z":..} | {"at":ms,"do":"inv","tag":".."} |
//        {"at":ms,"do":"open","x":..,"y":..,"z":..} | {"at":ms,"do":"attack","target":"Name","ms":15000}]
// Emits one JSON line per event to stdout.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const [name, port, lifetime, planJson] = process.argv.slice(2);
const plan = JSON.parse(planJson || '[]');
const emit = (o) => console.log(JSON.stringify({ t: Date.now(), bot: name, ...o }));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline' });
const stars = () => bot.inventory.items().filter(i => i.name === 'nether_star').reduce((n, i) => n + i.count, 0);
bot.on('messagestr', (m) => { if (/Сүлд|сүлд|Гэрэгэ|тахилга|Тахилга|булааж|хуурамч|Хуурамч|салахгүй/i.test(m)) emit({ chat: m }); });
bot.on('death', () => emit({ event: 'died' }));
bot.on('kicked', (r) => emit({ event: 'kicked', reason: String(r).slice(0, 120) }));
bot.once('spawn', () => {
  emit({ event: 'spawned', stars: stars() });
  for (const step of plan) {
    setTimeout(async () => {
      try {
        if (step.do === 'activate') {
          const b = bot.blockAt(new Vec3(step.x, step.y, step.z));
          await bot.lookAt(new Vec3(step.x + 0.5, step.y + 0.5, step.z + 0.5));
          await bot.activateBlock(b);
          emit({ did: 'activate', block: b && b.name });
        } else if (step.do === 'inv') {
          emit({ did: 'inv', tag: step.tag, stars: stars() });
        } else if (step.do === 'open') {
          const b = bot.blockAt(new Vec3(step.x, step.y, step.z));
          const win = await bot.openContainer(b);
          setTimeout(() => {
            const inChest = win.containerItems().filter(i => i.name === 'nether_star').length;
            emit({ did: 'open', block: b.name, starsInContainer: inChest });
            win.close();
          }, 1500);
        } else if (step.do === 'attack') {
          const end = Date.now() + (step.ms || 15000);
          const loop = setInterval(() => {
            const target = bot.players[step.target] && bot.players[step.target].entity;
            if (!target || Date.now() > end) { clearInterval(loop); emit({ did: 'attack-end' }); return; }
            bot.lookAt(target.position.offset(0, 1.5, 0), true);
            bot.attack(target);
          }, 650);
        }
      } catch (e) { emit({ error: String(e && e.message || e) }); }
    }, step.at);
  }
  setTimeout(() => { emit({ event: 'quit', stars: stars() }); bot.quit(); setTimeout(() => process.exit(0), 500); }, Number(lifetime));
});
bot.on('error', (e) => emit({ error: String(e && e.message || e) }));
