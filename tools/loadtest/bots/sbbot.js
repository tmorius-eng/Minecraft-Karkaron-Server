const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: 'Tester3', version: '1.21.11', auth: 'offline', hideErrors: true });
const lines = {};
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type === 'compound' || c.value) { const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); if (v.extra) { const ex = v.extra.value?.value || v.extra.value || v.extra; (Array.isArray(ex) ? ex : []).forEach(e => s += flat(e.value ? e : { value: e })); } return s; } return JSON.stringify(c); };
bot._client.on('scoreboard_score', (p) => { lines[p.itemName] = { v: p.value, name: flat(p.display_name ?? p.displayName) }; });
bot.once('spawn', () => setTimeout(() => { console.log(Object.values(lines).sort((a, b) => b.v - a.v).map(l => l.v + ' | ' + l.name).join('\n')); bot.quit(); process.exit(0); }, 8000));
