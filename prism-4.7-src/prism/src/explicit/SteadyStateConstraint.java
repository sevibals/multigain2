package explicit;

import parser.ast.RelOp;

/**
 * Class representing a steady-state constraint for an MDP of the form: l ≤ long time average probability od being in a set of states ≤ u
 * as defined in <a href="https://www.ijcai.org/proceedings/2021/0565.pdf">LTL-Constrained Steady-State Policy Synthesis</a>
 */
public class SteadyStateConstraint {

    /**
     * Label defining the set of states in the MDP (see {@link MDP#getLabelToStatesMap()}
     */
    private final String label;

    /**
     * the lower bound l
     */
    private final Double bound;

    /**
     * the lower bound u
     */
    private final RelOp op;

    public SteadyStateConstraint(final String label, final RelOp op, final Double bound) {
        this.label = label;
        this.op = op;
        this.bound = bound;
    }

    public RelOp getOp() {
        return op;
    }

    public Double getBound() {
        return bound;
    }

    public String getLabel() {
        return label;
    }
}
