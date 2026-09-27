import { ListOrdered, CheckCircle2, ArrowRight } from 'lucide-react';
import type { CreateOrderResponse } from '../api/client';

interface OrderFeedProps {
  orders: CreateOrderResponse[];
  onSelectOrder: (orderId: string) => void;
}

export const OrderFeed: React.FC<OrderFeedProps> = ({ orders, onSelectOrder }) => {
  return (
    <div className="glass-panel" id="order-feed-panel">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <ListOrdered size={18} color="var(--accent-emerald)" />
          <h2 style={{ fontSize: '1.15rem', margin: 0 }}>Recent Outbox Orders (Live Pipeline)</h2>
        </div>
        <span className="badge badge-emerald">PostgreSQL Transaction</span>
      </div>

      <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginBottom: '18px' }}>
        Live orders captured during session. Orders committed atomically to PostgreSQL Outbox and published to Kafka.
      </p>

      <div
        id="order-feed-list"
        style={{
          maxHeight: '260px',
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
          gap: '8px',
          paddingRight: '4px',
        }}
      >
        {orders.length > 0 ? (
          orders.map((o) => (
            <div
              key={o.id}
              className="order-feed-item"
              style={{
                background: 'rgba(30, 41, 59, 0.5)',
                border: '1px solid var(--card-border)',
                borderRadius: '8px',
                padding: '10px 14px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '2px' }}>
                  <span className="mono-text" style={{ fontWeight: 600, color: '#38bdf8', fontSize: '0.85rem' }}>
                    {o.id}
                  </span>
                  <span className="badge badge-cyan" style={{ fontSize: '0.68rem', padding: '2px 6px' }}>
                    {o.customerId}
                  </span>
                  <span className="badge badge-emerald" id="order-status-badge" style={{ fontSize: '0.68rem', padding: '2px 6px' }}>
                    <CheckCircle2 size={10} />
                    {o.status}
                  </span>
                </div>
                <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                  Total: ${o.totalAmount?.toFixed(2)} • Items: {o.items?.length || 1} • Outbox: PUBLISHED
                </div>
              </div>

              <button
                type="button"
                className="btn-secondary"
                style={{ fontSize: '0.75rem', padding: '6px 10px' }}
                onClick={() => onSelectOrder(o.orderId || o.id || '')}
              >
                Inspect ETA
                <ArrowRight size={12} />
              </button>
            </div>
          ))
        ) : (
          <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            No orders created in this session yet. Submit an order above to observe the pipeline!
          </div>
        )}
      </div>
    </div>
  );
};
