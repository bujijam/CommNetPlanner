import { create } from 'zustand';
import type { GraphDto, DisasterScenario, AnalysisResult } from '../types';
import * as api from '../api/client';

interface AppStore {
  graph: GraphDto;
  scenarios: DisasterScenario[];
  activeScenario: DisasterScenario | null;
  analysisResults: Record<string, AnalysisResult>;
  loading: boolean;
  selectedNodeId: number | null;
  highlightedLinks: Set<string>;
  suggestedConnectivityEdges: Array<{ fromId: number; toId: number; baseCost: number }>;
  activeSteinerType: 'steiner' | 'disaster-steiner' | null;
  addNodeMode: boolean;

  loadGraph: () => Promise<void>;
  loadScenarios: () => Promise<void>;
  setActiveScenario: (s: DisasterScenario | null) => void;
  runGnnPredict: (scenario: DisasterScenario) => Promise<void>;
  runShortestPath: (sourceId: number) => Promise<void>;
  runConnectivity: () => Promise<void>;
  runSteiner: () => Promise<void>;
  runDisasterSteiner: (terminalIds: number[]) => Promise<void>;
  runResilience: () => Promise<void>;
  selectNode: (id: number | null) => void;
  setAddNodeMode: (v: boolean) => void;
}

export const useStore = create<AppStore>((set) => ({
  graph: { nodes: [], links: [] },
  scenarios: [],
  activeScenario: null,
  analysisResults: {},
  loading: false,
  selectedNodeId: null,
  highlightedLinks: new Set(),
  suggestedConnectivityEdges: [],
  activeSteinerType: null,
  addNodeMode: false,

  loadGraph: async () => {
    set({ loading: true });
    try {
      const graph = await api.fetchGraph();
      set({ graph });
    } finally {
      set({ loading: false });
    }
  },

  loadScenarios: async () => {
    const scenarios = await api.fetchScenarios();
    set({ scenarios });
  },

  setActiveScenario: (activeScenario) => set({ activeScenario }),

  runGnnPredict: async (scenario) => {
    set({ loading: true });
    try {
      const graph = await api.gnnPredict(scenario);
      set({ graph, activeScenario: scenario });
    } finally {
      set({ loading: false });
    }
  },

  runShortestPath: async (sourceId) => {
    set({ loading: true });
    try {
      const data = await api.shortestPath(sourceId);
      set(state => ({ analysisResults: { ...state.analysisResults, 'shortest-path': { type: 'shortest-path', data } } }));
    } finally {
      set({ loading: false });
    }
  },

  runConnectivity: async () => {
    set({ loading: true });
    try {
      const data = await api.connectivityAnalysis();
      set(state => ({ analysisResults: { ...state.analysisResults, connectivity: { type: 'connectivity', data } }, suggestedConnectivityEdges: data.suggestedEdges }));
    } finally {
      set({ loading: false });
    }
  },

  runSteiner: async () => {
    set({ loading: true, suggestedConnectivityEdges: [] });
    try {
      const data = await api.steinerAnalysis();
      const highlighted = new Set(
        data.improvedEdges.map(e => `${Math.min(e.fromId, e.toId)}-${Math.max(e.fromId, e.toId)}`)
      );
      set(state => ({ analysisResults: { ...state.analysisResults, steiner: { type: 'steiner', data } }, highlightedLinks: highlighted, activeSteinerType: 'steiner' }));
    } finally {
      set({ loading: false });
    }
  },

  runDisasterSteiner: async (terminalIds) => {
    set({ loading: true, suggestedConnectivityEdges: [] });
    try {
      const data = await api.disasterSteinerAnalysis(terminalIds);
      const highlighted = new Set(
        data.steinerResult.improvedEdges.map(e => `${Math.min(e.fromId, e.toId)}-${Math.max(e.fromId, e.toId)}`)
      );
      set(state => ({ analysisResults: { ...state.analysisResults, 'disaster-steiner': { type: 'disaster-steiner', data } }, highlightedLinks: highlighted, activeSteinerType: 'disaster-steiner', graph: data.gnnResult }));
    } finally {
      set({ loading: false });
    }
  },

  runResilience: async () => {
    set({ loading: true });
    try {
      const data = await api.resilienceScore();
      set(state => ({ analysisResults: { ...state.analysisResults, resilience: { type: 'resilience', data } } }));
    } finally {
      set({ loading: false });
    }
  },

  selectNode: (selectedNodeId) => set({ selectedNodeId }),
  setAddNodeMode: (addNodeMode) => set({ addNodeMode }),
}));
