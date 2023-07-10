package explicit;

public class LtlSpecification {
	private final Double ltlThreshold;
	private final LTLModelChecker.LTLProduct<MDP> mdpProduct;

	public LtlSpecification(Double ltlThreshold, LTLModelChecker.LTLProduct<MDP> mdpProduct) {
		this.ltlThreshold = ltlThreshold;
		this.mdpProduct = mdpProduct;
	}

	public LTLModelChecker.LTLProduct<MDP> getMdpProduct() {
		return mdpProduct;
	}

	public Double getLtlThreshold() {
		return ltlThreshold;
	}
}
