# Evaluarea AI în Chess Lab

Ghidul descrie cum se verifică adversarii, indiciile și bara de evaluare.
Pentru categoriile mutărilor, precizie și Elo afișat, consultați
[contractul analizei](game-review.md). Calibrările experimentale descriu
sursa măsurată; aplicația folosește reperele din configurația guvernată.

Nivelul adversarului și indiciul au adâncimi și bugete configurate separat. O căutare păstrează ultima adâncime evaluată complet. Hard poate încerca șase semimutări în plafonul existent de 44.000 de noduri; acceptă rezultatul de la ultimul orizont numai dacă păstrează aceeași mutare lider ca adâncimea precedentă. În caz de schimbare a liderului, rămâne la ultima căutare completă și stabilă. Indiciul încearcă șase semimutări, cu o semimutare suplimentară pentru capturi, promovări sau ieșirea din șah la capătul căutării. În partide și lecții, evaluarea adaugă la material dezvoltarea pieselor, centrul, poziția regelui și perechea de nebuni. Ponderea termenilor se schimbă odată cu materialul rămas, astfel încât un rege activ să fie util în final. Căutarea verifică întâi capturile, promovările și mutările care activează piesele. Testele de tactică păstrează obiectivul lor explicit de material sau mat. [Politica de căutare](../../chess-core/src/main/resources/match-ai.yaml)

În finalul de turnuri, Hard folosește istoricul integral pentru a evalua repetițiile în arbore, nu doar la rădăcină. Poate extinde căutarea cu o semimutare peste adâncimea profilului, până la adâncimea 7, în același plafon de 44.000 de noduri. Extensia și fereastra de 50 cp pentru candidații rădăcină sunt fixate în `rook-endgame` din YAML; guvernanța respinge extensii sau ferestre în afara limitelor validate.

În jocurile Hard din afara repertoriului și a finalului special de turnuri, mutările care dau șah primesc prioritate numai în ordinea de căutare. Bonusul de 10.000 este configurabil și plafonat în `hard-ordering`; nu se adună la evaluarea poziției și nu crește limita de 44.000 de noduri. Basic, Advanced, indiciul și evaluarea barei păstrează ordonarea anterioară.

Calea de joc Hard reutilizează și liniile exacte pentru poziții transpozate. Intrările incomplete sau limitele alpha-beta nu sunt refolosite. Capacitatea este derivată și plafonată din buget în `hard-transpositions`. [Evaluarea cache-ului exact](#cache-exact-pentru-hard)

Basic caută două semimutări în pozițiile obișnuite și trei în finalurile elementare cu turnul ori dama contra regelui. Ambele căi folosesc zero noduri suplimentare. Adâncimea obișnuită se configurează prin `standard-max-plies` în YAML. Hard încheie căutarea la primul orizont complet care dovedește un mat câștigător. [Calibrarea Basic](#calibrarea-basic-cu-adâncime-selectivă) · [Dovezi de mat](#rezolvarea-quest-urilor-cu-obiectiv-declarat)

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
| Advanced | 4 | 10.000 |
| Hard | 6, cu lider stabil | 44.000 |
| Indiciu | 6, plus o extensie tactică | 220.000, inclusiv Hard și până la 32.000 pentru compararea alternativelor |

Guvernanța cere creșterea strictă a adâncimii și bugetului între adversari. Indiciul are cel puțin adâncimea Hard și propriul buget mai mare. În finalurile cu turn sau damă și regi, toate nivelurile, indiciul și bara folosesc aceeași evaluare a distanței dintre regi și a spațiului regelui apărat. Testele verifică transformarea avantajului în mat cu ambele culori și conservarea perspectivei albului în afișaj.

Protocolul ligii folosește aceleași deschideri cu ambele culori și aceleași
bugete. O partidă neterminată contribuie cu un interval, iar Elo relativ se
raportează împreună cu incertitudinea.

<details>
<summary>Repere istorice Hard6 și experimente respinse</summary>

### Reper Hard6 fără cache exact

Secțiunea păstrează rezultatele Hard6 înaintea cache-ului exact descris mai jos; valorile nu reprezintă comportamentul candidatului live actual.

Hard a păstrat plafonul de 44.000 de noduri, iar Basic și Advanced nu și-au schimbat profunzimea sau bugetul. O probă fără filtrul de stabilitate a coborât Intermezzo la 22/24. Varianta păstrată în candidatul live acceptă rezultatul de la adâncimea 6 numai dacă mutarea lider coincide cu cea de la adâncimea 5; altfel folosește rezultatul complet anterior.

Transferul Hard a trecut podelele înghețate: mat în 1 500/500, mat în 2 300/300, mat în 3 190/200, tactici 87/100, antrenament 40/48 și Intermezzo 24/24. Liga internă a măsurat Hard–Advanced la 20/1/0/3 și Elo pereche central +417 (interval 95% +172…∞), față de reperul anterior 19/1/0/4 și +374. Este o singură ligă internă, cu trei partide neterminate; diferența modestă nu constituie o nouă calibrare Elo.

Ultima calibrare externă Stockfish 19 înaintea probei Hard6 a estimat 1296/1406/1544 pentru Basic/Advanced/Hard, cu intervale empirice largi. Ancorele afișate în aplicație rămân însă cele verificate și guvernate în `game-review.yaml`: 1340/1461/1549, legate de observațiile cu sursa `b5c1ef3`. Nu am transferat calibrarea TMP în aplicație; ratingul Hard6 cere trei sesiuni externe pe același set de 12 deschideri și culori. Proba curentă a rămas offline. Rapoartele Extent și rejucările experimentelor sunt păstrate în TMP, nu sunt artifacte de release.

### A/B offline respinse

PVS aplicat pe toate iterațiile a coborât Mat în 3 la 188/200, sub podeaua 190, și a fost respins fără liga de joc. PVS numai la adâncimea finală a trecut toate podelele, dar liga a rămas 20/1/0/3 și +417, identic cu Hard6 fără PVS; complexitatea în plus nu a adus forță măsurată. LMR pentru mutări liniștite târzii a trecut transferul, dar atât pragul după mutarea a patra, cât și cel după a opta au coborât Hard–Advanced la 19/1/1/3 și +338. Ambele variante LMR sunt respinse. Niciuna nu a fost copiată în codul live; rapoartele sunt în TMP.

Extensia tactică la fiecare frunză a coborât Antrenamentul la 36/48, sub
pragul 39. Extensia numai din șah a coborât matul în trei la 185/200, iar
ordonarea killer la 169/200. Aceste variante au fost respinse înaintea ligii.

</details>

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

`HardQuestTransferBenchmarkTest` măsoară mutarea aleasă de adversarul Hard
pe catalogul încărcat la execuție. La mat se verifică prima cheie; la
exercițiile materiale, soluția catalogului. Pragul de Antrenament tactic
folosește lotul fix `training-001`–`training-048`; Extent afișează și totalul
capitolului activ. [Praguri](../../atf-tests/src/test/resources/hard-quest-transfer.yaml)

Rezultatele de mai jos sunt reperul istoric al experimentului de ordonare,
anterior catalogului și politicii actuale.

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

Metoda de [evaluare a unei semimutări](game-review.md#evaluarea-unei-semimutări),
[categoriile și precizia](game-review.md#categorii-și-precizie) și
[estimarea Elo](game-review.md#cum-se-estimează-elo) sunt explicate la owner.
Reperele AI afișate folosesc calibrarea externă guvernată din
[`game-review.yaml`](../../chess-core/src/main/resources/game-review.yaml).

Indexul recunoaște 3.865 de linii ECO. Caută ultima poziție denumită, inclusiv când partida ajunge la ea prin transpoziție, și marchează mutările de carte cât timp secvența urmează un prefix cunoscut. Numele ECO provin din catalogul englezesc CC0. Pentru regenerare, descărcați fișierele `a.tsv`–`e.tsv` de la revizia indicată și rulați scriptul de import cu chess.js 1.4.0. Guvernanța reia fiecare linie UCI și compară poziția rezultată cu EPD. [Revizia catalogului](https://github.com/lichess-org/chess-openings/tree/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39) · [Scriptul de import](../../scripts/import-review-openings.cjs)

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

## Cache exact pentru Hard

Am activat reutilizarea liniilor căutate complet numai pe calea adversarului Hard. Intrarea este cheia poziției (inclusiv partea la mutare, rocada și captura en passant legală), semimutările rămase, distanța față de rădăcină, obiectivul evaluării și extensia tactică. Păstrarea distanței evită reutilizarea unui scor de mat cu altă valoare. Cache-ul acceptă numai rezultate exacte; o întrerupere alpha-beta sau un rezultat incomplet nu poate deveni oracol pentru o altă ramură. Căutările Basic, Advanced, indiciul, bara și analiza de partidă rămân pe calea existentă.

Capacitatea este configurabilă în `match-ai.yaml`: 500 de intrări la 1.000 de noduri Hard suplimentare, cu plafon de 50.000. La bugetul actual de 44.000 rezultă maximum 22.000 de intrări. Am comparat și plafonul de 44.000; acesta nu a îmbunătățit rata la quest-uri sau liga, așa că păstrăm varianta cu memorie mai mică.

Am rulat aceeași ligă pe 12 deschideri noi, ambele culori, 24 de partide pentru fiecare pereche de profiluri, în copii TMP cu cod Hard6 identic în afara cache-ului. Advanced–Basic a rămas 16/7/1/0 în ambele. Hard6 față de Hard6+cache a fost:

| Pereche | Hard6 de referință | Hard6 cu cache exact | Estimare internă centrală |
|---|---:|---:|---:|
| Hard–Basic | 20/3/1/0 | 22/1/1/0 | +374 → +470 |
| Hard–Advanced | 21/1/0/2 | 23/1/0/0 | +470 → +669 |

Aceste conversii sunt doar scoruri relative în liga J7 și nu reprezintă Elo extern. Pe setul înghețat existent, câștigul a fost mai mic: o remiză a devenit victorie în Hard–Advanced. Podelele de transfer au rămas neschimbate: M1 500/500, M2 300/300, M3 194/200, tactici 87/100, antrenament 39/48 și Intermezzo 24/24. Testul de guvernanță verifică factorul, plafonul, dezactivarea și respinge valorile din afara intervalului.

Rapoartele Extent și replay-urile TSV pentru candidatul, referință și capacitatea dublă au rămas în `C:\Users\Cristi\AppData\Local\Temp\j7-hard-tt-*`; nu sunt artefacte de release. [Terminologia oficială Stockfish despre transpoziții și căutare](https://official-stockfish.github.io/docs/stockfish-wiki/Terminology.html) explică rolul tabelului de transpoziții în reutilizarea pozițiilor. Documentația nu oferă o creștere Elo garantată pentru această implementare.

Reperele configurate în aplicație provin din sursa anterioară căutării Hard6. Verificarea plafonului Advanced și a progresului Hard folosește partide cu executabilul Stockfish 19 local, identificat prin amprenta din `ai-anchor-observations.yaml`. Estimările depind de adversar, buget și protocol. [Calibrarea UCI Elo în Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)

### Prototip local de limite alpha-beta · neintegrat

Datele de mai jos descriu un prototip rulat separat în TMP, nu codul candidatului. Aplicația păstrează exclusiv linii exacte; nu memorează și nu reutilizează limite alpha-beta. Prototipul nu a fost promovat deoarece reducerea nodurilor la Mat în doi a venit cu o poziție mai puțin rezolvată la Mat în trei. Nu prezentați rezultatele sale drept comportament livrat.

Primul prototip păstrează adâncimea și distanța de la rădăcină în cheie. O limită inferioară se poate reutiliza dacă atinge beta, iar o limită superioară dacă este cel mult alpha. La nivelul care întoarce variantele către rădăcină se reutilizează numai linii exacte. Proba diferențială a verificat 60 de cazuri cu ferestre înguste, reutilizare și revenire la fereastră completă. Scorul final a coincis cu cel calculat fără cache. [Implementarea de referință Stockfish 19](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp)

Cu 44.000 de noduri și 22.000 de intrări, prototipul a trecut pragurile de transfer. Mat în două a rămas la 300/300, cu o medie de 12.430 de noduri față de 13.377. Mat în trei a coborât de la 194 la 193/200. Tactica a rămas la 87/100, antrenamentul la 39/48 și Intermezzo la 24/24.

În primele cinci înfrângeri din prima serie de calibrare, prima pierdere majoră identificată de Stockfish la adâncimea 12 a apărut la semimutările 10, 13, 34, 17 și 16. Hard consumase toate cele 44.000 de noduri în fiecare poziție. Cazul cel mai sever din eșantion a fost Gambitul Regelui cu negrul la semimutarea 16. Stockfish a dat `d7d5` la +91 cp și mutarea Hard `d7d6` la −339 cp.

În duelul cu versiunea live, prototipul a obținut 11 victorii, 4 remize și 9 înfrângeri în 24 de partide, cu culori inversate și plafon de 160 de semimutări. Estimarea diferenței este +29 Elo, cu interval de 95% între −58 și +120. Ordonarea după mutarea memorată la aceeași adâncime a produs un fișier de rejucare identic. Preluarea ordinii din iterația anterioară a trecut pragurile de transfer, dar a crescut media la Mat în trei de la 13.705 la 14.573 de noduri față de prototipul cu limite.

### Calibrarea versiunii live

Sursa `0e6a62f` a jucat 216 partide în trei serii, cu Stockfish 19 la `UCI_Elo 1500`, un fir, 6.000 de noduri cerute pe mutare și maximum 160 de semimutări. Am folosit cele 12 deschideri din protocolul de calibrare și am inversat culorile. Intervalele de 95% reeșantionează deschiderile, iar partidele active la plafon contribuie între zero și un punct.

| Profil | Victorii / remize / înfrângeri / neterminate | Elo estimat | Interval 95% |
|---|---:|---:|---:|
| Basic | 7 / 1 / 49 / 15 | 1268 | 922–1442 |
| Advanced | 23 / 3 / 30 / 16 | 1466 | 1316–1615 |
| Hard | 30 / 2 / 23 / 17 | 1534 | 1396–1672 |

Advanced cu 10.000 de noduri suplimentare și patru semimutări a jucat încă 72 de partide după același protocol. A obținut 19 victorii, 3 remize, 35 înfrângeri și 15 partide neterminate. Estimarea centrală a coborât de la 1466 la 1421, cu intervalul 1245–1553. În suita de zece poziții, pierderea medie Advanced a rămas la 14,1‰ și au existat zero pierderi majore. Estimarea centrală este sub plafonul cerut de 1450, iar intervalul încă îl traversează.

### Captura de la capătul căutării Hard

În poziția Siciliană din suita fixă, căutarea de patru semimutări alege `Nxe5` și vede replica. La cinci semimutări, caută `Nc3` și mută aceeași captură la capătul variantei. Prototipul a păstrat clasamentul par când liderul la adâncime impară amâna captura până la orizont.

Prototipul a crescut de la 9/10 la 10/10 alegeri identice cu Stockfish. Pierderea medie a scăzut de la 45,9‰ la 0‰, iar scorurile din cele 1.172 de probleme au rămas la 500/500, 300/300, 194/200, 87/100, 39/48 și 24/24. În duelul cu versiunea live a înregistrat 8 victorii, 6 remize și 10 înfrângeri. Diferența estimată este −29 Elo, cu interval de 95% între −89 și 0. Contra Stockfish, după 72 de partide, a obținut 1519 Elo (1357–1691) față de 1534 pentru referință. Duelul a dus la retragerea prototipului din codul live.

Comparația indiciului cu Hard pe 12 deschideri are o diferență cumulată de 281‰ față de 257‰. În Catalană, indiciul alege `c4d5` cu o pierdere de 49‰, iar Hard alege `b1c3` cu 25‰.

### Matul demonstrat și reutilizarea pozițiilor

Acceptarea unui mat demonstrat la ultima adâncime, indiferent de liderul anterior, a păstrat toate scorurile pe probleme. În 24 de partide, fiecare pereche cu culori inversate a produs aceeași secvență de mutări și duelul s-a încheiat cu 9 victorii, 6 remize și 9 înfrângeri. Prototipul rămâne în TMP.

Reutilizarea pozițiilor generate pentru ordonarea șahurilor a păstrat mutarea, adâncimea și nodurile pe 48 de poziții. Medianele au fost 594 și 639 ms față de 535 și 304 ms pentru referință în două rulări alternante. Alte probe rulau pe aceeași mașină, astfel că măsurarea are variație de sarcină. Implementarea cu hărți de poziții a fost respinsă.

### Bugetul experimental de 88.000 de noduri

La șase semimutări și 88.000 de noduri suplimentare, Hard a obținut 34 de victorii, o remiză, 19 înfrângeri și 18 partide neterminate după 72 de partide contra Stockfish 19. Estimarea este 1573 Elo (1432–1798), față de 1534 (1396–1672) la 44.000. În Mat în trei, rezultatul a crescut de la 194 la 198/200. Celelalte cinci categorii au păstrat scorurile. Media la tactică a crescut de la 29.626 la 45.761 de noduri. Pe zece poziții fixe a rămas aceeași pierdere medie de 45,9‰, cu o pierdere majoră în Siciliană.

Reeșantionarea perechilor după deschidere, cu jumătate de punct pentru partidele neterminate, estimează diferența la +39 Elo, cu interval de 95% între −54 și +132. Pe 48 de poziții, mediana a crescut de la 534 la 916 ms. Perechea de măsurări a rulat pe aceeași mașină, cu duelul JVM activ în fundal.

## Calibrarea Basic cu adâncime selectivă

Am jucat câte 72 de partide per variantă, în trei sesiuni, cu Stockfish 19 la `UCI_Elo 1500` și 6.000 de noduri cerute pe mutare. Am folosit aceleași 12 deschideri cu culori inversate și plafonul de 160 de semimutări. V, R, Î și N înseamnă victorii, remize, înfrângeri și partide neterminate pentru Basic.

| Variantă | V / R / Î / N | Elo estimat | Interval 95% |
|---|---:|---:|---:|
| Referință la trei semimutări | 7 / 1 / 49 / 15 | 1268 | 922–1442 |
| Două semimutări | 3 / 0 / 63 / 6 | 1083 | −∞–1322 |
| Două semimutări, trei în șah | 4 / 0 / 59 / 9 | 1151 | −∞–1346 |
| Basic livrat, lotul de referință | 2 / 0 / 63 / 7 | 1067 | −∞–1253 |
| Basic livrat, 12 deschideri separate | 2 / 1 / 63 / 6 | 1067 | −∞–1245 |
| Basic livrat, ambele loturi | 4 / 1 / 126 / 13 | 1067 | 638–1225 |

Excepția pentru șah a estimat 1253 Elo pe încă 72 de partide cu 12 deschideri diferite. Două semimutări în toate pozițiile au estimat 1173 Elo pe aceleași deschideri noi, dar testul de conversie a prins un final de turn încheiat fără mat la plafonul de 30 de semimutări. Codul acordă a treia semimutare numai în finalurile elementare cu turnul ori dama contra regelui și trece din nou testele de conversie. Estimarea centrală este sub 1200 în ambele loturi, cu intervale care depășesc 1200. Ratingul istoric din `game-review.yaml` rămâne legat de dovada și commitul său. [Protocolul Stockfish](#calibrarea-versiunii-live)

## Recaptura Hard la orizont

Prima probă a verificat o singură replică de recaptură pe pătratul ultimei capturi la orizont, cu nodurile scăzute din plafonul de 44.000. Pozițiile în șah păstrează evaluarea de bază, iar cache-ul include pătratul recapturii. Tehnica aplică adâncime selectivă schimburilor de material. [Căutare la adâncime variabilă](https://ijcai.org/proceedings/1983/183)

Pe zece poziții fixe, pierderea Hard a scăzut de la 45,9‰ la 2,4‰ și pierderile majore de la una la zero. Tactica a urcat de la 87/100 la 92/100. Mat în trei a coborât de la 194/200 la 193/200, iar `intermezzo-012` a coborât capitolul de la 24/24 la 23/24. Proba a încălcat pragul Intermezzo și a rămas în TMP.

Limitarea recapturii la adâncimi pare a păstrat scorurile tuturor capitolelor de referință, inclusiv 194/200 la Mat în trei și 24/24 la Intermezzo. În lotul fix Stockfish a rămas însă pierderea de 45,9‰, cu aceeași pierdere majoră. Colecția extinsă a dat 61/72 la Antrenament tactic cu Hard live, dintre care 22/24 în exercițiile noi. [Selecția publică](quest-curation.md#antrenament-tactic-și-lot-de-verificare)

Recaptura aplicată numai după ce o piesă ia una mai ieftină a trecut toate pragurile fixe. A obținut 95/100 la Tactică, 24/24 la Intermezzo și 192/200 la Mat în trei, față de 194/200 în codul live. Pe lotul fix Stockfish a păstrat 2,4‰ și zero pierderi majore. Pe 24 de poziții din 12 deschideri separate, cu ambele culori la mutare și Stockfish 19 la adâncimea 15, pierderea medie a urcat de la 53,0‰ la 68,0‰. Candidatul a îmbunătățit șase decizii, a egalat 14 și a înrăutățit patru. În Scandinavă, după `4.d4`, `…Nc6` a pierdut 399‰ față de `…Nf6`. În Siciliana clasică după `6.Bg5`, `…Nxe4` a pierdut încă 244‰ față de `…Bg4`. Candidatul rămâne în TMP.

În duelul direct, cu aceleași 12 deschideri și culori inversate, candidatul a obținut 10 victorii, 4 remize, 5 înfrângeri și 5 partide neterminate. Diferența estimată în duel este +73 Elo, cu interval 95% de la −137 la +307. Lotul independent și intervalul larg lasă creșterea Hard neconfirmată.

Restrângerea la recapturi după ultima mutare a lui Hard a trecut lotul fix, dar a crescut pierderea la 79,1‰ în cele 24 de poziții independente. Rezultatul arată că filtrul pe culoare singur nu rezolvă regresia.

## Rezolvarea quest-urilor cu obiectiv declarat

`HardQuestSolver` primește poziția și obiectivul. Pentru mat caută succesiv la o semimutare, trei și cinci, încearcă întâi șahurile și păstrează și mutările liniștite. O mutare de atac se dovedește numai dacă fiecare apărare legală permite continuarea. Tabela păstrează rezultatele exacte pe poziție și semimutările rămase. Plafonul este de 100.000 de noduri, iar epuizarea lui produce un rezultat distinct, fără soluție. Pentru material, aceeași evaluare minimax la trei semimutări cere o cheie unică și câștig forțat de cel puțin 200 cp. [Căutarea de dovezi în arbori de joc](https://research.tilburguniversity.edu/en/publications/proof-number-search-2/)

| Probă pe cele 200 de poziții de mat în trei | Soluții | Noduri medii | Maxim |
|---|---:|---:|---:|
| A · Hard în partidă | 194/200 | 14.446 suplimentare | 44.000 suplimentare |
| B · Dovadă, ordinea generatorului | 200/200 | 21.750 | 344.364 |
| C · Dovadă, șahuri înaintea mutărilor liniștite | 200/200 | 2.746 | 84.920 |

Comparația B/C folosește același plafon experimental de un milion de noduri. C a trecut apoi plafonul de 100.000 și a rezolvat 1.220/1.220 de quest-uri publice și 24/24 din lotul independent. Cheile YAML intră numai în aserțiune. Numărul de noduri al rezolvării și cel suplimentar al adversarului măsoară căutări diferite.

În partida Hard, oprirea la primul mat demonstrat a păstrat 194/200 la mat în trei și a redus media de la 14.446 la 9.657 de noduri suplimentare. Cele 24 de poziții independente au avut aceleași decizii, iar toate cele patru perechi de partide au avut secvențe identice, cu bilanț 3/2/3. Am integrat oprirea la dovadă ca economie de căutare. Poarta Stockfish rămâne la 45,9‰ și o pierdere majoră. Varianta cu fereastră nulă după prima ramură a obținut aceeași pierdere și a rămas în TMP. Ratingul Hard rămâne la estimarea de referință. [Protocol Fishtest](https://official-stockfish.github.io/docs/fishtest-wiki/Creating-my-first-test.html)

Rezervarea a 5.000 de noduri din bugetul de 44.000 pentru dovadă a dus la 199/200 la mat în trei. Antrenamentul a trecut de la 80/96 la 81/96, iar Tactica de la 87/100 la 86/100. Pe cele 24 de poziții independente cu istoric a ales aceleași mutări. Proba limita dovada la pozițiile primite fără istoric. A rămas în TMP. Cheia liniștită `…Nb4` din `mate3-005wy` a cerut 26.380 de noduri în căutarea de dovadă separată.

## Costul mutărilor legale și repertoriul Hard

Motorul păstrează pozițiile regilor când aplică o mutare. Verificarea șahului pornește de la rege, verifică atacurile scurte și se oprește la prima piesă pe fiecare rază. Direcțiile și atacurile folosesc în continuare regulile încărcate din YAML. La patru semimutări, cele patru poziții de control au produs 197.281, 4.085.603, 43.238 și 2.103.487 de continuări, identice cu Stockfish 19. Componenta verifică un subset rapid din aceleași poziții. [Poziții perft Stockfish](https://github.com/official-stockfish/Stockfish/blob/sf_19/tests/perft.sh)

Pe 48 de poziții, mutarea, adâncimea și nodurile Hard au coincis în cele patru rulări alternante. Medianele locale au fost 879 și 934 ms pentru generatorul anterior și 281 și 478 ms pentru cel actual. Percentila 95 a variat între 664 și 2.097 ms cu generatorul actual, sub sarcina rulărilor paralele. Măsurarea descrie JVM-ul local.

La 44.000 de noduri, evaluarea suplimentară a pionilor a crescut pierderea medie pe 24 de poziții independente de la 28,7‰ la 39,8‰. Adăugarea activității turnurilor și a protecției regelui a dus-o la 49,3‰. Bugetele experimentale de 110.000 și 175.000 au păstrat pierderea majoră din Siciliana cu `…e5`. Aceste variante rămân în TMP. [Model de evaluare pe faze](https://github.com/official-stockfish/Stockfish/blob/sf_14/src/evaluate.cpp)

În duelurile pe șase deschideri cu culori inversate, varianta cu pioni a avut 5 victorii, 5 înfrângeri și 2 partide neterminate. Varianta cu turnuri și protecția regelui a avut 4 victorii, 6 înfrângeri și 2 neterminate. Estimările diferenței au fost 0 Elo (−280–+280) și −58 Elo (−280–+191). [Metodologia Fishtest](https://official-stockfish.github.io/docs/fishtest-wiki/Creating-my-first-test.html)

Repertoriul Hard are șase replici noi pe poziții exacte. Stockfish 19 a ales aceeași replică la adâncimile 13, 16 și 19 pentru fiecare poziție. În Siciliana cu `…e5`, `Nxe5` a obținut la adâncimea 19 un scor așteptat de 993‰, față de 519‰ pentru `Nc3`. În Siciliana clasică, `…e6` a obținut 458‰ față de 277‰ pentru `…Bg4`. După replica de repertoriu, Hard caută continuarea cu plafonul de 44.000. [Repertoriu YAML](../../chess-core/src/main/resources/ai-repertoire.yaml)

Poarta fixă Stockfish a trecut cu 10/10 alegeri identice, pierdere medie 0‰ și zero pierderi majore. Poziția Siciliană intră în repertoriu, astfel că rezultatul măsoară și acoperirea repertoriului. Pe lotul anterior de 24 de poziții, 23 de decizii au coincis și una s-a îmbunătățit, cu pierdere medie de la 53,0‰ la 44,5‰. Lotul include Siciliana clasică din repertoriu. În cele 12 partide începute din pozițiile celor șase replici, cu culorile inversate, candidatul a obținut 6 victorii, o remiză, 3 înfrângeri și 2 partide neterminate. Estimarea diferenței este +89 Elo, cu interval de 95% de la −154 la +338. [Protocolul ancorei](#calibrarea-versiunii-live)

### Recalibrare pe cele 12 deschideri standard

Fiecare rând are 24 de partide cu Stockfish 19 la `UCI_Elo 1500`, un fir, 6.000 de noduri cerute pe mutare și ambele culori. Bilanțul este victorii, remize, înfrângeri și partide neterminate.

| Profil | Bilanț | Elo estimat | Interval 95% |
|---|---:|---:|---:|
| Basic | 2 / 0 / 22 / 0 | 1083 | −∞–1268 |
| Advanced | 6 / 0 / 11 / 7 | 1427 | 955–1691 |
| Hard înaintea repertoriului nou | 12 / 1 / 8 / 3 | 1558 | 1363–1755 |
| Hard cu repertoriul nou | 8 / 0 / 8 / 8 | 1500 | 1268–1780 |

Reluarea secvențelor UCI confirmă zero întâlniri cu cele șase poziții noi la mutarea lui Hard în ambele serii. Cele două estimări Hard diferă între rulările ancorei cu putere limitată. Duelul din pozițiile de repertoriu oferă măsura directă a schimbării, cu interval care include zero. [Protocolul ancorei](#calibrarea-versiunii-live)

Duelul direct pe alte 12 deschideri a dat 13 victorii, două remize, șapte înfrângeri și două partide neterminate pentru candidatul de 88.000. Diferența estimată este +89 Elo, cu intervalul 95% de la −14 la +232. Păstrăm bugetul live de 44.000 și folosim proba cu 88.000 pentru a identifica pozițiile unde căutarea selectivă poate economisi noduri.

## Captura imediată de pion în căutarea Hard

Când adâncimea pară a clasat prima o captură imediată de pion, Hard poate păstra acel rezultat dacă linia impară preferă o mutare liniștită și abia la ultima semimutare capturează același pion. Condițiile cer material cel puțin egal în linia capturii imediate, un orizont complet pentru ambele variante și pionul prezent pe aceeași casă până la captură. Matul dovedit are prioritate. Regula se aplică în căutarea obișnuită Hard, în afara repertoriului și a căutării de final de turnuri. Cheia `hard-horizon.preserve-pawn-capture` permite dezactivarea pentru A/B. [Politica YAML](../../chess-core/src/main/resources/match-ai.yaml)

| Lot | Referință → candidat | Martor |
|---|---|---|
| Siciliana cu pion expus, două poziții de reglaj și două de verificare | `Nc3 → Nxe5` în toate cele patru poziții | Stockfish 19 a ales `Nxe5` la adâncimile 14, 17 și 19. |
| Poziții cu negrul la mutare | `Nc6 → Nxe4` într-o poziție; două capturi deja corecte se păstrează | Stockfish 19 a ales `Nxe4` la toate cele trei adâncimi. |
| Pion apărat sau damă pe c7 | Deciziile martor rămân neschimbate | La adâncimea 19, Stockfish preferă dezvoltarea. |
| Tactică în Siciliana cu …h6 și Nc3 | Hard păstrează `Nd5`; Stockfish preferă `Nxe5` | Orizontul de patru indică pierdere de material, iar următorul nu se termină în buget. |

Pe patru poziții tematice, fiecare versiune a jucat cu ambele culori contra Stockfish 19, `UCI_Elo 1500`, un fir și 6.000 de noduri cerute pe mutare. A și B au refolosit răspunsul Stockfish cât timp istoricul a coincis. Șase trasee au rămas identice. În celelalte două, B a câștigat cu albele la semimutările 41 și 39; A a rămas activă la plafonul 180 și a câștigat la 135. Bilanțurile sunt 4 / 0 / 3 / 1 pentru A și 5 / 0 / 3 / 0 pentru B. Intervalele estimărilor se suprapun.

Poarta fixă a păstrat 10/10 pentru Hard și plafonul de 44.000 de noduri suplimentare. Pe probleme fără obiectiv transmis motorului, rezultatele au rămas 500/500, 300/300, 194/200, 87/100, 80/96 și 24/24. Solverul cu obiectiv declarat a păstrat 1.220/1.220 publice și 24/24 independente. Testul comparativ al indiciului a dat aceeași abatere în A și B, 281‰ cumulat pentru indiciu și 257‰ pentru Hard pe 12 poziții. Diferența vine din Catalană, unde indiciul preferă `cxd5` cu 49‰ pierdere, față de `Nc3` cu 25‰ la Hard. [Feedback AI](../feedback/README.md)

În repetarea tematică, după restrângerea regulii la același pion și la orizontul imediat precedent, A a obținut 3 / 0 / 2 / 3 și B 5 / 0 / 2 / 1. Una dintre cele două partide câștigate în plus s-a schimbat ulterior față de poziția de pornire. Pe șase deschideri neutre cu răspunsuri comune, cele 12 trasee au coincis și fiecare variantă a obținut 7 / 0 / 3 / 2. Estimarea Hard este 1620 Elo, cu intervalul 1500–1917. Lotul neutru nu demonstrează un câștig general.

| Profil | Ultimul lot neutru | Elo estimat | Interval 95% |
|---|---:|---:|---:|
| Basic | 0 / 0 / 11 / 1 | 955 | −∞–1309 |
| Advanced | 4 / 0 / 5 / 3 | 1471 | 1083–1917 |
| Hard | 7 / 0 / 3 / 2 | 1620 | 1500–1917 |

Fiecare profil are câte 12 partide cu aceeași ancoră. Basic și Advanced folosesc seria de calibrare, iar Hard seria A/B cu răspunsuri comune. Intervalele rămân largi. Reperele afișate în aplicație provin în continuare din calibrarea guvernată. [Protocolul ancorei](#calibrarea-versiunii-live)

## Verificarea schimburilor după o captură de pion

Hard pornește de la clasamentul obișnuit. Într-o poziție cu material egal și fără șah la rădăcină, o captură de pion care conduce la trei semimutări, cu un câștig aparent de cel puțin doi pioni, poate fi verificată dacă liderul de la patru semimutări este altul și următoarea iterație nu furnizează un rezultat acceptabil. Comparația caută separat cele două mutări la aceeași adâncime, cu până la două semimutări pentru capturi, promovări și ieșiri din șah. Captura trece înainte doar dacă ambele verificări se termină, scorul ei e mai bun și linia câștigă material de cel puțin un pion. Rezerva `hard-horizon.pawn-exchange-nodes` consumă până la 7.000 din cei 44.000 de noduri suplimentare și se poate dezactiva cu valoarea zero. [Politica YAML](../../chess-core/src/main/resources/match-ai.yaml)

După `1.e4 c5 2.Nf3 h6 3.Nc3 e5`, căutarea la patru semimutări ajungea la `Nxe5 Qf6 Nxf7 Qxf7` și evalua o pierdere de material. Verificarea completă compară și alte răspunsuri. Stockfish 19, la adâncimea 20 cu mutarea forțată, a dat +241 cp pentru `Nxe5` și +37 cp pentru `Nd5`. Șase poziții de reglaj și patru dintre cele șase poziții de verificare au trecut de la o mutare diferită la `Nxe5`. Celelalte două poziții de verificare au păstrat o captură bună și au refuzat o captură de pion apărat. Martorii cu dama pe c7, calul pe c6 sau pionul pe d6 au păstrat deciziile anterioare.

În cele 16 partide tematice contra Stockfish 19 cu culori inversate, bilanțurile au fost 5 / 0 / 2 / 1 pentru A și 3 / 0 / 3 / 2 pentru B. Pe lotul neutru, bilanțurile au fost 4 / 0 / 7 / 1 pentru A și 5 / 0 / 6 / 1 pentru B. În duelul direct pe patru deschideri tematice, B a obținut 3 / 0 / 2 / 3, cu o diferență estimată de +44 Elo și interval 95% de la −338 la o limită superioară deschisă. Bilanțul este victorii, remize, înfrângeri și partide neterminate. Răspunsul Stockfish este reutilizat cât timp istoricul coincide. Rezultatele confirmă corecția deciziei de captură, iar conversia avantajului rămâne o prioritate. [Metoda perechilor](https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html) · [Feedback AI](../feedback/README.md)

| Profil | Lot neutru | Stockfish 19 `UCI_Elo` | Elo estimat | Interval 95% |
|---|---:|---:|---:|---:|
| Basic | 2 / 0 / 10 / 0 | 1320 | 1040 | −∞–1200 |
| Advanced | 1 / 0 / 6 / 5 | 1500 | 1346 | −∞–1780 |
| Hard B | 5 / 0 / 6 / 1 | 1500 | 1471 | 1309–1691 |

Basic a pierdut toate cele 12 partide contra ancorei 1500. Rularea contra 1320 oferă o estimare finită. Fiecare profil are câte șase perechi, cu un fir Stockfish și 6.000 de noduri cerute pe mutare. Ratingurile depind de lot și de adversar. [Calibrarea Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)
