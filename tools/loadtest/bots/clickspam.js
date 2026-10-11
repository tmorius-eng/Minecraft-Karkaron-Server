const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl, port] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const name = 'Ck' + Math.floor(Math.random() * 900 + 100);
const b = mineflayer.createBot({ host: '127.0.0.1', port: +port, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
b.once('spawn', async () => {
  await sleep(4000);
  for (let i = 0; i < 8 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(1000); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  b.chat('/cosmetics'); await sleep(1500);
  const w = b.currentWindow; console.log('window', !!w);
  // a ChestStealer-style burst: 40 raw clicks in one go, no waiting for the server
  b._client.on('error', e => console.log('client error', e.message)); const Item = require('prismarine-item')(b.registry); let sent = 0; for (let i = 0; i < 40; i++) { try { b._client.write('window_click', { windowId: w.id, stateId: -1, slot: 17, mouseButton: 0, mode: 0, changedSlots: [], cursorItem: Item.toNotch(null) }); sent++; } catch (e) { console.log('write failed', e.message); break; } } console.log('sent', sent);
  await sleep(2500);
  b.quit(); setTimeout(() => process.exit(0), 300);
});
setTimeout(() => process.exit(1), 40000);
