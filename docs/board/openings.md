# Deschideri

Din meniul principal, intră în **Deschideri**, alege o linie și joacă cu albele sau cu negrele. Tabla se orientează cu piesele tale jos. Aplicația redă replicile din lecție și explică ideea fiecărei mutări. O mutare legală din altă variantă lasă lecția la pasul curent.

| Lecție | Poziția studiată | Ideea urmărită |
|---|---|---|
| Partida italiană | Giuoco Pianissimo, C54 | Centrul susținut de c3 și d3, dezvoltarea și rocada. [Chess.com](https://www.chess.com/openings/Italian-Game) |
| Partida spaniolă | Varianta închisă cu 6.d3, C84 | Presiunea pe calul de pe c6 și consolidarea pionului e4. [Chess.com](https://www.chess.com/openings/ruy-lopez-opening) · [Linia C84 în catalogul Lichess](https://github.com/lichess-org/chess-openings/blob/master/c.tsv#L1193) |
| Apărarea Caro–Kann | Avans, varianta Short, B12 | Nebunul iese înainte de e6, apoi negrul atacă baza lanțului de pioni. [Chess.com](https://www.chess.com/openings/Caro-Kann-Defense-Advance-Variation) · [Linia Short în catalogul Lichess](https://github.com/lichess-org/chess-openings/blob/master/b.tsv#L302) |
| Gambitul damei refuzat | Apărarea ortodoxă, D63 | Negrul susține d5 și dezvoltă piesele de pe flancul regelui. [Chess.com](https://www.chess.com/openings/Queens-Gambit-Declined-Orthodox-Defense) |
| Apărarea siciliană | Najdorf, B90 | Schimbul pionului c pentru pionul d și controlul câmpului b5 cu a6. [Chess.com](https://www.chess.com/openings/Sicilian-Defense-Open-Najdorf-Variation) |
| Partida scoțiană | Atacul Blackburne, C45 | Deschiderea rapidă a centrului și presiunea asupra calului de pe d4. [Chess.com](https://www.chess.com/openings/Scotch-Game) · [Linia Blackburne în Lichess](https://github.com/lichess-org/chess-openings/blob/master/c.tsv#L753) |
| Apărarea franceză | Avans, C02 | Albului îi revine spațiul de pe e5, iar negrul atacă baza d4 cu c5. [Chess.com](https://www.chess.com/openings/French-Defense-Advance-Variation) · [Linia cu 6.a3 în Lichess](https://github.com/lichess-org/chess-openings/blob/master/c.tsv#L60) |
| Apărarea scandinavă | Varianta clasică, B01 | Dezvoltare cu tempo asupra damei și eliberarea nebunului cu Bf5. [Linia clasică în Lichess](https://github.com/lichess-org/chess-openings/blob/master/b.tsv#L95) |
| Sistemul Londonez | Împotriva fianchetto-ului, A48 | Bf4 înainte de e3 și dezvoltarea pentru rocada ambelor părți. [Chess.com](https://www.chess.com/openings/London-System) · [Linia cu Be2 în Lichess](https://github.com/lichess-org/chess-openings/blob/master/a.tsv#L642) |
| Apărarea est-indiană | Aronin–Taimanov, E97 | Centrul larg al albului, contraatacul cu e5 și poziția închisă după d5. [Linia Aronin–Taimanov în Lichess](https://github.com/lichess-org/chess-openings/blob/master/e.tsv#L358) |

Săgețile deasupra tablei parcurg câte o semimutare. În istoric vezi poziția și explicația mutării alese. Atinge progresul pentru a sări la o poziție jucată. **Rejoacă de aici** reia lecția de la poziția afișată. Comanda sistemului **Înapoi** revine din istoric la poziția curentă.

La poziția curentă, săgeata înainte aplică exact următoarea mutare din lecție. Când replica din catalog rămâne în așteptare, mai apasă o dată săgeata sau folosește **Continuă lecția**. Dacă joci cu piesele pe tablă, replica vine automat. La final poți reveni la orice pas sau exersa din nou.

Pe aceeași fereastră, tabla păstrează poziția și dimensiunea când parcurgi mutările. Progresul și butoanele își rezervă înălțimea, iar **Indiciu lecție**, **Continuă lecția** și **Rejoacă de aici** folosesc același loc.

**Indiciu lecție** evidențiază întâi piesa. **Arată mutarea** selectează piesa, trasează săgeata și afișează coordonatele și ideea. Atinge destinația marcată ca să joci mutarea.

**Analiză AI** evaluează poziția afișată, inclusiv în istoric și la finalul lecției. Becul parcurge până la cinci continuări. Când sugestia coincide cu mutarea ta din lecție, o poți juca atingând destinația. O sugestie diferită apare etichetată drept variantă AI. Schimbarea poziției ascunde săgeata anterioară și actualizează evaluarea. [Politica indiciilor AI](ai-quality.md)

## Configurarea lecțiilor

Ordinea elementelor din `openings.yaml` stabilește ordinea din meniu. Fiecare lecție are un ID stabil, cod ECO, sursă HTTPS, titlu, variantă și plan în română și engleză. În `line`, fiecare pas conține mutarea UCI și ideea în ambele limbi. Pentru rocadă se declară mutarea regelui, de exemplu `e1g1`. Motorul reia toate mutările de la poziția inițială și indică lecția și pasul dacă găsește o mutare ilegală. [Catalogul YAML](../../chess-core/src/main/resources/openings.yaml)

La adăugarea unei linii, verificați codul ECO și mutările din sursă. Explicați ce schimbă mutarea pe tablă și ce plan pregătește. Păstrați ID-ul la editarea textului, iar pentru o variantă nouă adăugați o intrare distinctă. Rulați suita de componente pentru ambele culori și proba Android pentru navigare, rejucare și indicii. [Setul ECO Lichess](https://github.com/lichess-org/chess-openings) · [Strategia de testare](../testing/README.md#deschideri)
