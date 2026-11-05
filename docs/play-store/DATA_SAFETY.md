# Data safety preparation

This evidence is for the Android release source on this branch. Complete the live form after checking the provider's current data practices. The app's local-only microphone processing does not make the optional online provider path a no-data-collection app.

## Data flows

| Data | Destination | Purpose | User choice and retention |
| --- | --- | --- | --- |
| Microphone PCM and recognized speech | Device memory only | Locate the scene in captions | Optional microphone permission; no audio files or network upload |
| Imported and downloaded subtitle contents | App-private files | Caption playback and matching | User chooses a file or requests download; cleared with app storage or uninstall |
| History, languages and caption timing mappings | App-private database and settings | Restore prepared titles and preferences | Stored locally; app data is excluded from backup |
| OpenSubtitles API key | OpenSubtitles API over HTTPS | Authenticate provider requests | Optional provider setup; encrypted locally |
| OpenSubtitles username and password | OpenSubtitles login endpoint over HTTPS | Optional account authentication | Optional even within provider setup; encrypted locally |
| Search text, movie/episode IDs, caption language, subtitle IDs | OpenSubtitles API over HTTPS | Find and download selected captions | User initiates provider features; provider controls server retention |
| Network IP address, standard request headers | OpenSubtitles and subtitle delivery hosts | Deliver requests, provider access limits | Present on network requests; provider logging and use must be checked |
| Model identifier and network IP address | Hugging Face and delivery hosts | Download the optional multilingual model | Explicit user download; no credentials or speech sent |

## Proposed form treatment

- **Data collected: Yes**, for optional provider and model-hosting requests that transmit data off the device.
- **App activity / In-app search history:** title queries sent to OpenSubtitles; optional; app functionality.
- **Personal info / User IDs:** account username and account-linked API key; optional; app functionality and account management.
- **Personal info / Other info:** optional account authentication secret, subject to the current form's exact definitions; app functionality and account management.
- **Audio files / Voice or sound recordings:** not collected off-device. Do not mark audio as uploaded merely because RECORD_AUDIO is requested.
- **Files and docs:** selected SRT files are read locally, not uploaded.
- **Data encrypted in transit:** app endpoints use HTTPS; Android cleartext traffic is disabled. Provider download URLs are required to use HTTPS.
- **Account creation:** Kaptus does not create app accounts. OpenSubtitles accounts belong to the external provider.
- **Data deletion:** local data can be removed through Android app storage controls or uninstall. Optional model deletion and provider credential removal are available in Settings. Do not claim that clearing Kaptus deletes provider records.

Before final submission, verify whether OpenSubtitles and the model hosts retain or use IP addresses as device identifiers or infer location. Declare the corresponding types based on their actual use. Do not mark requests as processed ephemerally without evidence of provider retention.

Google excludes some user-initiated, expected transfers to third parties from its definition of sharing. Check that the actual provider disclosures and user choices satisfy that exception before selecting the sharing answer. The app currently explains that credentials are sent to OpenSubtitles and that microphone audio stays on the device.

## Source references

- `app/src/main/java/com/example/kaptus/data/remote/OpenSubtitlesApi.kt`
- `app/src/main/java/com/example/kaptus/data/remote/OpenSubtitlesService.kt`
- `app/src/main/java/com/example/kaptus/data/OpenSubtitlesRepository.kt`
- `app/src/main/java/com/example/kaptus/data/settings/SecureCredentialStore.kt`
- `app/src/main/java/com/example/kaptus/speech/SpeechModelManager.kt`
- `app/src/main/java/com/example/kaptus/speech/WhisperSpeechRecognitionEngine.kt`
- `app/src/main/AndroidManifest.xml`
- [Google Play Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Google Play privacy policy requirements](https://support.google.com/googleplay/android-developer/answer/9859455)
