import library.core.*;
import GameEngine.*;
import java.util.*;

class Builder extends PComponent {
    ArrayList<Anchor> anchors;
    ArrayList<Segment> segments;

    Sketch sketch;
    Cursor cursor;

    Anchor currentAnchor;
    Segment currentSegment;
    boolean segmentPlaced;

    public Builder(Sketch sketch) {
        anchors = new ArrayList<Anchor>();
        segments = new ArrayList<Segment>();

        this.sketch = sketch;

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
                        PVector.sub(currentSegment.getEndNode(), currentSegment.getStartNode()).heading(), true);
                currentSegment.updatePath(true);
            }

            if (cursor.selectedStartControlPoint != null) {
                float heading = PVector.sub(cursor.pos, currentSegment.getStartNode()).heading();
                float mag = PVector.sub(cursor.pos, currentSegment.getStartNode()).mag();
                cursor.selectedStartControlPoint.setStartControlPoint(heading, mag);
                currentSegment.updatePath(true);
            }
            if (cursor.selectedEndControlPoint != null) {
                float heading = PVector.sub(currentSegment.getEndNode(), cursor.pos).heading();
                float mag = PVector.sub(cursor.pos, currentSegment.getEndNode()).mag();
                cursor.selectedEndControlPoint.setEndControlPoint(heading, mag);
                currentSegment.updatePath(true);
            }
        } else {
        }
    }

    public void show() {
        for (Segment segment : segments) {
            if (segment == currentSegment)
                continue;
            segment.show();
        }
        if (!sketch.running) {
            if (Settings.drawAnchors) {
                for (Anchor anchor : anchors) {
                    anchor.show();
                }
            }
        }

        if (currentAnchor != null) {
            currentAnchor.show();
        }
        if (currentSegment != null) {
            currentSegment.showSelected();
        }
        if (currentAnchor == null && !sketch.running) {
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

        if (hoveringSegmentInfo())
            return true;

        return currentSegment.hovering();
    }

    public boolean hoveringSegmentInfo() {
        if (currentSegment == null)
            return false;

        return (currentSegment.segmentEditorPanel.isActive() && currentSegment.segmentEditorPanel.hover());
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

    public Segment getHoveredSegment(Segment... excludedSegments) {
        for (Segment segment : segments) {
            boolean cancel = false;
            if (excludedSegments.length > 0) {
                for (Segment excluded : excludedSegments) {
                    if (excluded == segment) {
                        cancel = true;
                        break;
                    }
                }
            }
            if (cancel)
                continue;
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
        for (Segment segment : currentSegment.segmentsNext) {
            segment.segmentsPrevious.remove(currentSegment);
        }
        for (Segment segment : currentSegment.segmentsPrevious) {
            segment.segmentsNext.remove(currentSegment);
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

    private void createBrandNewSegment(Anchor hoveredAnchor, Segment hoveredSegment) {
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
            currentSegment.setTrafficType(previousFirst.trafficType);
            currentSegment.setPriority(previousFirst.priority);
            if (previousFirst.endHeading != -Float.MIN_VALUE)
                currentSegment.setStartHeading(previousFirst.endHeading, true);

            return;
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

    /**
     * Splits a segment into two segments at the given anchor. The original segment
     * becomes the first half and the second half is returned (and added to the list
     * of segments).
     */
    private Segment splitSegment(Segment original, Anchor anchor) {
        Segment next = original.copy();

        for (Segment segment : original.segmentsNext)
            segment.segmentsPrevious.remove(original);
        original.segmentsNext.clear();

        for (Segment segment : next.segmentsPrevious)
            segment.segmentsNext.remove(next);
        next.segmentsPrevious.clear();
        for (Segment segment : next.segmentsNext) {
            segment.segmentsPrevious.add(next);
        }

        original.addSegmentNext(next);
        original.setClosingSegment(false);
        next.setOpeningSegment(false);

        original.endAnchor.endSegments.remove(original);
        original.endAnchor.endSegments.add(next);

        original.endAnchor = anchor;
        anchor.endSegments.add(original);

        next.startAnchor = anchor;
        anchor.beginSegments.add(next);

        original.setEndNode(anchor.pos);
        next.setStartNode(anchor.pos);

        original.updatePath(true);
        next.updatePath(true);

        segments.add(next);
        return next;
    }

    private void makeTJunction(Segment hovered, Segment comingIn) {
        Segment next = splitSegment(hovered, currentAnchor);
        comingIn.addSegmentNext(next);
    }

    private void make4WayJunction(Segment crossed, Segment comingIn, PVector intersection) {
        Anchor anchor = new Anchor(this, intersection);
        anchors.add(anchor);

        Segment crossedNext = splitSegment(crossed, anchor);
        Segment comingInNext = splitSegment(comingIn, anchor);

        comingIn.addSegmentNext(crossedNext);
        crossed.addSegmentNext(comingInNext);
    }

    /**
     * Completes the current segment where the ending is not on a pre-existing
     * anchor
     */
    private void completeCurrentSegmentFree() {
        Segment hovered = getHoveredSegment(currentSegment);
        Segment comingIn = currentSegment;

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
        currentSegment.setTrafficType(previous.trafficType);
        currentSegment.setPriority(previous.priority);
        if (previous.endHeading != -Float.MIN_VALUE)
            currentSegment.setStartHeading(previous.endHeading, true);

        // If we are hovering a pre-existing segment, then we need to split that one
        if (hovered != null) {
            makeTJunction(hovered, comingIn);
        } else {
            // If we crossed a pre-existing segment, then we need to make a 4-way junction
            // Segment crossed = comingIn.getCrossedSegment(segments);
            Object[] data = comingIn.getCrossedSegment(segments);
            if (data != null) {
                make4WayJunction((Segment) data[0], comingIn, (PVector) data[1]);
            }
        }
    }

    private void completeCurrentSegmentWithAnchor(Anchor hoveredAnchor) {
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
        currentSegment.setTrafficType(previous.trafficType);
        currentSegment.setPriority(previous.priority);
        if (previous.endHeading != -Float.MIN_VALUE)
            currentSegment.setStartHeading(previous.endHeading, true);
        return;
    }

    public void mouseClicked() {
        if (mouseButton != LEFT || sketch.running)
            return;

        // Make brand new segment that starts a path
        Anchor hoveredAnchor = getHoveredAnchor();
        Segment hoveredSegment = getHoveredSegment();
        if (currentAnchor == null && currentSegment == null && hoveredSegment == null) {
            createBrandNewSegment(hoveredAnchor, hoveredSegment);
            return;
        }

        // Make new segment that continues path
        if (currentSegment != null && !segmentPlaced && !hoveringSegmentInfo()) {
            if (hoveredAnchor == null)
                completeCurrentSegmentFree();
            else
                completeCurrentSegmentWithAnchor(hoveredAnchor);
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
        if (mouseButton != LEFT || sketch.running)
            return;
        if (cursor.draggedAnchor != null) {
            cursor.draggedAnchor.setPos(cursor.pos);
        }
    }

    public void keyPressed() {
        if (sketch.running)
            return;

        if (keyString.equals("Escape")) {
            if (currentSegment != null)
                deselectSegment();
            else if (currentAnchor != null)
                deselectAnchor();
        } else if (key == 'w') {
            if (currentSegment != null) {
                if (currentSegment.type == SegmentType.STRAIGHT) {
                    currentSegment.setType(SegmentType.BEZIER);
                } else {
                    currentSegment.setType(SegmentType.STRAIGHT);
                }
                currentSegment.updatePath(true);
            }
        }
    }
}
