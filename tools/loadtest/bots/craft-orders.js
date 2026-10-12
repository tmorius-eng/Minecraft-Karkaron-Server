// The Дархан's order board on the dev server (docs/CRAFTING.md), two offline test bots:
//   node craft-orders.js <srvctl.sh> <logfile> [port]
// P posts an order (the fee leaves P), C fills it from the board (C pays materials + recipe coins, gets the fee, P
// gets the item); P posts again and withdraws it (the fee comes back); P posts, leaves, C fills, and P finds the item
// in the mailbox on the next join.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, logfile, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };

function join(name) {
  return new Promise((res) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    b.chatLog = [];
    b.on('messagestr', m => b.chatLog.push(m));
    b.on('error', () => {});
    b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
    b.once('spawn', async () => {
      await sleep(3500);
      for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
      if (b.currentWindow) {
        await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600);
        await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200);
      }
      res(b);
    });
  });
}

async function coins(b) {
  const k = b.chatLog.length;
  b.chat('/balance'); await sleep(1200);
  const line = b.chatLog.slice(k).find(l => l.includes('зоос:')) || '';
  const m = line.match(/зоос:\s*(\d+)/);
  return m ? Number(m[1]) : NaN;
}

async function boardClick(b, slot) { // open /order, click a slot, confirm
  b.chat('/order'); await sleep(1800);
  if (!b.currentWindow) return false;
  await b.clickWindow(slot, 0, 0).catch(() => {}); await sleep(1200);
  if (!b.currentWindow) return false;
  await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(2000);
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  return true;
}

(async () => {
  const pn = 'P' + Math.floor(Math.random() * 900 + 100), cn = 'C' + Math.floor(Math.random() * 900 + 100);
  let P = await join(pn);
  const C = await join(cn);
  for (const n of [pn, cn]) { cmd(`suld exp ${n} 6000000`); cmd(`suld coins ${n} 2000`); }
  cmd(`itemsadmin give ${cn} item.chonon_arisan`); cmd(`itemsadmin give ${cn} item.tumriin_kheltarkhii`);
  await sleep(2500);

  // 1. post, the fee leaves the poster
  let p0 = await coins(P);
  let k = P.chatLog.length;
  P.chat('/order post esgii_boots 100'); await sleep(2500);
  check(P.chatLog.slice(k).some(l => l.includes('Захиалга өглөө')), 'an order is posted', P.chatLog.slice(k).join(' | '));
  let p1 = await coins(P);
  check(p0 - p1 === 100, 'the fee is held from the poster', p0 + ' -> ' + p1);

  // 2. the crafter fills it from the board
  const c0 = await coins(C);
  k = C.chatLog.length;
  const pk = P.chatLog.length;
  await boardClick(C, 0);
  check(C.chatLog.slice(k).some(l => l.includes('Захиалга биелүүллээ')), 'the crafter fills it', C.chatLog.slice(k).join(' | '));
  const c1 = await coins(C);
  check(c1 - c0 === 100 - 10, 'the crafter gets the fee and pays the recipe\'s coins (10)', c0 + ' -> ' + c1);
  check(P.chatLog.slice(pk).some(l => l.includes('Таны захиалга биеллээ')), 'the poster gets the item', P.chatLog.slice(pk).join(' | '));

  // 3. one's own order cannot be filled, only withdrawn: the fee comes back
  P.chat('/order post esgii_boots 50'); await sleep(2500);
  p0 = await coins(P);
  k = P.chatLog.length;
  await boardClick(P, 0);
  check(P.chatLog.slice(k).some(l => l.includes('буцаалаа') || l.includes('буцаж ирлээ')), 'the poster withdraws their own order', P.chatLog.slice(k).join(' | '));
  p1 = await coins(P);
  check(p1 - p0 === 50, 'the fee comes back', p0 + ' -> ' + p1);

  // 4. a poster who is away gets the item by mailbox
  P.chat('/order post esgii_boots 40'); await sleep(2500);
  P.quit(); await sleep(3000);
  k = C.chatLog.length;
  await boardClick(C, 0);
  check(C.chatLog.slice(k).some(l => l.includes('Захиалга биелүүллээ')), 'an order of someone offline is filled', C.chatLog.slice(k).join(' | '));
  P = await join(pn); await sleep(4000);
  check(P.chatLog.some(l => l.includes('буцааж өглөө') || l.includes('эд зүйл')), 'the poster finds it on the next join', P.chatLog.slice(-4).join(' | '));

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  P.quit(); C.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})();
