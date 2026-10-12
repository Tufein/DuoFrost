# DuoFrost 2.5.0 — voortgang en vervolgrichting

Bijgewerkt op **12 oktober 2026**. Het oorspronkelijke voorstel van 8 oktober
heeft geleid tot twee alpha's en de volledige
[2.5.0-release](https://github.com/Tufein/DuoFrost/releases/tag/v2.5.0).
De release richt zich op Smart Scenes, een bruikbare presetbibliotheek en een
rustigere, duidelijkere interface. De maintainer meldde dat de toesteltests
goed zijn verlopen; modellen, firmware en meetresultaten zijn daarbij niet
afzonderlijk opgegeven.

## Opgeleverd in 2.5.0

| Onderdeel | Wat er beschikbaar is |
| --- | --- |
| Smart Scenes | Combineer apps, gamegroepen, uren, weekdagen, batterij en opladen. Kies een preset of begrens de helderheid van de huidige look. |
| Appgroepen | Groepeer bijvoorbeeld emulators of media-apps wanneer automatische gameherkenning tekortschiet. |
| Scene-uitleg en tijdelijke keuze | Het dashboard toont de actieve keuze. Houd een preset 15, 30, 60 of 120 minuten vast en hervat daarna de actuele regels. |
| Favorieten en collecties | Favorieten bovenaan, eigen collecties en filters die met zoeken samenwerken. |
| Effectvoorbeelden | Bekijk opgeslagen kleuren en ondersteunde animaties zonder fysieke verlichting te veranderen. Live-input en flitsende effecten krijgen een toegelicht stil voorbeeld. |
| Importcontrole | Selecteer presets en kies kopiëren, passende gebruikerspresets vervangen of naamconflicten overslaan. Appkoppelingen zijn optioneel. |
| Tijdelijk herstel | Maak één geschikte import of verwijdering tien minuten ongedaan zolang latere relevante wijzigingen ontbreken. |
| GUI en stabiliteit | Duidelijkere bediening, presetkaarten en filters; rustige appwissels, veiligere gegevensverwerking en back-ups met scenes en bibliotheekorganisatie. |

De release bouwt voort op BiFrost's bestaande effecten, app-profielen, aparte
linker/rechter kleuren, thema's en integraties, en op DuoFrost's achtergrondbediening,
helderheidslimieten, timers, mute, widgets en tijdschema's. Die bestaande functies
worden niet opnieuw als nieuwe 2.5.0-features gepresenteerd.

## Ontwikkelstappen

| Moment | Resultaat |
| --- | --- |
| 8 oktober | Roadmapvoorstel en inventarisatie van de bestaande code. |
| 10 oktober — alpha.1 | Smart Scenes, appgroepen, tijdelijke keuzes en vaste preset-identiteiten gepubliceerd. |
| 10 oktober — alpha.2 | Favorieten, collecties, voorbeelden, importcontrole en Undo gepubliceerd. 366 automatische tests en 26 Android-schermtests geslaagd, met een gecontroleerde update vanaf alpha.1. |
| 11 oktober — volledige 2.5.0 | De geteste basis geconsolideerd, interface en code verder afgewerkt, met een getekende APK en gebruikersdocumentatie. Exacte releaseverificatie staat in [technical.md](../technical.md). |

De eerdere 2.3.0, alpha.1 en alpha.2 zijn op 12 oktober naar ongepubliceerde
conceptreleases teruggezet. Hun tags, broncode en releasebestanden blijven
bewaard; ze worden niet meer als publieke downloads aangeboden. De
[volledige 2.5.0-release](https://github.com/Tufein/DuoFrost/releases/tag/v2.5.0)
is de actuele download. Er is geen afzonderlijke publieke beta of release
candidate nodig geweest om deze geteste basis als volledige release uit te brengen.

## Later onderzoeken

Deze ideeën zitten **niet in 2.5.0**. Ze zijn voorstellen voor een volgende
ontwikkelronde, zonder vast versienummer of beloofde releasedatum.

| Idee | Meerwaarde | Eerst aantonen |
| --- | --- | --- |
| Algemene scene-overgangen | Instelbaar vloeiend wisselen tussen presets en scenes. | Eén eigenaar voor de uitvoer; Stop en mute reageren direct. Dit onderdeel uit het oorspronkelijke kernvoorstel is doorgeschoven. |
| Palette Studio en Palette Flow | Eigen paletten bewaren, kleuren uit afbeeldingen halen en door een kleurenreeks bewegen. | Veilige beeldverwerking, voorspelbare uitvoer en gemeten kosten op AYN-hardware. |
| Preset-playlists | Een reeks looks met duur, herhalen en shuffle. | Duidelijke voorrang ten opzichte van scenes en tijdelijke keuzes. |
| Snelle scene-acties | Favorietensnelkoppelingen en eventueel een volgende-favorietactie. | Betrouwbare presetverwijzingen en goed gedrag bij verwijderde presets. Bestaande widgets en Quick Settings blijven beschikbaar. |
| Scene-pakketten | Bewerkbare Retro, Night, Calm en Neon voorbeelden. | Elk pakket legt de regels begrijpelijk uit en werkt zonder verrassende lichtwissels. |
| Verschillende effecten per stick | Bijvoorbeeld schermkleuren links en batterijstatus rechts. | Effecten overschrijven elkaar niet en delen capture, mute, limieten en Stop correct. |
| Audio- en ambientuitbreidingen | Meer controle over gevoeligheid en samplingregio's. | Capturetoestemming en lifecycle op echte apparaten valideren, daarna prestaties meten. |
| Adaptieve performance | Updatefrequentie aanpassen bij batterij- of thermische druk. | Hardwaremetingen onderbouwen het gedrag; er is geen beloofde batterij- of FPS-winst. |
| Meldingsverlichting | Korte lichtmeldingen voor gekozen apps, met stille uren. | Begrensde duur, duidelijke voorrang en een apart toestemmingsontwerp. |

## Vaste uitgangspunten voor volgende releases

- Dagelijkse bediening blijft eenvoudig; uitgebreider gedrag krijgt duidelijke keuzes.
- Zoeken, voorbeelden bekijken en imports voorbereiden veranderen de verlichting niet.
- Stop, mute, helderheidslimieten, plugins en timers behouden hun betekenis.
- Presets, sceneverwijzingen, oude imports en back-ups blijven compatibel.
- Nieuwe capturefuncties respecteren Android's toestemmingen; stil herstel na
  procesverlies wordt niet beloofd.
- Controllerbediening, leesbaarheid, korte landscape en updatebehoud blijven
  onderdeel van verificatie.
- AYN-compatibiliteit en prestatieclaims worden alleen per bevestigd toestel en
  firmware of op basis van metingen uitgebreid.
- Publieke documentatie beschrijft de geteste APK. Eerdere volledige releases blijven beschikbaar.
- Alleen volledige stabiele releases worden gepubliceerd. Beta's en andere
  pre-releases blijven concepten, tenzij de maintainer later expliciet anders vraagt.

Zie het [gebruikersoverzicht](2.5.0.md), de [changelog](../CHANGELOG.md),
het [featureonderzoek](feature-research-2.5.0.md) en
[technical.md](../technical.md) voor verdere context.
