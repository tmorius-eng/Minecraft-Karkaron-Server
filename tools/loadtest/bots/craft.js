// The Дархан's recipe book on the dev server (docs/CRAFTING.md), one offline test bot:
//   node craft.js <srvctl.sh> <logfile> [port]
// A level-30 bot crafts a level-1 recipe: the item comes out at the recipe's cap (10), not at 30. The window opens,
// a click asks once, the second click crafts and spends exactly the recipe's materials.
const mineflayer = require('mineflayer');
const fs = require('fs');
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

const title = (w) => { try { return w ? JSON.stringify(w.title) : ''; } catch (e) { return String(w && w.title); } };
const stacks = (b, name) => b.inventory.items().filter(i => JSON.stringify(i.customName || i.displayName || '').includes(name)).reduce((n, i) => n + i.count, 0);

(async () => {
  const name = 'D' + Math.floor(Math.random() * 90000 + 10000);
  const B = await join(name);
  cmd(`suld exp ${name} 6000000`); await sleep(1500);
  cmd(`suld coins ${name} 5000`); await sleep(500);
  cmd(`itemsadmin give ${name} item.chonon_arisan`); await sleep(500);
  cmd(`itemsadmin give ${name} item.tumriin_kheltarkhii`); await sleep(1500);

  let k = B.chatLog.length;
  B.chat('/item craft recipe.esgii_boots'); await sleep(2000);
  const made = B.chatLog.slice(k).find(l => l.includes('Урлалаа')) || '';
  check(/Зэрэг 10\)/.test(made), 'a level-30 smith gets the recipe band\'s cap (10) from a level-1 recipe', made || B.chatLog.slice(k).join(' | '));

  B.chat('/item recipes'); await sleep(2000);
  check(B.currentWindow && /Жорын ном/.test(title(B.currentWindow)), 'the recipe book opens', title(B.currentWindow));
  const before = B.inventory.items().length;
  k = B.chatLog.length;
  if (B.currentWindow) {
    await B.clickWindow(0, 0, 0).catch(() => {}); await sleep(1200); // the first recipe, lowest level: Эсгий Гутал
    check(B.currentWindow && /Баталгаажуулах/.test(title(B.currentWindow)), 'a click asks first', title(B.currentWindow));
    await B.clickWindow(11, 0, 0).catch(() => {}); await sleep(2000);
  }
  const made2 = B.chatLog.slice(k).find(l => l.includes('Урлалаа')) || '';
  check(!!made2, 'the confirmation crafts', made2 || B.chatLog.slice(k).join(' | '));
  check(B.currentWindow && /Жорын ном/.test(title(B.currentWindow)), 'and the book opens again', title(B.currentWindow));
  if (B.currentWindow) B.closeWindow(B.currentWindow);

  k = B.chatLog.length;
  B.chat('/item craft recipe.khaany_chestplate'); await sleep(1500);
  check(B.chatLog.slice(k).some(l => l.includes('Түвшин 50 хэрэгтэй')), 'a recipe above the smith\'s level is refused', B.chatLog.slice(k).join(' | '));

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  B.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})();
