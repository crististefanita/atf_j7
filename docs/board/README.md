# Tabla de joc

Tabla este elementul dominant al ecranului. Verdele și cremul delimitează cele 64 de pătrate. Piesele Staunton au contur închis, bază curbată, cal văzut din profil și coroane distincte pentru rege și damă. Desenul este propriu, inspirat de captura de referință. Coordonatele stau în marginea tablei, iar ultima mutare marchează plecarea și destinația.

## Tactică și puncte

- În **Tactică**, alege mutarea care câștigă cel mai mult material. Verificarea ia în calcul mutarea ta, replica adversarului și continuarea ta. Poți deschide explicația pentru linia completă.
- Becul de sub tablă are o zonă de atingere de 48 dp. Prima apăsare marchează piesa, a doua adaugă săgeata, iar următoarea explică soluția. În timpul replicii, comanda este dezactivată.
- Prima rezolvare a fiecărei probleme acordă 10 puncte. O serie fără greșeli sau indicii adaugă câte 2 puncte la fiecare problemă, începând de la a doua, până la 20 de puncte pe problemă. Flacăra arată lungimea seriei și funcționează între capitole.
- O greșeală sau folosirea becului întrerupe seria. Rezolvarea acelei probleme rămâne eligibilă pentru cele 10 puncte de bază. Revizitarea și reluarea păstrează punctele deja acordate. Problemele rezolvate înainte de această schimbare primesc creditul de bază.

Sistemul de puncte și bonusul de la a doua rezolvare urmează modelul Puzzle Points. În J7, flacăra reprezintă problemele consecutive rezolvate fără ajutor. [Chess.com Puzzle Points](https://support.chess.com/en/articles/9681952-what-are-puzzle-points-on-chess-com)

## Tablă și indicii

- La intrarea în capitol, orientarea pornește cu partea care trebuie să rezolve problema jos. **Precedentă** și **Următoarea** păstrează orientarea și coordonatele. Marcajul „la mutare” urmărește culoarea activă. Folosește **Întoarce tabla** pentru a schimba orientarea.
- Etichetele și mesajele au loc rezervat. La aceeași dimensiune de fereastră și același font, tabla își păstrează poziția și dimensiunea când se schimbă problema, mutarea din istoric sau analiza. Pe ferestre scunde, tabla rămâne pătrată și se micșorează pentru a păstra locul comenzilor.
- Atingerea unei piese active arată numai destinațiile legale. O a doua atingere alege destinația. Promovarea cere o alegere explicită.
- Fiecare pătrat poate primi focus și anunță coordonata, piesa și destinația legală când există. Bara de stare anunță cine mută și rezultatul detectat de motor.
- O încercare greșită păstrează poziția. Dacă mutarea dă șah, aplicația arată replica adversarului și următorul răspuns, plus câștigul net de material sau finalul când linia se termină în mat ori pat. Când mai multe replici au același scor, mesajul arată câte sunt și indică o alternativă. Atinge mesajul pentru a-l citi integral. Patul direct are un mesaj propriu.
- În Mat în 2 și Mat în 3, mutarea corectă se vede imediat. Apărarea de referință urmează după 650 ms cât timp tabla e în prim-plan. În pauză se vede culoarea adversarului la mutare, iar tabla și indiciile așteaptă răspunsul. Apoi marcajul revine la jucător și ultima mutare scoate în evidență apărarea.
- **Indiciu** marchează discret cu turcoaz pătratul piesei și îi anunță numele și coordonata. **Arată mutarea** adaugă o săgeată subțire și textul „Mutare sugerată”, cu piesa necesară la promovare. Celulele anunță textual marcajele, iar săgeata urmează coordonatele la întoarcerea tablei. După apărare, indiciul pornește de la noua poziție.
- Cititorul de ecran anunță culoarea și piesa la **Indiciu**, apoi include destinația la **Arată mutarea**. La atingerea unui prag, confirmarea matului include și pragul. Bara de progres păstrează pragul vizibil la revenire.
- **De ce e mat?** deschide explicația detaliată a șahului și a câmpurilor de fugă. Motorul verifică atacurile după plecarea ipotetică a regelui și după o eventuală captură. Contorul crește când mutarea de mat este jucată pe tablă, inclusiv dacă ai cerut un indiciu.

Modelul de ajutor în două trepte pornește de la Daily Puzzle. Explicația rămâne o acțiune separată, la fel ca dezvăluirea explicită a soluției în antrenamentul Lichess. [Chess.com Daily Puzzle](https://support.chess.com/en/articles/8708990-how-does-the-daily-puzzle-work) · [Lichess Puzzle Training](https://lichess.org/training/daily)

## Reguli și probleme

[`chess-rules.yaml`](../../chess-core/src/main/resources/chess-rules.yaml) declară vectorii pieselor, deplasarea pe rază sau prin salt, sensul pionului, captura, promovarea și configurația celor două rocade. Motorul aplică blocajele, alternarea culorilor, protecția regelui și expirarea dreptului en passant. Condițiile de siguranță rămân obligatorii la încărcarea profilului standard.

[`tactical-analysis.yaml`](../../chess-core/src/main/resources/tactical-analysis.yaml) fixează orizontul la cel mult trei mutări individuale. Mesajul din joc folosește două, începând cu replica adversarului. Fiecare parte alege cea mai bună continuare pentru ea. Matul primează, patul valorează zero, iar restul pozițiilor se compară după materialul de la capătul liniei. Câștigul afișat este diferența de material față de poziția de după încercarea jucătorului. Scorurile egale la acest orizont rămân în același grup, iar ordinea UCI alege linia afișată. Repetarea, regula mutărilor fără captură sau pion și evaluarea pozițională țin de o analiză cu istoric și orizont mai lung.

[`quests.yaml`](../../chess-core/src/main/resources/quests.yaml) păstrează poziția FEN, soluția UCI și tema fiecărei probleme. Setul conține 52 de poziții cu albul la mutare și 50 cu negrul. Catalogul include compoziții pentru mat prin rocadă, en passant, promovare la cal și apărare din șah.

| ID | Poziție în catalog | Indiciu | Soluție |
|---|---|---|---|
| `mate-101` | 57 | Nebunul de pe `h6` | `Nd2#` oprește șahul turnului negru și deschide coloana h pentru dama albă. |
| `mate-102` | 58 | Pionul de pe `h3` | `hxg4#` capturează calul care dă șah și eliberează coloana h pentru turnul alb. |

[`mate-in-two.yaml`](../../chess-core/src/main/resources/mate-in-two.yaml) adaugă 100 de poziții cu o singură mutare inițială care forțează matul. După acea mutare, aplicația joacă apărarea de referință și acceptă orice mat legal din poziția rezultată. Indiciile urmează mutarea curentă, iar explicația arată linia completă. [Selecție și proveniență](quest-curation.md#mat-în-două-mutări)

[`mate-in-three.yaml`](../../chess-core/src/main/resources/mate-in-three.yaml) conține 100 de poziții de mat exact în trei, cu o singură primă mutare câștigătoare. Jocul aplică cele două apărări de referință, afișează câte mutări au mai rămas după prima apărare și acceptă orice mat legal pe ultima mutare. [Selecție și proveniență](quest-curation.md#mat-în-trei-mutări)

## Partidă cu calculatorul

**Partidă nouă cu calculatorul** deschide poziția inițială. Jucătorul are albele, iar calculatorul calculează local, cu negrele, la un orizont de trei mutări individuale. La egalitate, preferă pozițiile vizitate mai rar și mutările care dezvoltă piesele sau ocupă centrul. Replica apare după cel puțin 650 ms. Tabla așteaptă replica înainte de a primi o nouă mutare.

Jocul salvează istoricul înainte de a afișa fiecare mutare. **Reia partida** și redeschiderea directă a unui joc activ refac poziția, evidențiază ultima mutare și restabilesc culoarea la mutare. Dacă replica era în așteptare, pauza reîncepe și se aplică o singură replică. O partidă nouă cere confirmare înainte de a înlocui o partidă în curs cu mutări salvate.

Sub tablă, săgețile parcurg fiecare mutare, iar săgeata cu opritor revine la ultima. Lista se derulează orizontal și permite alegerea directă a oricărei poziții, inclusiv **Start**. Mutarea selectată este evidențiată discret. Controalele au zone de atingere de 48 dp. Pozițiile anterioare se afișează cu tabla blocată. Dacă botul răspunde în timp ce te uiți în urmă, mutarea nouă apare în listă și poziția consultată rămâne vizibilă. [Navigarea între mutări pe Chess.com](https://support.chess.com/en/articles/10328363-how-do-i-use-game-review-on-the-app)

Bifează **Analiză AI pentru o partidă nouă** înainte de pornire. Evaluarea arată avantajul material estimat în unități de pion. `+1,25 · Alb` favorizează albul, iar `−1,25 · Negru` favorizează negrul, indiferent cine mută. Matul și patul se afișează separat. [Convenția de evaluare Chess.com](https://support.chess.com/en/articles/8379354-computer-evaluation-numbers-on-chess-com)

Apasă becul din bara de sub tablă pentru a vedea linia completă și săgeata primei mutări. Motorul local caută până la trei semimutări pentru poziția afișată, inclusiv în istoric. Dacă mai multe prime mutări au același scor maxim, becul parcurge câte o linie pentru fiecare, apoi revine la prima. Schimbarea poziției ascunde indiciul. Indiciul nu joacă mutarea, iar opțiunea de analiză se păstrează la reluare.

Motorul aplică rocada, en passant și alegerea la promovare. Partida se oprește la mat, pat, configurațiile uzuale de material insuficient, a cincea apariție a poziției sau 75 de mutări de fiecare parte fără captură ori mutare de pion. Remizele la cerere fac parte din regulile de competiție. [Regulile FIDE](https://handbook.fide.com/chapter/e012023)

## Dificultate și progres

[`quest-order.yaml`](../../chess-core/src/main/resources/quest-order.yaml) stabilește ordinea după un scor reproductibil. Matul nebunului deschide colecția ca exercițiu introductiv. După el, numărul mutărilor legale contribuie limitat. Scorul crește mai mult pentru șahurile înșelătoare, mai ales dacă există o singură apărare, și pentru matul descoperit, rocadă, en passant și subpromovare. O poziție în care jucătorul începe în șah primește 16 puncte suplimentare. Cele două contra-maturi au scorul 32 și departajarea după ID le așază la pozițiile 57 și 58. Scorul este o estimare editorială care se poate calibra ulterior pe încercări reale. [Repere pentru compoziție](quest-curation.md)

Aplicația salvează ID-ul problemei curente, etapa atinsă și ID-urile rezolvate. Pentru problemele de mat, salvează și mutarea finală efectivă, astfel încât tabla și explicația să redea matul jucat. Rezolvările mai vechi fără mutare finală salvată afișează linia de referință. Reordonarea schimbă numărul afișat, iar identitatea salvată stabilește ce problemă se redeschide. Un ID curent scos din catalog duce la prima problemă disponibilă. Rezolvările vechi intră din nou în total dacă problema revine în catalog.

La prima pornire se deschide meniul. După alegere, redeschiderea revine în același capitol și reia poziția unei probleme în curs. Dacă răspunsul era în așteptare, pauza începe din nou în prim-plan și apărarea se aplică o singură dată. Butonul **Meniu** revine la selecție, păstrând progresul fiecărui capitol. **Următoarea** se activează după rezolvarea problemei și rămâne în același capitol.

Bara arată progresul în capitolul deschis. Pragurile 5, 15, 30, 50, 75 și finalul capitolului se recalculează din ID-urile rezolvate. La atingerea unui prag, ecranul confirmă realizarea și afișează următoarea țintă. Bara și textul pornesc de la același total după reluare. Ideea țintelor vizibile vine din Puzzle Journey. [Chess.com Puzzle Points](https://support.chess.com/en/articles/9681952-what-are-puzzle-points-on-chess-com)

[Regulile de șah și poziția inițială](https://handbook.fide.com/chapter/e012023)
