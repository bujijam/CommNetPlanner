# CommNetPlanner GNN Training Pipeline

This directory contains the Python pipeline for training and exporting the GNN models used by `GnnInferenceService`.

## Workflow

```
generate_dataset.py  →  train.py  →  export_onnx.py  →  src/main/resources/gnn/
```

## Setup

```bash
conda env create -f environment.yml
conda activate commnet-gnn
```

## 1. Generate Training Data

```bash
python generate_dataset.py --num-graphs 2000 --n-nodes 20 --out-dir ./data
```

Creates `./data/dataset.json` with 2000 randomly generated communication-network + disaster-scenario samples.

**Features per sample:**
- Node features (N×9): `[nodeType_onehot(5), population_norm, lat_norm, lon_norm, dist_to_epicenter_norm]`
- Link features (E×5): `[linkType_onehot(3), bandwidth_norm, baseCost_norm]`
- Disaster feature (4): `[disasterType_bits(2), magnitude_norm, radius_norm]`

**Labels:**
- `node_damage` (N): sigmoid of physics model `f(magnitude, distance, nodeType)`
- `link_resistance` (E): derived from endpoint damage + link type resilience

## 2. Train Models

```bash
python train.py --data-path ./data/dataset.json --epochs 80 --hidden 64 --out-dir ./models
```

Trains two separate GCN-based regressors:
- `node_damage_best.pt`
- `link_resistance_best.pt`

## 3. Export to ONNX

```bash
python export_onnx.py --model-dir ./models --out-dir ../src/main/resources/gnn
```

Writes `node_damage.onnx` and `link_resistance.onnx` to the Java classpath resources folder.

The Java `GnnInferenceService` automatically loads these files at startup and falls back to the physics formula if they are absent.

## Model Architecture

Both models use the same backbone:

```
Input: Node features (N×9)
  └─ GCNConv(9 → 64) + ReLU
  └─ GCNConv(64 → 64) + ReLU
  └─ Concat with disaster embedding (64-dim)
  └─ MLP(128 → 64 → 1) + Sigmoid
```

For link resistance, an additional link-feature projection is concatenated before the MLP.

## Notes

- All nodes are **randomly generated** (no real disaster data required).
- Geographically constrained to mainland China (lat 22–50°N, lon 80–135°E).
- Physics-based labels ensure physically plausible training signal.
