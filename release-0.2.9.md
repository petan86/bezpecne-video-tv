# Bezpečné video 0.2.9

Profil Hudba a adaptivní kvalita videa – rychlejší start přehrávání.

## Novinky

- Nový profil **🎵 Hudba** na úvodní obrazovce. Videa do něj lze přidávat z TV
  i ze stránky pro mobil/PC a povolovat do něj celé kanály.
- Adaptivní kvalita videa: přehrávání začne v nižší kvalitě, takže se video
  rozběhne skoro okamžitě. Jakmile je načteno několik sekund dopředu, přehrávač
  plynule (bez zastavení) přepne až na 720p. Při zpomalení připojení kvalitu
  sám sníží, aby se video nesekalo.
- Tlačítko „Kvalita“ ukazuje aktuální rozlišení, např. „Kvalita: auto (720p)“.
  V nabídce lze zvolit „Automaticky“ nebo pevnou kvalitu; změna proběhne bez
  restartu videa.

## Změny

- Pokud video nejde přehrát adaptivně, aplikace automaticky použije původní
  způsob přehrávání jedné kvality.

Ověření: sestavení APK, jednotkové testy a testy webového rozhraní.
