# Google Play release preparation

## Current Console status

Kaptus 1.0.0 (version code 8) was submitted to **Closed testing - Alpha** on November 4, 2026. Publishing overview shows **Changes in review**. Managed publishing is off, so the closed test will publish after approval.

The listing is free, targets ages 13 and older, and uses the Video Players & Editors category. App access, content ratings, Data safety and the remaining app-content declarations are saved. The signed AAB passed Console processing with no release errors. The only release warning is about a missing deobfuscation file; this build does not use code shrinking.

The saved Alpha country list has 147 named markets plus Rest of world, with all 30 EEA countries excluded. The track is explicitly unsynced from production because production access is currently locked.

The account must run a closed test with at least **12 opted-in testers for 14 continuous days** before applying for production access. The `Kaptus closed test` email list currently contains the developer's account. Add the remaining testers in Console, then share the opt-in link after the test becomes available. Adding an address to the list does not opt that person in.

- [Publishing overview](https://play.google.com/console/u/0/developers/6067490903935725737/app/4975177013176012197/publishing)
- [Closed test](https://play.google.com/console/u/0/developers/6067490903935725737/app/4975177013176012197/tracks/4701086012298210700)
- [Tester opt-in link, available after approval](https://play.google.com/apps/testing/com.desigrit.kaptus)

## Release identity and files

The first Play release candidate is **Kaptus 1.0.0**, version code **8**, package **com.desigrit.kaptus**. The developer confirmed that this is Kaptus's first listing. Google Play package names are permanent. The normal development build retains `com.example.kaptus` and its existing data.

Build with JDK 17 and the SDK, NDK and CMake versions in the main README:

```powershell
./tools/build_play_release.ps1 -ApplicationId com.desigrit.kaptus -InitializeUploadKey
```

Use `-InitializeUploadKey` only when creating the first upload key. Later builds omit it. The script preserves an existing key, runs unit tests and release lint, and produces a signed bundle and APK under `build/play-release/1.0.0/`. The JSON manifest records checksums and the source commit. Upload the **AAB** to Play Console. Use the APK for direct device checks.

### Signing

Select Google-managed app signing in Play Console for a new app. The local upload key signs submissions; Google Play signs delivered installations with its app signing key.

The upload keystore and public certificate live in `.private/play-upload/`, which is excluded from Git. Its password is protected with Windows DPAPI and the directory is restricted to the current Windows user. Back up the keystore and save its password in a password manager before relying on this computer for future releases. The DPAPI file alone cannot recover the password on a different Windows account or machine.

For CI or a different machine, supply the existing keystore through `KAPTUS_UPLOAD_STORE_FILE`, `KAPTUS_UPLOAD_STORE_PASSWORD`, `KAPTUS_UPLOAD_KEY_ALIAS` and `KAPTUS_UPLOAD_KEY_PASSWORD`. Pass the same package ID and increasing version code to Gradle. Never generate a replacement key for an existing listing without checking its upload certificate.

## Listing material

| Play field | Prepared source |
| --- | --- |
| App name | `en-US/title.txt` |
| Short description | `en-US/short-description.txt` |
| Full description | `en-US/full-description.txt` |
| Release notes | `en-US/release-notes.txt` |
| App icon | `graphics/icon.png`, 512 x 512 |
| Feature graphic | `graphics/feature-graphic.png`, 1024 x 500 |
| Phone screenshots | The three opaque PNGs in `graphics/phone-screenshots/` |
| Privacy policy | `https://desigrit.github.io/kaptus/privacy.html` |
| Category | Video Players & Editors |
| Public support email | raunakoberoi@gmail.com |

Kaptus is free to install and targets the 13-15, 16-17 and 18+ age groups. Kaptus contains no advertising or in-app purchases. Its microphone permission supports an optional foreground auto-seek feature. It uses the Android document picker instead of broad media permissions.

The policy source is `docs/site/privacy.html`. It is published at the root of the `gh-pages` branch using GitHub Pages, and the public HTTPS URL has been verified. The app links to it from Settings. When updating the policy, update both the source and the published branch.

## EEA exclusion

In **Test and release > Production > Countries / regions**, select the intended markets and deselect every country in `eea-exclusions.csv`. It contains the 27 EU members plus Iceland, Liechtenstein and Norway. The United Kingdom and Switzerland are outside the EEA.

The exclusions have been saved and read back for Closed testing - Alpha: none of the 30 EEA names appears in its 148 selected targets. When production becomes available, apply the same exclusions there before rollout. Keep future test tracks synced to that country list or apply the exclusions explicitly. Internal testing does not support country targeting.

## App access and review

Local SRT playback is accessible without an account. Reviewers can open an SRT using the document picker, choose its language, select manual timing and use playback controls. `review-demo.srt` is included for that path.

OpenSubtitles search and downloads require an API key. App access is declared partially restricted. The developer authorized their existing key for Google's reviewers, and it is saved only in Console's private instructions. Search and download access were verified. The instructions explain the five-download daily allowance and provide a local SRT fallback. No username, password, account creation or payment is required. The optional setting to share access with trusted partner devices is off. Never put reviewer credentials in the repository or public listing.

Microphone permission is optional. To review English auto-seek, open English captions, set spoken language to English, permit the microphone, and play matching dialogue from a separate speaker. Once a scene is found, capture stops. The bundled English model requires no download. The optional multilingual model is available in Settings before reviewing the nine additional language pairs.

## Subsequent releases

1. Sign into the existing Kaptus listing using a supported browser. Keep the same package name and upload key, and increase the version code.
2. Update the listing and app-content declarations if the app's behavior changes. Keep the live privacy policy aligned with the source.
3. Upload the signed AAB to the appropriate track and review bundle checks and pre-launch results.
4. Verify the non-EEA country list and reviewer access before sending changes for review.
5. For the first public release, complete the closed test, apply for production access, and set production country targeting before rollout.

## Sources

- [Google Play app setup and permanent package names](https://support.google.com/googleplay/android-developer/answer/9859152)
- [Signing an Android app](https://developer.android.com/studio/publish/app-signing)
- [Store asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Country targeting](https://support.google.com/googleplay/android-developer/answer/7550024)
- [Personal-account production testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Google sign-in and supported browsers](https://support.google.com/accounts/answer/7675428)
