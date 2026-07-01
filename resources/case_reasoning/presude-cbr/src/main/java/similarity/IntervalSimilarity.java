package similarity;

import es.ucm.fdi.gaia.jcolibri.exception.NoApplicableSimilarityFunctionException;
import es.ucm.fdi.gaia.jcolibri.method.retrieve.NNretrieval.similarity.LocalSimilarityFunction;

/**
 * Funkcija slicnosti za numericke vrednosti zasnovana na intervalu.
 * sim(x, y) = 1 - |x - y| / interval
 *
 * Koristi se za atribute: kolicinaUlovaKg, kaznaZatvoraMjeseci, kaznaZatvoraDani,
 * troskoviEur, brojSvjedoka, brojOkrivljenih.
 * Interval se definise kao maksimalni raspon mogucih vrednosti.
 */
public class IntervalSimilarity implements LocalSimilarityFunction {

    private double interval;

    /**
     * @param interval maksimalni raspon vrednosti (npr. 50 za kolicinu ulova u kg)
     */
    public IntervalSimilarity(double interval) {
        this.interval = interval;
    }

    @Override
    public double compute(Object value1, Object value2) throws NoApplicableSimilarityFunctionException {
        if (value1 instanceof Number && value2 instanceof Number) {
            double v1 = ((Number) value1).doubleValue();
            double v2 = ((Number) value2).doubleValue();
            double diff = Math.abs(v1 - v2);
            if (interval <= 0)
                return v1 == v2 ? 1.0 : 0.0;
            return Math.max(0.0, 1.0 - diff / interval);
        }
        return 0;
    }

    @Override
    public boolean isApplicable(Object value1, Object value2) {
        return value1 instanceof Number && value2 instanceof Number;
    }
}
