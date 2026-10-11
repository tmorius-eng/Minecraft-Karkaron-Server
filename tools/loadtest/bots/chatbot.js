const mineflayer = require('mineflayer');
const mk = (n) => mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: n, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const a = mk('Talker' + Math.floor(Math.random() * 100)), b = mk('Listener' + Math.floor(Math.random() * 100));
const seen = [], notices = [];
b._client.on('player_chat', (pk) => seen.push(String(pk.plainMessage || flat(pk.unsignedChatContent) || '')));
b._client.on('system_chat', (pk) => { const s = flat(pk.content); if (s.includes('»')) seen.push('SYS ' + s); });
a._client.on('system_chat', (pk) => { const s = flat(pk.content); if (/хүлээгээрэй|дахин бүү|хориотой/.test(s)) notices.push(s); });
let ready = 0; const go = async () => { if (++ready < 2) return;
  await sleep(6000);
  for (const m of ['сайн байна уу', 'сайн байна уу', 'play.otherserver.net ирээрэй', 'ХҮМҮҮС ЭНД ИРЭЭРЭЙ ОДОО', 'нэг', 'хоёр', 'гурав']) { a.chat(m); await sleep(250); }
  await sleep(2000);
  console.log('LISTENER SAW:\n' + seen.join('\n')); console.log('TALKER NOTICES:\n' + notices.join('\n'));
  process.exit(0); };
a.once('spawn', go); b.once('spawn', go);
setTimeout(() => process.exit(1), 60000);
