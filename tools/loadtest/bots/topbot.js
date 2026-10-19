// The leaderboards (api/leaderboard/Leaderboard, gui/LeaderboardService): /top opens the level board, each word opens
// its board (coins, dungeon, khuraldai, achievements) with the board's name in the title, a tab click switches boards,
// and the console gets the list as lines. Dev server, offline dev flag:
//   node topbot.js <srvctl.sh> <server latest.log> [port]
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, logfile, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'T' + Math.floor(Math.random() * 90000 + 10000) + 'p';
const title = (b) => (b.currentWindow ? JSON.stringify(b.currentWindow.title) : '');
async function opens(b, c, word) {
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(600);
  b.chat(c);
  for (let t = 0; t < 6000 && !(title(b).includes(word)); t += 250) await sleep(250);
  return title(b).includes(word);
}

(async () => {
  const b = await new Promise((res) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    bot.on('error', () => {});
    bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
    bot.once('spawn', async () => {
      await sleep(3500);
      for (let i = 0; i < 40 && !bot.currentWindow; i++) await sleep(500);
      if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await bot.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
      await sleep(1000);
      res(bot);
    });
  });
  check(await opens(b, '/top', 'Түвшин'), '/top opens the level board', title(b).slice(0, 80));
  check(await opens(b, '/top coins', 'Зоос'), '/top coins opens the coin board', title(b).slice(0, 80));
  check(await opens(b, '/top dungeon', 'Агуй'), '/top dungeon opens the dungeon-clears board', title(b).slice(0, 80));
  check(await opens(b, '/top khuraldai', 'Хуралдайн аян'), '/top khuraldai opens the campaign board', title(b).slice(0, 80));
  check(await opens(b, '/top achievements', 'Амжилт'), '/top achievements opens the achievements board', title(b).slice(0, 80));
  await b.clickWindow(38, 0, 0).catch(() => {});
  for (let t = 0; t < 5000 && !title(b).includes('Түвшин'); t += 250) await sleep(250);
  check(title(b).includes('Түвшин'), 'the first tab switches back to the level board', title(b).slice(0, 80));
  const before = fs.readFileSync(logfile, 'utf8').length;
  cmd('top dungeon'); await sleep(1500);
  const tail = fs.readFileSync(logfile, 'utf8').slice(before);
  check(/Шилдэг · Агуй/.test(tail), 'the console gets the dungeon board as lines', tail.split('\n').filter(l => l.includes('Шилдэг')).join(' | ').slice(0, 100));
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit();
  process.exit(results.every(Boolean) ? 0 : 1);
})().catch(e => { console.log('FAIL the bot itself :: ' + e.message); process.exit(1); });
