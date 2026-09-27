import React, { useState, useEffect } from 'react';
import { Header } from './components/Header';
import { OrderForm } from './components/OrderForm';
import { SearchPanel } from './components/SearchPanel';
import { OrderFeed } from './components/OrderFeed';
import { PredictionCard } from './components/PredictionCard';
import { OptimizerPanel } from './components/OptimizerPanel';
import { getPredictionStats } from './api/client';
import type { CreateOrderResponse, PredictionStats } from './api/client';

export const App: React.FC = () => {
  const [recentOrders, setRecentOrders] = useState<CreateOrderResponse[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>('ORD-BENCH-34');
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
    <div className="app-container">
      <Header stats={stats} />

      {/* Row 1: Ingress & CQRS Read Path */}
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
  );
};

export default App;
