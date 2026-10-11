const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const r = Math.floor(Math.random() * 100);
const mk = (n) => mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: n, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(res => setTimeout(res, ms));
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const A = mk('Sell' + r), B = mk('Buy' + r); const log = [];
for (const [n, b] of [['A', A], ['B', B]]) b._client.on('system_chat', (pk) => { const s = flat(pk.content); if (/Арилж|арилж|Зоос|зай/.test(s)) log.push(n + ': ' + s); });
let ready = 0;
const go = async () => { if (++ready < 2) return;
  await sleep(5000);
  for (const b of [A, B]) { if (b.currentWindow) await b.clickWindow(10, 0, 0).catch(() => {}); }
  await sleep(2500);
  for (const b of [A, B]) if (b.currentWindow) b.closeWindow(b.currentWindow);
  cmd('tp ' + B.username + ' ' + A.username); cmd('minecraft:clear ' + A.username + ' minecraft:diamond'); cmd('minecraft:give ' + A.username + ' minecraft:diamond 5');
  cmd('suld exp ' + B.username + ' 10'); await sleep(1500);
  const coinsB = () => 0;
  A.chat('/trade ' + B.username); await sleep(1000);
  B.chat('/trade accept ' + A.username); await sleep(1500);
  const wa = A.currentWindow, wb = B.currentWindow; log.push('windows: ' + !!wa + ' ' + !!wb);
  if (!wa || !wb) { console.log(log.join('\n')); process.exit(1); }
  const di = wa.slots.findIndex((it, i) => i >= 54 && it && it.name === 'diamond'); log.push('diamond slot ' + di);
  await A.clickWindow(di, 0, 1); await sleep(800);
  log.push('B sees offer slot5: ' + (B.currentWindow.slots[5] && B.currentWindow.slots[5].name + 'x' + B.currentWindow.slots[5].count));
  log.push('A inv diamonds after offer: ' + A.inventory.items().filter(i => i.name === 'diamond').reduce((n, i) => n + i.count, 0));
  await B.clickWindow(46, 0, 0); await sleep(500); // B offers 10 coins
  await A.clickWindow(45, 0, 0); await sleep(500); await B.clickWindow(45, 0, 0); await sleep(1500);
  log.push('A diamonds: ' + A.inventory.items().filter(i => i.name === 'diamond').reduce((n, i) => n + i.count, 0)
    + ' · B diamonds: ' + B.inventory.items().filter(i => i.name === 'diamond').reduce((n, i) => n + i.count, 0));
  // cancel path: B offers the diamonds back, then closes the window
  B.chat('/trade ' + A.username); await sleep(800); A.chat('/trade accept ' + B.username); await sleep(1500);
  const wb2 = B.currentWindow; const d2 = wb2 && wb2.slots.findIndex((it, i) => i >= 54 && it && it.name === 'diamond');
  if (wb2 && d2 > 0) { await B.clickWindow(d2, 0, 1); await sleep(800); B.closeWindow(wb2); await sleep(1500); }
  log.push('after cancel B diamonds: ' + B.inventory.items().filter(i => i.name === 'diamond').reduce((n, i) => n + i.count, 0));
  console.log(log.join('\n')); process.exit(0); };
A.once('spawn', go); B.once('spawn', go);
setTimeout(() => { console.log(log.join('\n')); process.exit(1); }, 80000);
