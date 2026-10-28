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
- webová obrazovka pro přihlášení a ověření YouTube Premium.

Výchozí rodičovský PIN je `2468`; při prvním spuštění ho změňte.

## Sestavení

Otevřete složku v Android Studiu a spusťte konfiguraci `app`. Projekt vyžaduje JDK 17 a Android SDK 36.

## Důležité

Aplikace neblokuje reklamy a neupravuje YouTube stream. Premium výhody závisejí na tom, zda YouTube rozpozná přihlášený účet ve vloženém přehrávači. Tuto část je nutné ověřit na cílovém telefonu.
