# Tabla de joc

Acest ghid explică tabla, problemele și partidele din Chess Lab. Alege
[Probleme](#tactică-și-puncte), [Tablă de analiză](#tablă-de-analiză) sau
[Partidă cu calculatorul](#partidă-cu-calculatorul).

## Tactică și puncte

- În **Tactică**, alege mutarea care câștigă cel mai mult material. Verificarea ia în calcul mutarea ta, replica adversarului și continuarea ta. Poți deschide explicația pentru linia completă.
- În **Probleme → Antrenament tactic** găsești 96 de poziții de apărare, deviere, sacrificiu, mutări liniștite, furculițe și legări. Progresul și punctele se păstrează când revii în capitol. [Catalog și verificare](quest-curation.md#antrenament-tactic-și-lot-de-verificare)
- În **Probleme → Mutarea intermediară**, caută șahul sau captura de forțare înaintea recapturii. Capitolul crește în dificultate pe trei trepte. [Catalog și punctaj](quest-curation.md#mutarea-intermediară)
- Becul de sub tablă are o zonă de atingere de 48 dp. Prima apăsare marchează piesa, a doua adaugă săgeata, iar următoarea explică soluția. În timpul replicii, comanda este dezactivată.
- Prima rezolvare a fiecărei probleme acordă 10 puncte. O serie fără greșeli sau indicii adaugă câte 2 puncte la fiecare problemă, începând de la a doua, până la 20 de puncte pe problemă. Flacăra arată lungimea seriei și funcționează între capitole.
- O greșeală sau folosirea becului întrerupe seria. Rezolvarea acelei probleme rămâne eligibilă pentru cele 10 puncte de bază. Revizitarea și reluarea păstrează punctele deja acordate. Problemele rezolvate înainte de această schimbare primesc creditul de bază.

Sistemul de puncte și bonusul de la a doua rezolvare urmează modelul Puzzle Points. În J7, flacăra reprezintă problemele consecutive rezolvate fără ajutor. [Chess.com Puzzle Points](https://support.chess.com/en/articles/9681952-what-are-puzzle-points-on-chess-com)

## Tablă și indicii

- Pătratele verzi și crem încadrează piesele Staunton. Crestătura deschisă și
  silueta mai înaltă deosebesc nebunul de pion. Coordonatele stau pe margine,
  iar ultima mutare marchează plecarea și destinația.
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

[`quests.yaml`](../../chess-core/src/main/resources/quests.yaml) păstrează poziția FEN, soluția UCI și tema fiecărei probleme. Setul are 500 de poziții, câte 250 cu fiecare culoare la mutare. Catalogul include compoziții pentru mat prin rocadă, en passant, promovare la cal și apărare din șah.

| ID | Indiciu | Soluție |
|---|---|---|
| `mate-101` | Nebunul de pe `h6` | `Nd2#` oprește șahul turnului negru și deschide coloana h pentru dama albă. |
| `mate-102` | Pionul de pe `h3` | `hxg4#` capturează calul care dă șah și eliberează coloana h pentru turnul alb. |

[`mate-in-two.yaml`](../../chess-core/src/main/resources/mate-in-two.yaml) are 300 de poziții cu o singură mutare inițială care forțează matul. După acea mutare, aplicația joacă apărarea de referință și acceptă orice mat legal din poziția rezultată. Indiciile urmează mutarea curentă, iar explicația arată linia completă. [Selecție și proveniență](quest-curation.md#mat-în-două-mutări)

[`mate-in-three.yaml`](../../chess-core/src/main/resources/mate-in-three.yaml) conține 200 de poziții de mat exact în trei, cu o singură primă mutare câștigătoare. Jocul aplică cele două apărări de referință, afișează câte mutări au mai rămas după prima apărare și acceptă orice mat legal pe ultima mutare. [Selecție și proveniență](quest-curation.md#mat-în-trei-mutări)

[`tactics.yaml`](../../chess-core/src/main/resources/tactics.yaml) adaugă 100 de poziții cu o singură mutare care câștigă material la orizontul capitolului. Selecția include furculițe, legări, atacuri descoperite și devieri.

## Tablă de analiză

- Din meniul principal pornești de la poziția inițială. Dintr-o partidă, lupa de lângă istoric deschide exact poziția afișată și păstrează mutările partidei disponibile pentru navigare.
- Joci manual pentru alb și negru. Evaluarea se actualizează local după fiecare poziție, iar becul arată o continuare. Săgețile și mutările din bandă selectează poziții. O mutare diferită înlocuiește continuarea din tablă, iar **Resetează linia** readuce poziția și continuarea de la intrare.
- După ce afișezi o variantă, atinge direct destinația din săgeată pentru a juca prima mutare, inclusiv promovarea indicată. Dacă selectezi o piesă după afișarea indiciului, alegi mutarea în mod obișnuit. Pe o poziție istorică jucabilă, mutarea se aplică liniei de studiu.
- După abandon sau remiză prin acord, ultima poziție rămâne jucabilă în analiză. Matul și finalurile automate rămân finale. Poți explora de la o poziție anterioară.
- În **Opțiunile tablei de analiză**, bifa **Analiză AI** controlează bara, becul și **Analizează toate mutările**. Raportul parcurge întreaga linie curentă, chiar dacă săgețile te-au dus la o poziție anterioară. Compară albul și negrul prin grafic, categorii și precizie. Din detaliul oricărei mutări poți reveni pe tablă sau porni din poziția de dinaintea mutării.
- Când linia păstrează mutările unei partide încheiate, aceeași comandă deschide **Analiza partidei** cu jucătorul, AI-ul, graficul, toate mutările și estimările Elo disponibile. Dacă partida are loc în arhivă, raportul se salvează acolo. O ramură editată sau o linie jucată manual rămâne un studiu pe culori, cu precizie și categorii.
- **Continuă ca partidă salvată** păstrează istoricul până la poziția selectată și folosește un loc liber dintre cele zece. Bifează **Lasă AI-ul să mute** pentru replici automate. Debifează pentru a juca ambele culori. Partida din care ai intrat în studiu își păstrează salvarea. Dacă toate locurile sunt ocupate, tabla păstrează linia deschisă. O continuare pornită cu un prefix din studiu păstrează graficul și precizia la final, cu Elo indisponibil pentru acea linie.

Navigarea și mutările manuale pentru ambele culori urmează fluxul de analiză individuală. [Tabla de analiză Chess.com](https://support.chess.com/en/articles/8583825-how-do-i-use-the-analysis-board)

## Partidă cu calculatorul

Meniul principal grupează **Partidă nouă**, **Partide salvate** și **Partide încheiate** în secțiunea **Partide**. Problemele, deschiderile și tabla de analiză sunt în **Studiu**, iar numele jucătorului este în **Profil**. Ambele liste de partide se deschid ca ecrane dedicate, în stilul meniului **Probleme**. **Înapoi** rămâne vizibil când derulezi și revine la ecranul de unde ai venit.

**Partide încheiate** păstrează ultimele zece rezultate, în ordinea încheierii, de la cel mai recent. Poți redeschide tabla și istoricul fiecărei partide. Dacă ai lăsat analiza să se termine, lista o marchează ca salvată, iar **📊 Analiza partidei** o deschide direct. Continuarea pornită din arhivă folosește un loc liber și păstrează rezultatul original. La al unsprezecelea rezultat, cel mai vechi rezultat și analiza lui ies din arhivă.

**Partide salvate** arată doar partidele în desfășurare, până la zece, de la cea mai recent începută. La încheiere, partida trece în **Partide încheiate** în aceeași salvare. **Partidă nouă** folosește primul loc gol, apoi poate reutiliza locul unei partide arhivate. Când toate locurile sunt ocupate de partide active, configurarea anunță înlocuirea celei mai vechi salvări. Reluarea unei partide îi păstrează locul în ordine. Pentru salvările din edițiile anterioare, ordinea inițială urmează numărul locului.

Rândul începe cu numărul partidei și numele adversarului în **Partide salvate** și cu numele adversarului în **Partide încheiate**. Sub titlu apar numărul de mutări înregistrate și starea sau rezultatul. Fiecare mutare a albului sau a negrului se numără o dată.

Eticheta discretă de la baza rândului arată ziua și ora locală. În **Partide salvate** apare momentul începerii, iar în **Partide încheiate** momentul terminării. Pentru partidele vechi a căror dată lipsește, rândul afișează titlul și mutările.

**Joacă și mutările AI-ului**, deasupra tablei, îți permite să joci mutările legale ale ambelor culori. Opțiunea se salvează separat pe fiecare partidă. Dacă o debifezi în tura AI-ului, motorul reia tura curentă. Când schimbi partida, o replică aflată în calcul se anulează și poate fi recalculată când revii la acea partidă. Dacă ai jucat pentru AI, analiza afișează precizia și categoriile mutărilor pe culori, iar estimările Elo sunt indisponibile pentru acea partidă.

**Analiza partidei** ocupă fereastra aplicației din timpul calculului, cu butonul **Închide** în antet. Derulează graficul, precizia, estimările Elo, tabelul și lista mutărilor în același ecran. În tabel, coloana **Jucător** este mereu prima dintre cele numerice, inclusiv când joci cu negrul. Fiecare categorie are un rând, cu zero când lipsește. [Reperul Game Review](https://support.chess.com/en/articles/10328363-how-do-i-use-game-review-on-the-app)

[Schemele și formulele analizei](game-review.md)

După rezultat, pictograma 📊 marchează intrarea în **Analiza partidei**. În timpul jocului, butonul **Opțiunile partidei** oferă remiză la mutarea ta. AI-ul refuză când are avantaj material și acceptă în celelalte poziții. **Renunță la partidă** cere confirmare și salvează victoria AI-ului. Meniul principal păstrează partida în curs pentru reluare.

Din meniul principal, **Probleme** grupează capitolele și progresul. În **Profil**, atinge numele pentru a-l edita sau reseta. Numele are cel mult 10 caractere și apare la locul jucătorului, inclusiv în partida reluată.

În **Partidă nouă**, alege culoarea Alb, Negru sau Aleatoriu și nivelul AI. Jocul rezolvă alegerea aleatorie o singură dată și așază culoarea jucătorului jos. Când joci cu negrul, calculatorul deschide cu albele. Alex joacă la Basic, Mira la Advanced și Atlas la Hard. Basic caută trei semimutări, iar Advanced patru. Hard încearcă șase în limita existentă de 44.000 de noduri și acceptă ultimul orizont numai dacă păstrează mutarea lider de la adâncimea anterioară; altfel folosește rezultatul complet stabil. În finalurile de turnuri poate extinde cu încă o semimutare, până la șapte, folosind istoricul repetițiilor și același plafon. La egalitate, motorul preferă pozițiile vizitate mai rar și mutările care dezvoltă piesele sau ocupă centrul. Replica apare după cel puțin 650 ms. [Politica și validarea Stockfish](ai-quality.md)

Jocul salvează istoricul înainte de a afișa fiecare mutare. Alegerea unei partide din **Partide salvate** și redeschiderea directă refac poziția, culoarea, nivelul, analiza și orientarea. Dacă motorul are mutarea, pauza reîncepe și urmează o singură replică. Salvările anterioare revin cu Alb și Basic.

Sub tablă, săgețile parcurg fiecare mutare, iar săgeata cu opritor revine la ultima. Lista se derulează orizontal și permite alegerea directă a oricărei poziții, inclusiv **Start**. Mutarea selectată este evidențiată discret. Controalele au zone de atingere de 48 dp. Pozițiile anterioare se afișează cu tabla blocată. Dacă botul răspunde în timp ce te uiți în urmă, mutarea nouă apare în listă și poziția consultată rămâne vizibilă. [Navigarea între mutări pe Chess.com](https://support.chess.com/en/articles/10328363-how-do-i-use-game-review-on-the-app)

Alege o poziție din istoric și apasă **Continuă de aici** sub tablă. Confirmarea păstrează poziția și mutările care duc la ea. Mutările de după poziția aleasă sunt eliminate din partida salvată. Bifa **Lasă AI-ul să mute** pornește replica atunci când e rândul calculatorului. Poți continua și dintr-o partidă terminată. Din arhivă, continuarea folosește un loc liber.

La final, deschide **Analiza partidei** din **Opțiunile partidei** sau de sub
rezultat. Cu **Analiză AI** activată, raportul este disponibil și după prima
mutare a unei partide în curs; replica automată așteaptă până îl închizi.

- Alegerea unei mutări din listă sau din grafic sincronizează tabla, scorul,
  notația și categoria. Comenzile permit navigarea și saltul la următorul moment-cheie.
- **De ce?** explică diferența dintre mutarea aleasă și recomandare. Poți parcurge
  ambele linii cu săgețile. Becul indică următoarea mutare din varianta recomandată.
- O analiză arhivată se redeschide la ultima mutare, linie și pas consultat.
  **Vezi pe tablă** revine la poziția selectată în partidă.
- **Reîncearcă mutarea** propune reluarea din poziția anterioară. După confirmare,
  partida din arhivă rămâne disponibilă și ramura folosește un loc separat.
- Reperul AI și estimarea Elo apar în analiza unei partide încheiate, când
  sunt aplicabile. [Metoda de analiză și estimare](game-review.md)

Bifează **Analiză AI** înainte de pornire. Bara verticală reflectă evaluarea poziției afișate, inclusiv în istoric. Segmentul mai lung indică partea în avantaj, iar la întoarcerea tablei segmentele își schimbă capetele. Textul arată scorul pozițional în unități de pion. `+1,25 · Alb` favorizează albul, iar `−1,25 · Negru` favorizează negrul. Bara se umple la opt unități sau la mat și se împarte egal la remiză. [Convenția de evaluare Chess.com](https://support.chess.com/en/articles/8379354-computer-evaluation-numbers-on-chess-com)

Apasă becul din bara de sub tablă pentru cea mai bine evaluată linie și săgeata primei mutări. Când controlezi culoarea la mutare, atinge destinația indicată ca să joci prima mutare. În istoric poți consulta varianta și alege **Continuă de aici** pentru a juca din poziția afișată. În tura automată a AI-ului, așteaptă replica sau activează **Joacă și mutările AI-ului**.

Indiciul pornește de la căutarea Hard, independent de nivelul adversarului. Comparația la adâncime comună folosește cel mult 32.000 de noduri din plafonul de 220.000 de noduri suplimentare al indiciului. Apăsările următoare parcurg până la cinci variante, în ordinea scorului. Lista se oprește când o mutare pierde mai mult de un sfert de pion față de prima. În poziții cu mat demonstrat, apar doar maturile cu același rezultat și aceeași distanță. Schimbarea poziției și încheierea partidei ascund indiciul. Opțiunea **Analiză AI** se păstrează la reluare. [Evaluarea AI](ai-quality.md)

După ce apeși becul, atinge varianta subliniată. Tabla ei pornește de la poziția
consultată. Prima săgeată înainte joacă recomandarea, a doua arată replica
adversarului calculată de motor. Eticheta indică semimutarea și culoarea care
a jucat. Săgeata înapoi parcurge linia invers, iar **Înapoi la poziție** revine
la mutarea din istoric cu indiciul păstrat. Același parcurs este disponibil pe
tabla manuală. Explicația numește piesa care dă mat sau captura care păstrează
un câștig material în linia calculată. În rest arată evaluarea motorului.
Comparația folosește ordinea deja verificată a indiciului, la ultima adâncime
terminată pentru toate variantele comparate. Prima variantă din bec și linia
recomandată coincid. Dacă parcurgi alternativele cu becul, dialogul deschide
linia afișată, iar atingerea destinației joacă exact mutarea marcată. Poți
parcurge ambele linii, inclusiv o alternativă din afara pragului becului.
Atinge explicația pentru a sări la captura sau replica de mat indicată. Pentru
a schimba recomandarea pozițională, motorul cere o căutare mai adâncă și
confirmarea după un număr egal de mutări pentru ambele părți sau un câștig
material în linie. Când lipsește confirmarea sau s-ar pierde dovada de mat,
dialogul oferă doar linia disponibilă. La revenire, becul păstrează varianta
marcată înainte de deschiderea dialogului.

Sub fiecare nume apar piesele capturate, cu un număr lângă simbol când sunt mai multe de același fel. Jucătorul cu avantaj material vede `+N` în același rând. Capturile și avantajul urmează semimutarea afișată, inclusiv în istoric și după reluare. [Referința de afișare Chess.com](https://support.chess.com/en/articles/8708638-how-can-i-see-the-captured-pieces-and-material-count)

Motorul aplică rocada, en passant și alegerea la promovare. Partida se oprește la mat, pat, configurațiile uzuale de material insuficient, a cincea apariție a poziției sau 75 de mutări de fiecare parte fără captură ori mutare de pion. Remizele la cerere fac parte din regulile de competiție. [Regulile FIDE](https://handbook.fide.com/chapter/e012023)

## Dificultate și progres

Mat în 1 începe cu matul nebunului și continuă în ordinea strict crescătoare a scorului intern. Profilul YAML stabilește ponderile pentru numărul de mutări legale, șahurile înșelătoare, apărările unice, mutările speciale și matul pornit din șah. Ordinea derivată se încarcă din `quest-order.yaml`. Mat în 2 și Mat în 3 urmează ratingurile din cataloage, iar Tactică urmează câmpul `difficulty`. [Profil YAML](../../chess-core/src/main/resources/quest-difficulty.yaml) · [Cum se întrețin cataloagele](quest-curation.md#configurarea-capitolelor)

Aplicația salvează ID-ul problemei curente, etapa atinsă și ID-urile rezolvate. Pentru problemele de mat, salvează și mutarea finală efectivă, astfel încât tabla și explicația să redea matul jucat. Rezolvările mai vechi fără mutare finală salvată afișează linia de referință. Reordonarea schimbă numărul afișat, iar identitatea salvată stabilește ce problemă se redeschide. Dacă problema curentă a fost retrasă, aplicația deschide prima problemă nerezolvată din același capitol. Rezolvările retrase rămân în salvare ca istoric, în afara totalului activ. ID-urile retrase rămân rezervate. [Registrul de ID-uri](../../chess-core/src/main/resources/quest-retired.yaml)

La prima pornire se deschide meniul. După alegere, redeschiderea revine în același capitol și reia poziția unei probleme în curs. Dacă răspunsul era în așteptare, pauza începe din nou în prim-plan și apărarea se aplică o singură dată. Butonul **Meniu** revine la selecție, păstrând progresul fiecărui capitol. **Următoarea** se activează după rezolvarea problemei și rămâne în același capitol.

Bara arată progresul în capitolul deschis. Pragurile 5, 15, 30, 50, 75 și finalul capitolului se recalculează din ID-urile rezolvate. La atingerea unui prag, ecranul confirmă realizarea și afișează următoarea țintă. Bara și textul pornesc de la același total după reluare. Ideea țintelor vizibile vine din Puzzle Journey. [Chess.com Puzzle Points](https://support.chess.com/en/articles/9681952-what-are-puzzle-points-on-chess-com)

[Regulile de șah și poziția inițială](https://handbook.fide.com/chapter/e012023)
