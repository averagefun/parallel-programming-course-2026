import csv
import matplotlib
from pathlib import Path

matplotlib.use("Agg")
import matplotlib.pyplot as plt

directory = Path(__file__).resolve().parent
with (directory / "stage0.csv").open() as source:
    result = next(csv.DictReader(source))

threads = int(result["threads"])
plt.bar([threads], [float(result["million_ops_per_second"])])
plt.xticks([threads])
plt.title("Этап 0")
plt.xlabel("Число потоков")
plt.ylabel("record(), млн/с")
plt.tight_layout()
plt.savefig(directory / "stage0.svg")
