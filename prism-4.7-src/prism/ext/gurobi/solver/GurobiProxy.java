package solvers;

import gurobi.GRB;
import gurobi.GRB.DoubleAttr;
import gurobi.GRB.DoubleParam;
import gurobi.GRB.IntAttr;
import gurobi.GRB.IntParam;
import gurobi.GRB.StringAttr;
import gurobi.GRBCallback;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import prism.PrismException;

public class GurobiProxy implements SolverProxyInterface {
	private class CallbackPositive extends GRBCallback
	{
		protected void callback() {
			try {
				if (where == GRB.CB_MIP) {
					// MIP solution callback
					double obj;
					obj = getDoubleInfo(GRB.CB_MIP_OBJBST);
					if (obj > 0) {
						abort();
					}
				}
			} catch (GRBException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	private static final boolean debug = false;
	
	private GRBModel model;
	private GRBEnv env;
	int result;

	private final Map<Integer, LpResult> resultMapping = Map.of(
			GRB.INFEASIBLE, LpResult.INFEASIBLE,
			GRB.OPTIMAL, LpResult.OPTIMAL
	);

	public GurobiProxy(int numRealVars) throws PrismException {
		this(numRealVars, 0);
	}

	public GurobiProxy(int numRealVars, int numBinaryVars) throws PrismException
	{
		try {
			env = new GRBEnv();
			env.set(DoubleParam.IntFeasTol, 10e-9);
			env.set(IntParam.DualReductions, 0);
			env.set(IntParam.Method, GRB.METHOD_DUAL);
			env.set(IntParam.Quad, GRB.FEASRELAX_QUADRATIC);
			model = new GRBModel(env);
			model.addVars(numRealVars, GRB.CONTINUOUS);
			if (numBinaryVars > 0)
				model.addVars(numBinaryVars, GRB.BINARY);
			model.update();
		} catch (GRBException ex) {
			throw new PrismException("Exception thrown when working with Gurobi solver: " + ex);
		}
	}
	
	public void setVarName(int idx, String name) throws PrismException
	{
		try {
			model.getVar(idx).set(StringAttr.VarName, name);
		} catch (GRBException ex) {
			throw new PrismException("Exception thrown when working with Gurobi solver: " + ex);
		}
	}
	
	public void setVarBounds(int idx, double lo, double up) throws PrismException
	{
		try {
			model.getVar(idx).set(DoubleAttr.LB, lo);
			model.getVar(idx).set(DoubleAttr.UB, up);
		} catch (GRBException ex) {
			throw new PrismException("Exception thrown when working with Gurobi solver: " + ex);
		}
	}
	
	public void addRowFromMap(Map<Integer,Double> row, double rhs, int op, String name) throws PrismException 
	{
		try {
		if (row == null || row.size() == 0)
			return; //nothing to do
		
		int count = row.size();
		
		GRBVar[] indices = new GRBVar[count];
		double[] values = new double[count];
		
		int i = 0;
		for (Entry<Integer, Double> entry : row.entrySet()) {
			indices[i] = model.getVar(entry.getKey());
			values[i] = entry.getValue();
			
			i++;
		}

		GRBLinExpr lhs = new GRBLinExpr();
		lhs.addTerms(values, indices);
		GRBLinExpr rhsEx = new GRBLinExpr();
		rhsEx.addConstant(rhs);
		char opG;
		if (op == GE)
			opG = GRB.GREATER_EQUAL;
		else if (op == LE)
			opG = GRB.LESS_EQUAL;
		else if (op == EQ)
			opG = GRB.EQUAL;
		else
			throw new UnsupportedOperationException("unknown comparison operator");
		model.addConstr(lhs, opG, rhsEx, name);
		} catch (GRBException ex) {
			throw new PrismException("Exception thrown when working with Gurobi solver: " + ex);
		}
	}

	public void removeRowByName(String name) throws PrismException {
		try {
			var constr = model.getConstrByName(name);
			if (constr!=null)
				model.remove(constr);
		} catch (GRBException ex) {
			throw new PrismException("Exception thrown when working with Gurobi solver: " + ex);
		}
	}

	public boolean solveIsPositive() throws PrismException {
		try {
			model.update();
			model.setCallback(new CallbackPositive());
			model.optimize();
			if (debug) 
				doDebug();
			this.result = model.get(IntAttr.Status);
			if (this.result == GRB.INFEASIBLE)
				return false;
			else if (this.result == GRB.OPTIMAL || this.result == GRB.INTERRUPTED) {
				double resultSolver = this.model.get(DoubleAttr.ObjVal);
				return resultSolver > 0;
			} else {
				throw new PrismException("Unexpected result of LP solving, when double value is expected: " + this.result);
			}
		} catch (GRBException ex) {
			throw new PrismException("Exception thrown when working with Gurobi solver: " + ex);
		}

	}
	
	public LpResult solve() throws PrismException {
		try {
			model.update();
			model.optimize();
			if (debug) 
				doDebug();
			this.result = model.get(IntAttr.Status);
			return mapResult(this.result);
		} catch (GRBException ex) {
			throw new PrismException("Exception thrown when working with Gurobi solver: " + ex);
		}
	}

	private void doDebug() throws GRBException, PrismException
	{
		model.write("lp_debug.lp");
		double[] val = getVariableValues();
		for (int i = 0; i < val.length; i++) {
			System.out.print(model.getVar(i).get(StringAttr.VarName) + ": " + val[i] + ", ");
		}
	}

	public boolean getBoolResult() throws PrismException {
		if (this.result == GRB.INFEASIBLE)
			return false;
		else if (this.result == GRB.OPTIMAL)
			return true;
		else
			throw new PrismException("Unexpected result of LP solving, when boolean value is expected. Returned value: " + this.result);

	}
	
	public double  getDoubleResult() throws PrismException {
		if (this.result == GRB.INFEASIBLE) {//TODO other results
			return Double.NaN;
		}
		else if (this.result == GRB.OPTIMAL) {
			try {
				double resultSolver = this.model.get(DoubleAttr.ObjVal);
				return resultSolver;
			} catch (GRBException ex) {
				throw new PrismException("Exception thrown when working with lpsolve solver: " + ex);
			}
		} else {
			throw new PrismException("Unexpected result of LP solving, when double value is expected: " + this.result);
		}
	}

	@Override
	public void setObjFunct(Map<Integer, Double> row, boolean max) throws PrismException
	{
		try {
			if (row == null || row.size() == 0)
				return; //nothing to do
			
			int count = row.size();
			
			GRBVar[] indices = new GRBVar[count];
			double[] values = new double[count];
			
			int i = 0;
			for (Entry<Integer, Double> entry : row.entrySet()) {
				indices[i] = model.getVar(entry.getKey());
				values[i] = entry.getValue();
				i++;
			}
	
			GRBLinExpr lhs = new GRBLinExpr();
			lhs.addTerms(values, indices);
			model.setObjective(lhs, (max) ? GRB.MAXIMIZE : GRB.MINIMIZE);
			//System.out.println("ADDING:" + indices[0].get(StringAttr.VarName));
		} catch (GRBException ex) {
			throw new PrismException("Exception thrown when working with Gurobi solver: " + ex);
		}
	}

	@Override
	public double[] getVariableValues() throws PrismException {
		try {
			double[] ret = this.model.get(DoubleAttr.X, this.model.getVars());
			return ret;
		} catch (GRBException ex) {
			throw new PrismException("Exception thrown when working with Gurobi solver: " + ex);
		}
	}

	@Override
	public void printModel() throws PrismException {
		try {
			doDebug();
		}
		catch(GRBException e) {
			throw new RuntimeException(e);
		}
	}

	private LpResult mapResult(int result) {
		return Optional.ofNullable(resultMapping.get(result)).orElse(LpResult.OTHER);
	}
}
