# Google Play release preparation

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

Confirm free distribution and target age groups with the developer when completing the account forms. Kaptus contains no advertising or in-app purchases. Its microphone permission supports an optional foreground auto-seek feature. It uses the Android document picker instead of broad media permissions.

The policy source is `docs/site/privacy.html`. It is published at the root of the `gh-pages` branch using GitHub Pages, and the public HTTPS URL has been verified. The app links to it from Settings. When updating the policy, update both the source and the published branch.

## EEA exclusion

In **Test and release > Production > Countries / regions**, select the intended markets and deselect every country in `eea-exclusions.csv`. It contains the 27 EU members plus Iceland, Liechtenstein and Norway. The United Kingdom and Switzerland are outside the EEA.

Keep open and closed test tracks synced to the same country list, or apply the same exclusions explicitly if a track is unsynced. Check the saved selection before rollout. Internal testing has no country targeting; use a controlled tester list for that track. These are planned settings until confirmed in Play Console.

## App access and review

Local SRT playback is accessible without an account. Reviewers can open an SRT using the document picker, choose its language, select manual timing and use playback controls. `review-demo.srt` is included for that path.

OpenSubtitles search and downloads require an API key. Declare that part of the app as restricted in the App access form and provide a dedicated reviewer API key plus setup instructions through Play Console's private access fields. Do not publish a personal API key in this repository or the public listing. A reviewer should not need to create an account or contact the developer to complete review. Provider download allowance must cover review.

Microphone permission is optional. To review English auto-seek, open English captions, set spoken language to English, permit the microphone, and play matching dialogue from a separate speaker. Once a scene is found, capture stops. The bundled English model requires no download. The optional multilingual model is available in Settings before reviewing the nine additional language pairs.

## Console sequence

1. Sign into Play Console using a normal supported browser. Google may block sign-in from Playwright-controlled browsers.
2. Check whether Kaptus already has a listing, its permanent package name, and the account's production eligibility.
3. Create the app if needed. Add the public support email, listing text, artwork and live privacy URL.
4. Complete App access, Ads, Target audience, Content rating and Data safety using the actual app behavior and the evidence in `DATA_SAFETY.md`.
5. Enroll in Play App Signing, upload the signed AAB and review Play's bundle checks and pre-launch report.
6. Set and verify the non-EEA country list for the intended testing or production track.
7. If Play requires closed testing, enroll the required testers and satisfy the testing period before applying for production access. Otherwise, complete the release review and submit.

## Sources

- [Google Play app setup and permanent package names](https://support.google.com/googleplay/android-developer/answer/9859152)
- [Signing an Android app](https://developer.android.com/studio/publish/app-signing)
- [Store asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Country targeting](https://support.google.com/googleplay/android-developer/answer/7550024)
- [Personal-account production testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Google sign-in and supported browsers](https://support.google.com/accounts/answer/7675428)
