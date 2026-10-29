# Přidávání videí z mobilu (od 0.1.23)

1. Aktualizujte aplikaci na TV. Otevřete **Rodičovská správa**, zadejte PIN a vyberte **Přidat video z mobilu – QR kód**.
2. Mobil připojte ke stejné domácí síti jako Chromecast. Naskenujte QR kód běžným fotoaparátem a otevřete odkaz v prohlížeči.
3. V aplikaci YouTube na mobilu najděte video a použijte **Sdílet → Kopírovat odkaz**.
4. Vraťte se na otevřenou stránku, vložte odkaz do políčka a klepněte na **Zobrazit video**.
5. Zkontrolujte náhled a název. Vyberte **Matýskovi**, **Filípkovi** nebo **Oběma** a potvrďte **Povolit video**.
6. Pro další odkaz zvolte **Přidat další video**. Po dokončení stiskněte na TV **Hotovo** a otevřete profil dítěte. Ručně přidané video je v kategorii **Vše**.

Není potřeba další účet ani YouTube Data API klíč. Samotný náhled video nepovolí: rozhodující je až tlačítko **Povolit video**. Opětovné přidání stejného videa do stejného profilu nevytvoří duplicitu. Pokud YouTube nevrátí název, lze ho napsat ručně.

## Připojení a platnost

Stránku poskytuje přímo TV v domácí síti přes HTTP. Používejte důvěryhodnou domácí síť; přenos není šifrovaný. Stránka nevyžaduje heslo ke Google účtu, PIN ani API klíč. Přístupový odkaz vzniká až po odemčení rodičovské správy a umožňuje pouze náhled a přidání videa. QR kód nesdílejte s dětmi ani jinými osobami, kterým nechcete přidávání povolit.

QR odkaz platí nejvýše 10 minut. Zavření obrazovky, přechod do jiné TV aplikace nebo ukončení aplikace přístup ukončí. Potom otevřete nový QR kód. Nový kód má jiný přístupový token; samotná IP adresa na TV přidávání nepovoluje.

## Když se stránka neotevře

- Mobil nesmí být pouze na mobilních datech nebo oddělené síti pro hosty. Chromecast může být připojený i kabelem do stejné domácí sítě.
- Pokud prohlížeč požádá o přístup k místní síti, povolte ho pro toto připojení.
- Nechte obrazovku s QR kódem na TV otevřenou. Po vypršení platnosti naskenujte nový kód.
- VPN, izolace klientů na routeru nebo síť bez místní IPv4 adresy mohou připojení znemožnit. V takovém případě je dál dostupné vyhledávání videí přímo v rodičovské správě na TV.

Přidávání celých kanálů zůstává v dosavadní rodičovské správě na TV; tato mobilní obrazovka přidává konkrétní videa.
