import library.core.*;
import java.util.*;

class Cursor extends PComponent {
    Builder builder;
    public PVector pos;

    boolean snappingToLeftOfSegment = false;
    boolean snappingToRightOfSegment = false;
    Segment snappingToSegment;
    PVector snappingLeftRightPoint = null;
    boolean snappingAnchor = false;
    boolean snappingMiddleSegment = false;
    /**
     * Angle only used for showSnapping()
     */
    float snappingMiddleSegmentAngle = 0;
    Segment snappingMiddleSegmentSegment = null;
    boolean snappingWall = false;
    boolean snappingMiddleScreen = false;
    boolean snapping90 = false;
    boolean snappingHeading = false;
    PVector snappingHeadingPoint = null;
    boolean snappingGuidelines = false;
    PVector snappingGuidelinesPoint = null;

    /**
     * Left/right snapping, anchor snapping, wall snapping, 90 snapping, heading
     * snapping, guidelines snapping
     */
    boolean[] enabledSnappingOptions = { true, true, true, true, true, true };

    boolean fixedX = false;
    boolean fixedY = false;

    Segment selectedStartControlPoint = null;
    Segment selectedEndControlPoint = null;
    Anchor draggedAnchor = null;

    float epsilon = 0.0001f;
    float radianMargin = 0.1f;
    float pixelMargin = 15;

    public Cursor(Builder builder) {
        this.builder = builder;
    }

    public void update(Segment currentSegment, ArrayList<Anchor> anchors, ArrayList<Segment> segments) {
        resetVariables();

        if (builder.hoveringSegmentInfo())
            return;

        // Absolute snaps (cannot combine with other snapping)
        if (enabledSnappingOptions[0] && snapLeftAndRightOfSegment(currentSegment, segments)) {
            return;
        }
        if (enabledSnappingOptions[1] && snapAnchors(currentSegment, anchors)) {
            return;
        }
        // TODO: Make this togglable
        if (snapMiddleSegment()) {
            return;
        }

        // Combination snaps
        if (enabledSnappingOptions[2]) {
            snapWalls();
        }

        // TODO: Make this togglable
        snapMiddleScreen();

        snapAngles(currentSegment);

        if (enabledSnappingOptions[5]) {
            snapGuidelines();
        }
    }

    public void show() {
        noStroke();
        fill(0);
        circle(pos, Settings.sizeAnchor);
    }

    private void resetVariables() {
        pos = mouse;

        snappingToLeftOfSegment = false;
        snappingToRightOfSegment = false;
        snappingToSegment = null;
        snappingLeftRightPoint = null;
        snappingAnchor = false;
        snappingMiddleSegment = false;
        snappingMiddleSegmentAngle = 0;
        snappingMiddleSegmentSegment = null;
        snappingWall = false;
        snappingMiddleScreen = false;
        snapping90 = false;
        snappingHeading = false;
        snappingHeadingPoint = null;
        snappingGuidelines = false;
        snappingGuidelinesPoint = null;

        fixedX = false;
        fixedY = false;
    }

    private boolean snapLeftAndRightOfSegment(Segment currentSegment, ArrayList<Segment> segments) {
        if (currentSegment != null && builder.segmentPlaced)
            return false;

        for (Segment segment : segments) {
            if (segment == currentSegment)
                continue;
            if (currentSegment != null && currentSegment.controlledSegments != null
                    && currentSegment.controlledSegments.contains(segment))
                continue;
            if (currentSegment != null && currentSegment.snappedToSegments != null
                    && currentSegment.snappedToSegments.contains(segment))
                continue;

            PVector node = (currentSegment == null) ? segment.getStartNode() : segment.getEndNode();
            float heading = (currentSegment == null) ? segment.startHeading : segment.endHeading;

            float myWidth = (currentSegment != null) ? currentSegment.segmentWidth : segment.segmentWidth;
            PVector pseudoAnchorLeft = PVector.add(node,
                    PVector.fromAngle(heading - PI / 2).setMag(segment.segmentWidth / 2 + myWidth / 2));
            PVector pseudoAnchorRight = PVector.add(node,
                    PVector.fromAngle(heading + PI / 2).setMag(segment.segmentWidth / 2 + myWidth / 2));

            if (PVector.dist(pos, pseudoAnchorLeft) < segment.segmentWidth / 2) {
                pos = pseudoAnchorLeft;
                snappingToLeftOfSegment = true;
                snappingToSegment = segment;
                snappingLeftRightPoint = node;
                return true;
            }
            if (PVector.dist(pos, pseudoAnchorRight) < segment.segmentWidth / 2) {
                pos = pseudoAnchorRight;
                snappingToRightOfSegment = true;
                snappingToSegment = segment;
                snappingLeftRightPoint = node;
                return true;
            }
        }

        return false;
    }

    private boolean snapAnchors(Segment currentSegment, ArrayList<Anchor> anchors) {
        if (draggedAnchor != null || (currentSegment != null && builder.segmentPlaced))
            return false;

        for (Anchor anchor : anchors) {
            if (anchor.hovering()) {
                pos = anchor.pos;
                snappingAnchor = true;
                return true;
            }
        }

        return false;
    }

    private boolean snapMiddleSegment() {
        // if (builder.currentSegment != null && builder.segmentPlaced)
        // return false;
        // TODO: (see trello)
        if (builder.currentSegment != null || builder.currentAnchor != null)
            return false;

        Segment hovered = builder.getHoveredSegment();
        if (hovered == null)
            return false;

        ArrayList<PVector> sortedPath = new ArrayList<>(hovered.path);
        sortedPath.sort(Comparator.comparingDouble(
                p -> PVector.dist(p, pos)));
        pos = PVector.add(sortedPath.get(0), sortedPath.get(1)).div(2);

        snappingMiddleSegment = true;
        snappingMiddleSegmentAngle = PVector.sub(sortedPath.get(1), sortedPath.get(0)).heading();
        snappingMiddleSegmentSegment = hovered;
        return true;
    }

    private boolean snapWalls() {
        if (mouseX < pixelMargin) {
            pos.x = 0;
            snappingWall = true;
            fixedX = true;
        } else if (mouseX > width - pixelMargin) {
            pos.x = width;
            snappingWall = true;
            fixedX = true;
        }
        if (mouseY < pixelMargin) {
            pos.y = 0;
            snappingWall = true;
            fixedY = true;
        } else if (mouseY > height - pixelMargin) {
            pos.y = height;
            snappingWall = true;
            fixedY = true;
        }

        return snappingWall;
    }

    private boolean snapMiddleScreen() {
        if (!snappingWall)
            return false;

        if (abs(mouseX - width / 2) < pixelMargin && !fixedX) {
            pos.x = width / 2;
            snappingMiddleScreen = true;
            fixedX = true;
        }
        if (abs(mouseY - height / 2) < pixelMargin && !fixedY) {
            pos.y = height / 2;
            snappingMiddleScreen = true;
            fixedY = true;
        }

        return snappingMiddleScreen;
    }

    private boolean snapAngles(Segment currentSegment) {
        if (currentSegment == null || (fixedX && fixedY))
            return false;

        PVector start = currentSegment.getStartNode();
        if (selectedEndControlPoint != null) {
            start = currentSegment.getEndNode();
        }
        float heading = PVector.sub(pos, start).heading();

        // Always trying snapping to a global 90 degree
        if (enabledSnappingOptions[3]
                && (!builder.segmentPlaced || selectedStartControlPoint != null || selectedEndControlPoint != null)) {
            float k = round(heading / (PI / 2));
            boolean rightSnapping = k == 0 && abs(mouseY - start.y) < pixelMargin;
            boolean topSnapping = k == -1 && abs(mouseX - start.x) < pixelMargin;
            boolean leftSnapping = abs(k) == 2 && abs(mouseY - start.y) < pixelMargin;
            boolean bottomSnapping = k == 1 && abs(mouseX - start.x) < pixelMargin;
            if ((rightSnapping || leftSnapping) && !fixedY) {
                fixedY = true;
                pos.y = start.y;
                snapping90 = true;
                return true;
            }
            if ((topSnapping || bottomSnapping) && !fixedX) {
                fixedX = true;
                pos.x = start.x;
                snapping90 = true;
                return true;
            }
        }

        if (!enabledSnappingOptions[4])
            return false;

        if (selectedStartControlPoint != null) {
            for (Segment previous : currentSegment.segmentsPrevious) {
                if (previous.type != SegmentType.STRAIGHT || previous.path.size() < 2)
                    continue;

                float cursorHeading = PVector.sub(pos, currentSegment.getStartNode()).heading();
                float endHeading = PVector.sub(previous.path.getLast(), previous.getNode(previous.path.size() - 2))
                        .heading();

                float headingDiff = abs(cursorHeading - endHeading);
                if (abs(headingDiff) < radianMargin) {
                    if (!fixedX && !fixedY) {
                        float dist = PVector.dist(currentSegment.getStartNode(), pos);
                        pos = PVector.fromAngle(endHeading).setMag(dist)
                                .add(currentSegment.getStartNode());
                        snappingHeading = true;
                        snappingHeadingPoint = currentSegment.getStartNode();
                        return true;
                    } else {
                        if (fixedX) {
                            if (abs(tan(endHeading)) > 10000)
                                return false;
                            pos.y = start.y + (pos.x - start.x) * tan(endHeading);
                            fixedY = true;
                        } else {
                            if (abs(tan(endHeading)) < epsilon)
                                return false;
                            pos.x = start.x + (pos.y - start.y) / tan(endHeading);
                            fixedX = true;
                        }

                        snappingHeading = true;
                        return true;
                    }
                }
            }
        }
        if (selectedEndControlPoint != null) {
            for (Segment next : currentSegment.segmentsNext) {
                if (next.type != SegmentType.STRAIGHT || next.path.size() < 2)
                    continue;

                float cursorHeading = PVector.sub(pos, currentSegment.getEndNode()).heading();
                float endHeading = PVector.sub(next.getNode(0), next.getNode(1)).heading();

                float headingDiff = abs(cursorHeading - endHeading);
                if (abs(headingDiff) < radianMargin) {
                    if (!fixedX && !fixedY) {
                        float dist = PVector.dist(currentSegment.getEndNode(), pos);
                        pos = PVector.fromAngle(endHeading).setMag(dist)
                                .add(currentSegment.getEndNode());
                        snappingHeading = true;
                        snappingHeadingPoint = currentSegment.getEndNode();
                        return true;
                    } else {
                        if (fixedX) {
                            if (abs(tan(endHeading)) > 10000)
                                return false;
                            pos.y = start.y + (pos.x - start.x) * tan(endHeading);
                            fixedY = true;
                        } else {
                            if (abs(tan(endHeading)) < epsilon)
                                return false;
                            pos.x = start.x + (pos.y - start.y) / tan(endHeading);
                            fixedX = true;
                        }

                        snappingHeading = true;
                        return true;
                    }
                }
            }
        }

        // When making a new segment, we try to align with the previous segment's end
        // heading
        for (Segment previous : currentSegment.segmentsPrevious) {
            if (previous.path.size() < 2)
                continue;

            float cursorHeading = PVector.sub(pos, currentSegment.getStartNode()).heading();
            float endHeading = PVector
                    .sub(previous.path.getLast(), previous.getNode(previous.path.size() - 2))
                    .heading();

            float headingDiff = abs(cursorHeading - endHeading);
            if (abs(headingDiff) < radianMargin) {
                if (!fixedX && !fixedY) {
                    float dist = PVector.dist(currentSegment.getStartNode(), pos);
                    pos = PVector.fromAngle(endHeading).setMag(dist)
                            .add(currentSegment.getStartNode());
                    snappingHeading = true;
                    snappingHeadingPoint = currentSegment.getStartNode();
                    return true;
                } else {
                    // Calculate what the other fixed axis should be
                    if (fixedX) {
                        if (abs(tan(endHeading)) > 10000)
                            return false;
                        pos.y = start.y + (pos.x - start.x) * tan(endHeading);
                        fixedY = true;
                    } else {
                        if (abs(tan(endHeading)) < epsilon)
                            return false;
                        pos.x = start.x + (pos.y - start.y) / tan(endHeading);
                        fixedX = true;
                    }

                    snappingHeading = true;
                    return true;
                }
            }
        }

        return false;
    }

    private boolean snapGuidelines() {
        if ((fixedX && fixedY))
            return false;

        float bestDistance = Float.MAX_VALUE;
        PVector bestNode = null;
        PVector bestSecondary = null;
        for (Segment segment : builder.segments) {
            if (segment == builder.currentSegment)
                continue;

            // TODO: confirm that the following is correct and not excessive
            if (builder.currentSegment != null && (builder.currentSegment.segmentsNext.contains(segment)
                    || builder.currentSegment.segmentsPrevious.contains(segment)))
                continue;

            float dist = snapGuideline(segment.getNode(0), segment.getNode(1), radianMargin, pixelMargin, false);
            if (dist < bestDistance) {
                bestDistance = dist;
                bestNode = segment.getNode(0);
                bestSecondary = segment.getNode(1);
            }
            dist = snapGuideline(segment.path.getLast(), segment.getNode(segment.path.size() - 2), radianMargin,
                    pixelMargin, false);
            if (dist < bestDistance) {
                bestDistance = dist;
                bestNode = segment.path.getLast();
                bestSecondary = segment.getNode(segment.path.size() - 2);
            }
        }

        if (bestNode != null) {
            snapGuideline(bestNode, bestSecondary, radianMargin, pixelMargin, true);
            return true;
        }

        return false;
    }

    private float snapGuideline(PVector node, PVector secondary, float radianMargin, float pixelMargin,
            boolean updatePos) {
        if (snappingHeadingPoint == null) {
            float cursorHeading = PVector.sub(pos, node).heading();
            float targetHeading = PVector.sub(node, secondary).heading();

            float headingDiff = abs(cursorHeading - targetHeading);
            if (abs(headingDiff) < radianMargin) {
                float dist = PVector.dist(node, pos);
                if (!updatePos)
                    return dist;
                if (!fixedX && !fixedY) {
                    pos = PVector.fromAngle(targetHeading).setMag(dist).add(node);
                    snappingGuidelines = true;
                    snappingGuidelinesPoint = node;
                } else {
                    if (fixedX) {
                        if (abs(tan(targetHeading)) > 10000)
                            return Float.MAX_VALUE;
                        pos.y = node.y + (pos.x - node.x) * tan(targetHeading);
                        fixedY = true;
                    } else {
                        if (abs(tan(targetHeading)) < epsilon)
                            return Float.MAX_VALUE;
                        pos.x = node.x + (pos.y - node.y) / tan(targetHeading);
                        fixedX = true;
                    }

                    snappingGuidelines = true;
                    snappingGuidelinesPoint = node;
                    return dist;
                }
            }
        } else {
            if (builder.currentSegment == null)
                return Float.MAX_VALUE;

            // Find intersection
            PVector intersection = builder.currentSegment.findIntersection(secondary, node, snappingHeadingPoint, pos);
            if (intersection == null)
                return Float.MAX_VALUE;

            float dist = intersection.dist(pos);
            if (dist < pixelMargin) {
                if (!updatePos)
                    return dist;

                pos = intersection;
                fixedX = true;
                fixedY = true;
                snappingGuidelines = true;
                snappingGuidelinesPoint = node;
                return dist;
            }
        }

        return Float.MAX_VALUE;
    }

    public void showSnapping() {
        stroke(230);
        strokeWeight(5);
        noFill();
        float mag = 45;

        // Left/right of segment snapping
        if (snappingToLeftOfSegment || snappingToRightOfSegment) {
            line(snappingLeftRightPoint, pos);
        }
        // Anchor snapping
        if (snappingAnchor) {
            circle(pos, Settings.sizeAnchor);
        }
        // Middle segment snapping
        if (snappingMiddleSegment) {
            float perpendicularMag = snappingMiddleSegmentSegment.segmentWidth / 3;
            float offset = Settings.sizeAnchor / 2 + mag / 4;

            PVector tangent = PVector.fromAngle(snappingMiddleSegmentAngle);
            PVector perpendicular = PVector.fromAngle(snappingMiddleSegmentAngle + PI / 2);

            PVector start = PVector.add(pos, tangent.setMag(offset));
            line(start, PVector.add(start, tangent.setMag(perpendicularMag)));
            line(PVector.add(start, perpendicular.setMag(perpendicularMag)),
                    PVector.sub(start, perpendicular.setMag(perpendicularMag)));
            start = PVector.sub(pos, tangent.setMag(offset));
            line(start, PVector.sub(start, tangent.setMag(perpendicularMag)));
            line(PVector.sub(start, perpendicular.setMag(perpendicularMag)),
                    PVector.add(start, perpendicular.setMag(perpendicularMag)));
        }
        // Wall snapping
        if (snappingWall) {
            if (pos.x == 0 || pos.x == width)
                line(pos.x, pos.y - mag, pos.x, pos.y + mag);
            if (pos.y == 0 || pos.y == height)
                line(pos.x - mag, pos.y, pos.x + mag, pos.y);
        }
        // Middle snapping
        if (snappingMiddleScreen) {
            if (pos.x == width / 2)
                line(pos.x, pos.y - mag, pos.x, pos.y + mag);
            if (pos.y == height / 2)
                line(pos.x - mag, pos.y, pos.x + mag, pos.y);
        }
        // Angle snapping
        if (snappingHeadingPoint != null) {
            line(snappingHeadingPoint, PVector.sub(pos, snappingHeadingPoint).setMag(mag).add(snappingHeadingPoint));
            line(snappingHeadingPoint,
                    PVector.sub(pos, snappingHeadingPoint).rotate(PI / 2).setMag(mag).add(snappingHeadingPoint));
        }
        // Guidelines
        if (snappingGuidelinesPoint != null) {
            line(snappingGuidelinesPoint, pos);
        }
    }

    public void mousePressed() {
        if (mouseButton != LEFT)
            return;
        if (builder.currentSegment != null && builder.segmentPlaced) {
            if (pos.dist(builder.currentSegment.getStartControlPoint()) < Settings.sizeAnchor / 2) {
                selectedStartControlPoint = builder.currentSegment;
            } else if (pos.dist(builder.currentSegment.getEndControlPoint()) < Settings.sizeAnchor / 2) {
                selectedEndControlPoint = builder.currentSegment;
            }
        }
        if (builder.currentSegment == null && builder.currentAnchor == null) {
            draggedAnchor = builder.getHoveredAnchor();
        }
    }

    public void mouseReleased() {
        selectedStartControlPoint = null;
        selectedEndControlPoint = null;
        draggedAnchor = null;
    }
}
