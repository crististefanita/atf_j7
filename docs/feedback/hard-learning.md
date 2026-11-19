# Învățarea Hard din partide arhivate

## Pe scurt

Acest registru deține dovezile încă necesare înaintea promovării unei ordonări învățate. [Ghidul de admitere](../testing/learning-archive.md#studii-de-ordonare-învățată) păstrează experimentul istoric încheiat și limitele sale.

| Prioritate | Lacună | Criteriu de închidere |
|---|---|---|
| P1 | Exemplele doar din pierderi pot confunda sacrificiile corecte cu piesele lăsate în priză. | Înghețați mutări bune păstrate, sacrificii cu replică adversă verificată și alternative pierzătoare apropiate înaintea ajustării; separați după origine. |
| P1 | Pe 36 de familii excluse, ordinea rădăcinii se schimbă în 34 de cazuri, dar toate deciziile complete rămân identice. | Îmbunătățiți deciziile complete și continuările adverse pe familii noi, la orizonturi și bugete egale, fără regresii tactice. |
| P1 | Liga istorică de 44k nu stabilește un câștig util de forță și nu măsoară politica adaptivă din prim-plan. | Calificați întâi transferul, apoi folosiți protocolul fixat pentru partide pereche și ancora Stockfish; raportați încrederea, neterminatele și costul. |

## Următorul contrast

Păstrați atât `learned-0007` (`Rb1`, câștig), cât și `learned-0015` (`Qxb5 …axb5`, pierdere). Comparațiile candidat/reper inspectate au atins patru, respectiv cinci semimutări, deci nu izolează efectul ordonării. Verificați replica adversă liberă la un orizont complet comun înaintea generalizării.

[Protocol AI](../board/ai-quality.md#control-la-fiecare-schimbare) · [Profil adaptiv](../board/ai-quality.md#buget-adaptiv-în-prim-plan)
