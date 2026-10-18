# DataDroid

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.12.1-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![Platforms](https://img.shields.io/badge/platforms-Android_%7C_iOS-34A853)](#requirements)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](#run-on-android)
[![iOS](https://img.shields.io/badge/iOS-18.2%2B-000000?logo=apple&logoColor=white)](#run-on-ios)
[![On-device AI](https://img.shields.io/badge/AI-on--device-FF6F00?logo=tensorflow&logoColor=white)](#features)
[![License](https://img.shields.io/badge/license-Apache_2.0-blue)](LICENSE)
[![Website](https://img.shields.io/badge/website-DataDroid-1967D2)](https://tawhidmonowar.github.io/DataDroid/)

DataDroid is a cross-platform, on-device image dataset studio built with Kotlin Multiplatform and Compose
Multiplatform. Create image classification datasets on Android and iOS, capture or import images per class,
check class balance, and export a ready-to-train zip.

Everything runs on the device, including the auto-sort model, so no image ever leaves the phone.

**Platforms:** Android (phone and tablet, Android 7.0+) and iOS (iPhone and iPad, iOS 18.2+).

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Run on Android](#run-on-android)
- [Run on iOS](#run-on-ios)
- [Try the key features](#try-the-key-features)
- [Architecture](#architecture)
- [Tests](#tests)
- [License and attribution](#license-and-attribution)

## Features

- **Datasets**: create, rename, delete; each starts with one class.
- **Classes**: add, rename inline, clear, delete. Class names are unique per dataset.
- **Images**: capture with the camera (tap for one image, hold to record ~5 frames/s, 640×640 square), pick from the photo
  library, add a folder to a class, or import a folder or zip of class folders (merged by class name).
  Originals are kept unchanged; files that aren't decodable images are skipped.
- **Auto-sort**: capture without choosing a class. An on-device MobileNetV3-Small model embeds each image and
  saves it to the class whose images it's most like. Images unlike every class wait under **Unknown**, where
  they can start a new class, join an existing one, or be discarded. Photos from the library can be sorted
  the same way. "Unknown detection" sets how alike an image must be to a class (0 turns it off).
  Embeddings are cached per dataset in `embeddings.bin`, so each image is only analysed once.
- **Review**: thumbnail strip per class, full image viewer, and a per-class gallery with multi-select to
  move or delete images.
- **Class balance**: image counts, per-class bars, and warnings for empty, small (<20 images) or
  imbalanced (largest ≥ 1.5× smallest) classes.
- **Image settings**: store images as captured, or resize (fit or square) and convert to JPEG, PNG or WebP,
  as an app default or per dataset.
- **Export**: a zip of the dataset folder, shared through the system share sheet:

  ```
  Animal_Dataset/
  ├── metadata.json      {"name": "Animal Dataset", "classes": ["cat", "dog", "bird"], "imageCount": 2438}
  ├── cat/cat_0001.jpg
  ├── dog/dog_0001.png
  └── bird/bird_0001.jpg
  ```

  Empty classes are left out. The same zips (or any zip of class folders) can be imported as a new dataset.

## Requirements

| Tool | Version | Needed for |
|---|---|---|
| JDK | 17 or newer to start Gradle. Gradle downloads the JDK 21 it runs on (see `gradle/gradle-daemon-jvm.properties`). | Both |
| Android Studio | A recent stable release that supports Android Gradle Plugin 9.4, with Android SDK 37 | Android |
| Android device or emulator | Android 7.0 (API 24) or newer. A camera is optional. | Android |
| macOS with Xcode | Xcode 26 (the version the project is developed with), with an iOS 18.2+ simulator or device | iOS |
| Internet access | On the first build, to download Gradle dependencies and the TensorFlow Lite C framework | Both |

Clone the repository first:

```bash
git clone https://github.com/tawhidmonowar/DataDroid.git
```

```bash
cd DataDroid
```

## Run on Android

**From Android Studio**

1. Open the `DataDroid` folder in Android Studio and wait for the Gradle sync to finish.
2. Pick the `androidApp` run configuration and an emulator or a connected device.
3. Press **Run**.

**From the command line**

1. Connect a device with USB debugging on, or start an emulator.
2. Build and install the debug app:

   ```bash
   ./gradlew :androidApp:installDebug
   ```

3. Open **DataDroid** from the launcher.

To build an APK without installing it, run `./gradlew :androidApp:assembleDebug`. The APK is written to
`androidApp/build/outputs/apk/debug/`.

The emulator camera works with its virtual scene or a webcam. On a real device, allow camera access when
the app asks.

## Run on iOS

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. Choose the `iosApp` scheme and an iPhone or iPad simulator.
3. Press **Run**.

The first build takes several minutes. Xcode's build phase calls Gradle
(`:shared:embedAndSignAppleFrameworkForXcode`), which compiles the shared Kotlin code and downloads the
TensorFlow Lite C framework (`:shared:downloadTfliteC`) into `third_party/`. Later builds are
incremental.

**On a physical iPhone or iPad** (needed for the camera, since the simulator has none):

1. Open `iosApp/Configuration/Config.xcconfig` and set `TEAM_ID` to your Apple Developer team ID. A free
   personal team works. The bundle ID becomes `org.onedroid.dataset.DataDroid<TEAM_ID>`, so it is unique to you.
2. Select your device in Xcode and press **Run**.
3. If iOS blocks the app, trust your developer certificate in
   **Settings › General › VPN & Device Management**.

On the simulator, use the photo library, **Import class folders** or **Import dataset (.zip)** to add images
instead of the camera. Drag images onto the simulator window to add them to its Photos library.

## Try the key features

A two-minute tour, on either platform:

1. **Create a dataset.** On the home screen, tap **New dataset** and give it a name, such as "Desk objects".
2. **Fill two classes.** Rename `Class 1` to `mug`, then tap **Add a class** and name it `pen`. Under
   **Add Image Samples**, choose **Camera** and tap the shutter for one image, or hold it to record about
   5 frames a second. Aim for 20 or more images per class. **Upload** adds photos from the library instead.
3. **Watch the class balance.** Tap the **Class balance** card to see per-class counts and warnings for
   empty, small or imbalanced classes.
4. **Auto-sort.** In the **Auto-sort** card, tap **Start** and point the camera at a mug or a pen. The live
   label shows the closest class and how similar it is. Captured images go straight to that class. Capture
   something new, like a phone: it waits under **Unknown**, where **New class** turns it into a third class.
   **Sort photos from library** does the same for existing photos.
5. **Review.** Tap **View all images** on a class. Long-press to select several images, then **Move to…** another
   class or **Delete** them.
6. **Change image settings.** In **Settings**, choose a size (Original, Fit or Square) and a format (JPEG,
   PNG or WebP) for new datasets. Use **Image settings** in a dataset's menu to change it for one dataset.
7. **Export.** Tap **Export ZIP** and share the zip through the system share sheet. It holds one folder per
   class plus `metadata.json`.
8. **Import.** Back on the home screen, use **Import dataset (.zip)** on the zip you just exported to get it
   back as a new dataset.

## Architecture

Most of the code is shared. About 85% of the app's Kotlin lives in `commonMain` (roughly 7,200 lines),
against about 490 lines in `androidMain` and 760 in `iosMain`. The Android and iOS app shells are a few
dozen lines each.

A single `shared` module holds everything, layered by package under `org.onedroid.dataset`:

| Package | Contents |
|---|---|
| `domain` | `Result`/`DataError` typed errors, models, repository interfaces (`DatasetRepository`, `ImageSorter`, `SettingsRepository`), use cases (class balance, class matching, name validation, folder grouping, export naming) |
| `data` | `FileDatasetRepository` (JSON record + image files per dataset), streaming `ZipWriter`/`ZipReader` with a shared CRC-32, exporter/importer, image processing, settings storage, `EmbeddingImageSorter` |
| `presentation` | MVI screens (`State`/`Action`/`Event`/`ViewModel`, `Root` + `Screen` composables), theme, components (`UiText`, `ObserveAsEvents`, dialogs), type-safe navigation |
| `core.platform` | `expect`/`actual` camera (CameraX / AVFoundation), pickers, share sheet, image codecs, and the image model backbone (LiteRT on Android, the TensorFlow Lite C API through cinterop on iOS) |
| `core.di` | Koin modules; `initKoin` is called from `DataDroidApplication` (Android) and `iOSApp.init` (iOS) |

Only the platform boundaries are written per platform: camera, image decoding and encoding, file and photo
pickers, the share sheet, zip inflation, and the model runtime. Image preprocessing for the model
(`toInputTensor`), the embedding cache, and class matching are shared, so both platforms sort images the
same way.

Storage: `<app files>/datasets/<id>/dataset.json`, `images/<imageId>.<ext>`, `thumbs/<imageId>.jpg`,
`embeddings.bin`.

## Tests

The shared tests in `shared/src/commonTest` run on both platforms:

- JVM: `./gradlew :shared:testAndroidHostTest`
- iOS simulator (macOS only): `./gradlew :shared:iosSimulatorArm64Test`

## License and attribution

DataDroid is licensed under the [Apache License 2.0](LICENSE).

It bundles the MobileNetV3-Small image embedder from
[Google MediaPipe](https://ai.google.dev/edge/mediapipe/solutions/vision/image_embedder) (Apache 2.0),
uses LiteRT on Android and the TensorFlow Lite C runtime on iOS (Apache 2.0), and includes Material Design
icon paths (Apache 2.0). See [NOTICE](NOTICE) for details and sources.

## Author

**Tawhid Monowar** - *Computer Science & Engineering Student* <br>
[LinkedIn](https://www.linkedin.com/in/tawhidmonowar) | [Portfolio](https://tawhidmonowar.github.io)  | [Email](mailto:tawhidmonowar@duck.com)

The contest essay is in [ESSAY.md](ESSAY.md).
