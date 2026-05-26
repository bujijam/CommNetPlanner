"""
train.py  —  训练 GNN 节点受损预测模型 (node_damage) 和链路阻力预测模型 (link_resistance)

网络结构: 2层 GCN + 全局灾情特征注入 → 节点/链路回归

用法:
  python train.py --data-path ./data/dataset.json --epochs 80 --out-dir ./models
"""

import argparse
import json
import os
import math
import random

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch_geometric.data import Data, Dataset, DataLoader
from torch_geometric.nn import GCNConv, global_mean_pool
from sklearn.model_selection import train_test_split
from tqdm import tqdm


# ---------- 数据集 ----------
class CommNetDataset(Dataset):
    def __init__(self, records: list[dict]):
        super().__init__()
        self.records = records

    def len(self): return len(self.records)

    def get(self, idx: int) -> Data:
        r = self.records[idx]
        x = torch.tensor(r['node_feats'], dtype=torch.float)           # (N, 9)
        edge_index = torch.tensor(r['edge_index'], dtype=torch.long)   # (2, 2E)
        edge_attr = torch.tensor(                                        # (2E, 5)
            r['link_feats'] + r['link_feats'], dtype=torch.float
        )
        y_node = torch.tensor(r['node_damage'], dtype=torch.float)      # (N,)
        y_link = torch.tensor(r['link_resistance'], dtype=torch.float)  # (E,)
        disaster = torch.tensor(r['disaster_feat'], dtype=torch.float)  # (4,)
        n_edges = r['n_edges']
        return Data(x=x, edge_index=edge_index, edge_attr=edge_attr,
                    y_node=y_node, y_link=y_link, disaster=disaster,
                    n_edges=n_edges)


# ---------- 模型 ----------
class NodeDamageGNN(nn.Module):
    """预测每个节点的受损概率 (0-1)。"""
    def __init__(self, node_feat_dim: int = 9, disaster_dim: int = 4, hidden: int = 64):
        super().__init__()
        self.conv1 = GCNConv(node_feat_dim, hidden)
        self.conv2 = GCNConv(hidden, hidden)
        self.disaster_proj = nn.Linear(disaster_dim, hidden)
        self.head = nn.Sequential(
            nn.Linear(hidden * 2, hidden),
            nn.ReLU(),
            nn.Linear(hidden, 1),
            nn.Sigmoid(),
        )

    def forward(self, x, edge_index, disaster):
        # x: (N, 9), disaster: (1, 4)  broadcasted to (N, hidden)
        h = F.relu(self.conv1(x, edge_index))
        h = F.relu(self.conv2(h, edge_index))
        d = F.relu(self.disaster_proj(disaster))            # (1, hidden)
        d = d.expand(h.size(0), -1)                         # (N, hidden)
        out = self.head(torch.cat([h, d], dim=-1))          # (N, 1)
        return out.squeeze(-1)                              # (N,)


class LinkResistanceGNN(nn.Module):
    """预测每条链路的阻力值 (0-1)。"""
    def __init__(self, node_feat_dim: int = 9, link_feat_dim: int = 5, disaster_dim: int = 4, hidden: int = 64):
        super().__init__()
        self.conv1 = GCNConv(node_feat_dim, hidden)
        self.conv2 = GCNConv(hidden, hidden)
        self.link_proj = nn.Linear(link_feat_dim, hidden)
        self.disaster_proj = nn.Linear(disaster_dim, hidden)
        self.head = nn.Sequential(
            nn.Linear(hidden * 3, hidden),
            nn.ReLU(),
            nn.Linear(hidden, 1),
            nn.Sigmoid(),
        )

    def forward(self, x, edge_index, edge_attr, disaster, n_edges):
        h = F.relu(self.conv1(x, edge_index))
        h = F.relu(self.conv2(h, edge_index))
        # 取每条边两端节点特征均值 (只用前 n_edges 条单向边)
        src = edge_index[0, :n_edges]
        dst = edge_index[1, :n_edges]
        h_edge = (h[src] + h[dst]) / 2                     # (E, hidden)
        lf = F.relu(self.link_proj(edge_attr[:n_edges]))    # (E, hidden)
        d = F.relu(self.disaster_proj(disaster)).expand(n_edges, -1)
        out = self.head(torch.cat([h_edge, lf, d], dim=-1)) # (E, 1)
        return out.squeeze(-1)                              # (E,)


# ---------- 训练 ----------
def train_model(model, loader, optimizer, loss_fn, device, is_link: bool):
    model.train()
    total_loss = 0.0
    for batch in loader:
        batch = batch.to(device)
        optimizer.zero_grad()
        d = batch.disaster.view(-1, 4)[:1]  # single graph per batch
        if is_link:
            pred = model(batch.x, batch.edge_index, batch.edge_attr, d, int(batch.n_edges))
            target = batch.y_link
        else:
            pred = model(batch.x, batch.edge_index, d)
            target = batch.y_node
        loss = loss_fn(pred, target)
        loss.backward()
        optimizer.step()
        total_loss += loss.item()
    return total_loss / len(loader)


@torch.no_grad()
def eval_model(model, loader, loss_fn, device, is_link: bool):
    model.eval()
    total_loss = 0.0
    for batch in loader:
        batch = batch.to(device)
        d = batch.disaster.view(-1, 4)[:1]
        if is_link:
            pred = model(batch.x, batch.edge_index, batch.edge_attr, d, int(batch.n_edges))
            target = batch.y_link
        else:
            pred = model(batch.x, batch.edge_index, d)
            target = batch.y_node
        total_loss += loss_fn(pred, target).item()
    return total_loss / len(loader)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--data-path', type=str, default='./data/dataset.json')
    parser.add_argument('--epochs', type=int, default=80)
    parser.add_argument('--batch-size', type=int, default=32)
    parser.add_argument('--lr', type=float, default=1e-3)
    parser.add_argument('--hidden', type=int, default=64)
    parser.add_argument('--out-dir', type=str, default='./models')
    parser.add_argument('--seed', type=int, default=42)
    args = parser.parse_args()

    torch.manual_seed(args.seed)
    random.seed(args.seed)
    np.random.seed(args.seed)
    os.makedirs(args.out_dir, exist_ok=True)

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f'使用设备: {device}')

    print('加载数据集…')
    with open(args.data_path, 'r', encoding='utf-8') as f:
        records = json.load(f)

    train_recs, val_recs = train_test_split(records, test_size=0.1, random_state=args.seed)
    train_ds = CommNetDataset(train_recs)
    val_ds = CommNetDataset(val_recs)
    train_loader = DataLoader(train_ds, batch_size=1, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=1, shuffle=False)

    loss_fn = nn.MSELoss()

    for model_name, is_link, ModelClass in [
        ('node_damage', False, NodeDamageGNN),
        ('link_resistance', True, LinkResistanceGNN),
    ]:
        print(f'\n=== 训练 {model_name} 模型 ===')
        if is_link:
            model = ModelClass(hidden=args.hidden).to(device)
        else:
            model = ModelClass(hidden=args.hidden).to(device)
        optimizer = torch.optim.Adam(model.parameters(), lr=args.lr)
        scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

        best_val = math.inf
        for epoch in range(1, args.epochs + 1):
            train_loss = train_model(model, train_loader, optimizer, loss_fn, device, is_link)
            val_loss = eval_model(model, val_loader, loss_fn, device, is_link)
            scheduler.step()
            if epoch % 10 == 0 or epoch == 1:
                print(f'  Epoch {epoch:3d}/{args.epochs}  train={train_loss:.4f}  val={val_loss:.4f}')
            if val_loss < best_val:
                best_val = val_loss
                torch.save(model.state_dict(), os.path.join(args.out_dir, f'{model_name}_best.pt'))

        print(f'  最优验证损失: {best_val:.4f}')
        # 保存最终模型
        torch.save(model.state_dict(), os.path.join(args.out_dir, f'{model_name}.pt'))

    print('\n训练完成。模型保存于', args.out_dir)


if __name__ == '__main__':
    main()
