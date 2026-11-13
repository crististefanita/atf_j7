# Evaluarea AI în Chess Lab

Nivelul adversarului și indiciul au adâncimi și bugete configurate separat. O căutare păstrează ultima adâncime evaluată complet. Hard poate încerca șase semimutări în plafonul existent de 44.000 de noduri; acceptă rezultatul de la ultimul orizont numai dacă păstrează aceeași mutare lider ca adâncimea precedentă. În caz de schimbare a liderului, rămâne la ultima căutare completă și stabilă. Indiciul încearcă șase semimutări, cu o semimutare suplimentară pentru capturi, promovări sau ieșirea din șah la capătul căutării. În partide și lecții, evaluarea adaugă la material dezvoltarea pieselor, centrul, poziția regelui și perechea de nebuni. Ponderea termenilor se schimbă odată cu materialul rămas, astfel încât un rege activ să fie util în final. Căutarea verifică întâi capturile, promovările și mutările care activează piesele. Testele de tactică păstrează obiectivul lor explicit de material sau mat. [Politica de căutare](../../chess-core/src/main/resources/match-ai.yaml)

În finalul de turnuri, Hard folosește istoricul integral pentru a evalua repetițiile în arbore, nu doar la rădăcină. Poate extinde căutarea cu o semimutare peste adâncimea profilului, până la adâncimea 7, în același plafon de 44.000 de noduri. Extensia și fereastra de 50 cp pentru candidații rădăcină sunt fixate în `rook-endgame` din YAML; guvernanța respinge extensii sau ferestre în afara limitelor validate.

În jocurile Hard din afara repertoriului și a finalului special de turnuri, mutările care dau șah primesc prioritate numai în ordinea de căutare. Bonusul de 10.000 este configurabil și plafonat în `hard-ordering`; nu se adună la evaluarea poziției și nu crește limita de 44.000 de noduri. Basic, Advanced, indiciul și evaluarea barei păstrează ordonarea anterioară.

Indiciul pornește de la căutarea Hard și verifică separat maturile și răspunsurile tactice. Dacă primele mutări diferă fără un mat dovedit, motorul compară alternativele la adâncimea Hard deja încheiată. Dacă verificarea își consumă bugetul, indiciul folosește rezultatul complet Hard. Lista arată până la cinci prime mutări distincte, la cel mult 25 cp de prima. La mat rămân doar variantele cu același rezultat și aceeași distanță. La scor egal, activitatea mutării și ordinea UCI stabilesc ordinea.

La Hard, într-o poziție cu material egal, clasamentul disponibil după patru semimutări folosește și scorul aceleiași mutări la trei. Contribuția liniei mai scurte este limitată. Pentru o mutare liniștită, creditul scade când linia provizorie sugerează un câștig de peste un pion. Maturile și remizele evaluate păstrează scorul terminal. Dacă bugetul împiedică încheierea adâncimii șase sau liderul se schimbă la acea adâncime, motorul păstrează ultima căutare completă. Bugetul Hard rămâne de 44.000 de noduri suplimentare.

În Pirc și Benoni, Hard și indiciul folosesc câte o replică verificată cu Stockfish 19 la adâncimea 13. Repertoriul se aplică la aceeași poziție legală, inclusiv prin transpoziție, iar continuarea este căutată în bugetul profilului. După replică sau în poziții vecine se folosește căutarea integrală. Încărcarea verifică legalitatea, pozițiile duplicate și maturile într-o mutare. [Repertoriu YAML](../../chess-core/src/main/resources/ai-repertoire.yaml)

Într-un ciclu, Hard folosește istoricul înainte de a elimina alternativele. Dacă poziția curentă a mai apărut de două ori, o mutare care ar produce a treia apariție sau mai mult primește 0 cp la rădăcina căutării. Motorul compară alternativele în același buget, la ultima adâncime încheiată. Între liniile încă favorabile, Hard poate alege o poziție vizitată de mai puține ori dacă păstrează cel puțin 100 cp avantaj și cedează cel mult 50 cp față de prima linie.

A treia apariție este un semnal pentru alegerea mutării. Partida se încheie automat la a cincea apariție, iar Hard poate alege acea remiză dacă nu vede un avantaj în ciclu. [Politica YAML](../../chess-core/src/main/resources/match-ai.yaml) · [Partida de regresie](../../atf-tests/src/test/resources/repetition-regression.yaml) · [Regulile FIDE, art. 9.2 și 9.6](https://handbook.fide.com/chapter/E012023)

Bara și prima linie de text folosesc o estimare separată pentru poziția curentă. Motorul evaluează două semimutări, câte una pentru fiecare parte, și aplică o parte din diferența obținută la trei. Ambele căutări verifică schimburile la orizont. Corecția este limitată la 25 cp, în bugetul propriu al afișajului. Apăsarea becului păstrează estimarea afișată și parcurge variantele în ordinea căutării de indicii.

## Control la fiecare schimbare

1. Rulați suita JVM înainte de schimbare. Comparați din aceeași copie TMP, cu același executabil Stockfish și același YAML de evaluare. Păstrați raportul Extent din fiecare rulare.
2. Înregistrați poziția în care decizia a fost slabă, mutarea aleasă, replica omisă și evaluarea de referință. Folosiți aceste exemple pentru ajustarea căutării. Păstrați setul de control fix în timpul ajustării și adăugați separat poziții noi pentru verificare.
3. Comparați Basic, Advanced, Hard și prima recomandare pentru fiecare poziție. Urmăriți pierderea medie a scorului așteptat, numărul pierderilor mari și potrivirile exacte. Verificați adâncimea finalizată, nodurile suplimentare și latența. Comparați latențele pe același dispozitiv, în condiții similare.
4. La regresie, deschideți în Extent pozițiile cu pierderea cea mai mare și verificați dacă mutarea a fost depunctată din cauza orizontului, a unei egalități sau a bugetului epuizat. Mențineți pragurile în timpul experimentului și justificați separat orice revizie a setului sau a pragurilor.

```powershell
mvn test '-Dtest=ChessBenchmarkSuite' "-Dchess.stockfish=$stockfish" '-Dchess.extent=target/match-ai-benchmark.html'
```

Setați `$stockfish` la executabilul Stockfish 19. Verificarea pornește câte o căutare la adâncimea fixată în YAML pentru poziția întreagă și pentru mutarea J7, cu un fir, 16 MiB de hash și memorie resetată între căutări. Scorul așteptat Stockfish este `W + D/2` în miimi. Diferența dintre căutări se plafonează la zero când cea restrânsă găsește o evaluare mai bună. Raportul arată și scorul UCI pentru fiecare mutare, ca să se vadă maturile sau diferențele care apar la un scor așteptat deja saturat. [Set și praguri](../../atf-tests/src/test/resources/match-ai-benchmark.yaml) · [Stockfish 19](https://github.com/official-stockfish/Stockfish/releases/tag/sf_19) · [Scorurile WDL Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Useful-data.html)

Scorurile acestui set măsoară alegerea unei mutări din poziții fixe. Pentru o estimare Elo, folosiți perechi de partide pe aceleași deschideri, cu culori inversate, timp fix și interval de încredere. Păstrați rezultatele partidelor separat de acest raport pe poziții.

## Liga offline

| Profil | Adâncime maximă | Noduri suplimentare |
|---|---:|---:|
| Basic | 3 | 0 |
| Advanced | 4 | 16.000 |
| Hard | 6, cu lider stabil | 44.000 |
| Indiciu | 6, plus o extensie tactică | 220.000, inclusiv Hard și până la 32.000 pentru compararea alternativelor |

Guvernanța cere creșterea strictă a adâncimii și bugetului între adversari. Indiciul are cel puțin adâncimea Hard și propriul buget mai mare. În finalurile cu turn sau damă și regi, toate nivelurile, indiciul și bara folosesc aceeași evaluare a distanței dintre regi și a spațiului regelui apărat. Testele verifică transformarea avantajului în mat cu ambele culori și conservarea perspectivei albului în afișaj.

### Proba internă pentru Hard la adâncimea 6 · 13 noiembrie 2026

Hard a păstrat plafonul de 44.000 de noduri, iar Basic și Advanced nu și-au schimbat profunzimea sau bugetul. O probă fără filtrul de stabilitate a coborât Intermezzo la 22/24. Varianta păstrată în candidatul live acceptă rezultatul de la adâncimea 6 numai dacă mutarea lider coincide cu cea de la adâncimea 5; altfel folosește rezultatul complet anterior.

Transferul Hard a trecut podelele înghețate: mat în 1 500/500, mat în 2 300/300, mat în 3 190/200, tactici 87/100, antrenament 40/48 și Intermezzo 24/24. Liga internă a măsurat Hard–Advanced la 20/1/0/3 și Elo pereche central +417 (interval 95% +172…∞), față de reperul anterior 19/1/0/4 și +374. Este o singură ligă internă, cu trei partide neterminate; diferența modestă nu constituie o nouă calibrare Elo.

Ultima calibrare externă Stockfish 19 înaintea probei Hard6 a estimat 1296/1406/1544 pentru Basic/Advanced/Hard, cu intervale empirice largi. Ancorele afișate în aplicație rămân însă cele verificate și guvernate în `game-review.yaml`: 1340/1461/1549, legate de observațiile cu sursa `b5c1ef3`. Nu am transferat calibrarea TMP în aplicație; ratingul Hard6 cere trei sesiuni externe pe același set de 12 deschideri și culori. Proba curentă a rămas offline. Rapoartele Extent și rejucările experimentelor sunt păstrate în TMP, nu sunt artifacte de release.

### A/B offline respinse · 13 noiembrie 2026

PVS aplicat pe toate iterațiile a coborât Mat în 3 la 188/200, sub podeaua 190, și a fost respins fără liga de joc. PVS numai la adâncimea finală a trecut toate podelele, dar liga a rămas 20/1/0/3 și +417, identic cu Hard6 fără PVS; complexitatea în plus nu a adus forță măsurată. LMR pentru mutări liniștite târzii a trecut transferul, dar atât pragul după mutarea a patra, cât și cel după a opta au coborât Hard–Advanced la 19/1/1/3 și +338. Ambele variante LMR sunt respinse. Niciuna nu a fost copiată în codul live; rapoartele sunt în TMP.

Am verificat și extensia tactică de un ply la fiecare frunză Hard: mat în 3 a crescut la 192/200 și tactici la 91/100, dar antrenamentul a coborât la 36/48, sub podeaua 39; am oprit transferul înaintea ligii. Extensia numai când frunza era în șah a coborât mat în 3 la 185/200. Ordonarea killer pentru mutările liniștite a coborât mat în 3 la 169/200 și adâncimea medie de la 5,86 la 5,45; la mat în 2, nodurile medii au crescut de la 18.529 la 27.085. Toate trei sunt respinse înainte de ligă și rămân doar în build-urile și rapoartele TMP `j7-hard-quiescence-20261113` și `j7-hard-killer-20261113`. Nu au schimbat codul live sau bugetul Hard.

Pentru fiecare dintre cele trei confruntări, liga pornește din 12 deschideri și inversează culorile. Motorul folosește alegerile din aplicație și păstrează istoricul pentru repetiție. Partidele încă active la 160 de semimutări sunt înregistrate ca neterminate. La calcul, fiecare astfel de partidă poate contribui între zero și un punct. Punctul central folosește mijlocul acestui interval. Intervalul empiric de 95% provine din 10.000 de reeșantionări ale perechilor întregi, cu ambele limite pentru partidele neterminate. Diferențele Elo sunt `400 log10(p / (1 − p))`, unde `p` este scorul confruntării. [Setul ligii](../../atf-tests/src/test/resources/ai-league.yaml) · [Metoda perechilor în Fishtest](https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html)

Ancora externă folosește Stockfish 19, `UCI_Elo 1500`, un fir și cererea de 6.000 de noduri pe mutare, cu culorile inversate în aceleași 12 deschideri. Rezultatul este performanța față de această configurație. Stockfish își calibrează opțiunea Elo într-un protocol de timp diferit, iar selecția la putere limitată introduce variație între rulări. [Setul ancorei](../../atf-tests/src/test/resources/ai-anchor.yaml) · [Calibrarea opțiunii Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)

Dintr-o copie TMP, folosiți căi noi pentru fiecare raport și fișier de rejucare. La rulări Maven în paralel, folosiți câte un depozit local în TMP per execuție, ca instalarea bibliotecilor ATF să nu scrie concurent aceleași fișiere.

```powershell
mvn test '-Dtest=ChessBenchmarkSuite' "-Dchess.league=$leagueTsv" "-Dchess.extent=$leagueHtml"
mvn test '-Dtest=AiAnchorBenchmarkTest' "-Dchess.stockfish=$stockfish" "-Dchess.anchor=$anchorTsv" "-Dchess.extent=$anchorHtml"
```

Extent consemnează rezultatele pe perechi, iar TSV păstrează secvențele UCI integrale. Pentru stil, comparați capturile și șahurile pe mutare, rocadele pe loc de joc și adâncimea medie încheiată. Folosiți aceleași deschideri și același plafon la fiecare comparație.

### Rezultatele ligii interne

| Confruntare | V / R / Î / N | Diferență Elo centrală înainte → după | Interval 95% după |
|---|---:|---:|---:|
| Advanced – Basic | 19 / 2 / 3 / 0 | +120 → +280 | +154 … +470 |
| Hard – Advanced | 10 / 11 / 2 / 1 | +232 → +120 | +14 … +211 |
| Hard – Basic | 19 / 3 / 1 / 1 | +470 → +338 | +120 … +669 |

V, R, Î și N înseamnă victorii, remize, înfrângeri și partide neterminate pentru primul profil. În această ligă, Basic a finalizat în medie adâncimea 3,00, Advanced 3,94 și Hard 4,43. Capturile au reprezentat 20,5%, 23,1% și 24,7% dintre mutările lor. Șahurile au reprezentat 5,9%, 11,9% și 15,1%. Cele 11 remize dintre Hard și Advanced au venit prin repetiție. În Alehină, cu Hard la alb, Stockfish a evaluat la `+8,57` poziția de la începutul ciclului, la semimutarea 36, într-o căutare fără istoricul partidei.

### Retestarea pentru 0.7.0

| Confruntare | V / R / Î / N | Diferență Elo centrală | Interval empiric 95% |
|---|---:|---:|---:|
| Advanced – Basic | 19 / 2 / 3 / 0 | +280 | +154 … +470 |
| Hard – Advanced | 14 / 7 / 2 / 1 | +191 | +104 … +307 |
| Hard – Basic | 22 / 0 / 1 / 1 | +470 | +191 … ∞ |

Hard a câștigat toate cele opt partide din Pirc și Benoni, din ambele perspective, contra Basic și Advanced. În partida Alehină Hard–Advanced, a jucat `Nc3` după semimutarea 44 și a ieșit din ciclul care ducea anterior la remiză la 52. Stockfish indică +6,71 pentru ieșire, cu istoricul partidei și pierdere WDL de 0‰. Partida a ajuns la plafonul de 160 de semimutări. Stockfish arată −5,19 pentru alb la finalul probei. În partida cu culorile inversate, Hard a câștigat la semimutarea 108. [Rejucarea istorică 0.7.0](https://github.com/crististefanita/atf_j7/blob/v0.7.0/release/0.7.0/reports/league.tsv) rămâne în tagul Git, nu în catalogul scurt de release-uri.

### Conversia avantajului în Alehină

Reluarea istorică oprește partida după 72 de semimutări. Hard juca `Qc5`, către a treia apariție a poziției. Stockfish 19 la adâncimea 13, cu toate mutările precedente, dă 0,00 pentru `Qc5`. Cu istoricul în căutare, Hard alege `Qxf6+`: +6,62 în căutarea restrânsă, față de +7,11 pentru aceeași mutare în căutarea liberă. Pierderea WDL este 0‰. Hard finalizează adâncimea 5 în 12.557 de noduri suplimentare, sub plafonul neschimbat de 44.000. [Poziția și mutările de control](../../atf-tests/src/test/resources/alekhine-conversion.yaml)

Din deschiderea inițială, Hard alb joacă `Nf4` după semimutarea 44 și câștigă la 153. Din poziția istorică de la 72, după schimbul damelor, Hard ajunge la plafon cu −0,29 pentru alb. Această probă păstrează apărarea Advanced și același plafon de 160 de semimutări.

| Liga de antrenament · 72 de partide | V / R / Î / N | Diferență Elo centrală | Interval empiric 95% |
|---|---:|---:|---:|
| Advanced – Basic | 19 / 2 / 3 / 0 | +280 | +154 … +470 |
| Hard – Advanced | 15 / 7 / 2 / 0 | +211 | +137 … +307 |
| Hard – Basic | 22 / 0 / 1 / 1 | +470 | +191 … ∞ |

Față de 0.7.0, 69 din 72 de partide au același rezultat. Hard transformă în victorii partida neterminată din Alehină și remiza din Gambitul regelui. Victoria Hard alb din Benoni modern devine remiză: la prima divergență, după 112 de semimutări, Stockfish evaluează noul `Kf4` la 0,00 și fostul `Kf6` la −1,75. Singura partidă rămasă la plafon este Petroff Hard–Basic, cu aceeași secvență ca înainte. Adâncimile medii finalizate sunt 3,00, 3,94 și 4,43 pentru Basic, Advanced și Hard.

#### Rejucarea finalului de turnuri

`RookEndgameHistoryTest` reconstruiește aceeași partidă completă până la semimutările 96, 108 și 112. Cazul de la 108 separă evaluarea numai după tablă de evaluarea cu istoricul păstrat: fără istoric, `a7a8` duce în linia de remiză; cu istoricul, Hard evită repetiția și joacă `h2h4`.

Pentru comparație independentă, trimiteți istoricul integral la Stockfish prin `position startpos moves ...` și evaluați mutările restrânse la adâncimea 13. Același fixture și aceeași comandă se folosesc pentru fiecare versiune.

```text
f5f6 g7f6 g1f1 a7a5 a1b1 b7b5 b1b5 e8d8 e2c3 d8c8 c3e2 a5a4
b5f5 c8c2 f2f4 c2a2 e2c1 a2d2 f1e1 d2g2 c1d3 f8g7 f5a5 h8e8
```

| Ply | Hard cu istoric | Stockfish 19, d13 | Pierdere așteptată | Noduri Hard |
|---:|---|---|---:|---:|
| 96 | `Kf1` | `Kf1`, +2,86 | 0‰ | 39.504 |
| 108 | `h2h4` | `h1g1`, +5,38; `h2h4`, +4,08 | 0‰ | 41.376 |
| 112 | `a7a8` | `d3e1`, +2,63; `a7a8`, +2,40 | 0‰ | 8.045 |

La 108, Stockfish evaluează `a7a8` la 500‰, iar `h1g1` la 1.000‰. Cheia de repetare include tabla, partea la mutare, drepturile de rocadă și doar un en passant legal. Cheia transpoziției păstrează și traseul căutării, astfel încât o valoare cu repetiție să nu fie reutilizată pe un traseu diferit. Toate cele trei decizii respectă limita Hard; profilurile Basic, Advanced și Indiciu nu folosesc această cale.

Verificarea acoperă regresia istorică și setul fix de 10 poziții Stockfish. Nu recalibrați Elo-ul Hard din aceste trei poziții: comparația de ligă completă rămâne necesară pentru o nouă estimare. [Următorul control](../feedback/README.md)

#### Transferul Hard către colecția de quest-uri

`HardQuestTransferBenchmarkTest` măsoară mutarea aleasă de adversarul Hard pe toate cele 1.172 de poziții din cele șase capitole. Mat în 1 verifică matul, mat în 2/3 verifică prima cheie acceptată, iar exercițiile materiale verifică soluția catalogului. Pragurile de bază sunt în [hard-quest-transfer.yaml](../../atf-tests/src/test/resources/hard-quest-transfer.yaml); sunt praguri de regresie, nu ținte finale.

| Capitol | Rezolvat de Hard |
|---|---:|
| Mat în 1 | 500 / 500 |
| Mat în 2 | 300 / 300 |
| Mat în 3 | 190 / 200 |
| Tactici | 86 / 100 |
| Antrenament | 39 / 48 |
| Intermezzo | 24 / 24 |

Ordonarea șahurilor la rădăcina căutării Hard ridică transferul M3 de la 172 la 190 fără creșterea plafonului de noduri. Rămân 10 poziții M3, 14 tactice și 9 de antrenament de diagnosticat. Testul păstrează podelele de regresie în suita benchmark și publică nodurile, adâncimea și exemplele ratate în Extent.

#### Liga paralelă și reglajul ordonării șahurilor

Pe 12 noiembrie 2026, am comparat bonusurile 5.000 și 10.000 pe aceleași 72 de partide, cu cel mult trei workeri, aceleași deschideri, culori și bugete. Ambele rulări au trecut, iar fiecare partidă s-a rejucat din checkpoint.

| Bonus | Hard–Advanced V / R / Î / N | Elo central | Interval 95% | Hard–Basic V / R / Î / N | Noduri Hard / tură |
|---:|---:|---:|---:|---:|---:|
| 5.000 | 19 / 2 / 1 / 2 | +338 | +154 … ∞ | 22 / 1 / 1 / 0 | 29.891 |
| 10.000 | 19 / 2 / 2 / 1 | +307 | +154 … +669 | 22 / 1 / 1 / 0 | 29.518 |

Advanced–Basic a rămas identic la 18 / 3 / 3 / 0 și +255 Elo, cu intervalul +137…+417. Între 5.000 și 10.000 s-a schimbat rezultatul unei singure partide: Vienna, Advanced cu albele contra Hard cu negrele, s-a încheiat cu victorie albă în loc să rămână neterminată la plafon. Stockfish 19 la adâncimea 13 a evaluat prima divergență `e8g8`→`f5c2` la −915→−926 cp; pierderea WDL a rămas 0‰ pentru ambele alegeri. Așadar liga nu demonstrează o creștere Elo la 10.000; poziția era deja pierdută pentru Hard, iar intervalele sunt largi.

Am ales 10.000 pentru câștigul măsurat în colecția de quest-uri: Mat în 3 a urcat la 172/200 față de 170/200 la 5.000, celelalte capitole nu au scăzut, iar media ponderată a nodurilor pe toate cele 1.172 de poziții a coborât cu 16,6%, de la aproximativ 7.554 la 6.299. Setul fix de 10 poziții Stockfish a rămas 10/10, cu pierdere WDL 0‰ pentru Hard. Aceste rezultate nu justifică recalibrarea Elo-ului. Extensia tactică globală și varianta limitată la poziții în șah au fost respinse: prima a coborât Antrenamentul la 35/48 și a încălcat porțile Stockfish, iar a doua Matul în 3 la 166/200. Rapoartele Extent și TSV rămân în TMP.

Am verificat și bonusuri de 15.000 și 20.000 pe aceleași 1.172 de quest-uri și pe poarta Stockfish. Ambele au păstrat aceleași rezultate ca 10.000 în toate capitolele și 10/10 contra Stockfish; media ponderată a scăzut doar la aproximativ 6.255 și 6.254 de noduri, sub 1% față de 10.000. Nu există câștig de transfer care să justifice bonusul mai mare, deci plafonul și valoarea de 10.000 rămân. Măsurătorile de timp din rulările concurente nu sunt folosite pentru comparație.

Am testat separat ordonarea mutărilor la rădăcină: Hard caută mai întâi șahurile și păstrează ordinea evaluării în interiorul grupurilor de șah și non-șah. Basic, Advanced, Indiciul, repertoriul și finalurile speciale de turnuri nu intră pe această cale. Pe toate quest-urile, M3 a urcat la 190/200, Antrenamentul a rămas 39/48, iar celelalte capitole nu s-au schimbat; media ponderată a nodurilor a coborât de la 6.299 la aproximativ 5.703. Setul Stockfish a rămas 10/10.

Liga pereche de 72 de partide a păstrat scorul Hard–Basic la 22/1/1/0 și Advanced–Basic la 18/3/3/0. Hard–Advanced a trecut de la 19/2/2/1, +307 Elo, la 19/3/1/1, +338 Elo; o victorie a albului în Vienna, cu Hard negru, s-a transformat în remiză prin repetiție. Intervalele 95% se suprapun larg (+154…+669 față de +191…+669), deci rezultatul susține lipsa unei regresii în eșantion, nu o creștere Elo confirmată. Media Hard a coborât de la 29.518 la 29.305 noduri pe tură.

O sortare mai agresivă a tuturor mutărilor rădăcină după capturi și activitate a dat tot 190/200 la M3, dar Hard–Advanced a scăzut la 14/6/2/2 (+191 Elo central). Am respins-o; câștigul la puzzle-uri nu justifică o slăbire în joc. Estimările absolute Elo se actualizează doar după cele trei sesiuni Stockfish 19 descrise mai jos.

### Rezultatele ancorei externe

Pe sursa ediției 0.9.0, trei sesiuni au folosit aceleași 12 poziții și culorile inversate, câte 24 de partide pe profil în fiecare sesiune. Stockfish 19 a rămas la `UCI_Elo 1500`, 6.000 de noduri cerute pe mutare și un fir. Plafonul a rămas la 160 de semimutări, iar profilurile J7 și-au păstrat bugetele.

| Profil | V / R / Î / N | Estimare în protocol | Interval empiric 95% | Estimări pe sesiuni |
|---|---:|---:|---:|---:|
| Basic | 12 / 2 / 43 / 15 | 1340 | 1139–1481 | 1380 / 1328 / 1309 |
| Advanced | 16 / 2 / 24 / 30 | 1461 | 1183–1684 | 1471 / 1411 / 1500 |
| Hard | 31 / 1 / 21 / 19 | 1549 | 1369–1739 | 1573 / 1573 / 1500 |

Din cele 216 partide, 64 au rămas active la plafon. Fiecare contribuie cu un interval de scor între zero și un punct. Calculul agregă cele trei perechi ale fiecărei deschideri înaintea celor 10.000 de reeșantionări. Deschiderea rămâne unitatea independentă. Hard conduce la estimarea centrală, iar intervalele celor trei profiluri se suprapun. `GameReviewGovernanceTest` recalculează toate cele trei repere folosite în aplicație. [Rezultatele perechilor și proveniența](../../atf-tests/src/test/resources/ai-anchor-observations.yaml)

În cele trei sesiuni istorice, Advanced a ajuns la plafon în cinci dintre cele șase partide Benoni modern. Stockfish 19 la adâncimea 13 a estimat 0‰ pentru Advanced la pozițiile finale ale celor cinci, inclusiv două poziții cu mat forțat. În calculul ancorei, toate cinci păstrează intervalul de partidă neterminată.

Reevaluarea istoricului complet din aceste sesiuni și din sesiunea de după corecția Alehină a găsit prima pierdere majoră a negrului la semimutarea 10. `…Ng4` a pierdut 335‰ în toate cele patru față de `…d6`. Cu albul, prima pierdere a apărut la semimutarea 11 după `…g6`, `…Na6` sau `…h6`, cu 379‰, 285‰ și 173‰. Repertoriul Advanced folosește `…d6`, `d6` după `…g6` și `e4` după `…Na6` sau `…h6`. Regulile se potrivesc pe poziția exactă și păstrează bugetul Advanced. [Replici și niveluri](../../chess-core/src/main/resources/ai-repertoire.yaml)

Pentru reproducere, folosiți o copie TMP și trei căi noi pentru TSV și Extent. Fiecare execuție validează perechile și reia toate mutările până la rezultatul înregistrat.

```powershell
foreach ($index in 1..3) {
    mvn -B -ntp test '-Dtest=AiAnchorBenchmarkTest' "-Dchess.stockfish=$stockfish" `
        "-Dchess.anchor=$scratch/anchor-$index.tsv" "-Dchess.extent=$scratch/anchor-$index.html"
    if ($LASTEXITCODE) { throw "Sesiunea $index a eșuat" }
}
```

Retestarea anterioară după corecția Alehină a acoperit 72 de partide.

| Profil | V / R / Î / N | Performanță centrală în protocol | Interval empiric 95% |
|---|---:|---:|---:|
| Basic | 2 / 0 / 17 / 5 | 1245 | −∞ … 1442 |
| Advanced | 11 / 1 / 8 / 4 | 1544 | 1346 … 1755 |
| Hard | 6 / 0 / 14 / 4 | 1380 | 1083 … 1558 |

Au rămas 13 partide la plafon. Advanced are cea mai mare estimare centrală contra configurației Stockfish, iar Hard conduce liga directă. Intervalele ancorei se suprapun; ordinea estimărilor nu stabilește ordinea absolută a profilurilor.

Prima măsurare, păstrată ca reper:

| Profil | V / R / Î / N | Performanță centrală în protocol | Interval empiric 95% |
|---|---:|---:|---:|
| Basic | 6 / 0 / 13 / 5 | 1396 | 1083 … 1589 |
| Advanced | 8 / 0 / 8 / 8 | 1500 | 1162 … 1732 |
| Hard | 5 / 0 / 11 / 8 | 1411 | 955 … 1654 |

În prima măsurare, 21 dintre cele 72 de partide au rămas active la plafon. Variația între rulări apare și la Basic și Advanced, ale căror reguli de căutare au rămas aceleași. Pentru o comparație între versiuni, păstrați bugetul și plafonul și repetați perechile în mai multe sesiuni.

#### Calibrare exploratorie a ordonării șahurilor · 12 noiembrie 2026

Am rulat trei sesiuni noi pe candidatul local cu prioritate stabilă pentru șahuri la rădăcina căutării Hard. Stockfish 19 a folosit `UCI_Elo 1500`, cererea de 6.000 de noduri pe mutare și un fir; fiecare profil a jucat 72 de partide pe aceleași 12 deschideri, cu ambele culori. Limita de 160 de semimutări și bugetele profilurilor au rămas neschimbate. Hash-ul executabilului Stockfish a corespuns ancorei.

| Profil | V / R / Î / N | Elo estimat | Interval pereche 95% |
|---|---:|---:|---:|
| Basic | 7 / 0 / 56 / 9 | 1212 | 1008–1369 |
| Advanced | 23 / 0 / 30 / 19 | 1466 | 1237–1654 |
| Hard | 34 / 0 / 23 / 15 | 1553 | 1411–1718 |

Intervalul reeșantionează cele 12 deschideri ca unități independente și tratează fiecare partidă neterminată ca scor între zero și un punct. Rulările au generat rapoarte Extent și TSV în TMP; fixture-ul, pragurile și bugetele nu s-au schimbat. Aceste valori descriu candidatul din copia locală, nu sunt promovate în `game-review.yaml` sau `ai-anchor-observations.yaml`: codul nu are încă un commit de proveniență pentru această calibrare. Intervalele sunt largi și se suprapun, deci rezultatul nu dovedește o creștere Elo și nici o separare statistică sigură între niveluri. Recalibrarea guvernată se face după fixarea sursei într-un commit și păstrarea hash-urilor rapoartelor.

## Analiza partidei

După rezultat, analiza locală compară mutarea jucată și recomandarea la ultima adâncime comună finalizată. Caută până la cinci semimutări, cu o extensie tactică și maximum 85.000 de noduri suplimentare pe poziție. Scorul așteptat este o transformare logistică a centipionilor, cu scară de 220 cp. Graficul păstrează perspectiva albului. Precizia fiecărei culori este procentul mediu de scor păstrat la mutările cu alegere, excluzând mutările de carte și cele forțate.

Pragurile de pierdere sunt 20‰, 50‰, 100‰ și 200‰ pentru mutare bună, imprecizie, greșeală și greșeală gravă. O ocazie ratată cere o șansă câștigătoare lăsată de adversar. O mutare strălucită cere adâncime de cel puțin patru semimutări, avantaj și acceptarea unui sacrificiu de cel puțin doi pioni în replica găsită. Căutarea trebuie să găsească fie o continuare distinct mai bună, fie un singur mat demonstrat. O mutare de catalog cu o pierdere de cel puțin 100‰ este clasificată după evaluare. [Politica locală](../../chess-core/src/main/resources/game-review.yaml) · [Clasificarea Chess.com](https://support.chess.com/en/articles/8572705-how-are-moves-classified-what-is-a-blunder-or-brilliant-etc)

Indexul recunoaște 3.865 de linii ECO. Caută ultima poziție denumită, inclusiv când partida ajunge la ea prin transpoziție, și marchează mutările de carte cât timp secvența urmează un prefix cunoscut. Numele ECO provin din catalogul englezesc CC0. Pentru regenerare, descărcați fișierele `a.tsv`–`e.tsv` de la revizia indicată și rulați scriptul de import cu chess.js 1.4.0. Guvernanța reia fiecare linie UCI și compară poziția rezultată cu EPD. [Revizia catalogului](https://github.com/lichess-org/chess-openings/tree/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39) · [Scriptul de import](../../scripts/import-review-openings.cjs)

Elo de referință al AI-ului provine din retestarea contra Stockfish 19 de mai sus. După minimum zece decizii ale jucătorului, estimarea lui folosește rezultatul (s) din perspectiva sa. Media scorului așteptat este `(1 + s) / 3`, iar diferența față de reper se obține cu formula Elo standard. Intervalul combină capetele reperului cu cuantilele de 5% și 95% ale distribuției actualizate de o singură partidă. O limită deschisă a reperului rămâne deschisă în afișaj.

Am verificat și o estimare Elo bazată exclusiv pe pierderea de scor la mutare. În eșantionul de 800 de partide Blitz 3+0 din ianuarie 2024, pe benzile 1200–2400, mediile lotului de calibrare au fost 74‰, 86‰, 73‰, 70‰ și 75‰. Lotul separat a dat 103‰, 77‰, 43‰, 73‰ și 54‰. Variația între benzi a dus la folosirea rezultatului contra reperului AI pentru Elo și a pierderii de scor pentru precizie și clasificare. [Arhiva Lichess](https://database.lichess.org/#standard_games) · [Game Rating pe Chess.com](https://support.chess.com/en/articles/10773754-how-is-game-rating-calculated-in-game-review)

### Indiciul pe deschiderile ligii

Pe aceleași 12 poziții de pornire, pierderea medie a indiciului față de Stockfish la adâncimea 13 a scăzut de la 127,3‰ la 73,0‰, apoi la 23,4‰ după verificarea repertoriului. Cazurile de cel puțin 150‰ au scăzut de la cinci la două, apoi la zero. Hard are același rezultat de 23,4‰ și zero pierderi mari. În Pirc, `…Bg7` înlocuiește `…Bd7` (296‰ pierdere), iar în Benoni, `…d6` înlocuiește `…Qa5` (299‰). Ambele replici coincid cu prima alegere Stockfish în aceste poziții. Testul guvernează pierderea cumulată, cazurile mari și plafonul de noduri. [Pozițiile ligii](../../atf-tests/src/test/resources/ai-league.yaml)

## Controlul deschiderilor

Suita Stockfish confirmă legalitatea tuturor mutărilor predate și verifică Hard și indiciul într-o poziție din fiecare lecție. Secvențele UCI sunt fixate în YAML. Guvernanța verifică acoperirea lecțiilor, ambele culori la mutare și corespondența fiecărei secvențe cu linia predată. Extent arată mutarea J7, mutarea Stockfish și ambele scoruri pentru fiecare caz. Porțile de calitate folosesc pierderea medie și numărul cazurilor cu o pierdere de cel puțin 150‰. [Poziții și praguri](../../atf-tests/src/test/resources/opening-ai-benchmark.yaml)

| Profil | Pierdere medie inițială | După prima extindere a lecțiilor | Cazuri ≥150‰ înainte / după |
|---|---:|---:|---:|
| Hard | 117,3‰ | 59,5‰ | 2 / 1 |
| Indiciu | 70,1‰ | 25,4‰ | 2 / 0 |

În Italiană, indiciul a trecut de la `Bd6` la `Nf6` și pierderea a coborât de la 178‰ la 0‰. În Scandinavă, a trecut de la `Nc6` la `Nf6`, cu pierdere de la 366‰ la 0‰.

### Calibrarea Hard și a barei

În Franceza de avans, după `5.Nf3`, Hard a trecut de la `…Qa5` la `…cxd4`. Pierderea a scăzut de la 277‰ la 21‰ față de `…Nge7` în Stockfish 19. Pe setul existent de deschideri, pierderea medie Hard a coborât de la 59,5‰ la 19,1‰ și nu au mai apărut pierderi de cel puțin 150‰. Cele șase poziții suplimentare din Franceză și Scoțiană au o pierdere medie de 39,7‰ și un maxim de 127‰.

După `1.e4 e5`, prima recomandare este `Nf3`, iar bara afișează `+0,19` față de `+1,40` anterior și `+0,43` în Stockfish 19. Pe șapte poziții suplimentare, prima calibrare a scăzut eroarea absolută medie de la 70,7 cp la 13,1 cp. Benchmarkul compară separat alegerea mutării în scor așteptat și estimarea barei în centipioni, din perspectiva albului. [Setul și porțile de calibrare](../../atf-tests/src/test/resources/opening-calibration.yaml) · [Scara Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Useful-data.html)

Când corecția la trei semimutări inversează semnul evaluării de la două, bara caută încă un orizont cu același număr de mutări pentru ambele culori. Dacă îl termină în bugetul existent, mediază cele două rezultate cu orizont par. După `1.d4 d5 2.c4`, bara trece de la `−0,19` la `+0,19`, față de `+0,22` în Stockfish 19. Eroarea scade de la 41 la 3 cp, iar media pe cele șapte controale ajunge la 7,7 cp. După `2…e6` eroarea observată este 33 cp, iar după `2…c6` este 8 cp. După acceptarea cu `2…dxc4`, bara afișează încă `−0,60` față de `+0,21`, un caz separat de compensare a pionului.

## Rezultatul de referință al primei extinderi

| Profil | Aceeași mutare ca Stockfish, înainte / după | Pierdere medie, înainte / după | Pierderi mari, înainte / după |
|---|---:|---:|---:|
| Basic | 5 / 6 din 10 | 202,7‰ / 202,4‰ | 2 / 2 |
| Advanced | 7 / 7 din 10 | 2,7‰ / 14,1‰ | 0 / 0 |
| Hard | 7 / 8 din 10 | 2,7‰ / 11,7‰ | 0 / 0 |
| Indiciu | 7 / 8 din 10 | 20,2‰ / 2,4‰ | 1 / 0 |

După calibrarea Hard, pierderea medie a acestui profil pe suita mixtă a scăzut de la 11,7‰ la 0‰. Regresia tactică din Siciliană păstrează `Nxe5` la Hard și la indiciu. Acest caz folosește altă poziție decât lecția Najdorf. Pragurile ambelor suite au rămas aceleași.
