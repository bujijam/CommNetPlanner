"""
generate_dataset.py  —  随机生成灾情通信网络训练数据集

节点特征 (9维):
  [nodeType_onehot(5), population_norm, lat_norm, lon_norm, dist_to_epicenter_norm]

链路特征 (5维):
  [linkType_onehot(3), bandwidth_norm, baseCost_norm]

标签:
  node_damage:      sigmoid((magnitude - dist/radius*10) + type_bias)
  link_resistance:  1 - (1-damageA)*(1-damageB) * link_type_resilience

用法:
  python generate_dataset.py --num-graphs 2000 --out-dir ./data
"""

import argparse
import json
import math
import os
import random


# ---------- 常量 ----------
NODE_TYPES = ['COMMAND', 'HOSPITAL', 'SHELTER', 'RELAY', 'AFFECTED']
NODE_TYPE_BIAS = {'COMMAND': -1.5, 'HOSPITAL': -1.0, 'SHELTER': -0.5, 'RELAY': 0.0, 'AFFECTED': 0.5}
LINK_TYPES = ['FIBER', 'MICROWAVE', 'SATELLITE']
LINK_TYPE_RESILIENCE = {'FIBER': 0.9, 'MICROWAVE': 0.7, 'SATELLITE': 0.5}
DISASTER_TYPES = ['EARTHQUAKE', 'FLOOD', 'WILDFIRE', 'TYPHOON']

# 中国大陆地理范围
LAT_RANGE = (22.0, 50.0)
LON_RANGE = (80.0, 135.0)


def haversine(lat1, lon1, lat2, lon2) -> float:
    R = 6371.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlam = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlam / 2) ** 2
    return R * 2 * math.asin(math.sqrt(a))


def sigmoid(x: float) -> float:
    if x >= 0:
        return 1.0 / (1.0 + math.exp(-x))
    ex = math.exp(x)
    return ex / (1.0 + ex)


def generate_graph(n_nodes: int = 20, seed: int | None = None) -> dict:
    """生成一个随机网络图（节点 + 链路 + 灾情场景）并返回特征与标签。"""
    rng = random.Random(seed)

    # ---- 节点 ----
    nodes = []
    for i in range(n_nodes):
        lat = rng.uniform(*LAT_RANGE)
        lon = rng.uniform(*LON_RANGE)
        node_type = rng.choice(NODE_TYPES)
        population = rng.randint(0, 500_000)
        nodes.append({'id': i, 'lat': lat, 'lon': lon, 'nodeType': node_type, 'population': population})

    # ---- 链路：k近邻稀疏图 ----
    k = min(4, n_nodes - 1)
    edges = set()
    for i in range(n_nodes):
        dists = []
        for j in range(n_nodes):
            if i == j:
                continue
            d = haversine(nodes[i]['lat'], nodes[i]['lon'], nodes[j]['lat'], nodes[j]['lon'])
            dists.append((d, j))
        dists.sort()
        for _, j in dists[:k]:
            a, b = min(i, j), max(i, j)
            edges.add((a, b))

    links = []
    for (a, b) in edges:
        link_type = rng.choice(LINK_TYPES)
        bandwidth = rng.choice([10, 50, 100, 200, 500, 1000])
        cost = haversine(nodes[a]['lat'], nodes[a]['lon'], nodes[b]['lat'], nodes[b]['lon'])
        links.append({'fromId': a, 'toId': b, 'linkType': link_type, 'bandwidth': bandwidth, 'baseCost': cost})

    # ---- 灾情场景 ----
    epi_lat = rng.uniform(*LAT_RANGE)
    epi_lon = rng.uniform(*LON_RANGE)
    magnitude = rng.uniform(4.0, 9.5)
    radius_km = rng.uniform(50.0, 400.0)

    # ---- 标签 ----
    node_damage = []
    for n in nodes:
        dist_km = haversine(n['lat'], n['lon'], epi_lat, epi_lon)
        raw = magnitude - dist_km / radius_km * 10 + NODE_TYPE_BIAS[n['nodeType']]
        damage = sigmoid(raw)
        node_damage.append(float(damage))

    link_resistance = []
    for lk in links:
        da = node_damage[lk['fromId']]
        db = node_damage[lk['toId']]
        resilience = LINK_TYPE_RESILIENCE[lk['linkType']]
        resistance = 1.0 - (1.0 - da) * (1.0 - db) * resilience
        link_resistance.append(float(resistance))

    # ---- 特征矩阵 ----
    # 归一化常量
    lat_min, lat_max = LAT_RANGE
    lon_min, lon_max = LON_RANGE
    max_pop = 500_000.0
    max_dist = haversine(lat_min, lon_min, lat_max, lon_max)
    max_cost = max_dist

    node_feats = []
    for n in nodes:
        onehot = [1.0 if n['nodeType'] == t else 0.0 for t in NODE_TYPES]
        pop_norm = n['population'] / max_pop
        lat_norm = (n['lat'] - lat_min) / (lat_max - lat_min)
        lon_norm = (n['lon'] - lon_min) / (lon_max - lon_min)
        dist_epi = haversine(n['lat'], n['lon'], epi_lat, epi_lon)
        dist_norm = min(dist_epi / max_dist, 1.0)
        node_feats.append(onehot + [pop_norm, lat_norm, lon_norm, dist_norm])

    link_feats = []
    for lk in links:
        onehot = [1.0 if lk['linkType'] == t else 0.0 for t in LINK_TYPES]
        bw_norm = math.log1p(lk['bandwidth']) / math.log1p(1000.0)
        cost_norm = min(lk['baseCost'] / max_cost, 1.0)
        link_feats.append(onehot + [bw_norm, cost_norm])

    # COO 边索引 (双向)
    edge_index_src = [lk['fromId'] for lk in links] + [lk['toId'] for lk in links]
    edge_index_dst = [lk['toId'] for lk in links] + [lk['fromId'] for lk in links]

    # 灾情特征 (4维): 震级归一化, 半径归一化, disasterType_compressed (2bit)
    dtype_idx = DISASTER_TYPES.index(rng.choice(DISASTER_TYPES))
    disaster_feat = [dtype_idx // 2, dtype_idx % 2, magnitude / 10.0, radius_km / 500.0]

    return {
        'node_feats': node_feats,         # (N, 9)
        'link_feats': link_feats,         # (E, 5)
        'edge_index': [edge_index_src, edge_index_dst],  # (2, 2E)
        'disaster_feat': disaster_feat,   # (4,)
        'node_damage': node_damage,       # (N,)
        'link_resistance': link_resistance,  # (E,)
        'n_nodes': n_nodes,
        'n_edges': len(links),
    }


def main():
    parser = argparse.ArgumentParser(description='生成通信网络灾情训练数据集')
    parser.add_argument('--num-graphs', type=int, default=2000, help='生成图数量')
    parser.add_argument('--n-nodes', type=int, default=20, help='每图节点数')
    parser.add_argument('--out-dir', type=str, default='./data', help='输出目录')
    parser.add_argument('--seed', type=int, default=42)
    args = parser.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)
    random.seed(args.seed)

    dataset = []
    for i in range(args.num_graphs):
        g = generate_graph(n_nodes=args.n_nodes, seed=args.seed + i)
        dataset.append(g)
        if (i + 1) % 200 == 0:
            print(f'  已生成 {i + 1}/{args.num_graphs} 图')

    out_path = os.path.join(args.out_dir, 'dataset.json')
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(dataset, f, ensure_ascii=False)
    print(f'数据集已保存至 {out_path}  ({args.num_graphs} 图)')


if __name__ == '__main__':
    main()
