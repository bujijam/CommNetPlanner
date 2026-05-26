export type NodeType = 'COMMAND' | 'HOSPITAL' | 'SHELTER' | 'RELAY' | 'AFFECTED';
export type LinkType = 'FIBER' | 'MICROWAVE' | 'SATELLITE';
export type DisasterType = 'EARTHQUAKE' | 'FLOOD' | 'WILDFIRE' | 'TYPHOON';

export interface Node {
  id: number;
  name: string;
  lat: number;
  lon: number;
  description?: string;
  nodeType: NodeType;
  population: number;
  operational: boolean;
  predictedDamageScore: number;
}

export interface Link {
  fromId: number;
  toId: number;
  baseCost: number;
  linkType: LinkType;
  bandwidth: number;
  predictedResistance: number;
}

export interface GraphDto {
  nodes: Node[];
  links: Link[];
}

export interface DisasterScenario {
  scenarioId: string;
  name: string;
  disasterType: DisasterType;
  epicenterLat: number;
  epicenterLon: number;
  magnitude: number;
  affectedRadiusKm: number;
}

export interface ResilienceScore {
  score: number;
  connectedKeyNodes: number;
  totalKeyNodes: number;
  avgLinkReliability: number;
}

export interface PathEntry {
  targetId: number;
  distance: number;
  reachable: boolean;
  path: number[];
}

export interface ShortestPathResult {
  sourceId: number;
  entries: PathEntry[];
}

export interface ConnectivityResult {
  connected: boolean;
  componentCount: number;
  components: number[][];
  suggestedEdges: Array<{ fromId: number; toId: number; baseCost: number }>;
  totalSuggestedCost: number;
}

export interface SteinerEdge {
  fromId: number;
  toId: number;
  baseCost: number;
}

export interface SteinerResult {
  terminalCount: number;
  baselineMstLength: number;
  baselineEdges: SteinerEdge[];
  improvedLength: number;
  improvedEdges: SteinerEdge[];
  improvement: number;
  usedAuxiliaryPoint: boolean;
  auxiliaryLat?: number;
  auxiliaryLon?: number;
}

export interface DisasterSteinerResult {
  gnnResult: GraphDto;
  steinerResult: SteinerResult;
}

export type AnalysisResult =
  | { type: 'shortest-path'; data: ShortestPathResult }
  | { type: 'connectivity'; data: ConnectivityResult }
  | { type: 'steiner'; data: SteinerResult }
  | { type: 'disaster-steiner'; data: DisasterSteinerResult }
  | { type: 'resilience'; data: ResilienceScore };
