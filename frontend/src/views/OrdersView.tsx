import React from 'react';
import { Terminal } from 'lucide-react';
import { Card3D } from '../components/Card3D';
import { OrderForm } from '../components/OrderForm';
import { OrderFeed } from '../components/OrderFeed';
import type { CreateOrderResponse } from '../api/client';

interface OrdersViewProps {
  onOrderCreated: (order: CreateOrderResponse) => void;
  onSelectOrder: (orderId: string) => void;
  recentOrders: CreateOrderResponse[];
}

export const OrdersView: React.FC<OrdersViewProps> = ({
  onOrderCreated,
  onSelectOrder,
  recentOrders,
}) => {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <div>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#f8fafc', marginBottom: '4px' }}>
            Order Ingestion & Transactional Outbox Terminal
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '13px' }}>
            Direct write ingress to Order Service (:8081). Evaluates PostgreSQL ACID transaction boundary, Outbox event generation, and Gateway Token-Bucket rate limiting.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <span className="badge badge-emerald">PostgreSQL :5433 (order_db)</span>
          <span className="badge badge-cyan">Kafka :9092</span>
        </div>
      </div>

      {/* Main Grid: Form + Live Feed */}
      <div className="grid-2col" style={{ marginBottom: '24px' }}>
        <OrderForm onOrderCreated={onOrderCreated} />
        <OrderFeed orders={recentOrders} onSelectOrder={onSelectOrder} />
      </div>

      {/* Architectural Guarantees & Transactional Lifecycle */}
      <Card3D maxTilt={4}>
        <div className="eng-card" style={{ margin: 0 }}>
          <div className="eng-card-header">
            <div className="eng-card-title">
              <Terminal size={15} color="#38bdf8" />
              Transactional Invariant: Outbox Pattern Execution Trace
            </div>
            <span className="badge badge-cyan">DUAL-WRITE IMMUNITY</span>
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
            <div><strong style={{ color: '#38bdf8' }}>Step 1 (Order Ingress):</strong> Client sends HTTP POST to Gateway :8080 ──► Token-Bucket rate check (100 req/s bucket) ──► Routed to Order Service :8081.</div>
            <div><strong style={{ color: '#34d399' }}>Step 2 (Local DB Transaction):</strong> `BEGIN TRANSACTION` ──► INSERT INTO orders (order_id, customer_id, total_amount, status='CREATED') ──► INSERT INTO outbox_events (event_id, aggregate_id, event_type='ORDER_CREATED', payload) ──► `COMMIT`.</div>
            <div><strong style={{ color: '#fbbf24' }}>Step 3 (Asynchronous Dispatch):</strong> Background scheduled poller (every 500ms) selects unprocessed outbox events ──► Publishes to Kafka topic `order.events.created` with key=`order_id` ──► Marks outbox status=`PUBLISHED`.</div>
            <div><strong style={{ color: '#a78bfa' }}>Step 4 (Fault Recovery):</strong> If Kafka broker dies, the local DB transaction still succeeds; outbox rows buffer persistently in PostgreSQL without data loss.</div>
          </div>
        </div>
      </Card3D>
    </div>
  );
};
