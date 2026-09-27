import { useState } from 'react';
import { Search, Clock, CheckCircle } from 'lucide-react';
import { searchOrders } from '../api/client';
import type { SearchOrderDoc } from '../api/client';

interface SearchPanelProps {
  onSelectOrder: (orderId: string) => void;
}

export const SearchPanel: React.FC<SearchPanelProps> = ({ onSelectOrder }) => {
  const [query, setQuery] = useState('CUST-1001');
  const [results, setResults] = useState<SearchOrderDoc[]>([]);
  const [totalHits, setTotalHits] = useState<number | null>(null);
  const [latencyMs, setLatencyMs] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSearch = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!query.trim()) return;

    setLoading(true);
    const res = await searchOrders(query);
    setResults(res.orders);
    setTotalHits(res.totalHits);
    setLatencyMs(res.latencyMs);
    setLoading(false);
  };

  return (
    <div className="glass-panel" id="search-panel">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Search size={18} color="var(--accent-amber)" />
          <h2 style={{ fontSize: '1.15rem', margin: 0 }}>CQRS Read Path (OpenSearch 2.12)</h2>
        </div>
        <span className="badge badge-amber">Inverted Index</span>
      </div>

      <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginBottom: '18px' }}>
        Search queries query the OpenSearch inverted index, completely isolated from relational PostgreSQL write locks.
      </p>

      <form onSubmit={handleSearch} style={{ display: 'flex', gap: '10px', marginBottom: '16px' }}>
        <input
          id="search-input"
          type="text"
          className="input-control"
          placeholder="Search by Order ID, Customer ID, or SKU (e.g. CUST-1001, PROD-101)..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button
          id="search-submit-btn"
          type="submit"
          className="btn-primary"
          style={{ whiteSpace: 'nowrap' }}
          disabled={loading}
        >
          <Search size={16} />
          {loading ? 'Searching...' : 'Search CQRS'}
        </button>
      </form>

      {latencyMs !== null && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
          <span style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
            Found <strong style={{ color: 'var(--text-primary)' }}>{totalHits}</strong> matching orders in OpenSearch
          </span>
          <span className="badge badge-emerald" id="search-latency-badge">
            <Clock size={12} />
            Latency: {latencyMs} ms
          </span>
        </div>
      )}

      {/* Results List */}
      <div
        id="search-results-list"
        style={{
          maxHeight: '260px',
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
          gap: '8px',
          paddingRight: '4px',
        }}
      >
        {results.length > 0 ? (
          results.slice(0, 8).map((doc: SearchOrderDoc) => (
            <div
              key={doc.order_id}
              className="search-result-item"
              style={{
                background: 'rgba(30, 41, 59, 0.5)',
                border: '1px solid var(--card-border)',
                borderRadius: '8px',
                padding: '10px 14px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                transition: 'border-color 0.2s ease',
              }}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '2px' }}>
                  <span className="mono-text" style={{ fontWeight: 600, color: '#38bdf8', fontSize: '0.85rem' }}>
                    {doc.order_id}
                  </span>
                  <span className="badge badge-cyan" style={{ fontSize: '0.68rem', padding: '2px 6px' }}>
                    {doc.customer_id}
                  </span>
                  <span className="badge badge-emerald" style={{ fontSize: '0.68rem', padding: '2px 6px' }}>
                    <CheckCircle size={10} />
                    {doc.status}
                  </span>
                </div>
                <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                  Total: ${doc.total_amount?.toFixed(2)} • SKUs: {doc.items?.map((i: { product_id: string }) => i.product_id).join(', ') || 'N/A'}
                </div>
              </div>

              <button
                type="button"
                className="btn-secondary"
                style={{ fontSize: '0.75rem', padding: '6px 10px' }}
                onClick={() => onSelectOrder(doc.order_id)}
              >
                Inspect ETA
              </button>
            </div>
          ))
        ) : (
          totalHits === 0 && (
            <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
              No orders matched query &ldquo;{query}&rdquo;
            </div>
          )
        )}
      </div>
    </div>
  );
};
