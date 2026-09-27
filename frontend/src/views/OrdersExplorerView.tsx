import React, { useState } from 'react';
import { Search, Filter, Package, Navigation, ChevronRight } from 'lucide-react';
import { INITIAL_ORDERS } from '../data/mockData';
import type { DetailedOrderRecord } from '../data/mockData';

interface OrdersExplorerViewProps {
  onSelectOrderForEta?: (orderId: string) => void;
}

export const OrdersExplorerView: React.FC<OrdersExplorerViewProps> = ({ onSelectOrderForEta }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL');
  const [selectedOrder, setSelectedOrder] = useState<DetailedOrderRecord | null>(INITIAL_ORDERS[0]);

  const filteredOrders = INITIAL_ORDERS.filter((order) => {
    const matchesSearch =
      order.orderId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      order.customerName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      order.items.some((i) => i.productName.toLowerCase().includes(searchTerm.toLowerCase()) || i.sku.toLowerCase().includes(searchTerm.toLowerCase()));
    const matchesStatus = selectedStatus === 'ALL' || order.status === selectedStatus;
    return matchesSearch && matchesStatus;
  });

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
        <div>
          <h2 style={{ fontSize: '1.5rem', marginBottom: '4px' }}>CQRS Order History & State Invariant Explorer</h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem' }}>
            Asynchronously projected from Kafka event log into OpenSearch 2.12 inverted index (`orders-index`).
          </p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <span className="badge badge-cyan">OpenSearch Cluster :9200</span>
          <span className="badge badge-emerald">Indexing Lag: ~133ms</span>
        </div>
      </div>

      {/* Search & Filter Bar */}
      <div className="glass-panel" style={{ padding: '16px 20px', marginBottom: '24px' }}>
        <div style={{ display: 'flex', gap: '14px', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: '280px', position: 'relative' }}>
            <Search size={16} color="var(--text-muted)" style={{ position: 'absolute', left: '12px', top: '12px' }} />
            <input
              type="text"
              className="input-control"
              style={{ paddingLeft: '38px' }}
              placeholder="Search by Order ID (ORD-...), Customer, or SKU..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Filter size={16} color="var(--text-muted)" />
            <select
              className="input-control"
              style={{ width: '160px' }}
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
            >
              <option value="ALL">All Statuses</option>
              <option value="CREATED">CREATED</option>
              <option value="ALLOCATED">ALLOCATED</option>
              <option value="DISPATCHED">DISPATCHED</option>
            </select>
          </div>
        </div>
      </div>

      {/* Main Split Layout: Orders Table & Deep Lifecycle Timeline */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: '24px' }}>
        {/* Left: Orders Table */}
        <div className="glass-panel" style={{ padding: '0', overflow: 'hidden' }}>
          <div className="data-table-container" style={{ border: 'none' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Customer</th>
                  <th>Destination</th>
                  <th>Total</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {filteredOrders.map((order) => {
                  const isSelected = selectedOrder?.orderId === order.orderId;
                  return (
                    <tr
                      key={order.orderId}
                      style={{
                        background: isSelected ? 'rgba(99, 102, 241, 0.12)' : undefined,
                        cursor: 'pointer',
                      }}
                      onClick={() => setSelectedOrder(order)}
                    >
                      <td>
                        <span className="mono-text" style={{ fontWeight: 600, color: '#38bdf8' }}>
                          {order.orderId}
                        </span>
                        <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                          FC: {order.assignedFc}
                        </div>
                      </td>
                      <td>
                        <div style={{ fontWeight: 500 }}>{order.customerName}</div>
                        <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>{order.customerId}</div>
                      </td>
                      <td style={{ color: 'var(--text-secondary)' }}>{order.destinationCity}</td>
                      <td style={{ fontWeight: 600, color: '#34d399' }}>${order.totalAmount.toFixed(2)}</td>
                      <td>
                        <span
                          className={`badge ${
                            order.status === 'CREATED'
                              ? 'badge-emerald'
                              : order.status === 'ALLOCATED'
                              ? 'badge-cyan'
                              : 'badge-violet'
                          }`}
                        >
                          {order.status}
                        </span>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="btn-secondary"
                          style={{ padding: '4px 8px', fontSize: '0.75rem' }}
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedOrder(order);
                          }}
                        >
                          Inspect
                          <ChevronRight size={12} />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        {/* Right: Selected Order Inspection Drawer */}
        {selectedOrder ? (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Package size={18} color="var(--accent-indigo)" />
                <h3 style={{ fontSize: '1.1rem', margin: 0 }}>Lifecycle & Invariant Trail</h3>
              </div>
              <span className="badge badge-emerald">{selectedOrder.status}</span>
            </div>

            <div style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '12px 16px', borderRadius: '8px', marginBottom: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Order ID:</span>
                <span className="mono-text" style={{ fontSize: '0.85rem', fontWeight: 600, color: '#c084fc' }}>
                  {selectedOrder.orderId}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Customer:</span>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-primary)' }}>{selectedOrder.customerName}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Tracking Ref:</span>
                <span className="mono-text" style={{ fontSize: '0.8rem', color: '#38bdf8' }}>{selectedOrder.trackingNumber}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Assigned Fulfillment Center:</span>
                <span className="badge badge-cyan" style={{ fontSize: '0.7rem' }}>
                  <Navigation size={10} />
                  {selectedOrder.assignedFc}
                </span>
              </div>
            </div>

            <h4 style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '12px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Distributed Timeline Events
            </h4>

            {/* Microsecond Pipeline Audit Timeline */}
            <div className="timeline-track">
              {selectedOrder.timeline.map((event, idx) => (
                <div key={idx} className={`timeline-item ${event.completed ? 'completed' : ''}`}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '2px' }}>
                    <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#f8fafc' }}>
                      {event.stage}
                    </span>
                    <span className="mono-text" style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                      {event.timestamp}
                    </span>
                  </div>
                  <p style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', margin: 0 }}>
                    {event.detail}
                  </p>
                </div>
              ))}
            </div>

            {onSelectOrderForEta && (
              <button
                type="button"
                className="btn-primary"
                style={{ width: '100%', marginTop: '16px' }}
                onClick={() => onSelectOrderForEta(selectedOrder.orderId)}
              >
                Inspect Delivery ETA & Routing
              </button>
            )}
          </div>
        ) : (
          <div className="glass-panel" style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
            Select an order from the table to inspect its distributed lifecycle audit trail.
          </div>
        )}
      </div>
    </div>
  );
};
