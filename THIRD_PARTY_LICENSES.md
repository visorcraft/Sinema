# Third-Party Licenses

Sinema is licensed under GPL-3.0-only. This file records the third-party build, runtime, and test dependencies used by the app.

The resolved dependency graph can be inspected with:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
./gradlew :app:dependencies --configuration debugUnitTestRuntimeClasspath
```

## Direct Runtime Dependencies

| Component | Version | License |
|---|---:|---|
| AndroidX Core KTX | 1.18.0 | Apache-2.0 |
| AndroidX Leanback | 1.2.0 | Apache-2.0 |
| AndroidX Media3 ExoPlayer | 1.10.1 | Apache-2.0 |
| AndroidX Media3 UI | 1.10.1 | Apache-2.0 |
| AndroidX Media3 UI Leanback | 1.10.1 | Apache-2.0 |
| AndroidX Media3 ExoPlayer HLS | 1.10.1 | Apache-2.0 |
| Glide | 5.0.7 | Simplified BSD License |
| OkHttp | 5.4.0 | Apache-2.0 |
| Gson | 2.14.0 | Apache-2.0 |
| kotlinx-coroutines-android | 1.11.0 | Apache-2.0 |
| AndroidX Lifecycle Runtime KTX | 2.11.0 | Apache-2.0 |
| AndroidX Security Crypto | 1.1.0 | Apache-2.0 |
| AndroidX TVProvider | 1.1.0 | Apache-2.0 |

## Build and Test Dependencies

| Component | Version | License |
|---|---:|---|
| Gradle | 9.6.1 | Apache-2.0 |
| Android Gradle Plugin | 9.2.1 | Apache-2.0 |
| Kotlin stdlib, resolved by AGP | 2.2.21 | Apache-2.0 |
| JUnit | 4.13.2 | Eclipse Public License 1.0 |
| Hamcrest Core | 1.3 | BSD License |

## Notable Transitive Dependencies

The Android app also resolves transitive dependencies through the direct dependencies above. The main transitive dependency families are:

| Component or Family | Resolved Version / Range | License |
|---|---:|---|
| AndroidX Annotation, AppCompat, Collection, Fragment, RecyclerView, SavedState, Startup, Tracing, and related modules | resolved by Gradle | Apache-2.0 |
| AndroidX Media3 Common, Container, Database, Datasource, Decoder, Extractor | 1.10.1 | Apache-2.0 |
| Kotlin stdlib | 2.2.21 | Apache-2.0 |
| Kotlinx serialization core | 1.7.3 | Apache-2.0 |
| Okio | 3.17.0 | Apache-2.0 |
| Guava Android | 33.3.1-android | Apache-2.0 |
| Google Tink Android | 1.8.0 | Apache-2.0 |
| Error Prone Annotations | 2.48.0 | Apache-2.0 |
| JSpecify | 1.0.0 | Apache-2.0 |

## Non-Applicable Ecosystems

The repository currently has no Rust crates, JavaScript packages, npm lockfiles, Python requirements, Go modules, or Ruby gems.
