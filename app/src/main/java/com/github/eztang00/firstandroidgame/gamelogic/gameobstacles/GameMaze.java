package com.github.eztang00.firstandroidgame.gamelogic.gameobstacles;

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.eztang00.firstandroidgame.SaveAndLoad;
import com.github.eztang00.firstandroidgame.gamephysics.DoublePoint;
import com.github.eztang00.firstandroidgame.gamephysics.GamePolyarcgon;
import com.github.eztang00.firstandroidgame.gamephysics.GamePolyarcgonBuilder;
import com.github.eztang00.firstandroidgame.gamephysics.OverlapCalculator;
import com.github.eztang00.firstandroidgame.gamephysics.PolyarcgonPoint;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;

/**
 * A GameMaze represents a maze as a set of segments connecting points
 * to each other.
 *
 * It is meant to be converted to a GamePolyarcgon with the build() method
 */
public class GameMaze {

    public static class GameCircularMaze {
        public int randomSeedUsed = 0;
        double pathThickness;
        double wallThickness;
        ArrayList<GameCircularMazeCell> cells;
        ArrayList<GameMazeWall> mainWalls;
        ArrayList<GameMazeWall> outerWalls;

        static class GameCircularMazeCell {
            double approximateX;
            double approximateY;
            LinkedHashMap<GameCircularMazeCell, GameMazeWall> neighbours;

            public GameCircularMazeCell(double approximateX, double approximateY) {
                this.approximateX = approximateX;
                this.approximateY = approximateY;
                this.neighbours = new LinkedHashMap<>();
            }

            public GameCircularMazeCell() {
                this(0, 0);
            }

            public void connect(GameCircularMazeCell other, GameMazeWall wallInBetween) {
                neighbours.put(other, wallInBetween);
                other.neighbours.put(this, wallInBetween);
            }
        }

        public GameCircularMaze(double x, double y, int rings, double pathThickness, double wallThickness, boolean smallCenter) {
            this.pathThickness = pathThickness;
            this.wallThickness = wallThickness;
            cells = new ArrayList<>();
            mainWalls = new ArrayList<>();
            outerWalls = new ArrayList<>();
            cells.add(new GameCircularMazeCell(x, y));
            int prevSubDivisions = 1;
            double wallRadius = smallCenter ? pathThickness / 2.0 + wallThickness / 2.0 : pathThickness + wallThickness / 2.0;
            for (int i = 0; i < rings; i++) {
                if (i < rings - 1) {
                    double nextWallRadius = wallRadius + pathThickness + wallThickness;
                    double cellRadius = (wallRadius + nextWallRadius) / 2.0;

                    double numberOfSubDivisionsThatFit = 2 * Math.PI / (2 * Math.asin((pathThickness + wallThickness) / (2 * wallRadius)));
                    int nextSubDivisions = (int) Math.pow(2, (int) (Math.log(numberOfSubDivisionsThatFit) / Math.log(2) + MAX_ROUNDING_ERROR)); // need rounding error here otherwise 2.0 becomes 1.0
                    int cellsBeforeThisRing = cells.size();

                    // first create the cells so can connect them in a loop
                    for (int j = 0; j < nextSubDivisions; j++) {
                        cells.add(new GameCircularMazeCell());
                    }

                    GameCircularMazeCell lastCell = cells.get(cells.size() - 1);
                    for (int j = 0; j < nextSubDivisions; j++) {
                        double startAngle = 2.0 * Math.PI * (((double) j) / nextSubDivisions);
                        double endAngle = 2.0 * Math.PI * (((double) j + 1.0) / nextSubDivisions);
                        GameCircularMazeCell newCell = cells.get(cellsBeforeThisRing + j);
                        newCell.approximateX = x + cellRadius * Math.cos((startAngle + endAngle) / 2);
                        newCell.approximateY = y + cellRadius * Math.sin((startAngle + endAngle) / 2);
                        GameCircularMazeCell connectedInnerCell = cells.get(cellsBeforeThisRing - prevSubDivisions + prevSubDivisions * j / nextSubDivisions);
                        double cosStartAngle = Math.cos(startAngle);
                        double sinStartAngle = Math.sin(startAngle);
                        double cosEndAngle = (j == nextSubDivisions - 1) ? 1 : Math.cos(endAngle); //avoid rounding error otherwise points aren't equal
                        double sinEndAngle = (j == nextSubDivisions - 1) ? 0 : Math.sin(endAngle);
                        GameMazeWall innerWall = new GameMazeWall(x + wallRadius * cosStartAngle, y + wallRadius * sinStartAngle, x + wallRadius * cosEndAngle, y + wallRadius * sinEndAngle, endAngle - startAngle);
                        newCell.connect(connectedInnerCell, innerWall);
                        mainWalls.add(innerWall);

                        GameMazeWall radialWall = new GameMazeWall(innerWall.start.x, innerWall.start.y, x + nextWallRadius * cosStartAngle, y + nextWallRadius * sinStartAngle, 0);
                        newCell.connect(lastCell, radialWall);
                        mainWalls.add(radialWall);
                        lastCell = newCell;
                    }

                    wallRadius = nextWallRadius;
                    prevSubDivisions = nextSubDivisions;
                } else {
//                    outerWall1 = new GameMazeWall(x-wallRadius, y, x+wallRadius, y, Math.PI);
//                    outerWall2 = new GameMazeWall(x+wallRadius, y, x-wallRadius, y, Math.PI);
                    for (int j = 0; j < prevSubDivisions; j++) {
                        double startAngle = 2.0 * Math.PI * (((double) j) / prevSubDivisions);
                        double endAngle = 2.0 * Math.PI * (((double) j + 1.0) / prevSubDivisions);
                        double cosStartAngle = Math.cos(startAngle);
                        double sinStartAngle = Math.sin(startAngle);
                        double cosEndAngle = (j == prevSubDivisions - 1) ? 1 : Math.cos(endAngle); //avoid rounding error otherwise points aren't equal
                        double sinEndAngle = (j == prevSubDivisions - 1) ? 0 : Math.sin(endAngle);
                        GameMazeWall outerWall = new GameMazeWall(x + wallRadius * cosStartAngle, y + wallRadius * sinStartAngle, x + wallRadius * cosEndAngle, y + wallRadius * sinEndAngle, endAngle - startAngle);
                        outerWalls.add(outerWall);
                    }
                }
            }
        }

        public void randomize(double startX, double startY, double finishX, double finishY, int seed) {
            double closestDistanceSqToStart = Double.MAX_VALUE;
            double closestDistanceSqToFinish = Double.MAX_VALUE;
            GameCircularMazeCell startCell = null;
            GameCircularMazeCell finishCell = null;
            for (GameCircularMazeCell cell : cells) {
                double distanceSqToStart = (cell.approximateX - startX) * (cell.approximateX - startX) + (cell.approximateY - startY) * (cell.approximateY - startY);
                double distanceSqToFinish = (cell.approximateX - finishX) * (cell.approximateX - finishX) + (cell.approximateY - finishY) * (cell.approximateY - finishY);
                if (distanceSqToStart < closestDistanceSqToStart && distanceSqToFinish < closestDistanceSqToFinish) {
                    if (distanceSqToStart / closestDistanceSqToStart < distanceSqToFinish / closestDistanceSqToFinish) {
                        startCell = cell;
                        closestDistanceSqToStart = distanceSqToStart;
                    } else {
                        finishCell = cell;
                        closestDistanceSqToFinish = distanceSqToFinish;
                    }
                } else if (distanceSqToStart < closestDistanceSqToStart) {
                    startCell = cell;
                    closestDistanceSqToStart = distanceSqToStart;
                } else if (distanceSqToFinish < closestDistanceSqToFinish) {
                    finishCell = cell;
                    closestDistanceSqToFinish = distanceSqToFinish;
                }
            }

            ArrayList<GameMazeWall> wallsToRemove = new ArrayList<>();
            // use linked because want to generate same maze for each seed
            // do not use ArraySet, it did not iterate the same way
            LinkedHashSet<GameCircularMazeCell> cellsAlreadyConnected = new LinkedHashSet<>();
            Random random = null;
//int testing = 0;
            int maxAttempts = 100000;
            attemptsLoop:
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                randomSeedUsed = (random == null) ? seed : random.nextInt();
                random = new Random(randomSeedUsed);
                wallsToRemove.clear();
                cellsAlreadyConnected.clear();
                GameCircularMazeCell currentCell = startCell;
                cellsAlreadyConnected.add(currentCell);
                while (currentCell != finishCell) {
//if (testing++ < 100) {
//    String connected = "";
//    for (GameCircularMazeCell celly : cellsAlreadyConnected) {
//        connected += cells.indexOf(celly) + ", ";
//    }
//    Log.i("me", seed + ", " + connected);
//}
                    currentCell = findAndProcessNeighbour(currentCell, cellsAlreadyConnected, wallsToRemove, true, random);
                    if (currentCell == null) {
                        continue attemptsLoop; // no unfinished neighbours, the path trapped itself
                    }
                }
                if (cellsAlreadyConnected.size() >= 0.3 * cells.size() && cellsAlreadyConnected.size() <= 0.6 * cells.size()) {
                    // good maze, no short path to finish, also no ridiculously long path like a labyrinth
                    // 30%-60% of maze for correct path, rest for wrong paths and "closets" along correct path.
                    break;
                }
            }

            ArrayList<GameCircularMazeCell> cellsNotYetConnected = new ArrayList<>(cells);
            while (cellsAlreadyConnected.size() < cells.size()) {
                cellsNotYetConnected.removeAll(cellsAlreadyConnected);
                int start = random.nextInt(cellsNotYetConnected.size());
                GameCircularMazeCell currentCell = cellsNotYetConnected.get(start);
                GameCircularMazeCell neighbour = findAndProcessNeighbour(currentCell, cellsAlreadyConnected, wallsToRemove, false, random);
                if (neighbour == null) {
                    continue; // no neighbours
                }
                cellsAlreadyConnected.add(currentCell);
                do {
                    //try to make a branch
                    currentCell = findAndProcessNeighbour(currentCell, cellsAlreadyConnected, wallsToRemove, true, random);
                } while (currentCell != null); // don't stop until trapped to use up as many cells in long paths
            }

            mainWalls.removeAll(wallsToRemove);
        }


        private static GameCircularMazeCell findAndProcessNeighbour(GameCircularMazeCell currentCell, LinkedHashSet<GameCircularMazeCell> cellsAlreadyConnected, ArrayList<GameMazeWall> wallsToRemove, boolean pickNewNeighbour, Random random) {
            ArrayList<GameCircularMazeCell> pickableNeighbours = new ArrayList<>();
            for (GameCircularMazeCell cell : currentCell.neighbours.keySet()) {
                if (pickNewNeighbour == !cellsAlreadyConnected.contains(cell)) {
                    pickableNeighbours.add(cell);
                }
            }
            if (pickableNeighbours.isEmpty()) {
                // whoops we're trapped
                return null;
            }
            int neighbour = random.nextInt(pickableNeighbours.size());
            GameCircularMazeCell neighbourCell = pickableNeighbours.get(neighbour);
            wallsToRemove.add(currentCell.neighbours.get(neighbourCell));
            if (pickNewNeighbour) {
                cellsAlreadyConnected.add(neighbourCell);
            }
            return neighbourCell;
        }

        public GameMaze asGameMaze() {
            GameMaze maze = new GameMaze();
            maze.walls.addAll(mainWalls);
            maze.walls.addAll(outerWalls);
//            maze.addWall(outerWall1);
//            maze.addWall(outerWall2);
            return maze;
        }

        public void build(GamePolyarcgonBuilder builder) {
            asGameMaze().build(builder, wallThickness);
        }

    }

    static final double MAX_ROUNDING_ERROR = 1000000.0 * Double.MIN_VALUE / Double.MIN_NORMAL;

    ArrayList<GameMazeWall> walls;

    public static void saveMaze(GameMaze maze, double x, double y, double pathThickness) {
        ArrayList<GameMazeWall> templateWalls = new ArrayList<>();
        for (GameMazeWall wall : maze.walls) {
            templateWalls.add(new GameMazeWall((wall.start.x - x) / pathThickness, (wall.start.y - y) / pathThickness, (wall.end.x - x) / pathThickness, (wall.end.y - y) / pathThickness, wall.arcAngleChange));
        }

        Gson gson = new Gson(); // Or use new GsonBuilder().create();
        String json = gson.toJson(templateWalls); // serializes target to JSON

//        Log.i("me", json);
        System.out.println(json);
    }

    public static GameMaze loadMaze(Context context, int id, double x, double y, double pathThickness) {
        ArrayList<GameMazeWall> templateWalls = SaveAndLoad.gsonLoadRawResource(context, id, new TypeToken<ArrayList<GameMazeWall>>() {
        }.getType());
        GameMaze maze = new GameMaze();
        for (GameMazeWall wall : templateWalls) {
            maze.walls.add(new GameMazeWall(wall.start.x * pathThickness + x, wall.start.y * pathThickness + y, wall.end.x * pathThickness + x, wall.end.y * pathThickness + y, wall.arcAngleChange));
        }
        return maze;
    }

    public GameMaze() {
        walls = new ArrayList<>();
    }

    public void addWall(GameMazeWall wall) {
        //TODO: check for intersections
        HashMap<GameMazeWall, TreeMap<Double, DoublePoint>> intersections = new HashMap<>();
        for (GameMazeWall prevWall : walls) {
            getIntersections(intersections, wall, prevWall);
        }
        boolean cutWall = false;
        for (Map.Entry<GameMazeWall, TreeMap<Double, DoublePoint>> intersectionDataEntry : intersections.entrySet()) {
            GameMazeWall wallToCut = intersectionDataEntry.getKey();
            if (wallToCut == wall) {
                cutWall = true;
            } else {
                walls.remove(wallToCut);
            }
            DoublePoint subWallStart = wallToCut.start;
            double subWallStartFraction = 0;
            for (Map.Entry<Double, DoublePoint> intersection : intersectionDataEntry.getValue().entrySet()) {
                DoublePoint subWallEnd = intersection.getValue();
                double subWallEndFraction = intersection.getKey();
                if (subWallEndFraction > MAX_ROUNDING_ERROR || subWallStartFraction != 0) {
                    walls.add(new GameMazeWall(subWallStart, subWallEnd, wall.arcAngleChange * (subWallEndFraction - subWallStartFraction)));
                }
                subWallStart = subWallEnd;
                subWallStartFraction = subWallEndFraction;
            }
            if (subWallStartFraction < 1 - MAX_ROUNDING_ERROR) {
                walls.add(new GameMazeWall(subWallStart, wallToCut.end, wall.arcAngleChange * (1 - subWallStartFraction)));
            }
        }
        if (!cutWall) {
            walls.add(wall);
        }
    }


    private static void getIntersections(HashMap<GameMazeWall, TreeMap<Double, DoublePoint>> intersections, GameMazeWall segment1, GameMazeWall segment2) {
        GetPolyarcgonPointAndCacheFromWall segment1PointCache = new GetPolyarcgonPointAndCacheFromWall(segment1);
        GetPolyarcgonPointAndCacheFromWall segment2PointCache = new GetPolyarcgonPointAndCacheFromWall(segment2);
        /*
        Note this is different than the GamePolyarcgonBuilder.IntersectionsCalculator
        because it just lists "real" intersections not fake intersections
         */
        OverlapCalculator handler = new OverlapCalculator() {
            @Override
            public void addLineSegmentToOverlap(double startX, double startY, double endX, double endY, int windingFactor, boolean lineSegmentIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {
                if (!isRealIntersection) {
                    return;
                }

                final GameMazeWall segmentToMark = lineSegmentIsFirstShape ? segment1 : segment2;
                final GetPolyarcgonPointAndCacheFromWall segmentToMarkPointCache = lineSegmentIsFirstShape ? segment1PointCache : segment2PointCache;
                final double distance = Math.sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY));
                final double fullDistance = Math.sqrt((segmentToMarkPointCache.endPoint.x - segmentToMarkPointCache.startPoint.x) * (segmentToMarkPointCache.endPoint.x - segmentToMarkPointCache.startPoint.x) + (segmentToMarkPointCache.endPoint.y - segmentToMarkPointCache.startPoint.y) * (segmentToMarkPointCache.endPoint.y - segmentToMarkPointCache.startPoint.y));
                final double fraction = distance / fullDistance;
                TreeMap<Double, DoublePoint> intersectionData = intersections.get(segmentToMark);
                if (intersectionData == null) {
                    intersectionData = new TreeMap<>();
                    intersections.put(segmentToMark, intersectionData);
                }
                intersectionData.put(fraction, new DoublePoint(endX, endY));
            }

            @Override
            public void addArcToOverlap(double radiusOfCurvature, double arcCenterX, double arcCenterY, double arcStartX, double arcStartY, double arcEndX, double arcEndY, double arcAngleChange, int windingFactor, boolean arcIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {
                if (!isRealIntersection) {
                    return;
                }

                final GameMazeWall segmentToMark = arcIsFirstShape ? segment1 : segment2;
                final double fraction = segmentToMark.arcAngleChange / arcAngleChange;
                TreeMap<Double, DoublePoint> intersectionData = intersections.get(segmentToMark);
                if (intersectionData == null) {
                    intersectionData = new TreeMap<>();
                    intersections.put(segmentToMark, intersectionData);
                }
                intersectionData.put(fraction, new DoublePoint(arcEndX, arcEndY));
            }
        };
        if (segment1PointCache.endPointCache instanceof GamePolyarcgon.PolyarcgonStraightPointCache) {
            if (segment2PointCache.endPointCache instanceof GamePolyarcgon.PolyarcgonStraightPointCache) {
                GamePolyarcgon.addPotentialLineSegmentIntersectionWithLineSegmentToOverlap(handler, segment1PointCache.startPointCache, (GamePolyarcgon.PolyarcgonStraightPointCache) segment1PointCache.endPointCache, segment2PointCache.startPointCache, (GamePolyarcgon.PolyarcgonStraightPointCache) segment2PointCache.endPointCache, MAX_ROUNDING_ERROR);
            } else {
                GamePolyarcgon.addPotentialLineSegmentIntersectionWithArcToOverlap(handler, segment1PointCache.startPointCache, (GamePolyarcgon.PolyarcgonStraightPointCache) segment1PointCache.endPointCache, segment2PointCache.startPointCache, (GamePolyarcgon.PolyarcgonArcedPointCache) segment2PointCache.endPointCache, true, MAX_ROUNDING_ERROR);
            }
        } else {
            if (segment2PointCache.endPointCache instanceof GamePolyarcgon.PolyarcgonStraightPointCache) {
                GamePolyarcgon.addPotentialLineSegmentIntersectionWithArcToOverlap(handler, segment2PointCache.startPointCache, (GamePolyarcgon.PolyarcgonStraightPointCache) segment2PointCache.endPointCache, segment1PointCache.startPointCache, (GamePolyarcgon.PolyarcgonArcedPointCache) segment1PointCache.endPointCache, false, MAX_ROUNDING_ERROR);
            } else {
                GamePolyarcgon.addPotentialArcIntersectionWithArcToOverlap(handler, segment1PointCache.startPointCache, (GamePolyarcgon.PolyarcgonArcedPointCache) segment1PointCache.endPointCache, segment2PointCache.startPointCache, (GamePolyarcgon.PolyarcgonArcedPointCache) segment2PointCache.endPointCache, MAX_ROUNDING_ERROR);
            }
        }
    }

    static class GetPolyarcgonPointAndCacheFromWall {
        final PolyarcgonPoint startPoint;
        final PolyarcgonPoint endPoint;
        final GamePolyarcgon.PolyarcgonPointCache startPointCache;
        final GamePolyarcgon.PolyarcgonPointCache endPointCache;

        GetPolyarcgonPointAndCacheFromWall(GameMazeWall wall) {
            startPoint = new PolyarcgonPoint(wall.start.x, wall.start.y, wall.arcAngleChange, false);
            endPoint = new PolyarcgonPoint(wall.end.x, wall.end.y, wall.arcAngleChange, false);
            startPointCache = startPoint.isAlmostStraight() ? new GamePolyarcgon.PolyarcgonStraightPointCache(startPoint) : new GamePolyarcgon.PolyarcgonArcedPointCache(startPoint);
            endPointCache = endPoint.isAlmostStraight() ? new GamePolyarcgon.PolyarcgonStraightPointCache(endPoint) : new GamePolyarcgon.PolyarcgonArcedPointCache(endPoint);

            startPointCache.updatePositionCache(0, 0, 1, 0);
            endPointCache.updatePositionCache(0, 0, 1, 0);

            endPointCache.updateLineOrArcCache(startPointCache);
        }
    }
//    private static ArrayList<ContourlessSegment> convertToContourlessSegments(ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> contoursOfPoints) {
//        ArrayList<ContourlessSegment> contourlessSegments = new ArrayList<>();
//        for (ArrayList<GamePolyarcgonBuilderPoint> contour : contoursOfPoints) {
//            if (!contour.isEmpty()) {
//                ContourlessSegment firstContourlessSegment = null;
//                ContourlessSegment lastContourlessSegment = null;
//                for (GamePolyarcgonBuilderPoint point : contour) {
//                    ContourlessSegment contourlessSegment = new ContourlessSegment(point, null);
//                    contourlessSegments.add(contourlessSegment);
//                    if (lastContourlessSegment != null) {
//                        lastContourlessSegment.nextPoint = contourlessSegment;
//                    } else {
//                        firstContourlessSegment = contourlessSegment;
//                    }
//                    lastContourlessSegment = contourlessSegment;
//                }
//                lastContourlessSegment.nextPoint = firstContourlessSegment;
//            }
//        }
//        return contourlessSegments;
//    }
//    private static ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> convertToContoursOfPoints(ArrayList<ContourlessSegment> contourlessSegments) {
//        ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> contoursOfPoints = new ArrayList<>();
//        while (!contourlessSegments.isEmpty()) {
//            ArrayList<GamePolyarcgonBuilderPoint> contour = new ArrayList<>();
//            contoursOfPoints.add(contour);
//            ContourlessSegment contourlessSegment = contourlessSegments.remove(0);
//            contour.add(contourlessSegment.thisPoint);
//            ContourlessSegment nextContourlessSegment = contourlessSegment.nextPoint;
//            while (nextContourlessSegment != contourlessSegment) {
//                contourlessSegments.remove(nextContourlessSegment);
//                contour.add(nextContourlessSegment.thisPoint);
//                nextContourlessSegment = nextContourlessSegment.nextPoint;
//            }
//        }
//        return contoursOfPoints;
//    }


    static class AnglePlus implements Comparable<AnglePlus> {
        double angle;
        double curvature;

        public AnglePlus(double angle, double curvature) {
            this.angle = angle;
            this.curvature = curvature;
        }

        @Override
        public int compareTo(AnglePlus other) {
            if (this == other) {
                return 0;
            } else if (angle > other.angle + MAX_ROUNDING_ERROR) {
                return 1;
            } else if (angle < other.angle - MAX_ROUNDING_ERROR) {
                return -1;
            } else if (curvature > other.curvature + MAX_ROUNDING_ERROR) {
                return 1;
            } else if (curvature < other.curvature - MAX_ROUNDING_ERROR) {
                return -1;
            } else {
                return 0;
            }
        }
//        public static boolean almost180DegreesApart(AnglePlus angle1, AnglePlus angle2) {
//        }
    }

    public static class GameMazeWall {
        @NonNull
        DoublePoint start;
        @NonNull
        DoublePoint end;
        double arcAngleChange;

        public GameMazeWall(double x1, double y1, double x2, double y2, double arcAngleChange) {
            this(new DoublePoint(x1, y1), new DoublePoint(x2, y2), arcAngleChange);
        }

        public GameMazeWall(@NonNull DoublePoint start, @NonNull DoublePoint end, double arcAngleChange) {
            this.start = start;
            this.end = end;
            this.arcAngleChange = arcAngleChange;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GameMazeWall that = (GameMazeWall) o;
            return Double.compare(that.arcAngleChange, arcAngleChange) == 0 && start.equals(that.start) && end.equals(that.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end, arcAngleChange);
        }
    }

    static class NodeInfo {
        HashMap<GameMazeWall, AnglePlus> anglesOfConnectedWalls1;
        TreeMap<AnglePlus, GameMazeWall> anglesOfConnectedWalls2;
        // Really having two maps is unnecessary since each node has very few walls.
        // But TreeMap/HashMap have no methods for getting a key from a value

        NodeInfo() {
            anglesOfConnectedWalls1 = new HashMap<>();
            anglesOfConnectedWalls2 = new TreeMap<>();
        }

        void addWall(GameMazeWall wall, boolean nodeIsAtWallStartInsteadOfEnd) {
            double angle;
            int factor;
            if (nodeIsAtWallStartInsteadOfEnd) {
                factor = 1;
                angle = Math.atan2(wall.end.y - wall.start.y, wall.end.x - wall.start.x) - wall.arcAngleChange / 2.0;
            } else {
                factor = -1;
                angle = Math.atan2(wall.start.y - wall.end.y, wall.start.x - wall.end.x) + wall.arcAngleChange / 2.0;
            }
            AnglePlus anglePlus;
            if (PolyarcgonPoint.isAlmostStraight(wall.arcAngleChange)) {
                anglePlus = new AnglePlus(angle, 0);
            } else {
                anglePlus = new AnglePlus(angle, 1.0 * factor / GamePolyarcgon.getArcCenterAndSignedRadius(wall.start.x, wall.start.y, wall.end.x, wall.end.y, wall.arcAngleChange)[2]);
            }
            anglesOfConnectedWalls2.put(anglePlus, wall);
            anglesOfConnectedWalls1.put(wall, anglePlus);
        }

        private Map.Entry<AnglePlus, GameMazeWall> pickNextWallClockwise(GameMazeWall initialWall, boolean counterClockwiseInstead) {
            final AnglePlus angle = anglesOfConnectedWalls1.get(initialWall);
            Map.Entry<AnglePlus, GameMazeWall> next = counterClockwiseInstead ? anglesOfConnectedWalls2.lowerEntry(angle) : anglesOfConnectedWalls2.higherEntry(angle);
            if (next == null) {
                next = counterClockwiseInstead ? anglesOfConnectedWalls2.lastEntry() : anglesOfConnectedWalls2.firstEntry();
            }
            return next;
        }

    }

    public void build(GamePolyarcgonBuilder builder, double wallThickness) {
        builder.newContour();

        HashMap<DoublePoint, NodeInfo> nodes = new HashMap<>();
        for (GameMazeWall wall : walls) {
            NodeInfo startNodeInfo = nodes.get(wall.start);
            if (startNodeInfo == null) {
                startNodeInfo = new NodeInfo();
                nodes.put(wall.start, startNodeInfo);
            }
            startNodeInfo.addWall(wall, true);
            NodeInfo endNodeInfo = nodes.get(wall.end);
            if (endNodeInfo == null) {
                endNodeInfo = new NodeInfo();
                nodes.put(wall.end, endNodeInfo);
            }
            endNodeInfo.addWall(wall, false);
        }

        HashSet<GameMazeWall> rightSideCompletedWalls = new HashSet<>();
        HashSet<GameMazeWall> leftSideCompletedWalls = new HashSet<>();

        for (GameMazeWall wall : walls) {
            for (int direction = -1; direction == -1 || direction == 1; direction += 2) {
                DoublePoint directedStart;
                DoublePoint directedEnd;
                if (direction == 1) {
                    if (rightSideCompletedWalls.contains(wall)) {
                        continue;
                    }
                    directedStart = wall.start;
                    directedEnd = wall.end;
                } else {
                    if (leftSideCompletedWalls.contains(wall)) {
                        continue;
                    }
                    directedStart = wall.end;
                    directedEnd = wall.start;
                }
                // first, we back up to the "real" starting point:
                GetNextTurn getPrevTurn = new GetNextTurn(directedEnd, wall, nodes, true);

                if (getPrevTurn.isEndlessLoop) {
                    // if it's an endless loop, we're done just add a circle
                    rightSideCompletedWalls.addAll(getPrevTurn.rightSideWallsIncluded);
                    leftSideCompletedWalls.addAll(getPrevTurn.leftSideWallsIncluded);

                    double[] arcCenterAndSignedRadius = GamePolyarcgon.getArcCenterAndSignedRadius(directedStart.x, directedStart.y, directedEnd.x, directedEnd.y, direction * wall.arcAngleChange);
                    builder.addCircleContour(arcCenterAndSignedRadius[0], arcCenterAndSignedRadius[1], Math.abs(arcCenterAndSignedRadius[2] + wallThickness / 2.0), arcCenterAndSignedRadius[2] > 0);
                } else {
                    DoublePoint lastTurn = getPrevTurn.nextTurn;
                    GameMazeWall wallAfterLastTurn = getPrevTurn.wallBeforeNextTurn;

                    // next, we find the next turn:
                    do {
                        GetNextTurn getNextTurn = new GetNextTurn(lastTurn, wallAfterLastTurn, nodes, false);

                        rightSideCompletedWalls.addAll(getNextTurn.rightSideWallsIncluded);
                        leftSideCompletedWalls.addAll(getNextTurn.leftSideWallsIncluded);

                        double arcAngleChange = getNextTurn.totalArcAngleChange;
                        if (Math.abs(Math.abs(getNextTurn.totalArcAngleChange) - 2 * Math.PI) < MAX_ROUNDING_ERROR) {
                            //i.e. we've gone full circle and need an artificial point in between to avoid 360 degree arc angle change
                            DoublePoint pointRightAfterLastTurn;
                            double wallAfterLastTurnArcAngleChange;
                            if (lastTurn.equals(wallAfterLastTurn.start)) {
                                pointRightAfterLastTurn = wallAfterLastTurn.end;
                                wallAfterLastTurnArcAngleChange = wallAfterLastTurn.arcAngleChange;
                            } else {
                                pointRightAfterLastTurn = wallAfterLastTurn.start;
                                wallAfterLastTurnArcAngleChange = -wallAfterLastTurn.arcAngleChange;
                            }
                            builder.arcToVirtualRoundedTurn(pointRightAfterLastTurn.x, pointRightAfterLastTurn.y, wallAfterLastTurnArcAngleChange, 0, false, wallThickness / 2.0, true);
                            arcAngleChange -= wallAfterLastTurnArcAngleChange;
                        }
                        if (Math.abs(getNextTurn.turnAngle) < MAX_ROUNDING_ERROR) {
                            // i.e. it's an inflection point not a real turn
                            builder.arcToVirtualRoundedTurn(getNextTurn.nextTurn.x, getNextTurn.nextTurn.y, arcAngleChange, 0, false, wallThickness / 2.0, true);
                        } else {
                            builder.arcToVirtualRoundedTurn(getNextTurn.nextTurn.x, getNextTurn.nextTurn.y, arcAngleChange, wallThickness / 2.0, getNextTurn.turnAngle > 0, wallThickness / 2.0, true);
                        }
                        lastTurn = getNextTurn.nextTurn;
                        wallAfterLastTurn = getNextTurn.wallAfterNextTurn;
                    } while (!lastTurn.equals(getPrevTurn.nextTurn) || wallAfterLastTurn != getPrevTurn.wallBeforeNextTurn);
                    // We have to test both the turn and the wall because it's possible for either equality to be true
                    // without finishing the loop. e.g. at the end of each wall, you repeat the same wall twice but with
                    // a different turn. e.g. at the point of a Q shaped loop, you repeat the same point twice but
                    // without having finished looping the tip of the Q.

                    builder.newContour();
                }
            }
        }
    }

    static class GetNextTurn {
        final DoublePoint nextTurn;
        final GameMazeWall wallBeforeNextTurn;
        final GameMazeWall wallAfterNextTurn;
        final double totalArcAngleChange;
        final double turnAngle;
        final boolean isEndlessLoop; //Note the total arc angle change might be 360 degrees even if not endless loop
        final HashSet<GameMazeWall> rightSideWallsIncluded = new HashSet<>();
        final HashSet<GameMazeWall> leftSideWallsIncluded = new HashSet<>();

        GetNextTurn(DoublePoint startingTurn, GameMazeWall wallAfterStartingTurn, HashMap<DoublePoint, NodeInfo> nodes, boolean counterClockwiseInstead) {
            DoublePoint nextPoint = startingTurn;
            GameMazeWall wall = wallAfterStartingTurn;
            double subtotalArcAngleChange = 0;
            while (true) {
                // make next point refer to the point on the other side of the wall
                // also, calculate subtotalArcAngleChange
                if (wall.start.equals(nextPoint)) {
                    nextPoint = wall.end;
                    subtotalArcAngleChange += wall.arcAngleChange;
                    (counterClockwiseInstead ? leftSideWallsIncluded : rightSideWallsIncluded).add(wall);
                } else {
                    nextPoint = wall.start;
                    subtotalArcAngleChange -= wall.arcAngleChange;
                    (counterClockwiseInstead ? rightSideWallsIncluded : leftSideWallsIncluded).add(wall);
                }

                NodeInfo nextNode = nodes.get(nextPoint);
                assert nextNode != null;
                AnglePlus thisAngle = nextNode.anglesOfConnectedWalls1.get(wall);
                assert thisAngle != null;
                Map.Entry<AnglePlus, GameMazeWall> nextAngleAndWall = nextNode.pickNextWallClockwise(wall, counterClockwiseInstead);
                AnglePlus nextAngle = nextAngleAndWall.getKey();
                GameMazeWall nextWall = nextAngleAndWall.getValue();
                double currentTurnAngle;
                if (wall != nextWall) {
                    currentTurnAngle = ((nextAngle.angle - thisAngle.angle + 10 * Math.PI) % (2 * Math.PI)) - Math.PI;
                } else {
                    currentTurnAngle = counterClockwiseInstead ? -Math.PI : Math.PI;
                }

                // if next angle and this angle are almost 180 degrees apart
                if (Math.abs(currentTurnAngle) < MAX_ROUNDING_ERROR) {
                    if (Math.abs(nextAngle.curvature + thisAngle.curvature) < MAX_ROUNDING_ERROR) {
                        wall = nextWall;
                        // We only declare an endless loop if the wall is the same as before,
                        // not if the node is the same as before.
                        // Even if the node is the same as before it might not be an endless loop,
                        // if another wall comes out of the node.
                        if (wall == wallAfterStartingTurn) {
                            nextTurn = null;
                            wallBeforeNextTurn = null;
                            wallAfterNextTurn = null;
                            totalArcAngleChange = subtotalArcAngleChange;
                            turnAngle = 0;
                            isEndlessLoop = true;
                            return;
                        } else {
                            continue;
                        }
                    } else {
                        turnAngle = 0;
                    }
                } else {
                    turnAngle = currentTurnAngle;
                }
                nextTurn = nextPoint;
                wallBeforeNextTurn = wall;
                wallAfterNextTurn = nextWall;
                totalArcAngleChange = subtotalArcAngleChange;
                isEndlessLoop = false;
                break;
            }

        }
    }

}
