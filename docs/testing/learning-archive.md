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

## Analiza finală și capetele indiciilor

Atașează fiecărei partide salvate analiza nativă completă, într-un `review.json`
separat. Folosește `GameReview.analyze` pentru un rezultat efectiv și `analyzeLine`
pentru un traseu plafonat; plafonarea nu devine partidă finalizată. Păstrează toate
mutările, culorile, etichetele, perspectiva scorului, adâncimile încheiate, liniile
jucată/recomandată, explicațiile și cache-ul legat de checkpoint. Traseele plafonate
nu au rating. Estimările locale după o partidă terminată nu sunt Elo măsurat și
trebuie să păstreze rolurile efective ale actorilor.

[`LearningReviewAudit`](../../atf-tests/src/test/java/ro/atf/chess/LearningReviewAudit.java)
leagă jurnalul original de identitatea separată a analizorului, politica încărcată,
checkpoint-ul final, rezultat și progresul cronologic neîntrerupt. Respinge mutările
lipsă, rulările reetichetate și scorurile lizibile diferite de cache. Căutarea
analizei este configurată la 85k noduri suplimentare; API-ul nu expune consumul
efectiv pe mutare. Separă acest plafon de indiciul public original de 200k.

Compară fiecare ramură jucată și recomandată, la fiecare semimutare ipotetică, cu
dovezi independente H17/H19. Fazele existente ale oracolului se pot referi doar cu
identitatea inițială și istoric complet, origine, rădăcină, binar și opțiuni identice.
Fazele noi își păstrează identitatea proprie. Nu reeticheta rezultatele moștenite.

Pentru fiecare indiciu public, verifică exact ultima mutare și poziția finală.
Enumeră capturile **legale** ale ultimei piese importante, inclusiv replica mai bună
de după capătul afișat. Un pion legat nu este recaptură legală. Expunerea singură
nu înseamnă sacrificiu greșit: păstrează `…Qg3 hxg3 …Rh1#` drept control corect.
Reține scorurile libere și impuse și evaluează ultima mutare înaintea interpretării
replicii. Coada Caro–Kann `…Qxd4`, damă contra pion, este un contraexemplu confirmat,
fără a justifica interzicerea schimburilor sau a tuturor sacrificiilor.

[`LearningPostgameBenchmarkTest`](../../atf-tests/src/test/java/ro/atf/chess/LearningPostgameBenchmarkTest.java)
cere analiza nativă și forma lizibilă pentru fiecare partidă, ambele ramuri, toate
capetele publice și fazele brute ale oracolului. Integritatea, calitatea capetelor
expuse și calitatea recomandărilor au verdicte Extent separate. Punctele logistice
native și WDL Stockfish au modele diferite; etichetele divergente cer calibrare.

## Reconfirmarea unei capturi cu dama

Păstrează producătorul inițial și varianta greșită originală drept contraexemplu.
Înregistrează un indiciu nou din sursa corectată, cu consumul total real; nu modifica
jurnalele istorice și nu reeticheta oracolul. Compară toate rădăcinile oferite pe
contextele de regresie înghețate și pe contexte noi înaintea promovării frontierei.
Nu activa modul tactic nou dintr-o comparație preliminară sub orizontul inițial
încheiat. Identitatea cache și sprijinul la ture egale trebuie să distingă modurile.
Un candidat suplimentar căutat selectiv nu devine automat echivalent.

Rejucarea catalogului Hard nativ este o măsurare separată. Egalitatea analizelor
native complete—alegeri, adâncimi, costuri și variante—demonstrează păstrarea lor,
nu o creștere Elo. Două partide cu Stockfish la putere completă sunt diagnostice,
nu o ligă evaluată. Păstrează fiecare pereche Hard/indiciu înaintea mutării, în
ambele culori, și atașează analiza nativă completă înainte să termine oracolul.
Fazele încă necalculate rămân în așteptare; integritatea și calitatea șahului au
verdicte Extent separate.
