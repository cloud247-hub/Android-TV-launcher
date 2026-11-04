# cloud247 TV Launcher

En superlett startskjerm for Android TV – laget for å gjøre **Philips Android TV** (og andre trege TV-er) raskere.
Erstatter den tunge standard-startskjermen (anbefalinger, reklame, bakgrunnsoppdateringer) med et enkelt
rutenett av apper i cloud247-design.

## Hvorfor raskere?

| Standard Philips/Google TV-startskjerm | cloud247 TV Launcher |
| --- | --- |
| Henter anbefalinger og reklame fra nett | Ingen nettverkskall i det hele tatt |
| Bakgrunnstjenester og kanaloppdatering | Ingen tjenester, ingen bakgrunnsjobber |
| Store biblioteker (Leanback, Compose, Play Services) | Kun `RecyclerView`, APK på ~1 MB |
| Animasjoner og video-forhåndsvisning | Én kort fokus-animasjon (120 ms) |

Inspirert av [Plume Launcher](https://github.com/aradhyas8/Plume_Launcher) (bl.a. HOME-knapp via tilgjengelighetstjeneste i stedet for å deaktivere standard-launcheren), men bevisst enda enklere: ingen bakgrunnsbilder, ingen «Fortsett å se», ingen nedlastinger.

Teknisk: ikoner/bannere lastes på bakgrunnstråd og caches i minnet, appliste oppdateres kun når pakker endres,
bakgrunnen tegnes direkte av vinduet (ingen overdraw), og alt annet er ren Android-rammeverkskode.

## Funksjoner

- **Favoritter** øverst (YouTube, Netflix, NRK TV, Disney+, Prime, Max, Viaplay, TV 2 Play osv. legges inn automatisk hvis installert)
- **Alle apper** i et rutenett med app-bannere, sortert alfabetisk
- **Live TV**-knapp (åpner Philips Watch TV / `org.droidtv.playtv`)
- **Kilder**-knapp: lister HDMI-innganger/antenne og bytter direkte
- **Innstillinger**-knapp, klokke og dato
- **HOME-knapp-støtte** uten å deaktivere Philips' egen startskjerm (se under)
- **Hold inne OK** (eller trykk MENU) på en app: legg til/fjern favoritt, flytt venstre/høyre, skjul, appinfo, avinstaller
- **Tilpass**: vis skjulte apper igjen, tilbakestill favoritter, velg standard startskjerm
- Norsk tekst (engelsk hvis TV-en står på engelsk)

## Design / farger

Alle farger ligger i én fil: [`app/src/main/res/values/colors.xml`](app/src/main/res/values/colors.xml).
Bytt hex-kodene der for å matche cloud247.no nøyaktig – resten av appen følger automatisk.

## Last ned APK

GitHub Actions bygger APK-en ved hver push:
**Actions → Build APK → siste kjøring → Artifacts → `cloud247-tv-launcher`**.
Lag en tag (`git tag v1.0.0 && git push --tags`) for å få APK-en som en GitHub Release.

### Fast signeringsnøkkel (anbefalt)

Uten oppsett signeres APK-en med en tilfeldig debug-nøkkel per bygg, og du må avinstallere før oppdatering.
Lag en egen nøkkel én gang:

```bash
keytool -genkeypair -keystore launcher.jks -alias cloud247 -keyalg RSA -keysize 2048 -validity 10000
```

```bash
base64 -w0 launcher.jks
```

Legg så inn disse under **Settings → Secrets and variables → Actions** i repoet:
`LAUNCHER_KEYSTORE_BASE64` (base64-teksten), `LAUNCHER_KEYSTORE_PASSWORD`, `LAUNCHER_KEY_ALIAS` (`cloud247`), `LAUNCHER_KEY_PASSWORD`.
Ikke legg nøkkelfilen i git.

## Installere på Philips TV

1. På TV-en: **Innstillinger → System → Om → trykk 7 ganger på «Build»** for å aktivere utvikleralternativer.
2. **Innstillinger → System → Utvikleralternativer → USB-feilsøking / Nettverksfeilsøking: På**.
3. Finn TV-ens IP-adresse (Innstillinger → Nettverk) og installer fra PC-en:

```bash
adb connect 192.168.1.50:5555
```

```bash
adb install -r cloud247-tv-launcher.apk
```

Alternativt: legg APK-en på en USB-minnepinne og installer med en filbehandler (f.eks. «File Commander»/«X-plore»).

4. Åpne **cloud247 TV** fra app-listen én gang.

### Få HOME-knappen til å åpne cloud247

> ⚠️ **Ikke deaktiver standard-launcheren** (`pm disable-user com.google.android.tvlauncher`).
> På Philips kan det ødelegge oppsett, kildebytte (HDMI) eller oppstart, og i verste fall kreve fabrikktilbakestilling.
> cloud247 legger seg i stedet *over* standard-launcheren.

**Alternativ A – hvis TV-en spør:** trykk HOME, velg **cloud247 TV** → **Alltid**.
(Eller: *Tilpass → Velg standard startskjerm*.)

**Alternativ B – Philips/Google TV der HOME er låst (vanligst):**
I launcheren: **Tilpass → HOME-knappen åpner cloud247**, og slå på **«cloud247: HOME-knapp»** under Tilgjengelighet.
Tjenesten leser kun HOME-tasten og merker når standard-startskjermen dukker opp (f.eks. etter oppstart) – ingen skjerminnhold, ingen data.

På Android 13+ kan bryteren være grå («begrenset innstilling») for apper som er installert utenfor Play.
Da: **Innstillinger → Apper → cloud247 TV → ⋮ → Tillat begrensede innstillinger**, eller slå den på direkte via adb:

```bash
adb shell settings put secure enabled_accessibility_services no.cloud247.tvlauncher/no.cloud247.tvlauncher.HomeButtonService
```

```bash
adb shell settings put secure accessibility_enabled 1
```

(Merk: den første kommandoen erstatter eventuelle andre aktive tilgjengelighetstjenester.)
Angre: slå av tjenesten under Tilgjengelighet, eller:

```bash
adb shell settings delete secure enabled_accessibility_services
```

### Ekstra fartstips (valgfritt, reversibelt)

Halver systemanimasjonene (sett tilbake til `1` for å angre):

```bash
adb shell settings put global window_animation_scale 0.5
```

```bash
adb shell settings put global transition_animation_scale 0.5
```

```bash
adb shell settings put global animator_duration_scale 0.5
```

## Bygge selv

Krever Android Studio eller Android SDK (compileSdk 35) og JDK 17:

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`.
