// The quest bar points at the region the chapter is played in, one offline bot:
//   node questregionbot.js <srvctl.sh> [port]
// Act II's mobs roam all four outer regions, and the bar used to point at the first region that lists the mob
// (Хэнтийн Нуруу) for every kill chapter, so «Усны Лус» sent the player to Хэнтий instead of Хөвсгөл. The bot stands in
// the city, is set to each Act II kill chapter (staff /suld quest) and reads the yellow quest bar's region.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'Q' + Math.floor(Math.random() * 90000 + 10000);
// chapter number (1-based, quests.json «story») → the chapter's title and the region its bar must name
const CHAPTERS = [
  [21, 'Усны Лус', 'Хөвсгөлийн Тайга'],
  [26, 'Тангудын Сүнснүүд', 'Зүүнгарын Говь'],
  [27, 'Балгасны Хилэнц', 'Зүүнгарын Говь'],
  [30, 'Хүрээний Харуул', 'Хэнтийн Нуруу'],
  [31, 'Дайны Чоно', 'Хэнтийн Нуруу'],
  [35, 'Уулын Савдаг', 'Хэнтийн Нуруу'],
  [39, 'Тэнгэрийн Цэрэг', 'Алтай'],
];
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
const bars = new Map(); // boss bar uuid -> plain title
const keep = (bar) => bars.set(bar.entityUUID, String(bar.title));
b.on('bossBarCreated', keep);
b.on('bossBarUpdated', keep);
b.on('bossBarDeleted', (bar) => bars.delete(bar.entityUUID));

b.once('spawn', async () => {
  try {
    for (let i = 0; i < 100 && !b.currentWindow; i++) await sleep(500);
    if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
    if (b.currentWindow) b.closeWindow(b.currentWindow);
    await sleep(2500);
    for (const [n, title, region] of CHAPTERS) {
      execFileSync(ctl, ['cmd', `suld quest ${name} ${n}`]);
      let line = '';
      for (let i = 0; i < 12 && !line; i++) {
        await sleep(500);
        line = [...bars.values()].find(t => t.includes(title)) || '';
      }
      check(line.includes(region), `chapter ${n} «${title}» points at ${region}`, JSON.stringify(line));
    }
  } catch (e) {
    check(false, 'crashed', e.stack);
  }
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
