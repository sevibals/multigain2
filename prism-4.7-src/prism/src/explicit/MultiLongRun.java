package explicit;

import explicit.rewards.MDPRewards;
import parser.type.TypeBool;
import parser.type.TypeDouble;
import prism.*;
import solvers.LpSolverProxy;
import solvers.SolverProxyInterface;
import strat.MultiLongRunStrategy;
import strat.Strategy;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * This class contains functions used when solving multi-objective mean-payoff (=long run, steady state) problem for MDPs. It provides a LP
 * encoding taken from http://qav.cs.ox.ac.uk/bibitem.php?key=BBC+11 (Two views on Multiple Mean-Payoff Objectives in Markov Decision
 * Processes).
 * <p>
 * Note that we use a bit different notation here and refer to y_s variables as Z, not to confuse them with y_{s,a}.
 *
 * @author vojfor
 */

public class MultiLongRun extends PrismComponent implements IMultiLongRun {
	MDP mdp;
	List<BitSet> mecs;
	ArrayList<MDPRewards> rewards;
	ArrayList<Operator> operators;
	ArrayList<Double> bounds;

	/**
	 * The instance providing access to the LP solver.
	 */
	SolverProxyInterface solver;

	boolean initialised = false;

	/**
	 * Number of continuous variables in the LP instance
	 */
	private int numRealLPVars;

	/**
	 * number of states that are contained in some mec.
	 */
	private int numMecStates;

	private LtlSpecification ltlSpecification;

	private MDP originalMDP;
	/**
	 * LP solver to be used
	 */
	private String method;

	/**
	 * xOffset[i] is the solver's variable (column) for the first action of state i, i.e. for x_{i,0}
	 */
	private int[] xOffsetArr;

	/**
	 * yOffset[i] is the solver's variable (column) for the first action of state i, i.e. for x_{i,0}
	 */
	private int[] yOffsetArr;

	private int deltaVarIndex;

	/**
	 * zIndex[i] is the z variable for the state i (i.e. y_i in LICS11 terminology).
	 */
	private int[] zIndex;

	private List<SteadyStateConstraint> steadyStateSpecification;

	private Double accFrequency;

	private boolean memoryless;

	private boolean strategyComputed;

	private double[] optUnichainSol;

	/**
	 * The default constructor.
	 *
	 * @param mdp       The MDP to work with
	 * @param rewards   Rewards for the MDP
	 * @param operators The operators for the rewards, instances of {@see prism.Operator}
	 * @param bounds    Lower/upper bounds for the rewards, if operators are >= or <=
	 * @param method    Method to use, should be a valid value for {@see PrismSettings.PrismSettings.PRISM_MDP_MULTI_SOLN_METHOD}
	 */
	public MultiLongRun(MDP mdp, ArrayList<MDPRewards> rewards, ArrayList<Operator> operators, ArrayList<Double> bounds,
						List<SteadyStateConstraint> steadyStateSpecification, LtlSpecification ltlSpecification, Double accFrequency, String method) {
		this.mdp = mdp;
		this.rewards = rewards;
		this.operators = operators;
		this.bounds = bounds;
		this.steadyStateSpecification = steadyStateSpecification;
		this.ltlSpecification = ltlSpecification;
		this.accFrequency = accFrequency;
		this.memoryless = accFrequency != null;
		this.method = method;
		this.strategyComputed = false;
		this.originalMDP = ltlSpecification == null ? mdp : ltlSpecification.getMdpProduct().getOriginalModel();
	}

	/**
	 * Creates a new solver instance, based on the argument {@see #method}.
	 *
	 * @throws PrismException If the jar file providing access to the required LP solver is not found.
	 */
	private void initialiseSolver() throws PrismException {

		try {
			Class<?> cl = Class.forName("solvers.GurobiProxy");
		} catch (ClassNotFoundException e) {
			mainLog.printWarning("Switching to explicit engine to allow verification of the formula.");
		}

		try { //below Class.forName throws exception if the required jar is not present
			if (method.equals("Linear programming")) {
				solver = new LpSolverProxy(numRealLPVars,0);
			} else if (method.equals("Gurobi")) {
				Class<?> cl = Class.forName("solvers.GurobiProxy");
				solver = (SolverProxyInterface) cl.getConstructor(int.class, int.class)
						.newInstance(numRealLPVars,0);
			} else
				throw new UnsupportedOperationException("The given method for solving LP programs is not supported: " + method);
		} catch (ClassNotFoundException ex) {
			throw new PrismException(
					"Cannot load the class required for LP solving. Was gurobi.jar file present in compilation time and is it present now?");
		} catch (NoClassDefFoundError e) {
			throw new PrismException(
					"Cannot load the class required for LP solving, it seems that gurobi.jar file is missing. Is GUROBI_HOME variable set properly?");
		} catch (InvocationTargetException e) {
			String append = "";
			if (e.getCause() != null) {
				append = "The message of parent exception is: " + e.getCause().getMessage();
			}

			throw new PrismException("Problem when initialising an LP solver. " +
					"InvocationTargetException was thrown" +
					"\n Message: " + e.getMessage() + "\n" + append);
		} catch (InstantiationException | IllegalAccessException
				 | IllegalArgumentException
				 | NoSuchMethodException
				 | SecurityException e) {
			throw new PrismException("Problem when initialising an LP solver. " +
					"It appears that the JAR file is present, but there is some problem, because the exception of type " + e.getClass()
					.toString() +
					" was thrown. Message: " + e.getMessage());
		}
	}

	/**
	 * computes the set of end components and stores it in {@see #mecs}
	 *
	 * @throws PrismException
	 */
	private void computeMECs() throws PrismException {
		double mecStartTime = System.currentTimeMillis();

		ECComputer ecc = ECComputerDefault.createECComputer(null, mdp);
		ecc.computeMECStates();
		mecs = ecc.getMECStates();

		this.numMecStates = 0;
		for (BitSet b : mecs)
			this.numMecStates += b.cardinality();

		double time = (System.currentTimeMillis() - mecStartTime) / 1000;
		System.out.println("Computing MECs took " + time + " s.");
	}

	/**
	 * The LICS11 paper considers variables y_{s,a}, y_s and x_{s,a}. The solvers mostly access variables just by numbers, starting from 0
	 * or so. We use {@see #getVarY(int, int)}, {@see #getVarZ(int)} and {@see #getVarX(int, int)} to get, for a variable y_{s,a}, y_s and
	 * x_{s,a}, respectively, in the LICS11 sense, a corresponding variable (i.e. column) in the linear program.
	 * <p>
	 * This method does all the required initialisations that are required for the above mentioned methods to work correctly.
	 */
	private void computeOffsets() {
		this.xOffsetArr = new int[mdp.getNumStates()];
		int current = 0;
		for (int i = 0; i < mdp.getNumStates(); i++) {
			boolean isInMEC = false;
			for (BitSet b : this.mecs) {
				if (b.get(i)) {
					isInMEC = true;
					break;
				}
			}
			if (isInMEC) {
				xOffsetArr[i] = current;
				current += mdp.getNumChoices(i);
			} else {
				xOffsetArr[i] = Integer.MIN_VALUE; //so that when used in array, we get exception
			}
		}

		this.yOffsetArr = new int[mdp.getNumStates()];
		for (int i = 0; i < mdp.getNumStates(); i++) {
			yOffsetArr[i] = current;
			current += mdp.getNumChoices(i);
		}

		this.zIndex = new int[mdp.getNumStates()];

		for (int i = 0; i < mdp.getNumStates(); i++) {
			zIndex[i] = (isMECState(i)) ? current++ : Integer.MIN_VALUE;
		}

		if (!this.memoryless) {
			this.numRealLPVars = current;
			return; //end here if we don't do memoryless strategy
		}

		//position of the epsilon variable
		this.deltaVarIndex = current++;
		this.numRealLPVars = current;

	}

	/**
	 * Returns true if the state given is in some MEC.
	 */
	private boolean isMECState(int state) {
		return xOffsetArr[state] != Integer.MIN_VALUE;
	}

	/**
	 * Names all variables, useful for debugging.
	 *
	 * @throws PrismException
	 */
	private void nameLPVars() throws PrismException {
		int current = 0;

		for (int i = 0; i < mdp.getNumStates(); i++) {
			if (isMECState(i)) {
				for (int j = 0; j < mdp.getNumChoices(i); j++) {
					String name = "x" + i + "c" + j;
					solver.setVarName(current + j, name);
					solver.setVarBounds(current + j, 0.0, 1.0);
				}
				current += mdp.getNumChoices(i);
			}
		}

		for (int i = 0; i < mdp.getNumStates(); i++) {

			for (int j = 0; j < mdp.getNumChoices(i); j++) {
				String name = "y" + i + "c" + j;
				solver.setVarName(current + j, name);
				solver.setVarBounds(current + j, 0.0, Double.MAX_VALUE);
			}
			current += mdp.getNumChoices(i);
		}

		for (int i = 0; i < mdp.getNumStates(); i++) {
			if (isMECState(i)) {
				String name = "z" + i;
				solver.setVarName(current, name);
				solver.setVarBounds(current++, 0.0, Double.MAX_VALUE);
			}
		}

		if (!this.memoryless)
			return; //there are no more variables

		solver.setVarName(current++, "delta");
	}

	/**
	 * Adds a row to the linear program, saying "all switching probabilities z must sum to 1". See LICS11 paper for details.
	 *
	 * @throws PrismException
	 */
	private void setZSumToOne() throws PrismException {
		//NOTE: there is an error in the LICS11 paper, we need to sum over MEC states only.
		HashMap<Integer, Double> row = new HashMap<Integer, Double>();

		for (BitSet b : this.mecs) {
			for (int i = 0; i < b.length(); i++) {
				if (b.get(i)) {
					int index = getVarZ(i);
					row.put(index, 1.0);
				}
			}
		}

		solver.addRowFromMap(row, 1.0, SolverProxyInterface.EQ, "sum");
	}

	/**
	 * Returns a hashmap giving a lhs for the equation for a reward {@code idx}. (i,d) in the hashmap says that variable i is multiplied by
	 * d. If key i is not present, it means 0.
	 *
	 * @param idx
	 * @return
	 */
	private HashMap<Integer, Double> getRowForReward(int idx, int mecIdx) {
		HashMap<Integer, Double> row = new HashMap<Integer, Double>();
		for (int state = 0; state < mdp.getNumStates(); state++) {
			if (isMECState(state)) {
				if (mecIdx != -1 && !mecs.get(mecIdx).get(state))
					continue;

				for (int i = 0; i < mdp.getNumChoices(state); i++) {
					int index = getVarX(state, i);
					double val = 0;
					if (row.containsKey(index))
						val = row.get(index);
					int originalState = state;
					if (ltlSpecification != null) {
						originalState = ltlSpecification.getMdpProduct().getModelState(state);
					}
					val += this.rewards.get(idx).getStateReward(originalState);
					val += this.rewards.get(idx).getTransitionReward(originalState, i);
					row.put(index, val);
				}
			}
		}

		return row;
	}

	/**
	 * Adds a row to the linear program saying that reward {@code idx} must have at least/most required value (given in the constructor to
	 * this class)
	 *
	 * @param idx
	 * @return
	 */
	private void setEqnForReward(int idx) throws PrismException {
		int op = 0;
		if (operators.get(idx) == Operator.R_GE) {
			op = SolverProxyInterface.GE;
		} else if (operators.get(idx) == Operator.R_LE) {
			op = SolverProxyInterface.LE;
		} else {//else it's min/max and we don't do anything
			return;
		}

		HashMap<Integer, Double> row = getRowForReward(idx, -1);
		if (this.memoryless) {
			row.put(getVarDelta(), 1.0);
		}
		double bound = this.bounds.get(idx);

		solver.addRowFromMap(row, bound, op, "r" + idx);
	}

	/**
	 * Adds an equation to the linear program saying that for mec {@code mecIds}, the switching probabilities in sum must be equal to the x
	 * variables in sum. See the LICS 11 paper for details.
	 *
	 * @param mecIdx
	 * @throws PrismException
	 */
	private void setEqnForMECLink(int mecIdx) throws PrismException {
		HashMap<Integer, Double> row = new HashMap<Integer, Double>();
		BitSet bs = this.mecs.get(mecIdx);

		for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
			//X
			for (int j = 0; j < mdp.getNumChoices(i); j++) {
				int index = getVarX(i, j);
				row.put(index, 1.0);
			}
			//Z
			int index = getVarZ(i);
			row.put(index, -1.0);
		}

		solver.addRowFromMap(row, 0, SolverProxyInterface.EQ, "m" + mecIdx);
	}

	private void setLtlConstraints() throws PrismException {
		if (ltlSpecification != null) {
			final var lhs = new HashMap<Integer, Double>();
			final var ltlThreshold = ltlSpecification.getLtlThreshold();

			final var acceptingMecs = acceptingMecs();

			for (int state = 0; state < mdp.getNumStates(); state++) {
				if (!isMECState(state))
					continue;
				if (notInAmec(acceptingMecs, state)) {
					for (int action = 0; action < mdp.getNumChoices(state); action++) {
						lhs.put(getVarX(state, action), 1.0);
					}
				}
			}
			solver.addRowFromMap(lhs, 1-ltlThreshold, SolverProxyInterface.LE, "ltl");
		}
	}

	private List<BitSet> acceptingMecs() {
		final var mdpProduct = ltlSpecification.getMdpProduct();
		return mecs.stream().filter(mec -> mdpProduct.getAcceptance().isBSCCAccepting(mec)).collect(Collectors.toList());
	}

	private boolean notInAmec(List<BitSet> amecs, int state) {
		return amecs.stream()
				.noneMatch(amec -> amec.get(state));
	}

	private boolean inAmec(List<BitSet> amecs, int state) {
		return amecs.stream()
				.anyMatch(amec -> amec.get(state));
	}

	/**
	 * These are the variables y_{state,action} from the paper. See {@see #computeOffsets()} for more details.
	 *
	 * @param state
	 * @param action
	 * @return
	 */
	private int getVarX(int state, int action) {
		return xOffsetArr[state] + action;
	}

	/**
	 * These are the variables x_{state,action} from the paper. See {@see #computeOffsets()} for more details.
	 *
	 * @param state
	 * @param action
	 * @return
	 */
	private int getVarY(int state, int action) {
		return yOffsetArr[state] + action;
	}

	/**
	 * These are the variables y_state from the paper. See {@see #computeOffsets()} for more details.
	 *
	 * @param state
	 * @return
	 */
	private int getVarZ(int state) {
		return zIndex[state];
	}

	private int getVarDelta() {
		return this.deltaVarIndex;
	}

	/**
	 * Adds all rows to the LP program that give requirements on the steady-state distribution (via x variables)
	 *
	 * @throws PrismException
	 */
	private void setXConstraints() throws PrismException {
		@SuppressWarnings("unchecked")
		HashMap<Integer, HashMap<Integer, Double>> map = new HashMap<Integer, HashMap<Integer, Double>>();

		HashMap<Integer, HashMap<Integer, Double>> normalizingMap = new HashMap<>();
		for (int state = 0; state < mdp.getNumStates(); state++) {
			if (isMECState(state))
				map.put(state, new HashMap<Integer, Double>());
		}

		//outflow
		for (int state = 0; state < mdp.getNumStates(); state++) {
			if (!isMECState(state))
				continue;

			for (int i = 0; i < mdp.getNumChoices(state); i++) {
				int index = getVarX(state, i);
				map.get(state).put(index, -1.0);
			}
		}

		//inflow
		for (int preState = 0; preState < mdp.getNumStates(); preState++) {
			if (!isMECState(preState))
				continue;

			for (int i = 0; i < mdp.getNumChoices(preState); i++) {
				int index = getVarX(preState, i);
				Iterator<Entry<Integer, Double>> it = mdp.getTransitionsIterator(preState, i);
				while (it.hasNext()) {
					Entry<Integer, Double> en = it.next();

					if (!isMECState(en.getKey()))
						continue;

					Map<Integer, Double> row = map.get(en.getKey());
					assert (row != null); //we created mec rows just aboved

					double val = 0;
					if (row.containsKey(index))
						val += map.get(en.getKey()).get(index);

					if (val + en.getValue() != 0)
						row.put(index, val + en.getValue());
					else if (row.containsKey(index))
						row.remove(index);
					//System.out.println("just added " + val + "+" + en.getValue()));
				}
			}
		}

		//fill in
		for (int state : map.keySet()) {
			solver.addRowFromMap(map.get(state), 0, SolverProxyInterface.EQ, "x" + state);
		}
	}

	private void setZXLink() throws PrismException {
		@SuppressWarnings("unchecked")
		HashMap<Integer, HashMap<Integer, Double>> map = new HashMap<Integer, HashMap<Integer, Double>>();
		for (int state = 0; state < mdp.getNumStates(); state++) {
			if (isMECState(state))
				map.put(state, new HashMap<Integer, Double>());
		}

		//outflow
		for (int state = 0; state < mdp.getNumStates(); state++) {
			if (!isMECState(state))
				continue;
			map.get(state).put(getVarZ(state), 1.0);
			for (int i = 0; i < mdp.getNumChoices(state); i++) {
				int index = getVarX(state, i);
				map.get(state).put(index, -1.0);
			}
		}

		//fill in
		double timeLastDebug = System.currentTimeMillis();
		for (int state : map.keySet()) {
			solver.addRowFromMap(map.get(state), 0, SolverProxyInterface.EQ, "zx" + state);
		}
	}

	/**
	 * Adds all rows to the LP program that give requirements on the MEC reaching probability (via y variables)
	 *
	 * @throws PrismException
	 */
	private void setYConstraints() throws PrismException {
		@SuppressWarnings("unchecked")
		HashMap<Integer, Double>[] map = (HashMap<Integer, Double>[]) new HashMap[mdp.getNumStates()];
		for (int state = 0; state < mdp.getNumStates(); state++) {
			map[state] = new HashMap<Integer, Double>();
		}

		int initialState = mdp.getInitialStates().iterator().next();

		for (int state = 0; state < mdp.getNumStates(); state++) {

			//outflow y
			for (int i = 0; i < mdp.getNumChoices(state); i++) {
				int index = getVarY(state, i);
				map[state].put(index, -1.0);
			}

			//outflow z
			if (isMECState(state)) {
				int idx = getVarZ(state);
				map[state].put(idx, -1.0);
			}
		}

		//inflow
		for (int preState = 0; preState < mdp.getNumStates(); preState++) {
			for (int i = 0; i < mdp.getNumChoices(preState); i++) {
				int index = getVarY(preState, i);
				Iterator<Entry<Integer, Double>> it = mdp.getTransitionsIterator(preState, i);
				while (it.hasNext()) {
					Entry<Integer, Double> en = it.next();
					double val = 0;
					if (map[en.getKey()].containsKey(index))
						val += map[en.getKey()].get(index);

					if (val + en.getValue() != 0)
						map[en.getKey()].put(index, val + en.getValue());
					else if (map[en.getKey()].containsKey(index))
						map[en.getKey()].remove(index);
					//System.out.println("just added " + val + "+" + en.getValue()));
				}
			}
		}

		double timeLastDebug = System.currentTimeMillis();
		//fill in

		for (int state = 0; state < mdp.getNumStates(); state++) {
			solver.addRowFromMap(map[state], (initialState == state) ? -1.0 : 0, SolverProxyInterface.EQ, "y" + state);
		}
	}

	/**
	 * Adds all rows to the LP program that give requirements on the steady state specification
	 *
	 * @throws PrismException
	 */
	private void setSteadyStateConstraints() throws PrismException {
		for (var ssc : steadyStateSpecification) {
			BitSet stateSelector = Optional.ofNullable(originalMDP.getLabelStates(ssc.getLabel()))
					.orElseThrow(() -> new PrismException("Label '" + ssc.getLabel() + "' does not exist in MDP"));
			if (ltlSpecification != null) {
				BitSet productStateSelector = new BitSet(mdp.getNumStates());
				for (int i = 0; i < mdp.getNumStates(); i++) {
					if (stateSelector.get(ltlSpecification.getMdpProduct().getModelState(i))) {
						productStateSelector.set(i);
					}
				}
				stateSelector = productStateSelector;
			}

			final var states = new HashMap<Integer, Double>();
			for (int state = 0; state < mdp.getNumStates(); state++) {
				if (stateSelector.get(state) && isMECState(state)) {
					for (int action = 0; action < mdp.getNumChoices(state); action++) {
						states.put(getVarX(state, action), 1.0);
					}
				}
			}
			int solverOp;
			switch (ssc.getOp()) {
				case GEQ:
					solverOp = SolverProxyInterface.GE;
					if (this.memoryless) {
						states.put(getVarDelta(), 1.0);
					}
					break;
				case LEQ:
					solverOp = SolverProxyInterface.LE;
					if (this.memoryless) {
						states.put(getVarDelta(), -1.0);
					}
					break;
				default:
					throw new PrismException(String.format("The operand \"%s\" is currently not supported for steady state constraints.", ssc.getOp().toString()));
			}

			solver.addRowFromMap(states, ssc.getBound(), solverOp, ssc.getLabel() + "-steady-state-" + (solverOp == SolverProxyInterface.GE ? "lb" : "ub"));
		}
	}

	private void setAcceptanceFrequency() {
		if (ltlSpecification == null) {
			return;
		}
		final var lhs = new HashMap<Integer, Double>();
		final var acceptingMecs = acceptingMecs();
		final var mdpProduct = ltlSpecification.getMdpProduct();

		for (int state = 0; state < mdp.getNumStates(); state++) {
			if (inAmec(acceptingMecs, state)) {
				var stateVector = new BitSet(mdp.getNumStates());
				stateVector.set(state);
				if (mdpProduct.getAcceptance().isBSCCAccepting(stateVector)) {
					for (int action = 0; action < mdp.getNumChoices(state); action++) {
						lhs.put(getVarX(state, action), 1.0);
					}
				}
			}
		}
		solver.addRowFromMap(lhs, 1 / accFrequency, SolverProxyInterface.GE, "memoryless_constraint");
	}

	/**
	 * Initialises the solver and creates a new instance of multi-longrun LP, for the parameters given in constructor. Objective function is
	 * not set at all, no matter if it is required.
	 *
	 * @throws PrismException
	 */
	public void createMultiLongRunProgram() throws PrismException {
		if (!initialised) {
			System.out.println("Computing end components.");
			computeMECs();
			System.out.println("Finished computing end components.");
			computeOffsets();
			initialised = true;
		}

		if (this.memoryless && this.accFrequency == null) {
			throw new PrismException("Accepting Frequency not specified.");
		}

		double solverStartTime = System.currentTimeMillis();

		initialiseSolver();

		nameLPVars();

		//Transient flow
		setYConstraints();
		//Recurrent flow
		setXConstraints();
		//Ensuring everything reaches an end-component
		setZSumToOne();

		setSteadyStateConstraints();

		setLtlConstraints();

		if (this.memoryless) {
			setAcceptanceFrequency();
		}
		//Linking the two kinds of flow
		for (int i = 0; i < this.mecs.size(); i++) {
			setEqnForMECLink(i);
		}

		//Reward bounds
		for (int i = 0; i < this.rewards.size(); i++) {
			setEqnForReward(i);
		}

		double time = (System.currentTimeMillis() - solverStartTime) / 1000;
		System.out.println("LP problem construction finished in " + time + " s.");

		strategyComputed = false;
	}

	/**
	 * Solves the multiobjective problem for constraint only, or numerical (i.e. no Pareto)
	 *
	 * @return
	 * @throws PrismException
	 */
	public StateValues solveDefault() throws PrismException {
		boolean isConstraintOnly = true;

		//Reward bounds
		for (int i = 0; i < this.rewards.size(); i++) {
			if (operators.get(i) != Operator.R_MAX && operators.get(i) != Operator.R_MIN) {
				continue;
			}

			if (!isConstraintOnly) {
				throw new PrismException("Incorrect call to solveDefault (multiple numerical objectives)"); //TODO better exception type
			}

			HashMap<Integer, Double> row = getRowForReward(i, -1);
			solver.setObjFunct(row, operators.get(i) == Operator.R_MAX);
			isConstraintOnly = false;
		}

        /*System.out.println("LP variables: " + solver.getNcolumns());
		System.out.println("LP constraints: " + solver.getNrows());*/

		double solverStartTime = System.currentTimeMillis();

		solver.solve();

		double time = (System.currentTimeMillis() - solverStartTime) / 1000;
		System.out.println("LP solving took " + time + " s.");
		System.out.println(solver.getBoolResult());

		//solver.printModel();

		strategyComputed = solver.getBoolResult();

		if (isConstraintOnly) {//We should return bool type
			StateValues sv = new StateValues(TypeBool.getInstance(), originalMDP);
			sv.setBooleanValue(originalMDP.getFirstInitialState(), solver.getBoolResult());
			return sv;
		} else {
			StateValues sv = new StateValues(TypeDouble.getInstance(), originalMDP);
			sv.setDoubleValue(originalMDP.getFirstInitialState(), solver.getDoubleResult());
			return sv;
		}
	}

	/**
	 * Solves the memoryless multiobjective problem for constraint only, or numerical (i.e. no Pareto)
	 *
	 * @return
	 * @throws PrismException
	 */
	public StateValues solveMemoryless() throws PrismException {
		//Reward bounds
		for (int i = 0; i < this.rewards.size(); i++) {
			if (operators.get(i) != Operator.R_MAX && operators.get(i) != Operator.R_MIN) {
				continue;
			} else throw new PrismException(
					"Memoryless problem cannot be solved for numerical objectives (Rmin/Rmax)"); //TODO better exception type
		}

		HashMap<Integer, Double> deltaObj = new HashMap<Integer, Double>();
		deltaObj.put(getVarDelta(), 1.0);
		solver.setObjFunct(deltaObj, false);

		double solverStartTime = System.currentTimeMillis();

		solver.solve();
		double time = (System.currentTimeMillis() - solverStartTime) / 1000;
		System.out.println("LP solving took " + time + " s.");

		strategyComputed = solver.getBoolResult();

		StateValues sv = new StateValues(TypeDouble.getInstance(), originalMDP);
		sv.setDoubleValue(originalMDP.getFirstInitialState(), solver.getDoubleResult());
		return sv;
	}

	/**
	 * Recalculates the transient run, such that disconnected clique of the recurrent behaviour is reached.
	 *
	 * @return
	 */
	private double[] recalcTransRun(double[] currSol) {
		strategyComputed = false;

		if(!initialised) {
			System.out.println("Computing end components for transient behaviour recalculation.");
			computeMECs();
			System.out.println("Finished computing end components for transient behaviour recalculation.");
			computeOffsets();
			initialised = true;
		}

		initialiseSolver();
		nameLPVars();

		//Transient flows
		setYConstraints();

		//Fix the switching probability of each MEC state equal to its frequency in the recurrent run
		for (int i = 0; i < mdp.getNumStates(); i++) {
			if(isMECState(i)) {
				var lhs = new HashMap<Integer, Double>();
				lhs.put(getVarZ(i), 1.0);

				double rhs = 0;
				for (int j = 0; j < mdp.getNumChoices(i); j++) {
					rhs += currSol[getVarX(i,j)];
				}
				solver.addRowFromMap(lhs, rhs, SolverProxyInterface.EQ, "xf" + i);
			}
		}

		solver.solve();

		return solver.getVariableValues();
	}

	/**
	 * Returns the strategy for the last call to solve*(), or null if it was never called before, or if the strategy did not exist.
	 *
	 * @return the strategy
	 */
	public Strategy getStrategy() throws PrismException {
		if (!strategyComputed) {
			throw new PrismException("No solution for the model found, thus no strategy exists.\nPlease notice that the algorithm can not compute a strategy for a pareto query.");
		}
		double[] resCo = optUnichainSol != null ? optUnichainSol : solver.getVariableValues();
		double[] resStrat = recalcTransRun(resCo);

		int numStates = this.mdp.getNumStates();
		Distribution[] transDistr = new Distribution[numStates];
		Distribution[] recDistr = new Distribution[numStates];
		double[] switchProb = new double[numStates];

		for (int i = 0; i < numStates; i++) {
			double transSum = 0;
			double recSum = 0;
			for (int j = 0; j < this.mdp.getNumChoices(i); j++) {
				transSum += resStrat[getVarY(i, j)];
				if (isMECState(i))
					recSum += resCo[getVarX(i, j)];
			}

			double switchExp = 0.0;
			if (isMECState(i))
				switchExp = resStrat[getVarZ(i)];

			if (transSum > 0)
				transDistr[i] = new Distribution();
			if (recSum > 0)
				recDistr[i] = new Distribution();

			if (switchExp > 0)
				switchProb[i] = switchExp / (transSum + switchExp);
			for (int j = 0; j < this.mdp.getNumChoices(i); j++) {
				if (transDistr[i] != null)
					if (resStrat[getVarY(i, j)] > 0) {
						transDistr[i].add(j, resStrat[getVarY(i, j)] / (transSum + switchExp));
					}
				if (recDistr[i] != null)
					if (resCo[getVarX(i, j)] > 0) {
						recDistr[i].add(j, resCo[getVarX(i, j)] / recSum);
					}
			}
		}

		MultiLongRunStrategy strat = new MultiLongRunStrategy(new MDPSparse(mdp), transDistr, switchProb, recDistr);
		return strat;
	}

	/**
	 * For the given weights, get a point p on that is maximal for these weights, i.e. there is no p' with p.weights<p'.weights.
	 *
	 * @throws PrismException
	 */
	public Point solveMulti(Point weights) throws PrismException {
		HashMap<Integer, Double> weightedMap = new HashMap<>();
		//Reward bounds
		int numCount = 0;
		ArrayList<Integer> numIndices = new ArrayList<Integer>();
		for (int i = 0; i < this.rewards.size(); i++) {
			if (operators.get(i) == Operator.R_MAX) {
				HashMap<Integer, Double> map = getRowForReward(i, -1);
				for (Entry<Integer, Double> e : map.entrySet()) {
					double val = 0;
					if (weightedMap.containsKey(e.getKey()))
						val = weightedMap.get(e.getKey());
					weightedMap.put(e.getKey(), val + (weights.getCoord(numCount) * e.getValue()));
				}
				numCount++;
				numIndices.add(i);
			} else if (operators.get(i) == Operator.R_MIN) {
				throw new PrismException(
						"Only maximising rewards in Pareto curves are currently supported (note: you can multiply your rewards by 1 and change min to max"); //TODO min
			}
		}

		solver.setObjFunct(weightedMap, true);

		var result = solver.solve();
		double[] resultVars = solver.getVariableValues();

		Point p = new Point(weights.getDimension());

		if (result == SolverProxyInterface.LpResult.OPTIMAL) {

			for (int i = 0; i < weights.getDimension(); i++) {
				double res = 0;
				HashMap<Integer, Double> rewardEqn = getRowForReward(numIndices.get(i), -1);
				for (int j = 0; j < resultVars.length; j++) {
					if (rewardEqn.containsKey(j)) {
						res += rewardEqn.get(j) * resultVars[j];
					}
				}
				p.setCoord(i, res);
			}
		} else {
			throw new PrismException("Unexpected result of LP solving: " + result.name());
		}

		this.strategyComputed = false;

		return p;
	}

	/**
	 * Solves the multiobjective problem for AMEC for constraint only, or numerical (i.e. no Pareto)
	 *
	 * @return
	 * @throws PrismException
	 */
	public StateValues solveUnichain() throws PrismException {
		String constraintName = "fixunichain";
		boolean isConstraintOnly = true;
		boolean minimize = false;
		strategyComputed = false;

		StateValues sv = new StateValues(TypeBool.getInstance(), originalMDP);
		//Reward bounds
		for (int i = 0; i < this.rewards.size(); i++) {
			if (operators.get(i) != Operator.R_MAX && operators.get(i) != Operator.R_MIN) {
				continue;
			}

			if (!isConstraintOnly) {
				throw new PrismException("Incorrect call to solveUnichain (multiple numerical objectives)");
			}
			sv = new StateValues(TypeDouble.getInstance(), originalMDP);
			if (operators.get(i) == Operator.R_MIN) {
				minimize = true;
				sv.setDoubleValue(originalMDP.getFirstInitialState(), Double.MAX_VALUE);
			}
			isConstraintOnly = false;
		}

		List<BitSet> amecs = new ArrayList<>();
		if (ltlSpecification == null) {
			for (BitSet mec : mecs) {
				amecs.add((BitSet) mec.clone());
			}
		} else {
			amecs = acceptingMecs();
		}

		for ( BitSet amec : amecs) {
			BitSet statesNotInAmec = (BitSet) amec.clone();
			statesNotInAmec.flip(0, mdp.getNumStates());
			// narrow model down to amec
			fixRecurrentVariables(statesNotInAmec, constraintName);

			StateValues currUnichain = solveDefault();

			if (isConstraintOnly) {
				if (solver.getBoolResult()) {
					strategyComputed = true;
					return currUnichain;
				}
			} else {
				if (minimize) {
					if (solver.getDoubleResult() < sv.getDoubleArray()[originalMDP.getFirstInitialState()]){
						sv.setDoubleValue(originalMDP.getFirstInitialState(), solver.getDoubleResult());
						strategyComputed = true;
						// Save the (current) best unichain solution for policy synthesis (getStrategy)
						optUnichainSol = solver.getVariableValues();
					}

				} else {
					if (solver.getDoubleResult() > sv.getDoubleArray()[originalMDP.getFirstInitialState()]){
						sv.setDoubleValue(originalMDP.getFirstInitialState(), solver.getDoubleResult());
						strategyComputed = true;
						// Save the (current) best unichain solution for policy synthesis (getStrategy)
						optUnichainSol = solver.getVariableValues();
					}
				}
			}

			solver.removeRowByName(constraintName);
		}
		return sv;
	}

	private void fixRecurrentVariables(BitSet stateSet, String constraintName) {
		final var lhs = new HashMap<Integer, Double>();
		for (int i = stateSet.nextSetBit(0); i >= 0; i = stateSet.nextSetBit(i+1)) {
			if (!isMECState(i)) {
				continue;
			}
			for (int j = 0; j < mdp.getNumChoices(i); j++) {
				lhs.put(getVarX(i, j), 1.0);
			}
		}
		solver.addRowFromMap(lhs, 0, SolverProxyInterface.EQ, constraintName);
	}
}
