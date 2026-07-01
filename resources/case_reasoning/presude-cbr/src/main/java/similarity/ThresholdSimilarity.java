package similarity;

import es.ucm.fdi.gaia.jcolibri.exception.NoApplicableSimilarityFunctionException;
import es.ucm.fdi.gaia.jcolibri.method.retrieve.NNretrieval.similarity.LocalSimilarityFunction;

/**
 * Funkcija slicnosti zasnovana na pragovnoj vrednosti.
 * Vraca 1 ako je razlika izmedju dve numericke vrednosti manja od praga,
 * inace vraca 0.
 *
 * Koristi se za atribute gdje je bitno da li su vrednosti u slicnom opsegu,
 * npr. kazna zatvora - ako je razlika manja od 6 mjeseci, smatra se slicnim.
 */
public class ThresholdSimilarity implements LocalSimilarityFunction {

    private double threshold;

    /**
     * @param threshold maksimalna dozvoljena razlika za slicnost = 1
     */
    public ThresholdSimilarity(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public double compute(Object value1, Object value2) throws NoApplicableSimilarityFunctionException {
        if (value1 instanceof Number && value2 instanceof Number) {
            double v1 = ((Number) value1).doubleValue();
            double v2 = ((Number) value2).doubleValue();
            return Math.abs(v1 - v2) <= threshold ? 1.0 : 0.0;
        }
        return 0;
    }

    @Override
    public boolean isApplicable(Object value1, Object value2) {
        return value1 instanceof Number && value2 instanceof Number;
    }
}
