/**
 * This file contains a recursive implementation of the TSP problem using dynamic programming. The
 * main idea is that since we need to do all n! permutations of nodes to find the optimal solution
 * that caching the results of sub paths can improve performance.
 *
 * <p>For example, if one permutation is: '... D A B C' then later when we need to compute the value
 * of the permutation '... E B A C' we should already have cached the answer for the subgraph
 * containing the nodes {A, B, C}.
 *
 * <p>Time Complexity: O(n^2 * 2^n) Space Complexity: O(n * 2^n)
 *
 * @author Steven & Felix Halim, William Fiset, Micah Stairs
 */

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadedTsp2 {

    private static final DecimalFormat df = new DecimalFormat("0.00");
    private static int N = 0;
    private static int start = 0;
    private static double[][] distance = new double[0][];
    private List<Integer> tour = new ArrayList<>();
    private double minTourCost = Double.POSITIVE_INFINITY;
    private boolean ranSolver = false;

    private static double newDistance;

    static class Threading implements Runnable {
        int next;
        int subsetWithoutNext;

        Double[][] memo;
        int end;


        public Threading(int next, int subsetWithoutNext, int end, Double[][] memo) {
            // store parameter for later user
            this.next = next;
            this.subsetWithoutNext = subsetWithoutNext;
            this.end = end;
            this.memo = memo;
        }

        @Override
        public void run() {
            newDistance = memo[end][subsetWithoutNext] + distance[end][next];
        }

    }

    public ThreadedTsp2(double[][] distance) {
        this(0, distance);
    }

    public ThreadedTsp2(int start, double[][] distance) {
        N = distance.length;

        if (N <= 2) throw new IllegalStateException("N <= 2 not yet supported.");
        if (N != distance[0].length) throw new IllegalStateException("Matrix must be square (n x n)");
        if (start < 0 || start >= N) throw new IllegalArgumentException("Invalid start node.");

        ThreadedTsp2.start = start;
        ThreadedTsp2.distance = distance;
    }

    // Returns the optimal tour for the traveling salesman problem.
    public List<Integer> getTour() throws InterruptedException {
        if (!ranSolver) solve();
        return tour;
    }

    // Returns the minimal tour cost.
    public double getTourCost() throws InterruptedException {
        if (!ranSolver) solve();
        return minTourCost;
    }


    // Solves the traveling salesman problem and caches solution.
    public void solve() throws InterruptedException {
        if (ranSolver) return;

        int END_STATE = (1 << N) - 1;
        Double[][] memo = new Double[N][1 << N];

        // Add all outgoing edges from the starting node to memo table.
        for (int end = 0; end < N; end++) {
            if (end == start) continue;
            memo[end][(1 << start) | (1 << end)] = distance[start][end];
        }

        for (int r = 3; r <= N; r++) {
            for (int subset : combinations(r, N)) {
                if (notIn(start, subset)) continue;
                ExecutorService executorService = Executors.newCachedThreadPool();
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            for (int next = 0; next < N; next++) {
                                if (next == start || notIn(next, subset)) continue;
                                int subsetWithoutNext = subset ^ (1 << next);
                                double minDist = Double.POSITIVE_INFINITY;
                                for (int end = 0; end < N; end++) {
                                    if (end == start || end == next || notIn(end, subset)) continue;

                                    newDistance = memo[end][subsetWithoutNext] + distance[end][next];

                                    if (newDistance < minDist) {
                                        minDist = newDistance;
                                    }
                                }
                                memo[next][subset] = minDist;
                            }
                        }
                        catch (Exception e) {
                            System.out.println("Exception is caught");
                        }
                    }
                });

                executorService.shutdown();
                boolean finished = executorService.awaitTermination(1, TimeUnit.MINUTES);
            }
        }

        // Connect tour back to starting node and minimize cost.
        for (int i = 0; i < N; i++) {
            if (i == start) continue;
            double tourCost = memo[i][END_STATE] + distance[i][start];
            if (tourCost < minTourCost) {
                minTourCost = tourCost;
            }
        }

        int lastIndex = start;
        int state = END_STATE;
        tour.add(start);

        // Reconstruct TSP path from memo table.
        for (int i = 1; i < N; i++) {

            int bestIndex = -1;
            double bestDist = Double.POSITIVE_INFINITY;
            for (int j = 0; j < N; j++) {
                if (j == start || notIn(j, state)) continue;
                double newDist = memo[j][state] + distance[j][lastIndex];
                if (newDist < bestDist) {
                    bestIndex = j;
                    bestDist = newDist;
                }
            }

            tour.add(bestIndex);
            state = state ^ (1 << bestIndex);
            lastIndex = bestIndex;
        }

        tour.add(start);
        Collections.reverse(tour);

        ranSolver = true;
    }

    private static boolean notIn(int elem, int subset) {
        return ((1 << elem) & subset) == 0;
    }

    // This method generates all bit sets of size n where r bits
    // are set to one. The result is returned as a list of integer masks.
    public static List<Integer> combinations(int r, int n) {
        List<Integer> subsets = new ArrayList<>();
        combinations(0, 0, r, n, subsets);
        return subsets;
    }

    // To find all the combinations of size r we need to recurse until we have
    // selected r elements (aka r = 0), otherwise if r != 0 then we still need to select
    // an element which is found after the position of our last selected element
    private static void combinations(int set, int at, int r, int n, List<Integer> subsets) {

        // Return early if there are more elements left to select than what is available.
        int elementsLeftToPick = n - at;
        if (elementsLeftToPick < r) return;

        // We selected 'r' elements, so we found a valid subset!
        if (r == 0) {
            subsets.add(set);
        } else {
            for (int i = at; i < n; i++) {
                // Try including this element
                set ^= (1 << i);

                combinations(set, i + 1, r - 1, n, subsets);

                // Backtrack and try the instance where we did not include this element
                set ^= (1 << i);
            }
        }
    }

    public static void printMatrix(double[][] matrix)
    {
        int matrixLength =  matrix.length;
        for(int i = 0; i < matrixLength; i++) {
            for(int j = 0; j < matrixLength; j++) {
                System.out.printf(df.format(matrix[i][j]) + "\t");
            }
            System.out.println();
        }
    }

    private static double euclideanDistance(double x1, double y1, double x2, double y2){
        return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
    }


    // Example usage:
    public static void main(String[] args) throws InterruptedException {

        // Create adjacency matrix
        int n = 25;
        double[][] distanceMatrix = new double[n][n];
        ArrayList<double[]> matrix = new ArrayList<>();
        Random rand = new Random();
        ThreadedTsp2 solver = new ThreadedTsp2(distanceMatrix);


        // Dynamic Way
        for (int i = 0; i < n; i++){
//            double xCoordinate = 1 + (100 - 1) * rand.nextDouble();
//            double yCoordinate = 1 + (100 - 1) * rand.nextDouble();
            int xCoordinate = rand.nextInt(100);
            int yCoordinate = rand.nextInt(100);
            double infectionProbability = rand.nextDouble();

            double[] coordinate = new double[3];
            coordinate[0] = xCoordinate;
            coordinate[1] = yCoordinate;
            coordinate[2] = infectionProbability;

            matrix.add(coordinate);
        }

        System.out.println("City Coordinates and Infection Probability (Randomly Generated):");
        int countCoordinate = 0;
        for(double[] i: matrix){
            countCoordinate ++;
            System.out.println("City " + countCoordinate + " => Coordinate: (" + i[0] + ", " + i[1] + ")\tand Infection Probability: " + df.format(i[2]));
        }
        System.out.println();

        double maxDistance = -1;
        double maxInfectionProbabilityMultiply = -1;

        // Calculating maxDistance and maxInfectionProbabilityMultiply
        int row = 0;
        for(double[] i: matrix){
            int column = 0;
            for(double[] j: matrix){
                double distance = euclideanDistance(i[0], i[1], j[0], j[1]);
                double infectionProbabilityMultiply = i[2]*j[2];

                if (distance > maxDistance){
                    maxDistance = distance;
                }
                if (infectionProbabilityMultiply > maxInfectionProbabilityMultiply){
                    maxInfectionProbabilityMultiply = infectionProbabilityMultiply;
                }
                column ++;
            }
            row ++;
        }

        // Calculating weight
        row = 0;
        for(double[] i: matrix){
            int column = 0;
            for(double[] j: matrix){

                double distance = euclideanDistance(i[0], i[1], j[0], j[1]);
                double infectionProbabilityMultiply = i[2]*j[2];

                distanceMatrix[row][column] = (0.5*distance)/maxDistance + (0.5*infectionProbabilityMultiply)/maxInfectionProbabilityMultiply;
                column ++;
            }
            row ++;
        }

        System.out.println("Weighted Adjacency Matrix:");
        printMatrix(distanceMatrix);
        System.out.println();

        long startTime = System.nanoTime();
        System.out.println("Tour: " + solver.getTour());
        System.out.println("Tour cost: " + solver.getTourCost());
        long endTime = System.nanoTime();
        long executionTimeForThreadedTsp2 = endTime - startTime;
        System.out.println("Total Execution time: " + executionTimeForThreadedTsp2);
    }
}