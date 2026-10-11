// node uibot.js <name> <port> <cmd|cmd...>: join, run commands; for each, record the opened window title and its item names.
const mineflayer = require('mineflayer');
const [name, port, cmds] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const out = { name, chat: [], windows: [], sidebar: null, tab: null, kicked: null };
const txt = (c) => { try { return typeof c === 'string' ? c : (c && c.toString ? c.toString() : JSON.stringify(c)); } catch (e) { return String(c); } };
bot.on('messagestr', (m) => { if (m.trim()) out.chat.push(m); });
bot.on('windowOpen', (w) => {
  let title = w.title; try { title = JSON.stringify(w.title).slice(0, 400); } catch (e) {}
  const items = w.slots.slice(0, w.inventoryStart).filter(Boolean).map(i => (i.customName || i.name) + 'x' + i.count);
  out.windows.push({ title, n: items.length, items: [...new Set(items)].slice(0, 12) });
});
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.once('spawn', async () => {
  const list = (cmds || '').split('|').filter(Boolean);
  await new Promise(r => setTimeout(r, 6000));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  for (const c of list) {
    out.chat.push('>>> ' + c);
    if (c.startsWith('click:') || c.startsWith('rclick:')) { const r = c.startsWith('r'); const slot = Number(c.split(':')[1]); if (bot.currentWindow) await bot.clickWindow(slot, r ? 1 : 0, 0).catch(e => out.chat.push('clickerr ' + e.message)); }
    else if (c.startsWith('console:')) { require('child_process').execFileSync('/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2ctl.sh', ['cmd', c.slice(8)]); }
    else bot.chat(c);
    await new Promise(r => setTimeout(r, 2000));
  }
  const sb = (bot.scoreboard && bot.scoreboard.sidebar) || Object.values(bot.scoreboards || {})[0];
  if (sb) out.sidebar = { title: txt(sb.title), items: sb.items.map(i => txt(i.displayName) + ' #' + i.value) };
  out.tab = { header: txt(bot.tablist && bot.tablist.header), footer: txt(bot.tablist && bot.tablist.footer),
    me: bot.players[name] ? txt(bot.players[name].displayName) : null };
  out.inventory = bot.inventory.slots.filter(Boolean).map(i => (i.customName ? 'named:' : '') + i.name + '@' + i.slot);
  bot.quit();
});
bot.on('end', () => { console.log(JSON.stringify(out, null, 1)); process.exit(0); });
bot.on('kicked', (r) => { out.kicked = txt(r); });
setTimeout(() => { out.timeout = true; console.log(JSON.stringify(out, null, 1)); process.exit(0); }, 150000);
