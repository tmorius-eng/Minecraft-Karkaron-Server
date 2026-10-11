// node packbot.js <name> <port>: join via "localhost", report the resource pack offer, accept it, quit.
const mineflayer = require('mineflayer');
const [name, port] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: 'localhost', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const out = { name, chat: [] };
bot.on('messagestr', (m) => out.chat.push(m));
bot.on('resourcePack', (url, hash) => { out.packUrl = url; out.packHash = hash; try { bot.acceptResourcePack(); } catch (e) { out.acceptError = String(e); } });
bot.once('spawn', () => setTimeout(() => bot.quit(), 5000));
bot.on('end', () => { console.log(JSON.stringify(out)); process.exit(0); });
bot.on('error', (e) => { out.error = String(e.message || e); });
setTimeout(() => { out.timeout = true; console.log(JSON.stringify(out)); process.exit(0); }, 40000);
