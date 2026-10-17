# DataDroid

DataDroid is a cross-platform, on-device image dataset studio built with Kotlin Multiplatform and Compose
Multiplatform. Create image classification datasets on Android and iOS, capture or import images per class,
check class balance, and export a ready-to-train zip.

## Features

- **Datasets**: create, rename, delete; each starts with two classes.
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
- **Export**: a zip of the dataset folder, shared through the system share sheet:

  ```
  Animal_Dataset/
  ├── metadata.json      {"name": "Animal Dataset", "classes": ["cat", "dog", "bird"], "imageCount": 2438}
  ├── cat/cat_0001.jpg
  ├── dog/dog_0001.png
  └── bird/bird_0001.jpg
  ```

  Empty classes are left out. The same zips (or any zip of class folders) can be imported as a new dataset.

## Architecture

Single `shared` module, layered by package under `org.onedroid.dataset`:

| Package | Contents |
|---|---|
| `core` | `Result`/`DataError` typed errors, `UiText`, `ObserveAsEvents` |
| `domain` | Models, `DatasetRepository` interface, use cases (class balance, name validation, folder grouping, export naming) |
| `data` | `FileDatasetRepository` (JSON record + image files per dataset), streaming `ZipWriter`, `ZipReader`, exporter/importer |
| `presentation` | MVI screens (`State`/`Action`/`Event`/`ViewModel`, `Root` + `Screen` composables), theme, components, type-safe navigation |
| `platform` | `expect`/`actual` camera (CameraX / AVFoundation), pickers, share sheet, image codecs, image model (LiteRT on Android, TensorFlow Lite C on iOS) |
| `di` | Koin modules; `initKoin` is called from `DataDroidApplication` (Android) and `iOSApp.init` (iOS) |

Storage: `<app files>/datasets/<id>/dataset.json`, `images/<imageId>.<ext>`, `thumbs/<imageId>.jpg`.

## Running

- Android: `./gradlew :androidApp:assembleDebug`
- iOS: open [/iosApp](./iosApp) in Xcode and run. The TensorFlow Lite C framework is downloaded into
  `third_party/` by Gradle (`:shared:downloadTfliteC`) on the first iOS build.

## Tests

- JVM: `./gradlew :shared:testAndroidHostTest`
- iOS simulator: `./gradlew :shared:iosSimulatorArm64Test`

## Author
**Tawhid Monowar** - *Computer Science & Engineering Student* <br>
[LinkedIn](https://www.linkedin.com/in/tawhidmonowar) | [Portfolio](https://tawhidmonowar.github.io/profile)  | [Email](mailto:tawhidmonowar@duck.com)