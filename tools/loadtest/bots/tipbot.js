const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: 'Tipper', version: '1.21.11', auth: 'offline', hideErrors: true });
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
bot._client.on('system_chat', (pk) => { const s = flat(pk.content); if (s.includes('Зөвлөгөө')) { console.log(JSON.stringify(pk.content).slice(0, 1500)); process.exit(0); } });
setTimeout(() => { console.log('no tip'); process.exit(1); }, 140000);
