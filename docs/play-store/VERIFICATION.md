# Release candidate verification

Checked on November 4, 2026 for Android release `com.desigrit.kaptus`, version 1.0.0, code 8, submitted to Closed testing - Alpha.

| Check | Result |
| --- | --- |
| Unit tests | 78 passed, no failures or errors |
| Release lint | No errors; 46 warnings and 2 hints |
| Signed Android App Bundle | Built successfully and accepted by bundletool 1.18.1 validation |
| Signed APK | APK Signature Scheme v2 verified |
| Bundle signature | JAR signature verified with the upload certificate |
| Package and SDK | `com.desigrit.kaptus`; minimum API 33; target API 36 |
| Release flags | Non-debuggable; app backup disabled |
| Permissions | Internet and microphone, plus the AndroidX internal receiver permission |
| Native ABIs | Three libraries, all ARM64 |
| 16 KB compatibility | All native ELF LOAD segments and APK library placement passed |
| Included speech assets | English base model and Silero VAD hashes match the pinned build checksums |
| Store icon | 512 x 512, 32-bit PNG |
| Feature graphic | 1024 x 500, 24-bit opaque PNG |
| Phone screenshots | Two 1080 x 1920 portraits and one 1920 x 1080 landscape; opaque PNGs; saved in Console |
| Listing copy | Title, short description, full description and notes fit Play field limits |
| Privacy policy | Public HTTPS returns 200; mobile layout fits 390 px without horizontal overflow |
| Signing secrets | Ignored by Git; password protected with Windows DPAPI; key directory restricted to its Windows owner |
| EEA country targeting | Saved Alpha list read back: 147 named markets plus Rest of world; no EEA country selected |
| Console bundle processing | Accepted version code 8; no release errors; native debug symbols attached |
| Console warning | No deobfuscation file; expected because release code shrinking is disabled |
| Pricing and audience | Free; 13-15, 16-17 and 18+ saved |
| Content ratings | Questionnaire saved; North America Teen, Brazil 14+, USK 12, IARC generic 12+ |
| Data safety and app content | Saved declarations match `DATA_SAFETY.md`; no advertising ID |
| Reviewer access | Provider search and download verified; private credentials and local SRT instructions saved |
| Submission | Publishing overview shows Changes in review; managed publishing off |
| Production eligibility | Locked pending a closed test with 12 opted-in testers for 14 continuous days |

Upload certificate SHA-256:

```text
2195b147b6486388c40ae47e2ef1aa7de0770fe40ce73bb3db54b47bcd978719
```

The release output folder contains the artifact checksums and bundle manifest. Existing physical-device recognition validation is documented separately in `docs/MULTILINGUAL_VERIFICATION.md`. This preparation run does not add physical-device or instrumented test results.

The signed app artifacts were built from source commit `e7d7f07a6afc59cc6b5439c5f5fe0fef54d265ec`. Later changes update listing assets, the published privacy policy and release documentation. The AAB submitted to Google matches the recorded SHA-256 in `release-manifest.json`.

The closed test is submitted for review. The tester list currently contains the developer's account; further testers and the required testing period are still needed before a production application. Production country targeting must be set when that track becomes available.
