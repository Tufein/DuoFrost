# DuoFrost — overdracht naar cloud en Claude

Deze notitie bewaart de projectcontext voor voortzetting op mobiel en voor de
geplande volledige overdracht aan Claude. Het project staat op
[GitHub: Tufein/DuoFrost](https://github.com/Tufein/DuoFrost), met `main` als basis.
Sinds 12 oktober 2026 onderhoudt Claude het project; deze notitie blijft de
startcontext voor nieuwe sessies.

## Huidige release

**2.5.1 is de nieuwste volledige, publieke release**, Android-versiecode `29`.
[Download de APK](https://github.com/Tufein/DuoFrost/releases/download/v2.5.1/DuoFrost-2.5.1.apk)
of bekijk [de release met broncode](https://github.com/Tufein/DuoFrost/releases/tag/v2.5.1).

Deze patch van 16 oktober 2026 sluit een achtergebleven welkomstvenster bij het
opnieuw aanmaken van het appscherm, gemeld op een Thor Max met Android 13.
381 JVM-tests en 30 Android 13-schermtests zijn geslaagd. De productiesleutel
blijft gelijk en 2.5.0 kan zonder verwijderen worden bijgewerkt. De open fixes
voor Smart Scenes, Undo en imports zijn onafhankelijk en zitten niet in deze patch.
Zie [patchdetails](2.5.1.md) en [technical.md](../technical.md).

### Vorige volledige release: 2.5.0

De APK bevat broncodecommit `d93bdd4`; tag `v2.5.0` staat op `474d6f2`.
Latere wijzigingen gaan over documentatie en publicatiebeleid. Op 12 oktober
2026 was de gepushte basis `76c1804`, vóór toevoeging van deze notitie en het
bijgewerkte Reddit-materiaal. Gebruik de actuele `main` voor verdere werkzaamheden.

De release doorstond 376 JVM-tests, 28 Android-schermtests, releasecontroles en
GitHub Actions. Een update over alpha.2 behield presets, scènes, groepen,
collecties, appkoppelingen, de helderheidslimiet en de gestopte verlichting.
De eigenaar meldde geslaagde toesteltests. Er is geen volledige lijst met
geteste toestellen en firmwareversies; fysieke LED-uitvoer en batterijverbruik
zijn niet door de emulator bewezen. Details staan in [technical.md](../technical.md).

## Wat behouden moet blijven

- DuoFrost is LED control voor **AYN devices**, niet uitsluitend AYN Thor.
- De hoofdfeatures van 2.5.0 zijn de vernieuwde GUI, Smart Scenes,
  favorieten/collecties, effectpreview en import met tijdelijke Undo.
- De hidden feature houdt het appvenster uit Androids **Recent apps**
  (`excludeFromRecents`, met `singleTop`). Opnieuw openen kan via het appicoon;
  Start/Stop bedient de verlichting. Dit garandeert geen permanente werking
  op de achtergrond.
- Android 13 of nieuwer is vereist. DuoFrost installeert naast BiFrost;
  gebruik één hardware-LED-controller tegelijk.
- Production 2.1.0, 2.3.0 en beide 2.5.0-alphas kunnen in-place bijwerken.
  Originele versie 1/2 gebruikt een andere sleutel en vereist een volledige
  externe backup, verwijderen, opnieuw installeren en herstellen.
- Bewaar de bestaande release-signing key voor volgende updates. Die is
  **privé en lokaal**, niet onderdeel van GitHub of deze cloudoverdracht.
  Upload geen keystore, wachtwoorden of toegangstokens. De publieke
  certificaatvingerafdruk staat in [technical.md](../technical.md).
  Zonder dezelfde sleutel kan de cloud geen compatibele productie-APK tekenen.

## Publicatiebeleid

Alleen stabiele releases worden publiek gepubliceerd. `v2.3.0`,
`v2.5.0-alpha.1` en `v2.5.0-alpha.2` zijn teruggezet naar concept; bestanden,
tags en geschiedenis zijn behouden. `v2.2.0` was al een concept.
Publiek blijven `v1.0.0`, `v2`, `v2.1.0`, `v2.5.0` en `v2.5.1`, met `v2.5.1` als nieuwste.
Nieuwe beta's, alphas en overige pre-releases blijven concepten, tenzij de
eigenaar expliciet anders vraagt. Vervang of verwijder oudere stabiele releases niet.

## Reddit en afbeeldingen

De aankondiging is voorbereid voor 12 oktober 2026; de GitHub-release is al
publiek. Reddit-publicatie is nog een handeling van de eigenaar.

- [Direct te plaatsen Reddit-post](reddit-2.5.0.md), inclusief hidden feature,
  update-instructies, credits en de korte AI-regel: “Development assisted by AI.”
- [Afbeeldingen, volgorde en captions](reddit-2.5.0-assets.md).
- [Compleet downloadpakket voor mobiel](../assets/marketing/2.5.0/DuoFrost-2.5.0-Reddit.zip)
  met tekst, captions, cover en drie originele screenshots.
- [Releasecover](../assets/marketing/2.5.0/duofrost-2.5.0-release-cover.png).
- Echte voorbeeldscreenshots van de getekende release in de emulator:
  [dashboard](../assets/screenshots/2.5.0-dashboard.png),
  [effectpreview](../assets/screenshots/2.5.0-preview.png) en
  [Smart Scenes](../assets/screenshots/2.5.0-scenes.png).

De cover is een promotioneel beeld gemaakt met imagegen. De losse screenshots
zijn originele emulatorcaptures met voorbeeldpresets. De previewringen tonen
een illustratie van effecten, geen opname van fysieke LEDs.

## Verder werken

Begin met [README](../README.md), [gebruikersgids](2.5.0.md),
[CHANGELOG](../CHANGELOG.md), [technical.md](../technical.md) en
[roadmap](roadmap-2.5.0.md). De roadmap onderscheidt opgeleverde functies van
toekomstige voorstellen; presenteer voorstellen niet als bestaande features.

De huidige vervolgvraag is mobiele toegang tot deze context. Nieuwe code,
builds, publicaties of berichten aan anderen volgen pas op een nieuwe opdracht.
Voor de volledige export naar Claude moeten broncode, releasegegevens,
technische documentatie, roadmap en marketingmateriaal samen worden meegenomen.
Een gitcheckout is voldoende voor code en gedeelde documenten; lokale
toolchains en privé signingconfiguratie blijven afzonderlijke lokale onderdelen.

Communiceer met de eigenaar in eenvoudig Nederlands. Houd gebruikersdocumentatie
kort en verplaats technische uitleg naar `technical.md`.
