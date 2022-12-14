/*
 *  java MPITsp.java <number of blocks> <number of cities per blocks>
 */

import java.awt.geom.Line2D;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MPITsp {
    public static int[][] fillMatrix(int[][] m){
        for(int i = 0; i < m.length; i++){
            for(int j = 0; j < m[i].length; j++){
                m[i][j] = (int)(Math.random()*9);
            }
        }
        return m;
    }
    private static final DecimalFormat df = new DecimalFormat("0.00");
    private static int N = 0;
    private static int start = 0;
    private static double[][] distance = new double[0][];
    private List<Integer> tour = new ArrayList<>();
    private double minTourCost = Double.POSITIVE_INFINITY;
    private boolean ranSolver = false;

    private static double newDistance;

    private static double totaltourCost = 0;

    static ArrayList<Integer> totalTpsPath = new ArrayList<Integer>();

    static ArrayList<ArrayList<Integer>> totalPartitionedTpsPath = new ArrayList<ArrayList<Integer>>();

    private static ArrayList<double[]> universalMatrix = new ArrayList<>();

    private static ArrayList<ArrayList<Integer[]>> coordinateMatrix = new ArrayList<ArrayList<Integer[]>>();

    private static ArrayList<Integer> afterColumnWiseStitching = new ArrayList<>();

    public MPITsp(double[][] distance) {
        this(0, distance);
    }

    public MPITsp(int start, double[][] distance) {
        N = distance.length;

        if (N <= 2) throw new IllegalStateException("N <= 2 not yet supported.");
        if (N != distance[0].length) throw new IllegalStateException("Matrix must be square (n x n)");
        if (start < 0 || start >= N) throw new IllegalArgumentException("Invalid start node.");

        MPITsp.start = start;
        MPITsp.distance = distance;
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
    public void solve() {

        if (ranSolver) return;

        final int END_STATE = (1 << N) - 1;
        Double[][] memo = new Double[N][1 << N];

        // Add all outgoing edges from the starting node to memo table.
        for (int end = 0; end < N; end++) {
            if (end == start) continue;
            memo[end][(1 << start) | (1 << end)] = distance[start][end];
        }

        for (int r = 3; r <= N; r++) {
            for (int subset : combinations(r, N)) {
                if (notIn(start, subset)) continue;
                for (int next = 0; next < N; next++) {
                    if (next == start || notIn(next, subset)) continue;
                    int subsetWithoutNext = subset ^ (1 << next);
                    double minDist = Double.POSITIVE_INFINITY;
                    for (int end = 0; end < N; end++) {
                        if (end == start || end == next || notIn(end, subset)) continue;
                        double newDistance = memo[end][subsetWithoutNext] + distance[end][next];
                        if (newDistance < minDist) {
                            minDist = newDistance;
                        }
                    }
                    memo[next][subset] = minDist;
                }
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

    // Print double Arraylist
    public static void printArrayList(ArrayList<double[]> matrix){
        for (double[] i: matrix){
            System.out.println(Arrays.toString(i));
        }
    }

    // Print double[][]
    public static void printMatrix(double[][] matrix) {
        int matrixLength =  matrix.length;
        for(int i = 0; i < matrixLength; i++) {
            for(int j = 0; j < matrixLength; j++) {
                System.out.printf(df.format(matrix[i][j]) + "\t");
            }
            System.out.println();
        }
    }

    // Find euclideanDistance
    private static double euclideanDistance(double x1, double y1, double x2, double y2){
        return Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
    }

    // calculate getDistanceMatrix from coordinate matrix
    private static double[][] getDistanceMatrix (ArrayList<double[]> matrix){
        double maxDistance = -1;
        double maxInfectionProbabilityMultiply = -1;
        double[][] distanceMatrix = new double[matrix.size()][matrix.size()];

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
        return distanceMatrix;
    }

    // calculate getDistanceMatrix from coordinate matrix and umber of city per block
    public static double[][] getDistanceMatrix(ArrayList<double[]> matrix, int numberOfCityPerBlock){
        double[][] distanceMatrix = new double[numberOfCityPerBlock][numberOfCityPerBlock];

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

        return distanceMatrix;
    }

    public static ArrayList<Integer> printTsp(ArrayList<double[]> matrix, int numberOfCityPerBlock, int finalCount) throws InterruptedException {
        MPITsp solver = new MPITsp(getDistanceMatrix(matrix, numberOfCityPerBlock));

        ArrayList<Integer> blockTour = new ArrayList<>();
        for (Integer integer: solver.getTour()){
            blockTour.add(integer+finalCount*numberOfCityPerBlock);
        }

        blockTour.remove(blockTour.size() - 1);

        if (finalCount == 0){
            System.out.println("(Row Master process): Block " + finalCount + "\t- TSP calculated " + blockTour +" and remains in Block " + finalCount/4);
            double blockTourCost = solver.getTourCost();
            totaltourCost += blockTourCost;

        }
        else{
            if ( (finalCount) % 4 == 0){
                System.out.println("(Row Master process): Block " + finalCount + "\t- TSP calculated " + blockTour + " and remains in Block " + (finalCount/4)*4 );
            }
            else{
                System.out.println("(Slave process): Block " + finalCount + "\t- TSP calculated " + blockTour + " and send to Block " + (finalCount/4)*4 );
            }

            double blockTourCost = solver.getTourCost();
            totaltourCost += blockTourCost;
        }


        return blockTour;
    }

    // Row-wise and Column-wise stitching algorithm
    public static void stitchingAlgorithm(ArrayList<Integer> blockTpsPath){
        totalTpsPath.addAll(blockTpsPath);
    }

    // Finding swap cost from 4 points (8 coordinates)
    public static int swapCost(int firstPointXCoordinate, int firstPointYCoordinate, int secondPointXCoordinate, int secondPointYCoordinate, 
                               int thirdPointXCoordinate, int thirdPointYCoordinate, int fourthPointXCoordinate, int fourthPointYCoordinate){
        // swapCost( (firstPoint, secondPoint), (thirdPoint, fourthPoint) ) = || firstPoint - fourthPoint || + || thirdPoint- secondPoint || - || firstPoint - secondPoint || - || thirdPoint - fourthPoint ||

        // || firstPoint - fourthPoint ||
        int firstPoint_fourthPoint = (int) Math.abs(euclideanDistance(firstPointXCoordinate, firstPointYCoordinate, fourthPointXCoordinate, fourthPointYCoordinate));

        // || thirdPoint- secondPoint ||
        int thirdPoint_secondPoint = (int) Math.abs(euclideanDistance(thirdPointXCoordinate, thirdPointYCoordinate, secondPointXCoordinate, secondPointYCoordinate));

        // || firstPoint - secondPoint ||
        int firstPoint_secondPoint = (int) Math.abs(euclideanDistance(firstPointXCoordinate, firstPointYCoordinate, secondPointXCoordinate, secondPointYCoordinate));

        // || thirdPoint - fourthPoint ||
        int thirdPoint_fourthPoint = (int) Math.abs(euclideanDistance(thirdPointXCoordinate, thirdPointYCoordinate, fourthPointXCoordinate, fourthPointYCoordinate));

        return firstPoint_fourthPoint + thirdPoint_secondPoint - firstPoint_secondPoint - thirdPoint_fourthPoint;
    }

    // Partition blocks in n^2 matrix
    public static ArrayList<Integer> PartitionStitchingAlgorithm(ArrayList<Integer> globalTpsPath, ArrayList<Integer> blockTpsPath){

        // Need to work on the algorithm
        globalTpsPath.addAll(blockTpsPath);

        return globalTpsPath;
    }

    // Partition blocks in n^2 matrix with coordinates
    public static ArrayList<Integer[]> PartitionMatrixStitchingAlgorithm(ArrayList<Integer[]> globalTpsPathMatrix, ArrayList<Integer[]> blockTpsPathMatrix){

        globalTpsPathMatrix.addAll(blockTpsPathMatrix);

        return globalTpsPathMatrix;
    }

    // Thread for each block
    static class Threading implements Runnable {
        ArrayList<double[]> matrix;
        int numberOfCityPerBlock;
        int finalCount;


        public Threading(ArrayList<double[]> matrix, int numberOfCityPerBlock, int finalCount) {
            // store parameter for later user
            this.matrix = matrix;
            this.numberOfCityPerBlock = numberOfCityPerBlock;
            this.finalCount = finalCount;
        }

        @Override
        public void run() {
            try {
                ArrayList<Integer> blockTpsPath = printTsp(matrix, numberOfCityPerBlock, finalCount);

                // Stitching Algorithm
                stitchingAlgorithm(blockTpsPath);

                // Partitioned Stitching Algorithm
                ArrayList<Integer[]> coordinateArray = new ArrayList<>();
                for (int i = 0; i < matrix.size(); i ++){
                    int xCoordinate = (int) matrix.get(i)[0];
                    int yCoordinate = (int) matrix.get(i)[1];
                    Integer[] n = {xCoordinate, yCoordinate, finalCount*numberOfCityPerBlock +i};
                    coordinateArray.add(n);
                }

                ArrayList<Integer[]> localCoordinateMatrix = new ArrayList<>();
                for (int i = 0; i < blockTpsPath.size(); i++){
                    localCoordinateMatrix.add(coordinateArray.get(blockTpsPath.get(i)%numberOfCityPerBlock));
                }

                totalPartitionedTpsPath.set(finalCount/4, PartitionStitchingAlgorithm(totalPartitionedTpsPath.get(finalCount/4), blockTpsPath));
                coordinateMatrix.set(finalCount/4, stitchingCoordinates(coordinateMatrix.get(finalCount/4), localCoordinateMatrix));


            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    // finds the 4 points that produces the least swapping cost and returns it
    public static Integer[][] findCoordinates(ArrayList<Integer[]> globalTpsPathMatrix, ArrayList<Integer[]> blockTpsPathMatrix){
        int minimumSwapCost = (int) Double.POSITIVE_INFINITY;
        Integer[][] minimumSwapCostCoordinates = new Integer[4][2];

        for (int i = 0; i < blockTpsPathMatrix.size() - 1; i ++){

            Integer[] firstPoint = blockTpsPathMatrix.get(i);
            Integer[] secondPoint = blockTpsPathMatrix.get(i+1);

            int firstPointXCoordinate = blockTpsPathMatrix.get(i)[0];
            int firstPointYCoordinate = blockTpsPathMatrix.get(i)[1];

            int secondPointXCoordinate = blockTpsPathMatrix.get(i+1)[0];
            int secondPointYCoordinate = blockTpsPathMatrix.get(i+1)[1];

//            System.out.println("First Point: [" + String.valueOf(firstPointXCoordinate) + ", " + String.valueOf(firstPointYCoordinate) + "], Second Point: [" + String.valueOf(secondPointXCoordinate) + ", " + String.valueOf(secondPointYCoordinate) + "]" );

            for (int j = 0; j < globalTpsPathMatrix.size() -1; j ++){

                Integer[] thirdPoint = globalTpsPathMatrix.get(j);
                Integer[] fourthPoint = globalTpsPathMatrix.get(j+1);

                int thirdPointXCoordinate = globalTpsPathMatrix.get(j)[0];
                int thirdPointYCoordinate = globalTpsPathMatrix.get(j)[1];

                int fourthPointXCoordinate = globalTpsPathMatrix.get(j+1)[0];
                int fourthPointYCoordinate = globalTpsPathMatrix.get(j+1)[1];

                int swapCost = swapCost(firstPointXCoordinate, firstPointYCoordinate, secondPointXCoordinate, secondPointYCoordinate,
                        thirdPointXCoordinate, thirdPointYCoordinate, fourthPointXCoordinate, fourthPointYCoordinate);

                if (swapCost < minimumSwapCost){
                    minimumSwapCost = swapCost;
                    minimumSwapCostCoordinates[0] = firstPoint;
                    minimumSwapCostCoordinates[1] = secondPoint;
                    minimumSwapCostCoordinates[2] = thirdPoint;
                    minimumSwapCostCoordinates[3] = fourthPoint;
                }
            }
        }

        return minimumSwapCostCoordinates;
    }

    // From the 4 points it add the two TSP with the stitching lagorithm
    public static ArrayList<Integer[]> stitchingCoordinates(ArrayList<Integer[]> globalTpsPathMatrix, ArrayList<Integer[]> blockTpsPathMatrix){
        ArrayList<Integer[]> afterStitching = new ArrayList<>();
        ArrayList<Integer> afterStitchingTsp = new ArrayList<>();

        Integer[][] coordinates = findCoordinates(globalTpsPathMatrix, blockTpsPathMatrix);

        // Block Coorodinates
        Integer[] firstPoint = coordinates[0];
        Integer[] secondPoint = coordinates[1];

        // Global Coorodinates
        Integer[] thirdPoint = coordinates[2];
        Integer[] fourthPoint = coordinates[3];

        // Block Index
        int firstPointIndex = blockTpsPathMatrix.indexOf(firstPoint);
        int secondPointIndex = blockTpsPathMatrix.indexOf(secondPoint);

        // Global Index
        int thirdPointIndex = globalTpsPathMatrix.indexOf(thirdPoint);
        int fourthPointIndex = globalTpsPathMatrix.indexOf(fourthPoint);

        if (globalTpsPathMatrix.size() == 0){
            afterStitching.addAll(blockTpsPathMatrix);
            return afterStitching;
        }

        else if (blockTpsPathMatrix.size() == 0){
            afterStitching.addAll(globalTpsPathMatrix);
            return afterStitching;
        }

        // First Leg -> before the global coordinates
        for (int i = 0; i <= thirdPointIndex; i++ ){
            afterStitching.add(globalTpsPathMatrix.get(i));
        }

        // Second Leg -> before the block coordinates
        ArrayList<Integer[]> temp = new ArrayList<>();
        for (int i = 0; i <= firstPointIndex; i++ ){
            temp.add(blockTpsPathMatrix.get(i));
        }
        Collections.reverse(temp);
        afterStitching.addAll(temp);

        // Third Leg -> After the block coordinates
        temp.clear();
        for (int i = secondPointIndex; i < blockTpsPathMatrix.size(); i++ ){
            temp.add(blockTpsPathMatrix.get(i));
        }
        Collections.reverse(temp);
        afterStitching.addAll(temp);

        // Third Leg -> After the global coordinates
        temp.clear();
        for (int i = fourthPointIndex; i < globalTpsPathMatrix.size(); i++ ){
            temp.add(globalTpsPathMatrix.get(i));
        }
        Collections.reverse(temp);
        afterStitching.addAll(temp);


        return afterStitching;
    }

    // Print ArrayList of integer array
    public static void printArraylistIntegerArray(ArrayList<Integer[]> old, int last){
        String newStr = "";
        for (int i = 0; i < old.size(); i++){
            Integer[] array = old.get(i);
            for (int j = 0; j <= array.length-last; j++){
                if (j == 0){
                    newStr += "[";
                }
                newStr += array[j] + ", ";
                if (j == array.length-last){
                    newStr = newStr.substring(0, newStr.length()-2);
                    newStr += "], ";
                }
            }
            if (i == old.size()-1){
                newStr = newStr.substring(0, newStr.length()-2);
            }
        }
        System.out.println(newStr);
    }

    // print 2-D array of Integers
    public static void printTwoDimentionalArray(Integer[][] old){
        String newStr = "";
        for (int i = 0; i < old.length; i++){
            Integer[] array = old[i];
            for (int j = 0; j < array.length; j++){
                if (j == 0){
                    newStr += "[";
                }
                newStr += array[j] + ", ";
                if (j == array.length-1){
                    newStr = newStr.substring(0, newStr.length()-2);
                    newStr += "], ";
                }
            }
            if (i == old.length-1){
                newStr = newStr.substring(0, newStr.length()-2);
            }
        }
        System.out.println(newStr);
    }

    // Inversion Algorithm
    public static ArrayList<Integer> inversion(ArrayList<Integer> inversionList){
        System.out.println("\nHandling Inversion");
        for (int i = 0; i < inversionList.size() - 3; i ++) {

            int[] firstNodeCoordinate = new int[2];
            int[] secondNodeCoordinate = new int[2];
            int[] thirdNodeCoordinate = new int[2];
            int[] fourthNodeCoordinate = new int[2];

            int firstNodeIndex = i;
            int secondNodeIndex = i+1;
            int thirdNodeIndex = i+2;
            int fourthNodeIndex = i+3;

            int firstNode = inversionList.get(firstNodeIndex);
            int secondNode = inversionList.get(secondNodeIndex);
            int thirdNode = inversionList.get(thirdNodeIndex);
            int fourthNode = inversionList.get(fourthNodeIndex);

            firstNodeCoordinate[0] = (int) universalMatrix.get(inversionList.get(firstNodeIndex))[0];
            firstNodeCoordinate[1] = (int) universalMatrix.get(inversionList.get(firstNodeIndex))[1];

            secondNodeCoordinate[0] = (int) universalMatrix.get(inversionList.get(secondNodeIndex))[0];
            secondNodeCoordinate[1] = (int) universalMatrix.get(inversionList.get(secondNodeIndex))[1];

            thirdNodeCoordinate[0] = (int) universalMatrix.get(inversionList.get(thirdNodeIndex))[0];
            thirdNodeCoordinate[1] = (int) universalMatrix.get(inversionList.get(thirdNodeIndex))[1];

            fourthNodeCoordinate[0] = (int) universalMatrix.get(inversionList.get(fourthNodeIndex))[0];
            fourthNodeCoordinate[1] = (int) universalMatrix.get(inversionList.get(fourthNodeIndex))[1];

            Line2D firstLine2D = new Line2D.Float((float)firstNodeCoordinate[0], (float)firstNodeCoordinate[1], (float)secondNodeCoordinate[0], (float)secondNodeCoordinate[1]);
            Line2D secondLine2D = new Line2D.Float((float)thirdNodeCoordinate[0], (float)thirdNodeCoordinate[1], (float)fourthNodeCoordinate[0], (float)fourthNodeCoordinate[1]);
            boolean secondLine2DCrossFirstLine2D = secondLine2D.intersectsLine(firstLine2D);
            if (secondLine2DCrossFirstLine2D){
                System.out.print("Inversion required for node " +
                        inversionList.get(i) + ", node " +
                        inversionList.get(i+1) + ", node " +
                        inversionList.get(i+2) + ", and node " +
                        inversionList.get(i+3) +
                        ":\t" +
                        Arrays.toString(firstNodeCoordinate) + ", " +
                        Arrays.toString(secondNodeCoordinate) + ", " +
                        Arrays.toString(thirdNodeCoordinate) + ", " +
                        Arrays.toString(fourthNodeCoordinate) + " and ");

                inversionList.set(secondNodeIndex, thirdNode);
                inversionList.set(thirdNodeIndex, secondNode);
                System.out.println("Inversion Handled");
            }

        }

        return inversionList;
    }

    // Example usage:
    public static void main(String[] args) throws InterruptedException {

        long startTime = System.nanoTime();
        Random rand = new Random();
        ArrayList<Integer> finalListAfterStitching = new ArrayList<>();

        if (args.length == 2) {

            // Declaring number Of Blocks and number Of City Per Block
            int numberOfBlocks = Integer.parseInt(args[0]);
            int numberOfCityPerBlock = Integer.parseInt(args[1]);

            for (int i = 0; i <= ((int) numberOfBlocks/4); i++ ){
                totalPartitionedTpsPath.add(new ArrayList<Integer>());
                coordinateMatrix.add(new ArrayList<Integer[]>());
            }

            System.out.println("Processes: ");
            for (int blocks = numberOfBlocks-1; blocks >= 0; blocks--) {
                int currentBlock = (int) blocks;
                ArrayList<double[]> matrix = new ArrayList<>();
                for (int i = 0; i < numberOfCityPerBlock; i++) {
                    // Making sure the coordinate each block is within a range
                    int xCoordinate = rand.nextInt(10*(i+1));
                    int yCoordinate = rand.nextInt(10*(i+1));
                    double infectionProbability = rand.nextDouble();

                    double[] coordinate = new double[3];
                    coordinate[0] = xCoordinate;
                    coordinate[1] = yCoordinate;
                    coordinate[2] = infectionProbability;

                    matrix.add(coordinate);
                }

                // n-1 slave Blocks
                if (blocks != 0) {
                    Threading thread = new Threading(matrix, numberOfCityPerBlock, blocks);
                    new Thread(thread).start();
                    universalMatrix.addAll(matrix);
                }


                // 1 master block
                if (blocks == 0) {
                    Threading thread = new Threading(matrix, numberOfCityPerBlock, 0);
                    new Thread(thread).start();
                    universalMatrix.addAll(matrix);

                    // Closing the path
                    TimeUnit.SECONDS.sleep(1);
                    totalTpsPath.add(totalTpsPath.get(0));
                    System.out.println("\nWeighted Adjacency Matrix: ");
                    printMatrix(getDistanceMatrix(universalMatrix));

                    // Row-wise Stitching
                    ArrayList<ArrayList<Integer>> afterRowWiseStitching = new ArrayList<>();
                    for (ArrayList<Integer[]> i: coordinateMatrix){
                        afterRowWiseStitching.add(getPartitionedTspPath(i));
                    }
                    System.out.println("\nAfter Row-wise Stitching");
                    for (int i = 0; i < afterRowWiseStitching.size(); i++){
                        if (i == blocks){
                            System.out.println("(Column Master process): Block 0" + " - TSP calculated " + afterRowWiseStitching.get(i) + " and remains in Block 0");
                        }
                       else{
                            System.out.println("(Row Master process): Block " + i*4 + "\t - TSP calculated " + afterRowWiseStitching.get(i) + " and send to Block 0");
                        }
                    }

                    // Column-wise Stitching
                    ArrayList<Integer[]> columnWiseStitching = new ArrayList<>();
                    for (ArrayList<Integer[]> i: coordinateMatrix){
                        columnWiseStitching = stitchingCoordinates(columnWiseStitching, i);
                    }

                    for(Integer[] i: columnWiseStitching ){
                        afterColumnWiseStitching.add(i[2]);
                    }

                    System.out.println("\nAfter Column-wise Stitching");
                    System.out.println(afterColumnWiseStitching);

                    System.out.println("\nTravelling Salesman Path (Before Inversion): ");
                    System.out.println("Total TSP: " + afterColumnWiseStitching);
                    System.out.println("\nTravelling Salesman Coordinates (Before Inversion): ");
                    printArraylistIntegerArray(columnWiseStitching, 2);
                    System.out.println("\nTotal Cost: " + totaltourCost);

                    ArrayList<Integer> beforeInversion = (ArrayList<Integer>) afterColumnWiseStitching.clone();
                    beforeInversion.add(beforeInversion.get(0));
                    ArrayList<Integer> inversionList = inversion(afterColumnWiseStitching);
                    inversionList.add(inversionList.get(0));
                    System.out.println("\nBefore Inversion:\t" + beforeInversion);
                    System.out.println("\nAfter Inversion:\t" + inversionList);

                }

            }

            long endTime = System.nanoTime();
            long executionTimeForMPITsp = endTime - startTime;
            System.out.println("Total Execution time: " + executionTimeForMPITsp + "\n");

        }

        else{
            System.out.println("Please give two arguments: number of blocks and number of cities per blocks. Ex - java MPITsp.java <number of blocks> <number of cities per blocks>");
        }

    }

    public static int multiply(int a, int b){
        System.out.println(a+b);
        return a*b;
    }

    // Return getPartitionedTspPath in a arrayList
    public static ArrayList<Integer> getPartitionedTspPath(ArrayList<Integer[]> old){
        ArrayList<Integer> newArrayList = new ArrayList<>();
        for (int i = 0; i < old.size(); i++){
            Integer[] array = old.get(i);
            for (int j = 0; j < array.length; j++){
                if (j == array.length-1){
                    newArrayList.add(array[j]);
                }
            }
        }
        return newArrayList;
    }
}