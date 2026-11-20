# Curse cu pion exterior

## Pe scurt

Modelul comun recunoaște o structură restrânsă de final: un pion liber avansat este blocat
direct de regele advers, iar partea care îl blochează are un pion exterior pe care regele
apărător nu îl ajunge. Corecția privește bonusul avansării, nu materialul, rezultatul căutării
sau rezultatul unei baze de finaluri. [Rezultate curente](../testing/hard-status.md)

## Evaluarea structurii

Ajustarea se aplică numai regilor și pionilor. Pionul exterior trebuie să fie la cel puțin
două coloane de pionul blocat, cu coloana promovării liberă și în afara pătratului regelui
apărător. Calculul include tempoul inițial al apărătorului. Promovarea accesibilă regelui,
pionul de pe coloana vecină, altă piesă sau lipsa blocajului direct păstrează evaluarea inițială.

[Politica tipizată](../../chess-core/src/main/resources/pawn-blockade.yaml) elimină o fracție
configurabilă din bonusul existent al avansării în final. Zero sau dezactivarea sunt neutre.
Modelul este comun pentru Basic, Advanced, Hard, indicii și Game Review; politica de noduri
neschimbată nu implică alegeri identice la nivelurile inferioare. [Geometria](../../chess-core/src/main/java/ro/atf/chess/PawnRace.java)
nu conține ID de problemă, căutare după FEN, cheie din catalog sau scor de la profesor.

## Confirmarea taxată a liderului

După schimbul turnurilor, avansarea susținută a pionului exterior poate înlocui liderul de la
orizontul impar numai dacă orizontul par se încheie, liderul vechi nu mai este la egalitate
pe primul loc și ambii lideri sunt comparați exact la orizontul par anterior. Ambele comparații
trebuie să se încheie și să prefere mutarea nouă. Folosesc aceeași alocație obișnuită; întreruperea
păstrează liderul vechi. Mutarea regelui nu poate împrumuta excepția. Regula de stabilitate
existentă rămâne valabilă în afara avansării explicit susținute.

Geometria declanșează verificarea, nu certifică victoria. Replicile adverse libere decid rezultatul.
Discountul general respins pierdea spargerea câștigătoare din `021cc`; regula restrânsă o păstrează.
Repararea inițială `Rc8+` a necesitat și `h4` după schimb: alegerea primei mutări corecte fără
conversia partidei rezultate nu a fost suficientă.

## Probe și limite

[PawnBlockadeTest](../../atf-tests/src/test/java/ro/atf/chess/PawnBlockadeTest.java) verifică ambele
culori, tempoul inițial, regele care ajunge promovarea, pionii vecini, neutralitatea cu turnuri,
confirmarea întreruptă și YAML strict. [PawnRaceEvidenceTest](../../atf-tests/src/test/java/ro/atf/chess/PawnRaceEvidenceTest.java)
păstrează martorii reci Stockfish H17/H19 liberi/forțați, respinge fazele alterate și păstrează
subpromovarea corectă la cal împotriva fiecărei replici legale.

Studiul extern `C:\work\sah\.studii\experiments\2026-11-20-hard-rook-defense\README.md`
păstrează candidații respinși, partidele observate inițial, fiecare încercare de indiciu maxim
înaintea mutării, analizele finale și fazele profesorului legate de sursă. Rejucarea unei poziții
salvate este regresie, nu observație nouă. Victoria într-un final compus este conversie, nu Elo.
Păstrează verdicte separate pentru recomandare, calibrarea scorului, catalogul complet și forța
în partide. [Protocolul studiilor native](../testing/hard-puzzle-study.md)
