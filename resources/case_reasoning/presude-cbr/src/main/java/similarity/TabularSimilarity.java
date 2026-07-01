package similarity;

import java.util.List;

import es.ucm.fdi.gaia.jcolibri.exception.NoApplicableSimilarityFunctionException;
import es.ucm.fdi.gaia.jcolibri.method.retrieve.NNretrieval.similarity.LocalSimilarityFunction;

/**
 * Tabelarna funkcija slicnosti - racuna slicnost izmedju dva stringa
 * na osnovu unaprijed definisane matrice slicnosti.
 * Koristi se za kategoricke atribute kao sto su tipKrivicnogDjela, clanKZ,
 * vrstaPresude, bracniStatus, zaposlenost itd.
 */
public class TabularSimilarity implements LocalSimilarityFunction {

    private double[][] matrix;
    private List<String> categories;

    public TabularSimilarity(List<String> categories) {
        this.categories = categories;
        int n = categories.size();
        matrix = new double[n][n];
        for (int i = 0; i < n; i++)
            matrix[i][i] = 1;  // slicnost elementa sa samim sobom = 1
    }

    /**
     * Postavi simetricnu slicnost izmedju dva elementa.
     */
    public void setSimilarity(String value1, String value2, double sim) {
        setSimilarity(value1, value2, sim, sim);
    }

    /**
     * Postavi asimetricnu slicnost (sim1: v1->v2, sim2: v2->v1).
     */
    public void setSimilarity(String value1, String value2, double sim1, double sim2) {
        int index1 = categories.indexOf(value1);
        int index2 = categories.indexOf(value2);
        if (index1 != -1 && index2 != -1) {
            matrix[index1][index2] = sim1;
            matrix[index2][index1] = sim2;
        }
    }

    @Override
    public double compute(Object value1, Object value2) throws NoApplicableSimilarityFunctionException {
        if (value1 instanceof String && value2 instanceof String)
            return compute((String) value1, (String) value2);
        if (value1 instanceof List && value2 instanceof List)
            return compute((List<String>) value1, (List<String>) value2);
        return 0;
    }

    public double compute(String str1, String str2) {
        int index1 = categories.indexOf(str1);
        int index2 = categories.indexOf(str2);
        if (index1 != -1 && index2 != -1)
            return matrix[index1][index2];
        if (str1 != null && str1.equals(str2))
            return 1;
        return 0;
    }

    public double compute(List<String> list1, List<String> list2) {
        if (list1.isEmpty() && list2.isEmpty())
            return 1;
        double sim1to2 = 0;
        for (String el1 : list1) {
            double sim = 0;
            for (String el2 : list2)
                sim = Math.max(sim, compute(el1, el2));
            sim1to2 += sim;
        }
        double sim2to1 = 0;
        for (String el2 : list2) {
            double sim = 0;
            for (String el1 : list1)
                sim = Math.max(sim, compute(el2, el1));
            sim2to1 += sim;
        }
        return (sim1to2 + sim2to1) / (list1.size() + list2.size());
    }

    @Override
    public boolean isApplicable(Object value1, Object value2) {
        if (value1 instanceof String && value2 instanceof String)
            return true;
        if (value1 instanceof List && value2 instanceof List)
            return true;
        return false;
    }
}
