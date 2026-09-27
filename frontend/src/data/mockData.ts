/**
 * Enterprise Logistics & Fulfillment Master Data
 * Realistic product catalog, customer database, fulfillment center topology, and telemetry.
 */

export interface ProductCatalogItem {
  sku: string;
  name: string;
  category: 'Electronics' | 'Computing' | 'Smart Home' | 'Audio' | 'Warehouse Equipment';
  description: string;
  unitPrice: number;
  weightKg: number;
  dimensionsCm: { l: number; w: number; h: number };
  stockPerFc: Record<string, number>;
  reorderLevel: number;
  popularityScore: number;
}

export interface CustomerProfile {
  customerId: string;
  fullName: string;
  company?: string;
  email: string;
  shippingAddress: {
    street: string;
    city: string;
    state: string;
    zipCode: string;
    latitude: number;
    longitude: number;
  };
  tier: 'Prime Platinum' | 'Enterprise Business' | 'Prime Same-Day' | 'Standard';
  lifetimeOrders: number;
}

export interface FulfillmentCenter {
  fcId: string;
  name: string;
  metroArea: string;
  coordinates: { lat: number; lng: number };
  maxCapacityUnits: number;
  currentStockUnits: number;
  outboundDockCapacityPerHour: number;
  activeRoboticUnits: number;
  baseHandlingFee: number;
  costPerTonKm: number;
  status: 'OPTIMAL' | 'HIGH_VOLUME' | 'MAINTENANCE';
}

export interface DetailedOrderRecord {
  orderId: string;
  customerId: string;
  customerName: string;
  destinationCity: string;
  status: 'PENDING' | 'CREATED' | 'PROCESSING' | 'ALLOCATED' | 'DISPATCHED' | 'DELIVERED';
  totalAmount: number;
  totalWeightKg: number;
  items: {
    sku: string;
    productName: string;
    quantity: number;
    unitPrice: number;
  }[];
  assignedFc: string;
  trackingNumber: string;
  estimatedDeliveryWindow: string;
  etaMinutes: number;
  createdAt: string;
  timeline: {
    stage: string;
    timestamp: string;
    detail: string;
    completed: boolean;
  }[];
}

// 1. Realistic Product Catalog (Amazon-grade SKUs)
export const MASTER_CATALOG: ProductCatalogItem[] = [
  {
    sku: 'PROD-101', // Alias for SKU-ECHO-ST4 for backward compatibility with tests
    name: 'Echo Studio High-Fidelity Dolby Atmos Smart Speaker with Alexa',
    category: 'Smart Home',
    description: 'Immersive 3D audio architecture with 5 directional speakers, spatial audio processing technology, and built-in Zigbee smart home hub.',
    unitPrice: 219.99,
    weightKg: 3.5,
    dimensionsCm: { l: 20.6, w: 20.6, h: 17.5 },
    stockPerFc: { 'BFI4': 450, 'DFW7': 620, 'EWR9': 380, 'ATL8': 290 },
    reorderLevel: 100,
    popularityScore: 94,
  },
  {
    sku: 'PROD-102', // Alias for SKU-KNDL-PW11
    name: 'Kindle Paperwhite Signature Edition (32 GB, Wireless Charging, Auto-Adjusting Light)',
    category: 'Electronics',
    description: '6.8-inch glare-free 300 ppi display, 10-week battery endurance, warm light adjustment, and Qi wireless fast charging.',
    unitPrice: 189.99,
    weightKg: 0.21,
    dimensionsCm: { l: 17.4, w: 12.5, h: 0.8 },
    stockPerFc: { 'BFI4': 850, 'DFW7': 1200, 'EWR9': 740, 'ATL8': 610 },
    reorderLevel: 200,
    popularityScore: 98,
  },
  {
    sku: 'PROD-103', // Alias for SKU-SNY-XM5B
    name: 'Sony WH-1000XM5 Wireless Industry-Leading Noise-Canceling Headphones',
    category: 'Audio',
    description: 'Integrated Processor V1 + HD Noise Canceling Processor QN1, 30-hour battery life, 8 beamforming microphones with AI noise reduction.',
    unitPrice: 399.99,
    weightKg: 0.25,
    dimensionsCm: { l: 22.5, w: 18.2, h: 7.4 },
    stockPerFc: { 'BFI4': 210, 'DFW7': 340, 'EWR9': 180, 'ATL8': 160 },
    reorderLevel: 50,
    popularityScore: 91,
  },
  {
    sku: 'SKU-APPL-M3AIR',
    name: 'Apple MacBook Air 15-inch M3 Chip (16GB Unified Memory, 512GB SSD Storage)',
    category: 'Computing',
    description: 'Liquid Retina display with 500 nits brightness, MagSafe 3 charging, dual Thunderbolt/USB 4 ports, and fanless silent thermal architecture.',
    unitPrice: 1499.00,
    weightKg: 1.51,
    dimensionsCm: { l: 34.0, w: 23.8, h: 1.15 },
    stockPerFc: { 'BFI4': 95, 'DFW7': 140, 'EWR9': 110, 'ATL8': 85 },
    reorderLevel: 30,
    popularityScore: 89,
  },
  {
    sku: 'SKU-LOGI-MX3S',
    name: 'Logitech MX Master 3S Ergonomic Performance Wireless Mouse (Quiet Clicks, 8K DPI)',
    category: 'Computing',
    description: 'MagSpeed electromagnetic scrolling wheel (1,000 lines/sec), darkfield glass tracking sensor, and cross-computer multi-OS Flow control.',
    unitPrice: 99.99,
    weightKg: 0.14,
    dimensionsCm: { l: 12.5, w: 8.4, h: 5.1 },
    stockPerFc: { 'BFI4': 640, 'DFW7': 890, 'EWR9': 520, 'ATL8': 410 },
    reorderLevel: 150,
    popularityScore: 86,
  },
  {
    sku: 'SKU-ANKR-737P',
    name: 'Anker 737 GaNPrime 24,000mAh Power Bank (140W Two-Way Fast Charging Smart Display)',
    category: 'Electronics',
    description: 'Power Delivery 3.1 bidirectional charging, real-time digital power output display, ActiveShield 2.0 temperature monitoring sensor.',
    unitPrice: 149.99,
    weightKg: 0.63,
    dimensionsCm: { l: 15.6, w: 5.5, h: 4.9 },
    stockPerFc: { 'BFI4': 380, 'DFW7': 460, 'EWR9': 310, 'ATL8': 240 },
    reorderLevel: 80,
    popularityScore: 82,
  },
  {
    sku: 'SKU-SSG-990P2',
    name: 'Samsung 990 PRO 2TB PCIe 4.0 NVMe M.2 Internal Gaming SSD (7450 MB/s Read)',
    category: 'Computing',
    description: 'V-NAND 3-bit MLC technology with nickel-coated controller and Dynamic Thermal Guard heat dissipation algorithm.',
    unitPrice: 179.99,
    weightKg: 0.08,
    dimensionsCm: { l: 8.0, w: 2.2, h: 0.23 },
    stockPerFc: { 'BFI4': 520, 'DFW7': 710, 'EWR9': 490, 'ATL8': 380 },
    reorderLevel: 100,
    popularityScore: 88,
  }
];

// 2. Realistic Customer Directory
export const MASTER_CUSTOMERS: CustomerProfile[] = [
  {
    customerId: 'CUST-1001',
    fullName: 'Dr. Sarah Jenkins',
    company: 'Cascade Bio-Analytics LLC',
    email: 's.jenkins@cascadebio.org',
    shippingAddress: {
      street: '4218 Fairview Ave N, Suite 400',
      city: 'Seattle',
      state: 'WA',
      zipCode: '98109',
      latitude: 47.625,
      longitude: -122.333,
    },
    tier: 'Prime Platinum',
    lifetimeOrders: 84,
  },
  {
    customerId: 'CUST-1002',
    fullName: 'Marcus Vance',
    company: 'Apex Cloud Logistics',
    email: 'm.vance@apexlogistics.io',
    shippingAddress: {
      street: '1100 Congress Ave',
      city: 'Austin',
      state: 'TX',
      zipCode: '78701',
      latitude: 30.274,
      longitude: -97.740,
    },
    tier: 'Enterprise Business',
    lifetimeOrders: 142,
  },
  {
    customerId: 'CUST-1003',
    fullName: 'Elena Rostova',
    company: 'Midwest Quantitative Trading',
    email: 'elena.rostova@mqt-capital.com',
    shippingAddress: {
      street: '233 S Wacker Dr, Floor 62',
      city: 'Chicago',
      state: 'IL',
      zipCode: '60606',
      latitude: 41.878,
      longitude: -87.635,
    },
    tier: 'Prime Same-Day',
    lifetimeOrders: 67,
  },
  {
    customerId: 'CUST-1004',
    fullName: 'David K. Chen',
    company: 'Bay Distributed Systems',
    email: 'david@baydistributed.tech',
    shippingAddress: {
      street: '500 Howard St, Suite 1200',
      city: 'San Francisco',
      state: 'CA',
      zipCode: '94105',
      latitude: 37.789,
      longitude: -122.397,
    },
    tier: 'Prime Platinum',
    lifetimeOrders: 119,
  },
  {
    customerId: 'CUST-1005',
    fullName: 'Amara Okafor',
    company: 'Piedmont Healthcare Logistics',
    email: 'amara.okafor@piedmont-supply.net',
    shippingAddress: {
      street: '1968 Peachtree Rd NW',
      city: 'Atlanta',
      state: 'GA',
      zipCode: '30309',
      latitude: 33.808,
      longitude: -84.394,
    },
    tier: 'Enterprise Business',
    lifetimeOrders: 53,
  },
];

// 3. Multi-Fulfillment Center Network Topology
export const MASTER_FCS: FulfillmentCenter[] = [
  {
    fcId: 'BFI4',
    name: 'BFI4 — Pacific Northwest Fulfillment Hub',
    metroArea: 'Seattle-Tacoma, WA',
    coordinates: { lat: 47.388, lng: -122.246 },
    maxCapacityUnits: 150000,
    currentStockUnits: 118420,
    outboundDockCapacityPerHour: 45,
    activeRoboticUnits: 320,
    baseHandlingFee: 8.50,
    costPerTonKm: 0.048,
    status: 'OPTIMAL',
  },
  {
    fcId: 'DFW7',
    name: 'DFW7 — Central Logistics Super-Center',
    metroArea: 'Dallas-Fort Worth, TX',
    coordinates: { lat: 32.899, lng: -97.040 },
    maxCapacityUnits: 250000,
    currentStockUnits: 214890,
    outboundDockCapacityPerHour: 75,
    activeRoboticUnits: 580,
    baseHandlingFee: 7.20,
    costPerTonKm: 0.042,
    status: 'OPTIMAL',
  },
  {
    fcId: 'EWR9',
    name: 'EWR9 — Northeast Express Gateway',
    metroArea: 'Newark-Carteret, NJ',
    coordinates: { lat: 40.584, lng: -74.225 },
    maxCapacityUnits: 180000,
    currentStockUnits: 156320,
    outboundDockCapacityPerHour: 60,
    activeRoboticUnits: 410,
    baseHandlingFee: 9.80,
    costPerTonKm: 0.052,
    status: 'HIGH_VOLUME',
  },
  {
    fcId: 'ATL8',
    name: 'ATL8 — Southeast Regional Distribution Center',
    metroArea: 'Atlanta-East Point, GA',
    coordinates: { lat: 33.682, lng: -84.444 },
    maxCapacityUnits: 140000,
    currentStockUnits: 98450,
    outboundDockCapacityPerHour: 40,
    activeRoboticUnits: 290,
    baseHandlingFee: 8.10,
    costPerTonKm: 0.045,
    status: 'OPTIMAL',
  },
];

// 4. Historical Verified Orders across Clusters
export const INITIAL_ORDERS: DetailedOrderRecord[] = [
  {
    orderId: 'ORD-308B9CA5',
    customerId: 'CUST-1003',
    customerName: 'Elena Rostova',
    destinationCity: 'Chicago, IL',
    status: 'CREATED',
    totalAmount: 439.98,
    totalWeightKg: 3.71,
    items: [
      { sku: 'PROD-101', productName: 'Echo Studio High-Fidelity Dolby Atmos', quantity: 1, unitPrice: 219.99 },
      { sku: 'PROD-102', productName: 'Kindle Paperwhite Signature Edition', quantity: 1, unitPrice: 189.99 },
    ],
    assignedFc: 'DFW7',
    trackingNumber: '1Z999AA10123456784',
    estimatedDeliveryWindow: 'Today by 5:30 PM (Priority)',
    etaMinutes: 19.5,
    createdAt: '2026-09-27T17:48:12.612Z',
    timeline: [
      { stage: 'API Gateway Ingress', timestamp: '17:48:12.612', detail: 'Token bucket allowed • 12ms network round-trip', completed: true },
      { stage: 'Transactional Outbox Commited', timestamp: '17:48:12.624', detail: 'PostgreSQL ACID write: order_db (Dual-Write immune)', completed: true },
      { stage: 'Kafka Topic Published', timestamp: '17:48:12.631', detail: 'Partition 2 offset #4812 • 0.07s relay duration', completed: true },
      { stage: 'Inventory Deduplication & Reserve', timestamp: '17:48:12.645', detail: 'Inbox verified UUID eventId • Reserved at DFW7', completed: true },
      { stage: 'OpenSearch CQRS Indexing', timestamp: '17:48:12.746', detail: 'Indexed in orders-index (133.98ms lag)', completed: true },
      { stage: 'Kinematic ETA Predicted', timestamp: '17:48:12.759', detail: 'Worker pool #4: 19.5 min ETA • Velocity index: 0.85', completed: true },
    ]
  },
  {
    orderId: 'ORD-77A912CE',
    customerId: 'CUST-1001',
    customerName: 'Dr. Sarah Jenkins',
    destinationCity: 'Seattle, WA',
    status: 'ALLOCATED',
    totalAmount: 1499.00,
    totalWeightKg: 1.51,
    items: [
      { sku: 'SKU-APPL-M3AIR', productName: 'Apple MacBook Air 15" M3 16GB/512GB', quantity: 1, unitPrice: 1499.00 },
    ],
    assignedFc: 'BFI4',
    trackingNumber: '1Z999AA10198765432',
    estimatedDeliveryWindow: 'Tomorrow by 10:30 AM',
    etaMinutes: 42.0,
    createdAt: '2026-09-27T17:52:40.108Z',
    timeline: [
      { stage: 'API Gateway Ingress', timestamp: '17:52:40.108', detail: 'Token bucket allowed • Gateway latency: 8ms', completed: true },
      { stage: 'Transactional Outbox Commited', timestamp: '17:52:40.119', detail: 'PostgreSQL order_db commit: ORD-77A912CE', completed: true },
      { stage: 'Kafka Topic Published', timestamp: '17:52:40.125', detail: 'Relayed to Kafka topic order.events.created', completed: true },
      { stage: 'Inventory Reserved', timestamp: '17:52:40.138', detail: 'Allocated from BFI4 local inventory', completed: true },
      { stage: 'CQRS Document Search Ready', timestamp: '17:52:40.241', detail: 'Inverted index hit totalHits: 1', completed: true },
      { stage: 'Kinematic ETA Predicted', timestamp: '17:52:40.255', detail: 'ETA: 42.0 min • BFI4 local highway routing', completed: true },
    ]
  },
  {
    orderId: 'ORD-548B2201',
    customerId: 'CUST-1004',
    customerName: 'David K. Chen',
    destinationCity: 'San Francisco, CA',
    status: 'DISPATCHED',
    totalAmount: 379.98,
    totalWeightKg: 0.22,
    items: [
      { sku: 'SKU-LOGI-MX3S', productName: 'Logitech MX Master 3S Ergonomic Mouse', quantity: 2, unitPrice: 99.99 },
      { sku: 'SKU-SSG-990P2', productName: 'Samsung 990 PRO 2TB NVMe PCIe 4.0 SSD', quantity: 1, unitPrice: 179.99 },
    ],
    assignedFc: 'BFI4',
    trackingNumber: '1Z999AA10165432198',
    estimatedDeliveryWindow: 'Delivered',
    etaMinutes: 0.0,
    createdAt: '2026-09-27T16:30:15.912Z',
    timeline: [
      { stage: 'API Gateway Ingress', timestamp: '16:30:15.912', detail: 'Token bucket evaluated: 201 Created', completed: true },
      { stage: 'Transactional Outbox Commited', timestamp: '16:30:15.922', detail: 'PostgreSQL ACID transaction finalized', completed: true },
      { stage: 'Kafka Ingestion', timestamp: '16:30:15.929', detail: 'Published to partition 0', completed: true },
      { stage: 'Inventory Stock Decremented', timestamp: '16:30:15.940', detail: 'Stock reserved and packed at BFI4 dock', completed: true },
      { stage: 'Carrier Handoff & Dispatch', timestamp: '16:45:00.000', detail: 'Dispatched via Express Line-Haul', completed: true },
      { stage: 'Final Delivery', timestamp: '18:10:00.000', detail: 'Delivered to San Francisco, CA reception', completed: true },
    ]
  }
];
