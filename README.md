# C-log — Android v0.1

Pierwsza wersja projektu Android dla loggera krótkofalarskiego C-log.

## Założenia kompatybilności

- Java 17
- Android Gradle Plugin 9.3.0
- Gradle 9.5
- compileSdk 36
- targetSdk 36
- minSdk 26 (Android 8.0+)
- interfejs działa jako lokalny HTML/JavaScript wewnątrz Android WebView
- dane prototypu mogą być przechowywane lokalnie przez WebView/DOM storage

Telefon z Androidem 10 (API 29) mieści się bez problemu w zakresie minSdk 26.

## Najprostsza droga na komputerze

1. Zainstaluj aktualne Android Studio.
2. Otwórz cały folder `C-log_Android_v0.1` jako projekt.
3. Pozwól Android Studio pobrać wymagane komponenty Gradle/SDK.
4. Wybierz `app`.
5. Uruchom `assembleDebug` albo przycisk Run.
6. APK debug znajdziesz w:
   `app/build/outputs/apk/debug/app-debug.apk`

## Alternatywa: GitHub Actions

W `.github/workflows/build-apk.yml` jest gotowy workflow. Po wrzuceniu projektu na GitHub można uruchomić workflow ręcznie i pobrać wygenerowany artefakt APK.

## Ważne

To jest pierwsza warstwa aplikacyjna. QRZ, GPS, eksporty i integracje z zewnętrznymi usługami nie są jeszcze podłączone. Najpierw sprawdzamy stabilność podstawowego loggera i interfejsu.

## Źródło interfejsu

`app/src/main/assets/index.html` zawiera obecny prototyp C-log.
