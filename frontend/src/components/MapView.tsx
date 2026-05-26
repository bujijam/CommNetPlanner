import { useEffect, useRef, useState, useImperativeHandle, forwardRef } from 'react';
import { MapContainer, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import { useStore } from '../store/useStore';

// TODO: Replace with your own map key for production. Options:
//   - 高德地图 (Gaode/AMap): Get free key at https://console.amap.com/dev/key/app
//     url: https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}
//   - 天地图 (TianDiTu): Get free key at https://console.tianditu.gov.cn
//     url: https://t0.tianditu.gov.cn/img_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=YOUR_KEY
// The current tile service is for demo purposes only.
const TILE_URL = 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}';

const NODE_COLORS: Record<string, string> = {
  COMMAND: '#1677ff',
  HOSPITAL: '#f5222d',
  SHELTER: '#52c41a',
  RELAY: '#fa8c16',
  AFFECTED: '#8c8c8c',
};
const NODE_RADII: Record<string, number> = {
  COMMAND: 12, HOSPITAL: 10, SHELTER: 9, RELAY: 8, AFFECTED: 7,
};
const NODE_TYPE_CN: Record<string, string> = {
  COMMAND: '指挥中心', HOSPITAL: '医院', SHELTER: '避难所', RELAY: '中继站', AFFECTED: '受灾点',
};

function linkResistanceColor(r: number): string {
  if (r < 0.3) return '#52c41a';
  if (r < 0.7) return '#faad14';
  return '#f5222d';
}

interface NetworkLayerProps {
  onNodeClick: (id: number, lat: number, lon: number) => void;
  onMapClick: (lat: number, lon: number) => void;
  onMouseMove: (lat: number, lon: number) => void;
}

function NetworkOverlay({ onNodeClick, onMapClick, onMouseMove }: NetworkLayerProps) {
  const map = useMap();
  const svgRef = useRef<SVGSVGElement | null>(null);
  const { graph, selectedNodeId, highlightedLinks, suggestedConnectivityEdges, analysisResults, activeSteinerType } = useStore();

  useMapEvents({
    click(e) { onMapClick(e.latlng.lat, e.latlng.lng); },
    mousemove(e) { onMouseMove(e.latlng.lat, e.latlng.lng); },
  });

  useEffect(() => {
    const container = map.getContainer();
    let svg = svgRef.current;
    if (!svg) {
      svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:400';
      container.appendChild(svg);
      svgRef.current = svg;
    }

    const render = () => {
      if (!svg) return;
      svg.innerHTML = '';
      const size = map.getSize();
      svg.setAttribute('width', String(size.x));
      svg.setAttribute('height', String(size.y));

      const { nodes, links } = graph;
      const nodeMap = new Map(nodes.map(n => [n.id, n]));
      const zoom = map.getZoom();
      const linkWidth = Math.max(2, Math.min(8, (zoom - 3) * 1.2));
      const haloWidth = linkWidth + 4;

      // Draw suggested connectivity edges (dashed lines)
      for (const edge of suggestedConnectivityEdges) {
        const a = nodeMap.get(edge.fromId);
        const b = nodeMap.get(edge.toId);
        if (!a || !b) continue;
        const pa = map.latLngToContainerPoint([a.lat, a.lon]);
        const pb = map.latLngToContainerPoint([b.lat, b.lon]);
        const dashLine = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        dashLine.setAttribute('x1', String(pa.x)); dashLine.setAttribute('y1', String(pa.y));
        dashLine.setAttribute('x2', String(pb.x)); dashLine.setAttribute('y2', String(pb.y));
        dashLine.setAttribute('stroke', '#1890ff');
        dashLine.setAttribute('stroke-width', String(linkWidth));
        dashLine.setAttribute('stroke-dasharray', `${linkWidth * 3},${linkWidth * 2}`);
        dashLine.setAttribute('opacity', '0.9');
        svg.appendChild(dashLine);
      }

      // Draw baseline edges as gray dashed lines (for Steiner comparison) — behind regular links
      const steinerResult = activeSteinerType ? analysisResults[activeSteinerType] : null;
      const sData = steinerResult
        ? (steinerResult.type === 'steiner' ? steinerResult.data : steinerResult.type === 'disaster-steiner' ? steinerResult.data.steinerResult : null)
        : null;
      if (sData && highlightedLinks.size > 0) {
        for (const edge of sData.baselineEdges) {
          const a = nodeMap.get(edge.fromId);
          const b = nodeMap.get(edge.toId);
          if (!a || !b) continue;
          const pa = map.latLngToContainerPoint([a.lat, a.lon]);
          const pb = map.latLngToContainerPoint([b.lat, b.lon]);
          const bl = document.createElementNS('http://www.w3.org/2000/svg', 'line');
          bl.setAttribute('x1', String(pa.x)); bl.setAttribute('y1', String(pa.y));
          bl.setAttribute('x2', String(pb.x)); bl.setAttribute('y2', String(pb.y));
          bl.setAttribute('stroke', '#999');
          bl.setAttribute('stroke-width', String(linkWidth + 1));
          bl.setAttribute('stroke-dasharray', `${linkWidth * 3},${linkWidth * 2}`);
          bl.setAttribute('opacity', '0.7');
          svg.appendChild(bl);
        }
      }

      for (const link of links) {
        const a = nodeMap.get(link.fromId);
        const b = nodeMap.get(link.toId);
        if (!a || !b) continue;
        const pa = map.latLngToContainerPoint([a.lat, a.lon]);
        const pb = map.latLngToContainerPoint([b.lat, b.lon]);
        const key = `${Math.min(link.fromId, link.toId)}-${Math.max(link.fromId, link.toId)}`;
        const isHighlighted = highlightedLinks.has(key);

        const halo = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        halo.setAttribute('x1', String(pa.x)); halo.setAttribute('y1', String(pa.y));
        halo.setAttribute('x2', String(pb.x)); halo.setAttribute('y2', String(pb.y));
        halo.setAttribute('stroke', 'white');
        halo.setAttribute('stroke-width', isHighlighted ? String(haloWidth + 2) : String(haloWidth));
        halo.setAttribute('stroke-linecap', 'round');
        halo.setAttribute('opacity', '0.9');
        svg.appendChild(halo);

        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('x1', String(pa.x)); line.setAttribute('y1', String(pa.y));
        line.setAttribute('x2', String(pb.x)); line.setAttribute('y2', String(pb.y));
        line.setAttribute('stroke', isHighlighted ? '#722ed1' : linkResistanceColor(link.predictedResistance));
        line.setAttribute('stroke-width', isHighlighted ? String(linkWidth + 1.5) : String(linkWidth));
        line.setAttribute('stroke-linecap', 'round');
        line.setAttribute('opacity', '0.9');
        svg.appendChild(line);
      }

      for (const node of nodes) {
        const p = map.latLngToContainerPoint([node.lat, node.lon]);
        const r = NODE_RADII[node.nodeType] ?? 7;
        const color = NODE_COLORS[node.nodeType] ?? '#8c8c8c';
        const isSelected = node.id === selectedNodeId;
        const opacity = node.predictedDamageScore > 0.5 ? 0.5 : 1.0;

        const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        g.style.pointerEvents = 'all';
        g.style.cursor = 'pointer';
        g.addEventListener('click', (e) => { e.stopPropagation(); onNodeClick(node.id, node.lat, node.lon); });

        const outline = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
        outline.setAttribute('cx', String(p.x)); outline.setAttribute('cy', String(p.y));
        outline.setAttribute('r', String((isSelected ? r + 3 : r) + 2));
        outline.setAttribute('fill', isSelected ? '#faad14' : 'white');
        outline.setAttribute('opacity', '0.9');
        g.appendChild(outline);

        const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
        circle.setAttribute('cx', String(p.x)); circle.setAttribute('cy', String(p.y));
        circle.setAttribute('r', String(isSelected ? r + 3 : r));
        circle.setAttribute('fill', color);
        circle.setAttribute('opacity', String(opacity));
        circle.setAttribute('stroke', isSelected ? '#faad14' : 'rgba(0,0,0,0.3)');
        circle.setAttribute('stroke-width', isSelected ? '3' : '1');

        const title = document.createElementNS('http://www.w3.org/2000/svg', 'title');
        title.textContent = `[${node.id}] ${node.name} (${NODE_TYPE_CN[node.nodeType] ?? node.nodeType})\n受损概率: ${(node.predictedDamageScore * 100).toFixed(1)}%`;

        g.appendChild(circle); g.appendChild(title);

        if (map.getZoom() >= 7) {
          const shadow = document.createElementNS('http://www.w3.org/2000/svg', 'text');
          shadow.setAttribute('x', String(p.x + r + 3)); shadow.setAttribute('y', String(p.y + 4));
          shadow.setAttribute('font-size', '11'); shadow.setAttribute('font-weight', '500');
          shadow.setAttribute('fill', 'white'); shadow.setAttribute('stroke', 'white');
          shadow.setAttribute('stroke-width', '3'); shadow.setAttribute('paint-order', 'stroke');
          shadow.style.pointerEvents = 'none';
          shadow.textContent = `${node.id} ${node.name}`;
          g.appendChild(shadow);

          const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
          text.setAttribute('x', String(p.x + r + 3)); text.setAttribute('y', String(p.y + 4));
          text.setAttribute('font-size', '11'); text.setAttribute('font-weight', '500');
          text.setAttribute('fill', '#222');
          text.style.pointerEvents = 'none';
          text.textContent = `${node.id} ${node.name}`;
          g.appendChild(text);
        }

        svg.appendChild(g);
      }

      // Draw auxiliary Steiner point on top of everything
      if (sData && highlightedLinks.size > 0 && sData.usedAuxiliaryPoint &&
          typeof sData.auxiliaryLat === 'number' && typeof sData.auxiliaryLon === 'number') {
        const ap = map.latLngToContainerPoint([sData.auxiliaryLat, sData.auxiliaryLon]);
        const diamond = document.createElementNS('http://www.w3.org/2000/svg', 'polygon');
        const s = 10;
        diamond.setAttribute('points', `${ap.x},${ap.y - s} ${ap.x + s},${ap.y} ${ap.x},${ap.y + s} ${ap.x - s},${ap.y}`);
        diamond.setAttribute('fill', '#722ed1');
        diamond.setAttribute('stroke', 'white');
        diamond.setAttribute('stroke-width', '2');
        svg.appendChild(diamond);

        if (zoom >= 6) {
          const label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
          label.setAttribute('x', String(ap.x + 14)); label.setAttribute('y', String(ap.y + 4));
          label.setAttribute('font-size', '11'); label.setAttribute('font-weight', '600');
          label.setAttribute('fill', '#722ed1'); label.setAttribute('stroke', 'white');
          label.setAttribute('stroke-width', '3'); label.setAttribute('paint-order', 'stroke');
          label.textContent = '辅助点';
          svg.appendChild(label);
        }
      }
    };

    render();
    map.on('move zoom moveend zoomend', render);
    return () => { map.off('move zoom moveend zoomend', render); };
  }, [map, graph, selectedNodeId, highlightedLinks, suggestedConnectivityEdges, analysisResults, activeSteinerType, onNodeClick, onMapClick]);

  return null;
}

function FlyToHandler({ mapRef }: { mapRef: React.MutableRefObject<L.Map | null> }) {
  const map = useMap();
  useEffect(() => { mapRef.current = map; }, [map, mapRef]);
  return null;
}

export interface MapViewHandle {
  flyTo: (lat: number, lon: number, zoom?: number) => void;
}

interface MapViewProps {
  onNodeClick: (id: number, lat: number, lon: number) => void;
  onMapClick: (lat: number, lon: number) => void;
}

import type L from 'leaflet';

const MapView = forwardRef<MapViewHandle, MapViewProps>(({ onNodeClick, onMapClick }, ref) => {
  const mapRef = useRef<L.Map | null>(null);
  const [cursorPos, setCursorPos] = useState<{ lat: number; lon: number } | null>(null);
  const { highlightedLinks, suggestedConnectivityEdges, activeScenario } = useStore();

  const hasAnalysisResult = highlightedLinks.size > 0 || suggestedConnectivityEdges.length > 0 || !!activeScenario;

  useImperativeHandle(ref, () => ({
    flyTo: (lat: number, lon: number, zoom?: number) => {
      mapRef.current?.flyTo([lat, lon], zoom ?? mapRef.current.getZoom(), { duration: 0.5 });
    },
  }));

  return (
    <MapContainer center={[35, 105]} zoom={5} style={{ height: '100%', width: '100%' }} attributionControl={false}>
      <TileLayer
        url={TILE_URL}
        subdomains={['1', '2', '3', '4']}
      />
      <NetworkOverlay onNodeClick={onNodeClick} onMapClick={onMapClick} onMouseMove={(lat, lon) => setCursorPos({ lat, lon })} />
      <FlyToHandler mapRef={mapRef} />

      {cursorPos && (
        <div style={{
          position: 'absolute', bottom: 8, left: 8, zIndex: 1000,
          background: 'rgba(255,255,255,0.9)', padding: '2px 8px',
          borderRadius: 4, fontSize: 12, color: '#333', pointerEvents: 'none',
          boxShadow: '0 1px 3px rgba(0,0,0,0.15)',
        }}>
          {cursorPos.lat.toFixed(4)}°N, {cursorPos.lon.toFixed(4)}°E
        </div>
      )}

      {/* Always-visible legend */}
      <div style={{
        position: 'absolute', top: 10, right: 10, zIndex: 1000,
        background: 'rgba(255,255,255,0.95)', padding: '10px 14px',
        borderRadius: 6, fontSize: 12, color: '#333', lineHeight: 1.8,
        boxShadow: '0 2px 8px rgba(0,0,0,0.15)', maxWidth: 200,
      }}>
        <div style={{ fontWeight: 600, marginBottom: 4 }}>图例说明</div>
        {hasAnalysisResult && (
          <>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ display: 'inline-block', width: 24, height: 3, background: '#52c41a', borderRadius: 2 }} />
              <span>链路阻力 &lt; 30%</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ display: 'inline-block', width: 24, height: 3, background: '#faad14', borderRadius: 2 }} />
              <span>链路阻力 30%-70%</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ display: 'inline-block', width: 24, height: 3, background: '#f5222d', borderRadius: 2 }} />
              <span>链路阻力 &gt; 70%</span>
            </div>
          </>
        )}
        {highlightedLinks.size > 0 && (
          <>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ display: 'inline-block', width: 24, height: 3, background: '#722ed1', borderRadius: 2 }} />
              <span>优化路径</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ display: 'inline-block', width: 24, height: 0, borderTop: '2px dashed #999' }} />
              <span>优化前路径（对比）</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ display: 'inline-block', width: 10, height: 10, background: '#722ed1', transform: 'rotate(45deg)' }} />
              <span>辅助中继点</span>
            </div>
          </>
        )}
        {suggestedConnectivityEdges.length > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ display: 'inline-block', width: 24, height: 0, borderTop: '2px dashed #1890ff' }} />
            <span>建议补边（虚线）</span>
          </div>
        )}
        {hasAnalysisResult && (
          <div style={{ borderTop: '1px solid #eee', marginTop: 4, paddingTop: 4 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: '#1677ff' }} />
              <span>节点不透明 = 正常</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: '#1677ff', opacity: 0.5 }} />
              <span>节点半透明 = 受损</span>
            </div>
          </div>
        )}
        {!hasAnalysisResult && (
          <div style={{ color: '#999', fontSize: 11 }}>应用灾情后显示详细图例</div>
        )}
      </div>

      {/* Custom attribution */}
      <div style={{
        position: 'absolute', bottom: 2, right: 8, zIndex: 1000,
        fontSize: 11, color: '#555', pointerEvents: 'none',
        textShadow: '0 0 3px white, 0 0 3px white',
      }}>
        ©2026 高德 - GS(2025)5996号 - 甲测资字11112528
      </div>
    </MapContainer>
  );
});

export default MapView;
