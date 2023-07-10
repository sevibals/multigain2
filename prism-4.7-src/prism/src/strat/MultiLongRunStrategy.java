package strat;

import explicit.*;
import parser.State;
import prism.PrismException;
import prism.PrismLog;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

public class MultiLongRunStrategy implements Strategy, Serializable
{
	public static final long serialVersionUID = 0L;

	// strategy info
	protected String info = "No information available.";

	// storing last state
	protected transient int lastState;

	protected MDPSparse model;

	protected Distribution[] transChoices;

	protected Distribution[] recChoices;

	protected double[] switchProb;
	private transient boolean isTrans; //represents the single bit of memory

	private MultiLongRunStrategy() {
		//for XML serialization by JAXB
	}

	/**
	 *
	 * Creates a multi-long run strategy
	 *
	 * @param transChoices The probability distribution of the transient flow
	 * @param switchProb The probability distribution of switching to the recurrent behaviour
	 * @param recChoices The probability distribution of the recurrent flow
	 */
	public MultiLongRunStrategy(MDPSparse model, Distribution[] transChoices, double[] switchProb, Distribution[] recChoices) {
		this.model = model;
		this.transChoices = transChoices;
		this.switchProb = switchProb;
		this.recChoices = recChoices;
	}

	/**
	 * Creates a ExactValueStrategy.
	 *
	 * @param scan
	 */
	public MultiLongRunStrategy(Scanner scan) {
	}

	private boolean switchToRec(int state) {
		if (this.switchProb == null)
			return this.recChoices[state] != null;
		else
			return Math.random() < this.switchProb[state];
	}

	@Override
	public void initialise(int state) {
		isTrans = !switchToRec(state);
		System.out.println("init to " + isTrans);
		lastState = state;
	}

	public void updateMemory(int action, int state) throws InvalidStrategyStateException {
		if (isTrans) {
			isTrans = !switchToRec(state);
		}
		lastState = state;
	}

	public Model buildPolicyFromMDPExplicit() throws PrismException {
		// construct a new STPG of size three times the original model
		MDPSimple mdp = new MDPSimple(2 * model.getNumStates());

		int n = mdp.getNumStates();

		List<State> oldStates = model.getStatesList();

		// creating helper states
		State stateTran = new State(1), stateRec = new State(1);
		stateTran.setValue(0, 0); // state where finite-memory machine is in transient state
		stateRec.setValue(0, 1); // state where finite-memory machine is in recurrent state

		// creating product state list
		List<State> newStates = new ArrayList<State>(n);
		for (int i = 0; i < oldStates.size(); i++) {
			newStates.add(new State(oldStates.get(i), stateTran));
			newStates.add(new State(oldStates.get(i), stateRec));
		}

		// setting the states list to STPG
		mdp.setStatesList(newStates);

		for (int i = 0; i < oldStates.size(); i++) {
			int tranIndx = 2 * i ;
			int recIndx = 2 * i + 1;

			Distribution distrTranState = new Distribution();
			Distribution distrRecState = new Distribution();
			Distribution distrSwitch = new Distribution();

			Distribution choicesTran = this.transChoices[i] != null ? this.transChoices[i] : new Distribution();
			Distribution choicesRec = this.recChoices[i];

			//recurrent states
			if (choicesRec != null) { //MEC state
				for (Entry<Integer, Double> choiceEntry : choicesRec) {
					Distribution prodChoice = new Distribution();
					Iterator<Entry<Integer, Double>> iterator = model.getTransitionsIterator(i, choiceEntry.getKey());
					while(iterator.hasNext()) {
						Entry<Integer,Double> transitionEntry = iterator.next();
						prodChoice.add(transitionEntry.getKey() * 2 + 1, transitionEntry.getValue());
					}
					Object action = model.getAction(i, choiceEntry.getKey());
					String label = action == null ? "" + choiceEntry.getKey() : action.toString();
					mdp.addActionLabelledChoice(recIndx, prodChoice, "[R]" + label + ":" + choiceEntry.getValue());
				}
			}

			//transient states, switching to recurrent
			if (switchProb[i] > 0) { //MEC state
				Distribution switchChoice = new Distribution();
				switchChoice.add(recIndx, 1.0);
				mdp.addActionLabelledChoice(tranIndx, switchChoice, "[SW]:" + switchProb[i]);
			}

			//transitent states, not switching
			for (Entry<Integer, Double> choiceEntry : choicesTran) {
				Distribution prodChoice = new Distribution();
				Iterator<Entry<Integer, Double>> iterator = model.getTransitionsIterator(i, choiceEntry.getKey());
				while(iterator.hasNext()) {
					Entry<Integer,Double> transitionEntry = iterator.next();
					prodChoice.add(transitionEntry.getKey() * 2, transitionEntry.getValue());
				}
				Object action = model.getAction(i, choiceEntry.getKey());
				String label = action == null ? "" + choiceEntry.getKey() : action.toString();
				mdp.addActionLabelledChoice(tranIndx, prodChoice, "[T]" + label + ":" + choiceEntry.getValue());
			}
		}

		// setting initial state for the game
		for (Integer init : model.getInitialStates()) {
			mdp.addInitialState(2 * init);
		}

		return mdp;
	}

	public String getInfo() {
		return info;
	}

	public void setInfo(String info) {
		this.info = info;
	}

	public int getMemorySize() {
		return (switchProb == null) ? 0 : 2;
	}

	public String getType() {
		return "Stochastic update strategy.";
	}

	public Object getCurrentMemoryElement() {
		return isTrans;
	}

	public void setMemory(Object memory) throws InvalidStrategyStateException {
		if (memory instanceof Boolean) {
			this.isTrans = (boolean) memory;
		} else
			throw new InvalidStrategyStateException("Memory element has to be a boolean.");
	}

	public String getStateDescription() {
		StringBuilder s = new StringBuilder();
		if (switchProb != null) {
			s.append("Stochastic update strategy.\n");
			s.append("Memory size: 2 (transient/recurrent phase).\n");
			s.append("Current memory element: ");
			s.append((this.isTrans) ? "transient." : "recurrent." );
		} else {
			s.append("Memoryless randomised strategyy.\n");
			s.append("Current state is ");
			s.append((this.isTrans) ? "transient." : "recurrent." );
		}
		return s.toString();
	}

	public void export(PrismLog out) {
		exportActions(out);
	}

	@Override
	public void exportActions(PrismLog out) {
		// Print transient distribution
		out.println("Choice distribution in the transient behaviour and switch probability to the recurrent behaviour:");
		out.println("State | Action | Switch probability");
		for (int i = 0; i < model.getNumStates(); i++) {
			if (transChoices[i] == null && switchProb[i] == 0)
				continue;
			out.print(i + " | ");
			StringJoiner joiner = new StringJoiner(", ");
			if (transChoices[i] != null) {
				for (Entry<Integer, Double> distEntry : transChoices[i]) {
					if (distEntry.getValue() > 0) {
						Object action = model.getAction(i, distEntry.getKey());
						String label = action == null ? "" + distEntry.getKey() : action.toString();
						joiner.add(label + ":" + distEntry.getValue());
					}
				}
			}
			out.print(joiner.toString());
			out.println(" | " + switchProb[i]);
		}

		// Print recurrent distribution
		out.println("Choice distribution in the recurrent behaviour:");
		out.println("State | Action");
		for (int i = 0; i < model.getNumStates(); i++) {
			if (recChoices[i] == null)
				continue;
			out.print(i + " | ");
			StringJoiner joiner = new StringJoiner(", ");
			for (Entry<Integer, Double> distEntry : recChoices[i]) {
				if(distEntry.getValue() > 0) {
					Object action = model.getAction(i, distEntry.getKey());
					String label = action == null ? "" + distEntry.getKey() : action.toString();
					joiner.add(label + ":" + distEntry.getValue());
				}
			}
			out.println(joiner.toString());
		}
	}

	@Override
	public void update(Object action, int s) {
		// TODO Auto-generated method stub

	}

	@Override
	public Object getChoiceAction() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub

	}

	@Override
	public void exportIndices(PrismLog out) {
		// Print transient distribution
		out.println("Choice distribution in the transient behaviour and switch probability to the recurrent behaviour:");
		out.println("State | Action | Switch probability");
		for (int i = 0; i < model.getNumStates(); i++) {
			if (transChoices[i] == null && switchProb[i] == 0) {
				continue;
			}
			out.print(i + " | " + transChoices[i].toString());
			out.println(" | " + switchProb[i]);
		}

		// Print recurrent distribution
		out.println("Choice distribution in the recurrent behaviour:");
		out.println("State | Action");
		for (int i = 0; i < model.getNumStates(); i++) {
			if (recChoices[i] == null)
				continue;
			out.println(i + " | " + recChoices[i].toString());
		}
	}

	@Override
	public void exportInducedModel(PrismLog out) {
	}

	@Override
	public void exportDotFile(final PrismLog out) {
		Model mdp = buildPolicyFromMDPExplicit();
		mdp.exportToDotFile(out, null, true);
	}
}
