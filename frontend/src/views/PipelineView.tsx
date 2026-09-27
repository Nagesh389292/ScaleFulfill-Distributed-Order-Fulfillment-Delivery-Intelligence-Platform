import React from 'react';
import { Layers, Activity, Database } from 'lucide-react';

export const PipelineView: React.FC = () => {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <div>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#f8fafc', marginBottom: '4px' }}>
            Asynchronous Event Pipeline & Kafka Backbone
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '13px' }}>
            Distributed event bus providing reliable asynchronous communication between transactional write-side and downstream read/prediction projections.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <span className="badge badge-violet">Apache Kafka :9092</span>
          <span className="badge badge-emerald">Lag: 0 msgs</span>
        </div>
      </div>

      {/* Kafka Topic Specification */}
      <div className="eng-card" style={{ marginBottom: '24px' }}>
        <div className="eng-card-header">
          <div className="eng-card-title">
            <Activity size={15} color="#c084fc" />
            Kafka Topic Specification: order.events.created
          </div>
          <span className="badge badge-cyan">PARTITIONED BY ORDER_ID</span>
        </div>

        <div className="grid-4col" style={{ marginBottom: '16px' }}>
          <div style={{ background: '#0b0f19', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-subtle)' }}>
            <div style={{ color: 'var(--text-dim)', fontSize: '11px' }}>TOPIC NAME</div>
            <div className="mono-text" style={{ fontSize: '13px', fontWeight: 600, color: '#38bdf8' }}>order.events.created</div>
          </div>
          <div style={{ background: '#0b0f19', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-subtle)' }}>
            <div style={{ color: 'var(--text-dim)', fontSize: '11px' }}>PARTITION COUNT</div>
            <div className="mono-text" style={{ fontSize: '13px', fontWeight: 600, color: '#34d399' }}>3 Partitions</div>
          </div>
          <div style={{ background: '#0b0f19', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-subtle)' }}>
            <div style={{ color: 'var(--text-dim)', fontSize: '11px' }}>PARTITION KEY</div>
            <div className="mono-text" style={{ fontSize: '13px', fontWeight: 600, color: '#fbbf24' }}>order_id (murmur2)</div>
          </div>
          <div style={{ background: '#0b0f19', padding: '12px', borderRadius: '6px', border: '1px solid var(--border-subtle)' }}>
            <div style={{ color: 'var(--text-dim)', fontSize: '11px' }}>CLEANUP POLICY</div>
            <div className="mono-text" style={{ fontSize: '13px', fontWeight: 600, color: '#c084fc' }}>Delete (7 days)</div>
          </div>
        </div>

        <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
          Partitioning by <code>order_id</code> ensures strict per-order chronological ordering across all downstream consumer groups while enabling concurrent multi-threaded partition processing.
        </div>
      </div>

      {/* Consumer Groups & Lag Table */}
      <div className="eng-card" style={{ marginBottom: '24px' }}>
        <div className="eng-card-header">
          <div className="eng-card-title">
            <Layers size={15} color="#38bdf8" />
            Active Kafka Consumer Groups
          </div>
          <span className="badge badge-emerald">ALL PARTICIPATING WORKERS ACTIVE</span>
        </div>

        <div className="eng-table-container">
          <table className="eng-table">
            <thead>
              <tr>
                <th>Consumer Group ID</th>
                <th>Subscribing Service</th>
                <th>Processing Pattern</th>
                <th>Consumer Lag</th>
                <th>Health Status</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td style={{ fontWeight: 600, color: '#f8fafc' }}>inventory-service-group</td>
                <td>inventory-service (:8082)</td>
                <td>Idempotent Inbox Insertion + Stock Reservation</td>
                <td><span className="badge badge-emerald">0 msgs</span></td>
                <td><span className="badge badge-emerald">ACTIVE</span></td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600, color: '#f8fafc' }}>search-service-group</td>
                <td>search-service (:8084)</td>
                <td>CQRS Read Projection (OpenSearch Indexing)</td>
                <td><span className="badge badge-emerald">0 msgs</span></td>
                <td><span className="badge badge-emerald">ACTIVE</span></td>
              </tr>
              <tr>
                <td style={{ fontWeight: 600, color: '#f8fafc' }}>prediction-service-group</td>
                <td>prediction-service (:8085)</td>
                <td>Bounded ThreadPool Queue + Analytical ETA</td>
                <td><span className="badge badge-emerald">0 msgs</span></td>
                <td><span className="badge badge-emerald">ACTIVE</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {/* Transactional Outbox Pattern Execution */}
      <div className="eng-card">
        <div className="eng-card-header">
          <div className="eng-card-title">
            <Database size={15} color="#34d399" />
            Transactional Outbox Pattern Mechanics
          </div>
          <span className="badge badge-cyan">AT-LEAST-ONCE SEMANTICS</span>
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
          <div>1. <strong style={{ color: '#38bdf8' }}>Atomicity:</strong> Database commit encompasses both <code>orders</code> and <code>outbox_events</code>. A failure in order creation rolls back outbox insertion; no orphan events can ever be published.</div>
          <div>2. <strong style={{ color: '#34d399' }}>Asynchronous Poller:</strong> Spring Scheduled task polls <code>outbox_events WHERE status = 'PENDING'</code> at 500ms fixed delay, batching up to 100 events per query.</div>
          <div>3. <strong style={{ color: '#fbbf24' }}>Broker Resilience:</strong> If Kafka is temporarily unreachable, events remain safely stored in PostgreSQL. Upon broker reconnection, the poller drains the backlog in chronological order.</div>
          <div>4. <strong style={{ color: '#a78bfa' }}>Idempotent Consumption:</strong> Consumers handle potential redeliveries via unique constraint tracking in <code>inbox_events</code>, achieving effective once-and-only-once business logic.</div>
        </div>
      </div>
    </div>
  );
};
