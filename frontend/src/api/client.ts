import axios from 'axios';
import type {
  GraphDto, Node, Link, DisasterScenario,
  ShortestPathResult, ConnectivityResult, SteinerResult,
  DisasterSteinerResult, ResilienceScore,
} from '../types';

// const api = axios.create({ baseURL: '/api' });
const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || '/api' });

export const fetchGraph = (): Promise<GraphDto> =>
  api.get('/graph').then(r => r.data);

export const addNode = (node: Omit<Node, 'predictedDamageScore'>): Promise<Node> =>
  api.post('/graph/nodes', node).then(r => r.data);

export const updateNode = (id: number, node: Partial<Node>): Promise<Node> =>
  api.put(`/graph/nodes/${id}`, node).then(r => r.data);

export const deleteNode = (id: number): Promise<void> =>
  api.delete(`/graph/nodes/${id}`).then(() => undefined);

export const addLink = (data: {
  fromId: number; toId: number; linkType: string; bandwidth: number;
}): Promise<Link> => api.post('/graph/links', data).then(r => r.data);

export const deleteLink = (fromId: number, toId: number): Promise<void> =>
  api.delete(`/graph/links/${fromId}/${toId}`).then(() => undefined);

export const clearNodes = (): Promise<void> =>
  api.delete('/graph/nodes/all').then(() => undefined);

export const clearLinks = (): Promise<void> =>
  api.delete('/graph/links/all').then(() => undefined);

export const importGeoJson = (json: string): Promise<void> =>
  api.post('/graph/import', json, { headers: { 'Content-Type': 'text/plain' } }).then(() => undefined);

export const exportGeoJson = (): Promise<string> =>
  api.get('/graph/export').then(r => r.data);

export const fetchScenarios = (): Promise<DisasterScenario[]> =>
  api.get('/scenarios').then(r => r.data);

export const createScenario = (s: DisasterScenario): Promise<DisasterScenario> =>
  api.post('/scenarios', s).then(r => r.data);

export const deleteScenario = (id: string): Promise<void> =>
  api.delete(`/scenarios/${id}`).then(() => undefined);

export const shortestPath = (sourceId: number): Promise<ShortestPathResult> =>
  api.post('/analysis/shortest-path', { sourceId }).then(r => r.data);

export const connectivityAnalysis = (): Promise<ConnectivityResult> =>
  api.post('/analysis/connectivity').then(r => r.data);

export const steinerAnalysis = (): Promise<SteinerResult> =>
  api.post('/analysis/steiner').then(r => r.data);

export const disasterSteinerAnalysis = (terminalIds: number[]): Promise<DisasterSteinerResult> =>
  api.post('/analysis/disaster-steiner', { terminalIds }).then(r => r.data);

export const gnnPredict = (scenario: DisasterScenario): Promise<GraphDto> =>
  api.post('/analysis/gnn-predict', scenario).then(r => r.data);

export const resilienceScore = (): Promise<ResilienceScore> =>
  api.get('/analysis/resilience').then(r => r.data);
