import library.core.*;
import GameEngine.*;
import java.util.*;

class Builder extends PComponent {
    ArrayList<Anchor> anchors;
    ArrayList<Segment> segments;

    Cursor cursor;

    Anchor currentAnchor;
    Segment currentSegment;
    boolean segmentPlaced;

    public Builder() {
        anchors = new ArrayList<Anchor>();
        segments = new ArrayList<Segment>();

        currentAnchor = null;
        currentSegment = null;
        segmentPlaced = false;

        cursor = new Cursor(this);
    }

    public void update() {
        cursor.update(currentSegment, anchors, segments);

        if (currentSegment != null) {
            if (!segmentPlaced) {
                currentSegment.setEnd(cursor.pos);
                currentSegment.setEndHeading(
                        PVector.sub(currentSegment.getFinalNode(), currentSegment.getStartNode()).heading(), true);
                currentSegment.updatePath(true);
            }

            if (cursor.selectedStartControlPoint != null) {
                float heading = PVector.sub(cursor.pos, currentSegment.getStartNode()).heading();
                float mag = PVector.sub(cursor.pos, currentSegment.getStartNode()).mag();
                cursor.selectedStartControlPoint.setStartControlPoint(heading, mag);
                currentSegment.updatePath(true);
            }
            if (cursor.selectedEndControlPoint != null) {
                float heading = PVector.sub(currentSegment.getFinalNode(), cursor.pos).heading();
                float mag = PVector.sub(cursor.pos, currentSegment.getFinalNode()).mag();
                cursor.selectedEndControlPoint.setEndControlPoint(heading, mag);
                currentSegment.updatePath(true);
            }
        } else {
        }
    }

    public void show() {
        if (Settings.drawAnchors) {
            for (Anchor anchor : anchors) {
                anchor.show();
            }
        }
        for (Segment segment : segments) {
            if (segment == currentSegment)
                continue;
            segment.show();
        }

        if (currentAnchor != null) {
            currentAnchor.show();
        }
        if (currentSegment != null) {
            currentSegment.showSelected();
        }
        if (currentAnchor == null) {
            cursor.show();
        }
    }

    public void addAnchor(Anchor anchor) {
        anchors.add(anchor);
    }

    public void addSegment(Segment segment) {
        segments.add(segment);
    }

    public void newAnchor(PVector pos) {
        currentAnchor = new Anchor(this, pos.copy());
        anchors.add(currentAnchor);
    }

    public void newSegment(PVector pos) {
        currentSegment = new Segment(this, pos.copy());
        currentSegment.setStartAnchor(currentAnchor);
        segments.add(currentSegment);
        segmentPlaced = false;
    }

    public void newSegment(PVector pos, float heading) {
        currentSegment = new Segment(this, pos.copy());
        currentSegment.setStartAnchor(currentAnchor);
        currentSegment.setStartHeading(heading, true);
        segments.add(currentSegment);
        segmentPlaced = false;
    }

    /**
     * Returns true if either the current anchor or any information related to it is
     * hovered by the mouse
     */
    public boolean hoveringAnchorRelevance() {
        if (currentAnchor == null)
            return false;

        return currentAnchor.hovering();
    }

    public boolean hoveringSegmentRelevance() {
        if (currentSegment == null)
            return false;

        if (currentSegment.type != SegmentType.STRAIGHT
                && cursor.pos.dist(currentSegment.getStartControlPoint()) < Settings.sizeAnchor / 2)
            return true;

        if (currentSegment.type != SegmentType.STRAIGHT
                && cursor.pos.dist(currentSegment.getEndControlPoint()) < Settings.sizeAnchor / 2)
            return true;

        return currentSegment.hovering();
    }

    public Anchor getHoveredAnchor() {
        for (Anchor anchor : anchors) {
            if (anchor.hovering())
                return anchor;
        }

        return null;
    }

    public Segment getHoveredSegment() {
        for (Segment segment : segments) {
            if (segment.hovering())
                return segment;
        }

        return null;
    }

    private void selectAnchor(Anchor anchor) {
        currentAnchor = anchor;
    }

    private void selectSegment(Segment segment) {
        currentSegment = segment;
    }

    private void deselectAnchor() {
        currentAnchor = null;
    }

    private void deselectSegment() {
        if (currentSegment == null)
            return;

        if (segmentPlaced) {
            currentSegment = null;
            segmentPlaced = false;
            return;
        }

        segments.remove(currentSegment);

        for (Segment segment : currentSegment.snappedToSegments) {
            segment.controlledSegments.remove(currentSegment);
            segment.controlledLeftStart.remove(currentSegment);
            segment.controlledRightStart.remove(currentSegment);
            segment.controlledLeftEnd.remove(currentSegment);
            segment.controlledRightEnd.remove(currentSegment);
            segment.controlledFullLeft.remove(currentSegment);
            segment.controlledFullRight.remove(currentSegment);
        }

        if (currentSegment.startAnchor != null)
            currentSegment.startAnchor.beginSegments.remove(currentSegment);
        if (currentSegment.endAnchor != null)
            currentSegment.endAnchor.endSegments.remove(currentSegment);

        delete(currentSegment);
        currentSegment = null;
        currentAnchor = null;
        segmentPlaced = false;

        removeUnusedAnchors();
    }

    private void removeUnusedAnchors() {
        for (int i = anchors.size() - 1; i >= 0; i--) {
            Anchor anchor = anchors.get(i);
            if (anchor.beginSegments.size() == 0 && anchor.endSegments.size() == 0) {
                anchors.remove(i);
                delete(anchor);
            }
        }
    }

    private void finishOffCurrentSegment() {
        if (segmentPlaced)
            return;

        newAnchor(cursor.pos);
        currentSegment.setEndAnchor(currentAnchor);
        float heading = currentSegment.getStraightHeading();
        if (currentSegment.type == SegmentType.STRAIGHT) {
            currentSegment.setHeadings(heading, heading);
            currentSegment.setPreviousEndHeading(heading);
            currentSegment.updatePath(true);
        }

        if (cursor.snappingWall) {
            currentSegment.setClosingSegment(true);
            currentAnchor.setClosingAnchor(true);
        }

        if (cursor.snappingToLeftOfSegment)
            cursor.snappingToSegment.controlLeftEnd(currentSegment);
        else if (cursor.snappingToRightOfSegment)
            cursor.snappingToSegment.controlRightEnd(currentSegment);
    }

    public void mouseClicked() {
        // Make brand new segment that starts a path
        Anchor hoveredAnchor = getHoveredAnchor();
        Segment hoveredSegment = getHoveredSegment();
        if (currentAnchor == null && currentSegment == null && hoveredSegment == null) {
            if (hoveredAnchor == null && hoveredSegment == null) {
                newAnchor(cursor.pos);
                newSegment(cursor.pos);
                if (cursor.snappingWall) {
                    currentSegment.setOpeningSegment(true);
                    currentAnchor.setOpeningAnchor(true);
                }
                if (cursor.snappingToLeftOfSegment) {
                    cursor.snappingToSegment.controlLeftStart(currentSegment);
                } else if (cursor.snappingToRightOfSegment) {
                    cursor.snappingToSegment.controlRightStart(currentSegment);
                }
            }
            if (hoveredAnchor != null) {
                if (hoveredAnchor.endSegments.size() == 0) {
                    selectAnchor(hoveredAnchor);
                    newSegment(cursor.pos);
                    return;
                }

                selectAnchor(hoveredAnchor);
                Segment previousFirst = hoveredAnchor.endSegments.iterator().next();
                Set<Segment> previousSegments = hoveredAnchor.endSegments;
                newSegment(cursor.pos, previousFirst.getStraightHeading());

                for (Segment previous : previousSegments) {
                    currentSegment.addSegmentPrevious(previous);
                }
                currentSegment.setType(previousFirst.type);
                if (previousFirst.endHeading != -Float.MIN_VALUE)
                    currentSegment.setStartHeading(previousFirst.endHeading, true);

                return;
            }

            return;
        }

        // Make new segment that continues path
        if (currentSegment != null && !segmentPlaced) {
            if (!hoveringSegmentRelevance() && hoveredAnchor == null) {
                finishOffCurrentSegment();
                Segment previous = currentSegment;
                segmentPlaced = false;

                newSegment(cursor.pos, currentSegment.getStraightHeading());

                if (cursor.snappingToLeftOfSegment)
                    cursor.snappingToSegment.controlLeftStart(currentSegment);
                else if (cursor.snappingToRightOfSegment)
                    cursor.snappingToSegment.controlRightStart(currentSegment);

                currentSegment.addSegmentPrevious(previous);
                currentSegment.setType(previous.type);
                if (previous.endHeading != -Float.MIN_VALUE)
                    currentSegment.setStartHeading(previous.endHeading, true);

                return;
            } else if (!hoveringSegmentRelevance() && hoveredAnchor != null) {
                selectAnchor(hoveredAnchor);
                currentSegment.setEndAnchor(currentAnchor);
                float heading = currentSegment.getStraightHeading();
                if (currentSegment.type == SegmentType.STRAIGHT) {
                    currentSegment.setHeadings(heading, heading);
                    currentSegment.setPreviousEndHeading(heading);
                    currentSegment.updatePath(true);
                }
                boolean updatedHeading = false;
                for (Segment segment : currentAnchor.beginSegments) {
                    currentSegment.addSegmentNext(segment);
                    if (!updatedHeading && currentSegment.type == SegmentType.BEZIER) {
                        currentSegment.setEndControlPoint(segment.startHeading, segment.startControlPointMag);
                        currentSegment.updatePath(true);
                        updatedHeading = true;
                    }
                }

                Segment previous = currentSegment;
                segmentPlaced = false;

                newSegment(cursor.pos, currentSegment.getStraightHeading());
                for (Segment segment : currentAnchor.endSegments) {
                    currentSegment.addSegmentPrevious(segment);
                }
                currentSegment.setType(previous.type);
                if (previous.endHeading != -Float.MIN_VALUE)
                    currentSegment.setStartHeading(previous.endHeading, true);
                return;
            }
        }

        if (hoveredSegment != null && (currentSegment == null || segmentPlaced)) {
            if (currentSegment != null)
                deselectSegment();
            selectSegment(hoveredSegment);
            segmentPlaced = true;
            return;
        }
    }

    public void mouseDragged() {
        if (cursor.draggedAnchor != null) {
            cursor.draggedAnchor.setPos(cursor.pos);
        }
    }

    public void keyPressed() {
        if (keyString.equals("Escape")) {
            if (currentSegment != null)
                deselectSegment();
            else if (currentAnchor != null)
                deselectAnchor();
        } else if (key == ' ') {
            if (currentSegment != null) {
                if (currentSegment.type == SegmentType.STRAIGHT) {
                    currentSegment.setType(SegmentType.BEZIER);
                } else {
                    currentSegment.setType(SegmentType.STRAIGHT);
                }
            }
        }
    }
}
