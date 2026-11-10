# Tabla de joc

Tabla este elementul dominant al ecranului. Verdele și cremul delimitează cele 64 de pătrate. Piesele Staunton au contur închis, bază curbată, cal văzut din profil și coroane distincte pentru rege și damă. Desenul este propriu, inspirat de captura de referință. Coordonatele stau în marginea tablei, iar mutarea de mat marchează plecarea și destinația.

- Culoarea care mută începe jos și are marcajul „la mutare” lângă tablă. Marcajul urmează poziția de joc și rămâne vizibil când apare un indiciu. Întoarcerea tablei schimbă poziția vizuală a etichetelor și a coordonatelor, inclusiv `h…a` cu negrul jos.
- Etichetele Alb și Negru stau lângă muchiile tablei. Panoul măsoară întâi etichetele și folosește spațiul disponibil pentru tablă. Pe ferestre scunde, tabla rămâne pătrată și se micșorează pentru a păstra locul comenzilor.
- Atingerea unei piese active arată numai destinațiile legale. O a doua atingere alege destinația. Promovarea cere o alegere explicită.
- Fiecare pătrat poate primi focus și anunță coordonata, piesa și destinația legală când există. Bara de stare anunță cine mută și rezultatul detectat de motor.
- O încercare greșită păstrează poziția. Dacă mutarea dă șah, aplicația arată o apărare legală în poziția încercată. Patul are un mesaj propriu.
- **Indiciu** conturează numai pătratul piesei de mutat și îi anunță numele și coordonata. **Arată soluția** adaugă o săgeată pe tablă și coordonatele mutării, inclusiv piesa necesară la promovare. Celulele anunță textual marcajele, iar săgeata urmează coordonatele la întoarcerea tablei.
- **De ce e mat?** deschide explicația detaliată a șahului și a câmpurilor de fugă. Motorul verifică atacurile după plecarea ipotetică a regelui și după o eventuală captură. Contorul crește când mutarea de mat este jucată pe tablă, inclusiv dacă ai cerut un indiciu.

Modelul de ajutor în două trepte pornește de la Daily Puzzle. Explicația rămâne o acțiune separată, la fel ca dezvăluirea explicită a soluției în antrenamentul Lichess. [Chess.com Daily Puzzle](https://support.chess.com/en/articles/8708990-how-does-the-daily-puzzle-work) · [Lichess Puzzle Training](https://lichess.org/training/daily)

## Reguli și probleme

[`chess-rules.yaml`](../../chess-core/src/main/resources/chess-rules.yaml) declară vectorii pieselor, deplasarea pe rază sau prin salt, sensul pionului, captura, promovarea și configurația celor două rocade. Motorul aplică blocajele, alternarea culorilor, protecția regelui și expirarea dreptului en passant. Condițiile de siguranță rămân obligatorii la încărcarea profilului standard.

[`quests.yaml`](../../chess-core/src/main/resources/quests.yaml) păstrează poziția FEN, soluția UCI și tema fiecărei probleme. Setul conține 52 de poziții cu albul la mutare și 50 cu negrul. Catalogul include compoziții pentru mat prin rocadă, en passant, promovare la cal și apărare din șah.

| ID | Poziție în catalog | Indiciu | Soluție |
|---|---|---|---|
| `mate-101` | 57 | Nebunul de pe `h6` | `Nd2#` oprește șahul turnului negru și deschide coloana h pentru dama albă. |
| `mate-102` | 58 | Pionul de pe `h3` | `hxg4#` capturează calul care dă șah și eliberează coloana h pentru turnul alb. |

[`mate-in-two.yaml`](../../chess-core/src/main/resources/mate-in-two.yaml) adaugă 100 de poziții cu o singură mutare inițială care forțează matul. După acea mutare, aplicația joacă apărarea de referință și acceptă orice mat legal din poziția rezultată. Indiciile urmează mutarea curentă, iar explicația arată linia completă. [Selecție și proveniență](quest-curation.md#mat-în-două-mutări)

## Dificultate și progres

[`quest-order.yaml`](../../chess-core/src/main/resources/quest-order.yaml) stabilește ordinea după un scor reproductibil. Matul nebunului deschide colecția ca exercițiu introductiv. După el, numărul mutărilor legale contribuie limitat. Scorul crește mai mult pentru șahurile înșelătoare, mai ales dacă există o singură apărare, și pentru matul descoperit, rocadă, en passant și subpromovare. O poziție în care jucătorul începe în șah primește 16 puncte suplimentare. Cele două contra-maturi au scorul 32 și departajarea după ID le așază la pozițiile 57 și 58. Scorul este o estimare editorială care se poate calibra ulterior pe încercări reale. [Repere pentru compoziție](quest-curation.md)

Aplicația salvează ID-ul problemei curente și setul ID-urilor rezolvate într-o singură scriere locală, la navigare și la mat. Reordonarea schimbă numărul afișat, iar identitatea salvată stabilește ce problemă se redeschide. Un ID curent scos din catalog duce la prima problemă disponibilă. Rezolvările vechi se păstrează și intră din nou în total dacă problema revine în catalog.

Fiecare capitol are propriul contor și își reține ultima problemă. La mat în două, prima mutare salvează și poziția după apărarea automată. Contorul crește după matul jucat în a doua mutare.

Bara arată progresul în întreaga colecție. Pragurile 5, 15, 30, 50, 75 și finalul catalogului se recalculează din ID-urile rezolvate. La atingerea unui prag, ecranul confirmă realizarea și afișează următoarea țintă. Bara și textul pornesc de la același total după reluare. Ideea țintelor vizibile vine din Puzzle Journey. [Chess.com Puzzle Points](https://support.chess.com/en/articles/9681952-what-are-puzzle-points-on-chess-com)

[Regulile de șah și poziția inițială](https://handbook.fide.com/chapter/e012023)
