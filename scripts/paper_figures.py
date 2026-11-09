"""Draws the paper's figures from the CSVs in paper/data into paper/figures (PDF and PNG).

Usage: python tools/paper_figures.py [repository root]
Needs matplotlib and numpy.
"""
import csv
import sys
from collections import defaultdict
from pathlib import Path

import matplotlib as mpl
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.lines import Line2D
from matplotlib.patches import Circle, FancyArrowPatch, FancyBboxPatch

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
DATA = ROOT / "paper" / "data"
OUT = ROOT / "paper" / "figures"

OURS = "#2a78d6"
BASE = "#eb6834"
BURST = "#1baf7a"
CONTEXT = "#8a8a8a"
INK = "#1f1f1f"
INK2 = "#555555"
GRID = "#e7e7e7"
BOX = "#f6f7f9"
BOX_EDGE = "#a3a7ad"
FROZEN = "#eaf2fc"

SINGLE = 3.35  # inches, one column
DOUBLE = 6.9  # inches, full width

mpl.rcParams.update({
    "font.family": "Arial",
    "font.size": 8,
    "axes.titlesize": 8.5,
    "axes.titleweight": "bold",
    "axes.titlelocation": "left",
    "axes.labelsize": 8,
    "xtick.labelsize": 7.5,
    "ytick.labelsize": 7.5,
    "legend.fontsize": 7.5,
    "text.color": INK,
    "axes.labelcolor": INK,
    "axes.edgecolor": "#9a9a9a",
    "axes.linewidth": 0.6,
    "xtick.color": INK2,
    "ytick.color": INK2,
    "xtick.major.width": 0.6,
    "ytick.major.width": 0.6,
    "xtick.major.size": 2.5,
    "ytick.major.size": 2.5,
    "axes.spines.top": False,
    "axes.spines.right": False,
    "axes.grid": True,
    "grid.color": GRID,
    "grid.linewidth": 0.6,
    "grid.linestyle": "-",
    "axes.axisbelow": True,
    "legend.frameon": False,
    "legend.handlelength": 1.6,
    "lines.linewidth": 1.5,
    "lines.solid_capstyle": "round",
    "lines.solid_joinstyle": "round",
    "savefig.bbox": "tight",
    "savefig.pad_inches": 0.03,
    "pdf.fonttype": 42,
    "ps.fonttype": 42,
})


def read(name):
    with open(DATA / name) as f:
        return list(csv.DictReader(f))


def save(fig, name):
    OUT.mkdir(parents=True, exist_ok=True)
    fig.savefig(OUT / f"{name}.pdf")
    fig.savefig(OUT / f"{name}.png", dpi=300)
    plt.close(fig)


def marker(ax, x, y, color, shape="o", size=5.5, **kw):
    """A filled marker with a white ring, so it stays legible over lines."""
    ax.plot(x, y, shape, color=color, markersize=size, markeredgecolor="white", markeredgewidth=0.9, **kw)


# --------------------------------------------------------------------------------------------------------------------
# Figure 1: system pipeline


def box(ax, x, y, w, h, title, detail=None, fill=BOX):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=1.2", linewidth=0.7,
                                edgecolor=BOX_EDGE, facecolor=fill))
    if detail:
        ax.text(x + w / 2, y + h * 0.7, title, ha="center", va="center", fontsize=6.8, fontweight="bold")
        ax.text(x + w / 2, y + h * 0.33, detail, ha="center", va="center", fontsize=6.2, color=INK2, linespacing=1.3)
    else:
        ax.text(x + w / 2, y + h / 2, title, ha="center", va="center", fontsize=7, fontweight="bold")


def arrow(ax, start, end, label=None, label_offset=(0, 1.4), connection="arc3", ha="center"):
    ax.add_patch(FancyArrowPatch(start, end, arrowstyle="-|>", mutation_scale=7, linewidth=0.8, color="#6b6b6b",
                                 shrinkA=0, shrinkB=0, connectionstyle=connection))
    if label:
        mx = (start[0] + end[0]) / 2 + label_offset[0]
        my = (start[1] + end[1]) / 2 + label_offset[1]
        ax.text(mx, my, label, ha=ha, va="bottom", fontsize=6.3, color=INK2)


def figure_pipeline():
    fig, ax = plt.subplots(figsize=(DOUBLE, 2.35))
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 35)
    ax.axis("off")

    h = 9.5
    top = 22
    bottom = 3.5
    mid = h / 2
    xs = {"capture": 0, "prep": 20.5, "model": 39.5, "match": 62, "unknown": 84.5}
    w = {"capture": 16, "prep": 14.5, "model": 16.5, "match": 15.5, "unknown": 15.5}
    right = {k: xs[k] + w[k] for k in xs}

    ax.text(0, top + h + 2.4, "Per image", fontsize=7, color=INK2, fontweight="bold")
    ax.text(0, bottom + mid, "Per dataset\nchange", fontsize=7, color=INK2, fontweight="bold", va="center")

    box(ax, xs["capture"], top, w["capture"], h, "Capture or import", "tap, 5 fps hold,\nor photo library")
    box(ax, xs["prep"], top, w["prep"], h, "Preprocess", "EXIF, centre crop,\nresize to 224×224")
    box(ax, xs["model"], top, w["model"], h, "MobileNetV3-Small", "frozen backbone\n(LiteRT / TFLite)", fill=FROZEN)
    box(ax, xs["match"], top, w["match"], h, "Matcher", "hybrid score,\nper-class radius test")
    box(ax, xs["unknown"], top, w["unknown"], h, "Unknown queue", "new class, file,\nor discard")

    box(ax, xs["model"], bottom, w["model"], h, "Embedding cache", "embeddings.bin,\none vector per image")
    box(ax, xs["match"], bottom, w["match"], h, "Refit", "grouping, centring,\ncentroids, radii")
    box(ax, xs["unknown"], bottom, w["unknown"], h, "Dataset", "class folders,\nimage records")

    arrow(ax, (right["capture"], top + mid), (xs["prep"], top + mid))
    arrow(ax, (right["prep"], top + mid), (xs["model"], top + mid))
    arrow(ax, (right["model"], top + mid), (xs["match"], top + mid), "1,024-d", label_offset=(0, 0.5))
    arrow(ax, (right["match"], top + mid), (xs["unknown"], top + mid), "reject", label_offset=(0, 0.5))
    arrow(ax, (right["match"], top + 1.0), (xs["unknown"], bottom + h - 1.0), "accept", label_offset=(0.2, -0.6),
          ha="left")

    arrow(ax, (xs["unknown"], bottom + mid), (right["match"], bottom + mid))
    arrow(ax, (right["model"], bottom + mid), (xs["match"], bottom + mid))
    model_x = xs["model"] + w["model"] / 2
    match_x = xs["match"] + w["match"] / 2
    arrow(ax, (model_x, top), (model_x, bottom + h), "stored images", label_offset=(-1.0, -0.8), ha="right")
    arrow(ax, (match_x, bottom + h), (match_x, top), "refitted", label_offset=(-1.0, -0.8), ha="right")

    save(fig, "fig1_pipeline")


# --------------------------------------------------------------------------------------------------------------------
# Figure 2: schematic of the two failure modes the matcher addresses


def figure_schematic():
    rng = np.random.default_rng(3)
    fig, (a, b) = plt.subplots(1, 2, figsize=(DOUBLE, 2.55))
    for ax in (a, b):
        ax.set_aspect("equal")
        ax.grid(False)
        ax.set_xticks([])
        ax.set_yticks([])
        for side in ("left", "bottom"):
            ax.spines[side].set_visible(False)

    # (a) A burst drags the class mean towards one moment.
    photos = rng.normal(0, 0.45, (8, 2))
    burst_centre = np.array([1.55, 1.05])
    burst = burst_centre + rng.normal(0, 0.06, (14, 2))
    other = np.array([3.35, -0.35]) + rng.normal(0, 0.45, (8, 2))
    a.scatter(*other.T, s=16, facecolor="white", edgecolor=CONTEXT, linewidth=0.8, zorder=2)
    a.scatter(*photos.T, s=16, color="#4a4a4a", edgecolor="white", linewidth=0.6, zorder=3)
    a.scatter(*burst.T, s=9, color="#4a4a4a", edgecolor="white", linewidth=0.4, zorder=3)
    a.add_patch(Circle(burst_centre, 0.32, fill=False, edgecolor=CONTEXT, linewidth=0.7, linestyle=(0, (2, 1.5))))
    plain = np.vstack([photos, burst]).mean(0)
    weighted = np.vstack([photos, burst_centre]).mean(0)
    marker(a, *plain, BASE, "s", 7, zorder=5)
    marker(a, *weighted, OURS, "o", 7.5, zorder=5)
    a.annotate("burst of 14 frames,\nweighted as one image", burst_centre + [0.3, 0.2], xytext=(2.25, 1.75),
               fontsize=6.6, color=INK2, va="center",
               arrowprops=dict(arrowstyle="-", color=CONTEXT, linewidth=0.6, shrinkA=1, shrinkB=1))
    a.annotate("mean of all images", plain, xytext=(1.75, -1.2), fontsize=6.6, color=INK2, va="center",
               arrowprops=dict(arrowstyle="-", color=CONTEXT, linewidth=0.6, shrinkA=1, shrinkB=4))
    a.annotate("group-weighted mean", weighted, xytext=(-1.55, -1.25), fontsize=6.6, color=INK2, va="center",
               arrowprops=dict(arrowstyle="-", color=CONTEXT, linewidth=0.6, shrinkA=1, shrinkB=4))
    a.text(-1.2, 1.25, "class A", fontsize=6.8, color=INK, fontweight="bold")
    a.text(3.1, 0.55, "class B", fontsize=6.8, color=INK, fontweight="bold")
    a.set_xlim(-1.7, 4.3)
    a.set_ylim(-1.75, 2.65)
    a.set_title("(a) Bursts pull the class mean", pad=4)

    # (b) A class with two looks: its mean falls between them, but its nearest images don't.
    upper = np.array([0.0, 1.45]) + rng.normal(0, 0.28, (7, 2))
    lower = np.array([0.0, -1.45]) + rng.normal(0, 0.28, (7, 2))
    other = np.array([1.65, -0.45]) + rng.normal(0, 0.25, (8, 2))
    query = np.array([0.72, -1.05])
    mean_a = np.vstack([upper, lower]).mean(0)
    mean_b = other.mean(0)
    near = np.vstack([upper, lower])[np.argsort(np.linalg.norm(np.vstack([upper, lower]) - query, axis=1))[:5]]
    for p in near:
        b.plot([query[0], p[0]], [query[1], p[1]], color=OURS, linewidth=1.1, zorder=1)
    b.plot([query[0], mean_b[0]], [query[1], mean_b[1]], color=BASE, linewidth=0.9, zorder=1)
    b.scatter(*other.T, s=16, facecolor="white", edgecolor=CONTEXT, linewidth=0.8, zorder=2)
    b.scatter(*np.vstack([upper, lower]).T, s=16, color="#4a4a4a", edgecolor="white", linewidth=0.6, zorder=3)
    marker(b, *mean_a, CONTEXT, "X", 7, zorder=4)
    marker(b, *mean_b, CONTEXT, "X", 7, zorder=4)
    b.plot(*query, marker="D", markersize=6, markerfacecolor="white", markeredgecolor=INK, markeredgewidth=1.0,
           zorder=6)
    b.annotate("mean of class A falls\nbetween its two looks", mean_a, xytext=(-2.15, 0.15), fontsize=6.6,
               color=INK2, va="center",
               arrowprops=dict(arrowstyle="-", color=CONTEXT, linewidth=0.6, shrinkA=1, shrinkB=4))
    b.annotate("query", query, xytext=(1.35, -1.3), fontsize=6.6, color=INK2, va="center",
               arrowprops=dict(arrowstyle="-", color=CONTEXT, linewidth=0.6, shrinkA=1, shrinkB=4))
    b.text(-2.0, 1.6, "class A", fontsize=6.8, color=INK, fontweight="bold")
    b.text(-2.0, -1.55, "class A", fontsize=6.8, color=INK, fontweight="bold")
    b.text(1.55, 0.2, "class B", fontsize=6.8, color=INK, fontweight="bold")
    b.legend(handles=[Line2D([], [], color=BASE, linewidth=0.9, label="nearest class mean: B"),
                      Line2D([], [], color=OURS, linewidth=0.9, label="nearest 5 images per class: A")],
             loc="upper center", bbox_to_anchor=(0.5, 0.0), ncol=2, fontsize=6.6, columnspacing=1.2)
    b.set_xlim(-2.6, 3.4)
    b.set_ylim(-2.15, 2.25)
    b.set_title("(b) A class with two looks", pad=4)

    fig.subplots_adjust(wspace=0.08)
    save(fig, "fig2_schematic")


# --------------------------------------------------------------------------------------------------------------------
# Figure 3: similarity of burst frames versus distinct photos


def figure_similarity():
    rows = read("similarity.csv")
    fig, axes = plt.subplots(1, 2, figsize=(DOUBLE, 2.05), sharey=True)
    bins = np.linspace(0.2, 1.0, 81)
    for ax, dataset in zip(axes, ("Flowers", "Imagewoof")):
        for kind, color, label in (("nearest_distinct_photo", CONTEXT, "most similar other photo"),
                                   ("burst_frame_vs_source", BURST, "burst frame vs its own photo")):
            values = np.array([float(r["cosine"]) for r in rows if r["dataset"] == dataset and r["kind"] == kind])
            counts, edges = np.histogram(values, bins=bins)
            share = counts / counts.sum()
            ax.fill_between(edges[:-1], share, step="post", color=color, alpha=0.12, linewidth=0)
            ax.step(edges[:-1], share, where="post", color=color, linewidth=1.3, label=label)
        ax.axvline(0.85, color=INK, linewidth=0.8)
        ax.text(0.845, ax.get_ylim()[1] * 0.97, "τ = 0.85", ha="right", va="top", fontsize=6.8, color=INK)
        ax.set_xlim(0.2, 1.0)
        ax.set_xticks([0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0])
        ax.set_xlabel("cosine similarity")
        ax.set_title(dataset, pad=3)
    axes[0].set_ylabel("share of images")
    axes[0].legend(loc="upper left", bbox_to_anchor=(0.0, 1.0))
    for ax in axes:
        ax.set_ylim(0, max(a.get_ylim()[1] for a in axes))
    fig.subplots_adjust(wspace=0.1)
    save(fig, "fig3_similarity")


# --------------------------------------------------------------------------------------------------------------------
# Figure 4: paired accuracy differences with 95% confidence intervals


SCENARIO_LABELS = {
    "5 photos/class": "S1  5 photos",
    "20 photos/class": "S2  20 photos",
    "60 photos/class": "S3  60 photos",
    "10 photos + 0-4 bursts": "S4  10 photos + bursts",
    "30 photos + 0-4 bursts": "S5  30 photos + bursts",
}


def figure_closed_set():
    rows = read("closed_set.csv")
    fig, ax = plt.subplots(figsize=(SINGLE, 2.9))
    labels, ys = [], []
    y = 0
    for dataset in ("Flowers", "Imagewoof"):
        ax.text(-3.9, y - 0.05, dataset, fontsize=7.4, fontweight="bold", va="center", ha="left")
        y -= 1
        for r in (r for r in rows if r["dataset"] == dataset):
            diff, low, high = float(r["diff_points"]), float(r["ci_low"]), float(r["ci_high"])
            ax.plot([low, high], [y, y], color=OURS, linewidth=1.5, zorder=2)
            clear = low > 0 or high < 0
            ax.plot(diff, y, "o", markersize=5.5, zorder=3, markeredgewidth=1.1,
                    color=OURS if clear else "white", markeredgecolor=OURS if not clear else "white")
            labels.append(SCENARIO_LABELS[r["scenario"]])
            ys.append(y)
            y -= 1
        y -= 0.6
    ax.axvline(0, color=INK, linewidth=0.8, zorder=1)
    ax.set_yticks(ys, labels)
    ax.tick_params(axis="y", length=0)
    ax.grid(axis="y", visible=False)
    ax.set_xlim(-3.9, 10)
    ax.set_ylim(y + 0.3, 0.6)
    ax.set_xlabel("accuracy change vs baseline (percentage points)")
    ax.spines["left"].set_visible(False)
    ax.legend(handles=[Line2D([], [], marker="o", linestyle="-", color=OURS, markersize=5.5, markeredgecolor="white",
                              label="95% CI excludes 0"),
                       Line2D([], [], marker="o", linestyle="-", color=OURS, markersize=5.5, markerfacecolor="white",
                              markeredgecolor=OURS, label="95% CI includes 0")],
              loc="lower right", bbox_to_anchor=(1.0, -0.02))
    save(fig, "fig4_closed_set")


# --------------------------------------------------------------------------------------------------------------------
# Figure 5: open-set operating points as strictness varies


def figure_open_set():
    rows = read("open_set.csv")
    fig, axes = plt.subplots(1, 2, figsize=(DOUBLE, 2.5), sharey=True)
    for ax, dataset in zip(axes, ("Flowers", "Imagewoof")):
        for matcher, color, shape, label in (("baseline", BASE, "s", "baseline"), ("ours", OURS, "o", "ours")):
            pts = sorted(((int(r["strictness"]), float(r["unknown_rejection"]), float(r["known_retention"]))
                          for r in rows if r["dataset"] == dataset and r["matcher"] == matcher))
            xs = [p[1] for p in pts]
            ys = [p[2] for p in pts]
            ax.plot(xs, ys, color=color, linewidth=1.5, zorder=2, label=label)
            for s, x, yv in pts:
                marker(ax, x, yv, color, shape, 5.5 if s != 50 else 7.5, zorder=3)
        ax.set_xlim(-0.03, 0.9)
        ax.set_ylim(0.57, 0.9)
        ax.set_xlabel("unknown images rejected")
        ax.set_title(dataset, pad=3)
    axes[0].set_ylabel("known images kept correctly")
    handles = [Line2D([], [], color=BASE, marker="s", markersize=5, markeredgecolor="white", label="baseline (NCM)"),
               Line2D([], [], color=OURS, marker="o", markersize=5, markeredgecolor="white", label="ours"),
               Line2D([], [], color=CONTEXT, marker="o", linestyle="none", markersize=7.5, markeredgecolor="white",
                      label="default strictness (s = 50)")]
    axes[0].legend(handles=handles, loc="lower left")
    fig.subplots_adjust(wspace=0.06)
    save(fig, "fig5_open_set")


# --------------------------------------------------------------------------------------------------------------------
# Figure 6: ablation


VARIANTS = [
    ("Centroid", 0, 0, "Centroid (baseline scoring)"),
    ("Centroid", 0, 1, "Centroid + grouping"),
    ("Centroid", 1, 0, "Centroid + centring"),
    ("Centroid", 1, 1, "Centroid + centring + grouping"),
    ("Hybrid", 0, 0, "Hybrid"),
    ("Hybrid", 0, 1, "Hybrid + grouping"),
    ("Hybrid", 1, 0, "Hybrid + centring"),
    ("Hybrid", 1, 1, "Hybrid + centring + grouping (ours)"),
    ("Neighbours only", 1, 1, "Neighbours only + centring + grouping"),
]


def figure_ablation():
    rows = read("ablation.csv")
    means = defaultdict(list)
    for r in rows:
        means[(r["dataset"], r["variant"], int(r["centring"]), int(r["grouping"]))].append(float(r["mean"]))
    fig, axes = plt.subplots(1, 2, figsize=(DOUBLE, 2.45), sharey=True)
    ys = np.arange(len(VARIANTS))[::-1]
    for ax, dataset in zip(axes, ("Flowers", "Imagewoof")):
        values = [np.mean(means[(dataset, v, c, g)]) for v, c, g, _ in VARIANTS]
        ax.axvline(values[0], color=BASE, linewidth=0.7, alpha=0.6, zorder=1)
        for y, value, (_, _, _, label) in zip(ys, values, VARIANTS):
            color = BASE if label.startswith("Centroid (baseline") else OURS if label.endswith("(ours)") else CONTEXT
            marker(ax, value, y, color, "o", 6.5 if color != CONTEXT else 5.5, zorder=3)
        ax.set_xlim(min(values) - 0.004, max(values) + 0.004)
        ax.set_title(dataset, pad=3)
        ax.set_xlabel("mean accuracy over S1–S5")
        ax.xaxis.set_major_formatter(mpl.ticker.FormatStrFormatter("%.3f"))
        ax.locator_params(axis="x", nbins=5)
    axes[0].set_yticks(ys, [label for *_, label in VARIANTS])
    for ax in axes:
        ax.tick_params(axis="y", length=0)
        ax.spines["left"].set_visible(False)
    fig.subplots_adjust(wspace=0.08)
    save(fig, "fig6_ablation")


# --------------------------------------------------------------------------------------------------------------------
# Figure 7: cost


def figure_cost():
    rows = read("cost.csv")
    fig, (a, b) = plt.subplots(1, 2, figsize=(DOUBLE, 2.2))
    for matcher, color, shape, label in (("baseline", BASE, "s", "baseline (NCM)"), ("ours", OURS, "o", "ours")):
        pts = sorted((int(r["images"]), float(r["fit_ms"]), float(r["match_ms"])) for r in rows if r["matcher"] == matcher)
        n = [p[0] for p in pts]
        a.plot(n, [p[1] for p in pts], color=color, zorder=2, label=label)
        b.plot(n, [p[2] for p in pts], color=color, zorder=2, label=label)
        for x, fit, match in pts:
            marker(a, x, fit, color, shape, 5, zorder=3)
            marker(b, x, match, color, shape, 5, zorder=3)
        a.text(n[-1] + 120, pts[-1][1], f"{pts[-1][1]:,.0f} ms", va="center", fontsize=6.6, color=INK2)
        b.text(n[-1] + 120, pts[-1][2], f"{pts[-1][2]:.2f} ms", va="bottom" if matcher == "baseline" else "center",
               fontsize=6.6, color=INK2)
    a.set_yscale("log")
    a.yaxis.set_major_formatter(mpl.ticker.FuncFormatter(lambda v, _: f"{v:g}"))
    a.set_ylabel("refit time (ms, log scale)")
    b.set_ylabel("time per query (ms)")
    for ax, title in ((a, "(a) Refit"), (b, "(b) Match one image")):
        ax.set_xlabel("images in dataset")
        ax.set_xlim(0, 6900)
        ax.xaxis.set_major_formatter(mpl.ticker.FuncFormatter(lambda v, _: f"{v / 1000:g}K" if v else "0"))
        ax.set_title(title, pad=3)
    b.set_ylim(0, None)
    a.legend(loc="upper left")
    fig.subplots_adjust(wspace=0.3)
    save(fig, "fig7_cost")


if __name__ == "__main__":
    figure_pipeline()
    figure_schematic()
    figure_similarity()
    figure_closed_set()
    figure_open_set()
    figure_ablation()
    figure_cost()
    print("figures written to", OUT)
