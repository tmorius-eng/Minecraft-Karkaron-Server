const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: 'Visitor', version: '1.21.11', auth: 'offline', hideErrors: true });
const out = { npcs: [], windows: [], chat: [] };
bot.on('messagestr', (m) => { if (m.trim()) out.chat.push(m); });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('windowOpen', (w) => { let t = ''; try { t = JSON.stringify(w.title).match(/"value":"([^"]{3,})"/g).slice(-1)[0]; } catch (e) {} out.windows.push(t); });
const nm = (e) => { try { const m = e.metadata; return JSON.stringify(m).match(/"text":\{"type":"string","value":"([^"]+)"/g) || m.filter(x => x && x.toString && /\w/.test(String(x))).slice(0, 3).map(String); } catch (x) { return '?'; } };
bot.once('spawn', async () => {
  await new Promise(r => setTimeout(r, 7000));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  const man = Object.values(bot.entities).filter(e => e.name === 'mannequin');
  for (const e of man) out.npcs.push({ at: [e.position.x, e.position.y, e.position.z].map(Math.round), dist: Math.round(e.position.distanceTo(bot.entity.position)), name: nm(e) });
  const near = man.sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position));
  for (const e of near.slice(0, 3)) {
    await bot.lookAt(e.position.offset(0, 1.6, 0));
    if (e.position.distanceTo(bot.entity.position) > 4) { require('child_process').execFileSync(process.argv[2], ['cmd', `tp Visitor ${e.position.x + 1.5} ${e.position.y} ${e.position.z + 1.5}`]); await new Promise(r => setTimeout(r, 1500)); await bot.lookAt(e.position.offset(0, 1.6, 0)); }
    bot.activateEntity(e);
    await new Promise(r => setTimeout(r, 1500));
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  }
  bot.quit();
});
bot.on('end', () => { console.log(JSON.stringify(out, null, 1)); process.exit(0); });
setTimeout(() => { console.log(JSON.stringify(out, null, 1)); process.exit(0); }, 60000);
