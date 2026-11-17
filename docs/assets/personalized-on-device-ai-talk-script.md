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

### Likely questions and short answers

**Is this really transfer learning if the network never trains?**
Yes. Using a frozen pretrained network as a feature extractor is the most common form of transfer learning. The linear head on top is a standard linear probe. What DataDroid doesn't do is fine-tune the backbone itself.

**Why not fine-tune the backbone on the phone?**
With 5 to 50 images per class it would overfit, it needs backpropagation through millions of weights, and it would have to rerun after every shutter press. The benchmarks also show the trained head adds only 0.2 to 1.2 points over the training-free matcher, so a deeper fine-tune is unlikely to pay for its cost.

**Why does the trained head add so little?**
Embeddings from a good backbone are already well separated by their centroids, so a linear layer has little left to fix. The head helps most where classes share a large variation that isn't related to class, which the synthetic test isolates.

**How fast is it on a real phone?**
Not measured yet. All timings are from a laptop JVM. A full refit from scratch takes about 9 ms for 250 images and about 1 s for 5,800 on that laptop, and matching one image takes 0.2 to 4.3 ms. Expect a mid-range phone to be several times slower, which is why refits are incremental and conflated.

**Why not use the GPU or NNAPI or Core ML?**
Hardware delegates haven't been tested. Inference runs on the CPU with 4 threads. It's a clear next step, especially for DINOv2.

**Does any data leave the device?**
No. There's no server and no network call in the sorting path. Models are bundled in the app, which is why it's about 38 MB of models.

**How does it handle a new object that isn't in any class?**
The per-class radius, measured with each group left out, decides whether the image belongs to the closest class at all. The "Unknown detection" slider sets the margin. Rejected images are kept as unknowns for the user to file later.

**Why is DINOv2 so much better?**
It's self-supervised and trained to produce features that work well for nearest-neighbour retrieval, which is exactly how the matcher uses them. MobileNet embeddings come from models trained to classify ImageNet labels.
