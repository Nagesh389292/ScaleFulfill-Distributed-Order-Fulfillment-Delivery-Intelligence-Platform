import React, { useState, useEffect } from 'react';
import {
  Layers,
  LayoutDashboard,
  ShoppingCart,
  Search,
  Warehouse,
  GitBranch,
  Cpu,
  Box,
  ShieldCheck,
} from 'lucide-react';
import { Header } from './components/Header';
import { OverviewView } from './views/OverviewView';
import { OrdersView } from './views/OrdersView';
import { SearchView } from './views/SearchView';
import { InventoryView } from './views/InventoryView';
import { PipelineView } from './views/PipelineView';
import { PredictionView } from './views/PredictionView';
import { OptimizationView } from './views/OptimizationView';
import { AuditView } from './views/AuditView';
import { getPredictionStats } from './api/client';
import type { CreateOrderResponse, PredictionStats } from './api/client';

export type NavigationTab =
  | 'OVERVIEW'
  | 'ORDERS'
  | 'SEARCH'
  | 'INVENTORY'
  | 'PIPELINE'
  | 'PREDICTION'
  | 'OPTIMIZATION'
  | 'AUDIT';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<NavigationTab>('OVERVIEW');
  const [recentOrders, setRecentOrders] = useState<CreateOrderResponse[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>('ORD-308B9CA5');
  const [stats, setStats] = useState<PredictionStats | null>(null);

  useEffect(() => {
    // Poll stats periodically
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
      {/* Utilitarian Engineering Sidebar */}
      <aside className="sidebar">
        <div>
          {/* Brand Header */}
          <div className="sidebar-brand">
            <div className="brand-title">
              <Layers size={18} color="#38bdf8" />
              <span>ScaleFulfill</span>
            </div>
            <div className="brand-sub">DISTRIBUTED CONTROL PLANE</div>
          </div>

          {/* Navigation Links */}
          <nav className="sidebar-nav">
            <button
              id="nav-overview"
              type="button"
              className={`nav-item ${activeTab === 'OVERVIEW' ? 'active' : ''}`}
              onClick={() => setActiveTab('OVERVIEW')}
            >
              <LayoutDashboard size={15} />
              <span>Overview</span>
            </button>

            <button
              id="nav-orders"
              type="button"
              className={`nav-item ${activeTab === 'ORDERS' ? 'active' : ''}`}
              onClick={() => setActiveTab('ORDERS')}
            >
              <ShoppingCart size={15} />
              <span>Orders</span>
            </button>

            <button
              id="nav-search"
              type="button"
              className={`nav-item ${activeTab === 'SEARCH' ? 'active' : ''}`}
              onClick={() => setActiveTab('SEARCH')}
            >
              <Search size={15} />
              <span>Search</span>
            </button>

            <button
              id="nav-inventory"
              type="button"
              className={`nav-item ${activeTab === 'INVENTORY' ? 'active' : ''}`}
              onClick={() => setActiveTab('INVENTORY')}
            >
              <Warehouse size={15} />
              <span>Inventory</span>
            </button>

            <button
              id="nav-pipeline"
              type="button"
              className={`nav-item ${activeTab === 'PIPELINE' ? 'active' : ''}`}
              onClick={() => setActiveTab('PIPELINE')}
            >
              <GitBranch size={15} />
              <span>Event Pipeline</span>
            </button>

            <button
              id="nav-prediction"
              type="button"
              className={`nav-item ${activeTab === 'PREDICTION' ? 'active' : ''}`}
              onClick={() => setActiveTab('PREDICTION')}
            >
              <Cpu size={15} />
              <span>Prediction</span>
            </button>

            <button
              id="nav-optimization"
              type="button"
              className={`nav-item ${activeTab === 'OPTIMIZATION' ? 'active' : ''}`}
              onClick={() => setActiveTab('OPTIMIZATION')}
            >
              <Box size={15} />
              <span>Optimization</span>
            </button>

            <button
              id="nav-audit"
              type="button"
              className={`nav-item ${activeTab === 'AUDIT' ? 'active' : ''}`}
              onClick={() => setActiveTab('AUDIT')}
            >
              <ShieldCheck size={15} />
              <span>Reliability & Audit</span>
            </button>
          </nav>
        </div>

        {/* System Node Telemetry */}
        <div className="sidebar-footer">
          <div>NODE: LOCALHOST (TEST CLUSTER)</div>
          <div style={{ color: '#34d399', marginTop: '3px' }}>● 6 SERVICES ONLINE</div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="main-wrapper">
        <div className="page-content">
          <Header stats={stats} />

          {/* PAGE 1: Overview Dashboard */}
          {activeTab === 'OVERVIEW' && (
            <OverviewView
              onOrderCreated={handleOrderCreated}
              onSelectOrder={handleSelectOrder}
              selectedOrderId={selectedOrderId}
              recentOrders={recentOrders}
              onNavigate={(tab) => setActiveTab(tab)}
            />
          )}

          {/* PAGE 2: Orders Ingestion & Outbox */}
          {activeTab === 'ORDERS' && (
            <OrdersView
              onOrderCreated={handleOrderCreated}
              onSelectOrder={handleSelectOrder}
              recentOrders={recentOrders}
            />
          )}

          {/* PAGE 3: CQRS OpenSearch Querying */}
          {activeTab === 'SEARCH' && (
            <SearchView
              onSelectOrderForEta={(id) => {
                setSelectedOrderId(id);
                setActiveTab('PREDICTION');
              }}
            />
          )}

          {/* PAGE 4: Multi-FC Inventory Ledger */}
          {activeTab === 'INVENTORY' && (
            <InventoryView />
          )}

          {/* PAGE 5: Event Pipeline & Kafka Backbone */}
          {activeTab === 'PIPELINE' && (
            <PipelineView />
          )}

          {/* PAGE 6: Kinematic ETA & Prediction Engine */}
          {activeTab === 'PREDICTION' && (
            <PredictionView selectedOrderId={selectedOrderId} />
          )}

          {/* PAGE 7: Mathematical Wave Optimization */}
          {activeTab === 'OPTIMIZATION' && (
            <OptimizationView />
          )}

          {/* PAGE 8: Reliability & Cross-Datastore Audit */}
          {activeTab === 'AUDIT' && (
            <AuditView />
          )}
        </div>
      </div>
    </div>
  );
};

export default App;
