# Release candidate verification

Checked on November 4, 2026 for the proposed Android release `com.desigrit.kaptus`, version 1.0.0, code 8.

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
| Listing copy | Title, short description, full description and notes fit Play field limits |
| Privacy policy | Public HTTPS returns 200; mobile layout fits 390 px without horizontal overflow |
| Signing secrets | Ignored by Git; password protected with Windows DPAPI; key directory restricted to its Windows owner |
| EEA market specification | 30 unique country codes, ready to apply in Console |

Upload certificate SHA-256:

```text
2195b147b6486388c40ae47e2ef1aa7de0770fe40ce73bb3db54b47bcd978719
```

The release output folder contains the artifact checksums and bundle manifest. Existing physical-device recognition validation is documented separately in `docs/MULTILINGUAL_VERIFICATION.md`. This preparation run does not add physical-device or instrumented test results.

Console upload, account production eligibility, the final Data safety answers, reviewer provider access and saved country targeting remain pending until Play Console is accessible. No Play release has been submitted.
