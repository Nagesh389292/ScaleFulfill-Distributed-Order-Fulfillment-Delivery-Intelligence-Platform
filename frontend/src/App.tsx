import React, { useState, useEffect } from 'react';
import {
  Layers,
  LayoutDashboard,
  Search,
  Warehouse,
  Cpu,
  ShieldCheck,
} from 'lucide-react';
import { Header } from './components/Header';
import { OrderForm } from './components/OrderForm';
import { SearchPanel } from './components/SearchPanel';
import { OrderFeed } from './components/OrderFeed';
import { PredictionCard } from './components/PredictionCard';
import { OptimizerPanel } from './components/OptimizerPanel';
import { OrdersExplorerView } from './views/OrdersExplorerView';
import { FulfillmentNetworkView } from './views/FulfillmentNetworkView';
import { ResilienceChaosView } from './views/ResilienceChaosView';
import { getPredictionStats } from './api/client';
import type { CreateOrderResponse, PredictionStats } from './api/client';

export type NavigationPage = 'OPERATIONS' | 'ORDERS_EXPLORER' | 'FULFILLMENT_NETWORK' | 'OPTIMIZATION' | 'RESILIENCE';

export const App: React.FC = () => {
  const [activePage, setActivePage] = useState<NavigationPage>('OPERATIONS');
  const [recentOrders, setRecentOrders] = useState<CreateOrderResponse[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>('ORD-308B9CA5');
  const [stats, setStats] = useState<PredictionStats | null>(null);

  useEffect(() => {
    // Poll stats on load
    getPredictionStats().then(setStats);
    const interval = setInterval(() => {
      getPredictionStats().then(setStats);
    }, 15000);
    return () => clearInterval(interval);
  }, []);

  const handleOrderCreated = (order: CreateOrderResponse) => {
    setRecentOrders((prev) => [order, ...prev.slice(0, 9)]);
    setSelectedOrderId(order.orderId || order.id || null);
  };

  const handleSelectOrder = (orderId: string) => {
    setSelectedOrderId(orderId);
  };

  return (
    <div className="app-layout">
      {/* Enterprise Sidebar Navigation */}
      <aside className="sidebar">
        <div>
          {/* Brand Logo & Version */}
          <div className="sidebar-header">
            <div className="sidebar-logo">
              <Layers size={22} color="#ffffff" />
            </div>
            <div>
              <div className="sidebar-title" style={{ fontWeight: 800, fontSize: '1.15rem', letterSpacing: '-0.02em' }}>
                <span className="gradient-title">ScaleFulfill</span>
              </div>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#10b981' }} className="pulse-indicator"></span>
                <span>Enterprise v1.7.0 LTS</span>
              </div>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="sidebar-nav">
            <button
              type="button"
              className={`nav-link ${activePage === 'OPERATIONS' ? 'active' : ''}`}
              onClick={() => setActivePage('OPERATIONS')}
            >
              <LayoutDashboard size={18} />
              <span className="nav-text">Operations & Ingress</span>
            </button>

            <button
              type="button"
              className={`nav-link ${activePage === 'ORDERS_EXPLORER' ? 'active' : ''}`}
              onClick={() => setActivePage('ORDERS_EXPLORER')}
            >
              <Search size={18} />
              <span className="nav-text">CQRS Order Explorer</span>
            </button>

            <button
              type="button"
              className={`nav-link ${activePage === 'FULFILLMENT_NETWORK' ? 'active' : ''}`}
              onClick={() => setActivePage('FULFILLMENT_NETWORK')}
            >
              <Warehouse size={18} />
              <span className="nav-text">Multi-FC Network</span>
            </button>

            <button
              type="button"
              className={`nav-link ${activePage === 'OPTIMIZATION' ? 'active' : ''}`}
              onClick={() => setActivePage('OPTIMIZATION')}
            >
              <Cpu size={18} />
              <span className="nav-text">Wave Optimization</span>
            </button>

            <button
              type="button"
              className={`nav-link ${activePage === 'RESILIENCE' ? 'active' : ''}`}
              onClick={() => setActivePage('RESILIENCE')}
            >
              <ShieldCheck size={18} />
              <span className="nav-text">Resilience & Invariants</span>
            </button>
          </nav>
        </div>

        {/* Bottom User Profile */}
        <div className="user-profile-badge">
          <div className="avatar">
            NR
            <span className="avatar-online" />
          </div>
          <div className="user-info" style={{ overflow: 'hidden' }}>
            <div style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-primary)', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}>
              Nagesh Reddy
            </div>
            <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
              Staff Systems Engineer
            </div>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="main-wrapper">
        <div className="content-container">
          <Header stats={stats} />

          {/* PAGE 1: Operations & Ingress Control Desk */}
          {activePage === 'OPERATIONS' && (
            <div>
              {/* Row 1: Ingress Form & CQRS Read Path */}
              <div className="grid-2col" style={{ marginBottom: '24px' }}>
                <OrderForm onOrderCreated={handleOrderCreated} />
                <SearchPanel onSelectOrder={handleSelectOrder} />
              </div>

              {/* Row 2: Live Pipeline Feed & Delivery ETA Intelligence */}
              <div className="grid-2col" style={{ marginBottom: '24px' }}>
                <OrderFeed orders={recentOrders} onSelectOrder={handleSelectOrder} />
                <PredictionCard selectedOrderId={selectedOrderId} />
              </div>

              {/* Row 3: Mathematical Wave Optimization Engine */}
              <OptimizerPanel />
            </div>
          )}

          {/* PAGE 2: CQRS Search & Order Explorer */}
          {activePage === 'ORDERS_EXPLORER' && (
            <OrdersExplorerView
              onSelectOrderForEta={(id) => {
                setSelectedOrderId(id);
                setActivePage('OPERATIONS');
              }}
            />
          )}

          {/* PAGE 3: Multi-FC Network & Inventory Topology */}
          {activePage === 'FULFILLMENT_NETWORK' && (
            <FulfillmentNetworkView />
          )}

          {/* PAGE 4: Wave Optimization & Solver Parameters */}
          {activePage === 'OPTIMIZATION' && (
            <div>
              <div style={{ marginBottom: '24px' }}>
                <h2 style={{ fontSize: '1.5rem', marginBottom: '4px' }}>Mathematical Fulfillment Wave Optimization</h2>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem' }}>
                  Empirical trade-off evaluation: Sub-2ms Greedy Checkout Heuristic vs 26–332ms Google OR-Tools SCIP MILP Wave Allocation.
                </p>
              </div>
              <OptimizerPanel />
              <div style={{ marginTop: '24px' }}>
                <PredictionCard selectedOrderId={selectedOrderId} />
              </div>
            </div>
          )}

          {/* PAGE 5: Distributed Systems Resilience & Chaos */}
          {activePage === 'RESILIENCE' && (
            <ResilienceChaosView />
          )}
        </div>
      </div>
    </div>
  );
};

export default App;
