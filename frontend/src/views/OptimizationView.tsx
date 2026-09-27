import React from 'react';
import { Box } from 'lucide-react';
import { Card3D } from '../components/Card3D';
import { OptimizerPanel } from '../components/OptimizerPanel';

export const OptimizationView: React.FC = () => {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <div>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#f8fafc', marginBottom: '4px' }}>
            Mathematical Wave Fulfillment Optimization
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '13px' }}>
            Empirical trade-off evaluation: Sub-2ms Greedy Checkout Heuristic vs 26–332ms Google OR-Tools SCIP Mixed-Integer Linear Programming (MILP) Wave Allocation.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <span className="badge badge-indigo">Google OR-Tools SCIP</span>
          <span className="badge badge-emerald">Solver Status: OPTIMAL</span>
        </div>
      </div>

      {/* Benchmark Workload Notice */}
      <div className="disclaimer-banner">
        <span>
          <strong>Optimization Note:</strong> Performance figures and cost savings reflect controlled evaluations on synthetic test batches (20–100 orders) designed to test solver scalability and branch-and-bound behavior.
        </span>
      </div>

      {/* Interactive Optimizer Panel */}
      <div style={{ marginBottom: '24px' }}>
        <OptimizerPanel />
      </div>

      {/* Mathematical Formulation Card */}
      <Card3D maxTilt={4}>
        <div className="eng-card" style={{ margin: 0 }}>
          <div className="eng-card-header">
            <div className="eng-card-title">
              <Box size={15} color="#818cf8" />
              MILP Mathematical Formulation & Objective Function
            </div>
            <span className="badge badge-cyan">SCIP SOLVER 8.0</span>
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
            <div><strong style={{ color: '#38bdf8' }}>Objective Function:</strong></div>
            <div style={{ paddingLeft: '16px', color: '#93c5fd' }}>
              Minimize Z = ∑ [ ShippingCost(i, j) + BaseHandling(j) ] * x(i, j) + λ * ImbalancePenalty
            </div>
            <div style={{ marginTop: '8px' }}><strong style={{ color: '#34d399' }}>Hard Constraints:</strong></div>
            <div style={{ paddingLeft: '16px' }}>1. <strong>Single Fulfillment Assignment:</strong> ∑_j x(i, j) = 1  ∀ order i</div>
            <div style={{ paddingLeft: '16px' }}>2. <strong>Inventory Availability:</strong> ∑_i demand(i, k) * x(i, j) ≤ inventory(j, k)  ∀ FC j, SKU k</div>
            <div style={{ paddingLeft: '16px' }}>3. <strong>Warehouse Outbound Capacity:</strong> ∑_i x(i, j) ≤ maxCapacity(j)  ∀ FC j</div>
            <div style={{ paddingLeft: '16px' }}>4. <strong>Binary Decision Variable:</strong> x(i, j) ∈ &#123;0, 1&#125;</div>
          </div>
        </div>
      </Card3D>
    </div>
  );
};
