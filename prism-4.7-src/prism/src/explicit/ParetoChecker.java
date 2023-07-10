package explicit;

import prism.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Component to approximate pareto curves of multi dimensional numerical properties
 */
public class ParetoChecker extends PrismComponent {
	private final Function<Point, Point> solver;

	/**
	 * @param parent {@link PrismComponent} to copy settings from
	 * @param solver {@link Function} from {@link Point} to {@link Point} mapping a weight vector of the
	 * numerical objectives to the optimal solution of some model for that weight vector
	 */
	public ParetoChecker(PrismComponent parent, Function<Point, Point> solver) {
		super(parent);
		this.solver = solver;
	}

	/**
	 * Approximate the pareto curve by calling solver repeatedly
	 * (at most {@param iterations} + {@param dimensions} times)
	 *
	 * @param dimensions the number of dimensions
	 * @param iterations the maximum number of iterations before stopping
	 * @return {@link TileList} representing the pareto approximation
	 * @throws PrismException
	 */
	public TileList approximatePareto(int dimensions, int iterations) throws PrismException {
		final var initialPoints = IntStream.range(0, dimensions).boxed()
				.map(dim -> unitVector(dimensions, dim))
				.map(solver)
				.collect(Collectors.toList());
		final var tolerance = settings.getDouble(PrismSettings.PRISM_PARETO_EPSILON);
		final var tileList = new TileList(new Tile(new ArrayList<>(initialPoints)), null, tolerance);
		final var improvement = new AtomicBoolean(true);
		int i = 0;
		for (; i < iterations && improvement.get(); i++) {
			Optional.of(tileList)
					.map(TileList::getCandidateHyperplane)
					.filter(Point::sane)
					.map(solver)
					.ifPresentOrElse(
							tileList::addNewPoint,
							() -> improvement.set(false)
					);
		}
		mainLog.println("Approximated pareto front in " + i + "/" + iterations + " iterations with tolerance " + tolerance);
		return tileList;
	}

	/**
	 * Export pareto curve into a format interpretable by prism/etc/scripts/pareto-plot.py
	 *
	 * @param tl         the {@link TileList} representing the pareto curve
	 * @param title      the title of the plot (e.g. the checked property)
	 * @param objectives the numerical objectives to label the axes
	 * @param filename   the filename where to export to
	 * @throws PrismException
	 */
	public void exportPareto(TileList tl, String title, List<String> objectives, String filename) throws PrismException {
		try {
			FileWriter fw;
			fw = new FileWriter(filename);
			fw.write(title + "\n");
			fw.write(String.join(",", objectives) + "\n");
			fw.write(tl.export());
			fw.close();
		} catch (IOException ex) {
			throw new PrismException("An IOException error occured when writing graph files (exception message: " + ex.getMessage() + ").");
		}
		mainLog.println("Exported Pareto curve. To see it, run\n etc/scripts/pareto-plot.py " + filename);
	}

	private Point unitVector(int dimensions, int dimension) {
		final var values = new double[dimensions];
		values[dimension] = 1.0;
		return new Point(values);
	}


}
