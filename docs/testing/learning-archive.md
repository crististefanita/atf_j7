# Admiterea în arhiva de învățare

## Dovezile obligatorii

Partidele pentru învățare stau în `C:\work\sah\.studii`, în afara checkout-ului.
Înaintea fiecărei mutări efective, a albului **și** a negrului, sunt obligatorii
calculul Hard nativ și un indiciu public separat, cerut la maximum 200.000 de
noduri totale. Calculul poate termina sub plafon. Alocația nativă și estimarea
afișată rămân separate. Regula include prefixele din teorie și replicile oracolului.

[`LearningGameAudit`](../../atf-tests/src/test/java/ro/atf/chess/LearningGameAudit.java)
leagă sursa, runtime-ul, configurația, originea, istoricul complet, culoarea,
contoarele, deciziile, liniile oferite și plafonul cerut. Jurnalul înlănțuit este
scris și confirmat pe disc înaintea avansării partidei. Scrierea eșuată păstrează
tabla și analiza în așteptare.
[`LearningGameAuditTest`](../../atf-tests/src/test/java/ro/atf/chess/LearningGameAuditTest.java)
respinge începutul lipsă, rularea străină, fazele substituite și cererile reduse.

## Admitere și calitate

Admiterea pe integritate este separată de calitatea indiciilor. Greșelile complet
înregistrate sunt contraexemple utile, fără a deveni lecții validate sau PASS de
calitate. Compară fiecare rădăcină oferită și fiecare replică ipotetică cu o căutare
adversă liberă la Stockfish H17/H19, în contextul complet identic. Verifică și
continuarea după captura unei piese valoroase: recapturile legale și apărările
liniștite mai bune. Legalitatea PV-ului sau scorul nativ ridicat nu demonstrează
un sacrificiu corect. Păstrează sacrificiile corecte confirmate drept controale.

Partidele istorice fără dovezile cerute înaintea mutărilor se retrag și se șterg
din corpusul de învățare. Recalcularea actuală nu dovedește existența analizelor
înaintea mutărilor istorice. Traseele noi primesc identități noi. Pentru partidele
șterse rămân doar amprentele contra contaminării și totalurile retragerii.
Fixture-urile sursă și dovezile edițiilor rămân istoric sursă, în afara învățării.

## Reproducere și retenție

README-ul arhivei externe deține căile corpusului actual, dependențele fixate,
identitățile sursă, recepțiile oracolului și comenzile de replay. Originea compusă
cere contor zero până la calificarea restaurării contoarelor FEN inițiale nenule.
Marchează explicit plafonarea; un traseu tematic neterminat nu este partidă Elo.

Păstrează uneltele reutilizabile, certificatele regresiei actuale, definițiile
problemelor deschise și bazele exacte de finaluri. Recepțiile Stockfish istorice
își păstrează identitatea inițială și pot servi doar ca referințe. Elimină HTML-ul
redundant, build-urile copiate, experimentele respinse și partidele neconforme după
verificarea fișierelor păstrate, referințelor Git și proceselor active.

Folosește [protocolul indiciilor](hard-hint-study.md) pentru interpretarea oracolului
și [protocolul ligii native](hard-audit.md#elo-și-partidele-neterminate) pentru putere.
