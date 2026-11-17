# Personalized On-Device AI for Mobile Devices

**30-minute technical session: speaker script and slide content**

Speaker: Tawhid Monowar
Source material: [`personalized-on-device-ai-for-mobile.md`](personalized-on-device-ai-for-mobile.md), [`docs/paper/paper.md`](../paper/paper.md), and the DataDroid source code.

---

## How to use this document

Each slide has three parts:

- **Slide content:** what goes on the slide. Keep it to the title, a few short lines and one visual.
- **Visual:** a suggestion for the diagram, code or chart.
- **Script:** what you say. It is written to be spoken at about 130 words a minute.

### Timing plan

| Section | Slides | Time | Ends at |
| --- | --- | --- | --- |
| Opening and the problem | 1–5 | 6:00 | 6:00 |
| Transfer learning and the recipe | 6–8 | 4:00 | 10:00 |
| Part 1: frozen backbone | 9–10 | 2:45 | 12:45 |
| Part 2: personalization with no training | 11–13 | 4:00 | 16:45 |
| Part 3: linear head trained on the device | 14–15 | 2:45 | 19:30 |
| Measuring it honestly, and the results | 16–18 | 4:00 | 23:30 |
| Mobile engineering | 19–20 | 2:45 | 26:15 |
| Limitations and takeaways | 21–22 | 1:45 | 28:00 |
| Q&A | 23 | 2:00 | 30:00 |

If you run late, shorten slide 10 (backbone export details) and slide 20 (native memory). Both can go without breaking the story.

---

## Slide 1: Title (0:00–0:45)

**Slide content**

- Personalized On-Device AI for Mobile Devices
- How a phone learns *your* image classes, with no server
- Tawhid Monowar · DataDroid (Kotlin Multiplatform, Android and iOS)

**Visual:** App icon and one phone screenshot of the capture screen.

**Script**

> Hi everyone, I'm Tawhid. Over the next half hour I'll show you how to make a phone learn categories that only one user cares about, from that user's own photos, without sending a single image to a server.
>
> Everything I show you runs in an open-source app called DataDroid. It's Kotlin Multiplatform, so the same learning code runs on Android and on iOS. You'll see real code and real benchmark numbers, including the ones that didn't turn out the way I hoped.

---

## Slide 2: Most on-device AI is the same for everyone (0:45–2:00)

**Slide content**

- A typical on-device model knows a fixed set of classes
- It knows the same classes for every user
- Personalized AI must learn **your** categories, from **your** examples
- It must work after the first few examples

**Visual:** Left: one model icon pointing to "1,000 ImageNet classes" for many users. Right: one phone with labels like "my mug", "my left shoe".

**Script**

> Most AI that ships on a phone today is a fixed model. An image classifier recognises the thousand things its authors picked, and it recognises the same thousand things for everyone.
>
> Personalization is a different problem. The model has to learn your categories, from your examples. Nobody can pretrain a model for "my left shoe" versus "my right shoe". Those labels exist only on one phone.
>
> And it has to be useful quickly. If a user has to label two hundred photos before the feature does anything, they'll give up long before that.

---

## Slide 3: The usual answer, and what it costs (2:00–3:00)

**Slide content**

- Usual answer: upload data, fine-tune in the cloud, push a model back
- Costs: a backend, a network round trip, and the user's privacy
- Question for this talk: **how far does a phone get alone?**

**Visual:** A loop of phone → cloud → phone, crossed out. Next to it, a phone on its own.

**Script**

> The standard answer is to upload the user's data, fine-tune a model in the cloud, and push the new model back to the phone.
>
> That works, but it costs three things. You need a backend and you pay to run it. Every update is a round trip, so the model is always behind the user. And the user's photos leave their device.
>
> So the question I wanted to answer was simple: how far can a phone get on its own? No server, no upload, no cloud training.

---

## Slide 4: DataDroid and auto-sort (3:00–4:30)

**Slide content**

- DataDroid: build image classification datasets on your phone
- Create classes, shoot from the camera, export a zip of class folders
- The slow part: choosing the class before every shot
- **Auto-sort:** just shoot, and the app files each image into the right class

**Visual:** Short screen recording or three screenshots: dataset with classes `mug` and `pen` → camera with live label → images sorted into class folders.

**Script**

> Here's the app. DataDroid turns a phone into a dataset studio. You create a dataset, add classes like "mug" and "pen", fill them from the camera, and export a zip of class folders that training tools read directly.
>
> The slow part is labelling. Pick a class, shoot, pick the next class, shoot again. If you forget to switch once, a whole run of images is silently in the wrong folder.
>
> Auto-sort removes that step. You just shoot, and the app decides which class each image belongs to. The classes are the ones the user invented a minute ago. That's the personalization problem in its purest form.

*(If you have a live demo, show it here. Keep it under one minute.)*

---

## Slide 5: Four constraints that make this hard (4:30–6:00)

**Slide content**

1. **The classes are the user's.** No pretraining possible.
2. **Classes are tiny and change constantly.** One image now, six images a second later.
3. **The camera records bursts.** About 5 frames a second of near-identical images.
4. **Some images belong nowhere.** The app must say "none of these".

Question: *what is the smallest amount of learning that still personalizes?*

**Visual:** Four icons, one per constraint.

**Script**

> On paper this is ordinary image classification. Four constraints make it a different problem.
>
> First, the classes belong to the user, so there's nothing to pretrain on.
>
> Second, classes are tiny and they change all the time. A new class might hold one image. The model has to be useful then, and again one second later when it holds six.
>
> Third, the camera records bursts. Holding the shutter saves about five frames a second. Two seconds of holding adds ten almost identical images, and they can drown out everything else in the class.
>
> Fourth, some images belong nowhere. Users point the camera at things that have no class yet, and the app has to say "none of these" instead of guessing.
>
> A normal fine-tuning loop fails the second constraint straight away. Retraining a network after every shutter press is far too slow, and with five images it would overfit anyway. So the real design question is: what is the smallest amount of learning that still personalizes?

---

## Slide 6: Is this transfer learning? Yes, the cheapest kind (6:00–7:30)

**Slide content**

Transfer learning options, from most to least expensive:

| Approach | What trains | On a phone? |
| --- | --- | --- |
| Full fine-tuning | Every layer | Too slow, overfits with 5 images |
| Partial fine-tuning | Last few layers | Still heavy, needs backprop through the network |
| Feature extraction | Nothing in the network | Yes, inference only |
| Linear probe | One linear layer on frozen features | Yes, milliseconds |

DataDroid = **feature extraction + training-free matcher + linear probe**

**Visual:** The table, with the bottom two rows highlighted.

**Script**

> A question I get a lot: is this transfer learning? Yes. It's the cheapest form of it.
>
> Transfer learning means reusing what a model learned on one big dataset for a new task. There's a spectrum. At the expensive end you fine-tune every layer. That's too slow on a phone, and with five images per class it overfits badly.
>
> In the middle you fine-tune the last few layers. That's still backpropagation through a real network.
>
> At the cheap end you freeze the whole network and use it only as a feature extractor. Then, optionally, you train one linear layer on top. That's called a linear probe.
>
> DataDroid lives at that cheap end. The pretrained network is frozen and never changes on the phone. On top of it there's a matcher that needs no training at all, and a linear probe that is trained on the device once there's enough data.

---

## Slide 7: The recipe has three small parts (7:30–8:30)

**Slide content**

1. **Frozen pretrained backbone:** image → embedding vector
2. **Training-free matcher:** a class works from its first image
3. **Linear head trained on the device:** takes over at 10 distinct images per class

No image leaves the device. There is no server.

**Visual:** [`blog-auto-sort-pipeline.png`](blog-auto-sort-pipeline.png)

**Script**

> Here's the whole recipe. It has three parts and each one is small.
>
> One: a frozen pretrained backbone turns every image into an embedding, a vector of numbers where similar-looking images land close together.
>
> Two: a matcher compares embeddings with no training, so a class works from its very first image.
>
> Three: a linear layer, trained on the phone, takes over once every class has ten distinct images.
>
> Every camera frame goes left to right through the frozen backbone. Every saved image loops back through a cache and a background refit, so the next frame meets a matcher that already knows about it.

---

## Slide 8: Architecture: shared intelligence, thin runtimes (8:30–10:00)

**Slide content**

- One Kotlin Multiplatform `shared` module with Compose Multiplatform UI
- About 87% of the Kotlin is in `commonMain` (about 9,600 lines)
- Android: a 34-line LiteRT wrapper · iOS: a 79-line TensorFlow Lite C API wrapper
- Platform code answers one question: *given 150,528 floats, what does the network output?*

```kotlin
interface Backbone : AutoCloseable {
    fun embed(input: FloatArray): FloatArray
}
```

**Visual:** Three boxes. A large `commonMain` box (preprocessing, cache, matcher, head, evaluation) above two thin boxes for `androidMain` and `iosMain`, joined by the `Backbone` interface.

**Script**

> Before the learning, a word on architecture, because it's what makes this maintainable on two platforms.
>
> DataDroid is one Kotlin Multiplatform module. About 87 percent of the code is shared. The preprocessing, the embedding cache, the matcher, the trained head and the evaluation harness are all in common code.
>
> The platform code answers exactly one question: given 150,528 floats, which is a 224 by 224 RGB image, what does the network output? On Android that's a 34-line wrapper around LiteRT. On iOS it's 79 lines over the TensorFlow Lite C API through cinterop.
>
> The seam between them is this interface, with one method. So both platforms sort images with identical logic.
>
> There's a bonus I didn't plan for. Because the model sits behind an interface, all the learning code is testable without a model file. The tests pass a fake backbone that returns an image's average colour.

---

## Slide 9: Part 1: a frozen backbone turns pixels into vectors (10:00–11:15)

**Slide content**

- A pretrained network already knows edges, textures and shapes
- Remove its last layer: what remains maps any image to an **embedding**
- **Frozen:** the phone only runs it and never updates its weights
- One input contract for every model: 224×224, NHWC RGB in [0, 1]

```kotlin
fun RgbImage.toInputTensor(): FloatArray {
    val tensor = FloatArray(width * height * 3)
    for (i in pixels.indices) {
        val argb = pixels[i]
        tensor[i * 3] = ((argb shr 16) and 0xFF) / 255f
        tensor[i * 3 + 1] = ((argb shr 8) and 0xFF) / 255f
        tensor[i * 3 + 2] = (argb and 0xFF) / 255f
    }
    return tensor
}
```

**Visual:** Image → network with its last layer greyed out → vector. A small 2D scatter plot showing similar images close together.

**Script**

> Part one. A network pretrained on millions of images has already learned edges, textures and shapes. Cut off its final classification layer, and what's left maps any image to a vector. Similar images land close together.
>
> We freeze that network. The phone only runs it, it never trains it. That one decision removes most of the cost of personalization. The expensive, general knowledge ships inside the app. The phone only has to learn how the user's classes are arranged in a space that already exists.
>
> One detail that saves a lot of pain: every model takes exactly the same input. 224 by 224, RGB, values from zero to one. The conversion is shared code, so Android and iOS normalise pixels identically. If a model needs something else, like ImageNet mean and standard deviation, that's baked into the model graph when we export it. The app never has an "if this model, then..." branch.

---

## Slide 10: Three backbones, one setting (11:15–12:45)

**Slide content**

| Backbone | File size | Embedding | Character |
| --- | --- | --- | --- |
| MobileNetV3-Small (default) | ~4 MB | 1,024 | Fastest |
| MobileNetV3-Large | ~11 MB | 1,280 | Slower, richer features |
| DINOv2 ViT-S/14 | ~23 MB | 384 | Most accurate, much slower |

DINOv2 export for mobile:
- Normalisation baked into the graph
- Position embeddings frozen at 224 px
- Int8 weights, float activations: 87 MB → 23 MB, cosine similarity 0.996

**Visual:** The table, plus a small "87 MB → 23 MB" callout.

**Script**

> No single model suits every phone, so the app bundles three and the user chooses in Settings.
>
> MobileNetV3-Small is the default. It's about 4 megabytes and very fast. MobileNetV3-Large is about 11 megabytes. And DINOv2, a self-supervised vision transformer from Meta, is about 23 megabytes. It's the most accurate, and much slower.
>
> Getting a vision transformer onto a phone took three steps in the export script. First, normalisation is baked into the graph, so it keeps the same input contract. Second, DINOv2's position embeddings are built for 518 pixels and get resized on every forward pass. We resize them once for 224 and freeze them, so that work becomes a constant. Third, we quantise only the weights to int8 and keep activations in float. The file goes from 87 to 23 megabytes, with a cosine similarity of 0.996 to the original output.
>
> Embeddings from different models can't be compared, so each model keeps its own cache. Switching models re-analyses a dataset once. Switching back is free.

---

## Slide 11: Part 2: personalization with no training (12:45–14:00)

**Slide content**

- Simplest personal classifier: **nearest class mean**
  - Average each class's embeddings into a centroid
  - New image → class with the most similar centroid
- Works from the first image. Updating = changing an average.
- Three problems in real use: bursts, classes with several looks, almost never says "unknown"

**Idea 1: a burst counts as one image**
- Group images within a class when cosine similarity ≥ **0.85**
- Each image weighs `1 / groupSize`

**Visual:** A class shown as dots. A tight cluster of 10 burst frames collapses into one dot with weight 1.

**Script**

> Part two. Once you have embeddings, the simplest personal classifier is nearest class mean. Average each class's embeddings into a centroid, and send a new image to the class whose centroid is most similar. Adding an image just changes an average, so a class works from its first image.
>
> The first release did exactly that. It worked, but real use showed three problems. Bursts dragged a class towards one moment. Classes with several looks, like a mug from the side and from above, got a centroid that sat between the looks and matched neither. And it almost never said "unknown".
>
> The current matcher keeps the no-training property and fixes each problem with one idea.
>
> Idea one: a burst counts as one image. Within each class, images that are at least 0.85 similar get grouped, and each image gets a weight of one over the group size. So a ten-frame burst weighs the same as one photo.
>
> The 0.85 comes from measurement. Neighbouring burst frames score above 0.86 against each other in 95 percent of cases. Distinct photos of the same thing almost never pass 0.75. A cut at 0.85 separates them almost completely.

---

## Slide 12: Centring and the hybrid score (14:00–15:15)

**Slide content**

**Idea 2: subtract what every image shares**
- One user's photos share lighting, background and framing
- Centre embeddings on the dataset mean, then renormalise (SimpleShot)

**Idea 3: blend centroid and nearest neighbours**
- Score = ½ × similarity to centroid + ½ × mean similarity to the 5 nearest images
- Centroid: stable with few images · Neighbours: follow a class with several looks

```kotlin
val toCentroid = dot(query, centroid)
return (toCentroid + if (taken > 0f) total / taken else toCentroid) / 2
```

**Visual:** A "mug" class with two clusters (side view, top view). The centroid sits between them. A query near the top-view cluster matches through its neighbours.

**Script**

> Idea two: subtract what every image shares. All of one user's photos have similar lighting, the same desk, the same framing. That shared part inflates every similarity score and hides the differences between classes. So we centre all embeddings on the dataset's mean and renormalise. This trick comes from a paper called SimpleShot, which showed that it makes plain nearest-neighbour methods competitive for few-shot learning.
>
> Idea three: blend the centroid with the nearest neighbours. A class's score is half its similarity to the centroid and half the average similarity to its five closest images. The centroid part is stable when a class has three images. The neighbour part handles classes with several looks, because the five nearest images all come from the look that matches.
>
> One performance note: this runs on every camera frame, so the five neighbours come from a small hand-written heap, not a full sort.

---

## Slide 13: An honest radius for "none of these" (15:15–16:45)

**Slide content**

**Idea 4: each class gets a radius**
- Radius = how far the class's own images typically land from it
- Trap: measured in-sample, the radius is too small
- Fix: score each group **against the class with that group removed**, take the 0.8 quantile
- Accept only if `distance ≤ radius × margin`
- One user control: the "Unknown detection" slider, margin from 1.3 (lenient) to 0.9 (strict)

None of this is training: grouping, weighted sums and dot products.

**Visual:** Circles around class clusters. A query outside every circle is labelled "unknown".

**Script**

> Idea four is the one users notice most: saying "none of these".
>
> Each class gets a radius, which is how far its own images typically land from its centre. The trap is measuring that in-sample. If you score an image against a centroid that already contains it, it always looks close. The radius comes out too small, and to compensate you need a big margin, which then accepts everything.
>
> So the matcher measures it honestly. It takes each group in the class, removes it, and asks how far that group lands from what's left. The 80th percentile of those distances is the radius. That estimates where a genuinely new image of this class would land.
>
> Then the decision is one comparison: is the distance within the radius times a margin? The margin is the only control the user sees, a slider called "Unknown detection".
>
> And I want to stress this: none of this is training. It's grouping, some weighted sums and dot products. It personalizes from the first image.

---

## Slide 14: Part 3: a linear head trained on the phone (16:45–18:30)

**Slide content**

- Centroids never look at other classes. A trained linear layer does.
- This is transfer learning's **linear probe**: frozen backbone, one weight vector per class
- 5 classes × 1,024 dimensions = 5,120 numbers. Small enough to train in the background.

Four on-device choices:
1. **Prototype initialisation:** weights start at the class centroids
2. **Regularise towards the prototype**, not towards zero
3. **Class-balanced loss:** 200 images and 12 images count the same
4. **Deterministic:** full-batch gradient descent with momentum, 30 epochs, no randomness

```kotlin
val weights = Array(classCount) { prototypes[it].copyOf() }
// ...
gradient[c][i] = REGULARISATION * (weights[c][i] - prototypes[c][i])
```

**Visual:** A 2D plot with two classes that differ in a small detail but share a large variation. The centroid boundary is wrong; the trained boundary is right.

**Script**

> Part three. A centroid has a blind spot. It's computed from one class alone and never looks at the others. If two classes differ in a small detail but share a big variation, like lighting, centroids weigh that big shared variation as heavily as the detail that actually separates them.
>
> A trained linear layer does look at the other classes. This is exactly what transfer learning calls a linear probe. The backbone stays frozen, and we learn one weight vector per class. For five classes that's about five thousand numbers, small enough to train on a phone in the background.
>
> The risk is overfitting with very few images. So the head is designed to be the centroid classifier until the data justifies something else. Four choices make that work.
>
> One: the weights start at the class centroids. Before the first step of training, the head is exactly the nearest-centroid classifier. Training can only move away from a sensible answer, never towards one from random noise.
>
> Two: regularisation pulls the weights back towards the centroids, not towards zero. With little data, the head stays close to nearest-centroid. With more data, the data wins.
>
> Three: the loss is class-balanced, because datasets that are still being built are always lopsided.
>
> Four: it's fully deterministic. Full-batch gradient descent with momentum, thirty epochs, no random initialisation, no shuffling. Same images, same head, every time. That makes it testable and makes bugs reproducible.

---

## Slide 15: When the head takes over, and what it can't do (18:30–19:30)

**Slide content**

Personalization deepens as the user works:
1. **1 image in a class:** hybrid score, borrowed radius
2. **5 distinct images:** the class measures its own radius
3. **10 distinct images in every class:** the trained head picks the class

There is no "Train" button anywhere in the app.

**A softmax can't say "none of these".**
- Head decides **which** class
- Radius decides **whether** the image belongs at all

**Visual:** A three-step staircase labelled 1, 5 and 10 images.

**Script**

> So personalization deepens in steps as the user works. With one image, the class works using the hybrid score and a radius borrowed from the other classes. At five distinct images, it measures its own radius. Once every class has ten distinct images, the trained head takes over choosing the class. "Distinct" uses the burst grouping, so thirty frames of one burst count as one example.
>
> There is no "Train" button anywhere in the app. The user never knows any of this is happening.
>
> One more design point. A softmax always picks some class, however strange the input. It can't say "none of these". So the head only decides which class. Whether the image belongs there is still decided by the radius from Part 2. Keeping those two questions separate meant the unknown-detection logic didn't change at all when the head was added.

---

## Slide 16: Measuring it honestly (19:30–20:30)

**Slide content**

- Personalized models are easy to flatter
- Hold out one burst frame, leave its 9 siblings in → any classifier looks perfect
- **Leave-one-group-out:** remove the held-out image's whole near-duplicate group
- Benchmark runs the **shipped Kotlin code** on embeddings from the **shipped `.tflite` files**
- Data: TF Flowers (5 classes), Imagewoof (10 similar dog breeds), 120 photos per class, simulated bursts, 5 scenarios × 10 draws

**Visual:** One burst highlighted. "Leave one out" with siblings left in (marked as cheating) versus "leave group out" (marked as honest).

**Script**

> Now, how do we know any of this works? Personalized models are very easy to flatter. Hold out one frame of a burst, leave its nine siblings in the training set, and any classifier looks perfect.
>
> So the evaluator removes the held-out image's whole near-duplicate group before refitting. That's leave-one-group-out.
>
> The benchmark is a JVM unit test that runs the actual shipped Kotlin matcher, not a Python re-implementation. It reads embeddings made by the exact model files in the app.
>
> The data is two public datasets. TF Flowers has five classes. Imagewoof has ten dog breeds that look very similar, which is deliberately hard. Five scenarios range from five photos per class to thirty photos plus bursts, each drawn ten times, with 95 percent confidence intervals.

---

## Slide 17: Results: the backbone matters more than the matcher (20:30–22:30)

**Slide content**

Average accuracy over five scenarios, training-free matcher:

| Backbone | Size | Flowers | Imagewoof | Flowers, 5 photos/class |
| --- | --- | --- | --- | --- |
| MobileNetV3-Small | ~4 MB | 80.7% | 82.2% | 71.6% |
| MobileNetV3-Large | ~11 MB | 80.8% | 88.3% | 72.4% |
| DINOv2 ViT-S/14 (int8) | ~23 MB | **94.8%** | **89.2%** | **92.0%** |

- Matcher vs plain nearest-class-mean: **+2.0 points** on average
- Trained head on top: **+1.2** (Flowers) and **+0.5** (Imagewoof) with MobileNetV3-Small; **+0.2** and **+0.5** with DINOv2
- Swapping the backbone: **+14 points** on Flowers
- Int8 weight quantisation: within 0.1 point of float32

**Visual:** Bar chart with three bars for "matcher improvements", "trained head" and "better backbone". The backbone bar dwarfs the others.

**Script**

> Here are the results, and some of them surprised me.
>
> All the matcher work, the burst grouping, centring and hybrid score, improved accuracy by about two points on average over plain nearest-class-mean. Five of ten comparisons were statistically significant, all positive.
>
> The trained head added between half a point and 1.2 points with MobileNet, and less with DINOv2. With five photos per class it adds exactly zero, because it never trains. That's the design working. It only kicks in once there's enough data.
>
> Then look at the backbone. Swapping MobileNet for DINOv2 moved Flowers accuracy by fourteen points. That's several times more than all my matcher and training work combined. With only five photos per class, DINOv2 already reaches 92 percent. That's the number that matters most for personalization: how good is it before the user has done much work?
>
> And the int8 weight quantisation was free: within a tenth of a point of the float version, at a quarter of the size.
>
> I'd rather show you a two-point gain honestly than the synthetic unit test where the head jumps from under 80 to over 95 percent.

---

## Slide 18: Better features fix unknown detection too (22:30–23:30)

**Slide content**

Known images kept / unseen-class images rejected, default strictness:

| | Flowers | Imagewoof |
| --- | --- | --- |
| MobileNetV3-Small | 85.2% / 15.1% | 82.2% / 25.1% |
| DINOv2 ViT-S/14 | 85.5% / **83.3%** | 83.7% / **64.5%** |

Same thresholds, tuned on MobileNetV3-Small, reused unchanged.

Cost: DINOv2 is about **20 to 40 times slower** per image, so it's a setting, not the default.

**Visual:** Two curves of "known kept" against "unknown rejected" across the strictness slider, one per backbone. DINOv2's curve sits above and to the right.

**Script**

> The same story repeats for unknown detection. At the default setting, MobileNet rejects only 15 percent of images from a class it has never seen. DINOv2 rejects 83 percent, while keeping the same share of known images.
>
> The thresholds were tuned on MobileNet and reused unchanged. So this is purely the backbone's doing, not a retuned matcher.
>
> The price is speed. DINOv2 is roughly 20 to 40 times slower per image, so live sorting can lag on older phones. That's why it's a setting and not the default.

---

## Slide 19: Making it feel instant on a phone (23:30–25:30)

**Slide content**

The learning code is about 500 lines. Most of the work was everything around it.

- **Embed each image once, ever:** append-only binary cache per dataset and model
- **Don't embed a frame twice:** embeddings of frames being saved wait in an LRU map keyed by content (size + CRC32 + hash)
- **Refit only for the latest state:** `conflate()` drops intermediate dataset states during a fit
- **Incremental refits:** keep each class's groups between fits
- **Captures beat preview frames:**

```kotlin
val (jpeg, save) = select {
    autoCaptures.onReceive { it to true }
    previewFrames.onReceive { it to false }
}
```

**Visual:** A timeline of camera frames at 5 per second. Fits run in the background, and intermediate states are dropped.

**Script**

> The learning code is about 500 lines. Most of the work in shipping it was everything around it.
>
> First, inference is the expensive step, so every image is embedded once, ever. Embeddings go into a small append-only binary file beside the dataset. It survives interrupted writes, and if the model changes, the header check fails and it's rebuilt.
>
> Second, auto-sort embeds a frame to decide its class, then saves it. Without care, the next refit would embed it again. So embeddings of frames being saved wait in a small map keyed by the image bytes, and the cache claims them when the image appears in the dataset.
>
> Third, a burst changes the dataset five times a second, and a refit can take longer than that. One Kotlin Flow operator, `conflate`, drops every intermediate state while a fit is running. Frames keep being sorted with the previous matcher until the new one is ready. Refits are also incremental: a class that only gained images at the end only groups the new ones.
>
> Fourth, the live label in the viewfinder competes with saved captures for the one interpreter. `select` in Kotlin coroutines is biased towards its first clause, so captures always win. Under load, the live label gets slower, and saved images don't.

---

## Slide 20: Native memory doesn't know about coroutines (25:30–26:15)

**Slide content**

- One shared interpreter, not thread-safe → a `Mutex` serialises inference
- Cancelling a coroutine **does not stop** native inference already running
- Close only when idle: `if (!mutex.tryLock()) return`, and the running embed closes it when it finishes
- Loading runs under `NonCancellable`, so an abandoned interpreter is still freed
- iOS: `TfLiteModelCreate` doesn't copy the buffer → copy the model to the native heap

**Visual:** A sequence diagram: `close()` arrives during `embed()` → deferred → closes after `embed()` returns.

**Script**

> One last engineering lesson, because it bit me. Native memory doesn't know about coroutines.
>
> There's one interpreter, shared, and it isn't thread-safe, so a mutex serialises inference. But cancelling a coroutine doesn't stop native code that's already running. If you close the interpreter as soon as it's cancelled, you free memory that's still in use, and the app crashes.
>
> So close only happens when the mutex is free. If an inference is running, it does the close itself when it finishes. Loading has the mirror-image problem, so it runs as non-cancellable and cleans up if it was abandoned.
>
> On iOS there's a similar trap: the C API doesn't copy the model buffer, so pinning a Kotlin array just for the call leaves the interpreter reading freed memory later.

---

## Slide 21: What it doesn't do yet (26:15–27:00)

**Slide content**

- Thresholds tuned on one backbone and on the evaluation datasets
- Bursts were simulated, with no real motion blur
- No on-device timings yet: all latencies are from a laptop JVM
- The trained head's gain is small and its phone training cost is unmeasured
- CPU only, 4 threads, no hardware delegates tested
- Three bundled models cost about 38 MB of app size

Next: a CLIP-style encoder (classes by name, no images), and in-app accuracy estimates

**Visual:** Plain list. No decoration.

**Script**

> I want to be clear about where the measurements stop.
>
> The thresholds were tuned on MobileNet and on the same datasets used for evaluation. Bursts were simulated. Every timing I showed is from a laptop, not a phone. The head's gain is small and I haven't measured its training cost on a phone. Inference is CPU-only. And three bundled models add about 38 megabytes to the app.
>
> Next, I want to try a CLIP-style encoder, so you could sort into a class that has a name but no images yet. And I want to show users an estimated accuracy for their own dataset, using the evaluator that's already in the app.

---

## Slide 22: What to take from this (27:00–28:00)

**Slide content**

1. **Freeze the backbone** and personalize on top of it
2. **Start with no training:** centroids and neighbours work from one example
3. **Add training as an upgrade:** start at the training-free answer and regularise towards it
4. **Separate "which class" from "any class at all"**
5. **Count near-duplicates once**, in fitting and in evaluation
6. **Put the learning in shared code** behind a tiny interface
7. **Spend effort on the backbone first**

github.com/tawhidmonowar/DataDroid

**Visual:** Numbered list and a QR code for the repository.

**Script**

> If you're adding personalization to your own app, here's what I'd reuse.
>
> Freeze the backbone. The expensive knowledge ships in the app, and the phone only learns how the user's classes are arranged.
>
> Start with no training at all. Centroids and neighbours work from one example and update in milliseconds. That covers the first minute, which is when personalization matters most.
>
> Add training as an upgrade, not a requirement. Start the trained layer at the training-free answer and regularise towards it, so the worst case is what you already had.
>
> Keep "which class" separate from "any class at all", because a softmax can only answer the first.
>
> Count near-duplicates once, in fitting and in evaluation. Camera data is full of them and they flatter every metric.
>
> Put the learning in shared code behind a tiny interface. The platform code shrinks to a few dozen lines, and the interesting code is testable without a model or a device.
>
> And spend your effort on the backbone first. In my benchmarks, a better backbone was worth several times more than a better matcher.
>
> The whole thing is open source under Apache 2.0. The link is on the slide. Thank you.

---

## Slide 23: Questions (28:00–30:00)

**Slide content**

- Questions?
- Repository: github.com/tawhidmonowar/DataDroid
- Write-up: `docs/assets/personalized-on-device-ai-for-mobile.md` · Paper: `docs/paper/paper.md`

**Visual:** QR code, contact details.

---

# Appendix: Q&A preparation

This appendix collects every question the audience is likely to ask, grouped by topic, with a short answer you can say out loud. Answers stick to what the code and benchmarks show. Where something hasn't been measured, the answer says so.

## Handling questions

- **Repeat the question** before answering, so the whole room hears it.
- **Answer in two or three sentences**, then offer to go deeper after the session.
- **If you don't know, say so**, and point to what *is* measured. "I haven't measured that on a phone. On the laptop it's X, and I expect a phone to be several times slower."
- **Bridge back to the key message:** freeze the backbone, start training-free, add training as an upgrade, invest in the backbone first.

## Key numbers cheat sheet

| Fact | Value |
| --- | --- |
| Shared Kotlin in `commonMain` | about 87% (about 9,600 lines) |
| Android model wrapper / iOS model wrapper | 34 lines / 79 lines |
| Model input | 224×224 RGB, NHWC, values in [0, 1] (150,528 floats) |
| MobileNetV3-Small / Large / DINOv2 ViT-S/14 | ~4 MB, 1,024-d / ~11 MB, 1,280-d / ~23 MB, 384-d |
| All three bundled models | about 38 MB |
| DINOv2 export | 87 MB → 23 MB, int8 weights, cosine similarity about 0.996 to PyTorch |
| Burst capture | about 5 frames a second, 640×640 squares |
| Near-duplicate threshold | cosine similarity ≥ 0.85 |
| Hybrid score | ½ centroid + ½ mean of the 5 nearest images |
| Radius | 0.8 quantile of leave-group-out distances, up to 32 probes, needs 5 groups |
| Strictness slider | 0 = off, default 50, margin 1.3 (lenient) to 0.9 (strict) |
| Head trains when | at least 2 classes and every class has ≥ 10 distinct images |
| Head training | 30 epochs, full batch, momentum 0.9, learning rate 0.06, temperature 10, regularisation 0.02, at most 48 examples per class |
| Matcher vs plain nearest-class-mean | +2.0 points on average (MobileNetV3-Small) |
| Head gain | +1.2 / +0.5 points (Small, Flowers / Imagewoof), +0.2 / +0.5 points (DINOv2) |
| Backbone swap, Small → DINOv2 | Flowers 80.7% → 94.8%; Imagewoof 82.2% → 89.2% |
| DINOv2 at 5 photos per class (Flowers) | 92.0% |
| Unknowns rejected at default strictness (Flowers) | 15.1% (Small) vs 83.3% (DINOv2) |
| DINOv2 speed | about 20 to 40 times slower per image than Small |
| Refit time (laptop JVM, from scratch) | 9 ms at 250 images, 176 ms at 2,000, 1,071 ms at 5,800 |
| Match one image (laptop JVM) | 0.2 ms at 250 images, 4.3 ms at 5,800 |
| Embedding cache per image | 1,024 floats × 4 bytes ≈ 4 KB (Small) |

---

## 1. Transfer learning and machine learning concepts

**Is this app using transfer learning?**
Yes. A network pretrained on millions of images is reused as a frozen feature extractor, and a linear layer is trained on its outputs on the phone. That second part is what the literature calls a linear probe. The app never fine-tunes the backbone's own weights.

**Is it really transfer learning if the network never trains?**
Yes. Feature extraction with a frozen network is the most common form of transfer learning. The knowledge transferred is the embedding space. Fine-tuning is just one way of transferring, not the definition.

**What's the difference between feature extraction, a linear probe and fine-tuning?**
Feature extraction runs the frozen network and uses its output vectors. A linear probe trains one linear layer on those vectors, with the network still frozen. Fine-tuning updates the network's own weights, either all layers or the last few. DataDroid does the first two.

**Why not fine-tune the backbone on the phone?**
Three reasons. With 5 to 50 images per class it would overfit. It needs backpropagation through millions of weights, which is slow and memory-hungry on a phone. And the dataset changes five times a second during a burst, so it would never catch up. The benchmarks also show a trained linear layer adds only 0.2 to 1.2 points, so deeper training is unlikely to pay for itself.

**Is this few-shot learning?**
Yes, in the practical sense: a class works from one image and gets useful at about five. The matcher is closest to the nearest-centroid and nearest-neighbour baselines from the few-shot literature, especially SimpleShot. There's no meta-learning or episodic training.

**How is this different from prototypical networks?**
Prototypical networks also classify by distance to class prototypes, but they train the embedding network with episodes so that prototypes work well. Here the embedding network is fixed and nothing is meta-trained. The prototypes are just centroids of the user's own images.

**Is this continual learning? What about catastrophic forgetting?**
The backbone is frozen, so it can't forget anything. The matcher and head are refitted from all of the user's current images every time, so there's nothing to forget either. The price is that refit cost grows with the dataset, which is why grouping is incremental and head training is capped at 48 examples per class.

**What is an embedding, in one sentence?**
A fixed-length vector a network produces for an image, arranged so that images that look alike get vectors that point in similar directions.

**Why cosine similarity and not Euclidean distance?**
Embeddings are normalised to unit length, so the dot product is the cosine similarity, and for unit vectors cosine and Euclidean distance rank neighbours the same way. Cosine is cheap: one dot product per comparison.

**Is this federated learning?**
No. Nothing is shared between devices or sent to a server. Each phone learns only from its own user's images. Federated learning would be a way to improve the *backbone* across users, which is a different problem.

**Is this "AI" or just nearest neighbours?**
Both. The heavy lifting is a deep network that learned visual features from millions of images. The personal part on top is deliberately simple, because simple methods work from one example and update in milliseconds. The trained head is real gradient descent, just on one layer.

**Would this approach work for audio, text or sensor data?**
The matcher and head only see vectors, so they don't care where the vectors come from. Swap the image backbone for an audio or text embedder and the same recipe applies. The burst threshold and radius settings would need re-measuring for that embedder.

---

## 2. Backbones and models

**Which models does the app use?**
Three bundled backbones, chosen in Settings: MobileNetV3-Small (default, about 4 MB), MobileNetV3-Large (about 11 MB) and DINOv2 ViT-S/14 (about 23 MB). The two MobileNets are Google MediaPipe image embedders. DINOv2 is converted from Meta's weights by a script in the repository.

**Why is MobileNetV3-Small the default if DINOv2 is so much better?**
Speed. DINOv2 is roughly 20 to 40 times slower per image, and live sorting runs at camera speed. Small works well enough on every phone. DINOv2 is an opt-in for users who want accuracy and have a fast phone.

**Why is DINOv2 so much better?**
It's self-supervised, and its features are known to work well for nearest-neighbour retrieval, which is exactly how the matcher uses them. The MobileNet embedders come from networks trained to classify ImageNet labels. Supervised features tend to emphasise what separates those 1,000 labels, not the user's own categories.

**DINOv2's embedding is only 384 numbers. How can it beat a 1,024-number embedding?**
Size isn't quality. What matters is how well the space separates things. DINOv2's 384 dimensions carry more useful structure than MobileNet's 1,024.

**Why is MobileNetV3-Large no better than Small on Flowers?**
It improved Imagewoof from 82.2% to 88.3% but not Flowers (80.7% to 80.8%). I don't have a confirmed explanation. One guess is that Large's extra capacity helps fine-grained dog breeds, which are ImageNet classes, more than flowers.

**How did you get a vision transformer to run on a phone?**
The export script does three things. It bakes ImageNet normalisation into the graph, so the input contract stays the same. It resizes DINOv2's position embeddings once for 224 px and freezes them, instead of resizing on every forward pass. And it quantises weights to int8 with float activations. The file goes from 87 MB to 23 MB.

**Did quantisation hurt accuracy?**
No, not measurably. The int8-weight model scores within 0.1 point of the float32 export on both datasets, with a cosine similarity of about 0.996 to the PyTorch output.

**Why not full int8 quantisation, including activations?**
Full int8 would need a calibration dataset and could be faster on int8 kernels, but it's riskier for accuracy, especially for transformers. Weight-only quantisation got the file size down with no measurable loss, so I stopped there. Full int8 is untested.

**Why not quantise MobileNetV3-Small too?**
It's already about 4 MB in float32, so there was little to gain in size. It hasn't been tried.

**Why 224×224 input?**
It's the input size all three models were trained or exported for. Captures are 640×640 squares and are scaled down for the model.

**Why not CLIP or MobileCLIP?**
That's the top item on the future list. A CLIP-style encoder adds text, so you could create a class by name and sort into it before it has any images. It hasn't been integrated yet.

**What happens to embeddings when a user switches models?**
Embeddings from different models aren't comparable, so each model has its own cache file per dataset. Switching re-analyses the dataset once with the new model. Switching back is free because the old cache is still there. A matcher also records the embedding length it was fitted on, so vectors from different models are never compared.

**Can users bring their own model?**
Not today. The design makes it easy to add: a model is one `BackboneSpec` with a file path, input size, embedding size and cache name, as long as it takes 224×224 RGB in [0, 1].

**How are models updated?**
They're bundled in the app, so a new model ships with an app update. Downloading models on demand would cut app size but would break the "no network at all" property.

---

## 3. The training-free matcher

**Why start with nearest class mean?**
It works from one image, updating it means changing an average, and with good embeddings it's a strong baseline. It's the right tool for the first minute of a new class, when personalization matters most.

**What was wrong with plain nearest class mean?**
Three things in real use. Bursts dragged a class towards one moment. Classes with several looks got a centroid between the looks that matched neither. And it almost never said "unknown".

**How does burst grouping work?**
Within each class, an image joins the first earlier group whose leader it resembles with cosine similarity of at least 0.85. Otherwise it starts a new group. Each image then gets weight 1 divided by its group size, so a ten-frame burst weighs the same as one photo.

**Why 0.85?**
It was measured. With MobileNetV3-Small, simulated neighbouring burst frames score at least 0.86 against their source in 95% of cases. The most similar *distinct* photo in the same dataset passes 0.68 to 0.75 only 5% of the time. A cut at 0.85 separates them almost completely.

**Is 0.85 right for DINOv2 too?**
It was tuned on MobileNetV3-Small and reused unchanged for the other backbones. DINOv2 still performed much better with it, but a per-backbone threshold might do better still. That's an open item.

**What if two genuinely different photos are grouped as a burst?**
They'd share weight, so the class counts them as one example. With a 0.85 threshold that's rare for distinct photos, as the measurement shows. The cost of a wrong merge is small: a slightly under-weighted image, not a wrong label.

**What does centring do?**
All of one user's photos share lighting, background and framing, which inflates every similarity. Centring subtracts the dataset's weighted mean from every embedding and renormalises, so scores reflect what differs between classes. It's the "CL2N" trick from SimpleShot. With only one class it's skipped, since there's nothing to contrast.

**Does centring always help?**
No. In the ablation it helps a lot with five photos per class (Flowers 61.6% to 67.2%) and is neutral or slightly harmful with large classes. It hurts in burst scenarios *without* grouping, because burst-heavy classes bias the mean. With grouping on, it's roughly neutral on average and helps the hybrid score.

**Why blend the centroid with nearest neighbours instead of using one?**
The centroid is stable when a class is small. The neighbours follow classes with several looks. In the ablation, neighbours alone were best on Flowers but worst on Imagewoof. The 50/50 blend was the most robust across both.

**Why five neighbours?**
It's the neighbour count chosen on the evaluation datasets. Neighbours are counted by weight, so a whole burst counts as at most one of the five.

**Which part of the matcher helped most?**
Burst grouping was the most consistent. It added 1.4 to 4.2 points in burst scenarios and never cost more than 0.1 point elsewhere. On Imagewoof, centroid plus grouping alone matched the full method.

**What does a fit actually do, if not training?**
It groups near-duplicates, computes weighted centroids and a dataset mean, and measures each class's radius with a few leave-group-out probes. That's grouping, weighted sums and dot products. No gradients.

---

## 4. The linear head trained on the device

**What does the head add that the matcher can't do?**
A centroid is computed from one class alone and never looks at the other classes. A trained linear layer does. If two classes differ in a small detail but share a large variation, like lighting, the head can learn to ignore the shared variation.

**How big is it?**
One weight vector per class, the size of the embedding. For five classes of 1,024-dimensional embeddings that's 5,120 numbers.

**Why initialise at the class centroids?**
So that before the first training step, the head is exactly the nearest-centroid classifier. Training can only move away from a sensible answer, never towards one from random noise.

**Why regularise towards the centroid instead of towards zero?**
Standard L2 regularisation pulls weights to zero, which is meaningless here. Pulling towards the centroid means that with little data the head stays close to nearest-centroid, and with more data the data wins. The worst case is roughly what you already had.

**Why full-batch gradient descent with momentum, not Adam or SGD?**
The problem is tiny and fits in memory, so full batches are cheap. Full batch with no shuffling and no random initialisation makes training deterministic: the same images always give the same head. That makes it testable and makes bugs reproducible.

**Why a temperature of 10?**
Embeddings and weights are unit-length, so raw dot products sit between −1 and 1. A softmax over values that close is too flat to learn from. Multiplying by 10 sharpens it.

**Why is the loss class-balanced?**
Datasets being built are lopsided. Each class shares an equal part of the loss, so a class with 200 images and one with 12 count the same.

**Why does it only train at 10 distinct images per class?**
Below that, a trained layer is likely to overfit, and the training-free matcher does as well or better. The benchmark shows the cutoff working: with five photos per class the head never trains and results are identical to the matcher.

**Why "distinct" images?**
A burst of thirty frames is one moment, not thirty examples. Each near-duplicate group is averaged into one training example, using the same grouping as the matcher.

**Why cap training at 48 examples per class?**
To bound the cost of a refit however large the dataset gets. The 48 are an even spread across the class's groups.

**Is the head retrained every time the dataset changes?**
Yes. It's refitted from scratch with the matcher whenever the dataset changes. Refits are conflated, so during a burst only the latest dataset state is fitted.

**How long does head training take on a phone?**
Not measured, and that's one of the stated limitations. As a rough size: ten classes at the 48-example cap with 1,024-dimensional embeddings is a few hundred million multiply-adds over 30 epochs. That runs in the background and doesn't block the camera, because the previous matcher keeps sorting until the new one is ready.

**Why does the head add so little on real data?**
Embeddings from a good backbone are already well separated by their centroids, so a linear layer has little left to fix. On a synthetic test built for the case it targets, it goes from under 80% to over 95%. Real photos are less dramatic.

**Then why keep it?**
It never makes things worse on average, it helps in the cases centroids can't handle, and it's cheap. It also makes personalization deepen as the user does more work.

**Can the head say "this is none of my classes"?**
No. A softmax always picks some class. So the head only decides *which* class, and the radius from the matcher still decides *whether* the image belongs there.

**Why not use the softmax probability as a confidence threshold?**
Maximum-softmax-probability is a known baseline for rejecting unknowns, but softmax scores are often overconfident on inputs far from the training data. A distance-based radius measured per class was already working, and keeping it separate meant adding the head didn't change unknown detection at all.

**Why not use the platform's on-device training APIs, such as Core ML updatable models or TensorFlow Lite on-device training?**
They would put the learning code back into platform-specific code and give different behaviour on Android and iOS. The head is small enough that about a hundred lines of plain Kotlin train it, identically on both platforms.

---

## 5. Unknown detection

**How does the app decide an image belongs to no class?**
Each class has a radius: how far its own images typically land from it. An image is accepted only if its distance to the best class is within that radius times a margin. Otherwise it goes to the Unknown list.

**What's wrong with measuring the radius directly?**
An image scored against a centroid that already contains it always looks close. The radius comes out too small, and the margin needed to compensate ends up accepting everything. So each group is scored against the class with that group removed, and the radius is the 0.8 quantile of those distances.

**What does the "Unknown detection" slider do?**
It sets the margin, from 1.3 radii at the lenient end to 0.9 at the strict end. Zero turns rejection off. The default is 50.

**What about a class with only one or two images?**
A class with fewer than five distinct groups can't measure its own spread, so it borrows the median radius of the other classes. If no class can measure one yet, a fixed fallback radius is used.

**How well does it work?**
At the default setting, MobileNetV3-Small rejects 15% of images from an unseen Flowers class and 25% on Imagewoof. DINOv2 rejects 83% and 65%, while keeping about the same share of known images. The backbone matters far more than the rule.

**Why is rejecting unknowns still hard?**
The benchmark's unknowns are *sibling* classes: another flower, another dog breed. That's the hardest case there is. Even at maximum strictness with MobileNet, 17% to 20% of them are accepted into a neighbouring class. Unrelated objects are easier.

**What happens to unknown images?**
They're kept on disk under Unknown, so closing the camera or the app loses none. From there the user can start a new class with them, add them to an existing class, or discard them. Exports put them in a separate `unknown` folder so training tools don't mistake them for a class.

---

## 6. Evaluation and results

**How did you evaluate it?**
Leave-one-group-out on two public datasets, TF Flowers (5 classes) and Imagewoof (10 similar dog breeds). 120 photos per class, 20 of them with four simulated burst frames. Five scenarios from 5 photos per class to 30 photos plus bursts, each drawn 10 times, with paired 95% confidence intervals.

**Why leave-one-group-out instead of plain leave-one-out?**
With bursts, plain leave-one-out leaves the held-out frame's siblings in the training set, and any classifier looks perfect. Removing the whole near-duplicate group gives an honest estimate of how a genuinely new photo is classified.

**Did you benchmark the real app code?**
Yes. The benchmark is a JVM unit test, `MatcherBenchmark`, that runs the shipped Kotlin matcher on embeddings from the exact `.tflite` files in the app. The ablation used a NumPy re-implementation with the same rules, so its absolute numbers differ slightly.

**How were bursts simulated?**
Each burst frame is a square crop of 88% to 98% of the short side, shifted by up to 4%, with brightness scaled by 0.9 to 1.1. Five frames per burst, matching about one second of holding the shutter.

**Are simulated bursts realistic?**
Not fully. They don't model motion blur, focus hunting or changing backgrounds. Real bursts may group less cleanly. A benchmark of real burst captures is on the future list.

**Are the results statistically significant?**
For the matcher against plain nearest-class-mean, five of ten comparisons have a 95% interval that excludes zero, all positive. For the head, three of ten with MobileNet and two of ten with DINOv2, all positive. The draws share a pool of 120 photos per class, so they aren't independent and the intervals are somewhat optimistic. Win counts are reported alongside.

**Weren't the thresholds tuned on the same data you evaluated on?**
Yes, and that's a stated limitation. The duplicate threshold, neighbour count, radius coverage and margins were chosen on these two datasets. That biases results in the matcher's favour. Evaluating on a separate set of datasets would give a cleaner estimate.

**Could the backbone have seen your test images during pretraining?**
Possibly. Imagewoof's breeds are ImageNet classes and its images come from the ImageNet training set. That likely helps both matchers and may understate the benefit on genuinely new domains.

**Why only two datasets?**
They cover an easy-ish coarse task and a hard fine-grained one, and both are public. More domains, especially non-ImageNet ones, would make the results stronger.

**Did you run a user study?**
No. The benchmarks measure classification quality, not time saved for users or how they react to rejections.

**What's the single most important result?**
Better features beat cleverer matching. Swapping MobileNetV3-Small for DINOv2 added 14 points on Flowers. All the matcher work added about 2 to 3, and the head about 1.

**What's the accuracy with five photos per class?**
On Flowers, 71.6% with MobileNetV3-Small and 92.0% with DINOv2. On Imagewoof, 74.6% and 83.0%.

---

## 7. Performance and mobile engineering

**How fast is it on a real phone?**
Not measured yet. All timings are from a laptop JVM on an Apple M3 Pro. There, a full refit takes about 9 ms at 250 images and about 1 s at 5,800, and matching one image takes 0.2 to 4.3 ms. Expect a mid-range phone to be several times slower.

**How does it keep up with five frames a second?**
Four things. Each image is embedded once and cached. Frames being saved reuse the embedding computed to sort them. Refits run off the UI thread and are conflated, so only the latest dataset state is fitted. And the previous matcher keeps sorting frames until the new one is ready.

**What does `conflate()` do here?**
It's a Kotlin Flow operator that drops intermediate values while the collector is busy. If the dataset changes five times during one refit, only the latest state is fitted next. Without it, refits would pile up behind a burst.

**Are refits incremental?**
Partly. Grouping near-duplicates is most of the cost, and `ClassMatcher.Fitter` keeps each class's groups between fits. A class that only gained images at the end, which is what a camera does, only groups the new ones. Radii and the head are recomputed.

**How does the embedding cache work?**
A small binary file per dataset and model: a magic number, the embedding size, then one record per image (an ID and its floats). New embeddings are appended. A record cut short by a crash is dropped on load. Deleted images' records are compacted away once the file is more than twice the live size. Any write failure deletes the cache and the app carries on.

**How much storage and memory do embeddings use?**
About 4 KB per image with MobileNetV3-Small (1,024 floats × 4 bytes), and about 1.5 KB with DINOv2. A 5,000-image dataset is about 20 MB with the default model.

**How do you avoid embedding the same frame twice?**
Embeddings of frames about to be saved wait in a 512-entry LRU map keyed by the image's content: its size plus two independent hashes (CRC-32 and a content hash). When the saved image appears in the dataset, its embedding is claimed by its bytes.

**What stops the live preview label from slowing down saves?**
Preview frames and captures compete for one interpreter. A coroutine `select` takes captures first, because `select` is biased towards its first clause. Under load the live label gets slower, and saved images don't.

**Is the interpreter thread-safe?**
No. One interpreter is loaded lazily and shared, and a `Mutex` serialises inference.

**What was the hardest bug class?**
Native memory and coroutine cancellation. Cancelling a coroutine doesn't stop native inference that's already running, so closing the interpreter on cancel could free memory mid-run. Close only runs when the mutex is free; otherwise the running inference closes it when it finishes. Loading runs under `NonCancellable` so an abandoned interpreter is still freed.

**Any iOS-specific traps?**
`TfLiteModelCreate` doesn't copy the model buffer. Pinning a Kotlin `ByteArray` only for the call leaves the interpreter reading freed memory later. The model is copied to the native heap and freed after the interpreter is closed.

**What about battery use?**
Not measured. Inference runs on the CPU with four threads. Each stored image is only embedded once, which helps. Live preview labelling does cost inference per preview frame while the camera is open.

**Does it use the GPU, NNAPI or Core ML?**
No. Inference is CPU-only with four threads. Hardware delegates are untested and are an obvious next step, especially for DINOv2.

**How does it scale to many classes or large datasets?**
Matching is linear in the number of images; refits grow faster than linearly, reaching about 1 s at 5,800 images on the laptop. Accuracy was evaluated with up to 10 classes and 2,000 images. Much larger datasets would need more incremental fitting.

**Does the app work offline?**
Completely. There's no server and the models are bundled.

---

## 8. Kotlin Multiplatform and architecture

**Why Kotlin Multiplatform?**
So the learning code is written once and behaves identically on Android and iOS. About 87% of the Kotlin is shared, including preprocessing, the cache, the matcher, the head and the evaluator. Only the camera, image codecs, pickers, share sheet, zip inflation and the model runtime are per platform.

**What runs the model on each platform?**
LiteRT (formerly TensorFlow Lite) on Android, and the TensorFlow Lite C API through Kotlin/Native cinterop on iOS. The same `.tflite` file serves both, loaded from Compose Multiplatform resources.

**How does iOS get TensorFlow Lite without CocoaPods?**
A Gradle task downloads the `TensorFlowLiteC` 2.17.0 xcframework on the first build, the same archive the official CocoaPod uses. The cinterop definition is three lines.

**Why not MediaPipe Tasks, ML Kit, ONNX Runtime or ExecuTorch?**
The app only needs raw tensors in and an embedding out. The LiteRT interpreter on Android and the TFLite C API on iOS do that with the same model file and very little code. The bundled MobileNets are already TFLite models. The other runtimes weren't evaluated.

**Do Android and iOS give exactly the same results?**
The matcher, head and preprocessing are the same Kotlin code, so they behave the same. The model runtimes differ (LiteRT on Android, TFLite C 2.17.0 on iOS), so embeddings could differ in the last few decimal places. That isn't benchmarked.

**How is the model abstracted?**
A `Backbone` interface with one method, `embed(input: FloatArray): FloatArray`, and a `BackboneLoader` that Koin provides per platform. Everything above it is shared.

**How do you test ML code without a model?**
Tests pass a fake `Backbone` that returns an image's mean colour and counts its calls, which is how "each image is embedded once" is asserted. Matcher and head tests use seeded random vectors. The shared test suite runs on the JVM and on the iOS simulator.

**How do you test something non-deterministic?**
The learning code is deterministic on purpose. Grouping, centroids and radii have no randomness, and head training uses no random initialisation or shuffling. Tests use fixed seeds for their synthetic data.

---

## 9. Privacy, product and practical use

**Does any data leave the device?**
No. There's no server, no upload and no network call in the sorting path. Models are bundled in the app.

**Why not use the cloud as a fallback for better accuracy?**
It would cost a backend and the user's privacy, and it would break offline use. The results also show a better on-device backbone (DINOv2) gets most of the accuracy a bigger model would give.

**What does the user actually see?**
A camera with an **Auto** chip. A live label shows the closest class and its similarity. Captures go straight to that class, or wait under Unknown. There's one slider for unknown detection and one setting for the model. There's no "Train" button.

**What if auto-sort puts an image in the wrong class?**
The user moves it to the right class. That changes the dataset, which triggers a refit, so the correction immediately becomes part of what the matcher learns from. The cached embedding is reused, so the move costs no inference.

**What does the user get at the end?**
A zip of class folders with a `metadata.json`, which standard training tools read directly. Auto-sort is a labelling aid; the output is a dataset for training a model elsewhere.

**Can you export the trained head or the matcher?**
Not today. The export is the labelled dataset. Exporting the matcher would be possible since it's just vectors and radii.

**What phones does it support?**
Android 7.0 (API 24) and newer, and iOS 18.2 and newer, on phones and tablets.

**How much does the app's size grow because of the models?**
About 38 MB for all three. MobileNetV3-Small alone is about 4 MB.

**What's the licence?**
Apache 2.0. The bundled DINOv2 and MediaPipe models are also Apache 2.0.

**Who is this for?**
Anyone building an image classification dataset: students, researchers, hobbyists, and teams collecting data for a custom model. More broadly, the recipe is for any app developer who wants personalization without a backend.

---

## 10. Limitations, future work and comparisons

**What are the biggest limitations?**
Thresholds were tuned on one backbone and on the evaluation data. Bursts were simulated. There are no on-device timings. The head's gain is small and its training cost on a phone is unmeasured. Inference is CPU-only. And the three models cost about 38 MB.

**What would you do next?**
A CLIP-style encoder so a class can be created by name before it has images. On-device timing and hardware delegates. Showing users an estimated accuracy for their own dataset using the leave-one-out evaluator that's already in the code. A real-burst benchmark. And temporal smoothing of the live label across frames.

**How does this compare with Apple Photos or Google Photos recognising my pets and people?**
Those systems recognise categories the vendor defined, or cluster faces with purpose-built models. DataDroid learns arbitrary categories the user invents, from a handful of examples, with a general-purpose backbone.

**How does this compare with Teachable Machine?**
The idea is similar: a frozen pretrained network with a small trainable part on top. DataDroid runs entirely on the phone in a native app, handles bursts and unknowns explicitly, and evaluates itself with leave-one-group-out.

**What would you do differently if starting again?**
Start with the backbone comparison. The benchmarks show a better backbone was worth several times more than the matcher work.

**What's the one thing the audience should try in their own apps?**
Freeze a good backbone and start with nearest-centroid on its embeddings. It works from one example, needs no training infrastructure, and is a strong baseline before you add anything clever.

---

## 11. Challenging questions

**Isn't the trained head over-engineering for a 1-point gain?**
It's about a hundred lines, it never makes things worse on average, and it helps in the cases centroids can't handle. But the honest answer is yes, the backbone matters far more. That's why the talk ends with "spend effort on the backbone first".

**Your matcher improvements are only 2 points. Why bother?**
The accuracy gain is modest, but the bigger change is unknown detection: rejection of unseen Flowers images went from 2.1% to 15.1% with the same backbone. And the gains land where users feel them: tiny classes and bursts.

**Why should we trust laptop benchmarks for a mobile talk?**
The *accuracy* numbers don't depend on the device, because the same Kotlin code and model files run on both. The *timing* numbers do, and I've said clearly that those are laptop numbers and phone timings are still to be measured.

**Couldn't you get the same result by just fine-tuning in the cloud?**
Probably similar accuracy, with more infrastructure, a network round trip, and the user's photos on someone else's server. The point of the talk is how far you get without any of that.

**Is cosine similarity on 1,024 dimensions meaningful? Doesn't the curse of dimensionality hurt?**
Embedding spaces from trained networks have far lower effective dimension than their size. The benchmarks show the similarities are informative in practice, and centring improves them further by removing the shared direction.

**What happens when the user's classes are visually almost identical, like left shoe and right shoe?**
That's the hardest case for any method built on general features. It's like Imagewoof's dog breeds, where accuracy is lower. A stronger backbone helps most, and the trained head can help when the classes share a large variation unrelated to the difference.

**Why should anyone believe the 0.85 burst threshold generalises?**
It's measured on simulated bursts with one backbone, so it might not. The cost of being slightly wrong is small: a few frames under- or over-weighted, not wrong labels. A real-burst benchmark is the right way to check.
