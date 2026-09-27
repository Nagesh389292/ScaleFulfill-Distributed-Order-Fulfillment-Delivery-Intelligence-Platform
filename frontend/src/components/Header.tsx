import React from 'react';
import { Layers, ShieldCheck, Activity, Cpu } from 'lucide-react';

interface HeaderProps {
  stats: {
    totalPredictions: number;
    activeWorkers: number;
  } | null;
}

export const Header: React.FC<HeaderProps> = ({ stats }) => {
  return (
    <header className="glass-panel" style={{ marginBottom: '24px', padding: '18px 24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '4px' }}>
            <div style={{
              width: '36px',
              height: '36px',
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #0284c7 0%, #6366f1 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 0 16px rgba(99, 102, 241, 0.4)'
            }}>
              <Layers size={20} color="#ffffff" />
            </div>
            <h1 style={{ fontSize: '1.5rem', margin: 0 }}>
              <span className="gradient-title">ScaleFulfill</span> Control Plane
            </h1>
            <span className="badge badge-emerald" id="system-status-badge">
              <ShieldCheck size={14} />
              Frozen Baseline v1.7.0
            </span>
          </div>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', margin: 0 }}>
            Event-Driven Order Fulfillment, CQRS Search Projections & Mathematical Wave Optimization
          </p>
        </div>

        {/* Live Cluster Health Indicators */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
          <div className="badge badge-cyan" id="gateway-indicator" title="Spring Cloud Gateway with Redis Token Bucket">
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#38bdf8' }} className="pulse-indicator"></span>
            Gateway :8080
          </div>
          <div className="badge badge-violet" id="kafka-indicator" title="Kafka Cluster topic: order.events.created">
            <Activity size={12} />
            Kafka :9092
          </div>
          <div className="badge badge-amber" id="opensearch-indicator" title="OpenSearch 2.12 CQRS Inverted Index">
            <Layers size={12} />
            OpenSearch :9200
          </div>
          <div className="badge badge-emerald" id="invariant-indicator" title="Cross-datastore correlation invariant: 0 data loss across 2,332 orders">
            <Cpu size={12} />
            Invariant: 0 Loss • {stats?.totalPredictions ? `${stats.totalPredictions} Preds` : '0 Drift'}
          </div>
        </div>
      </div>
    </header>
  );
};
