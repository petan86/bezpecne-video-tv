# Bezpečné video 0.2.2

Oprava získávání nativního YouTube streamu. Původní identifikace aplikace jako
Android TV prohlížeče způsobovala odpověď, kterou NewPipe Extractor neuměl přečíst
(`Could not get ytInitialData`). Extraktor nyní používá identifikaci desktopového
prohlížeče a respektuje vlastní HTTP hlavičky jednotlivých požadavků.

- Sjednocená identifikace HTTP klienta při získávání a stahování streamu.
- Opravené řazení rozlišení: výchozí volba vybírá nejvyšší dostupnou kvalitu do 1080p.
- Přidaná volitelná síťová diagnostika pro video `2DGmX8F3cPo`.

Ověřeno na PC: extrakce 14 video stop a 5 zvukových stop; načtení začátku
1080p H.264 videa i zvuku Opus se stavem HTTP 206. Testy aplikace a sestavení APK
prošly. Plynulost, dekódování a ovládání na Chromecastu s Google TV je ještě
potřeba ověřit na zařízení.

Síťovou diagnostiku lze spustit v PowerShellu pomocí
`$env:VIDEO_NETWORK_DIAGNOSTIC='1'` a následně
`gradle :app:testDebugUnitTest --tests cz.petr.bezpecnevideo.StreamDiagnosticTest --rerun-tasks`.
Běžné testy tuto diagnostiku bez proměnné přeskakují. Diagnostika nevypisuje
podepsané adresy streamů ani cookies.
