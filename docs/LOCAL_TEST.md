# SÜLD-ийг өөрийн компьютер дээр (localhost) ажиллуулж турших

Энэ заавраар серверийг ажлын компьютер дээрээ асааж, өөрийн Minecraft-аар `localhost` руу орж тоглоно. Бүх
тушаалыг дарааллаар нь бичлээ. Windows бол **PowerShell**, macOS/Linux бол **Terminal** ашиглана.

## 0. Урьдчилан суулгах зүйлс (нэг удаа)

| Юу | Хаанаас | Шалгах тушаал |
|---|---|---|
| Git | https://git-scm.com/downloads | `git --version` |
| Java 21 (Temurin JDK 21) | https://adoptium.net/temurin/releases/?version=21 | `java -version` → `21...` гэж гарна |
| Python 3 (заавал биш: нэмэлт plugin татахад) | https://www.python.org/downloads/ | `python3 --version` (Windows: `py --version`) |
| Minecraft Java Edition **1.21.11** | Minecraft Launcher | — |

Компьютер дээр дор хаяж 6 GB RAM сул байвал сайн (серверт 4 GB өгнө).

## 1. Кодыг татах

```bash
git clone https://github.com/tmorius-eng/Minecraft-Karkaron-Server.git
cd Minecraft-Karkaron-Server
```

Дараа нь шинэчлэх бол: `git pull`

## 2. Plugin-оо бүтээх (build)

macOS / Linux:
```bash
./gradlew build -x test
```
Windows (PowerShell):
```powershell
.\gradlew.bat build -x test
```

Эхний удаа Gradle болон сангуудыг татах тул 2–5 минут болно. Дууссаны дараа plugin энд гарна:
`suld-plugin/build/libs/suld-plugin-0.1.0-SNAPSHOT.jar` (`-sources.jar` биш, нөгөөг нь ашиглана).

(Бүх тестийг ажиллуулах бол `-x test`-гүйгээр: `./gradlew build` — 5–10 минут.)

## 3. Серверийн хавтас бэлдэх

`run/` хавтас git-д орохгүй, туршилтын сервер тэнд байна.

macOS / Linux:
```bash
mkdir -p run/plugins
cp suld-plugin/build/libs/suld-plugin-0.1.0-SNAPSHOT.jar run/plugins/
cd run
curl -fsSL https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["downloads"]["server:default"]["url"])' \
  | xargs curl -fL -o paper.jar
echo "eula=true" > eula.txt
```

Windows (PowerShell):
```powershell
New-Item -ItemType Directory -Force run\plugins | Out-Null
Copy-Item suld-plugin\build\libs\suld-plugin-0.1.0-SNAPSHOT.jar run\plugins\
cd run
$b = Invoke-RestMethod https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest
Invoke-WebRequest $b.downloads.'server:default'.url -OutFile paper.jar
Set-Content eula.txt "eula=true"
```

(`eula=true` гэдэг нь Minecraft-ын EULA-г зөвшөөрч байна гэсэн үг: https://aka.ms/MinecraftEULA)

### (Заавал биш) Бусад plugin-ууд

SÜLD ганцаараа ажиллана. Жинхэнэ сервертэй адил байлгах бол (LuckPerms, EssentialsX, WorldGuard ...) репогийн
үндсэн хавтаснаас:
```bash
python3 tools/plugins/fetch_plugins.py --dest run/plugins --profiles core
```
(Windows: `py tools\plugins\fetch_plugins.py --dest run\plugins --profiles core`)

## 4. Анхны асаалт

`run/` хавтас дотроос:
```bash
java -Xms2G -Xmx4G -jar paper.jar --nogui
```

Эхний удаа ертөнц үүсгэж, **Хархорум хотыг автоматаар барина** (хэдэн минут). Консолд `Done (...)` гарахад сервер
бэлэн. Дараа нь нэг удаа зогсоо: консолд `stop` гэж бичээд Enter.

## 5. Өөрийгөө эзэн (owner/OP) болгох

`run/plugins/SULD/config.yml` файлыг нээгээд:
```yaml
owners: [ТаныMinecraftНэр]
```
гэж бичиж хадгал. (Жишээ: `owners: [Tmorius]`.) Ингэхэд нэвтрэх бүрт OP эрх автоматаар өгнө.

`server.properties`-д `online-mode=true` хэвээр байг: өөрийн жинхэнэ Minecraft бүртгэлээр орно, аюулгүй.

Дахин асаа:
```bash
java -Xms2G -Xmx4G -jar paper.jar --nogui
```

## 6. Тоглоомд орох

1. Minecraft Launcher → **1.21.11** хувилбарыг сонгож асаа.
2. Multiplayer → Add Server → Server Address: `localhost` → Done → Join.
3. «Server resource pack» асуухад **Yes** (заавал хэрэгтэй: цэс, тэмдэг, зэвсгийн дүрс). Pack-ыг сервер өөрөө
   `localhost:8164`-оор түгээнэ.
4. Ангиа сонгоод «Батлах» дар — тоглоом эхэлнэ.

Хамт ажилладаг хүн тань ижил сүлжээнээс орох бол таны компьютерийн IP-г (`ipconfig` / `ifconfig`) `localhost`-ийн
оронд бичнэ; файерволд 25565 ба 8164 портыг нээ.

## 7. Турших тушаалууд (эзэн эрхтэй)

`<нэр>` = таны Minecraft нэр. Тушаалыг тоглоомын чатад `/`-тэй, консолд `/`-гүй бичнэ.

**Хурдан ахиулах**
| Тушаал | Юу хийх |
|---|---|
| `/suld exp <нэр> 6000000` | ~30-р түвшин хүртэл EXP |
| `/suld coins <нэр> 5000` | 5000 зоос |
| `/itemsadmin give <нэр> item.chonon_arisan` | 64 чонын арьс (материал) |
| `/itemsadmin give <нэр> item.tumriin_kheltarkhii` | 64 төмрийн хэлтэрхий |
| `/itemsadmin give <нэр> item.baavgain_arisan` | 64 баавгайн арьс |
| `/itemsadmin give <нэр> item.mosun_chuluu` | 64 мөсөн чулуу |
| `/itemsadmin give <нэр> item.altan_toos` | 64 алтан тоос |

**Дархан — урлал ба захиалга** (docs/CRAFTING.md)
| Тушаал | Юу хийх |
|---|---|
| `/item recipes` | Жорын ном (цонх): 43 жор, юу хэрэгтэйг харуулна, дарж урлана |
| `/item craft recipe.esgii_boots` | Нэг жорыг шууд урлах |
| `/order` | Захиалгын самбар |
| `/order post esgii_boots 100` | 100 зоосны хөлсөөр захиалга өгөх (2 дахь тоглогч биелүүлнэ) |

**Дэлхийн босс** (docs/WORLD_BOSSES.md)
| Тушаал | Юу хийх |
|---|---|
| `/suldevent boss ikh_baavgai` | Хөвсгөлийн Их Баавгайг одоо босгох (Уушгийн Өвөрт) |
| `/suldworld site uushig` | Тэр газар руу очих |
| `/suldevent status` | Одоогийн / дараагийн босс |
| `/suldevent boss stop` | Боссыг шагналгүй буцаах |
| `/chronicle boss` | Боссын хроник |

**Их Хуралдай, аян** (docs/KHURALDAI.md)
| Тушаал | Юу хийх |
|---|---|
| `/khuraldai` | Хуралдайн цонх |
| `/khuraldai open` | Санал хураалт нээх (админ) |
| `/khuraldai vote 1` | Санал өгөх (5-р түвшнээс) |
| `/khuraldai close` | Санал хураалт хаах |
| `/khuraldai force chonyn_ayan` | Аяныг одоо эхлүүлэх |
| `/khuraldai resolve` | Аяныг дүгнэх |
| `/chronicle` | Баатрын Хроник |

**Тоглоомын үндсэн**
| Тушаал | Юу хийх |
|---|---|
| `/menu` | «Командын Өргөө» — HD командын цонх (таб: Тоглогч, Даалгавар, Ур чадвар, Дэлгүүр, Rankup, Аялал, Тохиргоо, Тусламж) |
| `/travel` | Аялал: өртөө (өртөөнд зогсож байж аялна), түүхэн газрууд, овоо |
| `/settings` | Тохиргоо: самбар, зөвлөмж, дуу, чатын толгой, урилга |
| `/quest` | Эрэл (түүхийн бүлэг) |
| `/skills` | Чадварын мод |
| `/dungeon` | Агуйн шат (10 агуй) |
| `/suld spawnmob <нэр>` | Хажууд мангас гаргах |
| `/party invite <нэр>` · `/party find` | Баг, баг хайх |
| `/duel <нэр>` · `/duel accept` | Зөвшөөрөлт тулаан (хоёр тоглогч) |
| `/daily` · `/tasks` | Өдрийн шагнал, даалгавар |
| `/spawn` | Хархорум руу буцах |
| `/suldworld sites` | Түүхэн газрын жагсаалт |

### Цонхны шалгалт (docs/UI_HUB.md)

`/menu` нээгээд Options → Video Settings → GUI Scale-ийг 2, 3, 4 (ба Auto) болгож тус бүрт шалга: толгойн титэм
хайчлагдаагүй, цонх дэлгэцийн голд, доор цүнх харагдахгүй, идэвхтэй таб цэнхэр гэрэлтэй, картан дээр хулгана аваачихад
алтан гэрэл, tooltip бүр хөх-алтан хүрээтэй, `/profile`-ийн гарчгийн доорх мөр уншигдана. `/help start`, `/rules`,
`/commands`, `/discord` dialog нээнэ (чат биш), товчууд нь хөх-алтан. 1080p ба 1440p хоёулаа.

Нэг суултаар бүгдийг шалгах хуудас: `docs/qa/LAPTOP_TEST_2026-10.md`. Хаалттай альфа (15–20 хүн) зохион байгуулах:
`docs/ALPHA_PLAYTEST.md`.

## 8. Хоёр дахь тоглогчгүй бол (заавал биш)

Захиалга, duel зэргийг ганцаараа турших бол хоёр дахь Minecraft бүртгэл хэрэгтэй. Байхгүй бол **зөвхөн өөрийн
компьютер дээр** offline горимд оруулж болно:

1. `server.properties`: `online-mode=false`
2. `plugins/SULD/config.yml`: `auth:` доор `allow-insecure-offline-dev-mode: true`
3. Сервер дахин асаа.

⚠ Энэ горимд хэн ч хэний ч нэрээр орж чадна — **хэзээ ч олон нийтэд нээлттэй сервер дээр бүү асаа.** Offline горимд
`owners:` OP өгөхгүй (баталгаатай нэвтрэлт шаардлагатай), консолоос `op <нэр>` гэж өгнө. Туршилт дуусахад хоёуланг
нь буцааж `true` / `false` болго.

## 9. Шинэ код орж ирэхэд

Репогийн үндсэн хавтаснаас:
```bash
git pull
./gradlew build -x test          # Windows: .\gradlew.bat build -x test
cp suld-plugin/build/libs/suld-plugin-0.1.0-SNAPSHOT.jar run/plugins/
```
(Windows: `Copy-Item suld-plugin\build\libs\suld-plugin-0.1.0-SNAPSHOT.jar run\plugins\ -Force`)

Сервер ажиллаж байвал эхлээд `stop`, хуулсны дараа дахин асаа.

## 10. Асуудал гарвал

| Шинж | Шийдэл |
|---|---|
| `java -version` 21 биш | Temurin JDK 21 суулгаад terminal-аа дахин нээ |
| `Unsupported class file major version` | Java 21-ээр асааж байгаа эсэхээ шалга |
| Pack татагдахгүй | Сервер ба Minecraft нэг компьютер дээр байгаа эсэх, 8164 порт өөр програмд эзлэгдээгүй эсэх |
| `Failed to verify username` | `online-mode=true` үед жинхэнэ бүртгэлээр ор; эсвэл §8 |
| Сервер удаан | `-Xmx4G`-ийг компьютерийн RAM-д тааруулж өөрчил; хот анх баригдах үед түр удаан |
| Өгөгдлөө шинээр эхлүүлэх | серверийг зогсоогоод `run/world*`, `run/plugins/SULD/data` хавтсыг устга |

Өгөгдөл `run/plugins/SULD/data/` дотор H2 файл санд хадгалагдана (PostgreSQL хэрэггүй).
