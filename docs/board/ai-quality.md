# Evaluarea AI în Chess Lab

## Pe scurt

- **Politica actuală.** Adversarul, indiciul și bara de evaluare au bugete separate. Hard pornește cu 100.000 de noduri suplimentare și poate continua până la 200.000 în modul adaptiv. Indiciul are un singur plafon de 200.000 de noduri, care include orizonturile inițiale, frontiera tactică și până la 150.000 de noduri pentru compararea alternativelor. [Configurația căutării](../../chess-core/src/main/resources/match-ai.yaml)
- **Acceptanță.** `MatchAiGovernanceTest` și `HardAdaptivePolicyTest` verifică politicile, iar `ChessBenchmarkSuite` măsoară deciziile față de un oracol fix. Comparațiile istorice de mai jos aparțin versiunii măsurate.
- **Elo afișat.** [Contractul analizei partidei](game-review.md) definește categoriile, precizia și estimările afișate. Aplicația citește reperele de rating din configurația ediției. [Repere de rating](../../chess-core/src/main/resources/game-review.yaml)

<details>
<summary>Politici și limite ale căutării curente</summary>

Nivelul adversarului și indiciul au adâncimi și bugete configurate separat. O căutare păstrează ultima adâncime evaluată complet. Căutarea Hard pentru mutarea curentă pornește cu 100.000 de noduri suplimentare și poate continua până la 200.000 prin politica adaptivă de mai jos. Acceptă rezultatul de la ultimul orizont numai dacă păstrează aceeași mutare lider ca adâncimea precedentă. În caz de schimbare a liderului, rămâne la ultima căutare completă și stabilă, exceptând [confirmarea limitată a promovării prin schimb închis](#confirmarea-promovării-prin-schimb-închis). Indiciul încearcă până la șapte semimutări, cu o frontieră tactică plafonată la două semimutări pentru capturi, promovări sau ieșirea din șah. Comparația dintre o captură de pion și una de piesă poate urmări suplimentar cel mult două semimutări de recaptură când luarea piesei permite replica unui pion cu șah. Vizitele folosesc bugetul existent al comparației; orizontul nominal complet rămâne neschimbat. Adâncimea completă și nodurile efective descriu căutarea realizată. În partide și lecții, evaluarea adaugă la material dezvoltarea pieselor, centrul, poziția regelui și perechea de nebuni. Ponderea termenilor se schimbă odată cu materialul rămas, astfel încât un rege activ să fie util în final. Căutarea verifică întâi capturile, promovările și mutările care activează piesele. Testele de tactică păstrează obiectivul lor explicit de material sau mat. [Politica de căutare](../../chess-core/src/main/resources/match-ai.yaml) · [Politica adaptivă](../../chess-core/src/main/resources/hard-adaptive.yaml)

În finalul de turnuri, Hard folosește istoricul integral pentru a evalua repetițiile în arbore, inclusiv la rădăcină. Poate extinde căutarea cu o semimutare peste adâncimea profilului, până la adâncimea 7, în alocația adaptivă configurată. Extensia și fereastra de 50 cp pentru candidații rădăcină sunt fixate în `rook-endgame` din YAML. Guvernanța respinge extensii sau ferestre în afara limitelor validate.

În jocurile Hard din afara repertoriului și a finalului special de turnuri, mutările care dau șah primesc prioritate numai în ordinea de căutare. Bonusul de 10.000 este configurabil și plafonat în `hard-ordering`. El nu se adună la evaluarea poziției și nu modifică alocația de noduri. Indiciul reutilizează această ordonare Hard și mecanismul transpozițiilor exacte în propria alocație. Basic, Advanced și evaluarea barei păstrează ordonarea anterioară.

Calea de joc Hard reutilizează și liniile exacte pentru poziții transpozate. Intrările incomplete sau limitele alpha-beta nu sunt refolosite. Capacitatea este derivată și plafonată din buget în `hard-transpositions`. [Evaluarea cache-ului exact](#cache-exact-pentru-hard)

Basic caută două semimutări în pozițiile obișnuite și trei în finalurile elementare cu turnul ori dama contra regelui. Ambele căi folosesc zero noduri suplimentare. Adâncimea obișnuită se configurează prin `standard-max-plies` în YAML. Hard încheie căutarea la primul orizont complet care dovedește un mat câștigător. [Calibrarea Basic](#calibrarea-basic-cu-adâncime-selectivă) · [Dovezi de mat](#rezolvarea-quest-urilor-cu-obiectiv-declarat)

Indiciul folosește nucleul de căutare Hard independent de nivelul adversarului. Fiecare orizont complet evaluează toate mutările legale de la rădăcină prin aceeași frontieră tactică. Până la 150.000 de noduri din alocația unică de 200.000 verifică liderul și un candidat de la alt orizont complet. Ambele faze împart cache-ul exact și ordonarea. Replicile verificate cu o singură rădăcină păstrează o alocație inițială mai mare. O iterație mai adâncă întreruptă păstrează ultimul rezultat complet. Un lider schimbat la orizont impar cere confirmare la un orizont cu ture egale înainte de a înlocui rezultatul susținut. Lista arată cel mult trei prime mutări distincte, la maximum 25 cp sub lider. La mat rămân doar variantele cu același rezultat și aceeași distanță. Rejucarea folosește continuarea evaluată, fără o căutare nouă sau o replică adăugată fără evaluare. Estimarea statică explică tabla afișată și nu reordonează rezultatul căutării. [Citirea diferenței](README.md)

La Hard, într-o poziție cu material egal, clasamentul disponibil după patru semimutări folosește și scorul aceleiași mutări la trei. Contribuția liniei mai scurte este limitată. Pentru o mutare liniștită, creditul scade când linia provizorie sugerează un câștig de peste un pion. Maturile și remizele evaluate păstrează scorul terminal. Dacă adâncimea șase sau confirmarea liderului schimbat la acea adâncime nu se încheie, motorul păstrează ultima căutare completă. Căutările explicite și comparațiile pentru îndrumare folosesc bugetele declarate.

Basic, Advanced și Hard împart catalogul documentat de deschideri. Replica verificată pentru un profil are prioritate, urmată de prima continuare documentată a poziției curente exacte. Drepturile de rocadă și en passant rămân parte din identitate. Alt răspuns uman selectează ramura corespunzătoare, iar ieșirea reală din teorie readuce căutarea obișnuită a profilului. Selecția este deterministă. Excluderile rezervate analizei își păstrează domeniul. Teoria de rezervă a jocului nu intră în generarea indiciilor; replicile verificate pentru îndrumare și continuările căutate își păstrează domeniul. Încărcarea verifică legalitatea, pozițiile duplicate și maturile într-o mutare. [Repertoriu YAML](../../chess-core/src/main/resources/ai-repertoire.yaml)

Într-un ciclu, Hard folosește istoricul înainte de a elimina alternativele. Dacă poziția curentă a mai apărut de două ori, o mutare care ar produce a treia apariție sau mai mult primește 0 cp la rădăcina căutării. Motorul compară alternativele în același buget, la ultima adâncime încheiată. Între liniile încă favorabile, Hard poate alege o poziție vizitată de mai puține ori dacă păstrează cel puțin 100 cp avantaj și cedează cel mult 50 cp față de prima linie.

A treia apariție este un semnal pentru alegerea mutării. Partida se încheie automat la a cincea apariție, iar Hard poate alege acea remiză dacă nu vede un avantaj în ciclu. [Politica YAML](../../chess-core/src/main/resources/match-ai.yaml) · [Partida de regresie](../../atf-tests/src/test/resources/repetition-regression.yaml) · [Regulile FIDE, art. 9.2 și 9.6](https://handbook.fide.com/chapter/E012023)

Bara și prima linie de text folosesc o estimare separată pentru poziția curentă. Motorul evaluează două semimutări, câte una pentru fiecare parte, și aplică o parte din diferența obținută la trei. Ambele căutări verifică schimburile la orizont. Corecția este limitată la 25 cp, în bugetul propriu al afișajului. Apăsarea becului păstrează estimarea afișată și parcurge variantele în ordinea căutării de indicii.

</details>

## Buget adaptiv în prim-plan

`hard-adaptive.yaml` configurează o alocație de bază de 100.000 și un maxim de
200.000 de noduri suplimentare pentru deciziile normale de joc Hard. La pragul
de bază, aceeași căutare poate folosi rezerva dacă nu a încheiat cinci semimutări,
regele curent este în șah ori expus unei piese grele pe o coloană centrală, sau
reutilizarea exactă a cache-ului este sub 1% după cel puțin 4.096 de observații.
Un schimb de pion încă nerezolvat, identificat prin verificarea candidatului
existent, este și el un motiv tactic pentru continuarea verificării. Valorile
implicite curente țintesc șase semimutări. Ambele limite, adâncimea, numărul
de observații și pragul cache-ului sunt valori configurate cu tip verificat.

Continuarea păstrează stiva recursivă, ordinea mutărilor și tabelul de transpoziții
ale aceleiași decizii. Căutarea nu reia rădăcina după solicitarea rezervei.
O nouă decizie de joc pornește cu propriile observații.
Oprirea păstrează ultimul rezultat complet. Verificarea unui schimb cunoscut
poate consuma în continuare nodurile rezervate. Când se încheie a cincea
semimutare, ambele alternative ale schimbului sunt comparate la aceeași
adâncime, cu extensia tactică. Verificarea folosește același controller adaptiv
și costul cumulat al nodurilor. Solicită rezerva numai când o vizită efectivă
depășește pragul de bază. Calea veche de epuizare păstrează alocația sa rezervată.

Dezactivarea politicii adaptive restabilește profilul declarat în `match-ai.yaml`.
Acesta este folosit și de căile de comparație explicită. Indiciul, bara de evaluare,
Basic și Advanced își păstrează bugetele și punctele de intrare. Dovezile Elo
trebuie să identifice sursa exactă și politica activă. Liga experimentală de
ordonare învățată are propria comparație cu sursa identificată.

[Verificări de componentă și regresie](../testing/README.md#buget-adaptiv-hard)

În comparația pe sursa main, plafonul fix de 100.000 a încheiat cel puțin cinci
semimutări în 39/44 de poziții. Plafonul fix de 200.000 și cel adaptiv le-au
încheiat în 43/44. Adaptivul a păstrat toate cele 44 de alegeri de la 200.000 fix,
cu o medie de 124.062 de noduri suplimentare pe decizie, față de 187.184 pentru
200.000 fix. Rezerva a fost folosită în 12 poziții. A recuperat `…Ng4` după
ieșirea timpurie a damei și a păstrat recaptura cu dama în controlul apărării
regelui. Toate cele trei moduri au ales în continuare `…Bh3` în `fresh-030`.
Acest eșantion măsoară transferul deciziei și costul pe acea sursă. Estimarea
forței de joc a profilului adaptiv cere propria ligă de partide pereche.

## Verificarea schimburilor din indiciu

După `1.e4`, indiciul vechi se oprea la `…Nc6 Bb5 …Nd4 Bc4 …Nxc2+`, înainte de `Qxc2`. Previzualizarea adăuga recaptura după ordonare, iar selectorul public păstra liderul pierzător. Varianta din poziția inițială avea același defect cu `Bxd7+`. Ambele sunt regresii ale căutării, cu mutări legale.

Indiciul taxează acum orizonturile inițiale, frontiera schimburilor și comparația de la rădăcină dintr-o singură alocație de 200.000 de noduri. Captura și recaptura folosesc aceeași frontieră la ordonarea alternativelor. Tăierile alpha-beta sunt limite și nu pot intra în cache-ul rezultatelor exacte. Rejucarea păstrează linia evaluată și checkpoint-ul sursă. O estimare statică a rejucării nu poate înlocui tacit acea ordonare.

Martorii includ sacrificiul damei `Qxc8+`, urmat de mat în trei, și sacrificiul calului `Ng6+ hxg6 Rh4#`. Un bilanț material negativ singur nu poate respinge sacrificiul. Stockfish 19 verifică rădăcinile critice la adâncimile 17 și 19, cu replicile adversarului libere. Recepțiile motorului rece, rejucarea publică și rapoartele Extent se păstrează în `C:\work\sah\studii\2026-11-17-j7-hint-repair`. Corecția nu stabilește un rating Elo nou și nu garantează joc optim în orice poziție.

[Contractele indiciului](../../atf-tests/src/test/java/ro/atf/chess/MatchHintTest.java) · [Rejucarea fără modificări](../../atf-tests/src/test/java/ro/atf/chess/HintPreviewTest.java) · [Profilurile deschiderilor](../../atf-tests/src/test/java/ro/atf/chess/OpeningPlayTest.java)

## Comparația selectivă a indiciului și auditul mutărilor

Indiciul păstrează liderul și un candidat observat la alt orizont complet. Ambele faze reutilizează aceleași intrări exacte și aceeași ordonare în bugetul unic al deciziei. O tăiere rămâne limită. O valoare din fereastra completă, aflată în interiorul ferestrei originale, poate fi exactă chiar dacă ramurile secundare eliminate au produs limite. Martorii cu cache încălzit, rece, dezactivat și căutare întreruptă verifică distincția.

O schimbare la H6 cere dovezi anterioare H4. H5 folosește H4, iar H7 folosește H6. Tabelul orizontului par curent nu se poate confirma singur. O schimbare nesusținută păstrează adâncimea inițială efectivă și nu publică o comparație acceptată. Adâncimile căutării și comparației rămân distincte de numărul semimutărilor rejucate.

| Poziție | Comparație J7 la orizont comun | Rezultat public la 200k | Scor așteptat Stockfish 19 H19 |
|---|---|---|---|
| `e4 c5 Nf3 d6 d4` | H6, `…cxd4 −48` față de `…Nd7 −82 cp` | Păstrează `…cxd4`; exclude `…Nd7` dintre sugestiile echivalente | 477‰ față de 268‰ |
| `e4 c6 d4` | H5, `…d5 +1` față de `…d6 −33 cp` | Înlocuiește `…d6` cu `…d5` | 468‰ față de 383,5‰ |

Numerele Stockfish compară rădăcinile testate cu replicile adverse libere. Nu sunt centipioni J7 sau Elo. Pe martorii pereche la 200k, 13 dintre cei 14 lideri existenți au rămas identici, iar liderul Caro–Kann principal s-a îmbunătățit. Primul candidat a produs regresii în Catalană și în martorul anterior capturii `Nxc2` și a fost respins. Corectarea autoconfirmării a păstrat ambii lideri anteriori. Lotul inițial sigilat de patru poziții a păstrat liderii. După inspectare devine dovadă de regresie, fără a stabili o creștere generală de forță. Un martor adăugat separat după `e4 e5` schimbă `Nf3` cu `Nc3`: Stockfish H19 dă +27 față de +4 cp, respectiv 523‰ față de 503‰. Alternativa teoretică echilibrată pierde măsurat 20‰ și este raportată separat de cei 14 martori pereche.

[Protocolul și colecția de audit](../testing/hint-audit.md) · [Contractele frontierei](../../atf-tests/src/test/java/ro/atf/chess/HintFrontierTest.java)

## Prima colecție de audit mutare cu mutare

`opening-hints-v1` păstrează 24 de decizii pe trasee și nouă controale de buget din poziții pereche din [sursa auditată](https://github.com/crististefanita/chess/commit/a7e71d9412323603847c20da220c8554903ae9be). Rejucarea independentă validează 539 de semimutări PV, 233 de replici adverse, contoare, drepturi, material, PGN SAN și amprente. Douăsprezece cereri de rang indisponibil revin explicit la prima sugestie oferită. Datele sunt în `C:\work\sah\colectii\hint-hard\2026-11-17-v1`.

Auditul H17/H19 separă prima recomandare de rangul jucat intenționat. Găsește cinci decizii ale liderului cu pierdere de scor așteptat de cel puțin 150‰ la H19. Un `Bg5` selectat intenționat pierde 484,5‰, iar liderul acelei poziții pierde 9‰. Niciun număr nu stabilește un rating Elo. Prima colecție rămâne imuabilă când evaluăm algoritmi ulteriori.

| Martor | API efectiv și alocație | Rădăcină aleasă, SF H19 | Alternativă verificată, SF H19 |
|---|---|---|---|
| Decizia italiană 5 | Indiciu, 100k pe traseu; aceeași rădăcină la 200k | `…Bxf2+`, −311 cp / 0‰ | `…Qg6`, −49 cp / 444‰ |
| Aceeași poziție completă | Hard efectiv, 100k noduri suplimentare | `…Na5`, −120 cp / 155,5‰ | `…Qg6`, −49 cp / 444‰ |
| Decizia italiană 3 | Indiciu, 200k | `…Qd6`, −379 cp / 0‰ | `…Qd8`, −197 cp / 10‰ |

Pierderile italiene se reproduc și cu motorul precedent. După captura nebunului, `Kxf2 …Qc5+ d4` este o apărare reală și contează furculița ulterioară de cal. Creditul superficial și candidații indiciului sunt distincte de decizia Hard efectivă. În grila Caro–Kann cu albul după `…d5`, adâncimile selectate sunt H5/H5/H4 la 50k/100k/200k. O alocație mai mare nu implică deci un rezultat selectat mai adânc sau o mutare mai puternică. Acestea sunt cazuri explicite pentru continuare, fără a reprezenta o poartă de forță globală trecută.

Primele trei probe italiene au fost respinse înaintea corecției de credit documentate mai jos; aceste rezultate istorice aparțin sursei `a7e71d9`. Verificarea independentă a Hard-ului efectiv păstrează mutarea și adâncimea pe toate cele 24 de istorice v1 față de motorul precedent. Cinci costuri în noduri diferă, inclusiv două utilizări ale rezervei de la 100k la 200k. Eșantionul nu dovedește cost identic sau câștig Elo.

<details>
<summary>Probe italiene respinse și contractul păstrat</summary>

| Probă | Rezultat local | Motivul respingerii |
|---|---|---|
| V1 · filtrarea creditului brut și eliminarea rezervei H2 | S5 ajunge la `…Qg6` | S7 sicilian schimbă `N1c3` în `Bg5`, SF H19 de la 499,5‰ la 24‰; martorul italian S1 la 200k schimbă `…Nge7` în `…Qf6`, de la 243,5‰ la 26,5‰ |
| V2 · restabilește rezerva H3 și cea de teorie | Păstrează S7 sicilian | Produce în continuare regresie în martorul italian S1 |
| V3 · păstrează domeniul brut eligibil înaintea eliminării după credit | Păstrează alternativa relevantă | Nu încheie H4 în cele 50k ale căutării inițiale; S5 revine la H3 și `…Bxf2+`; poarta de componente 78/79 eșuează |

Probele respinse au arătat că filtrarea largă poate epuiza căutarea inițială sau regresa alt lider. Corecția de mai jos separă oferta materială infirmată de schimbul echilibrat și păstrează candidații diagnostici. Hard efectiv `…Na5` rămâne un P1 separat. Vechile recepții și martorii de sacrificiu rămân dovezi; nu devin interdicții FEN sau etichete automate. [Martori înghețați](../../atf-tests/src/test/java/ro/atf/chess/HintTransferRegressionTest.java)

</details>

[Protocolul auditului](../testing/hint-audit.md) · [Feedback algoritmic deschis](../feedback/README.md)


## Corecția creditului italian și calibrarea Hard nativ

Creditul orizontului anterior nu poate susține o ofertă materială imediată infirmată de dovezile brute H4. Căutarea păstrează valorile exacte și limitele observate înaintea filtrării după credit; nu face o căutare suplimentară. Numai liderul susținut de credit care oferă material net și iese din fereastra brută este demovat. Schimbul echilibrat `…Bxb5` și candidații istorici încă incerți rămân disponibili. Sugestiile publice sunt distincte de candidații comparației. Scorul unei reveniri exclude creditul de ordonare; preferința poate folosi dovezi de la alte orizonturi, deci nu promite sortare numerică după acele scoruri brute.

În S5 italian, indiciul la 200k recomandă numai `…Qg6`. Stockfish 19 rece, cu replici libere, confirmă la H19 `…Bxf2+` −311 cp / 0‰, `…Qg6` −49 cp / 444‰ și `…Nge7` −169 cp / 30,5‰. H17 păstrează aceeași ordine. În contextul Traxler distinct, același `…Bxf2+` rămâne liderul liber Stockfish la H17/H19 și este păstrat de J7; sacrificiile fără mat nu sunt interzise. Poziția italiană cu culorile inversate păstrează apărarea `Qg3`.

Dintre 44 de cereri de indiciu pe poziții pereche și bugete identice, numai liderul italian S5 se schimbă. Liderii Sicilianei și Caro–Kann rămân identici; patru istorice noi își păstrează liderii și alternativele. Toate cele 24 de decizii Hard native comparate păstrează mutarea, adâncimea și nodurile. Aceste mostre nu califică toate alternativele oferite: `Bg5` sicilian rămâne deschis. [Regresii de credit](../../atf-tests/src/test/java/ro/atf/chess/HintCreditRegressionTest.java)

Calibrarea din 18 noiembrie folosește sursa `772251450f5d8cf337f8f31c18734f5601043e8d`, 50 de starturi pereche și 100 de partide contra Stockfish 19 `UCI_Elo 1500`, un fir și 6.000 de noduri solicitate. Fiecare dintre cele 12.522 de mutări jucate are analiza efectivă Hard: 1.600 în prefixe, 5.462 la mutarea Hard și 5.460 înaintea replicii Stockfish. Rejucarea independentă PGN/JSONL și publicatorul Extent verifică întregul lot.

| Profil | V / R / Î / neterminate | Elo local central | Interval conservator 95% | Neterminate toate pierdute / câștigate |
|---|---|---|---|---|
| Hard nativ adaptiv 100k–200k | 39 / 3 / 37 / 21 | 1.507 | 1.357–1.656 | 1.433 / 1.581 |

Bootstrap-ul păstrează deschiderea ca unitate, cu 20.000 de eșantioane. Plafonul este 220 de semimutări; neterminatele rămân distincte de remize. Diferența față de ancora configurată este +7 Elo, cu interval −143…+156. Nu este rating uman și nu demonstrează creștere față de Hard anterior: corecția privește indiciul, iar algoritmul deciziei native nu s-a schimbat. Ancorele aplicației rămân guvernate de dovezile lor.

Prima rulare s-a oprit după 49 de partide și o partidă parțială din cauza validatorului unei repetiții legale de turn. După corecția validatorului, întregul lot a fost reluat; rularea întreruptă este păstrată separat și exclusă din estimare. Toate cele 208 clase de producție au fost identice între cele două lansări. Ulterior, trei colectări din calea indiciului au fost exprimate compatibil API 26, fără schimbarea deciziei Hard. Dovezile sunt în `C:\work\sah\studii\2026-11-17-j7-italian-calibration`; raportul calificat este `league/qualified/qualified-extent.html`. [Protocol nativ și comandă](../testing/hard-audit.md)


## Studiul frontierei native și auditul puzzle-urilor

La 18 noiembrie, sursa `6d14986` păstrează `…Na5` la H5 și 100k. Linia nativă `…Na5 Nb5 …Qc6 Nxe5 …Nxc4` se oprește înainte de `Nxc6`, care câștigă dama. Stockfish H19 confirmă +867 cp / 1.000‰ pentru `Nxc6`, dar −545 cp / 0‰ pentru șahul tentant `Nxc7+`. În poziția imediat după `…Na5`, apărarea liniștită `Be2` are 912‰ pentru alb, față de 478,5‰ pentru `Nb5`. Prima captură sau primul șah nu certifică replica optimă.

Comparația separată a rădăcinilor la H6 dă nativ `…Na5 −154 cp` și `…Qg6 −170 cp`, ambele exacte. Fiecare probă are propriul plafon de 200k și nu include iterațiile anterioare; nu reprezintă o decizie completă la acel cost. După continuările lor, Stockfish H19 găsește recapturile favorabile `…Qxe5` și `…Nxe5`, la +306 și +493 cp pentru negru. Perechea păstrează deci o eroare de scor și un schimb neîncheiat, independent de simpla acoperire a rădăcinilor.

| Probă experimentală | Italiană | Verificare | Verdict |
|---|---|---|---|
| Frontieră tactică uniformă de două semimutări | `…Qg6`, H4 complet și 200k | 83/87 componente; pe 32 de familii, trei pierderi H19 de cel puțin 150‰ | Respinsă |
| Aceeași frontieră cu protecția orizontului par | Același rezultat focal H4 | Ablație locală, fără calificare de transfer separată | Experimentală |
| Frontieră doar la capturarea legală a damei | Reintroduce `…Bxf2+`, H5 și 200k | Oferta rămâne infirmată de oracol | Respinsă la cazul focal |

Sursa originală separă evaluarea de repaus de evadările din șah și filtrează capturile prin evaluarea statică a schimbului. Sunt ipoteze distincte de verificat, fără copierea pragurilor numerice. [Căutarea tactică Stockfish 19](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp#L1484-L1687)

Cele 32 de familii provin din partide arhivate, nu dintr-un lot global nou. Selecția pozițiilor a fost fixată înainte de compararea candidatului și evaluarea oracolului. La H19, cele 15 alegeri schimbate au șase câștiguri, șase pierderi și trei egalități WDL; diferența cumulată este −513‰, adică −16,03‰ pe toate cele 32 de poziții. Basic și Advanced păstrează mutarea, adâncimea și nodurile în toate cele 37 de cereri, inclusiv martorii focali. Reperul trece 87/87 componente. Niciun candidat nu intră în motorul live sau în duel; nu există ΔElo candidat măsurat. Reperul Hard de 1.507 Elo local rămâne cel din 100 de partide, cu IC 95% 1.357–1.656.

Benchmarkul puzzle-urilor folosea eronat plafonul de rezervă explicită 44k pentru toate deciziile adaptive. Auditul nou păstrează 44k pentru repertoriul exact și plafonul YAML adaptiv pentru celelalte poziții. Respinge depășirea fiecărui plafon, raportul gol, contabilizarea străină și materialul substituit. Păstrează pragurile înghețate și granița `training-048`. Fiecare poziție apelează o singură dată analiza adversarului Hard, fără a-i furniza cheia sau obiectivul puzzle-ului; Extent păstrează alegerea, adâncimea, nodurile și PV-ul efectiv.

| Capitol | Prime decizii acceptate | Prag păstrat |
|---|---|---|
| Mat în 1 | 500/500 | 500 |
| Mat în 2 | 300/300 | 300 |
| Mat în 3 | 198/200 | 190 |
| Tactici | 93/100 | 86 |
| Antrenament | 94/114; 40/48 în lotul înghețat | 39/48 |
| Intermezzo | 24/24 | 24 |

Acestea sunt prime decizii acceptate, nu conversii native complete ale tuturor problemelor. Nu măsoară Elo și nu antrenează automat motorul. Dovezile și două lecții YAML cu replici și contraexemple sunt în `C:\work\sah\studii\2026-11-18-j7-native-italian`. [Auditul pozițiilor și contractele negative](../testing/hard-audit.md#auditul-puzzle-urilor-fără-obiectiv-transmis-adversarului)

## Control la fiecare schimbare

1. Rulați suita JVM înainte de schimbare. Comparați din aceeași copie TMP, cu același executabil Stockfish și același YAML de evaluare. Păstrați raportul Extent din fiecare rulare.
2. Înregistrați poziția în care decizia a fost slabă, mutarea aleasă, replica omisă și evaluarea de referință. Folosiți aceste exemple pentru ajustarea căutării. Păstrați setul de control fix în timpul ajustării și adăugați separat poziții noi pentru verificare.
3. Comparați Basic, Advanced, Hard și prima recomandare pentru fiecare poziție. Urmăriți pierderea medie a scorului așteptat, numărul pierderilor mari și potrivirile exacte. Verificați adâncimea finalizată, nodurile suplimentare și latența. Comparați latențele pe același dispozitiv, în condiții similare.
4. La regresie, deschideți în Extent pozițiile cu pierderea cea mai mare și verificați dacă mutarea a fost depunctată din cauza orizontului, a unei egalități sau a bugetului epuizat. Mențineți pragurile în timpul experimentului și justificați separat orice revizie a setului sau a pragurilor.

```powershell
mvn test '-Dtest=ChessBenchmarkSuite' "-Dchess.stockfish=$stockfish" '-Dchess.extent=target/match-ai-benchmark.html'
```

- Setați `$stockfish` la executabilul Stockfish 19. Verificarea pornește câte o căutare la adâncimea fixată în YAML pentru poziția întreagă și pentru mutarea J7, cu un fir, 16 MiB de hash și memorie resetată între căutări. Scorul așteptat Stockfish este `W + D/2` în miimi. Diferența dintre căutări se plafonează la zero când cea restrânsă găsește o evaluare mai bună. Raportul arată și scorul UCI pentru fiecare mutare, ca să se vadă maturile sau diferențele care apar la un scor așteptat deja saturat. [Set și praguri](../../atf-tests/src/test/resources/match-ai-benchmark.yaml) · [Stockfish 19](https://github.com/official-stockfish/Stockfish/releases/tag/sf_19) · [Scorurile WDL Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Useful-data.html)

- Scorurile acestui set măsoară alegerea unei mutări din poziții fixe. Pentru o estimare Elo, folosiți perechi de partide pe aceleași deschideri, cu culori inversate, timp fix și interval de încredere. Păstrați rezultatele partidelor separat de acest raport pe poziții.

## Liga offline

| Profil | Adâncime maximă | Noduri suplimentare |
|---|---:|---:|
| Basic | 3 | 0 |
| Advanced | 4 | 10.000 |
| Hard în prim-plan | 6, cu lider stabil | 100.000 bază, până la 200.000 adaptiv |
| Indiciu | Până la 7, plus frontieră tactică de două semimutări și cel mult două recapturi în comparația selectivă | 200.000 în total, inclusiv până la 150.000 pentru compararea alternativelor |

- Guvernanța cere creșterea strictă a adâncimii și bugetului între profilurile adversarilor. Indiciul are cel puțin adâncimea Hard configurată și propria alocație plafonată. În finalurile cu turn sau damă și regi, toate nivelurile, indiciul și bara folosesc aceeași evaluare a distanței dintre regi și a spațiului regelui apărat. Testele verifică transformarea avantajului în mat cu ambele culori și conservarea perspectivei albului în afișaj.

- Protocolul ligii folosește aceleași deschideri cu ambele culori și aceleași
  bugete. O partidă neterminată contribuie cu un interval, iar Elo relativ se
  raportează împreună cu incertitudinea.

<details>
<summary>Repere istorice Hard6 și experimente respinse</summary>

### Reper Hard6 fără cache exact

Secțiunea păstrează rezultatele Hard6 înaintea cache-ului exact descris mai jos; valorile nu reprezintă comportamentul candidatului live actual.

Hard a păstrat plafonul de 44.000 de noduri, iar Basic și Advanced nu și-au schimbat profunzimea sau bugetul. O probă fără filtrul de stabilitate a coborât Intermezzo la 22/24. Varianta păstrată în candidatul live acceptă rezultatul de la adâncimea 6 numai dacă mutarea lider coincide cu cea de la adâncimea 5; altfel folosește rezultatul complet anterior.

Transferul Hard a trecut podelele înghețate: mat în 1 500/500, mat în 2 300/300, mat în 3 190/200, tactici 87/100, antrenament 40/48 și Intermezzo 24/24. Liga internă a măsurat Hard–Advanced la 20/1/0/3 și Elo pereche central +417 (interval 95% +172…∞), față de reperul anterior 19/1/0/4 și +374. Este o singură ligă internă, cu trei partide neterminate; diferența modestă nu constituie o nouă calibrare Elo.

Ultima calibrare externă Stockfish 19 înaintea probei Hard6 a estimat 1296/1406/1544 pentru Basic/Advanced/Hard, cu intervale empirice largi. Ancorele afișate în aplicație rămân însă cele verificate și guvernate în `game-review.yaml`: 1340/1461/1549, legate de observațiile cu sursa `b5c1ef3`. Nu am transferat calibrarea TMP în aplicație; ratingul Hard6 cere trei sesiuni externe pe același set de 12 deschideri și culori. Proba curentă a rămas offline. Rapoartele Extent și rejucările experimentelor sunt păstrate în TMP, nu sunt artifacte de release.

### A/B offline respinse

PVS aplicat pe toate iterațiile a coborât Mat în 3 la 188/200, sub podeaua 190, și a fost respins fără liga de joc. PVS numai la adâncimea finală a trecut toate podelele, dar liga a rămas 20/1/0/3 și +417, identic cu Hard6 fără PVS; complexitatea în plus nu a adus forță măsurată. LMR pentru mutări liniștite târzii a trecut transferul, dar atât pragul după mutarea a patra, cât și cel după a opta au coborât Hard–Advanced la 19/1/1/3 și +338. Ambele variante LMR sunt respinse. Niciuna nu a fost copiată în codul live; rapoartele sunt în TMP.

Extensia tactică la fiecare frunză a coborât Antrenamentul la 36/48, sub
pragul 39. Extensia numai din șah a coborât matul în trei la 185/200, iar
ordonarea killer la 169/200. Aceste variante au fost respinse înaintea ligii.

</details>

- Pentru fiecare dintre cele trei confruntări, liga pornește din 12 deschideri și inversează culorile. Motorul folosește alegerile din aplicație și păstrează istoricul pentru repetiție. Partidele încă active la 160 de semimutări sunt înregistrate ca neterminate. La calcul, fiecare astfel de partidă poate contribui între zero și un punct. Punctul central folosește mijlocul acestui interval. Intervalul empiric de 95% provine din 10.000 de reeșantionări ale perechilor întregi, cu ambele limite pentru partidele neterminate. Diferențele Elo sunt `400 log10(p / (1 − p))`, unde `p` este scorul confruntării. [Setul ligii](../../atf-tests/src/test/resources/ai-league.yaml) · [Metoda perechilor în Fishtest](https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html)

- Ancora externă folosește Stockfish 19, `UCI_Elo 1500`, un fir și cererea de 6.000 de noduri pe mutare, cu culorile inversate în aceleași 12 deschideri. Rezultatul este performanța față de această configurație. Stockfish își calibrează opțiunea Elo într-un protocol de timp diferit, iar selecția la putere limitată introduce variație între rulări. [Setul ancorei](../../atf-tests/src/test/resources/ai-anchor.yaml) · [Calibrarea opțiunii Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)

- Dintr-o copie TMP, folosiți căi noi pentru fiecare raport și fișier de rejucare. La rulări Maven în paralel, folosiți câte un depozit local în TMP per execuție, ca instalarea bibliotecilor ATF să nu scrie concurent aceleași fișiere.

```powershell
mvn test '-Dtest=ChessBenchmarkSuite' "-Dchess.league=$leagueTsv" "-Dchess.extent=$leagueHtml"
mvn test '-Dtest=AiAnchorBenchmarkTest' "-Dchess.stockfish=$stockfish" "-Dchess.anchor=$anchorTsv" "-Dchess.extent=$anchorHtml"
```

- Extent consemnează rezultatele pe perechi, iar TSV păstrează secvențele UCI integrale. Pentru stil, comparați capturile și șahurile pe mutare, rocadele pe loc de joc și adâncimea medie încheiată. Folosiți aceleași deschideri și același plafon la fiecare comparație.

### Rezultatele ligii interne

- Bilanțurile pereche, intervalele Elo și observațiile de stil din liga internă.

<details>
<summary>Perechi, condiții și incertitudine</summary>

| Confruntare | V / R / Î / N | Diferență Elo centrală înainte → după | Interval 95% după |
|---|---:|---:|---:|
| Advanced – Basic | 19 / 2 / 3 / 0 | +120 → +280 | +154 … +470 |
| Hard – Advanced | 10 / 11 / 2 / 1 | +232 → +120 | +14 … +211 |
| Hard – Basic | 19 / 3 / 1 / 1 | +470 → +338 | +120 … +669 |

V, R, Î și N înseamnă victorii, remize, înfrângeri și partide neterminate pentru primul profil. În această ligă, Basic a finalizat în medie adâncimea 3,00, Advanced 3,94 și Hard 4,43. Capturile au reprezentat 20,5%, 23,1% și 24,7% dintre mutările lor. Șahurile au reprezentat 5,9%, 11,9% și 15,1%. Cele 11 remize dintre Hard și Advanced au venit prin repetiție. În Alehină, cu Hard la alb, Stockfish a evaluat la `+8,57` poziția de la începutul ciclului, la semimutarea 36, într-o căutare fără istoricul partidei.

</details>

### Retestarea pentru 0.7.0

- Tag-ul Git păstrează redarea din 0.7.0 și rezultatele pereche.

<details>
<summary>Ținte și praguri de transfer independent</summary>

| Confruntare | V / R / Î / N | Diferență Elo centrală | Interval empiric 95% |
|---|---:|---:|---:|
| Advanced – Basic | 19 / 2 / 3 / 0 | +280 | +154 … +470 |
| Hard – Advanced | 14 / 7 / 2 / 1 | +191 | +104 … +307 |
| Hard – Basic | 22 / 0 / 1 / 1 | +470 | +191 … ∞ |

Hard a câștigat toate cele opt partide din Pirc și Benoni, din ambele perspective, contra Basic și Advanced. În partida Alehină Hard–Advanced, a jucat `Nc3` după semimutarea 44 și a ieșit din ciclul care ducea anterior la remiză la 52. Stockfish indică +6,71 pentru ieșire, cu istoricul partidei și pierdere WDL de 0‰. Partida a ajuns la plafonul de 160 de semimutări. Stockfish arată −5,19 pentru alb la finalul probei. În partida cu culorile inversate, Hard a câștigat la semimutarea 108. [Rejucarea istorică 0.7.0](https://github.com/crististefanita/atf_j7/blob/v0.7.0/release/0.7.0/reports/league.tsv) rămâne în tagul Git, nu în catalogul scurt de release-uri.

</details>

### Conversia avantajului în Alehină

- Controlul istoric compară ieșirea din repetiție și alegerea după rejucarea a 72 de semimutări.

<details>
<summary>Rezultate pe profil și capitol</summary>

Reluarea istorică oprește partida după 72 de semimutări. Hard juca `Qc5`, către a treia apariție a poziției. Stockfish 19 la adâncimea 13, cu toate mutările precedente, dă 0,00 pentru `Qc5`. Cu istoricul în căutare, Hard alege `Qxf6+`: +6,62 în căutarea restrânsă, față de +7,11 pentru aceeași mutare în căutarea liberă. Pierderea WDL este 0‰. Hard finalizează adâncimea 5 în 12.557 de noduri suplimentare, sub plafonul neschimbat de 44.000. [Poziția și mutările de control](../../atf-tests/src/test/resources/alekhine-conversion.yaml)

Din deschiderea inițială, Hard alb joacă `Nf4` după semimutarea 44 și câștigă la 153. Din poziția istorică de la 72, după schimbul damelor, Hard ajunge la plafon cu −0,29 pentru alb. Această probă păstrează apărarea Advanced și același plafon de 160 de semimutări.

| Liga de antrenament · 72 de partide | V / R / Î / N | Diferență Elo centrală | Interval empiric 95% |
|---|---:|---:|---:|
| Advanced – Basic | 19 / 2 / 3 / 0 | +280 | +154 … +470 |
| Hard – Advanced | 15 / 7 / 2 / 0 | +211 | +137 … +307 |
| Hard – Basic | 22 / 0 / 1 / 1 | +470 | +191 … ∞ |

Față de 0.7.0, 69 din 72 de partide au același rezultat. Hard transformă în victorii partida neterminată din Alehină și remiza din Gambitul regelui. Victoria Hard alb din Benoni modern devine remiză: la prima divergență, după 112 de semimutări, Stockfish evaluează noul `Kf4` la 0,00 și fostul `Kf6` la −1,75. Singura partidă rămasă la plafon este Petroff Hard–Basic, cu aceeași secvență ca înainte. Adâncimile medii finalizate sunt 3,00, 3,94 și 4,43 pentru Basic, Advanced și Hard.

#### Rejucarea finalului de turnuri

`RookEndgameHistoryTest` reconstruiește aceeași partidă completă până la semimutările 96, 108 și 112. Cazul de la 108 separă evaluarea numai după tablă de evaluarea cu istoricul păstrat: fără istoric, `a7a8` duce în linia de remiză; cu istoricul, Hard evită repetiția și joacă `h2h4`.

Pentru comparație independentă, trimiteți istoricul integral la Stockfish prin `position startpos moves ...` și evaluați mutările restrânse la adâncimea 13. Același fixture și aceeași comandă se folosesc pentru fiecare versiune.

```text
f5f6 g7f6 g1f1 a7a5 a1b1 b7b5 b1b5 e8d8 e2c3 d8c8 c3e2 a5a4
b5f5 c8c2 f2f4 c2a2 e2c1 a2d2 f1e1 d2g2 c1d3 f8g7 f5a5 h8e8
```

| Ply | Hard cu istoric | Stockfish 19, d13 | Pierdere așteptată | Noduri Hard |
|---:|---|---|---:|---:|
| 96 | `Kf1` | `Kf1`, +2,86 | 0‰ | 39.504 |
| 108 | `h2h4` | `h1g1`, +5,38; `h2h4`, +4,08 | 0‰ | 41.376 |
| 112 | `a7a8` | `d3e1`, +2,63; `a7a8`, +2,40 | 0‰ | 8.045 |

La 108, Stockfish evaluează `a7a8` la 500‰, iar `h1g1` la 1.000‰. Cheia de repetare include tabla, partea la mutare, drepturile de rocadă și doar un en passant legal. Cheia transpoziției păstrează și traseul căutării, astfel încât o valoare cu repetiție să nu fie reutilizată pe un traseu diferit. Toate cele trei decizii respectă limita Hard; profilurile Basic, Advanced și Indiciu nu folosesc această cale.

Verificarea acoperă regresia istorică și setul fix de 10 poziții Stockfish. Nu recalibrați Elo-ul Hard din aceste trei poziții: comparația de ligă completă rămâne necesară pentru o nouă estimare. [Următorul control](../feedback/README.md)

#### Transferul Hard către colecția de quest-uri

`HardQuestTransferBenchmarkTest` măsoară mutarea aleasă de adversarul Hard
pe catalogul încărcat la execuție. La mat se verifică prima cheie; la
exercițiile materiale, soluția catalogului. Pragul de Antrenament tactic
folosește lotul fix `training-001`–`training-048`; Extent afișează și totalul
capitolului activ. [Praguri](../../atf-tests/src/test/resources/hard-quest-transfer.yaml)

Rezultatele de mai jos sunt reperul istoric al experimentului de ordonare,
anterior catalogului și politicii actuale.

| Capitol | Rezolvat de Hard |
|---|---:|
| Mat în 1 | 500 / 500 |
| Mat în 2 | 300 / 300 |
| Mat în 3 | 190 / 200 |
| Tactici | 86 / 100 |
| Antrenament | 39 / 48 |
| Intermezzo | 24 / 24 |

Ordonarea șahurilor la rădăcina căutării Hard ridică transferul M3 de la 172 la 190 fără creșterea plafonului de noduri. Rămân 10 poziții M3, 14 tactice și 9 de antrenament de diagnosticat. Testul păstrează podelele de regresie în suita benchmark și publică nodurile, adâncimea și exemplele ratate în Extent.

#### Liga paralelă și reglajul ordonării șahurilor

Pe 12 noiembrie 2026, am comparat bonusurile 5.000 și 10.000 pe aceleași 72 de partide, cu cel mult trei workeri, aceleași deschideri, culori și bugete. Ambele rulări au trecut, iar fiecare partidă s-a rejucat din checkpoint.

| Bonus | Hard–Advanced V / R / Î / N | Elo central | Interval 95% | Hard–Basic V / R / Î / N | Noduri Hard / tură |
|---:|---:|---:|---:|---:|---:|
| 5.000 | 19 / 2 / 1 / 2 | +338 | +154 … ∞ | 22 / 1 / 1 / 0 | 29.891 |
| 10.000 | 19 / 2 / 2 / 1 | +307 | +154 … +669 | 22 / 1 / 1 / 0 | 29.518 |

Advanced–Basic a rămas identic la 18 / 3 / 3 / 0 și +255 Elo, cu intervalul +137…+417. Între 5.000 și 10.000 s-a schimbat rezultatul unei singure partide: Vienna, Advanced cu albele contra Hard cu negrele, s-a încheiat cu victorie albă în loc să rămână neterminată la plafon. Stockfish 19 la adâncimea 13 a evaluat prima divergență `e8g8`→`f5c2` la −915→−926 cp; pierderea WDL a rămas 0‰ pentru ambele alegeri. Așadar liga nu demonstrează o creștere Elo la 10.000; poziția era deja pierdută pentru Hard, iar intervalele sunt largi.

Am ales 10.000 pentru câștigul măsurat în colecția de quest-uri: Mat în 3 a urcat la 172/200 față de 170/200 la 5.000, celelalte capitole nu au scăzut, iar media ponderată a nodurilor pe toate cele 1.172 de poziții a coborât cu 16,6%, de la aproximativ 7.554 la 6.299. Setul fix de 10 poziții Stockfish a rămas 10/10, cu pierdere WDL 0‰ pentru Hard. Aceste rezultate nu justifică recalibrarea Elo-ului. Extensia tactică globală și varianta limitată la poziții în șah au fost respinse: prima a coborât Antrenamentul la 35/48 și a încălcat porțile Stockfish, iar a doua Matul în 3 la 166/200. Rapoartele Extent și TSV rămân în TMP.

Am verificat și bonusuri de 15.000 și 20.000 pe aceleași 1.172 de quest-uri și pe poarta Stockfish. Ambele au păstrat aceleași rezultate ca 10.000 în toate capitolele și 10/10 contra Stockfish; media ponderată a scăzut doar la aproximativ 6.255 și 6.254 de noduri, sub 1% față de 10.000. Nu există câștig de transfer care să justifice bonusul mai mare, deci plafonul și valoarea de 10.000 rămân. Măsurătorile de timp din rulările concurente nu sunt folosite pentru comparație.

Am testat separat ordonarea mutărilor la rădăcină: Hard caută mai întâi șahurile și păstrează ordinea evaluării în interiorul grupurilor de șah și non-șah. Basic, Advanced, Indiciul, repertoriul și finalurile speciale de turnuri nu intră pe această cale. Pe toate quest-urile, M3 a urcat la 190/200, Antrenamentul a rămas 39/48, iar celelalte capitole nu s-au schimbat; media ponderată a nodurilor a coborât de la 6.299 la aproximativ 5.703. Setul Stockfish a rămas 10/10.

Liga pereche de 72 de partide a păstrat scorul Hard–Basic la 22/1/1/0 și Advanced–Basic la 18/3/3/0. Hard–Advanced a trecut de la 19/2/2/1, +307 Elo, la 19/3/1/1, +338 Elo; o victorie a albului în Vienna, cu Hard negru, s-a transformat în remiză prin repetiție. Intervalele 95% se suprapun larg (+154…+669 față de +191…+669), deci rezultatul susține lipsa unei regresii în eșantion, nu o creștere Elo confirmată. Media Hard a coborât de la 29.518 la 29.305 noduri pe tură.

O sortare mai agresivă a tuturor mutărilor rădăcină după capturi și activitate a dat tot 190/200 la M3, dar Hard–Advanced a scăzut la 14/6/2/2 (+191 Elo central). Am respins-o; câștigul la puzzle-uri nu justifică o slăbire în joc. Estimările absolute Elo se actualizează doar după cele trei sesiuni Stockfish 19 descrise mai jos.

</details>

### Rezultatele ancorei externe

- Reperul extern măsoară performanța în configurația Stockfish documentată.

<details>
<summary>Condiții Stockfish, estimări și limite</summary>

Pe sursa ediției 0.9.0, trei sesiuni au folosit aceleași 12 poziții și culorile inversate, câte 24 de partide pe profil în fiecare sesiune. Stockfish 19 a rămas la `UCI_Elo 1500`, 6.000 de noduri cerute pe mutare și un fir. Plafonul a rămas la 160 de semimutări, iar profilurile J7 și-au păstrat bugetele.

| Profil | V / R / Î / N | Estimare în protocol | Interval empiric 95% | Estimări pe sesiuni |
|---|---:|---:|---:|---:|
| Basic | 12 / 2 / 43 / 15 | 1340 | 1139–1481 | 1380 / 1328 / 1309 |
| Advanced | 16 / 2 / 24 / 30 | 1461 | 1183–1684 | 1471 / 1411 / 1500 |
| Hard | 31 / 1 / 21 / 19 | 1549 | 1369–1739 | 1573 / 1573 / 1500 |

Din cele 216 partide, 64 au rămas active la plafon. Fiecare contribuie cu un interval de scor între zero și un punct. Calculul agregă cele trei perechi ale fiecărei deschideri înaintea celor 10.000 de reeșantionări. Deschiderea rămâne unitatea independentă. Hard conduce la estimarea centrală, iar intervalele celor trei profiluri se suprapun. `GameReviewGovernanceTest` recalculează toate cele trei repere folosite în aplicație. [Rezultatele perechilor și proveniența](../../atf-tests/src/test/resources/ai-anchor-observations.yaml)

În cele trei sesiuni istorice, Advanced a ajuns la plafon în cinci dintre cele șase partide Benoni modern. Stockfish 19 la adâncimea 13 a estimat 0‰ pentru Advanced la pozițiile finale ale celor cinci, inclusiv două poziții cu mat forțat. În calculul ancorei, toate cinci păstrează intervalul de partidă neterminată.

Reevaluarea istoricului complet din aceste sesiuni și din sesiunea de după corecția Alehină a găsit prima pierdere majoră a negrului la semimutarea 10. `…Ng4` a pierdut 335‰ în toate cele patru față de `…d6`. Cu albul, prima pierdere a apărut la semimutarea 11 după `…g6`, `…Na6` sau `…h6`, cu 379‰, 285‰ și 173‰. Repertoriul Advanced folosește `…d6`, `d6` după `…g6` și `e4` după `…Na6` sau `…h6`. Regulile se potrivesc pe poziția exactă și păstrează bugetul Advanced. [Replici și niveluri](../../chess-core/src/main/resources/ai-repertoire.yaml)

Pentru reproducere, folosiți o copie TMP și trei căi noi pentru TSV și Extent. Fiecare execuție validează perechile și reia toate mutările până la rezultatul înregistrat.

```powershell
foreach ($index in 1..3) {
    mvn -B -ntp test '-Dtest=AiAnchorBenchmarkTest' "-Dchess.stockfish=$stockfish" `
        "-Dchess.anchor=$scratch/anchor-$index.tsv" "-Dchess.extent=$scratch/anchor-$index.html"
    if ($LASTEXITCODE) { throw "Sesiunea $index a eșuat" }
}
```

Retestarea anterioară după corecția Alehină a acoperit 72 de partide.

| Profil | V / R / Î / N | Performanță centrală în protocol | Interval empiric 95% |
|---|---:|---:|---:|
| Basic | 2 / 0 / 17 / 5 | 1245 | −∞ … 1442 |
| Advanced | 11 / 1 / 8 / 4 | 1544 | 1346 … 1755 |
| Hard | 6 / 0 / 14 / 4 | 1380 | 1083 … 1558 |

Au rămas 13 partide la plafon. Advanced are cea mai mare estimare centrală contra configurației Stockfish, iar Hard conduce liga directă. Intervalele ancorei se suprapun; ordinea estimărilor nu stabilește ordinea absolută a profilurilor.

Prima măsurare, păstrată ca reper:

| Profil | V / R / Î / N | Performanță centrală în protocol | Interval empiric 95% |
|---|---:|---:|---:|
| Basic | 6 / 0 / 13 / 5 | 1396 | 1083 … 1589 |
| Advanced | 8 / 0 / 8 / 8 | 1500 | 1162 … 1732 |
| Hard | 5 / 0 / 11 / 8 | 1411 | 955 … 1654 |

În prima măsurare, 21 dintre cele 72 de partide au rămas active la plafon. Variația între rulări apare și la Basic și Advanced, ale căror reguli de căutare au rămas aceleași. Pentru o comparație între versiuni, păstrați bugetul și plafonul și repetați perechile în mai multe sesiuni.

#### Calibrare exploratorie a ordonării șahurilor · 12 noiembrie 2026

Am rulat trei sesiuni noi pe candidatul local cu prioritate stabilă pentru șahuri la rădăcina căutării Hard. Stockfish 19 a folosit `UCI_Elo 1500`, cererea de 6.000 de noduri pe mutare și un fir; fiecare profil a jucat 72 de partide pe aceleași 12 deschideri, cu ambele culori. Limita de 160 de semimutări și bugetele profilurilor au rămas neschimbate. Hash-ul executabilului Stockfish a corespuns ancorei.

| Profil | V / R / Î / N | Elo estimat | Interval pereche 95% |
|---|---:|---:|---:|
| Basic | 7 / 0 / 56 / 9 | 1212 | 1008–1369 |
| Advanced | 23 / 0 / 30 / 19 | 1466 | 1237–1654 |
| Hard | 34 / 0 / 23 / 15 | 1553 | 1411–1718 |

Intervalul reeșantionează cele 12 deschideri ca unități independente și tratează fiecare partidă neterminată ca scor între zero și un punct. Rulările au generat rapoarte Extent și TSV în TMP; fixture-ul, pragurile și bugetele nu s-au schimbat. Aceste valori descriu candidatul din copia locală, nu sunt promovate în `game-review.yaml` sau `ai-anchor-observations.yaml`: codul nu are încă un commit de proveniență pentru această calibrare. Intervalele sunt largi și se suprapun, deci rezultatul nu dovedește o creștere Elo și nici o separare statistică sigură între niveluri. Recalibrarea guvernată se face după fixarea sursei într-un commit și păstrarea hash-urilor rapoartelor.

</details>

## Analiza partidei

- Metoda de [evaluare a unei semimutări](game-review.md#evaluarea-unei-semimutări),
  [categoriile și precizia](game-review.md#categorii-și-precizie) și
  [estimarea Elo](game-review.md#cum-se-estimează-elo) sunt explicate la owner.
  Reperele AI afișate folosesc calibrarea externă guvernată din
  [`game-review.yaml`](../../chess-core/src/main/resources/game-review.yaml).

- Indexul recunoaște 3.865 de linii ECO. Caută ultima poziție denumită, inclusiv când partida ajunge la ea prin transpoziție, și marchează mutările de carte cât timp secvența urmează un prefix cunoscut. Numele ECO provin din catalogul englezesc CC0. Pentru regenerare, descărcați fișierele `a.tsv`–`e.tsv` de la revizia indicată și rulați scriptul de import cu chess.js 1.4.0. Guvernanța reia fiecare linie UCI și compară poziția rezultată cu EPD. [Revizia catalogului](https://github.com/lichess-org/chess-openings/tree/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39) · [Scriptul de import](../../scripts/import-review-openings.cjs)

- Am verificat și o estimare Elo bazată exclusiv pe pierderea de scor la mutare. În eșantionul de 800 de partide Blitz 3+0 din ianuarie 2024, pe benzile 1200–2400, mediile lotului de calibrare au fost 74‰, 86‰, 73‰, 70‰ și 75‰. Lotul separat a dat 103‰, 77‰, 43‰, 73‰ și 54‰. Variația între benzi a dus la folosirea rezultatului contra reperului AI pentru Elo și a pierderii de scor pentru precizie și clasificare. [Arhiva Lichess](https://database.lichess.org/#standard_games) · [Game Rating pe Chess.com](https://support.chess.com/en/articles/10773754-how-is-game-rating-calculated-in-game-review)

### Indiciul pe deschiderile ligii

- Setul fix de deschideri consemnează calitatea indiciilor înainte și după verificarea repertoriului.

<details>
<summary>Poziții și verificări pentru Analiza partidei</summary>

Pe aceleași 12 poziții de pornire, pierderea medie a indiciului față de Stockfish la adâncimea 13 a scăzut de la 127,3‰ la 73,0‰, apoi la 23,4‰ după verificarea repertoriului. Cazurile de cel puțin 150‰ au scăzut de la cinci la două, apoi la zero. Hard are același rezultat de 23,4‰ și zero pierderi mari. În Pirc, `…Bg7` înlocuiește `…Bd7` (296‰ pierdere), iar în Benoni, `…d6` înlocuiește `…Qa5` (299‰). Ambele replici coincid cu prima alegere Stockfish în aceste poziții. Testul guvernează pierderea cumulată, cazurile mari și plafonul de noduri. [Pozițiile ligii](../../atf-tests/src/test/resources/ai-league.yaml)

</details>

## Controlul deschiderilor

- Suita fixă verifică legalitatea mutărilor predate și compară Hard și indiciul cu Stockfish pe poziții din lecții.

<details>
<summary>Acoperire, praguri și scoruri pe deschideri</summary>

Suita Stockfish confirmă legalitatea tuturor mutărilor predate și verifică Hard și indiciul într-o poziție din fiecare lecție. Secvențele UCI sunt fixate în YAML. Guvernanța verifică acoperirea lecțiilor, ambele culori la mutare și corespondența fiecărei secvențe cu linia predată. Extent arată mutarea J7, mutarea Stockfish și ambele scoruri pentru fiecare caz. Porțile de calitate folosesc pierderea medie și numărul cazurilor cu o pierdere de cel puțin 150‰. [Poziții și praguri](../../atf-tests/src/test/resources/opening-ai-benchmark.yaml)

| Profil | Pierdere medie inițială | După prima extindere a lecțiilor | Cazuri ≥150‰ înainte / după |
|---|---:|---:|---:|
| Hard | 117,3‰ | 59,5‰ | 2 / 1 |
| Indiciu | 70,1‰ | 25,4‰ | 2 / 0 |

În Italiană, indiciul a trecut de la `Bd6` la `Nf6` și pierderea a coborât de la 178‰ la 0‰. În Scandinavă, a trecut de la `Nc6` la `Nf6`, cu pierdere de la 366‰ la 0‰.

</details>

### Calibrarea Hard și a barei

- Alegerea mutării și eroarea afișajului sunt măsurate separat, cu indicatori distincți.

<details>
<summary>Ratinguri externe și intervale de încredere</summary>

În Franceza de avans, după `5.Nf3`, Hard a trecut de la `…Qa5` la `…cxd4`. Pierderea a scăzut de la 277‰ la 21‰ față de `…Nge7` în Stockfish 19. Pe setul existent de deschideri, pierderea medie Hard a coborât de la 59,5‰ la 19,1‰ și nu au mai apărut pierderi de cel puțin 150‰. Cele șase poziții suplimentare din Franceză și Scoțiană au o pierdere medie de 39,7‰ și un maxim de 127‰.

După `1.e4 e5`, prima recomandare este `Nf3`, iar bara afișează `+0,19` față de `+1,40` anterior și `+0,43` în Stockfish 19. Pe șapte poziții suplimentare, prima calibrare a scăzut eroarea absolută medie de la 70,7 cp la 13,1 cp. Benchmarkul compară separat alegerea mutării în scor așteptat și estimarea barei în centipioni, din perspectiva albului. [Setul și porțile de calibrare](../../atf-tests/src/test/resources/opening-calibration.yaml) · [Scara Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Useful-data.html)

Când corecția la trei semimutări inversează semnul evaluării de la două, bara caută încă un orizont cu același număr de mutări pentru ambele culori. Dacă îl termină în bugetul existent, mediază cele două rezultate cu orizont par. După `1.d4 d5 2.c4`, bara trece de la `−0,19` la `+0,19`, față de `+0,22` în Stockfish 19. Eroarea scade de la 41 la 3 cp, iar media pe cele șapte controale ajunge la 7,7 cp. După `2…e6` eroarea observată este 33 cp, iar după `2…c6` este 8 cp. După acceptarea cu `2…dxc4`, bara afișează încă `−0,60` față de `+0,21`, un caz separat de compensare a pionului.

</details>

## Rezultatul de referință al primei extinderi

- Prima extindere furnizează valori istorice de control pentru cele patru profiluri.

<details>
<summary>Prima extindere pe lot și profil</summary>

| Profil | Aceeași mutare ca Stockfish, înainte / după | Pierdere medie, înainte / după | Pierderi mari, înainte / după |
|---|---:|---:|---:|
| Basic | 5 / 6 din 10 | 202,7‰ / 202,4‰ | 2 / 2 |
| Advanced | 7 / 7 din 10 | 2,7‰ / 14,1‰ | 0 / 0 |
| Hard | 7 / 8 din 10 | 2,7‰ / 11,7‰ | 0 / 0 |
| Indiciu | 7 / 8 din 10 | 20,2‰ / 2,4‰ | 1 / 0 |

După calibrarea Hard, pierderea medie a acestui profil pe suita mixtă a scăzut de la 11,7‰ la 0‰. Regresia tactică din Siciliană păstrează `Nxe5` la Hard și la indiciu. Acest caz folosește altă poziție decât lecția Najdorf. Pragurile ambelor suite au rămas aceleași.

</details>

## Cache exact pentru Hard

- Hard reutilizează liniile căutate complet, în limita capacității configurate.

<details>
<summary>Politica și măsurătorile cache-ului exact</summary>

Am activat reutilizarea liniilor căutate complet numai pe calea adversarului Hard. Intrarea este cheia poziției (inclusiv partea la mutare, rocada și captura en passant legală), semimutările rămase, distanța față de rădăcină, obiectivul evaluării și extensia tactică. Păstrarea distanței evită reutilizarea unui scor de mat cu altă valoare. Cache-ul acceptă numai rezultate exacte; o întrerupere alpha-beta sau un rezultat incomplet nu poate deveni oracol pentru o altă ramură. Căutările Basic, Advanced, indiciul, bara și analiza de partidă rămân pe calea existentă.

Capacitatea este configurabilă în `match-ai.yaml`: 500 de intrări la 1.000 de noduri Hard suplimentare, cu plafon de 50.000. La bugetul actual de 44.000 rezultă maximum 22.000 de intrări. Am comparat și plafonul de 44.000; acesta nu a îmbunătățit rata la quest-uri sau liga, așa că păstrăm varianta cu memorie mai mică.

Am rulat aceeași ligă pe 12 deschideri noi, ambele culori, 24 de partide pentru fiecare pereche de profiluri, în copii TMP cu cod Hard6 identic în afara cache-ului. Advanced–Basic a rămas 16/7/1/0 în ambele. Hard6 față de Hard6+cache a fost:

| Pereche | Hard6 de referință | Hard6 cu cache exact | Estimare internă centrală |
|---|---:|---:|---:|
| Hard–Basic | 20/3/1/0 | 22/1/1/0 | +374 → +470 |
| Hard–Advanced | 21/1/0/2 | 23/1/0/0 | +470 → +669 |

Aceste conversii sunt doar scoruri relative în liga J7 și nu reprezintă Elo extern. Pe setul înghețat existent, câștigul a fost mai mic: o remiză a devenit victorie în Hard–Advanced. Podelele de transfer au rămas neschimbate: M1 500/500, M2 300/300, M3 194/200, tactici 87/100, antrenament 39/48 și Intermezzo 24/24. Testul de guvernanță verifică factorul, plafonul, dezactivarea și respinge valorile din afara intervalului.

Rapoartele Extent și replay-urile TSV pentru candidatul, referință și capacitatea dublă au rămas în `C:\Users\Cristi\AppData\Local\Temp\j7-hard-tt-*`; nu sunt artefacte de release. [Terminologia oficială Stockfish despre transpoziții și căutare](https://official-stockfish.github.io/docs/stockfish-wiki/Terminology.html) explică rolul tabelului de transpoziții în reutilizarea pozițiilor. Documentația nu oferă o creștere Elo garantată pentru această implementare.

Reperele configurate în aplicație provin din sursa anterioară căutării Hard6. Verificarea plafonului Advanced și a progresului Hard folosește partide cu executabilul Stockfish 19 local, identificat prin amprenta din `ai-anchor-observations.yaml`. Estimările depind de adversar, buget și protocol. [Calibrarea UCI Elo în Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)

</details>

### Prototip local de limite alpha-beta · neintegrat

- Prototipul a ratat paritatea la mat în trei și a rămas în afara aplicației.

<details>
<summary>Partide pereche și probe de conversie</summary>

Datele de mai jos descriu un prototip rulat separat în TMP, nu codul candidatului. Aplicația păstrează exclusiv linii exacte; nu memorează și nu reutilizează limite alpha-beta. Prototipul nu a fost promovat deoarece reducerea nodurilor la Mat în doi a venit cu o poziție mai puțin rezolvată la Mat în trei. Nu prezentați rezultatele sale drept comportament livrat.

Primul prototip păstrează adâncimea și distanța de la rădăcină în cheie. O limită inferioară se poate reutiliza dacă atinge beta, iar o limită superioară dacă este cel mult alpha. La nivelul care întoarce variantele către rădăcină se reutilizează numai linii exacte. Proba diferențială a verificat 60 de cazuri cu ferestre înguste, reutilizare și revenire la fereastră completă. Scorul final a coincis cu cel calculat fără cache. [Implementarea de referință Stockfish 19](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp)

Cu 44.000 de noduri și 22.000 de intrări, prototipul a trecut pragurile de transfer. Mat în două a rămas la 300/300, cu o medie de 12.430 de noduri față de 13.377. Mat în trei a coborât de la 194 la 193/200. Tactica a rămas la 87/100, antrenamentul la 39/48 și Intermezzo la 24/24.

În primele cinci înfrângeri din prima serie de calibrare, prima pierdere majoră identificată de Stockfish la adâncimea 12 a apărut la semimutările 10, 13, 34, 17 și 16. Hard consumase toate cele 44.000 de noduri în fiecare poziție. Cazul cel mai sever din eșantion a fost Gambitul Regelui cu negrul la semimutarea 16. Stockfish a dat `d7d5` la +91 cp și mutarea Hard `d7d6` la −339 cp.

În duelul cu versiunea live, prototipul a obținut 11 victorii, 4 remize și 9 înfrângeri în 24 de partide, cu culori inversate și plafon de 160 de semimutări. Estimarea diferenței este +29 Elo, cu interval de 95% între −58 și +120. Ordonarea după mutarea memorată la aceeași adâncime a produs un fișier de rejucare identic. Preluarea ordinii din iterația anterioară a trecut pragurile de transfer, dar a crescut media la Mat în trei de la 13.705 la 14.573 de noduri față de prototipul cu limite.

</details>

### Calibrarea versiunii live

- Calibrarea externă consemnează sursa, distribuția rezultatelor și incertitudinea.

<details>
<summary>Verificări la același orizont al rădăcinii</summary>

Sursa `0e6a62f` a jucat 216 partide în trei serii, cu Stockfish 19 la `UCI_Elo 1500`, un fir, 6.000 de noduri cerute pe mutare și maximum 160 de semimutări. Am folosit cele 12 deschideri din protocolul de calibrare și am inversat culorile. Intervalele de 95% reeșantionează deschiderile, iar partidele active la plafon contribuie între zero și un punct.

| Profil | Victorii / remize / înfrângeri / neterminate | Elo estimat | Interval 95% |
|---|---:|---:|---:|
| Basic | 7 / 1 / 49 / 15 | 1268 | 922–1442 |
| Advanced | 23 / 3 / 30 / 16 | 1466 | 1316–1615 |
| Hard | 30 / 2 / 23 / 17 | 1534 | 1396–1672 |

Advanced cu 10.000 de noduri suplimentare și patru semimutări a jucat încă 72 de partide după același protocol. A obținut 19 victorii, 3 remize, 35 înfrângeri și 15 partide neterminate. Estimarea centrală a coborât de la 1466 la 1421, cu intervalul 1245–1553. În suita de zece poziții, pierderea medie Advanced a rămas la 14,1‰ și au existat zero pierderi majore. Estimarea centrală este sub plafonul cerut de 1450, iar intervalul încă îl traversează.

</details>

### Captura de la capătul căutării Hard

- Candidatul a îmbunătățit pozițiile tactice fixe, iar partidele pereche i-au măsurat impactul mai larg.

<details>
<summary>Acoperirea și verificările candidatului Hard</summary>

În poziția Siciliană din suita fixă, căutarea de patru semimutări alege `Nxe5` și vede replica. La cinci semimutări, caută `Nc3` și mută aceeași captură la capătul variantei. Prototipul a păstrat clasamentul par când liderul la adâncime impară amâna captura până la orizont.

Prototipul a crescut de la 9/10 la 10/10 alegeri identice cu Stockfish. Pierderea medie a scăzut de la 45,9‰ la 0‰, iar scorurile din cele 1.172 de probleme au rămas la 500/500, 300/300, 194/200, 87/100, 39/48 și 24/24. În duelul cu versiunea live a înregistrat 8 victorii, 6 remize și 10 înfrângeri. Diferența estimată este −29 Elo, cu interval de 95% între −89 și 0. Contra Stockfish, după 72 de partide, a obținut 1519 Elo (1357–1691) față de 1534 pentru referință. Duelul a dus la retragerea prototipului din codul live.

Comparația indiciului cu Hard pe 12 deschideri are o diferență cumulată de 281‰ față de 257‰. În Catalană, indiciul alege `c4d5` cu o pierdere de 49‰, iar Hard alege `b1c3` cu 25‰.

</details>

### Matul demonstrat și reutilizarea pozițiilor

- Probele măsoară echivalența mutărilor și costul înainte de acceptarea unei schimbări în căutare.

<details>
<summary>Praguri și controale istorice</summary>

Acceptarea unui mat demonstrat la ultima adâncime, indiferent de liderul anterior, a păstrat toate scorurile pe probleme. În 24 de partide, fiecare pereche cu culori inversate a produs aceeași secvență de mutări și duelul s-a încheiat cu 9 victorii, 6 remize și 9 înfrângeri. Prototipul rămâne în TMP.

Reutilizarea pozițiilor generate pentru ordonarea șahurilor a păstrat mutarea, adâncimea și nodurile pe 48 de poziții. Medianele au fost 594 și 639 ms față de 535 și 304 ms pentru referință în două rulări alternante. Alte probe rulau pe aceeași mașină, astfel că măsurarea are variație de sarcină. Implementarea cu hărți de poziții a fost respinsă.

</details>

### Bugetul experimental de 88.000 de noduri

- Bugetul mai mare rămâne un experiment cu probe separate de forță și latență.

<details>
<summary>Progresul căutării și lotul de comparație</summary>

La șase semimutări și 88.000 de noduri suplimentare, Hard a obținut 34 de victorii, o remiză, 19 înfrângeri și 18 partide neterminate după 72 de partide contra Stockfish 19. Estimarea este 1573 Elo (1432–1798), față de 1534 (1396–1672) la 44.000. În Mat în trei, rezultatul a crescut de la 194 la 198/200. Celelalte cinci categorii au păstrat scorurile. Media la tactică a crescut de la 29.626 la 45.761 de noduri. Pe zece poziții fixe a rămas aceeași pierdere medie de 45,9‰, cu o pierdere majoră în Siciliană.

Reeșantionarea perechilor după deschidere, cu jumătate de punct pentru partidele neterminate, estimează diferența la +39 Elo, cu interval de 95% între −54 și +132. Pe 48 de poziții, mediana a crescut de la 534 la 916 ms. Perechea de măsurări a rulat pe aceeași mașină, cu duelul JVM activ în fundal.

</details>

## Calibrarea Basic cu adâncime selectivă

- Probele pereche susțin adâncimea selectivă pentru Basic în politica sa curentă de noduri.

<details>
<summary>Politica de adâncime Basic și controalele</summary>

Am jucat câte 72 de partide per variantă, în trei sesiuni, cu Stockfish 19 la `UCI_Elo 1500` și 6.000 de noduri cerute pe mutare. Am folosit aceleași 12 deschideri cu culori inversate și plafonul de 160 de semimutări. V, R, Î și N înseamnă victorii, remize, înfrângeri și partide neterminate pentru Basic.

| Variantă | V / R / Î / N | Elo estimat | Interval 95% |
|---|---:|---:|---:|
| Referință la trei semimutări | 7 / 1 / 49 / 15 | 1268 | 922–1442 |
| Două semimutări | 3 / 0 / 63 / 6 | 1083 | −∞–1322 |
| Două semimutări, trei în șah | 4 / 0 / 59 / 9 | 1151 | −∞–1346 |
| Basic livrat, lotul de referință | 2 / 0 / 63 / 7 | 1067 | −∞–1253 |
| Basic livrat, 12 deschideri separate | 2 / 1 / 63 / 6 | 1067 | −∞–1245 |
| Basic livrat, ambele loturi | 4 / 1 / 126 / 13 | 1067 | 638–1225 |

Excepția pentru șah a estimat 1253 Elo pe încă 72 de partide cu 12 deschideri diferite. Două semimutări în toate pozițiile au estimat 1173 Elo pe aceleași deschideri noi, dar testul de conversie a prins un final de turn încheiat fără mat la plafonul de 30 de semimutări. Codul acordă a treia semimutare numai în finalurile elementare cu turnul ori dama contra regelui și trece din nou testele de conversie. Estimarea centrală este sub 1200 în ambele loturi, cu intervale care depășesc 1200. Ratingul istoric din `game-review.yaml` rămâne legat de dovada și commitul său. [Protocolul Stockfish](#calibrarea-versiunii-live)

</details>

## Recaptura Hard la orizont

- Schimbarea se evaluează față de mutările fixe, pragurile capitolelor și pozițiile independente.

<details>
<summary>Căutarea recapturii Hard și transferul</summary>

Prima probă a verificat o singură replică de recaptură pe pătratul ultimei capturi la orizont, cu nodurile scăzute din plafonul de 44.000. Pozițiile în șah păstrează evaluarea de bază, iar cache-ul include pătratul recapturii. Tehnica aplică adâncime selectivă schimburilor de material. [Căutare la adâncime variabilă](https://ijcai.org/proceedings/1983/183)

Pe zece poziții fixe, pierderea Hard a scăzut de la 45,9‰ la 2,4‰ și pierderile majore de la una la zero. Tactica a urcat de la 87/100 la 92/100. Mat în trei a coborât de la 194/200 la 193/200, iar `intermezzo-012` a coborât capitolul de la 24/24 la 23/24. Proba a încălcat pragul Intermezzo și a rămas în TMP.

Limitarea recapturii la adâncimi pare a păstrat scorurile tuturor capitolelor de referință, inclusiv 194/200 la Mat în trei și 24/24 la Intermezzo. În lotul fix Stockfish a rămas însă pierderea de 45,9‰, cu aceeași pierdere majoră. Colecția extinsă a dat 61/72 la Antrenament tactic cu Hard live, dintre care 22/24 în exercițiile noi. [Selecția publică](quest-curation.md#antrenament-tactic-și-lot-de-verificare)

Recaptura aplicată numai după ce o piesă ia una mai ieftină a trecut toate pragurile fixe. A obținut 95/100 la Tactică, 24/24 la Intermezzo și 192/200 la Mat în trei, față de 194/200 în codul live. Pe lotul fix Stockfish a păstrat 2,4‰ și zero pierderi majore. Pe 24 de poziții din 12 deschideri separate, cu ambele culori la mutare și Stockfish 19 la adâncimea 15, pierderea medie a urcat de la 53,0‰ la 68,0‰. Candidatul a îmbunătățit șase decizii, a egalat 14 și a înrăutățit patru. În Scandinavă, după `4.d4`, `…Nc6` a pierdut 399‰ față de `…Nf6`. În Siciliana clasică după `6.Bg5`, `…Nxe4` a pierdut încă 244‰ față de `…Bg4`. Candidatul rămâne în TMP.

În duelul direct, cu aceleași 12 deschideri și culori inversate, candidatul a obținut 10 victorii, 4 remize, 5 înfrângeri și 5 partide neterminate. Diferența estimată în duel este +73 Elo, cu interval 95% de la −137 la +307. Lotul independent și intervalul larg lasă creșterea Hard neconfirmată.

Restrângerea la recapturi după ultima mutare a lui Hard a trecut lotul fix, dar a crescut pierderea la 79,1‰ în cele 24 de poziții independente. Rezultatul arată că filtrul pe culoare singur nu rezolvă regresia.

</details>

## Rezolvarea quest-urilor cu obiectiv declarat

- Motorul de catalog primește obiectivul și dovedește matul împotriva tuturor apărărilor legale.

<details>
<summary>Condiții de acceptare și rezultate pe obiective</summary>

`HardQuestSolver` primește poziția și obiectivul. Pentru mat caută succesiv la o semimutare, trei și cinci, încearcă întâi șahurile și păstrează și mutările liniștite. O mutare de atac se dovedește numai dacă fiecare apărare legală permite continuarea. Tabela păstrează rezultatele exacte pe poziție și semimutările rămase. Plafonul este de 100.000 de noduri, iar epuizarea lui produce un rezultat distinct, fără soluție. Pentru material, aceeași evaluare minimax la trei semimutări cere o cheie unică și câștig forțat de cel puțin 200 cp. [Căutarea de dovezi în arbori de joc](https://research.tilburguniversity.edu/en/publications/proof-number-search-2/)

| Probă pe cele 200 de poziții de mat în trei | Soluții | Noduri medii | Maxim |
|---|---:|---:|---:|
| A · Hard în partidă | 194/200 | 14.446 suplimentare | 44.000 suplimentare |
| B · Dovadă, ordinea generatorului | 200/200 | 21.750 | 344.364 |
| C · Dovadă, șahuri înaintea mutărilor liniștite | 200/200 | 2.746 | 84.920 |

Comparația B/C folosește același plafon experimental de un milion de noduri. C a trecut apoi plafonul de 100.000 și a rezolvat 1.220/1.220 de quest-uri publice și 24/24 din lotul independent. Cheile YAML intră numai în aserțiune. Numărul de noduri al rezolvării și cel suplimentar al adversarului măsoară căutări diferite.

În partida Hard, oprirea la primul mat demonstrat a păstrat 194/200 la mat în trei și a redus media de la 14.446 la 9.657 de noduri suplimentare. Cele 24 de poziții independente au avut aceleași decizii, iar toate cele patru perechi de partide au avut secvențe identice, cu bilanț 3/2/3. Am integrat oprirea la dovadă ca economie de căutare. Poarta Stockfish rămâne la 45,9‰ și o pierdere majoră. Varianta cu fereastră nulă după prima ramură a obținut aceeași pierdere și a rămas în TMP. Ratingul Hard rămâne la estimarea de referință. [Protocol Fishtest](https://official-stockfish.github.io/docs/fishtest-wiki/Creating-my-first-test.html)

Rezervarea a 5.000 de noduri din bugetul de 44.000 pentru dovadă a dus la 199/200 la mat în trei. Antrenamentul a trecut de la 80/96 la 81/96, iar Tactica de la 87/100 la 86/100. Pe cele 24 de poziții independente cu istoric a ales aceleași mutări. Proba limita dovada la pozițiile primite fără istoric. A rămas în TMP. Cheia liniștită `…Nb4` din `mate3-005wy` a cerut 26.380 de noduri în căutarea de dovadă separată.

</details>

## Costul mutărilor legale și repertoriul Hard

- Controalele verifică numărul de mutări legale, paritatea alegerii și schimbările evaluării în bugetul fix.

<details>
<summary>Costul căutării și verificările repertoriului</summary>

Motorul păstrează pozițiile regilor când aplică o mutare. Verificarea șahului pornește de la rege, verifică atacurile scurte și se oprește la prima piesă pe fiecare rază. Direcțiile și atacurile folosesc în continuare regulile încărcate din YAML. La patru semimutări, cele patru poziții de control au produs 197.281, 4.085.603, 43.238 și 2.103.487 de continuări, identice cu Stockfish 19. Componenta verifică un subset rapid din aceleași poziții. [Poziții perft Stockfish](https://github.com/official-stockfish/Stockfish/blob/sf_19/tests/perft.sh)

Pe 48 de poziții, mutarea, adâncimea și nodurile Hard au coincis în cele patru rulări alternante. Medianele locale au fost 879 și 934 ms pentru generatorul anterior și 281 și 478 ms pentru cel actual. Percentila 95 a variat între 664 și 2.097 ms cu generatorul actual, sub sarcina rulărilor paralele. Măsurarea descrie JVM-ul local.

La 44.000 de noduri, evaluarea suplimentară a pionilor a crescut pierderea medie pe 24 de poziții independente de la 28,7‰ la 39,8‰. Adăugarea activității turnurilor și a protecției regelui a dus-o la 49,3‰. Bugetele experimentale de 110.000 și 175.000 au păstrat pierderea majoră din Siciliana cu `…e5`. Aceste variante rămân în TMP. [Model de evaluare pe faze](https://github.com/official-stockfish/Stockfish/blob/sf_14/src/evaluate.cpp)

În duelurile pe șase deschideri cu culori inversate, varianta cu pioni a avut 5 victorii, 5 înfrângeri și 2 partide neterminate. Varianta cu turnuri și protecția regelui a avut 4 victorii, 6 înfrângeri și 2 neterminate. Estimările diferenței au fost 0 Elo (−280–+280) și −58 Elo (−280–+191). [Metodologia Fishtest](https://official-stockfish.github.io/docs/fishtest-wiki/Creating-my-first-test.html)

Repertoriul Hard are șase replici noi pe poziții exacte. Stockfish 19 a ales aceeași replică la adâncimile 13, 16 și 19 pentru fiecare poziție. În Siciliana cu `…e5`, `Nxe5` a obținut la adâncimea 19 un scor așteptat de 993‰, față de 519‰ pentru `Nc3`. În Siciliana clasică, `…e6` a obținut 458‰ față de 277‰ pentru `…Bg4`. După replica de repertoriu, Hard caută continuarea cu plafonul de 44.000. [Repertoriu YAML](../../chess-core/src/main/resources/ai-repertoire.yaml)

Poarta fixă Stockfish a trecut cu 10/10 alegeri identice, pierdere medie 0‰ și zero pierderi majore. Poziția Siciliană intră în repertoriu, astfel că rezultatul măsoară și acoperirea repertoriului. Pe lotul anterior de 24 de poziții, 23 de decizii au coincis și una s-a îmbunătățit, cu pierdere medie de la 53,0‰ la 44,5‰. Lotul include Siciliana clasică din repertoriu. În cele 12 partide începute din pozițiile celor șase replici, cu culorile inversate, candidatul a obținut 6 victorii, o remiză, 3 înfrângeri și 2 partide neterminate. Estimarea diferenței este +89 Elo, cu interval de 95% de la −154 la +338. [Protocolul ancorei](#calibrarea-versiunii-live)

</details>

### Recalibrare pe cele 12 deschideri standard

- Comparația pe 12 deschideri inversează culorile și raportează intervalul alături de estimare.

<details>
<summary>Schimbări controlate și rezultate independente</summary>

Fiecare rând are 24 de partide cu Stockfish 19 la `UCI_Elo 1500`, un fir, 6.000 de noduri cerute pe mutare și ambele culori. Bilanțul este victorii, remize, înfrângeri și partide neterminate.

| Profil | Bilanț | Elo estimat | Interval 95% |
|---|---:|---:|---:|
| Basic | 2 / 0 / 22 / 0 | 1083 | −∞–1268 |
| Advanced | 6 / 0 / 11 / 7 | 1427 | 955–1691 |
| Hard înaintea repertoriului nou | 12 / 1 / 8 / 3 | 1558 | 1363–1755 |
| Hard cu repertoriul nou | 8 / 0 / 8 / 8 | 1500 | 1268–1780 |

Reluarea secvențelor UCI confirmă zero întâlniri cu cele șase poziții noi la mutarea lui Hard în ambele serii. Cele două estimări Hard diferă între rulările ancorei cu putere limitată. Duelul din pozițiile de repertoriu oferă măsura directă a schimbării, cu interval care include zero. [Protocolul ancorei](#calibrarea-versiunii-live)

Duelul direct pe alte 12 deschideri a dat 13 victorii, două remize, șapte înfrângeri și două partide neterminate pentru candidatul de 88.000. Diferența estimată este +89 Elo, cu intervalul 95% de la −14 la +232. Păstrăm bugetul live de 44.000 și folosim proba cu 88.000 pentru a identifica pozițiile unde căutarea selectivă poate economisi noduri.

</details>

## Captura imediată de pion în căutarea Hard

- Hard poate păstra captura imediată a unui pion când o linie completă la adâncime impară doar amână aceeași captură.

<details>
<summary>Politica de captură cu pionul, liga și scorul</summary>

Când adâncimea pară a clasat prima o captură imediată de pion, Hard poate păstra acel rezultat dacă linia impară preferă o mutare liniștită și abia la ultima semimutare capturează același pion. Condițiile cer material cel puțin egal în linia capturii imediate, un orizont complet pentru ambele variante și pionul prezent pe aceeași casă până la captură. Matul dovedit are prioritate. Regula se aplică în căutarea obișnuită Hard, în afara repertoriului și a căutării de final de turnuri. Cheia `hard-horizon.preserve-pawn-capture` permite dezactivarea pentru A/B. [Politica YAML](../../chess-core/src/main/resources/match-ai.yaml)

| Lot | Referință → candidat | Martor |
|---|---|---|
| Siciliana cu pion expus, două poziții de reglaj și două de verificare | `Nc3 → Nxe5` în toate cele patru poziții | Stockfish 19 a ales `Nxe5` la adâncimile 14, 17 și 19. |
| Poziții cu negrul la mutare | `Nc6 → Nxe4` într-o poziție; două capturi deja corecte se păstrează | Stockfish 19 a ales `Nxe4` la toate cele trei adâncimi. |
| Pion apărat sau damă pe c7 | Deciziile martor rămân neschimbate | La adâncimea 19, Stockfish preferă dezvoltarea. |
| Tactică în Siciliana cu …h6 și Nc3 | Hard păstrează `Nd5`; Stockfish preferă `Nxe5` | Orizontul de patru indică pierdere de material, iar următorul nu se termină în buget. |

Pe patru poziții tematice, fiecare versiune a jucat cu ambele culori contra Stockfish 19, `UCI_Elo 1500`, un fir și 6.000 de noduri cerute pe mutare. A și B au refolosit răspunsul Stockfish cât timp istoricul a coincis. Șase trasee au rămas identice. În celelalte două, B a câștigat cu albele la semimutările 41 și 39; A a rămas activă la plafonul 180 și a câștigat la 135. Bilanțurile sunt 4 / 0 / 3 / 1 pentru A și 5 / 0 / 3 / 0 pentru B. Intervalele estimărilor se suprapun.

Poarta fixă a păstrat 10/10 pentru Hard și plafonul de 44.000 de noduri suplimentare. Pe probleme fără obiectiv transmis motorului, rezultatele au rămas 500/500, 300/300, 194/200, 87/100, 80/96 și 24/24. Solverul cu obiectiv declarat a păstrat 1.220/1.220 publice și 24/24 independente. Testul comparativ al indiciului a dat aceeași abatere în A și B, 281‰ cumulat pentru indiciu și 257‰ pentru Hard pe 12 poziții. Diferența vine din Catalană, unde indiciul preferă `cxd5` cu 49‰ pierdere, față de `Nc3` cu 25‰ la Hard. [Feedback AI](../feedback/README.md)

În repetarea tematică, după restrângerea regulii la același pion și la orizontul imediat precedent, A a obținut 3 / 0 / 2 / 3 și B 5 / 0 / 2 / 1. Una dintre cele două partide câștigate în plus s-a schimbat ulterior față de poziția de pornire. Pe șase deschideri neutre cu răspunsuri comune, cele 12 trasee au coincis și fiecare variantă a obținut 7 / 0 / 3 / 2. Estimarea Hard este 1620 Elo, cu intervalul 1500–1917. Lotul neutru nu demonstrează un câștig general.

| Profil | Ultimul lot neutru | Elo estimat | Interval 95% |
|---|---:|---:|---:|
| Basic | 0 / 0 / 11 / 1 | 955 | −∞–1309 |
| Advanced | 4 / 0 / 5 / 3 | 1471 | 1083–1917 |
| Hard | 7 / 0 / 3 / 2 | 1620 | 1500–1917 |

Fiecare profil are câte 12 partide cu aceeași ancoră. Basic și Advanced folosesc seria de calibrare, iar Hard seria A/B cu răspunsuri comune. Intervalele rămân largi. Reperele afișate în aplicație provin în continuare din calibrarea guvernată. [Protocolul ancorei](#calibrarea-versiunii-live)

</details>

## Verificarea schimburilor după o captură de pion

- Verificarea schimburilor compară mutări căutate separat și este evaluată pe poziții fixe și în partide pereche.

<details>
<summary>Politica de schimburi, regresii și rezultate</summary>

Hard pornește de la clasamentul obișnuit. Într-o poziție cu material egal și fără șah la rădăcină, o captură de pion care conduce la trei semimutări, cu un câștig aparent de cel puțin doi pioni, poate fi verificată dacă liderul de la patru semimutări este altul și următoarea iterație nu furnizează un rezultat acceptabil. Comparația caută separat cele două mutări la aceeași adâncime, cu până la două semimutări pentru capturi, promovări și ieșiri din șah. Captura trece înainte doar dacă ambele verificări se termină, scorul ei e mai bun și linia câștigă material de cel puțin un pion. Rezerva `hard-horizon.pawn-exchange-nodes` consumă până la 7.000 din cei 44.000 de noduri suplimentare și se poate dezactiva cu valoarea zero. [Politica YAML](../../chess-core/src/main/resources/match-ai.yaml)

După `1.e4 c5 2.Nf3 h6 3.Nc3 e5`, căutarea la patru semimutări ajungea la `Nxe5 Qf6 Nxf7 Qxf7` și evalua o pierdere de material. Verificarea completă compară și alte răspunsuri. Stockfish 19, la adâncimea 20 cu mutarea forțată, a dat +241 cp pentru `Nxe5` și +37 cp pentru `Nd5`. Șase poziții de reglaj și patru dintre cele șase poziții de verificare au trecut de la o mutare diferită la `Nxe5`. Celelalte două poziții de verificare au păstrat o captură bună și au refuzat o captură de pion apărat. Martorii cu dama pe c7, calul pe c6 sau pionul pe d6 au păstrat deciziile anterioare.

În cele 16 partide tematice contra Stockfish 19 cu culori inversate, bilanțurile au fost 5 / 0 / 2 / 1 pentru A și 3 / 0 / 3 / 2 pentru B. Pe lotul neutru, bilanțurile au fost 4 / 0 / 7 / 1 pentru A și 5 / 0 / 6 / 1 pentru B. În duelul direct pe patru deschideri tematice, B a obținut 3 / 0 / 2 / 3, cu o diferență estimată de +44 Elo și interval 95% de la −338 la o limită superioară deschisă. Bilanțul este victorii, remize, înfrângeri și partide neterminate. Răspunsul Stockfish este reutilizat cât timp istoricul coincide. Rezultatele confirmă corecția deciziei de captură, iar conversia avantajului rămâne o prioritate. [Metoda perechilor](https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html) · [Feedback AI](../feedback/README.md)

| Profil | Lot neutru | Stockfish 19 `UCI_Elo` | Elo estimat | Interval 95% |
|---|---:|---:|---:|---:|
| Basic | 2 / 0 / 10 / 0 | 1320 | 1040 | −∞–1200 |
| Advanced | 1 / 0 / 6 / 5 | 1500 | 1346 | −∞–1780 |
| Hard B | 5 / 0 / 6 / 1 | 1500 | 1471 | 1309–1691 |

Basic a pierdut toate cele 12 partide contra ancorei 1500. Rularea contra 1320 oferă o estimare finită. Fiecare profil are câte șase perechi, cu un fir Stockfish și 6.000 de noduri cerute pe mutare. Ratingurile depind de lot și de adversar. [Calibrarea Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)

</details>

## Studiul pereche nativ/indiciu și respingerea recapturii

Producătorul din 18 noiembrie `e320379212dbb55afb392351da559b1cf949ac4d` înregistrează trei studii de deschidere a câte 20 de semimutări. La fiecare checkpoint capturează Hard nativ, indiciile publice și evaluarea afișată înainte de joc. Bugetul nativ rămâne de cel mult 200k suplimentare; indiciile păstrează totalul separat de 200k. Motorul de producție este neschimbat.

| Constatare H19 | Rezultat măsurat |
|---|---:|
| Prime indicii cu pierdere de cel puțin 150‰ | 12/60 |
| Variante oferite cu acea pierdere | 15/83 |
| Prime replici adverse din PV cu acea pierdere | 10/83 |
| Recomandări native cu acea pierdere, inclusiv poziții de control | 3/60 |
| Decizii Hard efectiv jucate cu acea pierdere | 2/21 |

Toate cele 960 de recepții brute Stockfish H17/H19 se rejucă valid. Integritatea trece; benchmarkul separat de calitate șahistă eșuează. Sunt acoperite prima replică adversă și capetele liniilor, nu fiecare apărare ipotetică ulterioară. După `6.e6` din Caro–Kann, `…dxc3` nativ păstrează câștigul, iar primul indiciu `…fxe6` pierde 576‰. Bara pentru alb afișează +36 cp acolo unde Stockfish raportează −411 cp pentru alb. `Nd5/Na4` din Italiană pierde 467‰ în indiciu, iar `…Be7/…Nc6` din Siciliană 358‰. [Protocolul și ambele verdicte](../testing/hard-hint-study.md)

Prototipul de recaptură țintită închide `…Qxe5 / …Nxe5` în frunzele H6 izolate și ordonează `…Qg6 −50` înainte de `…Na5 −80 cp`, inversând reperul. Probele cache încălzit, rece, dezactivat și cu fereastră îngustă păstrează rezultatul local. Rădăcinile izolate au plafoane separate; nu dovedesc contabilizarea deciziei complete. Candidatul complet alege sacrificiul nesănătos `…Bxf2+` și pierde martorii 019/021/022 cu 478,5‰, 191,5‰ și 516‰ la H19. Este respins și rămâne în afara producției.

Game Review efectiv păstrează toate mutările și nu emite Elo pentru studiu, dar nouă neconcordanțe de clasificare persistă la ambele adâncimi ale oracolului. Rădăcinile jucate sunt auditate; liniile alternative sunt rejucate legal fără a pretinde evaluarea independentă a fiecărei alternative. [Constatările analizei](game-review.md#limitările-măsurate-ale-analizei)

Cele trei partide limitate păstrează rezultatul `*`. Nu produc Elo sau ΔElo nou al candidatului. Calibrarea nativă Hard calificată separat rămâne 1.507, cu interval conservator 95% 1.357–1.656, din 100 de partide. Patru lecții cu istoric complet sunt date externe de studiu, nu excepții FEN în runtime. [Lucrul rămas deschis](../feedback/README.md)

## Indiciul Caro–Kann și verificarea recapturii după pionul cu șah

După `1.e4 c6 2.d4 d5 3.e5 c5 4.Nc3 Nc6 5.dxc5 d4 6.e6`, indiciul alege acum `…dxc3` în loc de `…fxe6`. Vechea comparație H6 se oprea după `Qxd8`, fără recaptura legală de cal. Închiderea doar a șahului nu ajungea: o captură ulterioară de piesă putea rămâne fără răspuns. Închiderea generală a pierdut pe un istoric independent și a fost respinsă.

Domeniul acceptat compară capturi de pion și de piesă numai când luarea piesei permite o captură adversă legală de pion cu șah. La frontiera obișnuită poate urmări caseta ultimei capturi încă cel mult două semimutări, inclusiv ieșirile legale din șah. Evaluarea fără mutare nu este permisă în șah cât timp extensia este activă. Limita este `hint.verification-recapture-plies` din schema 8; zero o dezactivează. Căutarea inițială, jocul nativ obișnuit și bara păstrează politicile lor. Identitatea cache-ului include modul recapturii, deci o valoare inițială nu poate substitui dovada extinsă. Tăierile și lucrul întrerupt nu devin valori exacte în cache. Comparația selectivă poate înlocui liderul fără să introducă oferte echivalente noi.

Rezultatul public corectat are căutarea inițială H4 și comparația comună completă H5, în exact 200k vizite totale. Nu este redenumit H6. Ambele rădăcini rămân în diagnostic; `…fxe6` nu este oferită ca echivalentă. Stockfish H17/H19 confirmă pierdere zero de scor așteptat pentru `…dxc3`; vechiul indiciu pierdea 576‰ la H19. Indiciul albului `Nce2` de dinainte rămâne corect păstrat.

Rejucarea acelorași 60 de checkpoint-uri istorice schimbă numai această decizie publică. Toate deciziile native Hard, adâncimile, nodurile și PV-urile lor, evaluările afișate și toate indiciile din Italiană/Siciliană sunt identice. Noua colecție are 954 de cereri brute Stockfish. Numărul pierderilor mari la H19 scade de la 12 la 11 prime indicii și de la 15 la 14 mutări oferite, iar 10 prime replici adverse din PV rămân deficitare. Benchmarkul absolut de calitate încă eșuează la pragul neschimbat de 150‰. Aceste rejucări limitate nu produc Elo nou.

Șaisprezece istorii de dezvoltare au expus domeniul general respins. După înghețarea altor șaisprezece istorii PGN cu posibil context de pion forțant, doi lideri se îmbunătățesc la ambele adâncimi ale oracolului: pierderea H19 scade de la 243,5‰ la 8‰ și de la 88‰ la zero, fără pierdere nouă a liderului în acel lot. Sunt checkpoint-uri distincte din partide istorice, nu șaisprezece familii de deschidere independente statistic. Primul caz păstrează o alternativă secundară deficitară, de 243,5‰; rămâne lucru deschis. Game Review efectiv păstrează toate cele șaizeci de mutări și cele nouă neconcordanțe anterioare de clasificare. Corectarea unui indiciu nu certifică etichetele analizei.

Colecția legată de sursă și experimentele respinse sunt păstrate în `C:\work\sah\studii\2026-11-18-j7-caro-hint-recapture`. [Contracte componente și negative](../../atf-tests/src/test/java/ro/atf/chess/HintRecaptureRegressionTest.java) · [Politică](../../chess-core/src/main/resources/match-ai.yaml) · [Audit și publicare](../testing/hard-hint-study.md) · [Lacune de calitate rămase](../feedback/README.md)

## Recaptura damei și continuitatea indiciului italian

După `1.e4 e5 2.Nf3 Nc6 3.Bc4 Bc5 4.Nc3 Qf6`, indiciul public selectează doar `Nd5`, înlocuind `Na4`. Linia veche se termina după `Nd5 Qd6 O-O Na5 Bb5 Bxf2+ Rxf2 Qxd5`, înainte ca `exd5` legal al albului să câștige dama. Stockfish 19 H17/H19 confirmă pierdere nulă de scor așteptat pentru `Nd5`; vechiul `Na4` pierdea 467‰ la H19. Continuarea H6 acceptată este `Nd5 Qd8 O-O Nf6 Nxf6 gxf6`, nu schimbul vechi fără recaptură.

Căutarea comună a indiciului reverifică H3 complet doar când liderul unic, fără mat și fără captură, creează un atac nou asupra damei adverse în afara repertoriului. Poate încheia o recaptură legală de pion asupra damei la frontiera tactică, în limita `hint.verification-recapture-plies` și a aceluiași total de 200k. Atacul geometric alocă lucru; legalitatea recapturii se verifică separat, inclusiv pentru piese legate. Sămânța și comparația moștenesc același mod, inclus în cheia cache-ului exact. Confirmarea întreruptă păstrează orizontul deja complet; căutarea mai adâncă incompletă nu este declarată terminată. Comparațiile diagnostice pot activa modul din rădăcinile primite. Jocul nativ obișnuit și bara de evaluare nu primesc extensia.

Rezultatul public păstrează sămânța H4, comparația/selecția H6 și 200k vizite totale. Comparația explicită rece `Nd5/Na4` termină H5, cu +92/+70 cp; diferența nativă comprimată nu este calibrată la Stockfish. Recaptura selectivă poate înlocui liderul fără să creeze oferte echivalente noi. Becul, redarea jucabilă, comparația și atingerea destinației folosesc linia calculată și păstrează istoricul original. [Componente](../../atf-tests/src/test/java/ro/atf/chess/HintQueenRecaptureTest.java) · [Politică](../../chess-core/src/main/resources/match-ai.yaml)

Producătorul `3b1e7ed1a4f2659b544d29732848d8078c5f3abc` reia aceleași șaizeci de checkpoint-uri istorice. Fiecare raport nativ și estimare afișată sunt identice. Se schimbă doar doi lideri: `Na4 → Nd5` și ulterior `Bb5 → Be2`; liderii Sicilianei și Caro–Kann rămân intacți. La H19, lotul pereche ajunge la 9/60 lideri deficitari, 12/83 oferte deficitare și 9/83 prime replici adverse deficitare, față de 11/60, 14/83 și 10/83. Integritatea trece; pragul absolut neschimbat de 150‰ încă eșuează. Game Review efectiv rămâne identic pe cele șaizeci de mutări, cu cele nouă neconcordanțe cunoscute de clasificare.

Activarea generală respinsă a degradat un lider independent de la 132‰ la 274‰ pierdere. Cele șaisprezece istorii inspectate sunt martori de dezvoltare, nu validare nouă. După restrângere și înghețarea altor șaisprezece istorii de deschidere, un lider se îmbunătățește de la 339,5‰ la 17‰, iar altul scade de la 0‰ la 13,5‰; niciun lider nou nu depășește 150‰ acolo. Lotul de dezvoltare păstrează separat o scădere de la 0‰ la 18,5‰. Aceste eșantioane țintite de presiune asupra damei nu demonstrează forță globală sau oferte secundare perfecte.

Două studii tematice noi de șaisprezece semimutări urmează prefixele `Nd5` și deliberat inferior `Na4`, cu replici Stockfish noi. Cele 32 semimutări sunt raportate separat de cele șaizeci pereche; prefixele comune nu sunt partide independente. Colecția completă validează 1190 cereri brute H17/H19, inclusiv rădăcinile comparației diagnostice. Toate cele cinci partide sunt plafonate cu rezultat `*`, deci nu se măsoară Elo nou sau ΔElo nativ. Dovezile, prototipurile respinse și lecțiile doar de studiu rămân în `C:\work\sah\studii\2026-11-18-j7-italian-nd5-hint`. [Audit și publicare](../testing/hard-hint-study.md) · [Lacune rămase](../feedback/README.md)

Prima replică adversă din PV la țintă se îmbunătățește de la 273‰ la 13,5‰ pierdere H19. La liderul italian corectat ulterior, pierderea replicii crește însă de la 24,5‰ la 86‰. Ambele rămân sub pragul înghețat de pierdere mare; niciuna nu dovedește continuarea completă optimă.

## Egalități exacte în Siciliană și apărarea piesei legate

După `1.e4 c5 2.Nf3 d6 3.d4 cxd4 4.Nxd4 Nf6 5.Nc3 a6 6.Bg5 e6 7.f4`, indiciul public oferă acum doar `…Nbd7`. Atât acesta, cât și `…Nc6` păstrează −21 cp la H5 complet; sămânța H3 și plafonul total 200k sunt neschimbate. Ordinea veche ascundea o diferență relevantă: `…Nbd7` adaugă un apărător lui Nf6, legat relativ de Bg5 față de Qd8. Stockfish 19 H17/H19 evaluează independent rădăcina aleasă; pierderea H19 scade de la 358‰ pentru `…Nc6` la 33‰ pentru `…Nbd7`. Oracolul liber H19 preferă `…Qb6`, deci corecția nu dovedește un indiciu global optim sau un scor nativ calibrat.

`HintPinSupport` intervine doar după egalitatea scorurilor. Un nebun sau cal mutat fără captură trebuie să adauge în siguranță o apărare nouă altei piese ușoare deja legate față de rege sau damă. Apărătorul legat de propriul rege, apărătorul capturabil imediat, lipsa piesei regale din spatele razei sau atacantul legării de damă legat el însuși de rege invalidează preferința. Valorile cp diferite și scorurile de mat rămân prioritare. Departajarea defensivă acceptată produce o singură ofertă publică; cealaltă rădăcină rămâne în comparația diagnostică. Nu slăbește confirmarea inversărilor numerice și nu adaugă căutări după FEN, ID de deschidere sau oracol. [Preferință](../../chess-core/src/main/java/ro/atf/chess/HintPinSupport.java) · [Componente](../../atf-tests/src/test/java/ro/atf/chess/HintPinSupportTest.java)

Becul, tabla manuală, atingerea destinației și comparația explicită împart același nucleu și PV calculat. Egalitatea nativă −21/−21 este păstrată, nu înlocuită cu valori Stockfish. Costul H19 al primei replici adverse afișate rămâne 21,5‰; nici aceasta, nici restul continuării nu sunt certificate optime. Jocul nativ, bara, semantica cache-ului și Game Review rămân identice pe toate cele șaizeci de checkpoint-uri pereche. Se schimbă doar faza13 siciliană. Numerele cu pierdere mare devin 8/60 lideri, 11/82 oferte și 9/82 prime replici adverse; calitatea absolută încă eșuează la pragul neschimbat de 150‰.

Prototipurile anterioare de frontieră sunt respinse. Închiderea capturii de damă cu șah nu a schimbat ținta. Extensia completă pentru amenințarea de pion a ales `…Nbd7`, dar oprea alt schimb înainte de recaptura legală; închiderea lui a eliminat avantajul aparent. Trecerea vechilor componente nu a calificat rezultatul șahist. [Terminologia Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Terminology.html) explică rolul liniștirii, fără să dovedească aceste prototipuri plafonate. Păstrează sursele și martorii respinși, în loc să tratezi doar mutarea selectată drept dovadă.

Producătorul `b90f181b707a3a7dd4c67147a31208534b69f0e2` păstrează toate rapoartele native și celelalte 59 de indicii complete. Șaizeci și patru de istorii PGN selectate geometric au ieșiri vechi/noi identice; primele șaisprezece primesc verificări de rădăcină H17/H19. Aceasta dovedește păstrarea, nu activarea sau câștiguri independente. Două studii tematice noi de douăzeci de semimutări urmează prefixele forțate `…Nbd7` și inferior `…Nc6`, apoi câte șase semimutări efectiv jucate noi. Se suprapun cu prefixul sicilian inspectat și nu formează o ligă de forță. Auditul validează 284 de cereri brute noi și păstrează identitățile originale pentru 85 de proiecții de oracol neschimbate din colecția anterioară de 1190 de cereri. Toate cele 100 de recepții native curente sunt capturate din nou; cele vechi nu sunt reetichetate drept curente. [Contractul auditului](../testing/hard-hint-study.md)

Dovezile și lecțiile doar de studiu rămân în `C:\work\sah\studii\2026-11-18-j7-sicilian-knight-hint`. Cele 110 verificări de componentă/raport trec, iar motorul original eșuează real în aserțiunea țintită nouă. Traseele plafonate nu produc Elo nou sau ΔElo nativ. [Lacune rămase ale modelului și ofertelor](../feedback/README.md)

## Acoperirea celor patru rădăcini în Siciliană

După același prefix `7.f4`, indiciul compară `…Nbd7`, `…Nc6`, `…Be7` și `…Qb6` la H5
încheiat, într-un singur plafon total 200k. Scorurile brute rămân −21/−21/−25/−29 cp.
`HintCandidateCoverage` adaugă referința lipsă din teoria nativă într-o comparație defensivă
cu două rădăcini, apoi cel mult o mutare liniștită sigură de damă care atacă nou un pion
neapărat și o piesă minoră. Alocă muncă, nu bonus de scor, lookup FEN sau răspuns automat din
teorie. Toate cele patru rădăcini calculate rămân diagnostice; public se oferă numai `…Nbd7`.

Stockfish 19 H17/H19 verifică fiecare rădăcină, prima replică adversă din PV și capătul liniei.
La H19 pierderile rădăcinilor forțate sunt 33/358/10,5/11‰. Căutarea liberă H19 alege `…Qb6`;
cea forțată separat diferă cu 11‰, deci nu raportăm un zero exact inventat. Primele replici
adverse costă 21,5/15,5/69,5/37‰. Liniile legale sunt auditate, nu certificate optime la
fiecare semimutare ulterioară. Acoperirea extinsă nu repară subevaluarea numerică a damei și nebunului.

Replay-ul pereche de șaizeci de faze schimbă numai comparația/PV-ul indiciului țintă. Indiciile
din Italiană și Caro–Kann, toate deciziile native, evaluările, checkpoint-urile și mutările jucate
rămân identice. Cele șaizeci și patru de istorii de transfer au ieșiri identice. Numerele istorice
rămân 8/60 lideri, 11/82 oferte și 9/82 prime replici adverse cu pierdere mare. Două studii noi de
câte douăzeci de semimutări pornesc din `…Qb6` și `…Be7`; incluzându-le, numerele devin 15/100
lideri, 19/142 oferte și 15/142 replici. Cohortele noi expun limite rămase, nu o regresie pereche sau ΔElo.

Auditul legat de surse păstrează 100 de faze native noi, 308 interogări brute Stockfish noi și
958 de proiecții pe interogări exacte cu originea păstrată. Perechea liber/forțat cere același
producător de oracol, istoric, orizont și opțiuni. Combinarea recepțiilor valide din două rulări
este respinsă. Cele 119 verificări de componentă/raport și calificarea limitată trec; calitatea
absolută a indiciilor încă eșuează la limita neschimbată 150‰.

Dovezi: `C:\work\sah\studii\2026-11-18-j7-sicilian-candidate-coverage`.
[Componente](../../atf-tests/src/test/java/ro/atf/chess/HintCandidateCoverageTest.java) ·
[Audit indicii](../testing/hard-hint-study.md) · [Reper puzzle-uri native](../testing/hard-puzzle-study.md)

## Readmiterea calificată a rădăcinilor pentru mat

Producătorul din 18 noiembrie `cc37530be13cf5ade582b3e1d42784fcc4b28e21` recuperează
`mate3-04iY6` (`Kc4`, în loc de `Kxd4`) și `mate3-019gx` (`Ra4+`, în loc de `Ra8+`).
Dovezile selectate încheie H5 la 19.865 și 75.415 noduri suplimentare. Ambele probleme
sunt verificate contra tuturor apărărilor legale, inclusiv cu culorile inversate. Analiza
nativă nu primește ID-ul, cheia, apărarea sau lungimea declarată a problemei.

Căutarea obișnuită de turn păstrează lista scurtă validată și își încheie întâi orizontul
configurat. Dacă nu are mat câștigător, rădăcinile eliminate pot reintra pentru o dovadă
de mat. Șahurile sunt ordonate primele; orizonturile impare pornesc dincolo de H3 deja
calculat. Fereastra îngustă doar nominalizează candidatul. Urmează obligatoriu confirmarea
cu fereastră completă la același orizont de dovadă, din același buget rămas, cache și context
de repetiții. Numai matul pozitiv confirmat înlocuiește decizia. Eșecul sau întreruperea
păstrează alegerea încheiată. Raportul selectat arată H5 când dovada este H5, fără eticheta H7 împrumutată.

La baza adaptivă de 19.864 de noduri, confirmarea se întrerupe și rămâne `Kxd4`/H7.
La 19.865 se încheie dovada exactă `Kc4`/H5. Maximul rămâne 200k în ambele probe, astfel
încât cache-ul și plafonul comun sunt fixe. Martorii existenți de repetiție și remiză trec.
[Implementare](../../chess-core/src/main/java/ro/atf/chess/TacticalAnalysis.java) ·
[Componente țintite](../../atf-tests/src/test/java/ro/atf/chess/RookRootReentryTest.java)

Readmiterea generală la fiecare adâncime este respinsă: recuperează trei etape din catalog,
dar pierde victorii în `held-rook-08` și `held-rook-12` la ambele adâncimi Stockfish. Costul
rădăcinilor suplimentare împiedică încheierea răspunsului câștigător H7. Domeniul calificat,
limitat la dovada matului, păstrează acești martori. Al doilea lot, cu 24 de compoziții
legale noi de turnuri/pioni, înghețat după algoritm și înaintea ieșirilor sale, nu are regresii
WDL la H17/H19. Sunt poziții de verificare, nu probleme publice noi sau partide Elo.

### Reperul calificării maturilor cu turnuri

| Observație | Rezultat calificat |
|---|---:|
| Probleme native canonice complete | 1.184/1.238, anterior 1.182 |
| Etape canonice acceptate | 2.223/2.303, anterior 2.221 |
| Etape anterior acceptate pierdute | 0 |
| Capitolele curente mat în 1 / mat în 2 / mat în 3 | 500/500 · 300/300 · 200/200 |
| Toate continuările adverse legale din capitolele de mat | 1.500 ramuri terminale, zero ratate |
| Etape didactice încă neacceptate | 80 în 54 de probleme materiale |

Auditul exhaustiv păstrează cele 1.000 de mutări inițiale efectiv selectate, apoi înregistrează
1.236 de decizii native noi de continuare. Rejucarea independentă a recepțiilor consumă fiecare
ramură legală o singură dată. `NativeMateDefenseBenchmarkTest`, portabil în proiect, recalculează
independent toate cele 2.236 de alegeri native și aceleași 1.500 de frunze de mat. Reutilizează
`NativeMateAudit` împreună cu testele țintite; nu cere emulator, date externe sau solver cu
obiectiv declarat. [Comenzi și separarea solverelor](../testing/hard-puzzle-study.md)

Stockfish H17/H19 evaluează acum toate cele 80 de etape canonice încă ratate, nu un eșantion
favorabil. 57 pierd cel puțin 150‰ la ambele adâncimi; 22 păstrează victoria la ambele,
deși ratează obiectivul didactic; una are pierdere mai mică sau dependentă de adâncime.
Din cele 54 de prime ratări didactice, 43 au pierdere WDL mare la ambele adâncimi. Pentru a le numi prime pierderi WDL cronologice, trebuie verificate și prefixele anterior acceptate. Etapele ulterioare
unei intervenții de referință rămân dovezi condiționate, nu partide independente.
Toate cele 80 de chei didactice inspectate rămân sub limita existentă de pierdere mare la ambele adâncimi.

`training-051` rămâne la `Ke6`, nu `Rc8+` câștigător; ambele orizonturi confirmă victorie→remiză.
Fixul de mat nu declară acest final rezolvat. Acest reper a identificat `training-107:1`:
șase piese, `c5` în loc de `h8=Q`, H5 și 14.652 de noduri suplimentare, cu pierdere 500‰
la ambele adâncimi. Recuperarea calificată a promovării și noua lacună de conversie sunt
înregistrate mai jos. Alternativele câștigătoare păstrate și poarta completă de mat rămân martori.

Pe catalog, media muncii suplimentare crește de la 14.371,56 la 15.537,58 de noduri;
p95 rămâne 100k, iar maximul 200k. Sunt costuri deterministe, nu latență izolată sau măsurare
Elo. Baza H3 nativă rămâne separat nebugetată. Cele 146 de verificări de componentă/raport
trec, precum și noul benchmark portabil complet de mat. Verdictul „toate problemele rezolvate”
rămâne FAIL pentru cele 54 de probleme materiale incomplete.

Dovezi: `C:\work\sah\studii\2026-11-18-j7-rook-root-reentry`. README-ul indică Extent,
recepțiile canonice și de apărări libere, oracolul cu origini păstrate, cele 80 de lecții YAML
și 43 de priorități la prima pierdere. Nu s-au schimbat lookup-uri FEN runtime, acceptarea
catalogului, numărul public de probleme, politica nativă sau Elo-ul afișat.

## Confirmarea promovării prin schimb închis

`training-107:1` a expus o eroare de oprire, nu lipsa nodurilor. După `f6g7 f8d8`, H4
clasează `h8=Q` la +167 cp și dă lui `c5` o limită superioară de −109. H5 preferă linia
cooperantă `c5 Re8 h8=Q Rxh8 Kxh8` la +190. H6 încheiat inversează ordinea: `c5 Rd7+` este
−115, iar `h8=Q Rxh8 Kxh8` este +183. Veto-ul final de stabilitate păstra anterior H5.

Confirmarea cu fereastră completă rezolvă `c5` la H4 cu −279 cp, nu cu limita superioară
preliminară de −109. Mecanismul nu tratează acea limită ca scor exact de comparație.

[Politica promovării](../../chess-core/src/main/resources/hard-promotion-exchange.yaml)
permite o singură excepție structurală. PV-ul efectiv trebuie să închidă promovarea, capturarea
ei și o recaptură legală pe caseta promovării, păstrând câștigul material minim configurat.
Comparațiile candidat/lider cu fereastră completă trebuie să fie exacte, pozitive și strict mai
bune la ambele orizonturi pare, precedent și final. Verificarea folosește aceeași alocație nativă;
confirmarea întreruptă păstrează liderul și adâncimea încheiată anterioare. Politica runtime nu
citește chei didactice, FEN-uri, scoruri de oracol sau bonusuri. [Implementare](../../chess-core/src/main/java/ro/atf/chess/PromotionExchangePolicy.java)
· [Componente](../../atf-tests/src/test/java/ro/atf/chess/PromotionExchangePolicyTest.java)

| Observație | Rezultat calificat |
|---|---:|
| Alegerea efectivă a promovării | `h8=Q`, H6, 15.589 de noduri suplimentare |
| Probleme native canonice complete | 1.184 → 1.185 / 1.238 |
| Etape canonice acceptate | 2.223 → 2.224 / 2.303; zero pierdute |
| Funcționalitate dezactivată | Aceleași mutări, adâncimi, noduri și PV-uri în toate cele 2.303 decizii de referință |
| Compoziții tematice neatinse | 48 de poziții / 24 de familii geometrice; 12 alegeri schimbate, fără regresie WDL la H17/H19 |
| Joc advers extins | 178 de semimutări efective în șase contexte și două brațe; 90 de puncte de decizie nativă |
| Audit independent de oracol | 463 de interogări H17/H19 cu cache gol, comenzi brute, scoruri exacte și PV-uri legale |

La baza diagnostică de 15.588 de noduri, confirmarea se întrerupe și rămâne `c5`/H5. La 15.589,
comparațiile exacte se încheie și `h8=Q`/H6 îl înlocuiește. Ambele păstrează maximul de 200k și
capacitatea cache-ului. Publicarea curentă respinge un instantaneu străin al politicii încărcate.
Plafonul runtime rămâne adaptiv 100k→200k noduri suplimentare; H3 inițial rămâne nebugetat.
Media pe catalog se schimbă de la 15.537,58 la 15.537,99 noduri suplimentare. Nu se declară un
câștig de latență sau Elo. Basic și Advanced păstrează analiza completă în fiecare context comparat.

**Limita reperului anterior de promovare.** În joc liber, referința remizează prin
repetiție după `c5`. Candidatul ajunge la `h8=Q Rxh8 Kxh8 Ke4`, apoi pierde victoria cu `Kg7`
în loc de `a4`; ambele orizonturi confirmă 500‰ pierdut. Linia se încheie cu remiză și regi goi
după 12 semimutări. Lacuna este închisă prin [dovada limitată de conversie](#conversia-limitată-a-finalurilor-de-pioni) de mai jos.
`training-051` alege încă `Ke6`, nu `Rc8+` câștigător. Cele 79 de ratări canonice rămase cuprind
56 de pierderi WDL mari, 22 de obiective ratate cu victoria păstrată și un caz mai mic/dependent
de adâncime. Din cele 53 de prime ratări didactice, 42 au pierdere mare; verifică și prefixele
acceptate înainte de a declara prima pierdere WDL cronologică. Verdictul „toate problemele
rezolvate” rămâne FAIL pentru 53 de probleme.

Trec cele 121 de verificări țintite de componentă/raport, benchmarkul portabil complet de mat
și patru verificări ale dovezilor brute. Toate cele 1.000 de probleme de mat păstrează fiecare
ramură adversă legală. Studiul extern `C:\work\sah\studii\2026-11-18-j7-hard-training107-promotion`
conține Extent, PGN-urile efective, cele 79 de lecții contextuale păstrate și tiparele YAML
reutilizabile de promovare/tempo. Tiparele runtime generalizează geometria; lecțiile externe
păstrează contraexemplele și proveniența în loc să memoreze tacit mutările câștigătoare.
[Procedura repetabilă a studiului](../testing/hard-puzzle-study.md)

## Conversia limitată a finalurilor de pioni

După `h8=Q Rxh8 Kxh8 Ke4`, Hard obișnuit încheie efectiv H6 și preferă `Kg7` la
+104 cp. De această dată, veto-ul de stabilitate nu este cauza. Orizontul apreciază pionul
rămas după pierderea celuilalt, nu tempoul prin care `a4` câștigă. [Politica pionilor](../../chess-core/src/main/resources/hard-pawn-promotion.yaml)
adaugă o dovadă limitată pentru cel mult doi pioni proprii contra regelui singur, fără să
schimbe ponderile, repertoriul, Basic/Advanced sau căutarea obișnuită H6.

`PawnPushProof` acceptă un plan câștigător numai după ce fiecare replică legală a regelui
advers are o continuare câștigătoare. Poate alege mutări de pioni și mutări de rege care reduc
strict un potențial de escortă. Avansul pionilor și capturile sunt ireversibile; potențialul
regelui împiedică ciclurile interne. Pozițiile anterioare ale partidei rămân interzise, iar
remiza revendicabilă la cincizeci de mutări respinge dovada. Dama trebuie să evite orice
captură legală imediată și să nu producă pat. Obiectivul terminal este dama sigură, nu un mat
inventat. Dovada lipsă sau întreruptă înseamnă necunoscut și păstrează decizia obișnuită.
[Implementarea dovezii](../../chess-core/src/main/java/ro/atf/chess/PawnPushProof.java)

| Observație | Rezultat calificat |
|---|---:|
| Poziția cerută | `a4`; H6 obișnuit plus dovadă separată de 23 de semimutări |
| Muncă în buget comun | 2.568 obișnuite + 14.325 în dovadă = 16.893 de noduri suplimentare |
| Conversia referinței | Remiză în ambele culori, câte opt semimutări |
| Conversia efectivă contra replicilor libere Stockfish H19 | Mat în 37 de semimutări cu alb și 31 cu negru |
| Pierderi WDL native la H17/H19 | Zero pe ambele trasee ale candidatului |
| Poziții tematice neatinse | 24 de poziții, două alegeri schimbate, zero regresii WDL la rădăcină |
| Catalogul canonic existent | Toate cele 2.303 de decizii, scoruri, costuri și PV-uri păstrate; 1.185/1.238 complete |
| Capitolele de mat existente | Toate cele 1.000 păstrează fiecare ramură adversă legală |

`SearchReport.depth` rămâne orizontul obișnuit încheiat. Câmpul opțional `pawnProof` păstrează
separat lungimea planului, nodurile, limita cache-ului, contorul mutărilor fără progres și
amprentele istoricului și strategiei câștigătoare. Nu afirmă că fiecare rădăcină a fost căutată
până la H23. Dovada împarte alocația adaptivă existentă de 100k→200k noduri suplimentare și
capacitatea cache-ului; H3 rămâne separat nebugetat. La maximul diagnostic de 5k, dovada
întreruptă păstrează `Kg7`/H6 și nu publică certificat.
[Componentele dovezii și cazurile negative](../../atf-tests/src/test/java/ro/atf/chess/PawnPushProofTest.java)

Auditul legat de sursă înregistrează 84 de semimutări efective pentru referință/candidat și
ambele culori, cu 313 interogări de oracol cu cache gol. Șase interogări H17/H19 noi confirmă
martorul păstrat: `training-051` alege încă `Ke6` de remiză, nu `Rc8+` câștigător. Cele 79 de
ratări didactice în 53 de probleme rămân deschise. Două conversii în oglindă sau rădăcinile
de transfer nu stabilesc un câștig Elo. Conversia completă pe loturi independente și domeniile
materiale mai largi rămân lucrări separate.

Trec cele 128 de verificări țintite de componentă/raport, benchmarkul nativ complet de mat,
verificările finale ale cititorului și patru verificări de dovezi brute. Decodarea JSON nativă
este comună catalogului și `HardQuestAudit`; recepțiile vechi fără dovadă rămân citibile.
PV-urile native mai lungi cer certificatul păstrat, iar rejucarea respinge costuri, istoric,
acoperire adversă, strategie sau evaluare substituite.
[Contractul repetabil al auditului](../testing/hard-puzzle-study.md#auditarea-certificatelor-planurilor-câștigătoare)

Dovezile și lecțiile reutilizabile sunt în `C:\work\sah\studii\2026-11-18-j7-hard-promotion-conversion`:
Extent, PGN-uri până la rezultat efectiv, recepții brute de oracol/native legate de sursă și
YAML structural. Configurația runtime nu conține FEN-uri, chei didactice, scoruri de oracol
sau lookup-uri de mutări învățate.
