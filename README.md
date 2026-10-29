# Bezpečné video

Vlastní rodičovská aplikace pro Android TV / Google TV s ručně povolenými YouTube videi.

## První verze

- oddělené profily Matýsek a Filípek,
- samostatný seznam povolených videí pro každé dítě,
- rodičovská část chráněná PINem,
- přidání videa běžným YouTube odkazem,
- přehrávání pomocí oficiálního vloženého YouTube přehrávače,
- televizní launcher, landscape rozhraní a ovládání dálkovým ovladačem,
- přednastavený povolený kanál Toto je Slivki pro Matýska,
- vyhledávání videí přímo z rodičovské správy bez opisování odkazu,
- povolení jednotlivého videa nebo celého kanálu přímo z výsledků vyhledávání,
- kontrola, stažení a spuštění aktualizace přímo z rodičovské správy,
- tmavé televizní prostředí a galerie náhledů konkrétních videí z povolených kanálů,
- domovská obrazovka inspirovaná ovládáním YouTube pro TV se čtyřmi náhledy v řadě,
- přednastavený český kanál Tlapková patrola pro Filípka,
- přednastavený kanál CrunchLabs pro Matýska,
- přednastavený kanál Every Brick pro Matýska,
- přednastavený kanál Kidibli pro Filípka,
- česká a rodičem ručně povolená videa se v kategorii Vše zobrazují před anglickými,
- zahřátí YouTube přehrávače, hardwarová akcelerace, webová cache a paralelní načítání náhledů,
- CrunchLabs je řazený jako český obsah a přehrávač preferuje české rozhraní a jazyk,
- webová obrazovka pro přihlášení a ověření YouTube Premium.

Výchozí rodičovský PIN je `2468`; při prvním spuštění ho změňte.

Od verze 0.1.21 lze procházet celý veřejný archiv povolených kanálů po 50 položkách. Je potřeba vlastní YouTube Data API klíč: [návod k nastavení](ARCHIV_KANALU.md). Aplikace ukládá náhledy a seznamy do paměti a zachovává galerii při návratu z videa.

## Sestavení

Otevřete složku v Android Studiu a spusťte konfiguraci `app`. Projekt vyžaduje JDK 17 a Android SDK 36.

## Důležité

Aplikace neblokuje reklamy a neupravuje YouTube stream. Premium výhody závisejí na tom, zda YouTube rozpozná přihlášený účet ve vloženém přehrávači. Tuto část je nutné ověřit na cílovém telefonu.
