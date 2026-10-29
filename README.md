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

## Galerie a pokračování (0.1.22)

Galerie vykresluje jen viditelné karty a blízké okolí. Náhledy mají paměťovou cache 8 MB a diskovou cache 32 MB s expirací po 7 dnech. Nejde o stahování samotných videí.

Rozkoukaná videa se ukládají samostatně pro každý profil a objevují se nahoře v sekci **Pokračovat ve sledování**. Obvyklý stisk OK naváže na uložený čas (s návratem o dvě sekundy). Podržení OK otevře nabídku **Přehrát od začátku**. Po zhlédnutí alespoň 95 % se video označí jako zhlédnuté. Historie nikdy sama neuděluje povolení: po odebrání videa či kanálu se už takový obsah nenabízí.

Ukládá se maximálně 100 záznamů na profil, na úvodní obrazovce je nejvýše 8 rozkoukaných videí. Stav se ukládá každých 5 sekund a při odchodu; při násilném ukončení může několik posledních sekund chybět. Živá vysílání, pokud je přehrávač rozpozná, se do pokračování neukládají. Tato funkce nevyžaduje API klíč.

Ověření: `gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest` a `node tests/player.test.cjs`. Na reálné TV zkontrolujte posouvání většího archivu, načtení další stránky, návrat z videa, podržení OK a rozdílné pozice stejného videa v obou profilech.

## Sestavení aplikace

Otevřete složku v Android Studiu a spusťte konfiguraci `app`. Projekt vyžaduje JDK 17 a Android SDK 36.

## Důležité

Aplikace neblokuje reklamy a neupravuje YouTube stream. Premium výhody závisejí na tom, zda YouTube rozpozná přihlášený účet ve vloženém přehrávači. Tuto část je nutné ověřit na cílovém telefonu.
