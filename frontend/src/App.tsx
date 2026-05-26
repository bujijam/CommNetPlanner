import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Tabs, Button, Table, Popconfirm, Tag, Space, message, Select } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ImportOutlined, ExportOutlined, ClearOutlined } from '@ant-design/icons';
import MapView from './components/MapView';
import type { MapViewHandle } from './components/MapView';
import DisasterPanel from './components/DisasterPanel';
import AlgorithmPanel from './components/AlgorithmPanel';
import NodeEditModal from './components/NodeEditModal';
import { useStore } from './store/useStore';
import type { Node } from './types';
import * as api from './api/client';

const NODE_TYPE_COLORS: Record<string, string> = {
  COMMAND: 'blue', HOSPITAL: 'red', SHELTER: 'green', RELAY: 'orange', AFFECTED: 'default',
};
const NODE_TYPE_LABELS: Record<string, string> = {
  COMMAND: '指挥中心', HOSPITAL: '医院', SHELTER: '避难所', RELAY: '中继站', AFFECTED: '受灾点',
};
const LINK_TYPE_LABELS: Record<string, string> = {
  FIBER: '光纤', MICROWAVE: '微波', SATELLITE: '卫星',
};

export default function App() {
  const { graph, loadGraph, loadScenarios, selectNode, setAddNodeMode, addNodeMode, selectedNodeId } = useStore();
  const [modalOpen, setModalOpen] = useState(false);
  const [editNode, setEditNode] = useState<Node | null>(null);
  const [clickLat, setClickLat] = useState<number | undefined>();
  const [clickLon, setClickLon] = useState<number | undefined>();
  const [fromId, setFromId] = useState<number | undefined>();
  const [toId, setToId] = useState<number | undefined>();
  const [linkType, setLinkType] = useState<string>('FIBER');
  const [bandwidth] = useState<number>(100);
  const fileRef = useRef<HTMLInputElement>(null);
  const mapRef = useRef<MapViewHandle>(null);
  const nodeTableRef = useRef<HTMLDivElement>(null);

  // Resizable panel state
  const [sideWidth, setSideWidth] = useState(380);
  const dragging = useRef(false);
  const startX = useRef(0);
  const startWidth = useRef(380);

  const onDividerMouseDown = useCallback((e: React.MouseEvent) => {
    dragging.current = true;
    startX.current = e.clientX;
    startWidth.current = sideWidth;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }, [sideWidth]);

  useEffect(() => {
    const onMouseMove = (e: MouseEvent) => {
      if (!dragging.current) return;
      const delta = startX.current - e.clientX;
      const newWidth = Math.max(280, Math.min(700, startWidth.current + delta));
      setSideWidth(newWidth);
    };
    const onMouseUp = () => {
      if (dragging.current) {
        dragging.current = false;
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
      }
    };
    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
    return () => { window.removeEventListener('mousemove', onMouseMove); window.removeEventListener('mouseup', onMouseUp); };
  }, []);

  useEffect(() => {
    loadGraph();
    loadScenarios();
  }, []);

  useEffect(() => {
    if (selectedNodeId == null || !nodeTableRef.current) return;
    requestAnimationFrame(() => {
      const row = nodeTableRef.current?.querySelector(`[data-row-key="${selectedNodeId}"]`);
      if (row) row.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
  }, [selectedNodeId]);

  const handleMapClick = (lat: number, lon: number) => {
    if (addNodeMode) {
      setClickLat(lat);
      setClickLon(lon);
      setEditNode(null);
      setModalOpen(true);
      setAddNodeMode(false);
    }
  };

  const handleNodeClick = (id: number) => selectNode(id);

  const handleTableRowClick = (node: Node) => {
    selectNode(node.id);
    mapRef.current?.flyTo(node.lat, node.lon);
    // Scroll node table wrapper to the selected row
    if (nodeTableRef.current) {
      const selectedRow = nodeTableRef.current.querySelector('.ant-table-row-selected');
      if (selectedRow) {
        selectedRow.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }
  };

  const openEdit = (node: Node) => { setEditNode(node); setModalOpen(true); };
  const closeModal = () => { setModalOpen(false); setEditNode(null); };

  const handleDelete = async (id: number) => {
    await api.deleteNode(id);
    await loadGraph();
    message.success('节点已删除');
  };

  const handleClearNodes = async () => {
    await api.clearNodes();
    await loadGraph();
    message.success('已清空所有节点和链路');
  };

  const handleClearLinks = async () => {
    await api.clearLinks();
    await loadGraph();
    message.success('已清空所有链路');
  };

  const handleDeleteLink = async (fId: number, tId: number) => {
    await api.deleteLink(fId, tId);
    await loadGraph();
    message.success('链路已删除');
  };

  const handleAddLink = async () => {
    if (fromId === undefined || toId === undefined) { message.warning('请选择起点和终点'); return; }
    if (fromId === toId) { message.warning('起点与终点不能相同'); return; }
    await api.addLink({ fromId, toId, linkType, bandwidth });
    await loadGraph();
    message.success('链路已添加');
  };

  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const text = await file.text();
    await api.importGeoJson(text);
    await loadGraph();
    message.success('导入成功');
    e.target.value = '';
  };

  const handleExport = async () => {
    const json = await api.exportGeoJson();
    const blob = new Blob([typeof json === 'string' ? json : JSON.stringify(json, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = 'network.geojson'; a.click();
    URL.revokeObjectURL(url);
  };

  const nodeColumns = [
    { title: '名称', dataIndex: 'name', ellipsis: true, render: (name: string, node: Node) => <span><Tag style={{ marginRight: 4 }}>{node.id}</Tag>{name}</span> },
    { title: '类型', dataIndex: 'nodeType', width: 80, render: (t: string) => <Tag color={NODE_TYPE_COLORS[t]}>{NODE_TYPE_LABELS[t]}</Tag> },
    {
      title: '操作', width: 70, render: (_: unknown, node: Node) => (
        <Space size={4}>
          <EditOutlined style={{ cursor: 'pointer', color: '#1677ff' }} onClick={(e) => { e.stopPropagation(); openEdit(node); }} />
          <Popconfirm title="确认删除?" onConfirm={() => handleDelete(node.id)}>
            <DeleteOutlined style={{ cursor: 'pointer', color: 'red' }} onClick={(e) => e.stopPropagation()} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const linkColumns = [
    { title: '起点', dataIndex: 'fromId', width: 50 },
    { title: '终点', dataIndex: 'toId', width: 50 },
    { title: '类型', dataIndex: 'linkType', render: (t: string) => LINK_TYPE_LABELS[t] },
    { title: '距离(km)', dataIndex: 'baseCost', render: (v: number) => v.toFixed(1) },
    {
      title: '操作', width: 50, render: (_: unknown, link: { fromId: number; toId: number }) => (
        <Popconfirm title="删除链路?" onConfirm={() => handleDeleteLink(link.fromId, link.toId)}>
          <DeleteOutlined style={{ cursor: 'pointer', color: 'red' }} />
        </Popconfirm>
      ),
    },
  ];

  const sideItems = [
    {
      key: 'nodes',
      label: '节点管理',
      children: (
        <div className="tab-content">
          <Space wrap style={{ marginBottom: 8 }}>
            <Button size="small" icon={<PlusOutlined />} type="primary"
              onClick={() => { setAddNodeMode(true); message.info('请在地图上点击以添加节点'); }}>
              点击地图添加
            </Button>
            <Button size="small" icon={<PlusOutlined />}
              onClick={() => { setEditNode(null); setClickLat(35); setClickLon(105); setModalOpen(true); }}>
              手动添加
            </Button>
            <Popconfirm title="确认清空所有节点和链路?" onConfirm={handleClearNodes}>
              <Button size="small" icon={<ClearOutlined />} danger>清空节点</Button>
            </Popconfirm>
          </Space>
          <div className="node-table-wrap" ref={nodeTableRef}>
            <Table size="small" dataSource={graph.nodes} columns={nodeColumns} rowKey="id" pagination={false}
              rowClassName={(record) => record.id === selectedNodeId ? 'ant-table-row-selected' : ''}
              onRow={(record) => ({ onClick: () => handleTableRowClick(record), style: { cursor: 'pointer' } })}
            />
          </div>
          <div style={{ marginTop: 12, fontWeight: 600 }}>添加链路</div>
          <Space wrap style={{ marginTop: 6 }}>
            <Select style={{ width: 110 }} placeholder="起点" value={fromId} onChange={setFromId}
              options={graph.nodes.map(n => ({ value: n.id, label: `${n.id}-${n.name}` }))} />
            <Select style={{ width: 110 }} placeholder="终点" value={toId} onChange={setToId}
              options={graph.nodes.map(n => ({ value: n.id, label: `${n.id}-${n.name}` }))} />
            <Select style={{ width: 90 }} value={linkType} onChange={setLinkType}
              options={[{ value: 'FIBER', label: '光纤' }, { value: 'MICROWAVE', label: '微波' }, { value: 'SATELLITE', label: '卫星' }]} />
            <Button size="small" type="primary" onClick={handleAddLink}>添加</Button>
            <Popconfirm title="确认清空所有链路?" onConfirm={handleClearLinks}>
              <Button size="small" icon={<ClearOutlined />} danger>清空链路</Button>
            </Popconfirm>
          </Space>
          <div className="link-table-wrap" style={{ marginTop: 12 }}>
            <Table size="small" dataSource={graph.links} columns={linkColumns} rowKey={l => `${l.fromId}-${l.toId}`} pagination={{ pageSize: 5 }} />
          </div>
        </div>
      ),
    },
    { key: 'disaster', label: '灾情配置', children: <DisasterPanel /> },
    { key: 'algo', label: '救灾规划', children: <AlgorithmPanel /> },
  ];

  return (
    <>
      <header className="app-header">
        <h1>韧网智策 CommNetPlanner</h1>
        <Space>
          <Button size="small" icon={<ImportOutlined />} onClick={() => fileRef.current?.click()}>导入 GeoJSON</Button>
          <Button size="small" icon={<ExportOutlined />} onClick={handleExport}>导出 GeoJSON</Button>
          <input ref={fileRef} type="file" accept=".json,.geojson" style={{ display: 'none' }} onChange={handleImport} />
        </Space>
      </header>
      <div className="app-body">
        <div className="map-wrap" style={{ cursor: addNodeMode ? 'crosshair' : 'default' }}>
          <MapView ref={mapRef} onNodeClick={handleNodeClick} onMapClick={handleMapClick} />
        </div>
        <div className="resize-handle" onMouseDown={onDividerMouseDown} />
        <div className="side-panel" style={{ width: sideWidth }}>
          <Tabs items={sideItems} size="small" />
        </div>
      </div>
      <NodeEditModal
        open={modalOpen}
        editNode={editNode}
        initialLat={clickLat}
        initialLon={clickLon}
        onClose={closeModal}
      />
    </>
  );
}
