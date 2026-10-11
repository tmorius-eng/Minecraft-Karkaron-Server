// Protocol test bot: node bot.js <name> <holdMs> [port]
// Prints one JSON line: did it spawn, was it kicked (and why), and every chat line it saw.
const mineflayer = require('mineflayer');
const [name, holdMs = '6000', port = '25570'] = process.argv.slice(2);
const out = { name, spawned: false, kicked: null, ended: null, chat: [] };
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11',
  auth: 'offline', hideErrors: true });
const done = () => { console.log(JSON.stringify(out)); process.exit(0); };
bot.on('messagestr', (m) => out.chat.push(m));
bot.once('spawn', () => { out.spawned = true; setTimeout(() => { bot.quit(); }, Number(holdMs)); });
bot.on('kicked', (reason) => { out.kicked = typeof reason === 'string' ? reason : JSON.stringify(reason); });
bot.on('end', (r) => { out.ended = String(r); setTimeout(done, 200); });
bot.on('error', (e) => { out.error = String(e && e.message || e); });
setTimeout(() => { out.timeout = true; done(); }, Number(holdMs) + 20000);
