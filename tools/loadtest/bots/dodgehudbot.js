// The HUD survives a dodge (owner report 2026-10-12): sprint, Shift + Space twice (the second is on cooldown) —
// the last action bar the client holds must be the HUD panel, not the «Сөхрөх» cooldown line.
//   node dodgehudbot.js <srvctl.sh> [port]
const mineflayer = require('mineflayer');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: 'D' + Math.floor(Math.random() * 900 + 100), version: '1.21.11', auth: 'offline', hideErrors: true });
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
const bars = [];
b._client.on('action_bar', (pk) => bars.push({ at: Date.now(), t: JSON.stringify(pk.text) }));
b._client.on('system_chat', (pk) => { if (pk.isActionBar || pk.overlay) bars.push({ at: Date.now(), t: JSON.stringify(pk.content) }); });
b.once('spawn', async () => {
  await sleep(3500);
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(2000);
  for (let i = 0; i < 2; i++) {
    b.setControlState('sprint', true); b.setControlState('forward', true); await sleep(400);
    b.setControlState('sneak', true); await sleep(150); b.setControlState('jump', true); await sleep(250);
    b.setControlState('jump', false); b.setControlState('sneak', false); await sleep(300);
  }
  b.clearControlStates();
  const k = bars.length; await sleep(600);
  const recent = bars.slice(Math.max(0, k - 6));
  const cooldown = bars.some(x => x.t.includes('Сөхрөх'));
  const lastIsHud = bars.length > 0 && !bars[bars.length - 1].t.includes('Сөхрөх');
  console.log((!cooldown ? 'PASS ' : 'FAIL ') + 'the dodge cooldown never replaces the HUD action bar :: ' + bars.length + ' bars, cooldown raw ' + cooldown);
  console.log((lastIsHud ? 'PASS ' : 'FAIL ') + 'the last action bar is the HUD :: ' + (recent.length ? recent[recent.length - 1].t.slice(0, 80) : 'none'));
  console.log(!cooldown && lastIsHud ? 'ALL 2 PASSED' : 'FAILED');
  b.quit(); process.exit(!cooldown && lastIsHud ? 0 : 1);
});
