---
title: "Burst-Aware, Training-Free Auto-Sorting for On-Device Image Dataset Collection"
author:
  - name: Tawhid Monowar
    affiliation: "[Affiliation]"
    email: "[Email]"
date: "[Month Year]"
abstract: |
  Building an image classification dataset on a phone usually means picking a class before every shot. We study
  *auto-sorting*: capture first, and let the device file each image into the class it most resembles, or hold it
  aside as unknown. The setting needs no training step, starts from classes of a few images, and must cope with
  camera bursts that add runs of near-identical frames. We describe DataDroid, an open-source Kotlin Multiplatform
  app for Android and iOS that runs auto-sorting entirely on the device. It uses a frozen MobileNetV3-Small
  embedder and a training-free matcher. The matcher weights each group of near-duplicate frames as one image,
  centres embeddings on the dataset mean, scores a class by blending centroid similarity with the mean similarity
  of its five nearest images, and rejects unknowns with per-class radii measured leave-one-group-out. We compare it
  with the app's previous nearest-class-mean matcher under leave-one-out evaluation that excludes the held-out
  image's own burst. We use embeddings from the app's model on TF Flowers and Imagewoof with simulated bursts.
  Across ten scenarios, closed-set accuracy improves by 2.0 points on average (range -0.6 to +5.2). The gain is
  significant in five scenarios and significantly negative in none. At matched retention of known images, the new
  matcher rejects far more images of unseen classes. On flowers it rejects 15.1% of unknowns, against 2.1%, while
  keeping slightly more known images. Refits cost about 20x more (108 ms against 5 ms for 2,000 images on a laptop
  CPU). An ablation finds near-duplicate weighting the most consistent component. Code, evaluation harness and
  embedding script are released.
---

# 1 Introduction

Supervised image classifiers still begin with a labelled dataset. Practitioners who need a custom classifier, such
as a plant-disease detector for one farm or a defect classifier for one production line, often collect their own
images. A phone is a natural tool for this: it has a good camera and is already in the field. Yet the usual phone
workflow of choosing a class, shooting, choosing the next class and shooting again is slow. It is also error-prone,
because a forgotten class switch silently mislabels a run of images.

DataDroid is an open-source app for Android and iOS that turns a phone into a dataset studio. Users create datasets
and classes, capture or import images, check class balance, and export a zip of class folders ready for training.
Its *auto-sort* mode removes the class-switching step. The user captures without choosing a class. The app embeds
each image with an on-device network and files it into the class it most resembles. Images that resemble no class
are held under *Unknown*, where one tap starts a new class with them. Everything runs offline, so no image leaves
the device.

Auto-sorting is a form of few-shot, open-set classification, but several practical constraints set it apart from
the usual benchmark setting:

1. **No training step.** Classes change constantly as the user captures. Any per-change optimisation, even a
   linear probe, adds latency and complexity on a phone. A training-free matcher over frozen embeddings refits in
   milliseconds and needs no hyper-parameter search on the device.
2. **Tiny, growing classes.** A new class may hold one to five images, and the matcher must already behave sensibly.
3. **Bursts.** DataDroid records about five frames per second while the shutter is held. A two-second hold adds ten
   near-identical images to one class. Statistics such as class means and spreads are then dominated by whichever
   moment was burst-captured longest.
4. **Open set.** Users point the camera at things that belong to no class yet, and the app must notice.

The first release of DataDroid used a simple nearest-class-mean (NCM) matcher with a per-class rejection radius.
This paper describes a replacement designed around the four constraints above, and an evaluation protocol that
accounts for bursts.

Our contributions are:

- A description of a complete on-device, training-free auto-sorting pipeline shared across Android and iOS
  (Section 3).
- A burst-aware matcher combining near-duplicate group weighting, mean centring, a centroid and nearest-neighbour
  hybrid score, and leave-one-group-out radius estimation for unknown rejection (Section 4).
- A leave-one-out evaluation protocol that excludes the held-out image's near-duplicates, without which any
  matcher looks near-perfect on burst-heavy data. It is released as part of the app's shared code (Section 5).
- An evaluation on embeddings from the deployed model for two public datasets with simulated bursts. It reports
  paired confidence intervals, open-set operating points, an ablation and refit cost (Section 6). We are explicit
  about its limitations (Section 7).

# 2 Related work

**Nearest-class-mean and prototype classifiers.** Classifying by distance to class means over fixed features is a
long-standing approach for adding classes at near-zero cost [Mensink et al., 2013]. It underlies exemplar-based
incremental learning [Rebuffi et al., 2017] and, with learned embeddings, prototypical networks for few-shot
learning [Snell et al., 2017]. *SimpleShot* [Wang et al., 2019] showed that nearest-neighbour and nearest-centroid
classification over features from a pre-trained network are strong few-shot baselines once features are centred
and L2-normalised ("CL2N"). Our centring step follows this observation. We centre on the user's own dataset because
no separate base-class set is available on the device.

**Nearest-neighbour classification.** k-nearest-neighbour rules [Cover and Hart, 1967] adapt to multi-modal classes
that a single mean cannot represent. We blend a weighted top-k neighbour similarity with centroid similarity. The
centroid term stabilises very small classes, and the neighbour term follows classes with several distinct looks.

**Open-set recognition and out-of-distribution detection.** Open-set recognition [Scheirer et al., 2013; Bendale
and Boult, 2016] asks a classifier to reject inputs from unseen classes. Common baselines threshold the maximum
softmax probability [Hendrycks and Gimpel, 2017] or the distance to the nearest training feature [Sun et al., 2022].
We use a distance threshold expressed relative to a per-class radius. This lets one user-facing "strictness" control
behave consistently across classes of different spread.

**Mobile backbones and transfer.** MobileNetV3 [Howard et al., 2019] was designed for on-device inference. We use
the MobileNetV3-Small image embedder distributed with MediaPipe [Lugaresi et al., 2019]. Better ImageNet models tend
to transfer better [Kornblith et al., 2019], and recent mobile-oriented encoders such as MobileCLIP [Vasu et al.,
2024] or self-supervised features such as DINOv2 [Oquab et al., 2023] are natural future replacements (Section 8).
This paper improves the matcher on top of a fixed backbone. It does not change the backbone.

**Near-duplicates in evaluation.** Near-duplicate images that straddle a train/test split inflate measured
accuracy. Our evaluation excludes the held-out image's whole burst group when refitting, for the same reason.

# 3 System overview

DataDroid is written in Kotlin Multiplatform with Compose Multiplatform UI. About 85% of the Kotlin code, including
the UI, storage, preprocessing, embedding cache and matcher, is shared between Android and iOS. Only the camera,
image codecs, pickers and model runtime are platform-specific. As a result, both platforms sort images with
identical logic.

**Capture.** A tap captures one image. Holding the shutter records about five frames per second. Frames are
centre-cropped to 640x640 squares.

**Embedding.** Each image is decoded with EXIF orientation applied, centre-cropped to a square, scaled to 224x224
and mapped to RGB values in [0, 1]. A MobileNetV3-Small image embedder (MediaPipe, float32, about 4 MB) returns a
1,024-dimensional feature vector. Inference uses LiteRT on Android and the TensorFlow Lite C API on iOS, with four
threads. The model is loaded once and shared by all open datasets.

**Embedding cache.** Each image's embedding is appended to a per-dataset binary file, so every image is embedded
only once. The matcher is refitted from cached embeddings whenever the dataset changes, for example after an image
is added, moved or deleted, or a class is created. Refits are conflated, so only the latest dataset state is fitted.
Refitting happens off the UI thread.

**Sorting.** For each captured frame or imported photo, the app embeds the image and asks the matcher for a class.
If the image is within the class's acceptance radius (scaled by the strictness setting), it is saved to that class.
Otherwise it is held under *Unknown* with a preview and the closest class. A strictness of 0 disables rejection.

# 4 Method

## 4.1 Notation

A dataset has classes $k = 1..C$ with raw embeddings $x_i \in \mathbb{R}^D$ ($D = 1024$). Let
$u_i = x_i / \lVert x_i \rVert$ be the unit-normalised embedding, $n_k$ the number of images in class $k$, and $q$ a
query embedding.

## 4.2 Baseline: nearest class mean with a coverage radius

The previous matcher (DataDroid's first release) computes for each class the centroid
$c_k = \mathrm{norm}(\sum_{i \in k} u_i)$ and assigns $q$ to $k^* = \arg\max_k \hat q \cdot c_k$, where
$\hat q = q / \lVert q \rVert$. For classes with at least five images, the radius $r_k$ is the 0.8-quantile of the
in-sample distances $1 - u_i \cdot c_k$ (each image included in its own centroid). Classes with fewer images borrow
the median radius of the others (0.12 if no class can be measured). Radii are floored at 0.12. The image is accepted if
$1 - \hat q \cdot c_{k^*} \le r_{k^*} \cdot m(s)$, where the margin $m(s) = 2.5 + (1 - 2.5)\, s/100$ maps the user's
strictness $s \in [0, 100]$ to between 2.5 and 1 radii.

The baseline has three weaknesses in this setting:

- A burst of $b$ frames contributes $b$ nearly identical vectors to $c_k$, dragging it towards one moment.
- The single mean cannot represent classes with several looks.
- In-sample radii are biased low, and bursts shrink them further, so the margin needed to accept ordinary images
  ends up large enough that unseen classes are rarely rejected (Section 6.3).

## 4.3 Burst-aware matcher

**Near-duplicate grouping.** Within each class, images are assigned to groups greedily in storage order. Image $i$
joins the group of the first earlier group leader $\ell$ with $u_i \cdot u_\ell \ge \tau$, and otherwise starts a new
group with itself as leader. Each image gets weight $w_i = 1/|G(i)|$, so a group weighs as one image. We set
$\tau = 0.85$ from embedding statistics (Section 6.1). Simulated neighbouring burst frames have cosine similarity to
their source photo of at least 0.86 in 95% of cases, while the most similar *distinct* photo of the same dataset
exceeds 0.68 to 0.75 only 5% of the time.

**Centring.** With at least two classes, embeddings are centred on the weighted mean unit vector over all classes,
$\mu = \sum_i w_i u_i / \sum_i w_i$, and renormalised: $v_i = \mathrm{norm}(u_i - \mu)$, with
$\hat q = \mathrm{norm}(q/\lVert q\rVert - \mu)$. With a single class there is no between-class contrast to keep,
so centring is skipped.

**Hybrid score.** Each class has a weighted centroid $c_k = \mathrm{norm}(\sum_{i \in k} w_i v_i)$. The neighbour
term $N_k(\hat q)$ is a weighted top-$K$ mean: class members are visited in decreasing order of $\hat q \cdot v_i$,
each contributing weight $\min(w_i, K - \text{taken})$ until total weight $K = 5$ is reached (or the class is
exhausted). Because of the weights, a burst group counts as at most one neighbour. The class score is

$$ s_k(\hat q) = \tfrac{1}{2}\,\hat q \cdot c_k + \tfrac{1}{2}\, N_k(\hat q), $$

and the prediction is $k^* = \arg\max_k s_k$, with distance $d = 1 - s_{k^*}$.

**Leave-one-group-out radius.** For a class with at least five groups, we probe up to 32 evenly spaced groups. For
each probe group $g$, we score its leader against the class *with $g$ removed*: the centroid is recomputed without
$g$'s weighted contribution and $g$'s members are excluded from the neighbour term. The radius $r_k$ is the
0.8-quantile of these distances. Classes with fewer than five groups borrow the median measured radius of the other
classes, or 0.75 if no class can be measured (the low end of radii measured in our data, whose 25th percentile is 0.74 to
0.82).
Radii are floored at 0.12.

**Acceptance.** An image is accepted if $s = 0$ or $d \le r_{k^*} \cdot m'(s)$ with
$m'(s) = 1.3 + (0.9 - 1.3)\, s/100$. Leave-one-group-out radii are unbiased estimates of how far a new image of the
class lands, so the useful margin range is much narrower than the baseline's. The end points 1.3 and 0.9 were
chosen from operating curves (Section 7 discusses the resulting bias).

The fitting procedure is summarised below.

```text
fit(classes):
  for each class k:
    u_i <- normalize(x_i)
    groups <- greedy leader grouping of u_i at cosine >= 0.85
    w_i <- 1 / size(group(i))
  mu <- weighted mean of all u_i            (only if C >= 2, else 0)
  v_i <- normalize(u_i - mu)
  for each class k:
    c_k <- normalize(sum_i w_i v_i)
    if #groups(k) >= 5:
      for up to 32 evenly spaced groups g:
        d_g <- 1 - score_k(v_leader(g)) with g removed from centroid and neighbours
      r_k <- 0.8-quantile of d_g
  fill unmeasured r_k with median measured radius (or 0.75); floor at 0.12

match(q, strictness s):
  q' <- normalize(normalize(q) - mu)
  k* <- argmax_k 0.5 * q'.c_k + 0.5 * weighted_top5_mean_k(q')
  accept if s == 0 or 1 - score_k*(q') <= r_k* * (1.3 - 0.4 * s / 100)
```

## 4.4 Complexity

Let $N$ be the total number of images and $g_k$ the number of groups in class $k$.

- **Fit.** Grouping costs $O(\sum_k n_k g_k D)$. Radius estimation costs $O(\sum_k P\,(n_k D + n_k \log n_k))$ with
  $P \le 32$ probes.
- **Match.** One query costs $O(N D + \sum_k n_k \log n_k)$.
- **Baseline.** The baseline fits in $O(ND)$ and matches in $O(CD)$.

Section 6.5 reports measured times.

# 5 Evaluation protocol

## 5.1 Data

The app's own model (the exact `.tflite` file shipped in the app) embeds images from two public datasets:

- **TF Flowers** [TensorFlow Team, 2019]: 5 classes (daisy, dandelion, roses, sunflowers, tulips; 633 to 898
  images each).
- **Imagewoof** (`imagewoof2-160`, training split) [Howard, 2019]: 10 visually similar dog breeds (580 to 949
  images each), a fine-grained task.

Per class, 120 source photos are sampled (fixed seed). Preprocessing matches the app's `decodeSquare`: EXIF
transposition, centre square crop, bilinear resize to 224x224, and scaling to [0, 1].

**Simulated bursts.** For the first 20 sampled photos of each class, we add four *neighbouring frames*. Each is a
square crop of 88% to 98% of the short side, offset by up to ±4% of the side, with brightness scaled by 0.9 to 1.1.
This gives five frames per burst, matching about one second of DataDroid's hold-to-record capture. The result is
1,000 embeddings for flowers and 2,000 for Imagewoof. The embedding script (`tools/embed_dataset.py`) is released.

## 5.2 Scenarios

Each draw builds a dataset from the pool. Per class it takes $P$ single photos (from non-burst sources) and, in
burst scenarios, a uniformly random number $B \in \{0..4\}$ of bursts with all five frames. Varying $B$ per class
makes some classes burst-heavy and others not, as happens in real use. The five scenarios are:

| Scenario | Photos per class | Bursts per class |
|---|---|---|
| S1 | 5 | 0 |
| S2 | 20 | 0 |
| S3 | 60 | 0 |
| S4 | 10 | 0 to 4 (x5 frames) |
| S5 | 30 | 0 to 4 (x5 frames) |

Each scenario is drawn 10 times with seeds 0 to 9. Both matchers see identical draws.

## 5.3 Leave-one-group-out evaluation

For each held-out image, the matcher is refitted on all *other groups* and the image is classified. The held-out
image's whole burst group is left out of the refit, because a sibling frame would otherwise make classification
trivial. Only frame-0 images (single photos and each burst's source photo) are held out, so every burst counts once
as a test case. In the benchmark the true burst groups are known. For datasets with unknown bursts, the released
`LeaveOneOut.label` function derives groups with the same near-duplicate rule as the matcher.

**Closed-set accuracy** is the fraction of held-out images whose highest-scoring class is correct (strictness 0).
For each scenario we report mean ± standard deviation over the 10 draws, the paired mean difference with a 95%
Student-t confidence interval (9 degrees of freedom), and the number of draws in which the new matcher wins.

## 5.4 Open-set protocol

In each draw, one class (class index = draw mod $C$) is removed and treated as unknown. On the remaining classes:

- **Known retention** at strictness $s$ is the leave-one-group-out fraction of held-out known images that are
  assigned to their correct class *and* accepted.
- **Unknown rejection** is the fraction of the removed class's frame-0 images that are rejected by a matcher fitted
  on all known images.

Both are pooled over the five scenarios and 10 draws, and reported at $s \in \{0, 25, 50, 75, 100\}$. Because the two
matchers map strictness to margins differently, we compare them at matched retention rather than at equal $s$.

## 5.5 Implementation

The main results (Tables 2, 3 and 5) come from the shipped Kotlin implementation, run as a JVM unit test
(`MatcherBenchmark`). The baseline is the previous release's matcher, kept verbatim as a test fixture. The
ablation (Table 4) uses a NumPy re-implementation with identical scoring rules. Its random draws differ from the
Kotlin harness's, so its absolute numbers are not directly comparable to Table 2, only to each other. Timings
were measured on an Apple M3 Pro laptop with OpenJDK 21.

# 6 Results

## 6.1 Embedding statistics

Table 1 motivates the design. Raw MobileNetV3-Small features are not dominated by a shared positive direction:
about 59% of components are negative and the mean cosine similarity between images of different classes is low.
Even so, same-class and different-class similarities overlap heavily. On flowers the mean same-class similarity
(0.230) is less than twice the different-class one (0.124). Simulated burst frames are far more similar to their
source photos than distinct photos are to each other, which makes near-duplicate grouping reliable at
$\tau = 0.85$.

**Table 1.** Statistics of 1,024-d embeddings (frame-0 images; cosine similarity of unit vectors).

| | Imagewoof | Flowers |
|---|---|---|
| Fraction of negative components | 0.588 | 0.586 |
| Mean cosine, same class / different class | 0.241 / 0.069 | 0.230 / 0.124 |
| Same after centring on dataset mean | 0.169 / -0.019 | 0.100 / -0.027 |
| Burst frame vs its source photo: 5th percentile / median | 0.864 / 0.925 | 0.872 / 0.938 |
| Nearest distinct photo: 95th percentile / max | 0.679 / 0.818 | 0.752 / 0.999* |

\*TF Flowers contains a few exact duplicate files. The grouping rule correctly treats them as one image.

## 6.2 Closed-set accuracy

**Table 2.** Leave-one-group-out accuracy (strictness 0), mean ± s.d. over 10 paired draws. Δ is the paired
difference in percentage points with its 95% CI. Wins counts draws where the new matcher is more accurate (ties
count as non-wins).

| Dataset | Scenario | Baseline | Ours | Δ (95% CI) | Wins |
|---|---|---|---|---|---|
| Flowers | S1: 5 photos | 0.664 ± 0.098 | 0.716 ± 0.093 | **+5.2** (+2.2, +8.2) | 7/10 |
| Flowers | S2: 20 photos | 0.817 ± 0.021 | 0.826 ± 0.028 | +0.9 (-0.7, +2.5) | 5/10 |
| Flowers | S3: 60 photos | 0.853 ± 0.016 | 0.876 ± 0.014 | **+2.4** (+1.3, +3.4) | 10/10 |
| Flowers | S4: 10 photos + bursts | 0.731 ± 0.079 | 0.782 ± 0.064 | **+5.1** (+0.7, +9.4) | 9/10 |
| Flowers | S5: 30 photos + bursts | 0.809 ± 0.045 | 0.836 ± 0.034 | **+2.7** (+0.8, +4.7) | 8/10 |
| Imagewoof | S1: 5 photos | 0.726 ± 0.075 | 0.746 ± 0.072 | +2.0 (-0.1, +4.1) | 6/10 |
| Imagewoof | S2: 20 photos | 0.842 ± 0.021 | 0.837 ± 0.022 | -0.6 (-1.6, +0.5) | 4/10 |
| Imagewoof | S3: 60 photos | 0.861 ± 0.011 | 0.859 ± 0.014 | -0.2 (-0.7, +0.2) | 4/10 |
| Imagewoof | S4: 10 photos + bursts | 0.798 ± 0.040 | 0.821 ± 0.031 | **+2.3** (+0.8, +3.7) | 8/10 |
| Imagewoof | S5: 30 photos + bursts | 0.844 ± 0.022 | 0.847 ± 0.023 | +0.3 (-0.6, +1.2) | 6/10 |

Averaged over the ten dataset and scenario pairs, accuracy improves by 2.0 points. The confidence interval excludes
zero in five pairs (bold), all positive. No pair shows a significant loss. The gains concentrate where the method's
design predicts:

- In small classes (S1), centring helps most.
- With bursts (S4, S5), group weighting helps most.
- On flowers, whose classes are visually multi-modal, the neighbour term adds 1.5 to 2.4 points even at 60 photos
  per class.

On Imagewoof without bursts and with 20 or more photos per class, the two matchers are statistically
indistinguishable. Fine-grained breeds are compact, unimodal clusters in this feature space, which suits a plain
class mean.

## 6.3 Open-set rejection

**Table 3.** Known retention / unknown rejection, pooled over all scenarios and draws (one class held out as unknown
per draw).

| Strictness | Flowers baseline | Flowers ours | Imagewoof baseline | Imagewoof ours |
|---|---|---|---|---|
| 0 | 0.846 / 0.000 | 0.869 / 0.000 | 0.853 / 0.000 | 0.854 / 0.000 |
| 25 | 0.846 / 0.006 | 0.868 / 0.022 | 0.853 / 0.009 | 0.847 / 0.075 |
| 50 (default) | 0.839 / 0.021 | 0.852 / 0.151 | 0.850 / 0.027 | 0.822 / 0.251 |
| 75 | 0.805 / 0.145 | 0.783 / 0.461 | 0.822 / 0.183 | 0.749 / 0.567 |
| 100 | 0.621 / 0.639 | 0.635 / 0.797 | 0.610 / 0.824 | 0.603 / 0.832 |

At its default strictness the baseline almost never rejects an unseen class (2 to 3%). Its inflated margin, needed
to compensate for in-sample radii, makes the lower half of the strictness slider nearly inert. Compared at matched
known retention, the new matcher is better:

- On flowers, the baseline at $s = 75$ keeps 0.805 of known images and rejects 0.145 of unknowns. Ours at $s = 50$
  keeps more (0.852) and rejects about the same (0.151).
- At $s = 100$ on flowers, ours dominates on both axes (0.635 / 0.797 against 0.621 / 0.639).
- On Imagewoof, at equal retention of 0.822, ours rejects 0.251 of unknowns against 0.183.
- At the strictest setting on Imagewoof, the two are equivalent.

The new default is also stricter in effect. Against the baseline default, it rejects 13 more points of unknowns on
flowers while also retaining 1.3 points more known images. On Imagewoof it rejects 22 more points of unknowns at a
cost of 2.8 points of known retention. This is a product decision. Users who prefer the old behaviour can lower the
strictness.

Unseen *sibling* classes, such as another dog breed or another flower, are a hard case for open-set rejection. Even
at maximum strictness, about 17% to 20% of such images are accepted into a neighbouring class.

## 6.4 Ablation

**Table 4.** Closed-set accuracy of matcher variants, averaged over S1 to S5
(10 draws each, NumPy re-implementation; absolute values differ slightly from Table 2 because the draws differ).
"Hybrid" is the centroid and top-5 neighbour blend.

| Variant | Centring | Grouping | Flowers | Imagewoof | Mean |
|---|---|---|---|---|---|
| Centroid (baseline scoring) | | | 0.756 | 0.813 | 0.785 |
| Centroid | | ✓ | 0.767 | 0.821 | 0.794 |
| Centroid | ✓ | | 0.755 | 0.813 | 0.784 |
| Centroid | ✓ | ✓ | 0.768 | 0.822 | 0.795 |
| Hybrid | | | 0.771 | 0.811 | 0.791 |
| Hybrid | | ✓ | 0.778 | 0.820 | 0.799 |
| Hybrid | ✓ | | 0.780 | 0.812 | 0.796 |
| **Hybrid (ours)** | ✓ | ✓ | **0.788** | **0.821** | **0.805** |
| Top-5 neighbours only | ✓ | ✓ | 0.793 | 0.807 | 0.800 |

The ablation leads to four observations:

- **Grouping** is the most consistent component. It never hurts, because without bursts no groups form. In the
  burst scenarios it adds 1.4 to 4.2 points to the centroid matcher (for example flowers S4: 0.706 to 0.748;
  Imagewoof S4: 0.804 to 0.829).
- **Centring** is scenario-dependent. It helps markedly with five photos per class (flowers S1: 0.616 to 0.672;
  Imagewoof S1: 0.732 to 0.748). It is neutral to slightly harmful with larger classes and harmful in burst
  scenarios *without* grouping, where burst-heavy classes bias the mean. With grouping, centring is roughly neutral
  on average and helps the hybrid score.
- **The neighbour term** helps flowers (+1.5 points over the centroid on average) and is neutral on Imagewoof.
  Using neighbours *alone* is best on flowers but worst on Imagewoof (0.807). The blend is the most robust choice
  across both datasets.
- On Imagewoof, the simplest improvement, centroid plus grouping (0.821), matches the full method (0.821). A
  deployment that cannot afford neighbour search could take most of the burst benefit at baseline cost.

## 6.5 Cost

**Table 5.** Mean time per refit and per query with all pool images in the dataset (JVM, Apple M3 Pro, after
warm-up).

| | Flowers (1,000 images) | Imagewoof (2,000 images) |
|---|---|---|
| Baseline fit / match | 2.8 ms / 0.01 ms | 5.3 ms / 0.01 ms |
| Ours fit / match | 59.5 ms / 0.82 ms | 107.8 ms / 1.53 ms |

Refits are about 20 times slower, dominated by near-duplicate grouping and radius probes. At 2,000 images a refit
takes about 0.1 s on a laptop core and plausibly several times longer on a mid-range phone. Because refits are
conflated and run off the UI thread, this delays how quickly new images influence sorting but does not block the
interface. Per-query cost is linear in dataset size and stays well under the 200 ms budget of five frames per
second at these sizes. We did not measure on-device timings for this paper.

# 7 Limitations

- **Hyper-parameters were chosen on the evaluation data.** The duplicate threshold, $K$, the coverage, the fallback
  radius and the margin end points were selected by inspecting the same two datasets used for evaluation, with
  small numbers of draws. The closed-set gains depend only on $\tau$ and $K$. However, the open-set operating points
  in particular may be optimistic for other data. A held-out set of datasets would give a cleaner estimate.
- **Simulated bursts.** Real bursts include motion blur, focus hunting and changing backgrounds that our crop and
  brightness jitter does not model. Real bursts may also be less similar than simulated ones, in which case fewer
  frames group together.
- **Backbone pre-training overlap.** Imagewoof's breeds are ImageNet classes, and the MediaPipe embedder was
  pre-trained on ImageNet-scale data. This likely favours both matchers and may understate the benefit of
  structure-aware scoring on genuinely novel domains. Only one backbone was evaluated.
- **Dependent draws.** The 10 draws per scenario sample from a pool of 120 photos per class, so they are not
  independent. The paired t-intervals are therefore somewhat optimistic, and we report win counts alongside them.
  We did not correct for the ten comparisons in Table 2.
- **Scale.** We evaluated up to 2,000 images. Larger datasets increase refit cost super-linearly in the grouping
  step.
- **No user study.** We measure classification quality, not the time saved for users or how they respond to
  rejections.

# 8 Future work

- **Incremental fitting.** Groups, sums and radii can be updated when an image is added instead of refitted from
  scratch. This would bring refit cost back to near the baseline.
- **Stronger backbones.** MobileCLIP-class encoders would also allow zero-shot sorting into classes that have a name
  but no images yet.
- **Temporal smoothing.** Live predictions over consecutive frames could be smoothed.
- **In-app accuracy estimates.** The released leave-one-out evaluator could show users an estimated auto-sort
  accuracy for their own dataset and suggest a strictness.
- **Real-burst benchmark.** A benchmark of real burst captures with ground-truth classes would validate the
  grouping threshold.

# 9 Conclusion

Auto-sorting turns dataset collection on a phone from "choose, then shoot" into "shoot, then confirm". Training-free
matching over frozen on-device embeddings is enough to make it practical. However, the details that matter differ
from those in standard few-shot benchmarks: bursts of near-duplicates and the need to reject unseen classes dominate
the behaviour users experience.

Weighting near-duplicate groups as single images, centring on the dataset mean, blending centroid and neighbour
similarity, and measuring rejection radii leave-one-group-out together give the following on two public datasets:

- an average of 2.0 points better closed-set accuracy, with up to 5.2 points in small or burst-heavy classes and no
  significant losses;
- markedly better unknown rejection at matched retention;
- at the price of a roughly 20 times costlier refit.

All code, including the evaluation harness, is open source.

# Reproducibility

DataDroid, including the matcher (`ClassMatcher.kt`), the evaluator (`LeaveOneOut.kt`), the baseline fixture
(`LegacyClassMatcher.kt`), the benchmark (`MatcherBenchmark.kt`) and the embedding script
(`tools/embed_dataset.py`), is available at <https://github.com/tawhidmonowar/DataDroid> under the Apache 2.0
licence. To reproduce Tables 2, 3 and 5:

```bash
python tools/embed_dataset.py shared/src/commonMain/composeResources/files/mobilenet_v3_small.tflite <flower_photos dir> flowers.bin 120 20
python tools/embed_dataset.py shared/src/commonMain/composeResources/files/mobilenet_v3_small.tflite <imagewoof2-160/train dir> woof.bin 120 20
DATADROID_EMBEDDINGS=flowers.bin,woof.bin ./gradlew :shared:testAndroidHostTest --tests '*MatcherBenchmark*' --rerun -i
```

# Acknowledgements

[Author to complete.] Parts of the implementation, experiments and drafting of this manuscript were carried out
with the assistance of an AI coding assistant (Claude, Anthropic). The author reviewed the code, results and text
and takes full responsibility for the content.

# References

- Bendale, A. and Boult, T. E. (2016). Towards open set deep networks. In *CVPR*.
- Cover, T. and Hart, P. (1967). Nearest neighbor pattern classification. *IEEE Transactions on Information Theory*,
  13(1):21–27.
- Hendrycks, D. and Gimpel, K. (2017). A baseline for detecting misclassified and out-of-distribution examples in
  neural networks. In *ICLR*.
- Howard, A., Sandler, M., Chu, G., Chen, L.-C., Chen, B., Tan, M., Wang, W., Zhu, Y., Pang, R., Vasudevan, V.,
  Le, Q. V. and Adam, H. (2019). Searching for MobileNetV3. In *ICCV*.
- Howard, J. (2019). Imagenette and Imagewoof. <https://github.com/fastai/imagenette>.
- Kornblith, S., Shlens, J. and Le, Q. V. (2019). Do better ImageNet models transfer better? In *CVPR*.
- Lugaresi, C., Tang, J., Nash, H., McClanahan, C., Uboweja, E., Hays, M., Zhang, F., Chang, C.-L., Yong, M. G.,
  Lee, J., Chang, W.-T., Hua, W., Georg, M. and Grundmann, M. (2019). MediaPipe: A framework for building
  perception pipelines. arXiv:1906.08172.
- Mensink, T., Verbeek, J., Perronnin, F. and Csurka, G. (2013). Distance-based image classification: Generalizing
  to new classes at near-zero cost. *IEEE TPAMI*, 35(11):2624–2637.
- Oquab, M. et al. (2023). DINOv2: Learning robust visual features without supervision. arXiv:2304.07193.
- Rebuffi, S.-A., Kolesnikov, A., Sperl, G. and Lampert, C. H. (2017). iCaRL: Incremental classifier and
  representation learning. In *CVPR*.
- Scheirer, W. J., de Rezende Rocha, A., Sapkota, A. and Boult, T. E. (2013). Toward open set recognition.
  *IEEE TPAMI*, 35(7):1757–1772.
- Snell, J., Swersky, K. and Zemel, R. (2017). Prototypical networks for few-shot learning. In *NeurIPS*.
- Sun, Y., Ming, Y., Zhu, X. and Li, Y. (2022). Out-of-distribution detection with deep nearest neighbors. In
  *ICML*.
- TensorFlow Team (2019). Flowers dataset. <https://www.tensorflow.org/datasets/catalog/tf_flowers>.
- Vasu, P. K. A., Pouransari, H., Faghri, F., Vemulapalli, R. and Tuzel, O. (2024). MobileCLIP: Fast image-text
  models through multi-modal reinforced training. In *CVPR*.
- Wang, Y., Chao, W.-L., Weinberger, K. Q. and van der Maaten, L. (2019). SimpleShot: Revisiting
  nearest-neighbor classification for few-shot learning. arXiv:1911.04623.
