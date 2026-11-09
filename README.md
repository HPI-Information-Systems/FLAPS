# FLAPS — Fast Lattice Accurate Pattern Search

FLAPS (Fast Lattice Accurate Pattern Search) is a Java implementation for efficiently identifying **Maximal Uncovered Patterns (MUPs)** in categorical datasets.
MUPs capture regions of the data space that are underrepresented or entirely missing, making them essential for assessing **data coverage** and **diversity** in structured datasets.

---

## Overview

FLAPS follows a **two-stage approach**:
1. **Seeder** — identifies attribute–value combinations that are sufficiently covered and spreads this information through the lattice to prune the search space early.
2. **Validator** — traverses the remaining unexplored regions to efficiently identify **uncovered** and **maximal** patterns.

Both stages execute sequentially.

---

## Parameters

| Parameter | Description | Example | Default |
|------------|-------------|----------|----------|
| `--csv` | Path to the input dataset (CSV format). | `--csv data/airbnb.csv` | **required** |
| `--uninteresting-attributes` | Comma-separated list of attributes to exclude from analysis (e.g., IDs). | `--uninteresting-attributes id,flight_number` | *(none)* |
| `--interesting-attributes` | Comma-separated list of attributes to explicitly include. If omitted, all remaining attributes are used. | `--interesting-attributes airline,source_city,class` | *(none)* |
| `--mincov` | Minimum coverage threshold (`mincov`) — defines when a pattern is considered covered. | `--mincov 8` | `1` |
| `--chunk-size` | Number of attributes per chunk used in the **Seeder** stage. | `--chunk-size 5` | `1` |
| `--chunks-per-row` | Number of chunks sampled per tuple during seeding. | `--chunks-per-row 10` | `1` |
| `--output-mups [path]` | Write reverse-mapped MUPs and exact coverage. Without a path, writes `mups_DATASET_NAME_mincov_x.csv` under `results/`. | `--output-mups` | *(disabled)* |

---

## Usage

Requires Java 21 or newer and Maven. Run from the project directory.

```bash
mvn clean package

java -jar target/diversity-assessment-1.0-SNAPSHOT.jar \
  --csv /path/to/dataset.csv \
  --mincov 1 \
  --chunk-size 14 \
  --chunks-per-row 15
```

Runtime summaries are written under `results/`.
