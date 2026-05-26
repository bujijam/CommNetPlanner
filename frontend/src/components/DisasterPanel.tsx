import { useState } from 'react';
import { Form, Input, Select, Slider, Button, List, Popconfirm, Tag, message, Divider } from 'antd';
import { DeleteOutlined, ThunderboltOutlined } from '@ant-design/icons';
import type { DisasterScenario, DisasterType } from '../types';
import * as api from '../api/client';
import { useStore } from '../store/useStore';

const DISASTER_LABELS: Record<DisasterType, string> = {
  EARTHQUAKE: '地震', FLOOD: '洪水', WILDFIRE: '野火', TYPHOON: '台风',
};
const DISASTER_COLORS: Record<DisasterType, string> = {
  EARTHQUAKE: 'red', FLOOD: 'blue', WILDFIRE: 'orange', TYPHOON: 'purple',
};

const FIELD_LABELS: Record<DisasterType, { center: string; lat: string; lon: string; magnitude: string }> = {
  EARTHQUAKE: { center: '震中', lat: '震中纬度', lon: '震中经度', magnitude: '震级 (0-10)' },
  FLOOD:      { center: '灾区中心', lat: '灾区中心纬度', lon: '灾区中心经度', magnitude: '洪水等级 (0-10)' },
  WILDFIRE:   { center: '火场中心', lat: '火场中心纬度', lon: '火场中心经度', magnitude: '火势等级 (0-10)' },
  TYPHOON:    { center: '台风中心', lat: '台风中心纬度', lon: '台风中心经度', magnitude: '风力等级 (1-17)' },
};

const MAGNITUDE_CONFIG: Record<DisasterType, { min: number; max: number; step: number; marks: Record<number, string> }> = {
  EARTHQUAKE: { min: 0, max: 10, step: 0.1, marks: { 0: '0', 5: '5', 10: '10' } },
  FLOOD:      { min: 0, max: 10, step: 0.1, marks: { 0: '0', 5: '5', 10: '10' } },
  WILDFIRE:   { min: 0, max: 10, step: 0.1, marks: { 0: '0', 5: '5', 10: '10' } },
  TYPHOON:    { min: 1, max: 17, step: 1, marks: { 1: '1', 6: '6', 12: '12', 17: '17' } },
};

export default function DisasterPanel() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const { scenarios, activeScenario, setActiveScenario, runGnnPredict, loadScenarios } = useStore();
  const disasterType: DisasterType = Form.useWatch('disasterType', form) ?? 'EARTHQUAKE';
  const labels = FIELD_LABELS[disasterType];
  const magConfig = MAGNITUDE_CONFIG[disasterType];

  const onPredict = async () => {
    try {
      const values = await form.validateFields();
      const scenario: DisasterScenario = { ...values, scenarioId: values.scenarioId || Date.now().toString() };
      setLoading(true);
      await runGnnPredict(scenario);
      message.success('GNN 预测完成，节点/链路受损概率已更新');
    } catch (e: unknown) {
      if ((e as { errorFields?: unknown }).errorFields) return;
      message.error('GNN 预测失败');
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const onSave = async () => {
    try {
      const values = await form.validateFields();
      const scenario: DisasterScenario = { ...values, scenarioId: values.scenarioId || Date.now().toString() };
      await api.createScenario(scenario);
      await loadScenarios();
      message.success('场景已保存');
    } catch (e: unknown) {
      if ((e as { errorFields?: unknown }).errorFields) return;
      message.error('保存失败');
    }
  };

  const onDelete = async (id: string) => {
    await api.deleteScenario(id);
    await loadScenarios();
    if (activeScenario?.scenarioId === id) setActiveScenario(null);
  };

  const onSelect = (s: DisasterScenario) => {
    setActiveScenario(s);
    form.setFieldsValue(s);
  };

  return (
    <div className="tab-content">
      <Form form={form} layout="vertical" initialValues={{ magnitude: 6, affectedRadiusKm: 100, disasterType: 'EARTHQUAKE', epicenterLat: 31.0, epicenterLon: 103.4 }}>
        <Form.Item name="name" label="场景名称" rules={[{ required: true, message: '请输入场景名称' }]}>
          <Input placeholder="e.g. 汶川地震模拟" />
        </Form.Item>
        <Form.Item name="disasterType" label="灾害类型">
          <Select options={(['EARTHQUAKE', 'FLOOD', 'WILDFIRE', 'TYPHOON'] as DisasterType[]).map(t => ({ value: t, label: DISASTER_LABELS[t] }))} />
        </Form.Item>
        <Form.Item name="epicenterLat" label={labels.lat} rules={[{ required: true }]}>
          <Slider min={18} max={53} step={0.01} marks={{ 18: '18°N', 35: '35°N', 53: '53°N' }} />
        </Form.Item>
        <Form.Item name="epicenterLon" label={labels.lon} rules={[{ required: true }]}>
          <Slider min={73} max={135} step={0.01} marks={{ 73: '73°E', 104: '104°E', 135: '135°E' }} />
        </Form.Item>
        <Form.Item name="magnitude" label={labels.magnitude}>
          <Slider min={magConfig.min} max={magConfig.max} step={magConfig.step} marks={magConfig.marks} />
        </Form.Item>
        <Form.Item name="affectedRadiusKm" label="影响半径 (km)">
          <Slider min={10} max={500} step={10} marks={{ 10: '10km', 250: '250km', 500: '500km' }} />
        </Form.Item>
        <Button type="primary" icon={<ThunderboltOutlined />} block loading={loading} onClick={onPredict} style={{ marginBottom: 8 }}>
          应用灾情 (GNN 预测)
        </Button>
        <Button block onClick={onSave}>保存场景</Button>
      </Form>

      {scenarios.length > 0 && (
        <>
          <Divider>已保存场景</Divider>
          <List
              size="small"
              dataSource={scenarios}
              renderItem={s => (
                <List.Item
                  style={{ cursor: 'pointer', background: activeScenario?.scenarioId === s.scenarioId ? '#e6f4ff' : undefined }}
                  onClick={() => onSelect(s)}
                  actions={[
                    <Popconfirm key="del" title="删除?" onConfirm={() => onDelete(s.scenarioId)}>
                      <DeleteOutlined style={{ color: 'red' }} />
                    </Popconfirm>,
                  ]}
                >
                  <Tag color={DISASTER_COLORS[s.disasterType]}>{DISASTER_LABELS[s.disasterType]}</Tag>
                  {s.name}
                </List.Item>
              )}
            />
        </>
      )}
    </div>
  );
}
