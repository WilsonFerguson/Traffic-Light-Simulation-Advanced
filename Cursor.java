import library.core.*;
import java.util.*;

class Cursor extends PComponent {
    Builder builder;
    public PVector pos;

    boolean snappingWall = false;
    boolean snapping90 = false;
    boolean snappingAnchor = false;
    boolean snappingToLeftOfSegment = false;
    boolean snappingToRightOfSegment = false;
    Segment snappingToSegment;

    Segment selectedStartControlPoint = null;
    Segment selectedEndControlPoint = null;
    Anchor draggedAnchor = null;

    public Cursor(Builder builder) {
        this.builder = builder;
    }

    public void update(Segment currentSegment, ArrayList<Anchor> anchors, ArrayList<Segment> segments) {
        resetVariables();

        if (snapLeftAndRightOfSegment(currentSegment, segments))
            return;
        if (snapAnchors(currentSegment, anchors))
            return;
        if (snapWalls())
            return;
        if (snapAngles(currentSegment))
            return;
    }

    public void show() {
        noStroke();
        fill(0);
        circle(pos, Settings.sizeAnchor);
    }

    private void resetVariables() {
        pos = mouse;

        snappingWall = false;
        snapping90 = false;
        snappingToLeftOfSegment = false;
        snappingToRightOfSegment = false;
        snappingToSegment = null;
    }

    private boolean snapLeftAndRightOfSegment(Segment currentSegment, ArrayList<Segment> segments) {
        if (currentSegment != null && builder.segmentPlaced)
            return false;

        for (Segment segment : segments) {
            if (segment == currentSegment)
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
                return true;
            }
            if (PVector.dist(pos, pseudoAnchorRight) < segment.segmentWidth / 2) {
                pos = pseudoAnchorRight;
                snappingToRightOfSegment = true;
                snappingToSegment = segment;
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

    private boolean snapWalls() {
        float wallMargin = 0.03f;
        if (pos.x < width * wallMargin) {
            pos.x = 0;
            snappingWall = true;
            return true;
        } else if (pos.x > width - width * wallMargin) {
            pos.x = width;
            snappingWall = true;
            return true;
        }
        if (pos.y < height * wallMargin) {
            pos.y = 0;
            snappingWall = true;
            return true;
        } else if (pos.y > height - height * wallMargin) {
            pos.y = height;
            snappingWall = true;
            return true;
        }

        return false;
    }

    private boolean snapAngles(Segment currentSegment) {
        if (currentSegment == null)
            return false;

        float radianMargin = 0.1f;
        PVector start = currentSegment.getStartNode();
        float heading = PVector.sub(pos, start).heading();

        // Always trying snapping to a global 90 degree
        float k = round(heading / (PI / 2));
        if (abs(heading - k * (PI / 2)) < radianMargin) {
            float targetHeading = k * (PI / 2);
            float dist = PVector.dist(start, pos);
            pos = PVector.fromAngle(targetHeading).setMag(dist).add(start);
            snapping90 = true;
            return true;
        }

        // Try local snapping if we have a start heading
        if (currentSegment.startHeading != -Float.MIN_VALUE && selectedStartControlPoint == null
                && selectedEndControlPoint == null) {
            float cursorHeading = PVector.sub(pos, currentSegment.getStartNode()).heading();
            if (abs(cursorHeading - currentSegment.startHeading) < radianMargin) {
                float dist = PVector.dist(currentSegment.getStartNode(), pos);
                pos = PVector.fromAngle(currentSegment.startHeading).setMag(dist)
                        .add(currentSegment.getStartNode());
                snapping90 = true;
                return true;
            }
        } else if (selectedStartControlPoint != null) {
            for (Segment previous : currentSegment.segmentsPrevious) {
                if (previous.type != SegmentType.STRAIGHT)
                    continue;
                float cursorHeading = PVector.sub(pos, currentSegment.getStartNode()).heading();
                if (abs(cursorHeading - previous.startHeading) < radianMargin) {
                    float dist = PVector.dist(currentSegment.getStartNode(), pos);
                    pos = PVector.fromAngle(previous.startHeading).setMag(dist)
                            .add(currentSegment.getStartNode());
                    snapping90 = true;
                    return true;
                }
            }
        }

        // Local snapping when controlling end heading
        // TODO: this is really broken
        if (selectedEndControlPoint != null) {
            for (Segment next : currentSegment.segmentsNext) {
                if (next.type != SegmentType.STRAIGHT)
                    continue;
                float cursorHeading = PVector.sub(pos, currentSegment.getEndNode()).heading();
                float headingDiff = abs(abs(cursorHeading) + abs(next.endHeading));
                if (headingDiff % PI < radianMargin) {
                    float dist = PVector.dist(currentSegment.getEndNode(), pos);
                    pos = PVector.fromAngle(next.endHeading).setMag(-dist)
                            .add(currentSegment.getEndNode());
                    snapping90 = true;
                    return true;
                }
            }
        }

        return false;
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
