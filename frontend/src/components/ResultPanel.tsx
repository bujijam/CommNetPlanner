import { Table, Tag, Progress, Alert, Typography, Empty, Descriptions } from 'antd';
import { useStore } from '../store/useStore';
import type { AnalysisResult } from '../types';

const { Text } = Typography;

function ShortestPathView({ data }: { data: Extract<AnalysisResult, { type: 'shortest-path' }>['data'] }) {
  const { graph } = useStore();
  const nodeMap = new Map(graph.nodes.map(n => [n.id, n.name]));
  const cols = [
    { title: '目标节点', dataIndex: 'targetId', key: 'targetId', render: (id: number) => `${id} (${nodeMap.get(id) ?? '?'})` },
    { title: '距离 (km)', dataIndex: 'distance', key: 'distance', render: (d: number, r: { reachable: boolean }) => r.reachable ? d.toFixed(2) : <Tag color="red">不可达</Tag> },
    {
      title: '路径', dataIndex: 'path', key: 'path',
      render: (path: number[]) => path.length > 0 ? path.map(id => nodeMap.get(id) ?? id).join(' → ') : '-',
    },
  ];
  return (
    <>
      <Text strong>源节点: {data.sourceId} ({nodeMap.get(data.sourceId)})</Text>
      <Table size="small" columns={cols} dataSource={data.entries} rowKey="targetId" pagination={{ pageSize: 8 }} style={{ marginTop: 8 }} />
    </>
  );
}

function ConnectivityView({ data }: { data: Extract<AnalysisResult, { type: 'connectivity' }>['data'] }) {
  return (
    <div>
      <Descriptions size="small" bordered column={1} style={{ marginBottom: 8 }}>
        <Descriptions.Item label="连通状态">{data.connected ? <Tag color="green">已连通</Tag> : <Tag color="red">不连通</Tag>}</Descriptions.Item>
        <Descriptions.Item label="分量数">{data.componentCount}</Descriptions.Item>
        {data.totalSuggestedCost > 0 && <Descriptions.Item label="补边总长度">{data.totalSuggestedCost.toFixed(2)} km</Descriptions.Item>}
      </Descriptions>
      {data.suggestedEdges.length > 0 && (
        <>
          <Text strong>建议补边：</Text>
          <Table size="small" style={{ marginTop: 4 }}
            dataSource={data.suggestedEdges} rowKey={r => `${r.fromId}-${r.toId}`}
            pagination={false}
            columns={[
              { title: '起点', dataIndex: 'fromId', key: 'from' },
              { title: '终点', dataIndex: 'toId', key: 'to' },
              { title: '距离 (km)', dataIndex: 'baseCost', key: 'cost', render: (v: number) => v.toFixed(2) },
            ]}
          />
        </>
      )}
    </div>
  );
}

function SteinerView({ data }: { data: Extract<AnalysisResult, { type: 'steiner' }>['data'] }) {
  const saved = data.baselineMstLength - data.improvedLength;
  const pct = data.baselineMstLength > 0 ? (saved / data.baselineMstLength * 100) : 0;
  return (
    <div>
      <Descriptions size="small" bordered column={1} style={{ marginBottom: 8 }}>
        <Descriptions.Item label="终端节点数">{data.terminalCount}</Descriptions.Item>
        <Descriptions.Item label="基线 MST 长度">{data.baselineMstLength.toFixed(2)} km</Descriptions.Item>
        <Descriptions.Item label="Steiner 优化长度">{data.improvedLength.toFixed(2)} km</Descriptions.Item>
        <Descriptions.Item label="改进量">{saved.toFixed(2)} km ({pct.toFixed(1)}%)</Descriptions.Item>
        <Descriptions.Item label="使用辅助点">{data.usedAuxiliaryPoint ? <Tag color="blue">是</Tag> : <Tag>否</Tag>}</Descriptions.Item>
      </Descriptions>
    </div>
  );
}

function DisasterSteinerView({ data }: { data: Extract<AnalysisResult, { type: 'disaster-steiner' }>['data'] }) {
  return (
    <div>
      <Alert message="GNN 预测已更新节点受损概率和链路阻力" type="info" showIcon style={{ marginBottom: 8 }} />
      <SteinerView data={data.steinerResult} />
    </div>
  );
}

function ResilienceView({ data }: { data: Extract<AnalysisResult, { type: 'resilience' }>['data'] }) {
  const color = data.score >= 70 ? '#52c41a' : data.score >= 40 ? '#faad14' : '#f5222d';
  return (
    <div>
      <div style={{ textAlign: 'center', marginBottom: 16 }}>
        <Progress type="circle" percent={Math.round(data.score)} strokeColor={color} format={p => `${p}`} />
        <div style={{ marginTop: 8, fontWeight: 600, color }}>
          {data.score >= 70 ? '网络健康' : data.score >= 40 ? '中等风险' : '高风险'}
        </div>
      </div>
      <Descriptions size="small" bordered column={1}>
        <Descriptions.Item label="关键节点连通">
          {data.connectedKeyNodes} / {data.totalKeyNodes}
        </Descriptions.Item>
        <Descriptions.Item label="链路平均可靠性">
          <Progress percent={Math.round(data.avgLinkReliability * 100)} size="small" />
        </Descriptions.Item>
      </Descriptions>
    </div>
  );
}

export default function ResultPanel() {
  const { analysisResults } = useStore();
  const analysisResult = Object.values(analysisResults)[0];

  if (!analysisResult) {
    return <div className="tab-content"><Empty description="暂无结果，请先运行分析算法" /></div>;
  }

  return (
    <div className="tab-content">
      {analysisResult.type === 'shortest-path' && <ShortestPathView data={analysisResult.data} />}
      {analysisResult.type === 'connectivity' && <ConnectivityView data={analysisResult.data} />}
      {analysisResult.type === 'steiner' && <SteinerView data={analysisResult.data} />}
      {analysisResult.type === 'disaster-steiner' && <DisasterSteinerView data={analysisResult.data} />}
      {analysisResult.type === 'resilience' && <ResilienceView data={analysisResult.data} />}
    </div>
  );
}
