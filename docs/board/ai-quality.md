# Evaluarea AI în Chess Lab

Nivelul adversarului și indiciul au adâncimi și bugete configurate separat. O căutare păstrează ultima adâncime evaluată complet. Indiciul încearcă cinci semimutări, cu o semimutare suplimentară pentru capturi, promovări sau ieșirea din șah la capătul căutării. În partide și lecții, evaluarea adaugă la material dezvoltarea pieselor, centrul, poziția regelui și perechea de nebuni. Ponderea termenilor se schimbă odată cu materialul rămas, astfel încât un rege activ să fie util în final. Căutarea verifică întâi capturile, promovările și mutările care activează piesele. Testele de tactică păstrează obiectivul lor explicit de material sau mat. [Politica de căutare](../../chess-core/src/main/resources/match-ai.yaml)

Indiciul pornește de la căutarea Hard și verifică separat maturile și răspunsurile tactice. Dacă primele mutări diferă fără un mat dovedit, motorul compară alternativele la adâncimea Hard deja încheiată. Dacă verificarea își consumă bugetul, indiciul folosește rezultatul complet Hard. Lista arată până la cinci prime mutări distincte, la cel mult 25 cp de prima. La mat rămân doar variantele cu același rezultat și aceeași distanță. La scor egal, activitatea mutării și ordinea UCI stabilesc ordinea.

La Hard, într-o poziție cu material egal, clasamentul disponibil după patru semimutări folosește și scorul aceleiași mutări la trei. Contribuția liniei mai scurte este limitată. Pentru o mutare liniștită, creditul scade când linia provizorie sugerează un câștig de peste un pion. Maturile și remizele evaluate păstrează scorul terminal. Dacă se încheie căutarea la cinci semimutări, motorul folosește clasamentul acelei căutări. Bugetul Hard este de 44.000 de noduri suplimentare.

În Pirc și Benoni, Hard și indiciul folosesc câte o replică verificată cu Stockfish 19 la adâncimea 13. Repertoriul se aplică la aceeași poziție legală, inclusiv prin transpoziție, iar continuarea este căutată în bugetul profilului. După replică sau în poziții vecine se folosește căutarea integrală. Încărcarea verifică legalitatea, pozițiile duplicate și maturile într-o mutare. [Repertoriu YAML](../../chess-core/src/main/resources/ai-repertoire.yaml)

Într-un ciclu, Hard poate alege o poziție vizitată de mai puține ori dacă estimează că rămâne cu cel puțin 100 cp avantaj și cedează cel mult 50 cp față de prima linie. Partea în dezavantaj poate încheia partida prin a cincea apariție când mutarea legală există. [Politica YAML](../../chess-core/src/main/resources/match-ai.yaml) · [Partida de regresie](../../atf-tests/src/test/resources/repetition-regression.yaml)

Bara și prima linie de text folosesc o estimare separată pentru poziția curentă. Motorul evaluează două semimutări, câte una pentru fiecare parte, și aplică o parte din diferența obținută la trei. Ambele căutări verifică schimburile la orizont. Corecția este limitată la 25 cp, în bugetul propriu al afișajului. Apăsarea becului păstrează estimarea afișată și parcurge variantele în ordinea căutării de indicii.

## Control la fiecare schimbare

1. Rulați suita JVM înainte de schimbare. Comparați din aceeași copie TMP, cu același executabil Stockfish și același YAML de evaluare. Păstrați raportul Extent din fiecare rulare.
2. Înregistrați poziția în care decizia a fost slabă, mutarea aleasă, replica omisă și evaluarea de referință. Folosiți aceste exemple pentru ajustarea căutării. Păstrați setul de control fix în timpul ajustării și adăugați separat poziții noi pentru verificare.
3. Comparați Basic, Advanced, Hard și prima recomandare pentru fiecare poziție. Urmăriți pierderea medie a scorului așteptat, numărul pierderilor mari și potrivirile exacte. Verificați adâncimea finalizată, nodurile suplimentare și latența. Comparați latențele pe același dispozitiv, în condiții similare.
4. La regresie, deschideți în Extent pozițiile cu pierderea cea mai mare și verificați dacă mutarea a fost depunctată din cauza orizontului, a unei egalități sau a bugetului epuizat. Mențineți pragurile în timpul experimentului și justificați separat orice revizie a setului sau a pragurilor.

```powershell
mvn test '-Dtest=MatchAiBenchmarkTest' "-Dchess.stockfish=$stockfish" '-Dchess.extent=target/match-ai-benchmark.html'
```

Setați `$stockfish` la executabilul Stockfish 19. Verificarea pornește câte o căutare la adâncimea fixată în YAML pentru poziția întreagă și pentru mutarea J7, cu un fir, 16 MiB de hash și memorie resetată între căutări. Scorul așteptat Stockfish este `W + D/2` în miimi. Diferența dintre căutări se plafonează la zero când cea restrânsă găsește o evaluare mai bună. Raportul arată și scorul UCI pentru fiecare mutare, ca să se vadă maturile sau diferențele care apar la un scor așteptat deja saturat. [Set și praguri](../../atf-tests/src/test/resources/match-ai-benchmark.yaml) · [Stockfish 19](https://github.com/official-stockfish/Stockfish/releases/tag/sf_19) · [Scorurile WDL Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Useful-data.html)

Scorurile acestui set măsoară alegerea unei mutări din poziții fixe. Pentru o estimare Elo, folosiți perechi de partide pe aceleași deschideri, cu culori inversate, timp fix și interval de încredere. Păstrați rezultatele partidelor separat de acest raport pe poziții.

## Liga offline

| Profil | Adâncime maximă | Noduri suplimentare |
|---|---:|---:|
| Basic | 3 | 0 |
| Advanced | 4 | 16.000 |
| Hard | 5 | 44.000 |
| Indiciu | 5, plus o extensie tactică | 220.000, inclusiv Hard și până la 32.000 pentru compararea alternativelor |

Guvernanța cere creșterea strictă a adâncimii și bugetului între adversari. Indiciul are cel puțin adâncimea Hard și propriul buget mai mare. În finalurile cu turn sau damă și regi, toate nivelurile, indiciul și bara folosesc aceeași evaluare a distanței dintre regi și a spațiului regelui apărat. Testele verifică transformarea avantajului în mat cu ambele culori și conservarea perspectivei albului în afișaj.

Pentru fiecare dintre cele trei confruntări, liga pornește din 12 deschideri și inversează culorile. Motorul folosește alegerile din aplicație și păstrează istoricul pentru repetiție. Partidele încă active la 160 de semimutări sunt înregistrate ca neterminate. La calcul, fiecare astfel de partidă poate contribui între zero și un punct. Punctul central folosește mijlocul acestui interval. Intervalul empiric de 95% provine din 10.000 de reeșantionări ale perechilor întregi, cu ambele limite pentru partidele neterminate. Diferențele Elo sunt `400 log10(p / (1 − p))`, unde `p` este scorul confruntării. [Setul ligii](../../atf-tests/src/test/resources/ai-league.yaml) · [Metoda perechilor în Fishtest](https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html)

Ancora externă folosește Stockfish 19, `UCI_Elo 1500`, un fir și cererea de 6.000 de noduri pe mutare, cu culorile inversate în aceleași 12 deschideri. Rezultatul este performanța față de această configurație. Stockfish își calibrează opțiunea Elo într-un protocol de timp diferit, iar selecția la putere limitată introduce variație între rulări. [Setul ancorei](../../atf-tests/src/test/resources/ai-anchor.yaml) · [Calibrarea opțiunii Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)

Dintr-o copie TMP, folosiți căi noi pentru fiecare raport și fișier de rejucare:

```powershell
mvn test '-Dtest=AiLeagueBenchmarkTest' "-Dchess.league=$leagueTsv" "-Dchess.extent=$leagueHtml"
mvn test '-Dtest=AiAnchorBenchmarkTest' "-Dchess.stockfish=$stockfish" "-Dchess.anchor=$anchorTsv" "-Dchess.extent=$anchorHtml"
```

Extent consemnează rezultatele pe perechi, iar TSV păstrează secvențele UCI integrale. Pentru stil, comparați capturile și șahurile pe mutare, rocadele pe loc de joc și adâncimea medie încheiată. Folosiți aceleași deschideri și același plafon la fiecare comparație.

### Rezultatele ligii interne

| Confruntare | V / R / Î / N | Diferență Elo centrală înainte → după | Interval 95% după |
|---|---:|---:|---:|
| Advanced – Basic | 19 / 2 / 3 / 0 | +120 → +280 | +154 … +470 |
| Hard – Advanced | 10 / 11 / 2 / 1 | +232 → +120 | +14 … +211 |
| Hard – Basic | 19 / 3 / 1 / 1 | +470 → +338 | +120 … +669 |

V, R, Î și N înseamnă victorii, remize, înfrângeri și partide neterminate pentru primul profil. În această ligă, Basic a finalizat în medie adâncimea 3,00, Advanced 3,94 și Hard 4,43. Capturile au reprezentat 20,5%, 23,1% și 24,7% dintre mutările lor. Șahurile au reprezentat 5,9%, 11,9% și 15,1%. Cele 11 remize dintre Hard și Advanced au venit prin repetiție. În Alehină, cu Hard la alb, Stockfish a evaluat la `+8,57` poziția de la începutul ciclului, la semimutarea 36, într-o căutare fără istoricul partidei. [Feedback](../feedback/README.md)

### Rezultatele ancorei externe

| Profil | V / R / Î / N | Performanță centrală în protocol | Interval empiric 95% |
|---|---:|---:|---:|
| Basic | 6 / 0 / 13 / 5 | 1396 | 1083 … 1589 |
| Advanced | 8 / 0 / 8 / 8 | 1500 | 1162 … 1732 |
| Hard | 5 / 0 / 11 / 8 | 1411 | 955 … 1654 |

Intervalele se suprapun, iar 21 dintre cele 72 de partide au rămas active la plafon. Acest eșantion extern descrie performanța împotriva profilului Stockfish configurat și lasă ordinea nivelurilor nedeterminată. Diferențele din liga internă sunt măsurate direct între niveluri.

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

După `1.e4 e5`, prima recomandare este `Nf3`, iar bara afișează `+0,19` față de `+1,40` anterior și `+0,43` în Stockfish 19. Pe șapte poziții suplimentare eroarea absolută medie a barei a scăzut de la 70,7 cp la 13,1 cp. Eroarea maximă este 41 cp, la Gambitul damei după `2.c4`, unde semnele diferă. Benchmarkul compară separat alegerea mutării în scor așteptat și estimarea barei în centipioni, din perspectiva albului. [Setul și porțile de calibrare](../../atf-tests/src/test/resources/opening-calibration.yaml) · [Scara Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Useful-data.html)

## Rezultatul de referință al primei extinderi

| Profil | Aceeași mutare ca Stockfish, înainte / după | Pierdere medie, înainte / după | Pierderi mari, înainte / după |
|---|---:|---:|---:|
| Basic | 5 / 6 din 10 | 202,7‰ / 202,4‰ | 2 / 2 |
| Advanced | 7 / 7 din 10 | 2,7‰ / 14,1‰ | 0 / 0 |
| Hard | 7 / 8 din 10 | 2,7‰ / 11,7‰ | 0 / 0 |
| Indiciu | 7 / 8 din 10 | 20,2‰ / 2,4‰ | 1 / 0 |

După calibrarea Hard, pierderea medie a acestui profil pe suita mixtă a scăzut de la 11,7‰ la 0‰. Regresia tactică din Siciliană păstrează `Nxe5` la Hard și la indiciu. Acest caz folosește altă poziție decât lecția Najdorf. Pragurile ambelor suite au rămas aceleași.
