# Celý archiv kanálů (od verze 0.1.21)

Bez klíče aplikace dál používá krátký seznam novinek. S klíčem načítá veřejná videa povolených kanálů přes oficiální YouTube Data API, po nejvýše 50 položkách. Pod seznamem je pro každý kanál tlačítko **Načíst další videa**. Pro přehlednější procházení vyberte nahoře konkrétní kanál.

## Jednorázové nastavení na počítači

1. Otevřete https://console.cloud.google.com/ a přihlaste se svým Google účtem.
2. V horním výběru projektů vytvořte nový projekt, například **Bezpečné video TV**.
3. V nabídce **APIs & Services → Library** vyhledejte **YouTube Data API v3** a klikněte na **Enable** (Povolit).
4. V **APIs & Services → Credentials → Create credentials → API key** vytvořte klíč. Pro veřejné seznamy videí nepotřebujete OAuth přihlášení.
5. V nastavení klíče v **API restrictions** zvolte **Restrict key** a povolte pouze **YouTube Data API v3**. Tato verze používá REST požadavky bez ověřování Android aplikace; v **Application restrictions** ponechte **None**. Klíč uchovávejte soukromě, nesdílejte ho ani neukládejte do GitHubu.
6. Na TV aktualizujte aplikaci na **0.1.21**, otevřete **Rodičovská správa → Celý archiv kanálů – nastavit API klíč**, vložte klíč a uložte ho. Pro psaní lze použít klávesnici dálkového ovládání v mobilu.
7. Otevřete profil dítěte a vyberte kanál. Dole lze postupně načítat další stránky. Návrat z videa zachová načtený seznam i jeho pozici.

Klíč se ukládá v soukromých datech aplikace a používá pouze v požadavcích do Google API. Automatická záloha aplikace je vypnutá. Vyprázdněním pole se vrátíte k seznamu novinek.

## Když se archiv nenačte

Zkontrolujte internet, správnost klíče, povolení služby ve stejném projektu a **APIs & Services → Quotas**. Po změně nastavení klíče může chvíli trvat, než se projeví. Na TV použijte **Zkusit znovu**. Při vyčerpaném denním limitu další stránky nebudou dostupné, dokud se limit neobnoví.

Soukromá a smazaná videa se nezobrazují. Veřejné video může mít zvláštní regionální či přehrávací omezení. API klíč ani Premium nezaručují dostupnost každého videa ve vloženém přehrávači.

## Rychlost

Seznamy jsou v paměti aplikace po dobu pěti minut, náhledy mají omezenou mezipaměť 8 MB. Po návratu z videa se zachová otevřená galerie. Přehrávač se opakovaně používá a při opuštění obrazovky se zastaví. Mezipaměť není offline stahování videí a po ukončení aplikace se ztratí.

Vložený YouTube přehrávač si nadále řídí kvalitu a buffer sám. Vynucení 720p ani velikosti bufferu tato verze neslibuje. Rychlost a ovládání je třeba ověřit na cílovém Chromecastu.

Dokumentace: https://developers.google.com/youtube/v3/getting-started a https://developers.google.com/youtube/v3/docs/playlistItems/list
