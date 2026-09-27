import { useState } from 'react';
import { ShoppingCart, Zap, CheckCircle2, AlertCircle, Clock } from 'lucide-react';
import { createOrder } from '../api/client';
import type { CreateOrderResponse } from '../api/client';

interface OrderFormProps {
  onOrderCreated: (order: CreateOrderResponse) => void;
}

export const OrderForm: React.FC<OrderFormProps> = ({ onOrderCreated }) => {
  const [customerId, setCustomerId] = useState('CUST-1001');
  const [productId, setProductId] = useState('PROD-101');
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(false);
  const [burstLoading, setBurstLoading] = useState(false);
  const [lastResult, setLastResult] = useState<{
    data?: CreateOrderResponse;
    error?: string;
    status: number;
    latencyMs: number;
  } | null>(null);
  const [burstStats, setBurstStats] = useState<{ success: number; rateLimited: number } | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setBurstStats(null);

    const unitPrice = productId === 'PROD-101' ? 49.99 : 89.99;
    const res = await createOrder({
      customerId,
      items: [{ productId, quantity: Number(quantity), unitPrice }],
    });

    setLoading(false);
    setLastResult(res);

    if (res.data) {
      onOrderCreated(res.data);
    }
  };

  const handleBurstTraffic = async () => {
    setBurstLoading(true);
    setLastResult(null);
    setBurstStats(null);

    const requests = Array.from({ length: 25 }).map(() =>
      createOrder({
        customerId,
        items: [{ productId, quantity: 1, unitPrice: 49.99 }],
      })
    );

    const results = await Promise.all(requests);
    const success = results.filter((r) => r.status === 201).length;
    const rateLimited = results.filter((r) => r.status === 429).length;

    setBurstStats({ success, rateLimited });
    setBurstLoading(false);

    // Notify of first successful order if any
    const firstSuccess = results.find((r) => r.data)?.data;
    if (firstSuccess) {
      onOrderCreated(firstSuccess);
    }
  };

  return (
    <div className="glass-panel" id="order-form-panel">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <ShoppingCart size={18} color="var(--accent-cyan)" />
          <h2 style={{ fontSize: '1.15rem', margin: 0 }}>Create Order (Transactional Ingress)</h2>
        </div>
        <span className="badge badge-cyan">PostgreSQL + Outbox</span>
      </div>

      <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginBottom: '18px' }}>
        Orders commit atomically with an outbox event in PostgreSQL before asynchronous publication to Kafka.
      </p>

      <form onSubmit={handleSubmit}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 100px', gap: '12px', marginBottom: '16px' }}>
          <div>
            <label className="label-control" htmlFor="order-customer-select">Customer ID</label>
            <select
              id="order-customer-select"
              className="input-control"
              value={customerId}
              onChange={(e) => setCustomerId(e.target.value)}
            >
              <option value="CUST-1001">CUST-1001 (Priority)</option>
              <option value="CUST-1002">CUST-1002 (Standard)</option>
              <option value="CUST-1003">CUST-1003 (Express)</option>
            </select>
          </div>

          <div>
            <label className="label-control" htmlFor="order-product-select">Product SKU</label>
            <select
              id="order-product-select"
              className="input-control"
              value={productId}
              onChange={(e) => setProductId(e.target.value)}
            >
              <option value="PROD-101">PROD-101 — Ergonomic Chair ($49.99)</option>
              <option value="PROD-102">PROD-102 — Mechanical Keyboard ($89.99)</option>
            </select>
          </div>

          <div>
            <label className="label-control" htmlFor="order-quantity-input">Qty</label>
            <input
              id="order-quantity-input"
              type="number"
              min="1"
              max="50"
              className="input-control"
              value={quantity}
              onChange={(e) => setQuantity(Number(e.target.value))}
            />
          </div>
        </div>

        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <button
            id="order-submit-btn"
            type="submit"
            className="btn-primary"
            disabled={loading || burstLoading}
          >
            <ShoppingCart size={16} />
            {loading ? 'Committing Outbox...' : 'Place Order (Outbox)'}
          </button>

          <button
            id="gateway-burst-btn"
            type="button"
            className="btn-secondary"
            onClick={handleBurstTraffic}
            disabled={loading || burstLoading}
            title="Fire 25 parallel requests to test Redis Token Bucket Rate Limiting (429 vs 201)"
          >
            <Zap size={16} color="var(--accent-amber)" />
            {burstLoading ? 'Executing Burst...' : 'Simulate 25-Req Burst (Token Bucket)'}
          </button>
        </div>
      </form>

      {/* Single Result Feedback */}
      {lastResult && (
        <div style={{ marginTop: '16px' }}>
          {lastResult.status === 201 && lastResult.data && (
            <div
              id="order-success-banner"
              style={{
                background: 'rgba(16, 185, 129, 0.12)',
                border: '1px solid rgba(16, 185, 129, 0.3)',
                borderRadius: '8px',
                padding: '12px 16px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <CheckCircle2 size={18} color="#34d399" />
                <div>
                  <span style={{ fontWeight: 600, color: '#34d399', fontSize: '0.9rem' }}>
                    Order Created: <span className="mono-text" id="created-order-id">{lastResult.data.id}</span>
                  </span>
                  <div style={{ color: 'var(--text-secondary)', fontSize: '0.78rem' }}>
                    Total: ${lastResult.data.totalAmount.toFixed(2)} • Persisted in PostgreSQL + Outbox
                  </div>
                </div>
              </div>
              <span className="badge badge-emerald" id="order-latency-badge">
                <Clock size={12} />
                {lastResult.latencyMs} ms
              </span>
            </div>
          )}

          {lastResult.status === 429 && (
            <div
              id="order-error-banner"
              style={{
                background: 'rgba(244, 63, 94, 0.12)',
                border: '1px solid rgba(244, 63, 94, 0.3)',
                borderRadius: '8px',
                padding: '12px 16px',
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
              }}
            >
              <AlertCircle size={18} color="#fb7185" />
              <div>
                <span style={{ fontWeight: 600, color: '#fb7185', fontSize: '0.9rem' }}>
                  Gateway Rejection (HTTP 429 Too Many Requests)
                </span>
                <div style={{ color: 'var(--text-secondary)', fontSize: '0.78rem' }}>
                  Redis Token Bucket capacity reached. Downstream services protected from overload.
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Burst Result Feedback */}
      {burstStats && (
        <div
          id="burst-results-banner"
          style={{
            marginTop: '16px',
            background: 'rgba(99, 102, 241, 0.12)',
            border: '1px solid rgba(99, 102, 241, 0.3)',
            borderRadius: '8px',
            padding: '12px 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Zap size={18} color="var(--accent-amber)" />
            <div>
              <div style={{ fontWeight: 600, fontSize: '0.9rem', color: '#c7d2fe' }}>
                Gateway Token-Bucket Live Evaluation (25 Burst Requests)
              </div>
              <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
                Admitted: <strong style={{ color: '#34d399' }}>{burstStats.success} (201 Created)</strong> • Rejected: <strong style={{ color: '#fb7185' }}>{burstStats.rateLimited} (429 Rate-Limited)</strong>
              </div>
            </div>
          </div>
          <span className="badge badge-violet">Token Bucket Defense</span>
        </div>
      )}
    </div>
  );
};
