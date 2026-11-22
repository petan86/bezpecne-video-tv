# Bezpečné video 0.3.0

Přidávání celých kanálů odkazem, modrá ikona a snadnější aktualizace.

## Novinky

- Celý kanál lze povolit vložením odkazu – na stránce pro mobil/PC i v poli pro odkaz
  v rodičovské správě v TV. Funguje odkaz `youtube.com/channel/UC…`, `youtube.com/@nazev`
  i starší `/c/…` a `/user/…` (také s `/videos` na konci). Stránka ukáže název kanálu
  a nabídne, komu ho povolit; povolí se i všechna budoucí videa.
- Ikona a dlaždice aplikace v Google TV jsou modré, aby se nepletly s YouTube.
- Tlačítko „Zkontrolovat a nainstalovat aktualizaci“ je hned nahoře v rodičovské správě
  a ukazuje číslo nainstalované verze.

## Změny

- Automatická kontrola aktualizací proběhne při každém spuštění aplikace
  (dál nejvýše jednou za 6 hodin, dokud aplikace běží).
- Načítání kanálů podle `@nazev` funguje i v EU, kde YouTube nejdřív zobrazí
  stránku se souhlasem s cookies.
- Znovu povolený vestavěný kanál, který byl dříve odebrán, se opět zobrazí.

Ověření: sestavení APK, jednotkové testy a testy webového rozhraní.
