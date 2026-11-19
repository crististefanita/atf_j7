# Admiterea în arhiva de învățare

## Dovezile obligatorii

Partidele pentru învățare stau în `C:\work\sah\.studii`, în afara checkout-ului.
Înaintea fiecărei mutări efective, a albului **și** a negrului, sunt obligatorii
calculul Hard nativ și un indiciu public separat, cerut la maximum 200.000 de noduri
obișnuite, plus rezerva dovezii taxată separat când este necesară. Calculul poate
termina sub aceste plafoane. Alocația nativă și estimarea
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
obișnuită a analizei este configurată la 85k noduri suplimentare. Recepția dovezii
păstrează consumul obișnuit și rezerva; mutările vechi fără recepție nu primesc un
consum inventat. Separă aceste valori de alocația obișnuită de 200k a indiciului.

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

Benchmarkul de componentă `LearningArchiveBenchmarkTest.nativeHardChoicesHaveNoStableLargeIndependentOracleLoss`
păstrează o poartă de calitate separată pentru alegerile native. Leagă fiecare
mutare observată de jurnalul original și afișează în Extent ambele pierderi WDL
independente. O corecție ulterioară cere o rejucare cu sursa curentă sau o partidă
nouă și dovezi de oracol actualizate; schimbarea mutării așteptate ca să accepte
defectul nu îl poate închide.

[Micul fixture permanent](../../atf-tests/src/test/resources/hard-learning-witnesses.yaml)
păstrează istoricul complet exact și opt faze brute ale oracolului, în sintaxa JSON
validă YAML 1.2. `HardLearningWitnessTest` îl validează offline și respinge
substituirile; `HardLearningWitnessBenchmarkTest` recalculează alegerea nativă
curentă și păstrează fiecare abatere roșie până la corectare. O alternativă nouă
cere dovezi noi de oracol. Pozițiile sunt contraexemple, nu intrări în cartea de joc.

## Studii de continuare până la rezultat

Colectarea din 19 noiembrie înregistrează Engleza și Scandinava de la tabla inițială
până la mat, plus martorul sacrificiului corect de damă. Toate cele 106 semimutări
efective au propriul calcul Hard și indiciul public maxim înainte de joc, inclusiv
mutările teoretice și Stockfish. Cele 759 de semimutări ipotetice publice au 2.692 de
cereri reci distincte H17/H19. Analizele native complete folosesc un analizor identificat
separat. Fiecare ramură de analiză și capăt cu piesă importantă expusă cere propria
acoperire cu oracolul.

Folosește repetiția automată de cinci ori și regula celor 75 de mutări când colectorul
nu implementează revendicarea remizei de către jucător. O remiză doar revendicabilă
prin trei repetiții/50 de mutări nu trebuie să taie un joc nativ în desfășurare. Un
traseu plafonat folosește în continuare `analyzeLine` și nu are rating de joc încheiat.
Instrumentele externe sunt înghețate cu studiul; schimbările sursei live cer o rejucare
explicită și propria identitate de execuție, nu jurnale editate.

Păstrează integritatea și calitatea absolută în rapoarte Extent separate. O regresie
componentă trecută sau echivalența cu minimax nu șterg greșelile arhivate, nu califică
o familie nouă de deschidere și nu stabilesc Elo. Salvează un martor mic al primei
pierderi, cu context integral, în resursele testelor; păstrează PGN-ul, toate observațiile
pre-move și analizele complete în corpusul extern. Nu transforma cheile în mutări
forțate la execuție.

O colecție cu deschidere nouă nu trebuie să inventeze o partidă de sacrificiu de damă
pentru a copia un fixture istoric. Benchmarkul final cere exact un capăt separat de
sacrificiu corect când acel caz a fost efectiv colectat și zero altfel; toate capetele
publice, analizele native, amprentele surselor, fazele brute și pragurile de calitate
rămân obligatorii. Păstrează sacrificiul corect prin martorii de componentă și
controlul colecției originale când rejoci acea colecție.

Admiterea verifică și ofertele adverse liniștite de piese din interiorul PV-ului.
Dovada completă înregistrează domeniul real al capturilor și taxează fiecare vizită,
inclusiv capturile pe casete diferite. Păstrează verdicte distincte pentru indiciul
public, comparațiile diagnostice și analiza finală. Auditează fiecare semimutare
ipotetică oferită, în ambele culori, nu doar prima mutare sau ultima piesă expusă.
O piesă finală sigură nu dovedește corectitudinea alegerilor adverse anterioare.

Nu admite un domeniu nou doar dintr-un număr verde de componente. Păstrează primele
pierderi de transfer ca negative de componentă, fixează runtime-ul calificat după
poarta de transfer, apoi colectează partida observată următoare. Partidele candidaților
respinși păstrează producătorul original și statutul diagnostic; o rejucare sau o
corecție de adnotare nu le transformă în joc observat al sursei curente. Derivă
etichetele deschiderilor din ID-urile cazurilor reale; adaugă corecție legată de sursă
dacă proza fixată era greșită, păstrând amprentele jurnalului, PGN-ului și oracolului.

## Admiterea rădăcinii și a liniei complete

Prima mutare reparată are verdict separat de continuarea evaluată.
`CoachingLineEvidence` leagă corpul nativ exact de identitatea de încredere a oracolului,
toate cererile libere/forțate H17/H19 planificate, UCI-ul brut și fiecare prefix
ipotetic efectiv. Respinge fazele lipsă, duplicate, străine sau nefolosite înaintea
calculului de calitate. Un corp nativ incomplet nu se califică nici cu amprentă nouă.

`ScotchPromotionEvidenceTest` păstrează un experiment H7 respins: Re8+ trece pragul
independent al rădăcinii, dar două alegeri negre ulterioare eșuează și o apărare albă
are verdict diferit între adâncimi. Admiterea liniei cere rădăcină calificată și
absența pierderilor mari stabile sau a disputelor între adâncimi. Reamprentarea unui
rezumat editat nu verifică UCI-ul brut. Inventarul scurtat nu poate ascunde ultima mutare.

Păstrează benchmarkurile runtime originale executabile, inclusiv clasificarea prin
`GameReview.analyzeLine` efectiv pe o mutare. Validatorul de dovezi verde demonstrează
respingerea experimentului slab de către poartă; nu califică motorul.
