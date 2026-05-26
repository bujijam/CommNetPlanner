import { useState } from 'react';
import { Tabs, Select, Button, Alert, message, Spin, Tag, Progress, Descriptions, Table, Typography } from 'antd';
import { useStore } from '../store/useStore';
import type { AnalysisResult } from '../types';

const { Text } = Typography;

function InlineResult({ results, type }: { results: Record<string, AnalysisResult>; type: string }) {
  const result = results[type] as AnalysisResult | undefined;
  if (!result) return null;

  if (result.type === 'shortest-path') {
    const { graph } = useStore.getState();
    const nodeMap = new Map(graph.nodes.map(n => [n.id, n.name]));
    const data = result.data;
    const cols = [
      { title: '目标节点', dataIndex: 'targetId', key: 'targetId', render: (id: number) => `${id} (${nodeMap.get(id) ?? '?'})` },
      { title: '距离 (km)', dataIndex: 'distance', key: 'distance', render: (d: number, r: { reachable: boolean }) => r.reachable ? d.toFixed(2) : <Tag color="red">不可达</Tag> },
      { title: '路径', dataIndex: 'path', key: 'path', render: (path: number[]) => path.length > 0 ? path.map(id => nodeMap.get(id) ?? id).join(' → ') : '-' },
    ];
    return (
      <div style={{ marginTop: 12 }}>
        <Text strong>源节点: {data.sourceId} ({nodeMap.get(data.sourceId)})</Text>
        <div className="algo-table-wrap">
          <Table size="small" columns={cols} dataSource={data.entries} rowKey="targetId" pagination={{ pageSize: 6 }} scroll={{ y: 240 }} style={{ marginTop: 8 }} />
        </div>
      </div>
    );
  }

  if (result.type === 'connectivity') {
    const data = result.data;
    return (
      <div style={{ marginTop: 12 }}>
        <Descriptions size="small" bordered column={1} style={{ marginBottom: 8 }}>
          <Descriptions.Item label="连通状态">{data.connected ? <Tag color="green">已连通</Tag> : <Tag color="red">不连通</Tag>}</Descriptions.Item>
          <Descriptions.Item label="分量数">{data.componentCount}</Descriptions.Item>
          {data.totalSuggestedCost > 0 && <Descriptions.Item label="补边总长度">{data.totalSuggestedCost.toFixed(2)} km</Descriptions.Item>}
        </Descriptions>
        {data.suggestedEdges.length > 0 && (
          <>
            <Text strong>建议补边（蓝色虚线标注于地图）：</Text>
            <div className="algo-table-wrap" style={{ marginTop: 4 }}>
              <Table size="small"
                dataSource={data.suggestedEdges} rowKey={r => `${r.fromId}-${r.toId}`}
                pagination={false}
                columns={[
                  { title: '起点', dataIndex: 'fromId', key: 'from' },
                  { title: '终点', dataIndex: 'toId', key: 'to' },
                  { title: '距离 (km)', dataIndex: 'baseCost', key: 'cost', render: (v: number) => v.toFixed(2) },
                ]}
              />
            </div>
          </>
        )}
      </div>
    );
  }

  if (result.type === 'steiner') {
    const data = result.data;
    const saved = data.baselineMstLength - data.improvedLength;
    const pct = data.baselineMstLength > 0 ? (saved / data.baselineMstLength * 100) : 0;
    return (
      <div style={{ marginTop: 12 }}>
        <Alert message="Steiner 近似算法（紫色路径）相比传统最短连接的优化效果" type="info" showIcon style={{ marginBottom: 8 }} />
        <div style={{ overflowX: 'auto' }}>
          <Descriptions size="small" bordered column={1}>
            <Descriptions.Item label="关键节点数量">{data.terminalCount} 个</Descriptions.Item>
            <Descriptions.Item label="传统最短连接总长">{data.baselineMstLength.toFixed(2)} km</Descriptions.Item>
            <Descriptions.Item label="优化后总长">{data.improvedLength.toFixed(2)} km</Descriptions.Item>
            <Descriptions.Item label="节省长度">{saved.toFixed(2)} km ({pct.toFixed(1)}%)</Descriptions.Item>
            <Descriptions.Item label="是否引入辅助点">{data.usedAuxiliaryPoint ? <Tag color="blue">是</Tag> : <Tag>否</Tag>}</Descriptions.Item>
          </Descriptions>
        </div>
      </div>
    );
  }

  if (result.type === 'disaster-steiner') {
    const data = result.data.steinerResult;
    const saved = data.baselineMstLength - data.improvedLength;
    const pct = data.baselineMstLength > 0 ? (saved / data.baselineMstLength * 100) : 0;
    return (
      <div style={{ marginTop: 12 }}>
        <Alert message="结合 GNN 预测的受损数据，算法已自动规避高危节点/链路。紫色为优化路径，灰色虚线为优化前路径。" type="info" showIcon style={{ marginBottom: 8 }} />
        <div style={{ overflowX: 'auto' }}>
          <Descriptions size="small" bordered column={1}>
            <Descriptions.Item label="关键节点数量">{data.terminalCount} 个</Descriptions.Item>
            <Descriptions.Item label="灾前最短连接总长">{data.baselineMstLength.toFixed(2)} km</Descriptions.Item>
            <Descriptions.Item label="灾后优化总长">{data.improvedLength.toFixed(2)} km</Descriptions.Item>
            <Descriptions.Item label="节省长度">{saved.toFixed(2)} km ({pct.toFixed(1)}%)</Descriptions.Item>
            <Descriptions.Item label="是否引入辅助点">{data.usedAuxiliaryPoint ? <Tag color="blue">是</Tag> : <Tag>否</Tag>}</Descriptions.Item>
          </Descriptions>
        </div>
      </div>
    );
  }

  if (result.type === 'resilience') {
    const data = result.data;
    const color = data.score >= 70 ? '#52c41a' : data.score >= 40 ? '#faad14' : '#f5222d';
    return (
      <div style={{ marginTop: 12 }}>
        <div style={{ textAlign: 'center', marginBottom: 12 }}>
          <Progress type="circle" percent={Math.round(data.score)} strokeColor={color} format={p => `${p}`} size={100} />
          <div style={{ marginTop: 6, fontWeight: 600, color }}>
            {data.score >= 70 ? '网络健康' : data.score >= 40 ? '中等风险' : '高风险'}
          </div>
        </div>
        <div style={{ overflowX: 'auto' }}>
          <Descriptions size="small" bordered column={1}>
            <Descriptions.Item label="关键节点连通情况">{data.connectedKeyNodes} / {data.totalKeyNodes} 个关键节点保持连通</Descriptions.Item>
            <Descriptions.Item label="链路平均可靠性">
              <Progress percent={Math.round(data.avgLinkReliability * 100)} size="small" />
            </Descriptions.Item>
          </Descriptions>
        </div>
      </div>
    );
  }

  return null;
}

export default function AlgorithmPanel() {
  const { graph, analysisResults, loading, activeScenario,
    runShortestPath, runConnectivity, runSteiner, runDisasterSteiner, runResilience } = useStore();
  const nodes = graph.nodes;
  const [sourceId, setSourceId] = useState<number | undefined>();
  const [terminalIds, setTerminalIds] = useState<number[]>([]);

  const items = [
    {
      key: 'gnn',
      label: 'GNN 预测',
      children: (
        <div style={{ padding: 12 }}>
          <p style={{ marginBottom: 12, color: '#666', fontSize: 13 }}>
            图神经网络（GNN）根据灾情场景预测各节点受损概率与各链路通信阻力，为后续救灾规划提供数据支撑。
          </p>
          {!activeScenario ? (
            <Alert message="请先在「灾情模拟」面板中应用一个灾情场景，GNN 才能生成预测数据。" type="warning" showIcon />
          ) : (() => {
            const damagedNodes = graph.nodes.filter(n => n.predictedDamageScore > 0).sort((a, b) => b.predictedDamageScore - a.predictedDamageScore);
            const highRiskNodes = damagedNodes.filter(n => n.predictedDamageScore > 0.7);
            const riskyLinks = graph.links.filter(l => l.predictedResistance > 0).sort((a, b) => b.predictedResistance - a.predictedResistance);
            const severeLinks = riskyLinks.filter(l => l.predictedResistance > 0.7);
            const nodeNameMap = new Map(graph.nodes.map(n => [n.id, n.name]));
            return (
              <div>
                <Descriptions size="small" bordered column={2} style={{ marginBottom: 12 }}>
                  <Descriptions.Item label="受损节点">{damagedNodes.length} / {graph.nodes.length}</Descriptions.Item>
                  <Descriptions.Item label="高危节点">{highRiskNodes.length > 0 ? <Tag color="red">{highRiskNodes.length} 个</Tag> : <Tag color="green">0</Tag>}</Descriptions.Item>
                  <Descriptions.Item label="受影响链路">{riskyLinks.length} / {graph.links.length}</Descriptions.Item>
                  <Descriptions.Item label="严重受损链路">{severeLinks.length > 0 ? <Tag color="red">{severeLinks.length} 条</Tag> : <Tag color="green">0</Tag>}</Descriptions.Item>
                </Descriptions>
                <Text strong style={{ display: 'block', marginBottom: 4 }}>节点受损概率排名</Text>
                <div className="algo-table-wrap">
                  <Table size="small" dataSource={damagedNodes} rowKey="id" pagination={{ pageSize: 6 }} scroll={{ y: 180 }}
                    columns={[
                      { title: '节点', dataIndex: 'id', key: 'id', width: 140, render: (id: number) => `${id} (${nodeNameMap.get(id)})` },
                      { title: '类型', dataIndex: 'nodeType', key: 'type', width: 80, render: (t: string) => ({ COMMAND: '指挥', HOSPITAL: '医院', SHELTER: '避难', RELAY: '中继', AFFECTED: '受灾' }[t] || t) },
                      { title: '受损概率', dataIndex: 'predictedDamageScore', key: 'score', render: (v: number) => (
                        <Progress percent={Math.round(v * 100)} size="small" strokeColor={v > 0.7 ? '#f5222d' : v > 0.3 ? '#faad14' : '#52c41a'} />
                      )},
                    ]}
                  />
                </div>
                <Text strong style={{ display: 'block', margin: '12px 0 4px' }}>链路通信阻力排名</Text>
                <div className="algo-table-wrap">
                  <Table size="small" dataSource={riskyLinks} rowKey={r => `${r.fromId}-${r.toId}`} pagination={{ pageSize: 6 }} scroll={{ y: 180 }}
                    columns={[
                      { title: '链路', key: 'link', render: (_: unknown, r: { fromId: number; toId: number }) => `${nodeNameMap.get(r.fromId)} → ${nodeNameMap.get(r.toId)}` },
                      { title: '阻力', dataIndex: 'predictedResistance', key: 'res', render: (v: number) => (
                        <Progress percent={Math.round(v * 100)} size="small" strokeColor={v > 0.7 ? '#f5222d' : v > 0.3 ? '#faad14' : '#52c41a'} />
                      )},
                    ]}
                  />
                </div>
              </div>
            );
          })()}
        </div>
      ),
    },
    {
      key: 'sp',
      label: '应急路径',
      children: (
        <div style={{ padding: 12 }}>
          <p style={{ marginBottom: 12, color: '#666', fontSize: 13 }}>
            基于 Dijkstra 算法，从指定源节点出发，计算到所有其他节点的最短路径距离。
          </p>
          <Select
            style={{ width: '100%', marginBottom: 8 }}
            placeholder="选择源节点"
            value={sourceId}
            onChange={setSourceId}
            options={nodes.map(n => ({ value: n.id, label: `${n.id} - ${n.name}` }))}
          />
          <Button
            type="primary" block loading={loading}
            disabled={sourceId === undefined}
            onClick={() => sourceId !== undefined && runShortestPath(sourceId).catch(() => message.error('分析失败'))}
          >
            运行最短路径分析
          </Button>
          <InlineResult results={analysisResults} type="shortest-path" />
        </div>
      ),
    },
    {
      key: 'conn',
      label: '连通诊断',
      children: (
        <div style={{ padding: 12 }}>
          <p style={{ marginBottom: 12, color: '#666', fontSize: 13 }}>
            检测网络连通性，识别孤立分量，并建议最少补边方案使网络完全连通。
          </p>
          <Button type="primary" block loading={loading} onClick={() => runConnectivity().catch(() => message.error('分析失败'))}>
            运行连通性诊断
          </Button>
          <InlineResult results={analysisResults} type="connectivity" />
        </div>
      ),
    },
    {
      key: 'steiner',
      label: '组网优化',
      children: (
        <div style={{ padding: 12 }}>
          <p style={{ marginBottom: 12, color: '#666', fontSize: 13 }}>
            使用 Steiner 树近似算法，为关键节点（医院、指挥中心等）寻找总线路最短的组网方案，结果以紫色路径标注于地图。
          </p>
          <Button type="primary" block loading={loading} onClick={() => runSteiner().catch(() => message.error('分析失败'))}>
            运行组网优化
          </Button>
          <InlineResult results={analysisResults} type="steiner" />
        </div>
      ),
    },
    {
      key: 'ds',
      label: '灾后组网',
      children: (
        <div style={{ padding: 12 }}>
          <p style={{ marginBottom: 12, color: '#666', fontSize: 13 }}>
            结合 GNN 灾情预测，在规避受损区域的前提下为指定终端节点规划最优组网方案。需先在"灾情配置"中应用灾情。
          </p>
          <Select
            mode="multiple" style={{ width: '100%', marginBottom: 8 }}
            placeholder="选择终端节点"
            value={terminalIds}
            onChange={setTerminalIds}
            options={nodes.map(n => ({ value: n.id, label: `${n.id} - ${n.name}` }))}
          />
          <Button
            type="primary" block loading={loading}
            disabled={terminalIds.length < 2 || !activeScenario}
            onClick={() => runDisasterSteiner(terminalIds).catch(() => message.error('分析失败'))}
          >
            运行灾后组网分析
          </Button>
          <InlineResult results={analysisResults} type="disaster-steiner" />
        </div>
      ),
    },
    {
      key: 'res',
      label: '韧性评估',
      children: (
        <div style={{ padding: 12 }}>
          <p style={{ marginBottom: 12, color: '#666', fontSize: 13 }}>
            综合评估通信网络抵御灾害的能力，从关键设施连通性、链路可靠性和结构健壮性三个维度量化网络韧性。
          </p>
          <Button type="primary" block loading={loading} onClick={() => runResilience().catch(() => message.error('分析失败'))}>
            计算韧性评分
          </Button>
          <InlineResult results={analysisResults} type="resilience" />
        </div>
      ),
    },
  ];

  return (
    <Spin spinning={loading}>
      <Tabs items={items} size="small" />
    </Spin>
  );
}
