import { useState, useEffect } from 'react';
import { Cpu, Navigation, Gauge, ShieldAlert, Sparkles, CheckCircle2 } from 'lucide-react';
import { getPrediction } from '../api/client';
import type { DeliveryPrediction } from '../api/client';

interface PredictionCardProps {
  selectedOrderId: string | null;
}

export const PredictionCard: React.FC<PredictionCardProps> = ({ selectedOrderId }) => {
  const [orderId, setOrderId] = useState(selectedOrderId || 'ORD-308B9CA5');
  const [prediction, setPrediction] = useState<DeliveryPrediction | null>(null);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  useEffect(() => {
    if (selectedOrderId) {
      setOrderId(selectedOrderId);
      handleFetchPrediction(selectedOrderId);
    }
  }, [selectedOrderId]);

  const handleFetchPrediction = async (idToFetch = orderId) => {
    if (!idToFetch.trim()) return;
    setLoading(true);
    setSearched(true);
    const res = await getPrediction(idToFetch.trim());
    setPrediction(res);
    setLoading(false);
  };

  return (
    <div className="glass-panel" id="prediction-panel">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Cpu size={18} color="var(--accent-violet)" />
          <h2 style={{ fontSize: '1.15rem', margin: 0 }}>Delivery ETA & Intelligence</h2>
        </div>
        <span className="badge badge-violet">Prediction Service :8085</span>
      </div>

      <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginBottom: '18px' }}>
        Evaluates real-time fulfillment center distance, highway routing, demand velocity, and TTL staleness.
      </p>

      <div style={{ display: 'flex', gap: '10px', marginBottom: '16px' }}>
        <input
          id="prediction-order-input"
          type="text"
          className="input-control"
          placeholder="Enter Order ID (e.g. ORD-308B9CA5)..."
          value={orderId}
          onChange={(e) => setOrderId(e.target.value)}
        />
        <button
          id="prediction-submit-btn"
          type="button"
          className="btn-primary"
          style={{ whiteSpace: 'nowrap' }}
          disabled={loading}
          onClick={() => handleFetchPrediction()}
        >
          <Sparkles size={16} />
          {loading ? 'Evaluating...' : 'Query ETA'}
        </button>
      </div>

      {prediction ? (
        <div
          id="prediction-result-card"
          style={{
            background: 'rgba(30, 41, 59, 0.6)',
            border: '1px solid rgba(139, 92, 246, 0.3)',
            borderRadius: '10px',
            padding: '16px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px' }}>
            <div>
              <span className="mono-text" style={{ fontSize: '0.9rem', fontWeight: 600, color: '#c084fc' }}>
                {prediction.orderId}
              </span>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Pred ID: {prediction.predictionId}
              </div>
            </div>
            <div style={{ display: 'flex', gap: '8px' }}>
              <span className="badge badge-cyan" id="prediction-fc-badge">
                <Navigation size={12} />
                {prediction.assignedFcId || prediction.fulfillmentCenterId || 'FC-NORTH'}
              </span>
              <span className={`badge ${prediction.stale ? 'badge-rose' : 'badge-emerald'}`}>
                {prediction.stale ? <ShieldAlert size={12} /> : <CheckCircle2 size={12} />}
                {prediction.stale ? 'STALE' : 'ACTIVE TTL'}
              </span>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px' }}>
            <div style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '10px', borderRadius: '8px', textAlign: 'center' }}>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>ETA Window</div>
              <div id="prediction-eta-value" style={{ fontSize: '1.25rem', fontWeight: 700, color: '#38bdf8' }}>
                {prediction.predictedEtaMinutes !== undefined
                  ? `${prediction.predictedEtaMinutes.toFixed(1)} min`
                  : (prediction.estimatedDeliveryHours !== undefined
                      ? `${prediction.estimatedDeliveryHours.toFixed(1)} hrs`
                      : '24.0 min')}
              </div>
            </div>

            <div style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '10px', borderRadius: '8px', textAlign: 'center' }}>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Transit Distance</div>
              <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#34d399' }}>
                {prediction.featureVector?.distanceKm !== undefined
                  ? Math.round(prediction.featureVector.distanceKm)
                  : (prediction.distanceKm !== undefined ? Math.round(prediction.distanceKm) : 15)} km
              </div>
            </div>

            <div style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '10px', borderRadius: '8px', textAlign: 'center' }}>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Demand Velocity</div>
              <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#a78bfa' }}>
                <Gauge size={14} style={{ display: 'inline', marginRight: '4px' }} />
                {prediction.demandVelocityScore !== undefined
                  ? (prediction.demandVelocityScore * 100).toFixed(0)
                  : (prediction.demandVelocityIndex ? (prediction.demandVelocityIndex * 100).toFixed(0) : '85')}%
              </div>
            </div>
          </div>
        </div>
      ) : (
        searched && !loading && (
          <div style={{ textAlign: 'center', padding: '18px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            No prediction found for {orderId}. Order may still be in transit through Kafka.
          </div>
        )
      )}
    </div>
  );
};
