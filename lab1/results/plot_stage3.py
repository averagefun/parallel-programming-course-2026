import csv
import matplotlib
from pathlib import Path

matplotlib.use("Agg")
import matplotlib.pyplot as plt

directory = Path(__file__).resolve().parent
with (directory / "stage3.csv").open() as source:
    results = list(csv.DictReader(source))

threads = [int(row["threads"]) for row in results]
throughput = [float(row["million_ops_per_second"]) for row in results]
plt.plot(threads, throughput, marker="o")
plt.xticks(threads)
plt.title("Этап 3: ThreadLocal")
plt.xlabel("Число потоков")
plt.ylabel("record(), млн/с")
plt.grid(axis="y")
plt.tight_layout()
plt.savefig(directory / "stage3.svg")
