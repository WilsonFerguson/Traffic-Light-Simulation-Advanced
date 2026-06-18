import library.core.*;
import java.util.*;

class Cursor extends PComponent {
    Builder builder;
    public PVector pos;

    boolean snappingToLeftOfSegment = false;
    boolean snappingToRightOfSegment = false;
    Segment snappingToSegment;
    boolean snappingAnchor = false;
    boolean snappingWall = false;
    boolean snapping90 = false;
    boolean snappingHeading = false;
    PVector snappingHeadingPoint = null;
    boolean snappingGuidelines = false;
    PVector snappingGuidelinesPoint = null;

    boolean fixedX = false;
    boolean fixedY = false;

    Segment selectedStartControlPoint = null;
    Segment selectedEndControlPoint = null;
    Anchor draggedAnchor = null;

    public Cursor(Builder builder) {
        this.builder = builder;
    }

    public void update(Segment currentSegment, ArrayList<Anchor> anchors, ArrayList<Segment> segments) {
        resetVariables();

        if (builder.hoveringSegmentInfo())
            return;

        // Absolute snaps (cannot combine with other snapping)
        if (snapLeftAndRightOfSegment(currentSegment, segments))
            return;
        if (snapAnchors(currentSegment, anchors))
            return;

        // Combination snaps
        snapWalls();
        snapAngles(currentSegment);
        snapGuidelines();
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
        snappingAnchor = false;
        snappingWall = false;
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
        float pixelMargin = 25;
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

    private boolean snapAngles(Segment currentSegment) {
        if (currentSegment == null || (fixedX && fixedY))
            return false;

        float radianMargin = 0.1f;
        float pixelMargin = 15;
        PVector start = currentSegment.getStartNode();
        if (selectedEndControlPoint != null) {
            start = currentSegment.getEndNode();
        }
        float heading = PVector.sub(pos, start).heading();

        // Always trying snapping to a global 90 degree
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

        if (selectedStartControlPoint != null) {
            for (Segment previous : currentSegment.segmentsPrevious) {
                if (previous.type != SegmentType.STRAIGHT || previous.path.size() < 2)
                    continue;

                float cursorHeading = PVector.sub(pos, currentSegment.getStartNode()).heading();
                float endHeading = PVector.sub(previous.path.getLast(), previous.getNode(previous.path.size() - 2))
                        .heading();

                float headingDiff = abs(cursorHeading - endHeading);
                // TODO: check every % PI and see if it's actually needed (probably isn't)
                if (abs(headingDiff) % PI < radianMargin) {
                    if (!fixedX && !fixedY) {
                        float dist = PVector.dist(currentSegment.getStartNode(), pos);
                        pos = PVector.fromAngle(endHeading).setMag(dist)
                                .add(currentSegment.getStartNode());
                        snappingHeading = true;
                        snappingHeadingPoint = currentSegment.getStartNode();
                        return true;
                    } else {
                        if (fixedX) {
                            pos.y = start.y + (pos.x - start.x) * tan(endHeading);
                            fixedY = true;
                        } else {
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
                if (abs(headingDiff) % PI < radianMargin) {
                    if (!fixedX && !fixedY) {
                        float dist = PVector.dist(currentSegment.getEndNode(), pos);
                        pos = PVector.fromAngle(endHeading).setMag(dist)
                                .add(currentSegment.getEndNode());
                        snappingHeading = true;
                        snappingHeadingPoint = currentSegment.getEndNode();
                        return true;
                    } else {
                        if (fixedX) {
                            pos.y = start.y + (pos.x - start.x) * tan(endHeading);
                            fixedY = true;
                        } else {
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
            if (abs(headingDiff) % PI < radianMargin) {
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
                        pos.y = start.y + (pos.x - start.x) * tan(endHeading);
                        fixedY = true;
                    } else {
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
        if ((fixedX && fixedY) || builder.currentSegment == null)
            return false;

        float radianMargin = 0.1f;
        float pixelMargin = 15;

        for (Segment segment : builder.segments) {
            if (segment == builder.currentSegment)
                continue;

            boolean done = snapGuideline(segment.getNode(0), segment.getNode(1), radianMargin, pixelMargin);
            if (done)
                return true;
            done = snapGuideline(segment.path.getLast(), segment.getNode(segment.path.size() - 2), radianMargin,
                    pixelMargin);
            if (done)
                return true;
        }

        return false;
    }

    private boolean snapGuideline(PVector node, PVector secondary, float radianMargin, float pixelMargin) {
        if (snappingHeadingPoint == null) {
            float cursorHeading = PVector.sub(pos, node).heading();
            float targetHeading = PVector.sub(node, secondary).heading();

            float headingDiff = abs(cursorHeading - targetHeading);
            if (abs(headingDiff) < radianMargin) {
                if (!fixedX && !fixedY) {
                    float dist = PVector.dist(node, pos);
                    pos = PVector.fromAngle(targetHeading).setMag(dist).add(node);
                    snappingGuidelines = true;
                    snappingGuidelinesPoint = node;
                    return true;
                } else {
                    if (fixedX) {
                        pos.y = node.y + (pos.x - node.x) * tan(targetHeading);
                        fixedY = true;
                    } else {
                        pos.x = node.x + (pos.y - node.y) / tan(targetHeading);
                        fixedX = true;
                    }

                    snappingGuidelines = true;
                    snappingGuidelinesPoint = node;
                    return true;
                }
            }
        } else {
            // Find intersection
            PVector intersection = builder.currentSegment.findIntersection(secondary, node, snappingHeadingPoint, pos);
            if (intersection == null)
                return false;

            if (intersection.dist(pos) < pixelMargin) {
                pos = intersection;
                fixedX = true;
                fixedY = true;
                snappingGuidelines = true;
                snappingGuidelinesPoint = node;
                return true;
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
