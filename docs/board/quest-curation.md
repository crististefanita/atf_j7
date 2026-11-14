# Repere pentru compoziția problemelor de mat

- **Mat descoperit și mutare neobișnuită.** Poziția Lichess P9xLh, observată la rating 2184, are cinci mutări de turn care dau mat prin descoperire. Ideea merită studiată. Pentru o compoziție J7, păstrați o singură retragere câștigătoare și verificați fiecare retragere alternativă. [P9xLh](https://lichess.org/training/mateIn1/P9xLh)
- **Șahuri înșelătoare.** În 0jbFz, observată la rating 2370 prin API, există 18 mutări legale, opt șahuri cu apărare și o singură soluție, `Txb6#`. La compoziție, urmăriți ca retragerile naturale să lase o apărare concretă, iar cheia să elimine tocmai acea apărare. [0jbFz](https://lichess.org/training/mateIn1/0jbFz)
- **Mat din poziție de șah.** În 16meG, observată la rating 2075 prin API, albul este în șah și are doar trei mutări legale. Două mutări ale regelui scapă de atac, iar `Nc2#` blochează șahul și deschide simultan linia de mat. În J7, `mate-101` exersează interpunerea, iar `mate-102` compară două capturi posibile ale piesei care dă șah. [16meG](https://lichess.org/training/mateIn1/16meG) · [Contra-maturile J7](README.md#reguli-și-probleme)
- **Construcție.** Porniți de la o idee distinctă, cum ar fi matul descoperit, blocarea ultimei fugi sau promovarea la cal. Dacă există o a doua soluție, schimbați poziția înainte de a o publica. Păstrați câte o apărare concretă pentru încercările cele mai tentante. [Codex WFCC](https://www.wfcc.ch/rules/codex/) · [Teme Lichess](https://lichess.org/training/themes)

Pentru o poziție nouă, validați FEN-ul, enumerați toate mutările în ambele motoare și cereți exact un mat. Dați poziției un ID propriu, independent de rang. Ratingurile Lichess reflectă încercări reale ale jucătorilor, iar scorul local folosește caracteristicile poziției. [Metodologia și formatul Lichess](https://database.lichess.org/)

## Configurarea capitolelor

| Capitol | Ce se editează |
|---|---|
| Mat în 1 | ID, FEN, soluție UCI și temă în [quests.yaml](../../chess-core/src/main/resources/quests.yaml). Introducerea, ponderile și departajările se configurează în [quest-difficulty.yaml](../../chess-core/src/main/resources/quest-difficulty.yaml). |
| Mat în 2 | FEN, linie UCI și rating în [mate-in-two.yaml](../../chess-core/src/main/resources/mate-in-two.yaml). Păstrați ratingurile în ordine crescătoare. La rating egal contează ordinea din listă. |
| Mat în 3 | Același format, cu cinci semimutări, în [mate-in-three.yaml](../../chess-core/src/main/resources/mate-in-three.yaml). Poziția `mate3-0Ytul` înlocuiește exercițiul apropiat `mate3-02GAF` la același rating. |
| Tactică | FEN, soluție UCI și `difficulty` în [tactics.yaml](../../chess-core/src/main/resources/tactics.yaml). Folosiți valori întregi pozitive distincte. Selecția actuală pornește de la capturi directe și trece la mutări fără captură imediată. |
| Antrenament tactic | FEN, cheie și ordine în [training.yaml](../../chess-core/src/main/resources/training.yaml). Păstrați ID-urile și checksum-ul catalogului. |
| Mutarea intermediară | FEN, cheie și rating în [intermezzo.yaml](../../chess-core/src/main/resources/intermezzo.yaml). Ratingurile definesc ordinea crescătoare. |

Pentru Mat în 1, ponderile mai mari mută mai târziu pozițiile care au caracteristica respectivă. O departajare de la 1 la 9 se aplică numai la același scor de bază. Egalitățile rămase urmează ID-ul, iar scorul din fișierul de ordine crește strict. Generatorul refuză o poziție cu mai multe soluții. Rulați din copia TMP, cu JDK 21 și Maven. Pentru verificarea independentă, setați `$chessJs` la fișierul CommonJS din chess.js 1.4.0. Adăugați `-Offline` dacă dependențele sunt deja în cache sau `-MavenRepo` dacă folosiți alt depozit Maven.

```powershell
./scripts/update-mate-one-order.ps1 -ChessJs $chessJs
./scripts/update-mate-one-order.ps1 -Write -ChessJs $chessJs
```

Prima comandă compară ordinea existentă cu cea calculată. A doua regenerează ordinea după modificarea ponderilor. După o schimbare revizuită de poziții sau soluții, adăugați `-RefreshOracle` la a doua comandă, apoi rulați suitele de catalog. Chess.js recalculează dovada din pozițiile exportate de parserul YAML al proiectului.

Păstrați ID-ul la reordonare. Pentru o înlocuire, puneți ID-ul vechi în registrul de retrageri și atribuiți un ID nou poziției. Verificarea compară toate cele șase capitole. Normalizează culoarea și reflexiile permise, inclusiv rotațiile unei table fără pioni și fără drepturi de rocadă. Pentru poziții apropiate compară tipul mutării, destinația, câmpul regelui advers și maximum zece câmpuri diferite. Cele 31 de poziții noi de Mat în 1 provin din partidele offline de calibrare. Comentariile din YAML indică partida, semimutarea și eventualul schimb de culori și reflexie. [Registrul de ID-uri](../../chess-core/src/main/resources/quest-retired.yaml) · [Comenzi de verificare](../testing/README.md#progresul-problemelor)

## Calibrarea dificultății

Ediția 0.10.0 adaugă 786 de poziții din exportul CC0 Lichess din 9 noiembrie 2026. Selecția a împărțit candidații în cinci intervale de rating pentru fiecare culoare și capitol. A cerut popularitate de cel puțin 75, minimum 180 de încercări și deviație de rating de cel mult 100. FEN-ul de joc se obține după prima mutare din sursă. Sursa comprimată are SHA-256 `7b0d235fa40cfd7fdb7df1fec56a96b3b6ee1496b32ed2eb05be90d0ebf94253`. ID-urile și soluțiile celor 314 poziții din ediția anterioară au fost păstrate. [Export și licență](https://database.lichess.org/#puzzles)

Capitolele au 500, 300, 200 și 100 de poziții, cu alb și negru în proporții egale în fiecare. Mat în 1 urmează scorul structural, Mat în 2 și Mat în 3 urmează ratingul sursă, iar Tactica păstrează cele 12 introduceri și ordonează pozițiile noi după rating. Pentru tactică, selecția a verificat o mutare unică cu un câștig de cel puțin doi pioni la trei semimutări. Comentariile din YAML leagă problemele noi din Mat în 1 și Tactică de ID-urile sursă.

Pentru calibrarea cu jucători, alternați ordinea unui eșantion din începutul, mijlocul și finalul capitolului. Includeți `mate-101` și `mate-102`, care au doar șase, respectiv cinci mutări legale, dar încep în șah. Includeți `mate-129`, care are 14 șahuri înșelătoare, dintre care șase cu o singură apărare. Măsurați timpul și încercările greșite înainte de primul indiciu. Scorul local este o estimare până la această calibrare.

## Mat în două mutări

Primele 100 de poziții au provenit din subsetul `lczerolens/lichess-puzzles-mate-in-2`, iar cele 200 adăugate provin direct din exportul CC0. ID-ul `mate2-<PuzzleId>` păstrează legătura cu problema `https://lichess.org/training/<PuzzleId>`. Poziția afișată este FEN-ul sursă după prima mutare a adversarului. [Formatul și licența Lichess](https://database.lichess.org/#puzzles) · [Subsetul inițial](https://huggingface.co/datasets/lczerolens/lichess-puzzles-mate-in-2/viewer)

Selecția are câte 150 de poziții pentru fiecare culoare. Verificarea respinge matul imediat, cheia alternativă și orice apărare fără continuare de mat. Catalogul păstrează linia de referință, iar dovada independentă acoperă toate apărările și toate maturile din fiecare ramură.

Pentru o selecție de lucru, salvați în TMP paginile `rows-<offset>.json` cu 100 de rânduri pe pagină. Păstrați ID-urile publicate când integrați pozițiile verificate. [Revizia subsetului inițial](https://huggingface.co/datasets/lczerolens/lichess-puzzles-mate-in-2/tree/8c195a0696c30c8371d276db0467e833f1b72468)

```powershell
node scripts/verify-mate-in-two.cjs <cale-catre-chess.js> --import <pagini-TMP> <catalog-TMP.yaml>
```

## Mat în trei mutări

Cele 200 de poziții provin din baza Lichess CC0. Selecția are 100 de poziții cu albul la mutare și 100 cu negrul, ordonate după ratingul importat. ID-ul `mate3-<PuzzleId>` trimite la problema originală Lichess. [Baza Lichess](https://database.lichess.org/#puzzles) · [Revizia subsetului inițial](https://huggingface.co/datasets/lczerolens/lichess-puzzles-mate-in-3/tree/680d12e5e6447800d5cbe2b2321d4cffd6a024d1)

Ambele motoare verifică o singură primă mutare care forțează matul în trei, fără soluție mai scurtă. Pentru fiecare primă apărare se verifică toate mutările care forțează matul în următoarele două. Linia jucată în aplicație are și a doua mutare unică.

## Antrenament tactic și lot de verificare

La etapa 6 din `training-069`, `Qxb2` încheie exercițiul prin captura imediată a turnului. Cele +5 unități descriu poziția chiar după captură. Negrul mai are replica `…Qxd1+`. În linia de studiu `Rd8+ Kg7 Qxb2 Qxc4 Rd2`, șahul amână câștigul până la mutarea următoare. Atingeți mesajul după `Rd8+` pentru explicație și parcurgeți linia de pe tablă. Din bec puteți deschide și reluarea exercițiului. Poziția și punctele se păstrează în timpul studiului.

[Studiile ultimei mutări](../../chess-core/src/main/resources/quest-finish-studies.yaml) fixează poziția, cheia și continuarea în UCI. Consumatorul verifică etapa, cheia și poziția înainte să afișeze explicația. Linia de studiu rămâne distinctă de linia creditată din catalog.

Capitolul **Antrenament tactic** conține 114 poziții fără obiectiv de mat, ordonate după ratingul sursă. Pozițiile acoperă apărarea, devierea, mutarea liniștită, sacrificiul, furculița și legarea. ID-urile `training-049`–`training-072` acoperă benzile 1200–1599 și 1600–1849. ID-urile `training-073`–`training-096` adaugă câte o poziție pentru fiecare temă și culoare în benzile 1800–2199 și 2200–2699. Patru exerciții de turn, două pe fiecare culoare, intră între ratingurile sursă 1393 și 1794. Următorul lot adaugă patru lecții între ratingurile sursă 1198 și 1766, cu două poziții pe fiecare culoare. ID-urile și ordinea relativă a exercițiilor existente păstrează progresul salvat. [Catalog](../../chess-core/src/main/resources/training.yaml) · [Exportul Lichess CC0](https://database.lichess.org/#puzzles)

Stockfish 19 la adâncimea 15 a ales cheia în toate cele 24 de poziții noi și în 24 de poziții separate, din aceleași benzi și teme. J7 și chess.js 1.4.0 verifică unicitatea cheii pentru câștigul material forțat în trei semimutări. `QuestCatalogTest` verifică unicitatea între capitole și față de lotul separat. `TacticsCatalogTest` compară dovezile cu amprentele chess.js, iar `QuestStoreTest` verifică reluarea după intercalarea exercițiilor. Lotul separat rămâne în resursele de test și poate fi folosit pentru transferul motorului. [Lot independent](../../atf-tests/src/test/resources/tactical-transfer-holdout.yaml) · [Verificări locale](../testing/README.md#progresul-problemelor)

| Exercițiu | Lecție | Câștig minim după replica imediată la final |
|---|---|---:|
| training-101 · [017AB](https://lichess.org/training/017AB) | Sacrifică turnul cu șah pe c5 ca să promovezi pionul de pe b2. | 300 cp |
| training-102 · [00JYV](https://lichess.org/training/00JYV) | Înainte de a recaptura pe c3, ia nebunul din e7 cu șah. | 340 cp |
| training-103 · [00IMS](https://lichess.org/training/00IMS) | Deschide d5 pentru șahul nebunului după schimbul de pe f5. | 220 cp |
| training-104 · [0047P](https://lichess.org/training/0047P) | Folosește șahurile turnului ca să ataci regele și turnul din a2. | 500 cp |

Stockfish 19 confirmă cheile la adâncimile 16 și 19. Bilanțul din tabel este materialul rămas după linia jucabilă și cea mai costisitoare replică imediată. La adâncimea 19, Stockfish alege liniile publicate până la câștig. În 00IMS, la adâncimea 16, preferă altă apărare cu aceeași cheie.

Patru finaluri noi folosesc răspunsul din linia Stockfish 19 la adâncimea 19. Cheile coincid la adâncimile 16 și 19. Am păstrat pozițiile existente și ordinea lor relativă.

| Exercițiu | Lecție | Câștig minim la capătul liniei |
|---|---|---:|
| training-105 · [023eI](https://lichess.org/training/023eI) | Dama capturează cu șah și atacă și piesa din d6. | 430 cp |
| training-106 · [02WXt](https://lichess.org/training/02WXt) | Schimburile de pioni deschid linia turnului către d3. | 400 cp |
| training-107 · [00xxi](https://lichess.org/training/00xxi) | Intrarea regelui permite promovarea și câștigul turnului după schimb. | 400 cp |
| training-108 · [02oHj](https://lichess.org/training/02oHj) | Avansul pionului pe c2 permite promovarea după ce regele capturează pe d3. | 300 cp |

Cele patru finaluri de mai jos păstrează răspunsurile din linia Stockfish 19 la adâncimea 19 până la câștig. Cheile coincid la adâncimile 16 și 19.

| Exercițiu | Lecție | Câștig minim la capătul liniei |
|---|---|---:|
| training-109 · [01Jpm](https://lichess.org/training/01Jpm) | Avansează cu șah și promovează înaintea pionului advers. | 470 cp |
| training-110 · [00PHg](https://lichess.org/training/00PHg) | Intrarea turnului cu șah pregătește promovarea și recaptura. | 200 cp |
| training-111 · [00gS7](https://lichess.org/training/00gS7) | Împinge pionul pentru a limita răspunsurile și a câștiga nebunul. | 230 cp |
| training-112 · [01e8j](https://lichess.org/training/01e8j) | Regele capturează pe e4, apoi f5 păstrează opoziția și progresul pionilor. | 400 cp |

## Mutarea intermediară

Capitolul **Mutarea intermediară** are 24 de poziții în trei trepte de rating: introducere (1150–1549), consolidare (1550–1849) și avansat (1850–2400), cu patru poziții pentru fiecare culoare în fiecare treaptă. Cheia dă șah sau capturează înaintea recapturii și trebuie să păstreze câștigul material cerut de catalog. [Catalog](../../chess-core/src/main/resources/intermezzo.yaml)

Progresul și totalul acestui capitol sunt independente de **Tactică** și **Antrenament tactic**. Verificați în componentă ID-ul curent, rezolvarea idempotentă și totalul separat înainte de extinderea YAML-ului.

## Câștigul și lecția în problemele fără mat

Un exercițiu bun leagă mutarea de o alegere pe care jucătorul o poate recunoaște într-o altă partidă. Începeți de la relația dintre piese, identificați replica care pune ideea la încercare și opriți linia la primul câștig vizibil și apărat. În Mutarea intermediară, păstrați ultima captură din partida sursă, mutarea care amână recaptura și câștigul obținut prin ordinea nouă.

| Dovadă | Ce verificăm |
|---|---|
| Proveniență | ID-ul din exportul Lichess, partida și mutarea care produce poziția. Poziția și prima mutare trebuie să coincidă cu sursa. |
| Linie | `payoff-line` listează semimutările UCI, de la cheia `solution` până la ultima mutare a jucătorului. Repetați linia pe tablă și verificați fiecare replică legală la final. |
| Lecție | `lesson` numește motivul demonstrat de linie. Pentru o deviere, indicați ce apărător se mută și ce devine capturabil. Pentru o furculiță, urmăriți ambele ținte. |
| Rezultat | Măsurați materialul de la începutul liniei, cu piesele pierdute și câștigate. Verificați separat dacă adversarul poate recupera imediat câștigul. |

Pentru un câștig imediat, o singură mutare poate încheia exercițiul. Pentru o combinație, păstrați succesiunea cheie, apărare, câștig. Explicația pornește de la tabla finală și permite revenirea la fiecare mutare. Dificultatea crește când replica schimbă ordinea firească a mutărilor, când câștigul cere o mutare fără captură sau când aceeași idee trebuie recunoscută cu cealaltă culoare.

Cheia unică verificată la trei semimutări rămâne un control local de material. Pentru pozițiile mai lungi, comparați și replica preferată de Stockfish. Un scor pozitiv poate indica atac sau inițiativă chiar dacă materialul scade. De exemplu, în `training-001`, după `Rxh6+ gxh6 Qxh6+ Kg8 Rh1`, albul are atac, iar bilanțul material al liniei rămâne negativ. Verificați o continuare care arată un câștig concret înainte de a eticheta linia ca exercițiu de câștig material.

În `intermezzo-020`, după `…Nxe4`, albul poate recaptura imediat calul. Lecția este să joace mai întâi `Bxe7`, cu atac asupra damei, apoi `Nxe4` după `…Qxe7`. În `intermezzo-024`, negrul amenință mai întâi promovarea cu `…cxb2`. După `Rb8` recapturează turnul cu `…Nxa4`. În ambele exemple, schimbarea ordinii produce un câștig observabil.

În [0Nyzz](https://lichess.org/training/0Nyzz), `…f5` eliberează diagonala e7–h4 și nebunul atacă dama. Dacă albul joacă `Qxe7`, dama neagră recapturează pe e7. Jucătorul vede astfel de ce o mutare fără captură poate câștiga mai mult decât o captură imediată.

La revizuire păstrați ID-ul dacă poziția și cheia rămân aceleași. Pentru o poziție înlocuită, folosiți un ID nou și păstrați ID-ul anterior în registrul de retrageri. Comparați dificultatea, culoarea și lecția în ordinea capitolului. [Verificarea catalogului](../testing/README.md#tactică-și-punctaj) · [Exportul Lichess CC0](https://database.lichess.org/#puzzles)

| Capitol | Continuare jucabilă verificată | Câștig imediat verificat | Studiul apărării în explicație |
|---|---:|---:|---:|
| Tactică | 76 | 17 | 7 |
| Antrenament tactic | 87 | 15 | 10 |
| Mutarea intermediară | 20 | 4 | 0 |

Șase exerciții introductive locale au fost înlocuite cu poziții atestate. Cele noi ocupă aceleași locuri și păstrează distribuția culorilor. În cele 17 poziții a căror rezolvare se salvează după mutarea-cheie, **Explicația** deschide studiul apărării. Reia cheia, replica adversă și continuarea din [catalogul studiilor](../../chess-core/src/main/resources/quest-defense-studies.yaml).

Din cele 183 de continuări, 130 provin din linia publicată, 43 din comparația cu Stockfish și verificarea pe tablă, patru din partidele J7 păstrate și șase din exercițiile de înlocuire. Pentru o linie obținută cu Stockfish, prezentați replica ca variantă verificată. Dovada că un câștig persistă la o replică imediată se aplică poziției de la capătul liniei.

| Exercițiu | Lecție | Câștig la capătul liniei |
|---|---|---:|
| [training-097](https://lichess.org/training/0Luif) | `Rxb6+` atrage pionul a. Dama recapturează cu șah și câștigă turnul de la d8. | +2 |
| [training-098](https://lichess.org/training/0BTVa) | `…Rxg3+` forțează regele să se retragă. Turnul continuă cu `…Rxg4` și ia calul. | +4,2 |
| [training-099](https://lichess.org/training/0AZju) | După `…Rxh4+`, turnul atrage dama la g4, unde calul o capturează. | +5 |
| [training-100](https://lichess.org/training/02Nl2) | `Rxh6+` deschide calea damei la h7. Turnul continuă pe f6, atrage dama adversă și pionul o capturează. | +5 |

Pentru fiecare dintre cele 17 studii, catalogul păstrează proveniența Lichess, linia UCI și lecția specifică în română și engleză. Replica afișată este prima alegere Stockfish 19 la adâncimea 19 din poziția obținută după cheie. Rularea la adâncimea 17 arată unde alegerea se schimbă. Scorul afișat este estimarea motorului după cheie, din perspectiva celui care rezolvă. Bilanțul afișat se calculează direct din fiecare poziție, cu valorile pieselor din profilul tactic. La final, o valoare mai mică după o replică legală apare separat.

În `training-001`, `Rxh6+ gxh6 Qxh6+ Kg8 Rh1` deschide coloana h pentru damă și turn. Linia arată −3 unități de pion și un minim de −4 după o replică, iar Stockfish estimează poziția de după cheie la +7,23. În `tactic-052`, șahurile împing regele până la c4 și turnul capturează dama pe g4. Câștigul material de la captură este +9, cu minimum +4 după o replică. În ambele cazuri, folosiți pasul la care ideea devine vizibilă când scrieți lecția.

