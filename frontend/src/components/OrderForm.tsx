import { useState } from 'react';
import { ShoppingCart, Zap, CheckCircle2, AlertCircle, Clock } from 'lucide-react';
import { createOrder } from '../api/client';
import type { CreateOrderResponse } from '../api/client';
import { MASTER_CATALOG, MASTER_CUSTOMERS } from '../data/mockData';

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

  const selectedProduct = MASTER_CATALOG.find((p) => p.sku === productId) || MASTER_CATALOG[0];
  const selectedCustomer = MASTER_CUSTOMERS.find((c) => c.customerId === customerId) || MASTER_CUSTOMERS[0];
  const itemTotal = selectedProduct.unitPrice * Number(quantity);
  const totalWeight = (selectedProduct.weightKg * Number(quantity)).toFixed(2);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setBurstStats(null);

    const res = await createOrder({
      customerId,
      items: [{ productId, quantity: Number(quantity), unitPrice: selectedProduct.unitPrice }],
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

    const requests = Array.from({ length: 30 }).map(() =>
      createOrder({
        customerId,
        items: [{ productId, quantity: 1, unitPrice: selectedProduct.unitPrice }],
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
        <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1.5fr 90px', gap: '12px', marginBottom: '16px' }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.78rem', color: 'var(--text-secondary)', marginBottom: '6px' }} htmlFor="order-customer-select">
              Customer Profile
            </label>
            <select
              id="order-customer-select"
              className="input-control"
              value={customerId}
              onChange={(e) => setCustomerId(e.target.value)}
            >
              {MASTER_CUSTOMERS.map((c) => (
                <option key={c.customerId} value={c.customerId}>
                  {c.customerId} — {c.fullName} ({c.shippingAddress.city}, {c.shippingAddress.state})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.78rem', color: 'var(--text-secondary)', marginBottom: '6px' }} htmlFor="order-product-select">
              Product Catalog SKU
            </label>
            <select
              id="order-product-select"
              className="input-control"
              value={productId}
              onChange={(e) => setProductId(e.target.value)}
            >
              {MASTER_CATALOG.map((p) => (
                <option key={p.sku} value={p.sku}>
                  {p.sku} — {p.name} (${p.unitPrice.toFixed(2)})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.78rem', color: 'var(--text-secondary)', marginBottom: '6px' }} htmlFor="order-quantity-input">
              Qty
            </label>
            <input
              id="order-quantity-input"
              type="number"
              min="1"
              max="20"
              className="input-control"
              value={quantity}
              onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
            />
          </div>
        </div>

        {/* Live Order Specifications Bar */}
        <div style={{
          background: 'rgba(15, 23, 42, 0.6)',
          border: '1px solid var(--card-border)',
          borderRadius: '8px',
          padding: '10px 14px',
          marginBottom: '16px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          fontSize: '0.8rem'
        }}>
          <div>
            <span style={{ color: 'var(--text-muted)' }}>Destination: </span>
            <span style={{ color: 'var(--text-primary)', fontWeight: 500 }}>
              {selectedCustomer.shippingAddress.street}, {selectedCustomer.shippingAddress.city}, {selectedCustomer.shippingAddress.state}
            </span>
          </div>
          <div style={{ display: 'flex', gap: '14px' }}>
            <span>Weight: <strong style={{ color: '#38bdf8' }}>{totalWeight} kg</strong></span>
            <span>Total: <strong style={{ color: '#34d399' }}>${itemTotal.toFixed(2)}</strong></span>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            id="order-submit-btn"
            type="submit"
            className="btn-primary"
            style={{ flex: 1 }}
            disabled={loading || burstLoading}
          >
            <CheckCircle2 size={16} />
            {loading ? 'Committing Outbox...' : 'Place Order (Outbox Ingress)'}
          </button>

          <button
            id="gateway-burst-btn"
            type="button"
            className="btn-danger"
            disabled={loading || burstLoading}
            onClick={handleBurstTraffic}
            title="Fire 25 simultaneous order requests to test Redis Token Bucket rate limiting"
          >
            <Zap size={16} />
            {burstLoading ? 'Bursting...' : 'Simulate 25-Req Burst'}
          </button>
        </div>
      </form>

      {/* Single Order Confirmation Banner */}
      {lastResult?.data && (
        <div
          id="order-success-banner"
          style={{
            marginTop: '16px',
            padding: '12px 16px',
            background: 'rgba(16, 185, 129, 0.1)',
            border: '1px solid rgba(16, 185, 129, 0.3)',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <CheckCircle2 size={18} color="#34d399" />
            <div>
              <div style={{ fontWeight: 600, fontSize: '0.85rem' }}>
                Order Persisted: <span id="created-order-id" className="mono-text">{lastResult.data.orderId || lastResult.data.id}</span>
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                Status: {lastResult.data.status} • Total: ${lastResult.data.totalAmount?.toFixed(2)} • Outbox State: PENDING ➔ PUBLISHED
              </div>
            </div>
          </div>
          <span className="badge badge-emerald" id="order-latency-badge">
            <Clock size={12} />
            {lastResult.latencyMs} ms
          </span>
        </div>
      )}

      {/* Burst Rate-Limiting Evaluation Results */}
      {burstStats && (
        <div
          id="burst-results-banner"
          style={{
            marginTop: '16px',
            padding: '14px 16px',
            background: 'rgba(15, 23, 42, 0.9)',
            border: '1px solid rgba(244, 63, 94, 0.4)',
            borderRadius: '8px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
            <span style={{ fontWeight: 600, fontSize: '0.85rem', color: '#f8fafc' }}>
              Token-Bucket Live Evaluation (25 Concurrent Ingress Requests)
            </span>
            <span className="badge badge-rose">Gateway Protected</span>
          </div>
          <div style={{ display: 'flex', gap: '16px', fontSize: '0.82rem' }}>
            <div style={{ color: '#34d399' }}>
              ✓ <strong>{burstStats.success} Admitted</strong> (201 Created within token bucket)
            </div>
            <div style={{ color: '#fb7185' }}>
              ✗ <strong>{burstStats.rateLimited} Rejected</strong> (429 Rate-Limited Token Bucket shed)
            </div>
          </div>
          <div style={{ marginTop: '6px', fontSize: '0.72rem', color: 'var(--text-muted)' }}>
            Evaluated against Spring Cloud Gateway Redis Token-Bucket Limiter (Capacity: 20 tokens, Refill: 10/s)
          </div>
        </div>
      )}

      {/* Error Banner */}
      {lastResult?.error && !burstStats && (
        <div
          style={{
            marginTop: '16px',
            padding: '12px 16px',
            background: 'rgba(244, 63, 94, 0.1)',
            border: '1px solid rgba(244, 63, 94, 0.3)',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            color: '#fb7185',
            fontSize: '0.85rem',
          }}
        >
          <AlertCircle size={18} />
          <span>{lastResult.error}</span>
        </div>
      )}
    </div>
  );
};
