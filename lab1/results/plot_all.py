import csv
import matplotlib
from pathlib import Path

matplotlib.use("Agg")
import matplotlib.pyplot as plt

directory = Path(__file__).resolve().parent
series = (
    ("stage0.csv", None, "Однопоточный baseline"),
    ("stage1.csv", "empty", "Пустой лок"),
    ("stage1.csv", "synchronized", "Общий лок"),
    ("stage2.csv", "striped", "Шардированный лок"),
    ("stage3.csv", "thread_local", "ThreadLocal"),
    ("stage4.csv", "double_buffered", "Двойная буферизация"),
)

for filename, collector, label in series:
    with (directory / filename).open() as source:
        rows = list(csv.DictReader(source))
    points = [row for row in rows if collector is None or row["collector"] == collector]
    plt.plot(
        [int(row["threads"]) for row in points],
        [float(row["million_ops_per_second"]) for row in points],
        marker="o",
        label=label,
    )

plt.xticks([1, 2, 4, 8, 14])
plt.yscale("log")
plt.title("Пропускная способность коллекторов")
plt.xlabel("Число потоков")
plt.ylabel("record(), млн/с (логарифмическая шкала)")
plt.grid(axis="y", which="both")
plt.legend()
plt.tight_layout()
plt.savefig(directory / "all_stages.svg")
