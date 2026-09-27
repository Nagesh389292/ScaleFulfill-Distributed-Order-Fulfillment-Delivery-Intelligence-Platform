import { useState } from 'react';
import { Box, Play, CheckCircle2, TrendingDown, Clock, Award } from 'lucide-react';
import { runOptimization } from '../api/client';
import type { OptimizationResult } from '../api/client';

export const OptimizerPanel: React.FC = () => {
  const [greedyResult, setGreedyResult] = useState<OptimizationResult | null>(null);
  const [milpResult, setMilpResult] = useState<OptimizationResult | null>(null);
  const [loadingType, setLoadingType] = useState<string | null>(null);

  const handleRun = async (type: 'GREEDY' | 'MILP') => {
    setLoadingType(type);
    const res = await runOptimization(type, 20);
    if (type === 'GREEDY') {
      setGreedyResult(res);
    } else {
      setMilpResult(res);
    }
    setLoadingType(null);
  };

  const calculateSavings = () => {
    if (!greedyResult || !milpResult) return null;
    const diff = greedyResult.totalCost - milpResult.totalCost;
    const pct = ((diff / greedyResult.totalCost) * 100).toFixed(1);
    return { diff: diff.toFixed(2), pct };
  };

  const savings = calculateSavings();

  return (
    <div className="glass-panel" id="optimizer-panel">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Box size={18} color="var(--accent-indigo)" />
          <h2 style={{ fontSize: '1.15rem', margin: 0 }}>Mathematical Wave Fulfillment Optimization</h2>
        </div>
        <span className="badge badge-indigo">Google OR-Tools SCIP MILP</span>
      </div>

      <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', marginBottom: '18px' }}>
        Evaluates real-time single-order Greedy routing (&lt;2ms) vs. globally-optimal batch Wave MILP (26–332ms) across multi-center inventory.
      </p>

      <div style={{ display: 'flex', gap: '12px', marginBottom: '20px' }}>
        <button
          id="optimizer-run-greedy-btn"
          type="button"
          className="btn-secondary"
          style={{ flex: 1 }}
          disabled={loadingType !== null}
          onClick={() => handleRun('GREEDY')}
        >
          <Play size={14} />
          {loadingType === 'GREEDY' ? 'Solving Greedy...' : 'Run Greedy Heuristic (<2ms)'}
        </button>

        <button
          id="optimizer-run-milp-btn"
          type="button"
          className="btn-primary"
          style={{ flex: 1 }}
          disabled={loadingType !== null}
          onClick={() => handleRun('MILP')}
        >
          <Award size={14} />
          {loadingType === 'MILP' ? 'Solving MILP...' : 'Run OR-Tools SCIP MILP'}
        </button>
      </div>

      {/* Comparison Grid */}
      <div id="optimizer-result-card" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
        {/* Greedy Card */}
        <div
          style={{
            background: 'rgba(30, 41, 59, 0.4)',
            border: '1px solid var(--card-border)',
            borderRadius: '8px',
            padding: '14px',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
            <span style={{ fontWeight: 600, fontSize: '0.85rem' }}>Greedy Heuristic</span>
            <span className="badge badge-amber" style={{ fontSize: '0.68rem' }}>Checkout Path</span>
          </div>

          {greedyResult ? (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', fontSize: '0.82rem' }}>
                <span style={{ color: 'var(--text-muted)' }}>Solve Latency:</span>
                <span className="mono-text" style={{ color: '#fbbf24', fontWeight: 600 }}>
                  <Clock size={12} style={{ display: 'inline', marginRight: '4px' }} />
                  {greedyResult.solveTimeMs} ms
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', fontSize: '0.82rem' }}>
                <span style={{ color: 'var(--text-muted)' }}>Total Wave Cost:</span>
                <span className="mono-text" style={{ color: '#f8fafc', fontWeight: 700 }}>
                  ${greedyResult.totalCost?.toFixed(2)}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.82rem' }}>
                <span style={{ color: 'var(--text-muted)' }}>Status:</span>
                <span className="badge badge-emerald" style={{ padding: '1px 6px', fontSize: '0.65rem' }}>
                  <CheckCircle2 size={10} />
                  {greedyResult.status}
                </span>
              </div>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '16px 0', color: 'var(--text-muted)', fontSize: '0.78rem' }}>
              Click &ldquo;Run Greedy&rdquo; to benchmark baseline
            </div>
          )}
        </div>

        {/* MILP Card */}
        <div
          style={{
            background: 'rgba(30, 41, 59, 0.4)',
            border: '1px solid rgba(99, 102, 241, 0.3)',
            borderRadius: '8px',
            padding: '14px',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
            <span style={{ fontWeight: 600, fontSize: '0.85rem', color: '#c7d2fe' }}>OR-Tools SCIP MILP</span>
            <span className="badge badge-violet" style={{ fontSize: '0.68rem' }}>Wave Planning</span>
          </div>

          {milpResult ? (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', fontSize: '0.82rem' }}>
                <span style={{ color: 'var(--text-muted)' }}>Solve Latency:</span>
                <span className="mono-text" style={{ color: '#818cf8', fontWeight: 600 }}>
                  <Clock size={12} style={{ display: 'inline', marginRight: '4px' }} />
                  {milpResult.solveTimeMs} ms
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', fontSize: '0.82rem' }}>
                <span style={{ color: 'var(--text-muted)' }}>Total Wave Cost:</span>
                <span className="mono-text" style={{ color: '#34d399', fontWeight: 700 }}>
                  ${milpResult.totalCost?.toFixed(2)}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.82rem' }}>
                <span style={{ color: 'var(--text-muted)' }}>Status:</span>
                <span className="badge badge-emerald" style={{ padding: '1px 6px', fontSize: '0.65rem' }}>
                  <CheckCircle2 size={10} />
                  {milpResult.status}
                </span>
              </div>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '16px 0', color: 'var(--text-muted)', fontSize: '0.78rem' }}>
              Click &ldquo;Run OR-Tools&rdquo; to benchmark solver
            </div>
          )}
        </div>
      </div>

      {/* Savings Summary Banner */}
      {savings && (
        <div
          id="optimizer-savings-banner"
          style={{
            marginTop: '16px',
            background: 'rgba(16, 185, 129, 0.12)',
            border: '1px solid rgba(16, 185, 129, 0.3)',
            borderRadius: '8px',
            padding: '12px 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <TrendingDown size={18} color="#34d399" />
            <span style={{ fontSize: '0.85rem', color: '#f1f5f9' }}>
              OR-Tools MILP achieved <strong style={{ color: '#34d399' }}>${savings.diff} ({savings.pct}%)</strong> cost reduction on this 20-order wave batch!
            </span>
          </div>
          <span className="badge badge-emerald">Optimal Solution</span>
        </div>
      )}
    </div>
  );
};
