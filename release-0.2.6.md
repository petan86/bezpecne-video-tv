# Bezpečné video 0.2.6

Automatické pokračování dalším videem a omezení kvality na 720p.

## Novinky

- Po dohrání videa se zobrazí odpočet „Další video za 5 s“ a pak se samo spustí
  následující video z galerie (sekce „Videa z povolených kanálů“). Tlačítko Zpět
  odpočet zruší a přehrávač zavře. Přehrávají se jen povolená videa; na konci
  seznamu přehrávání skončí. Při odchodu z aplikace během odpočtu se další video
  nespustí.
- Po návratu z přehrávače je v galerii vybrané naposledy přehrané video.

## Změny

- Nativní přehrávač nabízí a spouští nejvýše rozlišení 720p. Pokud YouTube nic
  do 720p nenabídne, použije se nejnižší dostupná kvalita.

Ověření: sestavení APK, jednotkové testy a testy webového rozhraní.
