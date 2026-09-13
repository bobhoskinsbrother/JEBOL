package org.jebol.domain.render;

import java.util.ArrayList;
import java.util.List;

final class Dashes {

    private static final int STEPS_ALONG_A_CURVE = 48;

    private Dashes() {
    }

    private record Point(double across, double down) {
    }

    static List<PathStep> theGapsIn(List<PathStep> path, List<Double> pattern) {
        List<Point> walked = walkedOut(path);
        if (walked.size() < 2 || pattern.isEmpty()) {
            return List.of();
        }
        return piecesOf(walked, pattern, false);
    }

    static List<PathStep> theDashesIn(List<PathStep> path, List<Double> pattern) {
        List<Point> walked = walkedOut(path);
        if (walked.size() < 2 || pattern.isEmpty()) {
            return List.of();
        }
        return piecesOf(walked, pattern, true);
    }

    private static List<PathStep> piecesOf(
            List<Point> walked, List<Double> pattern, boolean wantTheDashes) {

        List<PathStep> pieces = new ArrayList<>();
        int inThePattern = 0;
        double leftInThisPiece = lengthAt(pattern, 0);
        boolean drawingADash = true;
        boolean penIsDown = false;

        for (int at = 1; at < walked.size(); at++) {
            Point from = walked.get(at - 1);
            Point to = walked.get(at);
            double wholeSegment = Math.hypot(
                    to.across() - from.across(), to.down() - from.down());
            double doneSoFar = 0;
            while (doneSoFar < wholeSegment) {
                double step = Math.min(leftInThisPiece, wholeSegment - doneSoFar);
                if (drawingADash == wantTheDashes) {
                    Point one = partWayAlong(from, to, doneSoFar, wholeSegment);
                    Point other = partWayAlong(from, to, doneSoFar + step, wholeSegment);
                    if (!penIsDown) {
                        pieces.add(new PathStep.MoveTo(one.across(), one.down()));
                        penIsDown = true;
                    }
                    pieces.add(new PathStep.LineTo(other.across(), other.down()));
                }
                doneSoFar += step;
                leftInThisPiece -= step;
                if (leftInThisPiece <= A_LENGTH_TOO_SHORT_TO_MATTER) {
                    inThePattern = (inThePattern + 1) % pattern.size();
                    leftInThisPiece = lengthAt(pattern, inThePattern);
                    drawingADash = !drawingADash;
                    penIsDown = false;
                }
            }
        }
        return List.copyOf(pieces);
    }

    private static final double A_LENGTH_TOO_SHORT_TO_MATTER = 0.000000001;

    private static Point partWayAlong(
            Point from, Point to, double along, double wholeSegment) {

        double share = wholeSegment == 0 ? 0 : along / wholeSegment;
        return new Point(
                from.across() + (to.across() - from.across()) * share,
                from.down() + (to.down() - from.down()) * share);
    }

    private static double lengthAt(List<Double> pattern, int at) {
        double length = pattern.get(at % pattern.size());
        return length > 0 ? length : 1;
    }

    private static List<Point> walkedOut(List<PathStep> path) {
        List<Point> walked = new ArrayList<>();
        Point standing = new Point(0, 0);
        Point startedAt = standing;
        for (PathStep step : path) {
            switch (step) {
                case PathStep.MoveTo to -> {
                    standing = new Point(to.across(), to.down());
                    startedAt = standing;
                    walked.add(standing);
                }
                case PathStep.LineTo to -> {
                    standing = new Point(to.across(), to.down());
                    walked.add(standing);
                }
                case PathStep.QuadraticTo to -> {
                    standing = alongAQuadratic(walked, standing, to);
                }
                case PathStep.CubicTo to -> {
                    standing = alongACubic(walked, standing, to);
                }
                case PathStep.Close ignored -> walked.add(startedAt);
                default -> {
                }
            }
        }
        return walked;
    }

    private static Point alongAQuadratic(
            List<Point> walked, Point from, PathStep.QuadraticTo to) {

        for (int step = 1; step <= STEPS_ALONG_A_CURVE; step++) {
            double along = step / (double) STEPS_ALONG_A_CURVE;
            double staying = 1 - along;
            walked.add(new Point(
                    staying * staying * from.across()
                            + 2 * staying * along * to.controlAcross()
                            + along * along * to.across(),
                    staying * staying * from.down()
                            + 2 * staying * along * to.controlDown()
                            + along * along * to.down()));
        }
        return new Point(to.across(), to.down());
    }

    private static Point alongACubic(
            List<Point> walked, Point from, PathStep.CubicTo to) {

        for (int step = 1; step <= STEPS_ALONG_A_CURVE; step++) {
            double along = step / (double) STEPS_ALONG_A_CURVE;
            double staying = 1 - along;
            walked.add(new Point(
                    staying * staying * staying * from.across()
                            + 3 * staying * staying * along * to.firstControlAcross()
                            + 3 * staying * along * along * to.secondControlAcross()
                            + along * along * along * to.across(),
                    staying * staying * staying * from.down()
                            + 3 * staying * staying * along * to.firstControlDown()
                            + 3 * staying * along * along * to.secondControlDown()
                            + along * along * along * to.down()));
        }
        return new Point(to.across(), to.down());
    }
}
