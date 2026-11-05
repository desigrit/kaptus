# Captions in your language

Kaptus can search for movie and TV captions in any language listed by OpenSubtitles. Availability depends on the title, episode and uploaded files. A local UTF-8 or UTF-16 SRT also works offline. Kaptus does not generate missing captions or translate the caption file you see.

Choose **Caption language** for the text on your screen and **Spoken language** for the movie's actual audio. A dubbed movie can have a different spoken language from its original release. Preferences are remembered, and you can override them before preparing a movie or episode. Imported files ask for their language, with an unknown-language option for manual playback.

Provider variants stay separate, including Brazilian and European Portuguese and simplified and traditional Chinese. History opens the saved caption language. Matching-helper files remain separate from displayed files, although one verified download can serve both roles without another provider request.

## Which combinations can find your place?

The Android and iOS previews offer auto-seek for English audio with English captions and the nine additional pairs below. English uses the bundled model. Each additional pair becomes available after downloading the shared multilingual model in Settings. The language pair is determined by the actual spoken language and the displayed caption language.

| Spoken language | Caption language | Android preview | iOS preview |
| --- | --- | --- | --- |
| English | English | Available with bundled model | Available with bundled model |
| Mandarin Chinese (`zh`) | English | Available after model download | Available after model download |
| Japanese (`ja`) | English | Available after model download | Available after model download |
| Spanish (`es`) | English | Available after model download | Available after model download |
| French (`fr`) | English | Available after model download | Available after model download |
| German (`de`) | English | Available after model download | Available after model download |
| Korean (`ko`) | English | Available after model download | Available after model download |
| Hindi (`hi`) | English | Available after model download | Available after model download |
| Telugu (`te`) | English | Available after model download | Available after model download |
| Tamil (`ta`) | English | Available after model download | Available after model download |
| Any other combination, including Cantonese | Any language | Manual timing | Manual timing |

These are available features, not claims of benchmarked phone performance. No physical-device accuracy or speed results for the nine additional languages are claimed here. The compact model can be less accurate for some languages, especially Telugu and Tamil, and a translated subtitle may paraphrase speech. An unsuccessful attempt leaves manual timing available. English auto-seek is retained from the existing app and is not newly certified by an acceptance report in this document.

For example, Hindi audio with an English caption file can auto-seek after model download. Hindi audio with a Hindi caption file uses manual timing. Reopening a saved English file without a Hindi helper can use direct translation without downloading another caption file. Kaptus never silently replaces the saved display language.

Manual timing supports play/pause, scrubbing and caption offset adjustments without microphone permission or speech-model initialization. Initial opening and explicit **Re-sync** are the only recognition triggers for an enabled pair. Scrubbing, rotation and returning from another app must not start recognition. Microphone audio stays in memory and is never saved or uploaded.

## Optional model and matching paths

English keeps its bundled model. The other nine pairs share the optional `ggml-small-q5_1.bin` model, downloaded once with progress, cancellation, retry and deletion controls. The installed file is SHA-256 verified and excluded from backup. Preparation can save captions before the model is downloaded; **offline auto-seek ready** additionally requires the model for the selected pair. All other combinations use manual timing without loading a speech model.

| Artifact | Pinned source | Bytes | SHA-256 |
| --- | --- | --- | --- |
| Multilingual speech model | [whisper.cpp model revision](https://huggingface.co/ggerganov/whisper.cpp/blob/98aa99a0a9db05ae2342309f5096248665f7cba3/ggml-small-q5_1.bin) | 190,085,487 | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| Bundled English speech model | `ggml-base.en-q5_1.bin` | 59,721,011 | `4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f` |

The two matching paths are available for the nine additional pairs and should be benchmarked separately:

- **Matching helper:** transcribe the spoken language against a source-language subtitle file, then cross-check the scene using translation against the English display file. Validate timing locally before saving a mapping. File hashes and the verified time region bound its reuse. Release names and cue numbers do not establish equivalent cuts or timing.
- **Direct translation:** when no suitable helper is available, translate captured speech into English and compare ordered utterances with English caption groups. Its performance can differ from helper matching.

Translation words retain utterance intervals rather than invented word timestamps. Independent confirmation requires non-overlapping captured evidence. Each attempt includes native initialization, recognition passes and matching inside a 35-second deadline. Recognition uses one active job and at most one pending audio window. A timeout or weak evidence offers manual timing, and never downloads a larger model automatically.

Caption preparation shows the maximum potential provider downloads before proceeding. Cached, verified files can reduce that number. A helper can add one download; theater preparation may save alternatives for an enabled path. Provider account limits continue to apply.

## Planned physical-device regression benchmarks

The language pairs above are enabled without waiting for these benchmarks. The protocol below measures their reliability, catches regressions and guides improvements; it is not a runtime availability gate. No completed physical-device benchmark is implied by model download, automated tests or feature availability.

Create a separate group for each platform, device/build, spoken language and matching path. Never combine Android results with iOS results, or helper results with direct-translation results. Android requires a recent physical ARM64 flagship on Android 13 or newer. Initial iOS qualification requires a physical iPhone 17 Pro on iOS 26.2 or newer. Emulators and simulators verify behavior, not speech accuracy or phone performance.

Use the preview's normal language selections and download the multilingual model before testing a foreign-language pair. Exercise helper and direct-translation paths separately, with suitable saved files or a missing helper as appropriate. Record the app artifact hash, source revision, whisper.cpp revision, model hash, device/OS and a reference to audit evidence. Feature availability does not establish that an acceptance target has been met.

For every group, collect at least **30 held-out, native-speaker-reviewed clear-dialogue trials** and **20 reviewed negative trials**. Keep original or appropriately licensed fixtures outside the app's microphone storage path. Check paraphrased English translations, omissions, code-switching, alternate cuts, subtitle offsets, frame-rate differences, seeks and missing helper tracks. Negatives must include repeated dialogue, incorrect caption tracks, silence, music-only audio and background conversation. Manually mark correct scenes and displayed timing references before scoring.

All of these must pass:

- At least 90% of clear-dialogue trials confidently acquire the correct scene within 30 seconds.
- Median absolute displayed timing error across accepted correct dialogue trials is strictly below 1,000 ms.
- No confident wrong selection in any dialogue trial, and no confident selection in the negative or ambiguous set.
- Airplane-mode synchronization after preparation, actual native ASR, bounded memory, no saved/uploaded microphone audio and zero background capture events.
- At most one active recognition job and one pending window, with the longest attempt no greater than 35 seconds including initialization and both passes.

Keep trial IDs unique. Reference the reviewed timing or negative-case record for every trial, and include the exact display caption hash. Helper-path dialogue trials also need the helper caption hash. Results that omit measurements or privacy/lifecycle evidence are incomplete.

### Validate supplied metadata

Run the validator from the Android repository:

```powershell
python tools/validate_language_trials.py D:/Validation/kaptus-trials.json --output D:/Validation/kaptus-report.json
python -m unittest discover -s tools -p test_validate_language_trials.py
```

Exit code `0` means all supplied groups meet the checks, `1` means at least one group is incomplete or failed, and `2` means the input or output could not be processed. The report includes each group's acquisition rate, accepted timing median and explicit errors.

This script validates supplied metadata. It does not attest device execution, inspect audio, verify the truth of audit references or enable or disable a language. A report marked qualified means its supplied metadata meets the benchmark criteria, not that the app has gained an additional capability. Review the referenced physical-device evidence before claiming measured reliability. Its unit tests use synthetic metadata only to test the validator's arithmetic and failure handling, and are not language accuracy results.

### Illustrative original synthetic fixture

The following is a schema example for an original line, "Bring the silver compass. Follow the northern lantern." It contains only one trial, has no real device evidence and deliberately does not qualify. Replace every example identity, reference and measurement with actual reviewed evidence when conducting trials.

```json
{
  "schema_version": 1,
  "groups": [{
    "platform": "android",
    "spoken_language": "es",
    "caption_language": "en",
    "matching_path": "direct_translation",
    "device": {
      "model": "Illustrative physical device",
      "os_version": "16",
      "api_level": 36,
      "physical_device": false,
      "arm64": true,
      "recent_flagship": true,
      "app_revision": "EXAMPLE, not a tested build",
      "recognizer_revision": "EXAMPLE",
      "app_artifact_sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "model_sha256": "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
      "audit_reference": "EXAMPLE, no device audit"
    },
    "review": {
      "reviewer": "EXAMPLE",
      "native_speaker": false,
      "held_out": false,
      "corpus_id": "original-compass-dialogue-example",
      "review_reference": "EXAMPLE, no native-speaker review"
    },
    "evidence": {
      "native_asr_used": false,
      "airplane_mode_sync_verified": false,
      "memory_bounded": false,
      "model_initialization_in_deadline": false,
      "audio_saved": false,
      "audio_uploaded": false,
      "background_capture_events": 0,
      "max_pending_windows": 1,
      "max_active_recognition_jobs": 1,
      "worst_attempt_duration_ms": 35000
    },
    "dialogue_trials": [{
      "id": "example-compass-dialogue",
      "reviewed": false,
      "held_out": false,
      "clear_dialogue": true,
      "reference": "EXAMPLE, unmeasured fixture",
      "display_caption_sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      "scenario": "clear_dialogue",
      "confident": false,
      "correct_scene": false,
      "acquisition_ms": null,
      "absolute_timing_error_ms": null
    }],
    "negative_trials": []
  }]
}
```

Publish reviewed report references with the exact platform, device, language and path they measure. Report incomplete or failed targets as plainly as passed targets. Telugu and Tamil are available in this preview, but may need a stronger approach if the compact model performs poorly. Caption playback and manual controls remain usable regardless of a benchmark result, and no larger model is downloaded without a deliberate product change.
