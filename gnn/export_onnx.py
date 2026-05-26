"""
export_onnx.py  —  将训练好的 PyTorch 模型导出为 ONNX 格式，供 Java GnnInferenceService 加载

用法:
  python export_onnx.py --model-dir ./models --out-dir ../src/main/resources/gnn
"""

import argparse
import json
import os
import sys

import torch
import torch.onnx

# 导入模型定义（与 train.py 共享）
sys.path.insert(0, os.path.dirname(__file__))
from train import NodeDamageGNN, LinkResistanceGNN


def export_node_damage(model_dir: str, out_dir: str, hidden: int = 64):
    model = NodeDamageGNN(hidden=hidden)
    pt_path = os.path.join(model_dir, 'node_damage_best.pt')
    if not os.path.exists(pt_path):
        pt_path = os.path.join(model_dir, 'node_damage.pt')
    model.load_state_dict(torch.load(pt_path, map_location='cpu'))
    model.eval()

    # 虚拟输入：N=5 节点
    N = 5
    dummy_x = torch.zeros(N, 9)
    dummy_edge_index = torch.tensor([[0, 1, 2, 3, 4, 1, 2, 3, 4, 0],
                                     [1, 2, 3, 4, 0, 0, 1, 2, 3, 4]], dtype=torch.long)
    dummy_disaster = torch.zeros(1, 4)

    out_path = os.path.join(out_dir, 'node_damage.onnx')
    torch.onnx.export(
        model,
        (dummy_x, dummy_edge_index, dummy_disaster),
        out_path,
        input_names=['x', 'edge_index', 'disaster'],
        output_names=['node_damage'],
        dynamic_axes={
            'x': {0: 'num_nodes'},
            'edge_index': {1: 'num_edges'},
            'node_damage': {0: 'num_nodes'},
        },
        opset_version=17,
    )
    print(f'  node_damage.onnx 导出至 {out_path}')


def export_link_resistance(model_dir: str, out_dir: str, hidden: int = 64):
    model = LinkResistanceGNN(hidden=hidden)
    pt_path = os.path.join(model_dir, 'link_resistance_best.pt')
    if not os.path.exists(pt_path):
        pt_path = os.path.join(model_dir, 'link_resistance.pt')
    model.load_state_dict(torch.load(pt_path, map_location='cpu'))
    model.eval()

    N, E = 5, 5
    dummy_x = torch.zeros(N, 9)
    dummy_edge_index = torch.tensor([[0, 1, 2, 3, 4, 1, 2, 3, 4, 0],
                                     [1, 2, 3, 4, 0, 0, 1, 2, 3, 4]], dtype=torch.long)
    dummy_edge_attr = torch.zeros(E * 2, 5)
    dummy_disaster = torch.zeros(1, 4)
    dummy_n_edges = E

    out_path = os.path.join(out_dir, 'link_resistance.onnx')
    # ONNX 不支持 int scalar 参数，将 n_edges 作为 tensor 传入
    torch.onnx.export(
        model,
        (dummy_x, dummy_edge_index, dummy_edge_attr, dummy_disaster, dummy_n_edges),
        out_path,
        input_names=['x', 'edge_index', 'edge_attr', 'disaster', 'n_edges'],
        output_names=['link_resistance'],
        dynamic_axes={
            'x': {0: 'num_nodes'},
            'edge_index': {1: 'num_edges'},
            'edge_attr': {0: 'num_edges'},
            'link_resistance': {0: 'num_edges'},
        },
        opset_version=17,
    )
    print(f'  link_resistance.onnx 导出至 {out_path}')


def main():
    parser = argparse.ArgumentParser(description='导出 GNN 模型为 ONNX')
    parser.add_argument('--model-dir', type=str, default='./models')
    parser.add_argument('--out-dir', type=str, default='../src/main/resources/gnn')
    parser.add_argument('--hidden', type=int, default=64)
    args = parser.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)

    print('导出 NodeDamageGNN…')
    export_node_damage(args.model_dir, args.out_dir, args.hidden)

    print('导出 LinkResistanceGNN…')
    export_link_resistance(args.model_dir, args.out_dir, args.hidden)

    print('ONNX 模型导出完成。')


if __name__ == '__main__':
    main()
