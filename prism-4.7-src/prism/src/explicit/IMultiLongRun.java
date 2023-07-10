package explicit;

import strat.Strategy;

public interface IMultiLongRun {

    /**
     * Construct the linear program corresponding to the implemented algorithm.
     */
    public void createMultiLongRunProgram();


    /**
     * Solve the currently constructed program with the underlying LP/MILP solver
     */
    public StateValues solveDefault();

    /**
     * Extract the values from the solver and compute the strategy/policy
     */
    public Strategy getStrategy();
}
