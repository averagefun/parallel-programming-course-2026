import csv
import matplotlib.pyplot as plt
from pathlib import Path

directory = Path(__file__).resolve().parent
with (directory / "stage1.csv").open() as source:
    results = list(csv.DictReader(source))

for collector, label in (("synchronized", "Общий лок"), ("empty", "Пустой лок")):
    points = [row for row in results if row["collector"] == collector]
    plt.plot(
        [int(row["threads"]) for row in points],
        [float(row["million_ops_per_second"]) for row in points],
        marker="o",
        label=label,
    )

plt.xticks(sorted({int(row["threads"]) for row in results}))
plt.title("Этап 1: mutex")
plt.xlabel("Число потоков")
plt.ylabel("record(), млн/с")
plt.grid(axis="y")
plt.legend()
plt.tight_layout()
plt.savefig(directory / "stage1.svg")
