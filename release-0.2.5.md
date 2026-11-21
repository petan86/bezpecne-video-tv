# Bezpečné video 0.2.5

Oprava samovolného vypínání během přehrávání a přehlednější obraz při sledování.

## Opravy

- Přehrávání se už nepřeruší přibližně po 5 minutách. Televize během hrajícího videa
  nespustí spořič obrazovky (ambient mode), který dříve aplikaci odsunul na pozadí
  a video zastavil. Po pozastavení nebo dohrání videa se spořič chová jako obvykle.
- Totéž platí pro záložní přehrávač.

## Novinky

- Dlaždice **Kvalita** a **Titulky** se po 10 sekundách bez stisku ovladače schovají.
- Stisk šipky nebo OK je znovu zobrazí a vybere **Kvalita**. První stisk dlaždice jen
  ukáže, aby omylem nespustil žádnou akci. Tlačítko Zpět funguje beze změny.

Ověření: sestavení APK a jednotkové testy.
