package org.kevoree.modeling.util.maths;

public class Ranking {

    /*Reddit news ranking algorithm: positive votes, negative votes and confidence level*/
    public static double wilsonRank(int positive, int negative, double confidence) {
        int n = positive + negative;
        if (n == 0) {
            return 0.0;
        }
        double z = Distribution.inverseNormalCDF(1.0 - confidence / 2.0);
        double p_hat = (1.0 * positive) / n;
        return (p_hat + z * z / (2.0 * n) - z * Math.sqrt((p_hat * (1.0 - p_hat) + z * z / (4.0 * n)) / n)) / (1.0 + z * z / n);
    }

}
