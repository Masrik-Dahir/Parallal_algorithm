/*
    java New.java <number of cities>
 */

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class New {

    private static final DecimalFormat df = new DecimalFormat("0.00");
    private final int N;
    private final int START_NODE;
    private final int FINISHED_STATE;

    private double[][] distance;
    private double minTourCost = Double.POSITIVE_INFINITY;

    private List<Integer> tour = new ArrayList<>();

    static double totalTpsCost = 0;
    private boolean ranSolver = false;

    public New(double[][] distance) {
        this(0, distance);
    }

    public New(int startNode, double[][] distance) {

        this.distance = distance;
        N = distance.length;
        START_NODE = startNode;

        // Validate inputs.
        if (N <= 2) throw new IllegalStateException("TSP on 0, 1 or 2 nodes doesn't make sense.");
        if (N != distance[0].length)
            throw new IllegalArgumentException("Matrix must be square (N x N)");
        if (START_NODE < 0 || START_NODE >= N)
            throw new IllegalArgumentException("Starting node must be: 0 <= startNode < N");

        // The finished state is when the finished state mask has all bits are set to
        // one (meaning all the nodes have been visited).
        FINISHED_STATE = (1 << N) - 1;
    }

    // Returns the optimal tour for the traveling salesman problem.
    public List<Integer> getTour() {
        if (!ranSolver) solve();
        return tour;
    }

    // Returns the minimal tour cost.
    public double getTourCost() {
        if (!ranSolver) solve();
        return minTourCost;
    }

    public void solve() {

        // Run the solver
        int state = 1 << START_NODE;
        Double[][] memo = new Double[N][1 << N];
        Integer[][] prev = new Integer[N][1 << N];
        minTourCost = tsp(START_NODE, state, memo, prev);

        // Regenerate path
        int index = START_NODE;
        while (true) {
            tour.add(index);
            Integer nextIndex = prev[index][state];
            if (nextIndex == null) break;
            int nextState = state | (1 << nextIndex);
            state = nextState;
            index = nextIndex;
        }
        tour.add(START_NODE);
        ranSolver = true;
    }

    private double tsp(int i, int state, Double[][] memo, Integer[][] prev) {

        // Done this tour. Return cost of going back to start node.
        if (state == FINISHED_STATE) return distance[i][START_NODE];

        // Return cached answer if already computed.
        if (memo[i][state] != null) return memo[i][state];

        double minCost = Double.POSITIVE_INFINITY;
        int index = -1;
        for (int next = 0; next < N; next++) {

            // Skip if the next node has already been visited.
            if ((state & (1 << next)) != 0) continue;

            int nextState = state | (1 << next);
            double newCost = distance[i][next] + tsp(next, nextState, memo, prev);
            if (newCost < minCost) {
                minCost = newCost;
                index = next;
            }
        }

        prev[i][state] = index;
        return memo[i][state] = minCost;
    }

    public static void printMatrix(double[][] matrix){
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
    public static void main(String[] args) {
        if (args.length == 1){
            // Create adjacency matrix
            int n = Integer.parseInt(args[0]);
            double[][] distanceMatrix = new double[n][n];
            ArrayList<double[]> matrix = new ArrayList<>();
            Random rand = new Random();
            New solver = new New(distanceMatrix);


            // Dynamic Way
            for (int i = 0; i < n; i++){
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

            long startTime = System.nanoTime();
            System.out.println("\nTravelling Salesman Path: ");
            System.out.println("Tour: " + solver.getTour());
            System.out.println("Tour cost: " + solver.getTourCost());
            long endTime = System.nanoTime();
            long executionTimeForNew = endTime - startTime;
            System.out.println("Total Execution time: " + executionTimeForNew);
        }

        else{
            System.out.println("Please give an argument for number of cities. Ex - java New.java <number of cities>");
        }
    }
}