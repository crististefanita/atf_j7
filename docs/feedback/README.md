# Feedback · Chess Lab

## Pe scurt

Registrul păstrează lacunele și criteriile deschise. [Ghidul tablei](../board/README.md)
descrie comportamentul, iar [catalogul edițiilor](../../release/README.md) indică dovezile publicate.

[Revizuirea mutărilor între suite](../testing/README.md#schimbarea-testelor-față-de-o-bază-git) descrie baza Git și dovezile de acoperire.

- [Forță nativă și comparații indiciu/analiză](#pierderi-native-din-partide-încheiate-și-martori-de-frontieră)
- [Transferul ordonării învățate încă necesar](hard-learning.md)
- [Paritate APK/Web și dovezi randate](web-parity.md)

<details>
<summary>Registrul priorităților și criteriile de închidere</summary>

| Prioritate | Zonă | Observație | Criteriu de închidere |
|---|---|---|---|
| P1 | Nimzo · alternativele publice ale indiciului | După `1.d4 Nf6 2.c4 e6 3.Nc3`, a doua variantă este `…Be7`, cu pierdere 249,5‰ la H17/H19. Partida cu hint maxim are sursă exactă și ajunge la mat la semimutarea 63. | Recalculează toate rădăcinile oferite și replicile adverse libere în politica declarată, păstrează teoria verificată și sacrificiile corecte și treci scopul nativ de calitate; componentele de integritate singure nu pot închide defectul. [Martor](#martorul-public-al-indiciului-nimzo) |
| P2 | Web · portabilitatea dovezii | Compilarea TeaVM curentă ajunge la furnizorii de securitate JRE prin `PawnPushProof.hash`; eșuează la `Class.getModule` / `ClassLoader.loadClass`. Clasa dovezii este identică reperului live `b51e17e`. | Asigură o implementare portabilă cu digest identic ori o substituție suportată la build, cu vectori cunoscuți și paritate JVM/Web; nu modifica semantica dovezii și nu numi compilarea Java singură un PASS Web executabil. |
| P1 | Scoțiană · șah câștigător și mat advers | La mutarea 20, indiciul compară `…dxc3` și `…gxf6`, omite `…Re8+` și pierde 314/348,5‰. Mai târziu în PV, `Bxb2` înlocuiește matul alb în trei prin `Bxh7+`. | Readmite rădăcina câștigătoare și caută replicile adverse libere, inclusiv șahurile liniștite ulterioare; păstrează sacrificiul corect de nebun, gărzile rădăcinilor și limitele negative de lucru. Scopurile de rădăcină în indiciu, analiză și continuare rămân FAIL. [Martori](#scoțiană-șah-câștigător-și-mat-advers) |
| P1 | Comparație indiciu · PV diagnostic complet | Linia publică reparată din Spaniolă nu are pierdere mare, dar comparația respinsă `Ng5 …O-O Nc3` păstrează o mutare slabă de 346/378,5‰. | Auditează continuările comparative alături de indiciile oferite; repară linia cu replici libere și păstrează benchmarkul comparației FAIL până la calificarea ambelor orizonturi. [Mecanism](../board/ai-quality.md#oferte-adverse-liniștite-în-interiorul-indiciului) |
| P1 | Indiciu · continuarea adversă completă | A doua variantă nouă după `1.e4` conține `Bxe6` slab; jocul advers liber preferă `Nc3`, cu pierdere 382/399‰ la H17/H19. | Repară continuarea completă evaluată cu 200k lucru obișnuit și rezerva finită de dovadă taxată separat, verifică fiecare semimutare PV pe familii independente și păstrează sacrificiile corecte. [Martor](#replici-adverse-slabe-în-indiciile-publice) |
| P1 | Release · identitatea guvernanței | Identitatea XML este verificată după nume și verdict. | Respinge recepțiile PASS din alt commit sau din altă rulare. |
| P2 | Release · arhiva probei compacte | Dovada compactă declarată este verificată; geometria modificată încă nu o impune la promovare. | Respinge omiterea dovezii obligatorii, păstrând controalele fazelor, rulării și capturilor substituite. |
| P2 | Documentație · protejarea traducerilor | Poarta bilingvă verifică structura și linkurile, iar proza și exemplele cer în continuare revizuire semantică. | Detectează proza copiată și exemplele schimbate prin cazuri negative țintite, păstrând literalii tehnici și dovezile imuabile. |
| P1 | Game Review · clasificarea mutărilor | Ultimul corpus cu 106 semimutări are 11 etichete native de eroare pe pierderi sub 50‰ la ambele adâncimi și o pierdere mare omisă. | Izolează modelul de scor și efectele orizontului fără istoric schimbat, Elo inventat sau scoruri ale indiciului puse în locul analizei. |
| P1 | Game Review · liniile recomandate | Zece din 106 recomandări proaspete pierd cel puțin 150‰ la H17/H19; ambele ramuri ale analizei sunt auditate integral. | Repară comparațiile complete rădăcină/replică la același orizont și păstrează mutările efective bune și istoricul din cache. [Cazuri măsurate](#pierderi-native-din-partide-încheiate-și-martori-de-frontieră) |
| P2 | Game Review · NVDA | Vorbirea RO repeta poziția și scorul. | Confirmă un singur anunț în RO și EN și revenirea focusului. |
| P1 | Italiană · schimbul neîncheiat nativ | Recaptura țintită repară perechea H6 izolată, dar decizia completă și martorii regresează. | Rezolvă decizia completă în 200k, păstrând 019/021/022 și transferul independent înainte de duel. |
| P1 | Indiciu · lider, alternative și replici adverse | Ultimele partide legate de sursă păstrează pierderi mari în 6/106 prime indicii, 11/143 variante și 14/140 prime replici adverse. | Repară continuările evaluate complete la orizonturi comune; toate cele 759 de semimutări ipotetice și recapturile legale de piese importante rămân evaluate independent. |
| P2 | Audit indicii · transfer mai larg | Încă două partide ajung la mat, cu 106 perechi native/indiciu pre-move, incluzând martorul sacrificiului corect. | Adaugă ascendențe de deschidere excluse și partide efective cu candidatul; rejucarea traseelor inspectate nu dovedește familii noi sau Elo. |
| P2 | Probleme · focus TalkBack în opțiuni | Opțiunile problemelor mai cer dovada focusului. | Înregistrează traseul focusului în ambele limbi. |
| P2 | Android · gestul predictiv | Gestul predictiv mai cere un dispozitiv compatibil. | Verifică anularea și reluarea pe API 33+. |
| P1 | Hard nativ · pierderi în jocuri încheiate | `g4` din Scandinavă este protejat de noua regresie `Ne4`; Franceza `Nc4` este acum protejată de dovada recapturii la H6 complet; Engleza `…Qf6` și Gambitul Damei `…Qa5` rămân. | Compară replicile libere complete la un orizont comun; păstrează catalogul, sacrificiile corecte și Basic/Advanced înainte de promovare. [Pașii următori](#pierderi-native-din-partide-încheiate-și-martori-de-frontieră) |
| P2 | Frontieră tactică · șah la limită | O frontieră în șah poate încă să se oprească fără evadare legală; extensia generală pierde 13 contracte existente. | Rezolvă cauza precisă în aceeași alocație taxată; păstrează eșecurile benchmark explicite până trec calitatea și transferul. |
| P1 | Coaching · dovada recapturii ascunse | Verificarea forțată din motor și recepția lucrului sunt implementate; previzualizarea încă arată linia integrală evaluată. | Proiectează prefixul vizibil mai scurt păstrând dovada integrală, recomandarea comună și identitatea după restart. Nu ascunde replici decisive. [Contract](#dovada-tactică-și-liniile-afișate) |
| P1 | Calibrarea profilurilor AI | Intervalele Elo încă se suprapun. | Adaugă jocuri pereche pentru a restrânge incertitudinea. |
| P1 | Forța Hard adaptiv | Căutarea pornește cu 100.000 de noduri și o rezervă până la 200.000. | Compară cele trei moduri pe partide excluse din ajustare și verifică replica adversă. |
| P1 | Învățarea Hard din PGN-uri | Modelul de ordonare antrenat pe pierderi păstrează toate mutările finale în 36 de familii noi. | Adaugă mutări bune păstrate și sacrificii, apoi verifică ordonarea și deciziile complete. |
| P1 | Căutarea Hard · generalizare | Recaptura selectivă pierde pe deschideri independente. | Cere progres independent la bugetul fix. |
| P1 | Hard · conversia după captura de pion | Avantajul după captură poate fi pierdut ulterior. | Verifică păstrarea avantajului pe poziții și replici noi. |
| P1 | Transferul tactică → partidă | Hard ratează tactici fără obiectiv declarat. | Măsoară detectarea și păstrează contractul rezolvării cu obiectiv. |
| P1 | Hard nativ · obiective materiale rămase | Rămân 79 de etape canonice neacceptate în 53 de probleme; `training-051` încă remizează cu `Ke6`. | Rezolvă `Rc8+` față de `Ke6` cu replici adverse libere, păstrând conversia calificată cu pioni, remizele apărate și catalogul complet. |
| P2 | Studii native · contorul FEN inițial | O partidă nouă din tablă pornește contorul fără progres la zero, iar metadatele FEN ale oracolului păstrează valoarea inițială. | Adaugă inițializare nativă explicită din contorul FEN și cazuri 0/99/149 înainte de a declara paritate de remiză pentru origini compuse arbitrare. |
| P2 | Progres · upgrade Android | Upgrade-ul semnat mai cere o probă de progres. | Păstrează ID-urile, rezolvările și punctele după upgrade și restart. |
| P2 | Calitatea AI · evaluare material | Scorul material diferă de Stockfish. | Redu abaterea la aceleași bugete. |
| P2 | Calibrarea problemelor | Benzile de dificultate importate se suprapun. | Verifică timpul și încercările pe un lot amestecat separat. |
| P2 | Android · separarea tranzițiilor de ecran | Ecranul Android deține mai multe tranziții de sesiune. | Mută tranzițiile într-un responsabil testabil pe JVM. |

</details>

## Replici adverse slabe în indiciile publice

Jurnalul nou cu hint maxim `learning-max-hint-20261118`, producător
`8d564655b554d817fea4e3a04723a538ae5f3864`, înregistrează fiecare mutare efectivă
alb/negru înaintea executării. După `1.e4`, a doua variantă include
`…Nf6 Qf3 …Nc6 Bc4 …e6 Bxe6 …dxe6 Qxf6`. La faza albului `Bxe6`, căutările
independente libere preferă `Nc3`: WDL așteptat este 382‰/399‰ la H17/H19 față de
0‰/0‰ cu `Bxe6` impus. Prefixul ipotetic complet, UCI brut și ambele adâncimi
rămân în `C:\work\sah\.studii\corpus\2026-11-18-v1\deep-oracle`.
Poziția repetată în trei trasee este un singur context, nu trei familii.

Aceasta confirmă o continuare adversă slabă, fără a fi neapărat poziția exactă
nespecificată de utilizator. Indiciul public a fost cerut efectiv la 200k.
Integritatea trece; calitatea absolută eșuează. Nu închide problema prin adăugarea
unei recapturi neevaluate, ascunderea variantei secundare sau interzicerea
sacrificiilor. Compară rădăcinile complete și replicile adverse libere la orizonturi
native comune în același buget total. Păstrează `…Qg3 hxg3 …Rh1#` și martorii din
Italiană/Caro/Siciliană. Marchează onest un orizont nedovedit; un PV legal nu devine
optim doar pentru că bilanțul material corespunde.

Folosește [contractul de admitere](../testing/learning-archive.md) pentru partidele
viitoare. Partidele istorice fără admiterea actuală se retrag. Fixture-urile sursă,
dovezile edițiilor, definițiile problemelor deschise și referințele originale ale
oracolului sunt separate de corpusul activ de învățare.

## Calibrarea analizei complete pe corpusul admis

Toate cele cinci partide păstrează acum `review.json`, `analysis.json` lizibil și
un README propriu. Analiza adăugată are producătorul separat
`87ccb91bb169ed95ed7c0dc4b3e58ab67ce7e6df`; jurnalele Hard/hint originale rămân
identice la nivel de octet. Sunt 76 de mutări analizate, 152 de ramuri jucate/recomandate
și 745 de semimutări ipotetice ale analizei. Oracolul adaugă 854 de cereri reci
H17/H19 și referă 1.090 de cereri originale cu context identic, fără a le reeticheta
rularea. Analiza nativă păstrează plafonul configurat efectiv de 85k, separat de
indiciul public original de 200k.

Nouă recomandări au pierdere mare stabilă față de oracol. În Caro–Kann, analiza
preferă `…Qa5` în locul mutării corecte jucate `…d5`, la mutarea a patra, cu
pierdere 299/318‰. După `Nc3`, recomandă `…e5` în locul mutării jucate `…dxe4`,
cu pierdere 472/448,5‰. În conversia de pioni, alternativa analizei pierde
500/500‰, iar `a4` jucat păstrează câștigul. Sunt rezultate complete ale analizei
înregistrate, nu scoruri de rădăcini alese manual. Păstrează acest P1 deschis până
când analiza efectivă repară recomandările și transferă independent. Nu eticheta
toate recomandările drept optime doar fiindcă alegerea Hard originală era bună.

Cele 118 capete de hint conțin nouă piese importante expuse legal, dar numai două
combină expunerea cu pierdere mare stabilă a ultimei mutări. În Caro–Kann,
`…Qxd4` ia un pion, permite `Qxd4` și pierde 518,5/513‰; după recaptură, ultimul
jucător care mutase are scor așteptat 0‰ la ambele adâncimi. `…Nh6` permite
`Bxh6` și pierde 308,5/290‰. Păstrează schimburile care nu pierd WDL și martorul
corect `…Qxg3+ hxg3 …Rh1#`, care reține 1.000‰ după captura pionului.
Capetele complete și toate recapturile legale ale pieselor importante rămân lângă
analiză în `C:\work\sah\.studii\corpus\2026-11-18-v1\postgame-audit`.

Integritatea, calitatea capetelor expuse și calitatea recomandărilor analizei au
verdicte Extent separate. Prima trece; ultimele două eșuează. Ratingurile locale
ale analizei nu sunt Elo măsurat, iar cele trei trasee plafonate nu au rating.
[Contract și componente](../testing/learning-archive.md#analiza-finală-și-capetele-indiciilor)

## Constatările partidelor complete păstrate ca ținte de componentă

Cele două partide complete (Gambitul Damei și Franceza) și martorul de mat aparțin
producătorului `242d6fe21d6fbe625f628cb3f677f9618f588674`: 130 de perechi Hard/indiciu
maxim înaintea mutării și analize native finale complete. Cele 1.001 semimutări
ipotetice ale indiciilor au 70 de pierderi mari stabile (65 de cereri constrânse
distincte), inclusiv 31 de replici adverse. Șase rădăcini oferite și cinci alegeri
native înregistrate pierd minimum 150‰ la ambele adâncimi. Sunt poziții de
diagnostic, nu o ligă de rating sau reguli învățate.

| Următoarea țintă nativă | Defectul curent | Închidere prin componente |
|---|---|---|
| `queens-gambit-21` · `11…Qa5` | Pierdere 225,5/219,5‰; Stockfish preferă `…a5`; Hard încheie H5 cu 100k noduri suplimentare. | Compară `…Qa5 Bd3` și `…a5` cu replici libere la același orizont nativ; treci martorul permanent și familiile independente. |
| `french-20` · `11.Bb5+` | Pierdere 475,5/453‰; `…c6` infirmă linia; Stockfish preferă `Nc4`; Hard încheie H5 cu 200k noduri suplimentare. | Prioritizează pierderea mai mare, izolează `…c6` și schimbul ulterior, apoi păstrează martorii lanțului de dame și catalogul complet. |

ID-urile folosesc semimutarea originală înaintea jocului, numerotată de la zero.
Micul fixture din surse și `HardLearningWitnessTest` păstrează istoricul legal,
toate cele opt faze brute ale oracolului și negativele de substituire fără
instalare Stockfish. Cele două ținte `HardLearningWitnessBenchmarkTest`
recalculează alegerea nativă curentă; rămân FAIL până la corectare, iar o alternativă
nouă cere dovezi noi de oracol. Nu le închide schimbând mutarea greșită așteptată
sau prin căutare FEN în timpul jocului. Verdictele generale de calitate pentru
nativ, indiciu, capăt expus și analiză rămân separate de integritate și de suita
obișnuită de regresie a ediției.

Analiza completă măsoară în plus șase recomandări greșite, douăsprezece capete
publice expuse greșite și șaisprezece etichete native de eroare cu pierdere mică în
oracol. Acestea sunt rezultate ale producătorului original, nu afirmații că fixurile
ulterioare ale indiciilor ar fi fost jucate retroactiv. Păstrează observațiile cu
sursa lor; comparațiile indiciilor din sursa curentă sunt rejucări explicite cu
identitățile originale ale profesorului, niciodată jurnale istorice inventate.
[Admitere și reproducere reutilizabile](../testing/learning-archive.md)

## Dovezi și verificări de închidere

<details>
<summary>P1 · Release · identitatea guvernanței</summary>

Preflight leagă recepția pentru schimbarea testelor de Git, iar XML-urile Documentation, Release și Extent sunt verificate după nume și verdict.

Leagă fiecare XML de commitul candidat și de rularea de calificare. Înlocuiește pe rând un XML cu o recepție PASS din alt commit sau din altă rulare și verifică refuzul, păstrând recepția Git curentă. [Preflight](../../release-tools/src/main/java/ro/atf/chess/release/ReleaseGate.java) · [Teste](../../atf-tests/src/test/java/ro/atf/chess/ReleaseGovernanceTest.java)

</details>

<details>
<summary>P2 · Release · arhiva probei compacte</summary>

Verificatorul arhivei controlează deja fazele compacte declarate, identitatea execuției și amprentele capturilor. Lacuna rămasă este selectarea dovezii obligatorii din schimbare: `ReleaseGate qualification` cere componente și Game Review, fără să impună singur recepția compactă pentru o modificare de geometrie.

Respingeți omiterea dovezii compacte obligatorii înaintea promovării, păstrând verificările existente pentru fază lipsă, execuție străină și captură înlocuită. Recepțiile istorice rămân neschimbate. [Procedura promovării](../release/README.md#calificare-și-publicare) · [Verificatorul arhivei](../../atf-tests/src/test/java/ro/atf/chess/ArchivedReleaseEvidence.java)

</details>

<details>
<summary>P1 · Game Review · clasificarea mutărilor</summary>

Apelul efectiv `GameReview.analyzeLine` păstrează toate cele 60 de mutări și nu emite rating pentru cele trei studii limitate. Totuși, `6.e6` din Caro–Kann are eticheta `BEST`, iar precizia albului este 100, deși rădăcina jucată pierde 533‰ la H17 și 581,5‰ la H19. `5.Nc3` din Siciliană are eticheta `BLUNDER`, cu 0‰ pierdere la ambele orizonturi. În cele trei partide, trei etichete pozitive însoțesc o pierdere mare la oracol, iar șase etichete severe însoțesc sub 10‰ pierdere la ambele adâncimi.

Îngheață martorii cu istoric complet înainte de corecția comparației comune. Verifică rădăcinile jucată și recomandată la același orizont complet, păstrează redarea legală și marcajele din istoric și măsoară familii noi. Pierderea logistică locală și WDL Stockfish sunt modele distincte; nu lega producția de Stockfish și nu copia mecanic eticheta sa. Auditul curent evaluează rădăcina jucată și verifică legalitatea PV-urilor salvate; nu certifică independent fiecare alternativă a analizei. [Contractul analizei și limitarea](../board/game-review.md#limitările-măsurate-ale-analizei) · [Audit pereche](../testing/hard-hint-study.md)

</details>

<details>
<summary>P1 · Italiană · schimbul neîncheiat din căutarea nativă</summary>

Prototipul de recaptură pe ultima casetă capturată schimbă ordonarea H6 izolată din `…Na5 −154 / …Qg6 −170 cp` în `−80 / −50 cp`. În frunze găsește `…Qxe5` și `…Nxe5`; ambele culori și probele cache rece, încălzit, dezactivat și cu fereastră îngustă păstrează rezultatul local. Fiecare rădăcină izolată are propriul buget; probele nu certifică o decizie completă în același buget cumulat.

Candidatul complet alege însă `…Bxf2+`, evaluat la −311 cp și 0‰ de Stockfish H19. Pierde și 478,5‰, 191,5‰ și 516‰ în martorii 019/021/022. `…cxd4` din Siciliană și `…d5` din Caro–Kann rămân neschimbate. Respinge prototipul, precum și extensiile anterioare uniformă și doar pentru damă. Păstrează contextul ultimei capturi în identitatea cache-ului; nu copia indiciul în joc și nu relaxa veto-ul de stabilitate.

Izolează de ce închiderea schimbului schimbă liderul complet și cei trei martori. Cere istoricul complet, replici adverse libere la același orizont și decizia nativă completă în 200k suplimentare înainte de transfer neinspectat și duel pereche. Indiciul public S5 corectat rămâne `…Qg6`; Hard nativ rămâne `…Na5`. Motorul curent este neschimbat, deci nu există ΔElo nou al candidatului. [Studiul măsurat](../board/ai-quality.md#studiul-pereche-nativindiciu-și-respingerea-recapturii) · [Contractul auditului](../testing/hard-hint-study.md)

</details>

<details>
<summary>P1 · Indiciu · lider, alternative și replici adverse</summary>

Regresiile primului indiciu din Italiană și Caro–Kann rămân închise. Defectul liderului sicilian `…Nc6/…Nbd7` după `7.f4` este de asemenea corectat la H5 complet și 200k; egalitatea brută −21/−21 este păstrată. Mecanismul și domeniile generale respinse aparțin [ghidului AI](../board/ai-quality.md#recaptura-damei-și-continuitatea-indiciului-italian), cu teste de componentă permanente. Alte poziții cu presiune asupra damei rămân deficitare: `validation-039`, poziție distinctă cu istoric complet după `…Qb6`, păstrează pierderea anterioară de 462‰ a primului indiciu.

Rejucarea pereche de șaizeci de faze încă eșuează la calitatea absolută: 8 prime indicii, 11 mutări oferite și 9 prime replici adverse din PV depășesc 150‰. Indiciul sicilian corectat oferă doar `…Nbd7` și pierde 33‰ la H19, față de 358‰ înainte; oracolul liber preferă `…Qb6`, iar teoria nativă încă furnizează `…Be7` corect. Modelul tot nu diferențiază numeric cele două rădăcini de cal. Acoperirea candidaților este acum implementată și guvernată: toate patru rădăcinile sunt comparate la H5/200k. Repară diferențierea numerică și replicile adverse fără să promovezi un schimb oprit înaintea recapturii. [Măsurători](../board/ai-quality.md#acoperirea-celor-patru-rădăcini-în-siciliană) `N1c3` anterior încă oferă `Bg5` inferior. [Mecanism și prototipuri respinse](../board/ai-quality.md#egalități-exacte-în-siciliană-și-apărarea-piesei-legate) Cazul de transfer cu pion forțant încă oferă `Bxe4` la 243,5‰. Noua istorie `validation-057` îmbunătățește liderul, dar păstrează `Bg5` inferior ca ofertă secundară la 339,5‰. Prioritizează prima divergență la orizont comun și replica adversă liberă; diferența mică dintre scorurile native nu dovedește mutări echivalente.

Păstrează martorii Caro și Italiană închiși, sacrificiile corecte, negativele cache-ului exact, plafonul total 200k al indiciului și mutările istorice intacte. Rezolvă ofertele secundare și replicile PV deficitare înainte de a pretinde explicații scolastice complete. Evaluează separat liderii, fiecare rădăcină oferită și replicile calculate la H17/H19. Corecția indiciului nu certifică jocul Hard nativ, bara sau clasificările Game Review. [Audit și publicare](../testing/hard-hint-study.md)

</details>

<details>
<summary>P2 · Audit indicii · transfer mai larg</summary>

Runda curentă păstrează toate cele șaizeci și patru de istorii geometrice înghețate. Capturează 100 de faze native noi, inclusiv cele două ramuri noi cu damă/nebun, și validează 308 interogări brute noi plus 958 de proiecții pe interogări originale exacte. Perechile liber/forțat cer aceeași identitate de oracol; integritatea cererilor nu permite combinarea a doi producători. Păstrează identitățile originale și legătura exactă a cererilor; nu reeticheta dovezi și nu număra prefixele comune ca partide independente.

Colecția italiană anterioară și domeniul respins al presiunii asupra damei rămân dovezi istorice în ghidul AI. Redarea legală și integritatea recepțiilor nu implică PASS la calitate șahistă.

În acea colecție anterioară, șaisprezece istorii inspectate cu presiune asupra damei au expus domeniul general respins și au devenit martori de dezvoltare. Șaisprezece istorii de deschidere înghețate ulterior păstrează o îmbunătățire de 322,5‰ a liderului și o scădere de 13,5‰, fără pierdere nouă de lider peste 150‰. Lotul de dezvoltare păstrează separat o scădere de 18,5‰. Nu regla pe aceste observații ca apoi să le declari neinspectate. Extinde către familii neatinse cu oferte secundare greșite și etichete înșelătoare ale analizei; păstrează eșecurile, sursa și istoriile exacte. Studiile plafonate cu prefixe comune nu măsoară Elo. [Audit pereche](../testing/hard-hint-study.md)

</details>

<details>
<summary>P2 · Probleme · focus TalkBack în opțiuni</summary>

Sanity-ul verifică deschiderea, comenzile și păstrarea progresului în toate cele șase capitole.

Cu TalkBack activ, verificați focusul pe **Întoarce tabla**, glisarea la **Meniu principal** și revenirea la butonul din antet după **Înapoi**. Înregistrați evenimentele de focus în RO și EN pe API 28 și pe un profil cu navigare gestuală. [Proba TalkBack](../testing/README.md#proba-talkback)

</details>

<details>
<summary>P2 · Android · gestul predictiv</summary>

Lint validează protecția API, iar APK-ul 0.13.0 păstrează partida Hard la comanda Înapoi pe API 28.

Verificați gestul predictiv în joc și Deschideri pe API 33+, inclusiv anularea gestului. [Sanity Android](../testing/README.md#sanity-android)

</details>

<details>
<summary>P1 · Calibrarea profilurilor AI</summary>

Basic a estimat 1067 Elo (638–1225) după 144 de partide cu Stockfish 19. Advanced la 10.000 de noduri a estimat 1421 (1245–1553), iar Hard la 44.000 a estimat 1534 (1396–1672) în același protocol.

Adăugați perechi noi cu ambele culori pentru a restrânge incertitudinea în jurul limitei Basic de 1200 și a benzii Advanced. Recalibrați valorile din aplicație cu sursă fixată și dovezi guvernate. [Calibrarea Basic](../board/ai-quality.md#calibrarea-basic-cu-adâncime-selectivă)

</details>

<details>
<summary>P1 · Căutarea Hard · generalizare</summary>

Recaptura selectivă îmbunătățește Tactica, dar pierderea medie pe deschideri independente urcă de la 53,0‰ la 68,0‰. Prototipul cache cu limite pierde o problemă de mat în trei, iar intervalul duelului include zero.

În benchmarkul Hard, includeți Scandinavă după `4.d4` și Siciliană clasică după `6.Bg5`. Cereți progres pe lot independent și în duel cu culori inversate, cu profilul fix sau adaptiv declarat. Păstrați corectitudinea matului în trei și înregistrați nodurile efectiv consumate. [Recaptură](../board/ai-quality.md#recaptura-hard-la-orizont) · [Cache experimental](../board/ai-quality.md#prototip-local-de-limite-alpha-beta--neintegrat)

</details>

<details>
<summary>P1 · Hard · conversia după captura de pion</summary>

Hard găsește acum `Nxe5` în Siciliana cu `…h6` și în poziții vecine, la 44.000 de noduri. În continuarea cu `…Nc6 Nxc6`, împingerea `e5` pierde 211‰ față de `d3` la adâncimea 17. În lotul tematic final, B a încheiat cu 3 victorii, iar A cu 5.

Verificați alegerea `d3` pe poziții noi și după replici adverse distincte, apoi urmăriți păstrarea avantajului în jocuri pereche cu același buget. Păstrați testele de captură și martorii cu pion apărat. [Dovezi](../board/ai-quality.md#verificarea-schimburilor-după-o-captură-de-pion)

</details>

<details>
<summary>P1 · Transferul tactică → partidă</summary>

Cu obiectiv declarat, solverul rezolvă 1.220/1.220 publice și 24/24 independente. La obiectiv necunoscut, Hard ratează încă șase maturi în trei și chei materiale. Oprirea la mat dovedit a redus costul mediu pe Mat în trei cu 33%.

Măsurați ce tactici pot fi detectate din poziție și păstrați 100% în solver. Pentru adversar, cereți progres pe lotul independent cu aceleași bugete și absența regresiilor pe jocuri pereche. [Dovezi și duel](../board/ai-quality.md#rezolvarea-quest-urilor-cu-obiectiv-declarat)

</details>

<details>
<summary>P2 · Progres · upgrade Android</summary>

Restartul procesului pentru Antrenament tactic este demonstrat în 0.12.0, iar extinderea catalogului păstrează cursorul și punctajul în componentă. Lipsește proba de update cu APK-ul semnat și date deja salvate, pe două ediții cu aceeași identitate.

Instalați ediția publicată pe un emulator curat, creați progres, instalați candidatul compatibil peste ea și verificați după restart ID-ul, rezolvările și punctele fără dublare. [Proba de upgrade](../testing/README.md#upgrade-al-progresului)

</details>

<details>
<summary>P2 · Calitatea AI · evaluare material</summary>

După `2…dxc4`, Chess afișează `−0,60`, iar Stockfish 19 la adâncimea 13 indică `+0,21`; după `3.e3`, abaterea ajunge la 111 cp.

Urmăriți recuperarea pionului pe ambele culori și cu ramuri de control. Reduceți abaterea fără să depășiți bugetul barei sau să slăbiți porțile. [Măsurători](../board/ai-quality.md#calibrarea-hard-și-a-barei)

</details>

<details>
<summary>P2 · Calibrarea problemelor</summary>

În Mat în 1, medianele ratingului importat urcă pe cele cinci segmente, cu suprapuneri între segmente.

Măsurați timpul și încercările greșite înaintea indiciului pe poziții amestecate, separat pentru pozițiile pornite din șah. Validați ponderile YAML pe un lot separat și păstrați ID-urile. [Calibrare](../board/quest-curation.md#calibrarea-dificultății)

</details>

<details>
<summary>P2 · Android · separarea tranzițiilor de ecran</summary>

`ChessActivity` deține meniurile, cursorul, salvarea și programarea replicilor. O modificare în reluarea partidei atinge aceeași clasă care orchestrează exercițiile și navigarea.

Extrageți întâi tranzițiile reluare, anulare și rezultat AI întârziat într-un owner testabil JVM. Păstrați componentele pentru restaurare și respingerea replicii vechi; pe Android verificați o singură reluare și ordinea vizibilă. [Partidă și istoric](../testing/README.md#partidă-și-istoric)

</details>

<details>
<summary>P1 · Forța Hard adaptiv</summary>

Căutarea pentru mutarea curentă folosește o alocație configurabilă de 100.000 de noduri, cu o rezervă până la 200.000. Continuarea păstrează stiva activă și cache-ul. Testele de componentă protejează verificarea existentă a schimbului.

Comparația istorică v1 păstrează `Bg5` nativ în S7 sicilian (24‰), față de `N1c3` din indiciu (499,5‰). Indiciul italian este între timp reparat la `…Qg6`; vechea recomandare `…Bxf2+` nu mai descrie sursa live. Compară în continuare API-urile independent, fără a copia automat selecția indiciului în Hard.

Măsurați 100.000 fix, 200.000 fix și modul adaptiv pe aceeași sursă, în partide excluse din ajustare și în poziții cu pierderi verificate de Stockfish. Păstrați identitatea sursei pentru liga ordonării învățate și verificați alternativele legale liniștite, matul și finalurile de remiză. [Contractul adaptiv](../board/ai-quality.md#buget-adaptiv-în-prim-plan)

</details>

<details>
<summary>P1 · Învățarea Hard din PGN-uri</summary>

Modelul de ordonare antrenat pe 58 de pierderi schimbă 34/36 de liste de rădăcini excluse din ajustare și păstrează toate mutările finale. Verificarea cronologică identifică o pierdere mai devreme într-una dintre cele 36 de partide.

Includeți în următorul lot de antrenare mutări bune păstrate, replici liniștite și sacrificii verificate. Cereți îmbunătățirea rangului alternativei și schimbarea deciziilor complete în familii excluse din ajustare înainte de interpretarea Elo pereche. [Cercetarea offline](hard-learning.md)

</details>

<details>
<summary>P2 · Documentație · protejarea traducerilor</summary>

Poarta bilingvă verifică inventarul documentelor, titlurile localizate, blocurile structurale, destinațiile linkurilor și dovezile arhivate. Nu inspectează sensul paragrafelor sau conținutul exemplelor delimitate. Un paragraf englezesc copiat sub titluri românești sau o comandă schimbată în același bloc poate trece verificările structurale. Traducerile revizuite cer în continuare verificarea semantică explicită din [procesul bilingv](../release/README.md#documentație-bilingvă).

Adaugă cazuri negative țintite pentru proză copiată, titluri de secțiuni colapsabile, etichete de link și etichete umane din diagrame, precum și comenzi sau valori măsurate modificate. Păstrează identificatorii, etichetele UI citate, SAN/FEN/UCI, sintaxa comenzilor și dovezile imuabile de execuție. Compararea propozițiilor identice poate semnala omisiuni; nu certifică singură limba sau corectitudinea unei propoziții reformulate. [Controalele bilingve actuale](../../atf-tests/src/test/java/ro/atf/chess/DocumentationPairGovernanceTest.java)

</details>

<details>
<summary>P1 · Hard fără obiectiv declarat · obiective materiale și primele pierderi WDL</summary>

Păstrează deschise 79 de etape neacceptate în 53 de probleme materiale: 56 au pierdere mare
la ambele orizonturi Stockfish, 22 păstrează victoria, iar una e mai mică/dependentă de adâncime.
Cele 42 de prime ratări didactice cu pierdere mare confirmată preced etapele condiționate
ulterioare. Verifică și prefixele acceptate înainte de a declara prima pierdere cronologică.

**Următorul martor compact: `training-051`.** Hard obișnuit alege încă `Ke6`, nu `Rc8+`;
șase interogări H17/H19 noi confirmă o pierdere de 500‰, victorie→remiză. Compară mutarea de
turn și replicile adverse libere la orizonturi comune, în alocația efectivă. Noua dovadă de
pioni nu se aplică atunci când adversarul are turn; nu extinde lema ei de siguranță la acel
domeniu. Păstrează `training-021/065` și porțile native pentru catalogul complet și maturi.

Conversia `a4` după promovare este închisă printr-un certificat de plan câștigător legat de
sursă și maturi efective în ambele culori. Mecanismul, partidele de 37/31 de semimutări,
cazurile negative și limitele aparțin [ghidului AI](../board/ai-quality.md#conversia-limitată-a-finalurilor-de-pioni).
Cele 24 de poziții tematice noi stabilesc non-regresie WDL la rădăcină, nu conversie completă
sau câștig Elo. Joacă alte familii excluse până la rezultat efectiv, inclusiv remize apărate,
înainte să extinzi domeniul material sau să promovezi o afirmație de forță. Păstrează PGN-urile
complete și primele pierderi verificate; nu număra exemplele în oglindă ca partide Elo independente.

Cele 79 de lecții contextuale originale păstrează producătorul de oracol după verificarea
egalității integrale a deciziilor native. YAML-ul structural nou descrie tempoul pionilor,
escorta regelui, repetiția și promovarea sigură cu contraexemple; runtime nu conține chei
didactice sau lookup FEN.
[Procedura repetabilă pentru certificate și recepții](../testing/hard-puzzle-study.md#auditarea-certificatelor-planurilor-câștigătoare)

</details>


<details>
<summary>P2 · Studii native · contorul inițial FEN pentru remiză</summary>

`ChessMatch.fromPosition` primește tabla și pornește o partidă nouă cu contorul la zero;
`StudyFen` păstrează separat contoarele FEN pentru oracol. Nu poate stabili astfel paritate
nativ/oracol pentru regula celor cincizeci de mutări într-un FEN arbitrar. Cazul calificat
după promovare nu este afectat: ultima captură resetează contorul, iar `…Ke4` îl duce la unu
pe ambele trasee.

Responsabilitatea aparține adaptorului sesiunii compuse, nu ponderilor evaluării. Păstrează
explicit contorul inițial și testează table identice cu valorile 0/99/149, resetări prin
capturi/pioni, reluarea partidei și refuzul unei recepții din alt contor. Păstrează recepțiile
originale cu producătorul real; nu le modifica antetele pentru a fabrica paritate.
[Constructorul nativ](../../chess-core/src/main/java/ro/atf/chess/ChessMatch.java)
· [Helperul recepției FEN](../../atf-tests/src/test/java/ro/atf/chess/StudyFen.java)

</details>

## Pierderi native din partide încheiate și martori de frontieră

Lotul curent folosește producătorul `37c5f65`, nu consumatorul Native PVS ulterior.
Engleza se încheie în 51 de semimutări, iar Scandinava în 52, ambele pierdute de Hard
nativ contra Stockfish complet; martorul sacrificiului corect de damă se încheie în
trei. Toate cele 106 semimutări efective păstrează ambele căutări pre-move și analiza
nativă completă. Cele 4.104 cereri reci ale oracolului acoperă toate cele 759 de
semimutări ipotetice publice, fiecare ramură a analizei și recapturile pieselor
importante. Admiterea integrității nu șterge 77 de semimutări PV slabe, cinci capete
de indiciu cu expunere incorectă și zece recomandări greșite ale analizei.

Rejucarea Native PVS păstrează toate cele 2.303 rezultate canonice și cele 212 analize
complete Basic/Advanced. Repară `g4` din Scandinavă în `Ne4`, cu pierdere independentă
526,5/620 → 25,5/110,5‰. Cele 236 de cadre curente/anterioare comparate nu au o pierdere
mare nouă confirmată; al doilea câștig este o alegere Native de control pe o poziție
jucată efectiv de Stockfish. Nu este a doua partidă efectivă îmbunătățită. Două alegeri
franceze anterioare au și diferențe mici de scor, inclusiv o degradare de 2,5/4,5‰.
Lucrul mediu pe catalog crește cu aproximativ 8,1%; nu pretindem câștig de viteză,
adâncime sau Elo. [Metodă și limite](../board/ai-quality.md#verificarea-mutărilor-secundare-native)

Comparația franceză nativă este închisă prin [contractul recapturii complete](../board/ai-quality.md#închiderea-recapturii-de-pion-la-orizontul-complet). Defectele separate ale indiciului și analizei
sunt acum acoperite de componentele contrajocului liber; păstrează Engleza `…Qf6` față de `…Nc6` liber ca transfer. Închiderea uniformă de o semimutare câștigă 24 de etape,
dar pierde 40; repetarea ei sau simpla schimbare a răspunsului așteptat nu este o
reparație acceptabilă. Păstrează apărările libere la orizont comun, `training-021/051/065`,
toate capitolele de mat, recapturile restrânse de dame și sacrificiul corect de damă.
Testele frontierei în șah rămân explicit marcate benchmark. [Contractul de admitere](../testing/learning-archive.md#studii-de-continuare-până-la-rezultat)
explică identitățile originale și remizele automate față de cele revendicabile.
Dovezile rămân în `C:\work\sah\.studii\experiments\2026-11-19-hard-continuation`.


## Dovada tactică și liniile afișate

Cerut pe 19 noiembrie: replica finală de verificare poate rămâne în afara previzualizării
obișnuite când este necesară doar pentru a stabili rezultatul schimbului. Sunt necesare
două reprezentări explicite: prefixul vizibil și dovada tactică integrală evaluată.
Păstrează auditabile originea, istoricul integral, recaptura legală, scorul înainte/după,
orizontul obișnuit complet, lungimea dovezii, lucrul consumat și cauza opririi. Nu adăuga
o replică decorativă și nu înlocui scorul după afișare. Dovada lipsă sau întreruptă
rămâne necunoscută; nu poate certifica un câștig material.

O singură recaptură automată nu ajunge când adversarul are o apărare liniștită mai
puternică, mai multe recapturi legale sau un sacrificiu câștigător. Caută alternativele
legale; păstrează legăturile, en passant, promovarea, matul și sacrificiul corect de
damă. Martorul francez `Nxf5 …exf5` este exact o captură piesă–pion urmată de recaptură
de pion. Păstrează SAN-ul și ambele bilanțuri materiale; nu clasifica `Nxf5` drept
mutare liniștită.

Proprietatea dovezii în motor și componentele negative sunt implementate. Proiectează
prefixul vizibil fără să modifici dovada sau istoricul salvat.
Becul, destinația jucată și analiza finală trebuie să indice aceeași recomandare
evaluată. Mock-urile acoperă lucrul întrerupt, rulările străine și fazele lipsă;
deciziile șahistice păstrează dovada independentă Stockfish H17/H19. Separarea UI
rămâne deschisă până trec contractele dovezii și prezentării.

Rezerva din motor închide acum frontiera deschisă prin recapturi continue și evadări
obligatorii taxate, respingând rezultatele incomplete ale indiciului/analizei. Vezi
[contractul implementării](../board/ai-quality.md#frontiera-dinamică-de-coaching). Separă
lucrările rămase:

Cele două defecte franceze sunt reparate prin comparația taxată a contrajocului liber la
două orizonturi: `Nc4` înlocuiește `Bb5+` în ambele mecanisme, cu pierdere H17/H19 sub 50‰,
iar scopurile originale de calitate intră în suita normală de componente.
[Componentele contrajocului](../../atf-tests/src/test/java/ro/atf/chess/FrenchCoachingTest.java)
păstrează ambele culori, cele patru PV-uri evaluate, negativul nodului lipsă și compatibilitatea cache v1/v2/v3.

- Previzualizarea arată încă dovada integrală; prefixul vizibil mai scurt și sufixul de
  verificare accesibil separat cer teste proprii de prezentare și restart.
- Păstrează garda orizonturilor pare: prototipul fără gardă înlocuia `Nxd7` calificat
  independent cu `Bxe4`, crescând pierderea de la 43,5/8 la 335/243,5‰ la H17/H19.
  Garda finală trebuie să păstreze scorul închis efectiv, să taxeze confirmarea și
  să nu publice scor dacă aceasta se întrerupe.
- Cache-urile v1 rămân lizibile, fără recepția forțată nouă. Distinge analiza istorică
  de evaluatorul nou calificat când revizitezi rezultate vechi păstrate în cache.

Plafonul YAML de cinci milioane de noduri crește în pași de 25k. O ieșire normală din
teoria Italianei cere 1.037.401 noduri de rezervă; negativul cu buget mic trebuie să rămână
incomplet, fără restaurarea scorului inițial. Păstrează auditabile pragul de schimbare a
liderului de 25cp și verificarea orizontului par anterior. Inversarea siciliană `…Nc6` era
independent mai slabă decât `…Nbd7` la ambele adâncimi ale profesorului; o egalitate de scor
inventată nu este o corecție. Becul, destinația și comparația explicită folosesc aceeași
decizie calificată a dovezii.
Analiza păstrează și liderul la egalitate numerică exactă. Egalitatea `Nf3`/`e4` din Engleză
este +21 după închidere, fără vechiul +651. Păstrează cele șase faze ale profesorului și
negativul de componentă: ordinea lexicală producea singură o recomandare independent mai slabă.
Replay-ul calificării precedente pe 106 poziții activează rezerva în 44 de indicii și 66 de analize, fără
rezultat incomplet. Cele 43 de recomandări schimbate au 252 de faze H17/H19. Nicio pierdere
nouă nu atinge 150‰; maximele suplimentare sunt 73,5/54‰. Aceste degradări mici rămân
datorie de calibrare. Totalurile istorice nu califică un evaluator ulterior. Frontiera tactică
închisă nu certifică joc global optim și nu înlocuiește măsurarea Elo în partide complete noi.

Nu deduce Elo sau transfer independent larg din controalele forțate compuse. Partidele
noi complete cer în continuare hint maxim înaintea fiecărei mutări, analiză finală și
fazele profesorului rece sub producătorul real, cu recepția rezervei păstrată.

## Audit nou de deschidere după reparația franceză

Evaluatorul corectat a jucat o partidă completă de Spaniolă, cu indicii maxime salvate
înaintea tuturor celor 55 de semimutări efective, inclusiv teoria și mutările oracolului.
Hard nativ a furnizat 22 de mutări efective; Stockfish 23, iar zece provin din prefixul
de teorie declarat. Este o partidă observată, nu un meci Elo sau o rejucare redenumită.

Cele 77 de oferte de indiciu conțin 398 de semimutări ipotetice, verificate independent
la H17/H19 cu istoric complet: 40 pierd cel puțin 150‰ la ambele adâncimi, inclusiv
19 replici adverse. Șase rădăcini oferite și un prim indiciu depășesc pragul. Cele 55 de
alegeri native înregistrate nu au pierdere mare stabilă în această partidă. Verdictele
de calitate sunt separate; integritatea recepțiilor nu califică indiciile.

Prioritizează replica liberă slabă, nu o excepție de deschidere: după ramura salvată
`8.Ng5`, indiciul folosește `…Be6`, cu pierdere 509,5/511‰ față de jocul liber. Mai devreme,
în altă previzualizare, `…d5` pierde 428,5/443‰ când este disponibil `…Nxe4`. Păstrează
ambele istorii ipotetice complete, compară replicile libere la orizont comun și păstrează
componentele franceze, italiene, Caro–Kann și sacrificiile corecte. Scopul executabil
`LearningArchiveBenchmarkTest.maximumHintsHaveNoStableLargeLossInEitherSideOfThePreview`
rămâne FAIL; nu schimba aserțiunea zero erori pentru a accepta numărul observat.
Analiza finală și toate ramurile ei păstrează un audit separat al oracolului.
Dovezi: `C:\work\sah\.studii\corpus\2026-11-19-french-coaching-quiet`; producător
`098848be9612e68e370d3495df4dc679a0f7aedd`, jurnalul și recepțiile de lucru originale intacte.

Analiza finală auditează ambele ramuri ale tuturor celor 55 de mutări. Păstrează o
recomandare inferioară (mutarea 26, pierdere 267,5/289,5‰) și șapte etichete native
de eroare pe mutări care pierd sub 50‰ la ambele adâncimi. Toate cele 77 de capete
de indiciu sunt verificate independent: niciunul nu expune piesa importantă finală
unei recapturi legale, dar 14 ultime mutări rămân inferioare la ambele adâncimi.
Aceasta nu califică întregul PV: benchmarkul calității analizei finale rămâne FAIL
pentru recomandarea respectivă. Repară separat comparațiile cu replici libere și
clasificarea; nu ascunde replicile interne slabe doar fiindcă piesa finală e sigură.

Oferta liniștită a nebunului din Spaniolă este reparată în indiciul oferit: închiderea
completă a capturilor elimină previzualizarea optimistă `Ng5` și oferă
`Nc3 …O-O d3 …Be6 Be3`. Toate cele cinci semimutări oferite au pierdere sub 50‰ la
H17/H19 rece. Aceeași `…Be6` este corectă în martorul italian; o listă de mutări
interzise nu repară mecanismul. Păstrează componentele franceze și sacrificiile
corecte. Continuarea comparativă slabă rămâne deficit separat, cu benchmark
executabil și faze brute de oracol. O partidă nouă trebuie să păstreze fiecare indiciu
anterior mutării și analiza finală sub producătorul efectiv; nu redenumi partida
anterioară de 55 de semimutări din Spaniolă ca joc curent.

## Scoțiană șah câștigător și mat advers

Sursa restrânsă `e4bdd31af382297360e825bb1a47729e20c5cbbc` a trecut poarta celor 106
poziții istorice: trei recomandări schimbate, 18 faze noi de oracol, zero rezultate
incomplete și zero regresii mari noi ale rădăcinilor. Scoțiana de 52 de semimutări
este joc nou observat: zece mutări de teorie declarată, 21 efectiv jucate de Hard și
21 Stockfish, până la matul negru. Fiecare tură are indiciul maxim calculat anterior.
Toate cele 52 de obiecte de indiciu coincid cu reperul pe pozițiile noi; admiterea
completă a capturilor nu s-a activat. Este conservare, nu dovadă independentă de forță.

Cele 66 de oferte publice au 377 de semimutări ipotetice și 1.282 de interogări H17/H19.
Douăzeci și șapte pierd stabil cel puțin 150‰, inclusiv douăsprezece replici adverse;
trei prime indicii pierd substanțial. Două dintre cele 52 de alegeri native au pierdere
mare; doar una a fost efectiv jucată de Hard, cealaltă este alegere umbră la tura
oracolului. Analiza finală păstrează toate cele 52 de ture și ambele ramuri: trei
rădăcini recomandate pierd mult, 39 de semimutări din ramuri sunt slabe, trei etichete
native de eroare au pierdere mică de oracol și o pierdere mare nu are etichetă de eroare.
Toate cele 66 de capete sunt verificate: zero piese importante expuse nesustenabil,
dar cinci ultime mutări rămân inferioare. Partida nu stabilește Elo sau indicii global optime.

[ScotchWitnessTest](../../atf-tests/src/test/java/ro/atf/chess/ScotchWitnessTest.java)
păstrează zece faze brute, istoricul real/ipotetic complet și identitatea originală.
La mutarea reală, Stockfish liber alege `…Re8+`; lista de verificare îl omite. Mai
târziu, albul liber dovedește `Bxh7+ Kxh7 Qh5+ Kg8 Qh8#` la ambele adâncimi.
`Bxb2` ratează acel mat, dar păstrează șanse de câștig; nu îl descrie drept sacrificiu
pierzător. Sacrificiul corect de nebun pentru pion trebuie păstrat în modificările viitoare.
[ScotchContinuationBenchmarkTest](../../atf-tests/src/test/java/ro/atf/chess/ScotchContinuationBenchmarkTest.java)
păstrează scopuri FAIL separate pentru rădăcina indiciului, selecția rădăcinii în analiză
și continuarea adversă completă. Nu repara doar sufixul afișat, nu interzice capturile, nu forța o singură
apărare și nu rescrie istoricul. Verifică rădăcina și replicile libere la orizontul real.
Dovezi în `C:\work\sah\.studii\corpus\2026-11-19-ruy-qualified-coaching`.

La aceeași mutare, analiza nativă selectează `…dxc3` și numește `…Re8+` efectiv jucat
BLUNDER, cu 298 puncte native pierdute. Verificările forțate H17/H19 ale Re8+ pierd
doar 7,5/8‰. Acoperirea listei scurte nu închide singură defectul: analiza acoperă
și așa toate rădăcinile legale, dar le ordonează greșit. Păstrează benchmarkul separat
al rădăcinii din analiză.

Studiul următor a reconfirmat Re8+ prin căutări reci H17/H19, inclusiv cu culorile
inversate. Nicio corecție runtime nu s-a calificat. Prototipul pozițional selecta
Re8+ la H7, dar continuarea liberă `Re8+ Be3 dxc3 b3 Rxe3+ fxe3 Qxf6` a eșuat:
`…dxc3` pierde 450,5/450‰, iar `…Rxe3+` pierde 536/668,5‰. `b3` al albului rămâne
disputat (32,5/200,5‰), nu o apărare calificată independent. Prima mutare corectă
nu dovedește înțelegerea poziției. Prototipul rămâne în afara main.

Folosește [ScotchPromotionEvidenceTest](../../atf-tests/src/test/java/ro/atf/chess/ScotchPromotionEvidenceTest.java)
și martorul complet legat de sursă pentru a preveni închiderea doar pe rădăcină.
Negativele resping scoruri editate și reamprentate, adâncimi substituite, faze lipsă
sau duplicate, o rulare reetichetată consecvent, inventarul scurtat și corpul nativ
incomplet chiar când primește o amprentă nouă. Scopul separat pentru
[Game Review efectiv](../../atf-tests/src/test/java/ro/atf/chess/ScotchReviewClassificationBenchmarkTest.java)
rămâne FAIL pe clasificarea BLUNDER. Componentele verzi ale auditului nu trebuie
raportate drept decizie șahistă reparată.

Izolează în continuare `…dxe3` versus `…dxc3` după **Re8+ Be3**, cu **O-O** liber
pentru alb. După ramura respinsă **…dxc3 b3**, compară **…Qxf6** cu **…Rxe3+** și
include **Qf3**, rocada legală ulterioară și șahurile libere. Păstrează sacrificiile
corecte de nebun și damă, Franceza și Spaniola. Nu acorda regelui adăpostul unei
rocade încă necăutate și nu cumpăra rădăcina corectă printr-un sacrificiu de turn
supraevaluat. Chiar comparația izolată H9 la 20M de noduri s-a oprit fără scor;
lucrul suplimentar nu a calificat reparația. Runtime-ul păstrează bugetele și evaluarea
existente. Dovezi: `C:\work\sah\.studii\experiments\2026-11-19-scotch-re8`.

## Martorul public al indiciului Nimzo

Partida nouă `nimzo-indian-hard-black`, produsă de `3a1b0dbc01da1617d5eca29983f8f86056a7f8e0`,
păstrează 63 de semimutări reale din poziția inițială, cu Hard și indiciul public
maxim înaintea fiecărei mutări alb/negru, inclusiv cele șase mutări declarate de teorie.
Hard joacă negru și pierde prin mat contra profesorului offline la forță deplină.
Este dovadă de diagnostic, nu estimare Elo pereche ori poartă de forță a deschiderii.

A doua variantă salvată după `1.d4 Nf6 2.c4 e6 3.Nc3` este
`…Be7 e4 …d6 Bf4 …Nc6 Nf3`. Căutările Stockfish 19 libere și reci preferă `…Bb4`;
forțarea lui `…Be7` pierde 249,5‰ WDL la H17 și H19. Recepțiile originale
își păstrează identitățile efective în
[nimzo-hint-witness.yaml](../../atf-tests/src/test/resources/nimzo-hint-witness.yaml).
[NimzoWitnessTest](../../atf-tests/src/test/java/ro/atf/chess/NimzoWitnessTest.java)
verifică legalitatea, ambele orizonturi, rezultate schimbate, faze străine și recepții lipsă.
[NimzoHintBenchmarkTest](../../atf-tests/src/test/java/ro/atf/chess/NimzoHintBenchmarkTest.java)
rămâne un scop nativ de calitate explicit eșuat; un PV legal și arhiva intactă
nu trebuie să transforme cazul într-un PASS de motor reparat. Auditează fiecare
continuare și recaptură de piesă importantă înainte să restrângi schimbarea următoare.
Auditul complet păstrează 1.462 de interogări originale reci ale oracolului, plus completările pentru analiza finală. Există 80 de indicii oferite și 479 de semimutări ipotetice: 43 au pierdere mare stabilă, inclusiv 17 replici adverse și șase rădăcini oferite (o primă alegere). Analiza nativă finală pe 63 de semimutări are patru recomandări cu pierdere mare, 42 de semimutări ipotetice slabe, trei etichete de eroare pe pierderi independente mici și două pierderi mari omise. Integritatea dovezii trece; calitatea nativă rămâne FAIL.

La semimutarea reală 34, prima previzualizare se încheie `…Rxf6 Ne4 …Rf7 dxc5 …b6 Be3 …bxc5 Bxc5`. Ultimul `Bxc5` pierde 504‰ la ambele orizonturi, dar recaptura aparentă `…Qxc5` este o capcană respinsă prin `Nxc5`; ea păstrează câștigul albului. Jocul advers liber folosește în schimb mutarea liniștită `…Ne5`. Albul putea păstra câștigul prin `Rf1` înainte de `Bxc5`. Același martor păstrează ambele identități ale profesorului și testează o replică străină chiar după recalcularea hashului plicului. Nu adăuga `…Qxc5` drept continuare presupus forțată și nu interzice sacrificiile de nebun pentru a ascunde defectul real al replicilor libere.

Primele pierderi mari în jocul efectiv Native Hard sunt `…Nd7` la semimutarea 18 (259/159‰) și `…Nf6` la 20 (224/202,5‰), cu `…Qc7` preferat independent. Ambele decizii reale au consumat 200k noduri suplimentare și au păstrat H5. Sunt decizii de căutare, nu dovadă că simpla variație a cărții îmbunătățește Elo. Jurnalele originale, ambele ramuri ale analizei complete, replicile de la capătul liniilor și rapoartele Extent finale rămân în `C:\work\sah\.studii\corpus\2026-11-19-nimzo-variety` și studiul pereche `experiments\2026-11-19-opening-variety`.

Variația deschiderilor este acoperită de
[OpeningVarietyTest](../../atf-tests/src/test/java/ro/atf/chess/OpeningVarietyTest.java)
și documentată în [ghidul AI](../board/ai-quality.md). Nu randomizează ordinea
indiciilor, nu schimbă bugetele de căutare și nu dovedește o creștere Elo pentru Hard.
