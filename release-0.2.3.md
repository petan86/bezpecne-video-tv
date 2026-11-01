# Bezpečné video 0.2.3

- Nativní přehrávač automaticky upřednostní českou zvukovou stopu před jinými jazyky, i když mají vyšší datový tok.
- Pokud čeština není dostupná, přednost má původní jazyk videa.
- Běžný český zvuk má přednost před českým zvukovým popisem.
- Při dostupné české stopě a samostatném obrazu se nepoužívají varianty s vloženým cizojazyčným zvukem. Volba češtiny tak zůstává zachovaná při změně rozlišení.

Ověřeno automatickými testy výběru jazyka a sestavením APK. Přehrávání na Chromecastu ani jazykové možnosti záložního webového přehrávače tímto testem ověřeny nebyly. Výběr závisí na stopách a jazykových údajích, které poskytne YouTube extraktor.
