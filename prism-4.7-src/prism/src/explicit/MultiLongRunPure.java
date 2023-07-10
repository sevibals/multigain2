package explicit;

import explicit.rewards.MDPRewards;
import parser.type.TypeBool;
import parser.type.TypeDouble;
import prism.Pair;
import prism.PrismComponent;
import prism.PrismException;
import solvers.LpSolverProxy;
import solvers.SolverProxyInterface;
import strat.MDStrategyArray;
import strat.Strategy;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

public class MultiLongRunPure extends PrismComponent implements IMultiLongRun {

	/**
	 * The models original MDP
	 */
	MDP mdp;

	/**
	 * The product MDP corresponding to the @ltlSpecification
	 */
	MDP mdpProductModel;

	ArrayList<MDPRewards> rewards;

	LTLModelChecker.LTLProduct<MDP> mdpProduct;

	List<BitSet> amecs;

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
	 * Number of binary in the LP instance
	 */
	private int numBinaryLPVars;

	/**
	 * number of states that are contained in some mec.
	 */
	private int numMecStates;

	private LtlSpecification ltlSpecification;
	/**
	 * LP solver to be used
	 */
	private String method;

	/**
	 * xOffset[i] is the solver's variable (column) for the first action of state i, i.e. for x_{i,0}
	 */
	private int[] xOffsetArr;

	/**
	 * fOffset[i] is the solver's variable (column) for the flow between variables
	 */
	private Map<Pair<Integer, Integer>, Integer> fOffsetMap;

	/**
	 * piOffset[i] is the solver's variable (column) for the deterministic policy restriction
	 */
	private int[] piOffsetArr;

	private String[] piNames;

	/**
	 * isqOffsetArr[i] is the solver's variable indicator variable I_sq
	 */
	private int[] isqOffsetArr;

	/**
	 * ikOffsetArr[i] is the solver's variable indicator variable I^k
	 */
	private int[] ikOffsetArr;

	/**
	 * iskOffsetArr[i] is the solver's variable indicator variable I_s^k
	 */
	private int[] iskOffsetArr;

	/**
	 * isOffsetArr[i] is the solver's variable indicator variable I_s^k
	 */
	private int[] isOffsetArr;

	private List<SteadyStateConstraint> steadyStateSpecification;

	private double epsilon;

	boolean strategyComputed;

	/**
	 * The default constructor.
	 *
	 * @param mdp                      The MDP to work with
	 * @param steadyStateSpecification
	 * @param ltlSpecification
	 * @param method                   Method to use, should be a valid value for {@see PrismSettings.PrismSettings.PRISM_MDP_MULTI_SOLN_METHOD}
	 */
	public MultiLongRunPure(MDP mdp, ArrayList<MDPRewards> rewards, List<SteadyStateConstraint> steadyStateSpecification, LtlSpecification ltlSpecification, String method) {
		this.mdp = mdp;
		this.rewards = rewards;
		this.mdpProductModel = ltlSpecification.getMdpProduct().getProductModel();
		this.mdpProduct = ltlSpecification.getMdpProduct();
		this.steadyStateSpecification = steadyStateSpecification;
		this.ltlSpecification = ltlSpecification;
		this.method = method;
		this.epsilon = 1E-5;
		this.strategyComputed = false;
	}

	// TODO copied from MultiLongRun (refactor?)

	/**
	 * Creates a new solver instance, based on the argument {@see #method}.
	 *
	 * @throws PrismException If the jar file providing access to the required LP solver is not found.
	 */
	private void initialiseSolver() throws PrismException {
		try { //below Class.forName throws exception if the required jar is not present
			if (method.equals("Linear programming")) {
				//create new solver
				solver = new LpSolverProxy(this.numRealLPVars, this.numBinaryLPVars);
			} else if (method.equals("Gurobi")) {
				Class<?> cl = Class.forName("solvers.GurobiProxy");
				solver = (SolverProxyInterface) cl.getConstructor(int.class, int.class)
						.newInstance(this.numRealLPVars, this.numBinaryLPVars);
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
	 * Initialises the solver and creates a new instance of multi-longrun MILP, for the parameters given in constructor. Objective function is
	 * not set at all, no matter if it is required.
	 *
	 * @throws PrismException
	 */
	public void createMultiLongRunProgram() throws PrismException {
		if (!initialised) {
			System.out.println("Computing end components.");
			computeAMECs();
			System.out.println("Finished computing end components.");
			computeOffsets();
			initialised = true;
		}

		double solverStartTime = System.currentTimeMillis();

		initialiseSolver();

		nameLPVars();

		// constraint names follow the enumeration of the paper
		constraintOne();
		constraintTwo();
		constraintThree();
		constraintFour();
		constraintFive();
		constraintSix();
		constraintSeven();
		constraintEight();
		constraintNine();
		constraintTen();
		constraintEleven();
		constraintTwelve();
		constraintThirteen();
		constraintFourteen();
		constraintFifteen();
		constraintSixteen();

		double endtime = (System.currentTimeMillis() - solverStartTime) / 1000;
		System.out.println("MILP problem construction finished in " + endtime + " s.");

		strategyComputed = false;
	}

	/**
	 * computes the set of end components and stores it in {@see #mecs}
	 *
	 * @throws PrismException
	 */
	private void computeAMECs() throws PrismException {
		ECComputer ecc = ECComputerDefault.createECComputer(null, mdpProductModel);
		ecc.computeMECStates();
		var mecs = ecc.getMECStates();

		this.amecs = mecs.stream().filter(mec -> ltlSpecification.getMdpProduct().getAcceptance().isBSCCAccepting(mec)).collect(Collectors.toList());
	}

	/**
	 *
	 */
	private void computeOffsets() {
		// x_sqa
		this.xOffsetArr = new int[mdpProductModel.getNumStates()];
		int current = 0;
		for (int i = 0; i < mdpProductModel.getNumStates(); i++) {
			xOffsetArr[i] = current;
			current += mdpProductModel.getNumChoices(i);
		}

		// f_sqs'q'
		this.fOffsetMap = new HashMap<>();
		for (int i = 0; i < mdpProductModel.getNumStates(); i++) {
			for (int j = 0; j < mdpProductModel.getNumChoices(i); j++) {
				SuccessorsIterator successorStates = mdpProductModel.getSuccessors(i, j);
				while (successorStates.hasNext()) {
					var key = new Pair<>(i, successorStates.nextInt());
					if(!fOffsetMap.containsKey(key)) {
						fOffsetMap.put(key, current++);
					}
				}
			}
		}

		this.numRealLPVars = current;

		// pi(a|s,q) / mirroring the x variables
		this.piOffsetArr = new int[mdpProductModel.getNumStates()];
		for (int i = 0; i < mdpProductModel.getNumStates(); i++) {
			piOffsetArr[i] = current;
			current += mdpProductModel.getNumChoices(i);
		}

		// I_sq
		this.isqOffsetArr = new int[mdpProductModel.getNumStates()];
		for (int i = 0; i < mdpProductModel.getNumStates(); i++) {
			isqOffsetArr[i] = current++;
		}

		// I^k
		this.ikOffsetArr = new int[amecs.size()];
		for (int i = 0; i < amecs.size(); i++) {
			ikOffsetArr[i] = current++;
		}

		// I_s^k
		this.iskOffsetArr = new int[mdp.getNumStates() * amecs.size()];
		for (int i = 0; i < mdp.getNumStates(); i++) {
			iskOffsetArr[i] = current;
			current += amecs.size();
		}

		// I_s
		this.isOffsetArr = new int[mdp.getNumStates()];
		for (int i = 0; i < mdp.getNumStates(); i++) {
			isOffsetArr[i] = current;
			current++;
		}

		this.numBinaryLPVars = current - this.numRealLPVars;
	}

	/**
	 * Names all variables, useful for debugging.
	 *
	 * @throws PrismException
	 */
	private void nameLPVars() throws PrismException {
		int current = 0;

		// x_sqa
		for (int i = 0; i < mdpProductModel.getNumStates(); i++) {
			for (int j = 0; j < mdpProductModel.getNumChoices(i); j++) {
				String name = "x_sq" + i + "_a" + j;
				solver.setVarName(current + j, name);
				solver.setVarBounds(current + j, 0.0, 1.0);
			}
			current += mdpProductModel.getNumChoices(i);
		}

		// f_sqs'q'
		Map<Pair<Integer, Integer>, Integer>  checkMap = new HashMap<>();
		for (int i = 0; i < mdpProductModel.getNumStates(); i++) {
			for (int j = 0; j < mdpProductModel.getNumChoices(i); j++) {
				SuccessorsIterator successorStates = mdpProductModel.getSuccessors(i, j);
				while (successorStates.hasNext()) {
					int nextInt = successorStates.nextInt();
					var key = new Pair<>(i, nextInt);
					if(!checkMap.containsKey(key)) {
						checkMap.put(key, current);
						String name = "f_sq" + i + "_s'q'" + nextInt;
						solver.setVarName(current, name);
						int upper = i == nextInt ? 0 : 1;
						solver.setVarBounds(current, 0.0, upper);
						current++;
					}
				}
			}
		}

		// pi(a|s,q) / mirroring the x variables
		piNames = new String[isqOffsetArr[0] - piOffsetArr[0]];
		int piInd = 0;
		for (int i = 0; i < mdpProductModel.getNumStates(); i++) {
			for (int j = 0; j < mdpProductModel.getNumChoices(i); j++) {
				String name = "pi_sq" + i + "_a" + j;
				solver.setVarName(current + j, name);
				solver.setVarBounds(current + j, 0.0, 1.0);
				piNames[piInd++] = name;
			}
			current += mdpProductModel.getNumChoices(i);
		}

		// I_sq
		for (int i = 0; i < mdpProductModel.getNumStates(); i++) {
			String name = "I_sq" + i;
			solver.setVarName(current + i, name);
			solver.setVarBounds(current + i, 0.0, 1.0);
		}
		current += mdpProductModel.getNumStates();

		// I^k
		for (int i = 0; i < amecs.size(); i++) {
			String name = "I_k" + i;
			solver.setVarName(current + i, name);
			solver.setVarBounds(current + i, 0.0, 1.0);
		}
		current += amecs.size();

		// I_s^k
		for (int i = 0; i < mdp.getNumStates(); i++) {
			for (int k = 0; k < amecs.size(); k++) {
				String name = "I_s" + i + "_k" + k;
				solver.setVarName(current + k, name);
				solver.setVarBounds(current + k, 0.0, 1.0);
			}
			current += amecs.size();
		}

		// I_s
		for (int i = 0; i < mdp.getNumStates(); i++) {
			String name = "I_s" + i;
			solver.setVarName(current + i, name);
			solver.setVarBounds(current + i, 0.0, 1.0);
		}
	}

	private void constraintOne() throws PrismException {
		final var constraintMap = new HashMap<Integer, Map<Integer, Double>>();
		for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
			for (int action = 0; action < mdpProductModel.getNumChoices(state); action++) {
				final int a = action;
				mdpProductModel.forEachTransition(state, action, (s, s_prime, d) -> {
					nestedPutOrAdd(constraintMap, s_prime, getVarX(s, a), d);
				});
				nestedPutOrAdd(constraintMap, state, getVarX(state, action), -1.);
			}
		}

		for (Map.Entry<Integer, Map<Integer, Double>> entry : constraintMap.entrySet()) {
			solver.addRowFromMap(entry.getValue(), 0, SolverProxyInterface.EQ, "constraint1_" + entry.getKey());
		}
	}

	private void constraintTwo() throws PrismException {
		final var map = new HashMap<Integer, Double>();
		for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
			for (int action = 0; action < mdpProductModel.getNumChoices(state); action++) {
				map.put(getVarX(state, action), 1.);
			}
		}
		solver.addRowFromMap(map, 1.0, SolverProxyInterface.EQ, "constraint2");
	}

	private void constraintThree() throws PrismException {
		for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
			for (int action = 0; action < mdpProductModel.getNumChoices(state); action++) {
				solver.addRowFromMap(Map.of(
						getVarX(state, action), 1.,
						getVarPi(state, action), -1.
				), 0., SolverProxyInterface.LE, "constraint3_" + state + "_" + action);
			}
		}
	}

	private void constraintFour() throws PrismException {
		for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
			final var map = new HashMap<Integer, Double>();
			for (int action = 0; action < mdpProductModel.getNumChoices(state); action++) {
				map.put(getVarPi(state, action), 1.);
			}
			solver.addRowFromMap(map, 1.0, SolverProxyInterface.EQ, "constraint4_state" + state);
		}
	}

	private void constraintFive() throws PrismException {
		final var constraintMap = new HashMap<Pair<Integer, Integer>, Map<Integer, Double>>();
		for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
			for (int action = 0; action < mdpProductModel.getNumChoices(state); action++) {
				final int a = action;
				mdpProductModel.forEachTransition(state, action, (s, s_prime, d) -> {
					if (s != s_prime) {
						nestedPut(constraintMap, pair(s, s_prime), getVarPi(s, a), -1 * d);
					}
				});
			}
		}

		for (Map.Entry<Pair<Integer, Integer>, Map<Integer, Double>> entry : constraintMap.entrySet()) {
			final var statePair = entry.getKey();
			final var constraint = entry.getValue();
			final var varF = getVarF(statePair);
			if (varF.isPresent()) {
				constraint.put(varF.get(), 1.);
				solver.addRowFromMap(constraint, 0.0, SolverProxyInterface.LE, "constraint5_" + statePair);
			} else {
				throw new PrismException("Illegal access to flow variable");
			}
		}
	}

	private void constraintSix() throws PrismException {
		boolean initStateVisited = false;
		for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
			if (mdp.isInitialState(mdpProduct.getModelState(state)) && !initStateVisited) {
                //TODO balsse Adjust so that the correct (not first) initial state is skipped
				initStateVisited = true;
				continue;
			}
			final var constraint = new HashMap<Integer, Double>();
			constraint.put(getVarIsq(state), -epsilon);
			for (int state_prime = 0; state_prime < mdpProductModel.getNumStates(); state_prime++) {
				//Outgoing flow
				getVarF(state, state_prime).ifPresent(varF -> putOrAdd(constraint, varF, -1.));
				//Incoming flow
				getVarF(state_prime, state).ifPresent(varF -> putOrAdd(constraint, varF, 1.));
			}
			solver.addRowFromMap(constraint, 0., SolverProxyInterface.GE, "constraint6_state_" + state);
		}
	}

	private void constraintSeven() throws PrismException {
		for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
			final var constraint = new HashMap<Integer, Double>();
			constraint.put(getVarIsq(state), -1.);
			for (int state_prime = 0; state_prime < mdpProductModel.getNumStates(); state_prime++) {
				//ignore self-transitions for flow variables
				if (state != state_prime) {
					getVarF(state_prime, state).ifPresent(varF -> constraint.put(varF, 1.));
				}
			}
			solver.addRowFromMap(constraint, 0., SolverProxyInterface.LE, "constraint7_state_" + state);
		}
	}

	private void constraintEight() throws PrismException {
		for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
			final var constraint = new HashMap<Integer, Double>();
			for (int state_prime = 0; state_prime < mdpProductModel.getNumStates(); state_prime++) {
				//ignore self-transitions for flow variables
				if (state != state_prime) {
					getVarF(state, state_prime).ifPresent(varF -> putOrAdd(constraint, varF, 1.));
					getVarF(state_prime, state).ifPresent(varF -> putOrAdd(constraint, varF, -0.5));
				}
			}
			solver.addRowFromMap(constraint, 0., SolverProxyInterface.GE, "constraint8_state_" + state);
		}
	}

	private void putOrAdd(HashMap<Integer, Double> map, Integer key, Double value) {
		var newValue = Optional.ofNullable(map.get(key)).orElse(0.) + value;
		map.put(key, newValue);
	}
	private void constraintNine() throws PrismException {
		for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
			final var constraint = new HashMap<Integer, Double>();
			constraint.put(getVarIsq(state), -1.);
			for (int action = 0; action < mdpProductModel.getNumChoices(state); action++) {
				constraint.put(getVarX(state, action), 1.);
			}
			solver.addRowFromMap(constraint, 0., SolverProxyInterface.LE, "constraint9_" + state);
		}
	}

	private void constraintTen() throws PrismException {
		for (var ssc : steadyStateSpecification) {
			final var stateSelector = Optional.ofNullable(mdpProductModel.getLabelStates(ssc.getLabel()))
					.orElseThrow(() -> new PrismException("Label '" + ssc.getLabel() + "' does not exist in MDP"));

			final var states = new HashMap<Integer, Double>();
			for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
				if (stateSelector.get(state)) {
					for (int action = 0; action < mdpProductModel.getNumChoices(state); action++) {
						states.put(getVarX(state, action), 1.0);
					}
				}
			}
			int solverOp;
			switch (ssc.getOp()) {
				case EQ:
					solverOp = SolverProxyInterface.EQ;
					break;
				case GEQ:
					solverOp = SolverProxyInterface.GE;
					break;
				case LEQ:
					solverOp = SolverProxyInterface.LE;
					break;
				default:
					throw new PrismException(String.format("The operand \"%s\" is currently not supported for steady state constraints.", ssc.getOp().toString()));
			}

			solver.addRowFromMap(states, ssc.getBound(), solverOp, ssc.getLabel() + "-steady-state-" + ssc.getOp());
		}
	}

	private void constraintEleven() throws PrismException {
		if (ltlSpecification != null) {
			final var lhs = new HashMap<Integer, Double>();
			//final var ltlThreshold = ltlSpecification.getLtlThreshold();

			for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
				BitSet stateVector = new BitSet();
				stateVector.set(state);
				if (mdpProduct.getAcceptance().isBSCCAccepting(stateVector)) {
					for (int action = 0; action < mdpProductModel.getNumChoices(state); action++) {
						lhs.put(getVarX(state, action), 1.0);
					}
				}
			}
			solver.addRowFromMap(lhs, epsilon, SolverProxyInterface.GE, "ltl");
		}
	}

	private void constraintTwelve() throws PrismException {
		for (int amec = 0; amec < amecs.size(); amec++) {
			final var lhs = new HashMap<Integer, Double>();
			amecs.get(amec).stream().forEach(productState -> {
				for (int action = 0; action < mdpProductModel.getNumChoices(productState); action++) {
					lhs.put(getVarX(productState, action), 1.);
				}
			});
			lhs.put(getVarIk(amec), -1.);
			solver.addRowFromMap(lhs, 0, SolverProxyInterface.LE, "constraint_12_amec_" + amec);
		}
	}

	private void constraintThirteen() throws PrismException {
		for (int amec = 0; amec < amecs.size(); amec++) {
			final var lhsMap = new HashMap<Integer, Map<Integer, Double>>();
			amecs.get(amec).stream().forEach(productState -> {
				for (int originalState = 0; originalState<mdp.getNumStates(); originalState++) {
					nestedPut(lhsMap, originalState, getVarIsq(productState), -1.);
				}
			});

			for(Map.Entry<Integer, Map<Integer, Double>> entry: lhsMap.entrySet()) {
				final var originalState = entry.getKey();
				final var lhs = entry.getValue();
				lhs.put(getVarIsk(originalState, amec), 1.);
				solver.addRowFromMap(lhs, 0, SolverProxyInterface.LE, "constraint_13_amec_" + amec + "_state_" + originalState);
			}
		}
	}

	private void constraintFourteen() throws PrismException {
		for (int amec = 0; amec < amecs.size(); amec++) {
			final var lhsMap = new HashMap<Integer, Map<Integer, Double>>();
			amecs.get(amec).stream().forEach(productState -> {
				for (int originalState = 0; originalState<mdp.getNumStates(); originalState++) {
					nestedPut(lhsMap, originalState, getVarIsq(productState), 1. / mdpProduct.getAutomataSize());
				}
			});

			for(Map.Entry<Integer, Map<Integer, Double>> entry: lhsMap.entrySet()) {
				final var originalState = entry.getKey();
				final var lhs = entry.getValue();
				lhs.put(getVarIsk(originalState, amec), -1.0);
				solver.addRowFromMap(lhs, 0, SolverProxyInterface.LE, "constraint_14_amec_" + amec + "_state_" + originalState);
			}
		}
	}

	private void constraintFifteen() throws PrismException {
		for (int originalState = 0; originalState < mdp.getNumStates(); originalState++) {
			final var lhs = new HashMap<Integer, Double>();
			for (int amec = 0; amec < amecs.size(); amec++) {
				lhs.put(getVarIsk(originalState, amec), -1. / amecs.size());
				lhs.put(getVarIk(amec), 1. / amecs.size());
			}
			lhs.put(getVarIs(originalState), 1.);
			solver.addRowFromMap(lhs, 1, SolverProxyInterface.LE, "constraint_15_state_" + originalState);
		}
	}

	private void constraintSixteen() throws PrismException {
		final var lhs = new HashMap<Integer, Double>();
		for (int originalState = 0; originalState < mdp.getNumStates(); originalState++) {
			lhs.put(getVarIs(originalState), 1.);
		}
		solver.addRowFromMap(lhs, 1, SolverProxyInterface.GE, "constraint_16");
	}

	private <K1, K2, V> void nestedPut(final Map<K1, Map<K2, V>> map, final K1 key1, final K2 key2, final V value) {
		Optional.ofNullable(map.get(key1)).ifPresentOrElse(
				nestedMap -> nestedMap.put(key2, value),
				() -> map.put(key1, new HashMap<>(Map.of(key2, value)))
		);
	}

	private <K1, K2, V> void nestedPutOrAdd(final Map<K1, Map<K2, Double>> map,  final K1 key1, final K2 key2, final double value) {
		Optional.ofNullable(map.get(key1)).ifPresentOrElse(
				nestedMap -> nestedMap.put(key2, Optional.ofNullable(nestedMap.get(key2)).orElse(0.) + value),
				() -> map.put(key1, new HashMap<>(Map.of(key2, value)))
		);
	}

	private boolean inAmec(List<BitSet> amecs, int state) {
		return amecs.stream()
				.anyMatch(amec -> amec.get(state));
	}

	private <S, T> Pair<S, T> pair(final S s, final T t) {
		return new Pair<>(s, t);
	}

	private double getReward(int rewardId, int state, int choice) {
		final var mdpState = mdpProduct.getModelState(state);
		var val = 0.;
		val += rewards.get(rewardId).getTransitionReward(mdpState, choice);
		val += rewards.get(rewardId).getStateReward(mdpState);
		return val;
	}

	public StateValues solveDefault() throws PrismException {
		solver.setObjFunct(new HashMap<Integer, Double>(), true);
		boolean noRewards = true;
		for (int i = 0; i < this.rewards.size(); i++) {
			/**
			 if (operators.get(i) != Operator.R_MAX && operators.get(i) != Operator.R_MIN) {
			 continue;
			 }

			 if (!isConstraintOnly) {
			 throw new PrismException("Incorrect call to solveDefault (multiple numerical objectives)"); //TODO better exception type
			 }
			 **/
			noRewards = false;
			final var row = new HashMap<Integer, Double>();
			for (int state = 0; state < mdpProductModel.getNumStates(); state++) {
				for (int action = 0; action < mdpProductModel.getNumChoices(state); action++) {
					final int a = action;
					final var finalI = i;
					final var v = mdpProductModel.sumOverTransitions(state, action, (s, s_prime, d) -> d * getReward(finalI, s, a));
					row.put(getVarX(state, action), v);
				}
			}
			solver.setObjFunct(row, true);
		}

		double solverStartTime = System.currentTimeMillis();

		solver.solve();
		double time = (System.currentTimeMillis() - solverStartTime) / 1000;
		System.out.println("LP solving took " + time + " s.");

		//solver.printModel();

		strategyComputed = solver.getBoolResult();

		if (noRewards) {//We should return bool type
			StateValues sv = new StateValues(TypeBool.getInstance(), mdp);
			sv.setBooleanValue(mdp.getFirstInitialState(), solver.getBoolResult());
			return sv;
		} else {
			StateValues sv = new StateValues(TypeDouble.getInstance(), mdp);
			sv.setDoubleValue(mdp.getFirstInitialState(), solver.getDoubleResult());
			return sv;
		}
	}

	public Strategy getStrategy() throws PrismException {
		if (!strategyComputed) {
			throw new PrismException("No solution for the model found, thus no strategy exists.");
		}

		double[] piValues = Arrays.copyOfRange(solver.getVariableValues(), piOffsetArr[0], isqOffsetArr[0]);
		int[] actionPolicy = new int[mdp.getNumStates()];
		int currModelState = 0;
		int actionsOfProcessedStates = 0;
		for (int i = 0; i < piValues.length; i++) {
			if (i >= actionsOfProcessedStates + mdpProductModel.getNumChoices(currModelState)){
				actionsOfProcessedStates += mdpProductModel.getNumChoices(currModelState);
				currModelState++;
			}
			if (piValues[i] > 0) {
				// We assume that every state has exactly one pi > 0
				actionPolicy[mdpProduct.getModelState(currModelState)] = i - actionsOfProcessedStates;
			}
		}

		MDStrategyArray strat = new MDStrategyArray(mdp, actionPolicy);

		return strat;
	}

	/**
	 * These are the variables x_sqa from the paper. See {@see #computeOffsets()} for more details.
	 *
	 * @param state
	 * @param action
	 * @return
	 */
	private int getVarX(int state, int action) {
		return xOffsetArr[state] + action;
	}

	/**
	 * These are the variables f_sqs'q' from the paper. See {@see #computeOffsets()} for more details.
	 *
	 * @param state
	 * @param successor
	 * @return null if no action between state and successor in product MDP
	 */
	private Optional<Integer> getVarF(int state, int successor) {
		return getVarF(new Pair<>(state, successor));
	}

	/**
	 * These are the variables f_sqs'q' from the paper. See {@see #computeOffsets()} for more details.
	 *
	 * @param statePair the state pair
	 * @return null if no action between state and successor in product MDP
	 */
	private Optional<Integer> getVarF(Pair<Integer, Integer> statePair) {
		return Optional.ofNullable(fOffsetMap.get(statePair));
	}

	/**
	 * These are the variables pi_sqa from the paper. See {@see #computeOffsets()} for more details.
	 *
	 * @param state
	 * @param action
	 * @return
	 */
	private int getVarPi(int state, int action) {
		return piOffsetArr[mdpProduct.getModelState(state)] + action;
	}

	/**
	 * These are the variables I_sq from the paper. See {@see #computeOffsets()} for more details.
	 *
	 * @param state
	 * @return
	 */
	private int getVarIsq(int state) {
		return isqOffsetArr[state];
	}

	/**
	 * These are the variables I^k from the paper. See {@see #computeOffsets()} for more details.
	 *
	 * @param amec
	 * @return
	 */
	private int getVarIk(int amec) {
		return ikOffsetArr[amec];
	}

	/**
	 * These are the variables I_s^k from the paper. See {@see #computeOffsets()} for more details.
	 *
	 * @param state
	 * @param amec
	 * @return
	 */
	private int getVarIsk(int state, int amec) {
		return iskOffsetArr[state] + amec;
	}

	/**
	 * These are the variables I_s from the paper. See {@see #computeOffsets()} for more details.
	 *
	 * @param state
	 * @return
	 */
	private int getVarIs(int state) {
		return isOffsetArr[state];
	}
}
