package org.jebol.domain.render;

import java.util.ArrayList;
import java.util.List;

final class GouraudMesh {

    private static final int PIECES_ALONG_EACH_EDGE = 16;

    private static final double SPREAD_TO_HIDE_THE_SEAMS = 0.5;

    private GouraudMesh() {
    }

    record Corner(double across, double down, Colour colour) {
    }

    record Piece(List<PathStep> path, Colour colour) {
    }

    static List<Piece> shading(Corner first, Corner second, Corner third) {
        List<Piece> pieces = new ArrayList<>();
        for (int down = 0; down < PIECES_ALONG_EACH_EDGE; down++) {
            for (int across = 0; across < PIECES_ALONG_EACH_EDGE - down; across++) {
                pieces.add(pointingOneWay(first, second, third, across, down));
                if (across < PIECES_ALONG_EACH_EDGE - down - 1) {
                    pieces.add(pointingTheOther(first, second, third, across, down));
                }
            }
        }
        return List.copyOf(pieces);
    }

    private static Piece pointingOneWay(
            Corner first, Corner second, Corner third, int across, int down) {

        return aPieceThrough(first, second, third,
                new double[][] {
                        {across, down}, {across + 1, down}, {across, down + 1}});
    }

    private static Piece pointingTheOther(
            Corner first, Corner second, Corner third, int across, int down) {

        return aPieceThrough(first, second, third,
                new double[][] {
                        {across + 1, down}, {across + 1, down + 1}, {across, down + 1}});
    }

    private static Piece aPieceThrough(
            Corner first, Corner second, Corner third, double[][] atThe) {

        double middleAcross = (atThe[0][0] + atThe[1][0] + atThe[2][0]) / 3;
        double middleDown = (atThe[0][1] + atThe[1][1] + atThe[2][1]) / 3;
        List<PathStep> path = new ArrayList<>();
        for (int corner = 0; corner < atThe.length; corner++) {
            double alongFirst = spreadFromTheMiddle(atThe[corner][0], middleAcross);
            double alongSecond = spreadFromTheMiddle(atThe[corner][1], middleDown);
            double[] point = pointAt(first, second, third, alongFirst, alongSecond);
            path.add(corner == 0
                    ? new PathStep.MoveTo(point[0], point[1])
                    : new PathStep.LineTo(point[0], point[1]));
        }
        path.add(new PathStep.Close());
        return new Piece(List.copyOf(path),
                colourAt(first, second, third, middleAcross, middleDown));
    }

    private static double spreadFromTheMiddle(double corner, double middle) {
        return corner + (corner - middle) * SPREAD_TO_HIDE_THE_SEAMS;
    }

    private static double[] pointAt(
            Corner first, Corner second, Corner third,
            double alongSecond, double alongThird) {

        double towardsSecond = alongSecond / PIECES_ALONG_EACH_EDGE;
        double towardsThird = alongThird / PIECES_ALONG_EACH_EDGE;
        double staying = 1 - towardsSecond - towardsThird;
        return new double[] {
                first.across() * staying + second.across() * towardsSecond
                        + third.across() * towardsThird,
                first.down() * staying + second.down() * towardsSecond
                        + third.down() * towardsThird};
    }

    private static Colour colourAt(
            Corner first, Corner second, Corner third,
            double alongSecond, double alongThird) {

        double towardsSecond = alongSecond / PIECES_ALONG_EACH_EDGE;
        double towardsThird = alongThird / PIECES_ALONG_EACH_EDGE;
        double staying = 1 - towardsSecond - towardsThird;
        return new Colour(
                mixed(first.colour().red(), second.colour().red(), third.colour().red(),
                        staying, towardsSecond, towardsThird),
                mixed(first.colour().green(), second.colour().green(),
                        third.colour().green(), staying, towardsSecond, towardsThird),
                mixed(first.colour().blue(), second.colour().blue(),
                        third.colour().blue(), staying, towardsSecond, towardsThird));
    }

    private static int mixed(
            int first, int second, int third,
            double staying, double towardsSecond, double towardsThird) {

        double blended = first * staying + second * towardsSecond + third * towardsThird;
        return Math.clamp((int) Math.round(blended), 0, 255);
    }
}
