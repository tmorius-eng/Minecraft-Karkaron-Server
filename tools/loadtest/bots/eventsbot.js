// The world events (content/events.json, plugin/worldevent/WorldEventService): every event flagged "automatic" starts
// from the console (`suldevent start <id>`) with its own boss bar, the bar leaves on `suldevent stop`, and the bundled
// content has at least four of them. Dev server, offline dev flag:
//   node eventsbot.js <srvctl.sh> [port]
const mineflayer = require('mineflayer');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'E' + Math.floor(Math.random() * 90000 + 10000);
const content = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', '..', 'suld-plugin', 'src', 'main', 'resources', 'content', 'events.json'), 'utf8'));
const automatic = content.worldEvents.filter(e => e.automatic);
async function waitFor(pred, ms = 8000) { for (let t = 0; t < ms; t += 250) { if (pred()) return true; await sleep(250); } return pred(); }

function join() {
  return new Promise((res) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    b.bars = new Map(); // uuid -> last title text
    const title = (bar) => { try { return bar.title.toString(); } catch (e) { return String(bar.title); } };
    b.on('bossBarCreated', bar => b.bars.set(String(bar.entityUUID), title(bar)));
    b.on('bossBarUpdated', bar => b.bars.set(String(bar.entityUUID), title(bar)));
    b.on('bossBarDeleted', bar => b.bars.delete(String(bar.entityUUID)));
    b.on('error', () => {});
    b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
    b.once('spawn', async () => {
      await sleep(3500);
      for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
      if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
      if (b.currentWindow) b.closeWindow(b.currentWindow);
      await sleep(1000);
      res(b);
    });
  });
}
const barWith = (b, s) => [...b.bars.values()].some(t => t.includes(s));

(async () => {
  check(automatic.length >= 4 && automatic.some(e => e.id === 'event.chonyn_dovtolgoo'), 'the bundled content has four or more automatic events', automatic.map(e => e.name).join(', '));
  const b = await join();
  cmd('suldevent stop'); await sleep(1500);
  for (const e of automatic) {
    cmd('suldevent start ' + e.id);
    const up = await waitFor(() => barWith(b, e.name));
    check(up, 'suldevent start ' + e.id + ' shows the event\'s bar', up ? [...b.bars.values()].find(t => t.includes(e.name)).slice(0, 80) : 'bars: ' + [...b.bars.values()].join(' | ').slice(0, 120));
    cmd('suldevent stop');
    const down = await waitFor(() => !barWith(b, e.name));
    check(down, 'suldevent stop takes it down again', down ? 'gone' : 'still shown');
    await sleep(1000);
  }
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit();
  process.exit(results.every(Boolean) ? 0 : 1);
})().catch(e => { console.log('FAIL the bot itself :: ' + e.message); process.exit(1); });
