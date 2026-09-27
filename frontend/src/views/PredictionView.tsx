import React, { useState, useEffect } from 'react';
import { Cpu } from 'lucide-react';
import { PredictionCard } from '../components/PredictionCard';
import { getPredictionStats } from '../api/client';
import type { PredictionStats } from '../api/client';

interface PredictionViewProps {
  selectedOrderId: string | null;
}

export const PredictionView: React.FC<PredictionViewProps> = ({ selectedOrderId }) => {
  const [stats, setStats] = useState<PredictionStats | null>(null);

  useEffect(() => {
    getPredictionStats().then(setStats);
    const interval = setInterval(() => {
      getPredictionStats().then(setStats);
    }, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <div>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#f8fafc', marginBottom: '4px' }}>
            Delivery ETA & Kinematic Routing Engine
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '13px' }}>
            Asynchronously consumes order creation events to compute kinematic transit times based on fulfillment center distance, highway velocity, package weight, and local demand pressure.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <span className="badge badge-violet">Prediction Service :8085</span>
          <span className="badge badge-emerald">Model: eta-v1</span>
        </div>
      </div>

      {/* Worker Pool Telemetry Grid */}
      <div className="grid-4col" style={{ marginBottom: '20px' }}>
        <div className="eng-card" style={{ padding: '12px 16px', margin: 0 }}>
          <div style={{ fontSize: '11px', color: 'var(--text-dim)' }}>ACTIVE WORKER THREADS</div>
          <div style={{ fontSize: '14px', fontWeight: 600, color: '#38bdf8', fontFamily: 'var(--font-mono)' }}>
            {stats ? `${stats.activeWorkers} / ${stats.corePoolSize} Threads` : '8 / 8 Active'}
          </div>
        </div>

        <div className="eng-card" style={{ padding: '12px 16px', margin: 0 }}>
          <div style={{ fontSize: '11px', color: 'var(--text-dim)' }}>BOUNDED QUEUE DEPTH</div>
          <div style={{ fontSize: '14px', fontWeight: 600, color: '#34d399', fontFamily: 'var(--font-mono)' }}>
            {stats ? `${stats.queueDepth} queued (${stats.queueRemaining} capacity)` : '0 queued (1000 capacity)'}
          </div>
        </div>

        <div className="eng-card" style={{ padding: '12px 16px', margin: 0 }}>
          <div style={{ fontSize: '11px', color: 'var(--text-dim)' }}>PREDICTIONS COMPUTED</div>
          <div style={{ fontSize: '14px', fontWeight: 600, color: '#fbbf24', fontFamily: 'var(--font-mono)' }}>
            {stats ? stats.totalPredictions.toLocaleString() : '2,332+'}
          </div>
        </div>

        <div className="eng-card" style={{ padding: '12px 16px', margin: 0 }}>
          <div style={{ fontSize: '11px', color: 'var(--text-dim)' }}>STALE ACCESS DETECTIONS</div>
          <div style={{ fontSize: '14px', fontWeight: 600, color: '#c084fc', fontFamily: 'var(--font-mono)' }}>
            {stats ? stats.staleAccessCount : 0} (TTL Expired)
          </div>
        </div>
      </div>

      {/* Interactive Prediction Query Card */}
      <div style={{ marginBottom: '24px' }}>
        <PredictionCard selectedOrderId={selectedOrderId} />
      </div>

      {/* Kinematic Model Formulation Card */}
      <div className="eng-card">
        <div className="eng-card-header">
          <div className="eng-card-title">
            <Cpu size={15} color="#c084fc" />
            Analytical Baseline Formulation (eta-v1)
          </div>
          <span className="badge badge-cyan">DETERMINISTIC INFERENCE</span>
        </div>

        <div style={{
          background: '#0b0f19',
          border: '1px solid var(--border-subtle)',
          borderRadius: '6px',
          padding: '16px',
          fontFamily: 'var(--font-mono)',
          fontSize: '12px',
          lineHeight: '1.7',
          color: '#cbd5e1',
        }}>
          <div><strong style={{ color: '#38bdf8' }}>Mathematical Equation:</strong></div>
          <div style={{ paddingLeft: '16px', color: '#93c5fd' }}>
            ETA (minutes) = (Distance_km / Velocity_kmh * 60) + HandlingTime * (1 + DemandVelocityScore)
          </div>
          <div style={{ marginTop: '8px' }}><strong style={{ color: '#34d399' }}>Feature Vector Parameters:</strong></div>
          <div style={{ paddingLeft: '16px' }}>• <code>Distance_km</code>: Great-circle distance between customer geo-coordinates and assigned fulfillment center.</div>
          <div style={{ paddingLeft: '16px' }}>• <code>Velocity_kmh</code>: Regional baseline speed (default 45.0 km/h in urban corridors).</div>
          <div style={{ paddingLeft: '16px' }}>• <code>HandlingTime</code>: Warehouse picking & packing latency scaled by total item weight.</div>
          <div style={{ paddingLeft: '16px' }}>• <code>DemandVelocityScore</code>: Real-time sliding-window order density per zip-code region.</div>
        </div>
      </div>
    </div>
  );
};
