# DuoFrost 2.5.0 — roadmapvoorstel

Opgesteld op 8 oktober 2026. Dit is een ontwikkelvoorstel: de nieuwe onderdelen
zijn nog niet gebouwd of als release toegezegd. De volgorde volgt afhankelijkheden;
er is nog geen vaste releasedatum.

## Richting

**Slimme verlichting die vanzelf bij je sessie past, met eenvoudige bediening.**

De hoofdfeature wordt **Smart Scenes**: één begrijpelijke plek voor regels op basis
van apps, games, tijd en batterijstatus. Daarnaast krijgt DuoFrost betere tools om
presets te organiseren, vooraf te bekijken, te maken en veilig te delen.

Voorbeeld: een gebruiker opent een emulator en krijgt zijn Retro-scene. Na 22:00
wordt die gedimd; bij een lage batterij verlaagt een gekozen regel de helderheid
verder. Een tijdelijk handmatig gekozen scene blijft actief tot de gekozen eindtijd.
Het dashboard laat zien welke regel geldt en waarom.

De professionele stijl van 2.3.0 blijft de basis. Nieuwe functies krijgen duidelijke
namen en eenvoudige standaardkeuzes; uitgebreide opties staan achter een aparte
instelling. Veel functies toevoegen mag de dagelijkse bediening niet ingewikkelder
maken.

## Bestaande basis

- GitHub heeft [2.1.0 als stabiele release](https://github.com/Tufein/DuoFrost/releases/tag/v2.1.0)
  en [2.3.0 als pre-release](https://github.com/Tufein/DuoFrost/releases/tag/v2.3.0).
  Gecontroleerd op 8 oktober 2026.
- De lokale commit `0daaea4` voegt Game Scene toe. Die vormt de eerste bouwsteen
  voor Smart Scenes en zit nog niet in de gepubliceerde 2.3.0-APK.
- Schermkleuren, audio-reactie, 15 effecten, aparte linker/rechter kleuren,
  app-profielen, adaptieve helderheid, batterijlimieten, laadmeldingen, tijdschema's,
  widgets, een Quick Settings-tegel, back-ups en presetdeling bestaan al.
- Tijdschema's ondersteunen al weekdagen, nachtelijke periodes en seizoensvensters.
  De huidige bibliotheek heeft al zoeken, eigen afbeeldingen en handmatig sorteren.
- Er is al een overgang wanneer een externe lichtovername eindigt. Algemene
  overgangen tussen scenes en presets zijn een uitbreiding daarvan.

Deze functies worden in 2.5.0 verbeterd of gecombineerd, niet opnieuw als nieuw
gepresenteerd.

## Beoogde features

**Kern** betekent noodzakelijk voor de voorgestelde release. **Uitbreiding** betekent
gewenst nadat de kern stabiel is; deze onderdelen bepalen de releasedatum niet.

| Feature | Wat de gebruiker krijgt | Prioriteit | Omvang |
| --- | --- | --- | --- |
| 1. Smart Scenes | Regels zoals "als deze appgroep actief is en het avond is, gebruik deze preset met dit helderheidsmaximum". Voorinstellingen en zichtbare regelvoorrang. | Kern | Groot |
| 2. Game- en appgroepen | Eigen groepen zoals Retro, Android Games en Media. Handmatig apps toevoegen of uitsluiten wanneer automatische gameherkenning tekortschiet. | Kern | Middel |
| 3. Scene-status en tijdelijke keuze | Het dashboard toont waarom een scene actief is. Automatisering tijdelijk pauzeren of een scene vasthouden tot een gekozen tijd; daarna opnieuw de actuele situatie beoordelen. | Kern | Middel |
| 4. Favorieten en collecties | Favorieten bovenaan, eigen collecties en filters. De bestaande zoekfunctie blijft beschikbaar. | Kern | Middel |
| 5. Visueel effectvoorbeeld | Een geanimeerd voorbeeld van de ondersteunde kleuren en effecten vóór toepassen. Bekijken verandert de fysieke verlichting niet. | Kern | Middel tot groot |
| 6. Veiliger importeren | De inhoud van een presetpakket vooraf bekijken, items selecteren en per conflict toevoegen, vervangen, hernoemen of overslaan. | Kern | Middel |
| 7. Herstel na een fout | Een import of verwijdering ongedaan maken en een begrensde lokale geschiedenis van opgeslagen presets. De bewaargrens wordt tijdens implementatie gekozen. | Kern | Middel |
| 8. Vloeiende scene-overgangen | Instelbare overgang tussen presets en automatische scenes, met een directe overgang als optie. Stop en mute reageren direct. | Kern | Middel tot groot |
| 9. Snelle scene-acties | Een optionele favorieten-tegel, volgende favoriet en app-snelkoppelingen. Zelfde status en selectie als in het dashboard. | Uitbreiding | Middel |
| 10. Palette Studio | Kleurenpaletten bewaren en kleuren lokaal uit een gekozen afbeelding halen. Eerst een voorbeeld en handmatige correctie, daarna toepassen. | Uitbreiding | Middel |
| 11. Palette Flow | Een nieuw effect dat door een zelfgekozen reeks kleuren loopt, met regelbare snelheid en optionele vertraging tussen links en rechts. Geen claim van afzonderlijk adresseerbare LED-pixels. | Uitbreiding | Middel |
| 12. Kant-en-klare scene-pakketten | Geteste Retro, Night, Calm en Neon voorbeelden die de nieuwe functies uitleggen. Pakketten werken lokaal en zijn vrij te bewerken. | Uitbreiding | Klein |

**Gebruiksgemak als vaste release-eis:** onboarding voor scenes, volledige
controller-/D-pad-navigatie in de nieuwe schermen, duidelijke focus, leesbare
grote tekst en een toestelcheck met het bestaande veilige LED-testprogramma.
Dit bouwt voort op de GUI van 2.3.0.

## Ideeën die eerst een prototype nodig hebben

| Idee | Meerwaarde | Beslissing vóór opname |
| --- | --- | --- |
| Verschillende effecten per stick | Bijvoorbeeld schermkleuren links en batterijstatus rechts. Aparte kleuren bestaan al; verschillende effecten zijn nieuw. | Eerst aantonen dat twee effecten tegelijk geen zones overschrijven en capture, mute, limieten en Stop correct delen. Dit is de kandidaat voor een tweede grote hoofdfeature. |
| Preset-playlists | Een eigen reeks looks met duur per preset, herhalen en shuffle. | Pas toevoegen als de scene-selectie, tijdelijke keuzes en overgangen één eigenaar hebben. |
| Adaptieve performance | De updatefrequentie automatisch afbouwen onder batterij- of thermische druk, vervolgens herstellen. | Meten op hardware; geen beloofde batterijwinst of FPS-winst zonder meting. Bestaande handmatige performanceprofielen blijven beschikbaar. |
| Audio 2.0 | Instelbare attack/release, automatische gevoeligheid en eventueel afzonderlijke frequentiebanden. | Eerst capture en permissies op echte apparaten valideren, daarna signaalverwerking en kosten meten. |
| Ambient 2.0 | Meer controle over samplingregio's en gedrag bij donkere randen. | Bestaande sampling exact inventariseren; displaywissels, rotatie en beschermde inhoud testen. |
| Meldingsverlichting | Een korte lichtmelding voor expliciet gekozen apps, met stille uren. | Apart toestemmingsontwerp, beperkte duur en duidelijke prioriteit. Geen opslag van meldingsinhoud. |

Deze ideeën blijven buiten de vaste releasekern totdat het prototype en de
hardwaretest voldoende bewijs leveren. Ze kunnen naar een latere release.

## Roadmap en oplevermomenten

| Stap | Werk | Klaar wanneer |
| --- | --- | --- |
| 0. Basis vastleggen | Bestaande Game Scene opnemen in de ontwikkelbasis; compatibiliteit van presets, schema's en back-ups vastleggen; stabiele preset-ID's en migratie ontwerpen. | Oude bestanden en verwijzingen naar presets zijn afgedekt door migratietests. De exacte regels voor Stop, mute, plugins, schema's en scenes liggen vast. |
| 1. Eerste 2.5.0-alpha | Smart Scenes, game-/appgroepen, tijdelijke keuze en uitleg in het dashboard. | Overlappende regels geven steeds dezelfde keuze. Wisselen tussen game, launcher en app werkt; Stop blijft gerespecteerd. |
| 2. Tweede alpha | Favorieten, collecties, visueel voorbeeld, selectieve import en herstel. | Zoeken, bekijken en import voorbereiden veranderen nooit onbedoeld de verlichting. Hernoemen, verwijderen en importeren houden verwijzingen intact. |
| 3. Feature freeze | Algemene overgangen; gewenste uitbreidingen toevoegen op basis van beschikbare tijd en testresultaten. | De kern is compleet. Alleen bewezen uitbreidingen worden opgenomen; overige ideeën krijgen een later doel. |
| 4. 2.5.0-beta | Gebruiksgemak, prestaties, migraties en fysieke AYN-tests. | De belangrijkste flows werken in portrait, korte landscape en met grote tekst/controller. Bevestigde modellen en firmware worden genoteerd. |
| 5. Release candidate | Getekende APK, update- en hersteltest, definitieve screenshots en changelog. | Installeren over ondersteunde productieversies behoudt instellingen. De APK, broncode, signingcertificaat en checksums komen overeen. Geen open releaseblokker. |
| 6. 2.5.0-release | GitHub-download, gebruikersdocumentatie, technische notities, Reddit-post en banner. | Alle publieke teksten beschrijven alleen wat in de geteste APK zit. Bestaande releases blijven beschikbaar. |

Alpha en beta zijn voorgestelde testmomenten; er zijn nog geen tags of releases
aangemaakt. Een aparte 2.4.0 is geen technische vereiste. Of Game Scene eerder
apart wordt uitgebracht, staat los van deze 2.5.0-roadmap.

## Technische aanpak

### Eén plek bepaalt de scene

Een pure selector krijgt de actuele voorwaarden en kiest de basis-scene. Die geeft
ook de bronregel en uitleg terug. Brightness-limieten en mute worden daarna op de
uitvoer toegepast. Een scene-regel kan zo een maximum niet omzeilen.

- Gebruikers-Stop, timerexpiry en uitschakelregels behouden hun vastgelegde betekenis.
  Stop wordt niet behandeld als een tijdelijk handmatig gekozen effect.
- Externe plugins houden hun bestaande toestemmingen, voorrang en leasegedrag.
- Exacte appregels krijgen een duidelijke verhouding tot groepen, Game Scene en
  tijdsregels. Gelijke prioriteiten hebben een stabiele beslisregel.
- Een tijdelijke handmatige keuze eindigt met een nieuwe evaluatie, niet met een
  verouderde momentopname van het vorige effect.
- Debouncing en hysteresis voorkomen snel heen-en-weer schakelen bij appwissels
  en batterijgrenzen.
- Voorbeelden en het testen van regels krijgen een aparte route die geen
  achtergrondverlichting start.

### Opslag en code opruimen tijdens het bouwen

- Presets krijgen stabiele interne ID's. Appregels, schema's, widgets en scenes
  verwijzen daarna naar ID's; oudere naamverwijzingen worden gemigreerd.
  De bestaande externe API blijft presetnamen ondersteunen; installeren en
  bijwerken via die API behouden plugin-eigendom en ownerPackage.
- Een gedeelde presetrepository vervangt verspreide parsing en bewaarlogica.
  Een nieuw opslagframework is alleen nodig als de migratie dat rechtvaardigt.
- Scene-configuratie krijgt een versieerbaar formaat met validatie en veilige
  defaults. Bewerkingen zijn terug te draaien voordat nieuwe toestand wordt toegepast.
- Import wordt eerst volledig gevalideerd; naam-/ID-conflicten en afhankelijkheden
  worden in een importplan opgelost voordat bestaande gegevens wijzigen.
- Scene-editor, bibliotheek en importcontroller worden uit MainActivity gehouden.
  Selectie, overgangen en herstel worden stapsgewijs uit LEDService losgetrokken.
- Capture-tokens, actieve pluginleases en tijdelijke runtimebeslissingen worden
  niet als blijvende presetconfiguratie geback-upt.

## Grenzen en onderzoek

- Android kan een appcategorie ongedefinieerd laten. Daarom zijn handmatige
  gamegroepen en uitzonderingen noodzakelijk; herkenning van een afzonderlijke
  ROM binnen dezelfde emulator vraagt medewerking van die emulator of launcher.
  Zie [ApplicationInfo](https://developer.android.com/reference/android/content/pm/ApplicationInfo).
- Een nieuwe MediaProjection-sessie vraagt toestemming. De roadmap belooft
  daarom geen stil, permanent herstel van audio-/projectiecapture na procesverlies.
  Zie [Media projection](https://developer.android.com/media/grow/media-projection).
- Android-audiocapture vereist onder meer RECORD_AUDIO en toestemming, en de
  bronapp moet capture toelaten. De permissies in het uiteindelijke manifest en
  de runtimeflow worden vóór Audio 2.0 gecontroleerd. De bronmanifest-audit vond
  geen RECORD_AUDIO; dat is een verificatiepunt, geen aangetoonde fout bij iedere
  huidige gebruiker. Zie [Capture video and audio playback](https://developer.android.com/media/platform/av-capture).
- De bestaande hardwaretransportcode gebruikt PServerBinder en vaste sn3112l/r
  paden. Compatibiliteit wordt per AYN-model en firmware getest; de productnaam
  blijft gericht op AYN devices, met een eerlijke lijst van bevestigde toestellen.
- De eerste alpha bepaalt welke voorwaarden met bestaande events en alarmen
  kunnen werken. Nieuw permanent achtergrondpolling wordt niet standaard toegevoegd.

Bij herziening van de roadmap wegen hardwaremetingen, feedback op de eerste alpha,
migratiekosten en de complexiteit van verschillende effecten per stick het zwaarst.

## Releasecontroles

- Selectie: exacte appregel, groepen, handmatige uitzondering, launcherfallback,
  nachtelijke periode, opladen, batterijgrens en gelijke prioriteiten.
- Controle: Stop, mute, tijdelijke keuze, timer en externe plugin; ook na
  procesherstel en wanneer voorwaarden tijdens de overname wijzigen.
- Data: oude presets/back-ups, naam-naar-ID-migratie, dubbele importnamen,
  beschadigde archieven, herstel en verwijderde presets.
- Android: geweigerde/ingetrokken toestemming, slaap/wake, rotatie, herstart en
  op Thor beide schermen. Een beëindigd token wordt niet hergebruikt voor een
  nieuwe MediaProjection-sessie; een geldige actieve sessie mag scene-wissels ondersteunen.
- GUI: controller/keyboard, focusvolgorde, grote tekst, contrast en korte landscape.
- Hardware: echte kleuren per ondersteunde zone, limieten, overgangen, capture
  en CPU/batterijbelasting. Emulatorresultaten vervangen deze tests niet.
- Publicatie: versiecode hoger dan alle eerder gepubliceerde codes, dezelfde
  productiesigningkey, geslaagde tests/lint, APK-verificatie, bijbehorende broncode
  en checksum. Versienummer en publieke documenten worden pas voor de concrete
  geteste build aangepast.

## Codebasis voor de planning

- [AppProfileSelection](../app/src/main/java/io/github/tufein/duofrost/services/AppProfileSelection.kt)
  en [AppProfileManager](../app/src/main/java/io/github/tufein/duofrost/services/AppProfileManager.kt).
- [ScheduleRule](../app/src/main/java/io/github/tufein/duofrost/schedule/ScheduleRule.kt)
  en [ScheduleEvaluator](../app/src/main/java/io/github/tufein/duofrost/schedule/ScheduleEvaluator.kt).
- [PresetController](../app/src/main/java/io/github/tufein/duofrost/presets/PresetController.kt),
  [LedPreset](../app/src/main/java/io/github/tufein/duofrost/presets/LedPreset.kt)
  en [PresetLibrarySearch](../app/src/main/java/io/github/tufein/duofrost/ui/PresetLibrarySearch.kt).
- [LEDService](../app/src/main/java/io/github/tufein/duofrost/services/LEDService.kt)
  en [LedController](../app/src/main/java/io/github/tufein/duofrost/tools/LedController.kt).
- [GUI van 2.3.0](gui-refresh.md), [changelog](../CHANGELOG.md)
  en [technische notities](../technical.md).
