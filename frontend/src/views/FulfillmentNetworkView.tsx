import React, { useState } from 'react';
import { Warehouse, CheckCircle2, AlertTriangle } from 'lucide-react';
import { MASTER_FCS, MASTER_CATALOG } from '../data/mockData';

export const FulfillmentNetworkView: React.FC = () => {
  const [selectedFcId, setSelectedFcId] = useState<string>('BFI4');

  const selectedFc = MASTER_FCS.find((fc) => fc.fcId === selectedFcId) || MASTER_FCS[0];

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
        <div>
          <h2 style={{ fontSize: '1.5rem', marginBottom: '4px' }}>Multi-Fulfillment Center Network Topology</h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem' }}>
            Real-time multi-regional inventory tracking with idempotent Inbox pattern reservation (`inventory_db`).
          </p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <span className="badge badge-emerald">Inbox Idempotency: 100%</span>
          <span className="badge badge-cyan">4 Active FC Hubs</span>
        </div>
      </div>

      {/* FC Cards Grid */}
      <div className="grid-4col" style={{ marginBottom: '28px' }}>
        {MASTER_FCS.map((fc) => {
          const isSelected = fc.fcId === selectedFcId;
          const utilizationPct = Math.round((fc.currentStockUnits / fc.maxCapacityUnits) * 100);
          return (
            <div
              key={fc.fcId}
              className="glass-panel"
              style={{
                cursor: 'pointer',
                borderColor: isSelected ? 'var(--accent-indigo)' : undefined,
                background: isSelected ? 'rgba(30, 41, 59, 0.9)' : undefined,
                boxShadow: isSelected ? '0 0 20px rgba(99, 102, 241, 0.3)' : undefined,
                padding: '20px',
              }}
              onClick={() => setSelectedFcId(fc.fcId)}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Warehouse size={18} color="var(--accent-cyan)" />
                  <span style={{ fontWeight: 700, fontSize: '1.05rem' }}>{fc.fcId}</span>
                </div>
                <span className="badge badge-emerald" style={{ fontSize: '0.68rem' }}>{fc.status}</span>
              </div>

              <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '14px' }}>
                {fc.metroArea}
              </div>

              <div style={{ marginBottom: '10px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', marginBottom: '4px' }}>
                  <span style={{ color: 'var(--text-muted)' }}>Storage Capacity</span>
                  <span style={{ fontWeight: 600, color: '#38bdf8' }}>{utilizationPct}%</span>
                </div>
                <div style={{ width: '100%', height: '6px', background: 'rgba(255,255,255,0.1)', borderRadius: '3px', overflow: 'hidden' }}>
                  <div
                    style={{
                      width: `${utilizationPct}%`,
                      height: '100%',
                      background: utilizationPct > 85 ? 'var(--accent-amber)' : 'linear-gradient(90deg, #06b6d4, #6366f1)',
                    }}
                  />
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                <span>Stock: {fc.currentStockUnits.toLocaleString()} units</span>
                <span>{fc.outboundDockCapacityPerHour} trucks/hr</span>
              </div>
            </div>
          );
        })}
      </div>

      {/* Selected FC Deep Metrics & Inventory Allocation Table */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '24px' }}>
        {/* Left: Operational Parameters */}
        <div className="glass-panel">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
            <Warehouse size={20} color="var(--accent-indigo)" />
            <h3 style={{ fontSize: '1.15rem', margin: 0 }}>{selectedFc.name}</h3>
          </div>

          <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
            Coordinates: {selectedFc.coordinates.lat}° N, {selectedFc.coordinates.lng}° W • Metro Hub: {selectedFc.metroArea}
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '12px 14px', borderRadius: '8px', display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Active Robotic Kiva Units</span>
              <span style={{ fontWeight: 600, color: '#34d399' }}>{selectedFc.activeRoboticUnits} Robots</span>
            </div>

            <div style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '12px 14px', borderRadius: '8px', display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Outbound Dock Throughput</span>
              <span style={{ fontWeight: 600, color: '#38bdf8' }}>{selectedFc.outboundDockCapacityPerHour} Trucks / Hour</span>
            </div>

            <div style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '12px 14px', borderRadius: '8px', display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Base Wave Handling Fee</span>
              <span style={{ fontWeight: 600, color: '#c084fc' }}>${selectedFc.baseHandlingFee.toFixed(2)} / order</span>
            </div>

            <div style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '12px 14px', borderRadius: '8px', display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Line-Haul Transit Rate</span>
              <span style={{ fontWeight: 600, color: '#fbbf24' }}>${selectedFc.costPerTonKm.toFixed(3)} / ton-km</span>
            </div>

            <div style={{ background: 'rgba(15, 23, 42, 0.6)', padding: '12px 14px', borderRadius: '8px', display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Inbox Table Deduplication</span>
              <span className="badge badge-emerald" style={{ fontSize: '0.7rem' }}>
                <CheckCircle2 size={10} />
                Strict Primary Key
              </span>
            </div>
          </div>
        </div>

        {/* Right: SKU Inventory Allocation Matrix */}
        <div className="glass-panel" style={{ padding: '0', overflow: 'hidden' }}>
          <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--card-border)' }}>
            <h3 style={{ fontSize: '1.1rem', margin: 0 }}>Stock Distribution Matrix across Fulfillment Network</h3>
          </div>

          <div className="data-table-container" style={{ border: 'none' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Product SKU & Description</th>
                  <th>Category</th>
                  <th>Unit Price</th>
                  <th>BFI4 (WA)</th>
                  <th>DFW7 (TX)</th>
                  <th>EWR9 (NJ)</th>
                  <th>ATL8 (GA)</th>
                  <th>Reorder Alert</th>
                </tr>
              </thead>
              <tbody>
                {MASTER_CATALOG.map((item) => {
                  const totalStock = Object.values(item.stockPerFc).reduce((a, b) => a + b, 0);
                  const isLow = totalStock < item.reorderLevel * 4;
                  return (
                    <tr key={item.sku}>
                      <td>
                        <span className="mono-text" style={{ fontWeight: 600, color: '#38bdf8' }}>
                          {item.sku}
                        </span>
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-primary)', maxWidth: '280px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {item.name}
                        </div>
                      </td>
                      <td>
                        <span className="badge badge-violet" style={{ fontSize: '0.68rem' }}>
                          {item.category}
                        </span>
                      </td>
                      <td style={{ fontWeight: 600, color: '#34d399' }}>${item.unitPrice.toFixed(2)}</td>
                      <td style={{ fontWeight: selectedFcId === 'BFI4' ? 700 : 400, color: selectedFcId === 'BFI4' ? '#38bdf8' : undefined }}>
                        {item.stockPerFc['BFI4'] || 0}
                      </td>
                      <td style={{ fontWeight: selectedFcId === 'DFW7' ? 700 : 400, color: selectedFcId === 'DFW7' ? '#38bdf8' : undefined }}>
                        {item.stockPerFc['DFW7'] || 0}
                      </td>
                      <td style={{ fontWeight: selectedFcId === 'EWR9' ? 700 : 400, color: selectedFcId === 'EWR9' ? '#38bdf8' : undefined }}>
                        {item.stockPerFc['EWR9'] || 0}
                      </td>
                      <td style={{ fontWeight: selectedFcId === 'ATL8' ? 700 : 400, color: selectedFcId === 'ATL8' ? '#38bdf8' : undefined }}>
                        {item.stockPerFc['ATL8'] || 0}
                      </td>
                      <td>
                        {isLow ? (
                          <span className="badge badge-amber" style={{ fontSize: '0.68rem' }}>
                            <AlertTriangle size={10} />
                            REORDER
                          </span>
                        ) : (
                          <span className="badge badge-emerald" style={{ fontSize: '0.68rem' }}>
                            HEALTHY
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};
