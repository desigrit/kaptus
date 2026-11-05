# Data safety declaration

These are the answers saved in Play Console and submitted with Android release 1.0.0 on November 4, 2026. They cover optional OpenSubtitles and model-hosting requests as well as the app's local processing.

## Data flows

| Data | Destination | Purpose | User choice and retention |
| --- | --- | --- | --- |
| Microphone PCM and recognized speech | Device memory only | Locate the scene in captions | Optional microphone permission; no audio files or network upload |
| Imported and downloaded subtitle contents | App-private files | Caption playback and matching | User chooses a file or requests download; cleared with app storage or uninstall |
| History, languages and caption timing mappings | App-private database and settings | Restore prepared titles and preferences | Stored locally; app data is excluded from backup |
| OpenSubtitles API key | OpenSubtitles API over HTTPS | Authenticate provider requests | Optional provider setup; encrypted locally |
| OpenSubtitles username and password | OpenSubtitles login endpoint over HTTPS | Optional account authentication | Optional even within provider setup; encrypted locally |
| Search text, movie/episode IDs, caption language, subtitle IDs | OpenSubtitles API over HTTPS | Find and download selected captions | User initiates provider features; provider controls server retention |
| Network IP address, standard request headers | OpenSubtitles and subtitle delivery hosts | Deliver requests, provider access limits and security | Present on optional network requests; provider controls server retention |
| Model identifier and network IP address | Hugging Face and delivery hosts | Download the optional multilingual model | Explicit user download; no credentials or speech sent |
| Connection identifiers, request activity and inferred session location | Network providers | Service operation, security and analysis | Optional online paths; provider retention applies |

## Saved form answers

Data collection is **Yes**. All six declared types are **optional** and **not processed ephemerally**. Local SRT playback with the bundled English model remains available without these network requests.

| Play data type | Collected | Shared | Selected purposes |
| --- | --- | --- | --- |
| Personal info / User IDs | Yes | No | App functionality; account management; fraud prevention, security and compliance |
| Personal info / Other info | Yes | No | App functionality; account management |
| Location / Approximate location | Yes | Yes | Analytics; fraud prevention, security and compliance |
| App activity / App interactions | Yes | Yes | App functionality; analytics; fraud prevention, security and compliance |
| App activity / In-app search history | Yes | No | App functionality; fraud prevention, security and compliance |
| Device or other IDs | Yes | Yes | App functionality; analytics; fraud prevention, security and compliance |

Credentials and title searches are user-initiated transfers to the named OpenSubtitles provider. Their sharing answers use Google's exception for expected, user-initiated transfers. Connection metadata is declared shared conservatively because providers use it for their own service operation, analysis and security. The app explains provider credential use in Settings, and the public policy describes connection records and inferred session location.

OpenSubtitles describes request and IP logging with a normal seven-day retention period, with longer retention possible for disputes. Hugging Face describes automatically collected IP addresses and session location. Neither flow is declared ephemeral. Approximate location refers to provider inference from network connections, not Android location permission. Device identifiers refer to connection identifiers, not an advertising ID.

- **Encryption in transit: Yes.** App endpoints use HTTPS, Android cleartext traffic is disabled, and provider download URLs must use HTTPS.
- **Kaptus account creation: None.** Users may connect an existing external OpenSubtitles account, explained in the form's external-login answer.
- **Data deletion request mechanism: No.** Settings can remove local credentials and the optional model. Clearing app storage or uninstalling removes local app data, but Kaptus does not operate a request process for deleting provider records.
- **Audio, files and documents: Not collected off-device.** Microphone PCM, transcripts and SRT contents stay on the device.
- **Advertising ID: No.** There are no advertising, analytics or automatic crash-reporting SDKs in the app.
- **Independent security review badge and UPI certification: Not claimed.**

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
- [OpenSubtitles privacy policy](https://opensubtitles.tawk.help/article/privacy-policy)
- [Hugging Face privacy policy](https://huggingface.co/privacy)
