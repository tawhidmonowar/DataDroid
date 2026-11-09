# DataDroid: Contest Essay

## Background

I'm Tawhid Monowar, a Computer Science and Engineering student <!-- TODO: add university, year, and one line on how you got into Kotlin and machine learning -->. I build Android apps in Kotlin, and in machine learning courses I kept running into the same problem: the model was the easy part, and collecting a clean, labelled image dataset took most of the time.

## The idea

DataDroid turns a phone into an image dataset studio. You create a dataset, add classes, and fill them with the camera (one tap for a photo, hold to record about five frames a second), the photo library, a folder, or an existing zip. DataDroid checks class balance as you go and warns about empty, small or lopsided classes. When the dataset is ready, it exports a zip in the class-folder layout that most training tools read directly.

The feature I'm proudest of is auto-sort. You can shoot without picking a class first. An on-device MobileNetV3-Small model embeds each image and saves it to the class it looks most like. Images unlike any class wait under "Unknown", where one tap starts a new class. Everything runs offline, so no photo ever leaves the device.

## Technologies

DataDroid is built with Kotlin Multiplatform and Compose Multiplatform for Android and iOS. Roughly 85% of the Kotlin code lives in `commonMain`: the UI, MVI view models, navigation, Koin dependency injection, the file-based repository, a streaming zip reader and writer, and the embedding sorter. Platform code is limited to `expect`/`actual` boundaries: the camera (CameraX and AVFoundation), image codecs, pickers, the share sheet, and the model runtime (LiteRT on Android, the TensorFlow Lite C API through cinterop on iOS). kotlinx-io, kotlinx.serialization and coroutines handle storage and concurrency, and the shared logic is covered by tests that run on both the JVM and the iOS simulator.
