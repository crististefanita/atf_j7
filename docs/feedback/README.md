# Feedback · Chess Lab

## Pe scurt

Registrul păstrează lacunele și criteriile deschise. [Ghidul tablei](../board/README.md)
descrie comportamentul, iar [catalogul edițiilor](../../release/README.md) indică dovezile publicate.

[Revizuirea mutărilor între suite](../testing/README.md#schimbarea-testelor-față-de-o-bază-git) descrie baza Git și dovezile de acoperire.

| Prioritate | Zonă | Observație | Criteriu de închidere |
|---|---|---|---|
| P1 | Release · identitatea guvernanței | Identitatea XML este verificată după nume și verdict. | Respinge recepțiile PASS din alt commit sau din altă rulare. |
| P2 | Release · arhiva probei compacte | Proba compactă are o arhivă separată. | Respinge fazele lipsă, rulările străine și PNG-urile substituite. |
| P1 | Game Review · clasificarea mutărilor | Nouă neconcordanțe persistă la ambele adâncimi ale oracolului, inclusiv pierdere mare ratată și gafă falsă. | Reproduce și corectează clasificarea fără a schimba istoricul, inventa Elo sau substitui dovezile analizei cu scorul indiciului. |
| P2 | Game Review · NVDA | Vorbirea RO repeta poziția și scorul. | Confirmă un singur anunț în RO și EN și revenirea focusului. |
| P1 | Italiană · schimbul neîncheiat nativ | Recaptura țintită repară perechea H6 izolată, dar decizia completă și martorii regresează. | Rezolvă decizia completă în 200k, păstrând 019/021/022 și transferul independent înainte de duel. |
| P1 | Indiciu · lider, alternative și replici adverse | Rejucarea corectată păstrează pierderi mari în 9/60 prime indicii, 12/83 variante oferite și 9/83 prime replici adverse. | Repară sugestiile publice complete și replicile calculate la orizonturi comune, păstrând alegerile native corecte și familiile independente. |
| P2 | Audit indicii · transfer mai larg | Captura pereche nativ/indiciu și rejucarea brută H17/H19 acoperă acum trei rejucări și două ramuri tematice noi. | Îngheață istorii independente noi și verifică transferul corecțiilor dincolo de traseele inspectate. |
| P2 | Probleme · focus TalkBack în opțiuni | Opțiunile problemelor mai cer dovada focusului. | Înregistrează traseul focusului în ambele limbi. |
| P2 | Android · gestul predictiv | Gestul predictiv mai cere un dispozitiv compatibil. | Verifică anularea și reluarea pe API 33+. |
| P1 | Calibrarea profilurilor AI | Intervalele Elo încă se suprapun. | Adaugă jocuri pereche pentru a restrânge incertitudinea. |
| P1 | Forța Hard adaptiv | Căutarea pornește cu 100.000 de noduri și o rezervă până la 200.000. | Compară cele trei moduri pe partide excluse din ajustare și verifică replica adversă. |
| P1 | Învățarea Hard din PGN-uri | Modelul de ordonare antrenat pe pierderi păstrează toate mutările finale în 36 de familii noi. | Adaugă mutări bune păstrate și sacrificii, apoi verifică ordonarea și deciziile complete. |
| P1 | Căutarea Hard · generalizare | Recaptura selectivă pierde pe deschideri independente. | Cere progres independent la bugetul fix. |
| P1 | Hard · conversia după captura de pion | Avantajul după captură poate fi pierdut ulterior. | Verifică păstrarea avantajului pe poziții și replici noi. |
| P1 | Transferul tactică → partidă | Hard ratează tactici fără obiectiv declarat. | Măsoară detectarea și păstrează contractul solverului. |
| P2 | Progres · upgrade Android | Upgrade-ul semnat mai cere o probă de progres. | Păstrează ID-urile, rezolvările și punctele după upgrade și restart. |
| P2 | Calitatea AI · evaluare material | Scorul material diferă de Stockfish. | Redu abaterea la aceleași bugete. |
| P2 | Calibrarea problemelor | Benzile de dificultate importate se suprapun. | Verifică timpul și încercările pe un lot amestecat separat. |
| P2 | Android · separarea tranzițiilor de ecran | Ecranul Android deține mai multe tranziții de sesiune. | Mută tranzițiile într-un owner testabil în JVM. |

## Dovezi și verificări de închidere

<details>
<summary>P1 · Release · identitatea guvernanței</summary>

Preflight leagă recepția pentru schimbarea testelor de Git, iar XML-urile Documentation, Release și Extent sunt verificate după nume și verdict.

Leagă fiecare XML de commitul candidat și de rularea de calificare. Înlocuiește pe rând un XML cu o recepție PASS din alt commit sau din altă rulare și verifică refuzul, păstrând recepția Git curentă. [Preflight](../../release-tools/src/main/java/ro/atf/chess/release/ReleaseGate.java) · [Teste](../../atf-tests/src/test/java/ro/atf/chess/ReleaseGovernanceTest.java)

</details>

<details>
<summary>P2 · Release · arhiva probei compacte</summary>

Publicatorul Android verifică raportul compact bilingv. Poarta reutilizabilă califică fazele Android generale; procedura cere și arhiva compactă pentru schimbările de geometrie.

Adaugă verificarea compactă în poarta de promovare pentru schimbările de geometrie. Respinge faza lipsă, faza din altă rulare și PNG-ul substituit. Păstrează validarea arhivelor din edițiile anterioare. [Calificare](../release/README.md#calificare-și-publicare) · [Publicator compact](../../atf-tests/src/test/java/ro/atf/chess/AndroidSanityPublication.java)

</details>

<details>
<summary>P1 · Game Review · clasificarea mutărilor</summary>

Apelul efectiv `GameReview.analyzeLine` păstrează toate cele 60 de mutări și nu emite rating pentru cele trei studii limitate. Totuși, `6.e6` din Caro–Kann este BEST, iar precizia albului este 100, deși rădăcina jucată pierde 533‰ la H17 și 581,5‰ la H19. `5.Nc3` din Siciliană este BLUNDER cu 0‰ pierdere la ambele orizonturi. În cele trei partide, trei etichete pozitive însoțesc o pierdere mare la oracol, iar șase etichete severe însoțesc sub 10‰ pierdere la ambele adâncimi.

Îngheață martorii cu istoric complet înainte de corecția comparației comune. Verifică rădăcinile jucată și recomandată la același orizont complet, păstrează redarea legală și bookmark-urile și măsoară familii noi. Pierderea logistică locală și WDL Stockfish sunt modele distincte; nu lega producția de Stockfish și nu copia mecanic eticheta sa. Auditul curent evaluează rădăcina jucată și verifică legalitatea PV-urilor salvate; nu certifică independent fiecare alternativă a analizei. [Contractul analizei și limitarea](../board/game-review.md#limitările-măsurate-ale-analizei) · [Audit pereche](../testing/hard-hint-study.md)

</details>

<details>
<summary>P2 · Game Review · NVDA</summary>

NVDA 2026.3.1 a reprodus în RO dublarea poziției și a scorului. Componenta și Edge confirmă acum o singură valoare accesibilă.

Confirmați vorbirea pe build-ul corectat, în RO și EN, și revenirea la aceeași mutare din **De ce?**. [Feedback Web](web-parity.md)

</details>

<details>
<summary>P1 · Italiană · schimbul neîncheiat din căutarea nativă</summary>

Prototipul de recaptură pe ultima casetă capturată schimbă ordonarea H6 izolată din `…Na5 −154 / …Qg6 −170 cp` în `−80 / −50 cp`. În frunze găsește `…Qxe5` și `…Nxe5`; ambele culori și probele cache rece, încălzit, dezactivat și cu fereastră îngustă păstrează rezultatul local. Fiecare rădăcină izolată are propriul buget; probele nu certifică o decizie completă în același buget cumulat.

Candidatul complet alege însă `…Bxf2+`, evaluat la −311 cp și 0‰ de Stockfish H19. Pierde și 478,5‰, 191,5‰ și 516‰ în martorii 019/021/022. `…cxd4` din Siciliană și `…d5` din Caro–Kann rămân neschimbate. Respinge prototipul, precum și extensiile anterioare uniformă și doar pentru damă. Păstrează contextul ultimei capturi în identitatea cache-ului; nu copia indiciul în joc și nu relaxa veto-ul de stabilitate.

Izolează de ce închiderea schimbului schimbă liderul complet și cei trei martori. Cere istoricul complet, replici adverse libere la același orizont și decizia nativă completă în 200k suplimentare înainte de transfer neinspectat și duel pereche. Indiciul public S5 corectat rămâne `…Qg6`; Hard nativ rămâne `…Na5`. Motorul live este neschimbat, deci nu există ΔElo nou al candidatului. [Studiul măsurat](../board/ai-quality.md#studiul-pereche-nativindiciu-și-respingerea-recapturii) · [Contractul auditului](../testing/hard-hint-study.md)

</details>

<details>
<summary>P1 · Indiciu · lider, alternative și replici adverse</summary>

Defectul italian `Nd5/Na4` după `4…Qf6` și cel Caro–Kann `…dxc3/…fxe6` după `6.e6` sunt închise ca regresii ale primului indiciu. Mecanismul și domeniile generale respinse aparțin [ghidului AI](../board/ai-quality.md#recaptura-damei-și-continuitatea-indiciului-italian), cu teste de componentă permanente. Alte poziții cu presiune asupra damei rămân deficitare: `validation-039`, poziție distinctă cu istoric complet după `…Qb6`, păstrează pierderea anterioară de 462‰ a primului indiciu.

Rejucarea pereche de șaizeci de faze încă eșuează la calitatea absolută: 9 prime indicii, 12 mutări oferite și 9 prime replici adverse din PV depășesc 150‰. Liderul sicilian `…Nc6` pierde încă 358‰, oferind `…Nbd7` mult mai bun; `N1c3` anterior încă oferă `Bg5` inferior. Cazul de transfer cu pion forțant încă oferă `Bxe4` la 243,5‰. Noua istorie `validation-057` îmbunătățește liderul, dar păstrează `Bg5` inferior ca ofertă secundară la 339,5‰. Prioritizează prima divergență la orizont comun și replica adversă liberă; diferența mică dintre scorurile native nu dovedește mutări echivalente.

Păstrează martorii Caro și Italiană închiși, sacrificiile corecte, negativele cache-ului exact, plafonul total 200k al indiciului și mutările istorice intacte. Rezolvă ofertele secundare și replicile PV deficitare înainte de a pretinde explicații scolastice complete. Evaluează separat liderii, fiecare rădăcină oferită și replicile calculate la H17/H19. Corecția indiciului nu certifică jocul Hard nativ, bara sau clasificările Game Review. [Audit și publicare](../testing/hard-hint-study.md)

</details>

<details>
<summary>P2 · Audit indicii · transfer mai larg</summary>

Captura pereche și rejucarea brută Stockfish acoperă acum șaizeci de semimutări istorice pereche și două studii tematice distincte de șaisprezece semimutări. Noul audit al comparației include rădăcinile diagnostice. Studiul selectat are 1190 recepții brute validate; redarea legală și integritatea recepțiilor nu implică PASS la calitate șahistă.

Șaisprezece istorii inspectate cu presiune asupra damei au expus domeniul general respins și au devenit martori de dezvoltare. Șaisprezece istorii de deschidere înghețate ulterior păstrează o îmbunătățire de 322,5‰ a liderului și o scădere de 13,5‰, fără pierdere nouă de lider peste 150‰. Lotul de dezvoltare păstrează separat o scădere de 18,5‰. Nu regla pe aceste observații ca apoi să le declari neinspectate. Extinde către familii neatinse cu oferte secundare greșite și etichete înșelătoare ale analizei; păstrează eșecurile, sursa și istoriile exacte. Studiile plafonate cu prefixe comune nu măsoară Elo. [Audit pereche](../testing/hard-hint-study.md)

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

După `2…dxc4`, J7 afișează `−0,60`, iar Stockfish 19 la adâncimea 13 indică `+0,21`; după `3.e3`, abaterea ajunge la 111 cp.

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
