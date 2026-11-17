# Auditul offline al indiciilor

## Pe scurt

- **Scop.** Urmărim o partidă scurtă decizie cu decizie, păstrăm fiecare indiciu oferit și replicile adverse și comparăm bugetele din poziții identice. Colecția este diagnostică, fără estimare Elo.
- **Responsabilitate.** `HintAuditPolicy` și `HintAuditSession` din teste consumă motorul real printr-un singur apel plafonat pentru fiecare decizie. Nu antrenează și nu configurează adversarul live.
- **Verificări.** `HintAuditSessionTest` acoperă integritatea recepțiilor cu mock-uri. Metoda sa cu etichetă separată de benchmark creează colecția reală și raportul Extent auditabil. [Plan](../../atf-tests/src/test/resources/hint-audit.yaml) · [Contracte](../../atf-tests/src/test/java/ro/atf/chess/HintAuditSessionTest.java)

## Rularea unei colecții reale

Folosiți un checkout extern curat și un director nou de ieșire în `C:\work\sah`.
Păstrați fișierul Extent lângă directorul datelor brute. ID-ul rulării, commit-ul
sursă și amprenta motorului trebuie să identifice sursa și clasele calificate.
Păstrați manifestul surselor și claselor înainte și după generare. O etichetă
furnizată doar de apelant nu atestă ce motor a rulat.

```powershell
Set-Location $checkout
mvn test '-Dtest=HintAuditSessionTest#collectsShortRankedPathsAndAnIdenticalPositionBudgetGrid' `
  "-Dchess.hintAudit.output=$auditOutput" "-Dchess.extent=$extentPath" `
  "-Dchess.hintAudit.run=$runId" "-Dchess.hintAudit.revision=$sourceCommit" `
  "-Dchess.hintAudit.engineDigest=$engineDigest"
```

Setați `TEMP`, `TMP` și directorul temporar JVM în spațiul extern înainte de
build. Colectorul selectat explicit fără ieșirea obligatorie eșuează. Suita
normală de componente exclude metoda benchmark și rulează contractele cu
mock-uri fără Stockfish sau emulator.

## Planul și comparația

YAML-ul versionat declară prefixe scurte de Siciliană, Caro–Kann și Italiană,
opt decizii planificate pe traseu, programe de 50.000, 100.000 și 200.000 de
noduri și rangurile cerute 1, 2 și 3 ale indiciilor. Plafonul rămâne 200.000.
Parserul respinge programele goale, câmpurile necunoscute, starturile ilegale,
ID-urile duplicate și valorile în afara limitelor declarate.

Fiecare traseu începe cu mutări legale de deschidere, apoi urmează rangul cerut
al indiciului. Explorăm astfel intenționat alternative. Mutarea documentată din
teorie a adversarului Hard normal este înregistrată separat. Colecția studiază
politicile indiciilor, fără a pretinde o ligă de rating sau jocul efectiv al adversarului live.

Un rang indisponibil este înregistrat explicit. Rezerva declarată selectează
prima sugestie reală. Nu inventează a doua ori a treia recomandare. Candidații
comparați și verificați sunt dovezi de diagnostic, separate de recomandările oferite.

Alte nouă rânduri compară cele trei bugete din același checkpoint complet de
start al fiecărui traseu. Numai aceste rânduri pereche măsoară efectul bugetului.
Traseele care au deviat nu pot susține o comparație directă a forței bugetelor.
O partidă încheiată poate opri traseul mai devreme și își păstrează rezultatul real.

## Dovezile păstrate

| Înregistrare | Semnificație | Validare |
|---|---|---|
| Identitate | Colecție, rulare, revizie sursă exactă, amprentele motorului și configurației | Identitățile străine sunt respinse |
| Poziție | Checkpoint complet, FEN, culoare, contoare, drepturi de rocadă și en passant | Rejucare din întregul istoric |
| Context | Buget, număr de repetiții și semantica cache-ului | Istorii diferite rămân contexte diferite |
| Căutare | Noduri efective, adâncimea inițială, cea selectată și cea comparată | Un apel, plafonat la alocația cerută |
| Variante | Ranguri oferite, scoruri, material, PV-uri complete și replici adverse | Verificarea legalității, materialului și etichetelor terminale |
| Selecție | Rang cerut, disponibilitate, mutare selectată și referință teoretică | Rezervă explicită și continuare legală |
| Integritate | Amprenta recepției precedente și amprenta de încredere a întregului fișier | Reluarea respinge trunchierea, substituția și scorurile modificate |

Lipsa unei comparații acceptate se înregistrează cu adâncimea comparației zero.
Aceasta nu înseamnă că indiciul a căutat zero semimutări. Adâncimile selectată și
inițială notează orizonturile atinse, separat de lungimea PV-ului.

Căutarea este proaspătă și dependentă doar de poziție. Păstrarea istoricului
pentru audit nu face indiciul sensibil la repetiții și nu creează un cache învățat
persistent. Șahul, captura și replicile descriu pericole observate. Nu certifică
siguranța în fața oricărei apărări legale.

JSONL-ul fiecărui traseu și PGN-ul cu SAN păstrează mutările exacte. JSONL-ul
combinat și manifestul identifică întreaga colecție. Un studiu neterminat are
rezultatul PGN `*`. Reluarea cere o amprentă JSONL de încredere separată și
revalidează istoricul fără o nouă căutare. Singur, lanțul intern de amprente nu
poate autentifica un fișier rescris coerent.

## Verificarea independentă a calității

Stockfish 19 evaluează checkpoint-ul complet exact la H17 și H19. Comparați
căutarea liberă de la rădăcină cu mutarea oferită sau jucată, apoi lăsați toate
replicile adverse libere. Păstrați scorurile, WDL, variantele principale,
comenzile, identitatea motorului și recepțiile brute. Reutilizarea cere intrări
identice și proveniență explicită.

Separați pierderea primei recomandări de cea produsă prin alegerea intenționată
a rangului 2 sau 3. Rejucarea legală, istoricul păstrat și integritatea trecută
nu stabilesc forța mutării. O schimbare care repară o deschidere și strică alta
rămâne experimentală. Documentați separat cazurile de dezvoltare, cele de
transfer neatinse și cazurile refolosite ulterior ca martori de regresie.

Greșelile capturate devin cazuri de regresie cu istoric complet, înghețate numai
după fixarea intrărilor oracolului, a apărărilor libere și a sursei. Păstrează un
lider cunoscut corect și contraexemple de sacrificii corecte lângă fiecare eșec.
Cazurile inspectate nu mai sunt un lot de transfer neatins. Rezultatele brute
ale partidelor nu devin etichete automate de învățare, iar interdicțiile FEN în
runtime nu pot înlocui căutarea.
[Contracte de transfer înghețate](../../atf-tests/src/test/java/ro/atf/chess/HintTransferRegressionTest.java)

Cele trei probe italiene inițiale au fost respinse. Corecția ulterioară păstrează dovezile brute H4 și demovează numai oferta materială susținută de credit, dar infirmată de acele dovezi. La S5 recomandă `…Qg6` și nu mai oferă `…Bxf2+` sau `…Nge7` ca echivalente. Dintre 44 de cereri pereche, numai acel lider se schimbă; patru istorice noi își păstrează liderii. Schimbul echilibrat sicilian și sacrificiul Traxler `…Bxf2+` rămân valide. Comparațiile diagnostice rămân distincte de sugestiile publice, iar scorurile de revenire exclud creditul istoric. Ordinea de preferință poate include dovezi de la orizonturi anterioare și nu promite sortare numerică după scorurile brute. [Regresii și contraexemple](../../atf-tests/src/test/java/ro/atf/chess/HintCreditRegressionTest.java)

Hard nativ și indiciul rămân API-uri distincte. [Auditul fiecărei mutări și Elo](hard-audit.md) măsoară separat partidele reale.

Testele cache-ului exact disting valorile ferestrei complete de limitele tăierilor
și compară cache-ul încălzit, rece și dezactivat. Referința originală clasifică
și ea limitele separat. [Căutarea Stockfish 19](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp#L1460-L1468)

[Politica AI](../board/ai-quality.md) · [Strategia testării](README.md) · [Feedback curent](../feedback/README.md)
