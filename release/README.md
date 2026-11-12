# Edițiile Chess Lab

## Ediția publicată

[`index.json`](index.json) este catalogul tehnic. `currentVersion` identifică ultima ediție publicată, iar `versions` păstrează o singură ediție activă. Cele două linkuri din [README-ul principal](../README.md#jocul) trebuie să indice tagul și APK-ul acestei versiuni și să aibă dovezi complete în dosarul ei.

## Candidat

Un `candidate` din catalog este pregătire internă; nu intră în lista descărcabilă și nu schimbă README-ul principal. După semnare, calificare și publicarea asset-ului, mutați versiunea candidat în `currentVersion` și `versions`, eliminați `candidate`, apoi actualizați cele două linkuri și dovezile. Versiunile istorice rămân în taguri și în dosarele lor de audit, în afara listei active.

[Procesul de release](../docs/release/README.md) · [Dovezile ediției publicate](0.11.2/reports/README.md) · [Candidatul pregătit](0.12.0/README.md)
