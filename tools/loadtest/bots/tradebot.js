// /trade between two players (plugin/trade/TradeService, PERS-004 COINS -> ITEMS), dev server, offline dev flag:
//   node tradebot.js <srvctl.sh> [port]
// A offers a diamond, B offers 10 coins, both confirm: «Арилжаа амжилттай!», B holds the diamond, the coins moved in
// the database (A +10, B -10). Then the two ways out: B closes the window with a diamond on the table and gets it
// back («цонх хаагдсан»); A quits with a diamond on the table, B is told, and A holds it again after rejoining.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const psql = (q) => execFileSync('runuser', ['-u', 'postgres', '--', 'psql', '-d', process.env.SULD_DB || 'suldit', '-At', '-c', q], { cwd: '/tmp' }).toString().trim();
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const tag = Math.floor(Math.random() * 90000 + 10000);
const nameA = 'T' + tag + 'a', nameB = 'T' + tag + 'b';
const coins = (n) => Number(psql(`select currency from suld_profiles where name='${n}'`) || 0);

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
      if (b.currentWindow) { // class pick, then the confirm page
        await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600);
        await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500);
      }
      if (b.currentWindow) b.closeWindow(b.currentWindow);
      await sleep(1000);
      res(b);
    });
  });
}
const said = (b, s) => b.chatLog.some(l => l.includes(s));
const diamonds = (b) => b.inventory.items().filter(i => i.name === 'diamond').reduce((n, i) => n + i.count, 0);
const tradeWindow = (b) => b.currentWindow && JSON.stringify(b.currentWindow.title).includes('Арилжаа');
const tableHas = (b, item) => !!b.currentWindow && b.currentWindow.slots.slice(0, 45).some(s => s && s.name === item);
async function waitFor(pred, ms = 8000) { for (let t = 0; t < ms; t += 250) { if (pred()) return true; await sleep(250); } return pred(); }
async function offer(b, item) { // a shift-click in the own bag puts the stack on the table
  const w = b.currentWindow;
  const ws = w.slots.findIndex((s, n) => n >= w.inventoryStart && s && s.name === item);
  if (ws < 0) return false;
  await b.clickWindow(ws, 0, 1).catch(() => {}); await sleep(800);
  return tableHas(b, item);
}
async function open(from, to) { // from asks, to accepts; both windows must be the trade
  from.chatLog.length = 0; to.chatLog.length = 0;
  from.chat('/trade ' + to.username); await sleep(1500);
  to.chat('/trade accept ' + from.username);
  return waitFor(() => tradeWindow(from) && tradeWindow(to));
}

(async () => {
  const A = await join(nameA), B = await join(nameB);
  cmd(`minecraft:tp ${nameB} ${nameA}`); await sleep(1500);
  cmd(`minecraft:give ${nameA} minecraft:diamond 1`); cmd(`suld coins ${nameB} 500`); await sleep(2500);
  const a0 = coins(nameA), b0 = coins(nameB);
  check(diamonds(A) === 1 && b0 >= 500, 'A holds a diamond and B has coins', `A diamonds ${diamonds(A)}, B coins ${b0}`);

  // 1. a completed trade: A's diamond for B's 10 coins
  A.chat('/trade ' + nameB); await sleep(1500);
  check(said(B, nameA + ' арилжаа санал болгож байна'), 'B is told about the request', B.chatLog.filter(l => l.includes('арилжаа')).join(' | ').slice(0, 120));
  B.chat('/trade accept ' + nameA);
  const opened = await waitFor(() => tradeWindow(A) && tradeWindow(B));
  check(opened && A.currentWindow.inventoryStart === 54, 'accepting opens the 54-slot trade window on both sides',
    `A ${A.currentWindow && JSON.stringify(A.currentWindow.title).slice(0, 60)}, B ${!!B.currentWindow && tradeWindow(B)}`);
  if (!opened) { console.log('1 FAILED'); process.exit(1); }
  const offered = await offer(A, 'diamond');
  await waitFor(() => tableHas(B, 'diamond'), 3000);
  check(offered && tableHas(B, 'diamond') && diamonds(A) === 0, 'a shift-click moves the diamond from A\'s bag to the table on both views',
    `A table ${tableHas(A, 'diamond')}, B table ${tableHas(B, 'diamond')}, A bag ${diamonds(A)}`);
  await B.clickWindow(46, 0, 0).catch(() => {}); await sleep(800); // +10 coins
  await A.clickWindow(45, 0, 0).catch(() => {}); await sleep(800); // A confirms
  check(tradeWindow(A) && tradeWindow(B), 'one confirm alone settles nothing', `windows still open: A ${tradeWindow(A)}, B ${tradeWindow(B)}`);
  await B.clickWindow(45, 0, 0).catch(() => {}); // B confirms: coins first, then the goods
  const done = await waitFor(() => said(A, 'Арилжаа амжилттай') && said(B, 'Арилжаа амжилттай'));
  await sleep(3000);
  check(done && !A.currentWindow && !B.currentWindow, 'both confirms complete the trade and close the windows',
    `success A ${said(A, 'Арилжаа амжилттай')}, B ${said(B, 'Арилжаа амжилттай')}`);
  check(diamonds(B) === 1 && diamonds(A) === 0, 'the diamond changed hands', `A ${diamonds(A)}, B ${diamonds(B)}`);
  check(coins(nameA) === a0 + 10 && coins(nameB) === b0 - 10, 'the 10 coins moved in the database (COINS -> ITEMS)',
    `A ${a0} -> ${coins(nameA)}, B ${b0} -> ${coins(nameB)}`);

  // 2. the window is closed with goods on the table: everything comes back
  cmd(`minecraft:give ${nameB} minecraft:diamond 1`); await sleep(1500);
  const opened2 = await open(B, A);
  const offered2 = opened2 && await offer(B, 'diamond');
  B.closeWindow(B.currentWindow);
  const cancelled = await waitFor(() => said(A, 'Арилжаа цуцлагдлаа: цонх хаагдсан') && said(B, 'Арилжаа цуцлагдлаа: цонх хаагдсан'));
  await sleep(1500);
  check(opened2 && offered2 && cancelled && !A.currentWindow, 'closing the window cancels the trade for both', `opened ${opened2}, offered ${offered2}, cancelled ${cancelled}`);
  check(diamonds(B) === 2 && diamonds(A) === 0, 'the diamond on the table came back to B', `A ${diamonds(A)}, B ${diamonds(B)}`);
  check(coins(nameA) === a0 + 10 && coins(nameB) === b0 - 10, 'a cancelled trade moves no coins', `A ${coins(nameA)}, B ${coins(nameB)}`);

  // 3. a player quits with goods on the table: the other is told, the goods survive the relog
  cmd(`minecraft:give ${nameA} minecraft:diamond 1`); await sleep(1500);
  const opened3 = await open(A, B);
  const offered3 = opened3 && await offer(A, 'diamond');
  A.quit();
  const told = await waitFor(() => said(B, 'Арилжаа цуцлагдлаа: ' + nameA + ' гарлаа'));
  await sleep(4000);
  check(opened3 && offered3 && told && !B.currentWindow, 'a quit cancels the trade and tells the other side', `opened ${opened3}, offered ${offered3}, told ${told}`);
  const A2 = await join(nameA);
  await sleep(2000);
  check(diamonds(A2) === 1, 'the diamond A offered is in A\'s bag after rejoining', `A diamonds ${diamonds(A2)}, B ${diamonds(B)}`);
  check(coins(nameA) === a0 + 10 && coins(nameB) === b0 - 10, 'no coins moved on the quit', `A ${coins(nameA)}, B ${coins(nameB)}`);

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  A2.quit(); B.quit();
  process.exit(results.every(Boolean) ? 0 : 1);
})();
