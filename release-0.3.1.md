# Bezpečné video 0.3.1

Plynulejší start přehrávání – video se po pár sekundách nezasekne a hned běží v dobré kvalitě.

## Opravy

- Video se po prvních sekundách nezastavuje. Zvuk se nově stahuje po krátkých
  úsecích stejně jako obraz; dřív se stahoval jedním dlouhým požadavkem, který
  YouTube zpomaloval, a přehrávání se na 5–10 s seklo.
- Po rozběhnutí už kvalita nespadne na nejhorší. Pomalé stahování zvuku dřív
  zkreslovalo odhad rychlosti připojení.

## Změny

- Přehrávač si pamatuje změřenou rychlost připojení, takže další videa (i po
  změně kvality nebo titulků) začínají rovnou ve správné kvalitě.
- První video začíná ve vyšší kvalitě (odhad 2,5 Mbit/s místo 1 Mbit/s).
- Se zapnutými titulky se zvuk stahuje původním způsobem.

Ověření: sestavení APK a jednotkové testy.
