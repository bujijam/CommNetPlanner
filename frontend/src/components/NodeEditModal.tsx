import React, { useState } from 'react';
import { Modal, Form, Input, Select, InputNumber, Switch, Button } from 'antd';
import type { Node, NodeType } from '../types';
import * as api from '../api/client';
import { useStore } from '../store/useStore';

interface Props {
  open: boolean;
  initialLat?: number;
  initialLon?: number;
  editNode?: Node | null;
  onClose: () => void;
}

const NODE_TYPES: NodeType[] = ['COMMAND', 'HOSPITAL', 'SHELTER', 'RELAY', 'AFFECTED'];
const TYPE_LABELS: Record<NodeType, string> = {
  COMMAND: '指挥中心', HOSPITAL: '医院', SHELTER: '避难所', RELAY: '中继站', AFFECTED: '受灾点',
};

export default function NodeEditModal({ open, initialLat, initialLon, editNode, onClose }: Props) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const loadGraph = useStore(s => s.loadGraph);
  const graph = useStore(s => s.graph);

  React.useEffect(() => {
    if (open) {
      if (editNode) {
        form.setFieldsValue(editNode);
      } else {
        const nextId = graph.nodes.length > 0 ? Math.max(...graph.nodes.map(n => n.id)) + 1 : 1;
        form.setFieldsValue({
          id: nextId, lat: initialLat ?? 35, lon: initialLon ?? 105,
          nodeType: 'RELAY', population: 0, operational: true, description: '',
        });
      }
    }
  }, [open, editNode, initialLat, initialLon, form, graph.nodes]);

  const onFinish = async (values: Node) => {
    setLoading(true);
    try {
      if (editNode) {
        await api.updateNode(editNode.id, values);
      } else {
        await api.addNode(values);
      }
      await loadGraph();
      onClose();
      form.resetFields();
    } catch (e: unknown) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={editNode ? '编辑节点' : '添加节点'}
      open={open}
      onCancel={onClose}
      footer={null}
      destroyOnClose
    >
      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Form.Item name="id" label="ID" rules={[{ required: true }]}>
          <InputNumber style={{ width: '100%' }} disabled={!!editNode} />
        </Form.Item>
        <Form.Item name="name" label="名称" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name="lat" label="纬度" rules={[{ required: true }]}>
          <InputNumber style={{ width: '100%' }} step={0.001} />
        </Form.Item>
        <Form.Item name="lon" label="经度" rules={[{ required: true }]}>
          <InputNumber style={{ width: '100%' }} step={0.001} />
        </Form.Item>
        <Form.Item name="nodeType" label="节点类型" rules={[{ required: true }]}>
          <Select options={NODE_TYPES.map(t => ({ value: t, label: TYPE_LABELS[t] }))} />
        </Form.Item>
        <Form.Item name="population" label="人口">
          <InputNumber style={{ width: '100%' }} min={0} />
        </Form.Item>
        <Form.Item name="operational" label="正常运行" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block>
            {editNode ? '保存' : '添加'}
          </Button>
        </Form.Item>
      </Form>
    </Modal>
  );
}
