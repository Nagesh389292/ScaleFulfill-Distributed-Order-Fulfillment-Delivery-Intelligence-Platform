/**
 * ScaleFulfill Unified API Client
 * Interacts with Spring Cloud Gateway (:8080) and Prediction Service (:8085)
 */

export interface OrderItem {
  productId: string;
  quantity: number;
  unitPrice: number;
}

export interface CreateOrderRequest {
  customerId: string;
  items: OrderItem[];
}

export interface CreateOrderResponse {
  orderId: string;
  id?: string;
  customerId: string;
  status: string;
  totalAmount: number;
  createdAt: string;
  items: {
    itemId?: number;
    productId: string;
    quantity: number;
    unitPrice: number;
  }[];
}

export interface SearchOrderDoc {
  order_id: string;
  customer_id: string;
  status: string;
  total_amount: number;
  created_at: string;
  search_text: string;
  items: {
    product_id: string;
    product_name?: string;
    quantity: number;
    unit_price: number;
  }[];
}

export interface SearchResponse {
  query: string;
  totalHits: number;
  orders: SearchOrderDoc[];
  latencyMs: number;
}

export interface DeliveryPrediction {
  predictionId: string;
  orderId: string;
  predictedEtaMinutes?: number;
  estimatedDeliveryHours?: number;
  assignedFcId?: string;
  fulfillmentCenterId?: string;
  confidenceScore?: number;
  distanceKm?: number;
  demandVelocityScore?: number;
  demandVelocityIndex?: number;
  featureVector?: {
    distanceKm?: number;
    totalWeightKg?: number;
    carrierVelocityKmh?: number;
  };
  createdAt?: string;
  expiresAt?: string | number;
  stale: boolean;
}

export interface OptimizationResult {
  batchId: string;
  algorithm: string;
  solverType?: string;
  solveTimeMs: number;
  totalCost: number;
  status: string;
  batchSize: number;
  allocatedCount: number;
  unallocatedCount: number;
  assignments: {
    orderId: string;
    assignedFcId: string;
    shippingCost: number;
    totalCost: number;
  }[];
}

export interface PredictionStats {
  totalPredictions: number;
  activeWorkers: number;
  corePoolSize: number;
  queueDepth: number;
  queueRemaining: number;
  staleAccessCount: number;
}

// 1. Create Order via Gateway
export async function createOrder(req: CreateOrderRequest): Promise<{ data?: CreateOrderResponse; error?: string; status: number; latencyMs: number }> {
  const t0 = performance.now();
  try {
    const res = await fetch('/api/v1/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    });
    const latencyMs = Math.round(performance.now() - t0);

    if (res.status === 201) {
      const data = await res.json();
      const normalized: CreateOrderResponse = {
        orderId: data.orderId || data.id,
        id: data.orderId || data.id,
        customerId: data.customerId,
        status: data.status,
        totalAmount: data.totalAmount,
        createdAt: data.createdAt,
        items: data.items || [],
      };
      return { data: normalized, status: 201, latencyMs };
    } else if (res.status === 429) {
      return { error: 'HTTP 429: Rate Limit Exceeded (Redis Token Bucket)', status: 429, latencyMs };
    } else {
      const errText = await res.text();
      return { error: `HTTP ${res.status}: ${errText || 'Order creation failed'}`, status: res.status, latencyMs };
    }
  } catch (err: any) {
    const latencyMs = Math.round(performance.now() - t0);
    return { error: err.message || 'Network error', status: 500, latencyMs };
  }
}

// 2. Query Orders via OpenSearch CQRS Search
export async function searchOrders(query: string): Promise<SearchResponse> {
  const t0 = performance.now();
  try {
    const res = await fetch(`/api/v1/search/orders?q=${encodeURIComponent(query)}`);
    const latencyMs = Math.round(performance.now() - t0);
    if (!res.ok) {
      return { query, totalHits: 0, orders: [], latencyMs };
    }
    const data = await res.json();
    const orderList: SearchOrderDoc[] = data.results || data.orders || [];
    return {
      query,
      totalHits: data.totalHits !== undefined ? data.totalHits : orderList.length,
      orders: orderList,
      latencyMs,
    };
  } catch {
    return { query, totalHits: 0, orders: [], latencyMs: Math.round(performance.now() - t0) };
  }
}

// 3. Fetch Delivery Prediction
export async function getPrediction(orderId: string): Promise<DeliveryPrediction | null> {
  try {
    const res = await fetch(`/api/predictions/${encodeURIComponent(orderId)}`);
    if (res.ok) {
      return await res.json();
    }
    return null;
  } catch {
    return null;
  }
}

// 4. Fetch Prediction Engine Stats
export async function getPredictionStats(): Promise<PredictionStats | null> {
  try {
    const res = await fetch('/api/predictions/stats');
    if (res.ok) {
      return await res.json();
    }
    return null;
  } catch {
    return null;
  }
}

// 5. Run Fulfillment Wave Optimization (Greedy vs MILP)
export async function runOptimization(solverType: 'GREEDY' | 'MILP', orderCount = 8): Promise<OptimizationResult | null> {
  try {
    const endpoint = solverType === 'GREEDY' ? '/api/optimize/greedy' : '/api/optimize/milp';

    const orders = Array.from({ length: orderCount }).map((_, i) => ({
      orderId: `WAVE-ORD-${1000 + i}`,
      customerId: `CUST-${1001 + (i % 3)}`,
      latitude: 37.77 + (i * 0.1),
      longitude: -122.41 + (i * 0.1),
      itemDemands: {
        'PROD-101': 1 + (i % 2),
        'PROD-102': i % 3 === 0 ? 1 : 0
      }
    }));

    const fulfillmentCenters = [
      {
        fcId: 'FC-WEST',
        name: 'West Bay FC',
        latitude: 37.78,
        longitude: -122.40,
        maxCapacity: 100,
        baseCost: 8.50,
        costPerKm: 0.05,
        availableInventory: { 'PROD-101': 50, 'PROD-102': 25 }
      },
      {
        fcId: 'FC-SOUTH',
        name: 'South Valley FC',
        latitude: 34.05,
        longitude: -118.24,
        maxCapacity: 80,
        baseCost: 12.00,
        costPerKm: 0.06,
        availableInventory: { 'PROD-101': 40, 'PROD-102': 20 }
      },
      {
        fcId: 'FC-CENTRAL',
        name: 'Central Hub FC',
        latitude: 39.73,
        longitude: -104.99,
        maxCapacity: 150,
        baseCost: 15.00,
        costPerKm: 0.04,
        availableInventory: { 'PROD-101': 80, 'PROD-102': 40 }
      }
    ];

    const reqBody = {
      batchId: `WAVE-${Date.now()}`,
      orders,
      fulfillmentCenters,
      solverTimeoutMs: 3000,
      imbalancePenaltyWeight: 0.50
    };

    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(reqBody),
    });

    if (res.ok) {
      return await res.json();
    }
    return null;
  } catch {
    return null;
  }
}
