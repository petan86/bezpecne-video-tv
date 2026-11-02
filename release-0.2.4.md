# Bezpečné video 0.2.4

Rodičovská správa se spouští automaticky s aplikací na pevném portu 32795.
Při rezervované IP adrese TV 192.168.68.108 lze používat stále stejnou záložku:
http://192.168.68.108:32795/

Není nutné otevírat QR kód ani opisovat klíč. Stránka automaticky získá aktuální
relaci, včetně po restartu aplikace. Přístup nemá časový limit a je dostupný
zařízením v domácí síti. Kontrola původu požadavků zůstává aktivní.

IP adresu je potřeba rezervovat v routeru. Server běží po dobu běhu aplikace;
při vypnutí TV nebo ukončení aplikace není dostupný. Po opětovném spuštění aplikace
obnovte stránku na PC. Nejde o službu spouštěnou při startu Androidu.

Ověření: test přidání videa bez QR odkazu, opětovné použití portu po restartu serveru,
odmítnutí požadavku z cizí stránky, testy webového rozhraní a sestavení APK.
