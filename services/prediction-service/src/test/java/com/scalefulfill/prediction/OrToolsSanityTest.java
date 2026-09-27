package com.scalefulfill.prediction;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import com.google.ortools.linearsolver.MPObjective;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OrToolsSanityTest {

    @Test
    void testOrToolsNativeLoadAndSolve() {
        Loader.loadNativeLibraries();
        MPSolver solver = MPSolver.createSolver("SCIP");
        assertNotNull(solver, "SCIP solver should be available in OR-Tools");

        // Simple LP: max x + 2y subject to x + y <= 4, x, y >= 0
        MPVariable x = solver.makeNumVar(0.0, Double.POSITIVE_INFINITY, "x");
        MPVariable y = solver.makeNumVar(0.0, Double.POSITIVE_INFINITY, "y");

        var constraint = solver.makeConstraint(0.0, 4.0, "c0");
        constraint.setCoefficient(x, 1);
        constraint.setCoefficient(y, 1);

        MPObjective objective = solver.objective();
        objective.setCoefficient(x, 1);
        objective.setCoefficient(y, 2);
        objective.setMaximization();

        MPSolver.ResultStatus status = solver.solve();
        assertEquals(MPSolver.ResultStatus.OPTIMAL, status);
        assertEquals(8.0, objective.value(), 1e-6);
        assertEquals(0.0, x.solutionValue(), 1e-6);
        assertEquals(4.0, y.solutionValue(), 1e-6);
    }
}
