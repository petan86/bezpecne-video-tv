# Bezpečné video 0.2.0

## Nativní přehrávač

- nový přehrávací engine AndroidX Media3 + NewPipe Extractor,
- TV nabídka dostupných rozlišení, snímkové frekvence a kodeku,
- TV nabídka titulků s možností titulky úplně vypnout,
- spojení samostatné video a zvukové stopy pro kvalitu nad 720p,
- zachované pokračování ve sledování a historie oddělená podle profilu.

## Rodičovská správa z PC

- přístup přes IP adresu nebo QR kód ze stejné domácí sítě,
- přidávání, prohlížení a odebírání ručně povoleného obsahu,
- relace nemá časový limit a běží do ukončení procesu aplikace,
- požadavky jsou chráněné náhodným přístupovým tokenem.

## Další opravy

- opravený český kanál Tlapkové patroly,
- spolehlivější zjištění identity kanálu,
- automatické zotavení původního iframe přehrávače z dlouhého bufferování zůstává
  v kódu jako záložní mechanismus,
- projekt je nově distribuován pod GPL-3.0-or-later.

Nativní engine používá neoficiální rozhraní YouTube. Budoucí změny YouTube mohou
vyžadovat aktualizaci NewPipe Extractoru.
