import library.core.*;
import GameEngine.*;
import java.util.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

class Builder extends PComponent {
    ArrayList<Anchor> anchors;
    ArrayList<Segment> segments;

    Sketch sketch;
    Cursor cursor;

    Anchor currentAnchor;
    Segment currentSegment;
    Segment lastSelectedSegment;
    boolean segmentPlaced;

    int parallelNumSegments = 2;
    boolean parallelMode = false;

    boolean makeIntermediateAnchors = false;

    BufferedImage referenceImage;
    float referenceImageScale = 1;
    boolean referenceImageShow = false;
    PVector referenceImageOffset = PVector.zero();

    public Builder(Sketch sketch) {
        anchors = new ArrayList<Anchor>();
        segments = new ArrayList<Segment>();

        this.sketch = sketch;

        currentAnchor = null;
        currentSegment = null;
        lastSelectedSegment = null;
        segmentPlaced = false;

        cursor = new Cursor(this);

        try {
            referenceImage = ImageIO.read(new File("intersection.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void update() {
        cursor.update(currentSegment, anchors, segments);

        if (currentSegment != null) {
            if (!segmentPlaced) {
                currentSegment.setEnd(cursor.pos);
                currentSegment.setEndHeading(
                        PVector.sub(currentSegment.getEndNode(), currentSegment.getStartNode()).heading(), true);
                currentSegment.updatePath();
            }

            if (cursor.selectedStartControlPoint != null) {
                float heading = PVector.sub(cursor.pos, currentSegment.getStartNode()).heading();
                float mag = PVector.sub(cursor.pos, currentSegment.getStartNode()).mag();
                cursor.selectedStartControlPoint.setStartControlPoint(heading, mag);
                currentSegment.updatePath();
            }
            if (cursor.selectedEndControlPoint != null) {
                float heading = PVector.sub(currentSegment.getEndNode(), cursor.pos).heading();
                float mag = PVector.sub(cursor.pos, currentSegment.getEndNode()).mag();
                cursor.selectedEndControlPoint.setEndControlPoint(heading, mag);
                currentSegment.updatePath();
            }
        } else {
        }

    }

    public void show() {
        if (referenceImageShow) {
            AffineTransform old = getGraphics2D().getTransform();
            getGraphics2D().scale(referenceImageScale, referenceImageScale);
            getGraphics2D().translate(referenceImageOffset.x, referenceImageOffset.y);
            getGraphics2D().drawImage(referenceImage, 0, 0, width, height, null);
            getGraphics2D().setTransform(old);
        }

        segments.sort(Comparator.comparingInt(o -> o.priority));
        for (Segment segment : segments) {
            if (segment == currentSegment)
                continue;
            segment.show();
        }
        if (sketch.mode == SimulationMode.BUILD) {
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
            if (parallelMode && (parallelNumSegments < -1 || parallelNumSegments > 1) && !segmentPlaced) {
                int offset = (parallelNumSegments < 0) ? -1 : 1;

                fill(currentSegment.segmentColor);
                stroke(255);
                strokeWeight(3);
                noStroke();

                for (int i = 1; i < abs(parallelNumSegments); i++) {
                    ArrayList<PVector> path = currentSegment.parallelSegment(currentSegment.segmentWidth * (i),
                            offset);
                    currentSegment.drawPath(path);
                }
            }

            currentSegment.showSelected();
        }
        if (sketch.mode == SimulationMode.BUILD) {
            cursor.showSnapping();
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

        currentSegment.setSettings(lastSelectedSegment);
    }

    public void newSegment(PVector pos, float heading) {
        currentSegment = new Segment(this, pos.copy());
        currentSegment.setStartAnchor(currentAnchor);
        currentSegment.setStartHeading(heading, true);
        segments.add(currentSegment);
        segmentPlaced = false;

        currentSegment.setSettings(lastSelectedSegment);
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
        currentSegment.updateUIWithValues();
    }

    private void deselectAnchor() {
        currentAnchor = null;
    }

    private void deselectSegment() {
        if (currentSegment == null)
            return;

        lastSelectedSegment = currentSegment;

        if (segmentPlaced) {
            currentSegment = null;
            segmentPlaced = false;
            return;
        }

        currentSegment.segmentEditorPanel.setActive(false);
        for (UIElement element : currentSegment.segmentEditorPanel.getElements()) {
            if (element == currentSegment.colorPickerPathColor)
                continue;
            element.setActive(false);
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
            segment.segmentsNextOptions.remove(currentSegment);
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

    public void deleteCurrentSegment() {
        currentSegment.deleteSegment();
        currentSegment = null;
    }

    public boolean deleteAnchor(Anchor anchor) {
        if (anchor.beginSegments.size() == 1 && anchor.endSegments.size() == 1) {
            // TODO: pair up the segments
            Segment incoming = anchor.endSegments.iterator().next();
            Segment outgoing = anchor.beginSegments.iterator().next();

            incoming.setEndAnchor(outgoing.endAnchor);
            incoming.setEnd(outgoing.end);
            for (Segment segment : outgoing.segmentsNext) {
                incoming.addSegmentNext(segment);
            }
            for (Segment segment : outgoing.snappedToSegments) {
                incoming.snappedToSegments.add(segment);
            }
            for (Segment segment : segments) {
                if (segment.controlledSegments.contains(outgoing))
                    incoming.controlledSegments.add(segment);
                if (segment.controlledLeftStart.contains(outgoing))
                    incoming.controlledLeftStart.add(segment);
                if (segment.controlledRightStart.contains(outgoing))
                    incoming.controlledRightStart.add(segment);
                if (segment.controlledLeftEnd.contains(outgoing))
                    incoming.controlledLeftEnd.add(segment);
                if (segment.controlledRightEnd.contains(outgoing))
                    incoming.controlledRightEnd.add(segment);
                if (segment.controlledFullLeft.contains(outgoing))
                    incoming.controlledFullLeft.add(segment);
                if (segment.controlledFullRight.contains(outgoing))
                    incoming.controlledFullRight.add(segment);
            }

            incoming.setEndControlPoint(outgoing.endHeading, outgoing.endControlPointMag);
            incoming.setClosingSegment(outgoing.closingSegment);

            incoming.updatePath();
            incoming.updateUIWithValues();

            delete(outgoing);
            segments.remove(outgoing);

            delete(anchor);
            anchors.remove(anchor);
            return true;
        }
        if (anchor.beginSegments.size() == 0 && anchor.endSegments.size() == 0) {
            delete(anchor);
            anchors.remove(anchor);
            return true;
        }

        return false;
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
            // if (cursor.snappingToLeftOfSegment) {
            // cursor.snappingToSegment.controlLeftStart(currentSegment);
            // } else if (cursor.snappingToRightOfSegment) {
            // cursor.snappingToSegment.controlRightStart(currentSegment);
            // }
        }
        if (hoveredAnchor != null) {
            if (hoveredAnchor.endSegments.size() == 0) {
                selectAnchor(hoveredAnchor);
                newSegment(hoveredAnchor.pos);
                return;
            }

            selectAnchor(hoveredAnchor);
            Segment previousFirst = hoveredAnchor.endSegments.iterator().next();
            Set<Segment> previousSegments = hoveredAnchor.endSegments;
            newSegment(hoveredAnchor.pos, previousFirst.getStraightHeading());

            for (Segment previous : previousSegments) {
                currentSegment.addSegmentPrevious(previous);
            }
            currentSegment.setSettings(previousFirst);
            if (previousFirst.endHeading != -Float.MIN_VALUE)
                currentSegment.setStartHeading(previousFirst.endHeading, true);

            currentSegment.updateUIWithValues();

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
            currentSegment.updatePath();
        }

        if (cursor.snappingWall) {
            currentSegment.setClosingSegment(true);
            currentAnchor.setClosingAnchor(true);
        }

        // if (cursor.snappingToLeftOfSegment)
        // cursor.snappingToSegment.controlLeftEnd(currentSegment);
        // else if (cursor.snappingToRightOfSegment)
        // cursor.snappingToSegment.controlRightEnd(currentSegment);
        if (cursor.snappingToSegment != null) {
            currentSegment.setEndControlPoint(cursor.snappingToSegment.endHeading,
                    cursor.snappingToSegment.endControlPointMag);
        }
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
        original.segmentsNextOptions.clear();

        for (Segment segment : next.segmentsPrevious) {
            segment.segmentsNext.remove(next);
            segment.segmentsNextOptions.remove(next);
        }
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

        original.setEndNode(anchor.pos.copy());
        next.setStartNode(anchor.pos.copy());

        int nodeBeforeIndex = 0;
        for (int i = 0; i < original.path.size(); i++) {
            if (original.path.get(i).dist(anchor.pos) < Settings.distanceToNodeThreshold) {
                nodeBeforeIndex = i - 1;
                break;
            }
        }
        nodeBeforeIndex = max(0, nodeBeforeIndex);
        float targetHeading = PVector.sub(anchor.pos, original.path.get(nodeBeforeIndex)).heading();
        original.setEndHeading(targetHeading, true);

        original.updatePath();
        next.updatePath();

        original.updateUIWithValues();
        next.updateUIWithValues();

        segments.add(next);
        return next;
    }

    private Segment makeParallelSegment(Segment baseSegment, int direction) {
        PVector offset = PVector.sub(baseSegment.getNode(1), baseSegment.getStartNode()).rotate(direction * PI / 2)
                .setMag(baseSegment.segmentWidth);
        PVector start = PVector.add(baseSegment.getStartNode(), offset);

        Segment segment = new Segment(this, start);
        segment.setStartControlPoint(baseSegment.startHeading, baseSegment.startControlPointMag);
        segment.setEndControlPoint(baseSegment.endHeading, baseSegment.endControlPointMag);

        boolean foundAnchor = false;
        if (direction == 1) {
            // baseSegment.controlRightStart(segment);
            // baseSegment.controlRightEnd(segment);
            // for (Segment previousSegment : baseSegment.segmentsPrevious) {
            // for (Segment seg : previousSegment.controlledFullRight) {
            // seg.addSegmentNext(segment);
            //
            // foundAnchor = true;
            // segment.setStartAnchor(seg.endAnchor);
            // seg.endAnchor.beginSegments.add(segment);
            // }
            // }
        } else {
            // baseSegment.controlLeftStart(segment);
            // baseSegment.controlLeftEnd(segment);
            // for (Segment previousSegment : baseSegment.segmentsPrevious) {
            // for (Segment seg : previousSegment.controlledFullLeft) {
            // seg.addSegmentNext(segment);
            //
            // foundAnchor = true;
            // segment.setStartAnchor(seg.endAnchor);
            // seg.endAnchor.beginSegments.add(segment);
            // }
            // }
        }
        for (Anchor anchor : anchors) {
            if (anchor.pos.dist(start) < 5) {
                foundAnchor = true;
                segment.setStartAnchor(anchor);
                anchor.beginSegments.add(segment);
                for (Segment seg : anchor.endSegments) {
                    seg.addSegmentNext(segment);
                }
                break;
            }
        }
        if (!foundAnchor) {
            Anchor startAnchor = new Anchor(this, start.copy());
            anchors.add(startAnchor);
            segment.setStartAnchor(startAnchor);
        }

        segment.setSettings(baseSegment);
        segment.setOpeningSegment(baseSegment.openingSegment);
        segment.setClosingSegment(baseSegment.closingSegment);

        segment.path = baseSegment.parallelSegment(baseSegment.segmentWidth, direction);
        segments.add(segment);

        foundAnchor = false;
        // for (Segment nextSegment : baseSegment.segmentsNext) {
        // if (direction == 1) {
        // for (Segment seg : nextSegment.controlledFullRight) {
        // seg.addSegmentPrevious(segment);
        //
        // foundAnchor = true;
        // segment.setEndAnchor(seg.startAnchor);
        // seg.startAnchor.endSegments.add(segment);
        // }
        // } else {
        // for (Segment seg : nextSegment.controlledFullLeft) {
        // seg.addSegmentPrevious(segment);
        //
        // foundAnchor = true;
        // segment.setEndAnchor(seg.startAnchor);
        // seg.startAnchor.endSegments.add(segment);
        // }
        // }
        // }
        for (Anchor anchor : anchors) {
            if (anchor.pos.dist(segment.path.getLast()) < 5) {
                foundAnchor = true;
                segment.setEndAnchor(anchor);
                anchor.endSegments.add(segment);
                for (Segment seg : anchor.beginSegments) {
                    seg.addSegmentPrevious(segment);
                }
                break;
            }
        }
        if (!foundAnchor) {
            Anchor endAnchor = new Anchor(this, segment.path.getLast().copy());
            anchors.add(endAnchor);
            endAnchor.setClosingAnchor(segment.closingSegment);
            segment.setEndAnchor(endAnchor);
        }

        // TODO: this use to be ", true" but I changed to false to fix a bug. Why was it
        // true? I know there was intent behind it
        ArrayList<Object[]> data = segment.getCrossedSegments(segments, false);
        // NOTE: we only choose the last one here because for now we are not making any
        // intermediate 4 way junctions
        if (data.size() > 0) {
            if (data.getLast()[1] == null)
                throw new RuntimeException("Anchor position is null when making this t-junction");

            Anchor junctionAnchor = new Anchor(this, (PVector) data.getLast()[1]);
            anchors.add(junctionAnchor);
            makeTJunction((Segment) data.getLast()[0], segment, junctionAnchor);
        }

        return segment;
    }

    private void makeParallelSegments(Segment baseSegment) {
        int offset = (parallelNumSegments < 0) ? -1 : 1;

        Segment controllingSegment = baseSegment;
        for (int i = 1; i < abs(parallelNumSegments); i++) {
            Segment segment = makeParallelSegment(controllingSegment, offset);
            controllingSegment = segment;
        }

        baseSegment.updateControlledPaths();
    }

    private void makeTJunction(Segment hovered, Segment comingIn, Anchor anchor) {
        Segment next = splitSegment(hovered, anchor);
        comingIn.addSegmentNext(next);
    }

    private Segment make4WayJunction(Segment crossed, Segment comingIn, PVector intersection) {
        Anchor anchor = new Anchor(this, intersection);
        anchors.add(anchor);
        return make4WayJunction(crossed, comingIn, anchor);
    }

    private Segment make4WayJunction(Segment crossed, Segment comingIn, Anchor anchor) {
        Segment crossedNext = splitSegment(crossed, anchor);
        Segment comingInNext = splitSegment(comingIn, anchor);

        comingIn.addSegmentNext(crossedNext);
        crossed.addSegmentNext(comingInNext);

        limitHeadings(comingIn, comingIn.start.dist(comingIn.end));
        limitHeadings(comingInNext, comingInNext.start.dist(comingInNext.end));
        limitHeadings(crossed, crossed.start.dist(crossed.end));
        limitHeadings(crossedNext, crossedNext.start.dist(crossedNext.end));

        return comingInNext;
    }

    private void limitHeadings(Segment segment, float mag) {
        segment.startControlPointMag = min(mag, segment.startControlPointMag);
        segment.endControlPointMag = min(mag, segment.endControlPointMag);
        segment.updatePath();
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

        // if (cursor.snappingToLeftOfSegment)
        // cursor.snappingToSegment.controlLeftStart(currentSegment);
        // else if (cursor.snappingToRightOfSegment)
        // cursor.snappingToSegment.controlRightStart(currentSegment);
        if (cursor.snappingToSegment != null) {
            currentSegment.setStartControlPoint(cursor.snappingToSegment.startHeading,
                    cursor.snappingToSegment.startControlPointMag);
        }

        currentSegment.addSegmentPrevious(previous);
        currentSegment.setSettings(previous);
        if (previous.endHeading != -Float.MIN_VALUE)
            currentSegment.setStartHeading(previous.endHeading, true);

        currentSegment.updateUIWithValues();

        // If we are hovering a pre-existing segment, then we need to split that one
        if (hovered != null) {
            makeTJunction(hovered, comingIn, currentAnchor);
            if (parallelMode && (parallelNumSegments < -1 || parallelNumSegments > 1)) {
                makeParallelSegments(comingIn);
            }
        } else {
            ArrayList<Segment> bases = new ArrayList<Segment>();
            bases.add(comingIn);
            // If we crossed a pre-existing segment, then we need to make a 4-way junction
            // Segment crossed = comingIn.getCrossedSegment(segments);
            if (makeIntermediateAnchors) {
                ArrayList<Object[]> data = comingIn.getCrossedSegments(segments, false);
                if (data.size() > 0) {
                    for (Object[] d : data) {
                        if (d[1] == null)
                            throw new RuntimeException("Anchor position is null when making this t-junction");

                        Segment crossed = (Segment) d[0];
                        PVector intersection = (PVector) d[1];
                        if (intersection == null)
                            continue;

                        // Snap to closer anchor if possible
                        Anchor junctionAnchor = null;
                        float distanceThreshold = 3f * Settings.pixelsPerMeter;
                        for (Anchor anchor : anchors) {
                            if (anchor.pos.dist(intersection) < distanceThreshold) {
                                junctionAnchor = anchor;
                                break;
                            }
                        }

                        if (junctionAnchor != null) {
                            Segment nextBase = make4WayJunction(crossed, comingIn, junctionAnchor);
                            bases.add(nextBase);
                        } else {
                            Segment nextBase = make4WayJunction(crossed, comingIn, intersection);
                            bases.add(nextBase);
                        }
                    }
                }
            }

            if (parallelMode && (parallelNumSegments < -1 || parallelNumSegments > 1)) {
                for (Segment base : bases) {
                    makeParallelSegments(base);
                }
            }
        }
    }

    private void completeCurrentSegmentWithAnchor(Anchor hoveredAnchor) {
        Segment comingIn = currentSegment;

        selectAnchor(hoveredAnchor);
        currentSegment.setEndAnchor(currentAnchor);
        float heading = currentSegment.getStraightHeading();
        if (currentSegment.type == SegmentType.STRAIGHT) {
            currentSegment.setHeadings(heading, heading);
            currentSegment.setPreviousEndHeading(heading);
            currentSegment.updatePath();
        }
        boolean updatedHeading = false;
        for (Segment segment : currentAnchor.beginSegments) {
            currentSegment.addSegmentNext(segment);
            if (!updatedHeading && currentSegment.type == SegmentType.BEZIER) {
                currentSegment.setEndControlPoint(segment.startHeading, segment.startControlPointMag);
                currentSegment.updatePath();
                updatedHeading = true;
            }
        }

        Segment previous = currentSegment;
        segmentPlaced = false;

        newSegment(cursor.pos, currentSegment.getStraightHeading());
        for (Segment segment : currentAnchor.endSegments) {
            currentSegment.addSegmentPrevious(segment);
        }
        currentSegment.setSettings(previous);
        if (previous.endHeading != -Float.MIN_VALUE)
            currentSegment.setStartHeading(previous.endHeading, true);

        if (parallelMode && (parallelNumSegments < -1 || parallelNumSegments > 1)) {
            makeParallelSegments(comingIn);
        }
    }

    private void branchSegment(Segment hoveredSegment, Anchor hoveredAnchor) {
        // Get closest two path points and average between them
        ArrayList<PVector> sortedPath = new ArrayList<>(hoveredSegment.path);
        sortedPath.sort(Comparator.comparingDouble(
                p -> PVector.dist(p, cursor.pos)));
        PVector pos = PVector.add(sortedPath.get(0), sortedPath.get(1)).div(2);

        if (hoveredAnchor == null) {
            currentAnchor = new Anchor(this, pos.copy());
            anchors.add(currentAnchor);
        } else {
            currentAnchor = hoveredAnchor;
            pos = currentAnchor.pos;
        }

        lastSelectedSegment = hoveredSegment;
        currentSegment = new Segment(this, pos.copy());
        currentSegment.setStartAnchor(currentAnchor);

        int index0 = hoveredSegment.path.indexOf(sortedPath.get(0));
        int index1 = hoveredSegment.path.indexOf(sortedPath.get(1));
        float heading = PVector.sub(sortedPath.get(1), sortedPath.get(0)).heading();
        heading += (index1 < index0) ? PI : 0;
        currentSegment.startHeading = heading;

        segments.add(currentSegment);
        segmentPlaced = false;

        currentSegment.setSettings(hoveredSegment);
        currentSegment.updateUIWithValues();

        Segment hoveredNext = splitSegment(hoveredSegment, currentAnchor);
        hoveredSegment.addSegmentNext(currentSegment);
    }

    public void mouseClicked() {
        if (sketch.mode != SimulationMode.BUILD)
            return;

        if (mouseButton == RIGHT) {
            if (currentSegment != null)
                deselectSegment();
            else if (currentAnchor != null)
                deselectAnchor();
            return;
        }

        // Make brand new segment that starts a path
        Anchor hoveredAnchor = getHoveredAnchor();
        Segment hoveredSegment = getHoveredSegment();
        if (currentAnchor == null && currentSegment == null && hoveredSegment == null && mouseButton == LEFT) {
            createBrandNewSegment(hoveredAnchor, hoveredSegment);
            return;
        }

        // Make new segment that continues path
        if (currentSegment != null && !segmentPlaced && !hoveringSegmentInfo() && mouseButton == LEFT) {
            if (hoveredAnchor == null)
                completeCurrentSegmentFree();
            else
                completeCurrentSegmentWithAnchor(hoveredAnchor);
        }

        if (hoveredSegment != null && (currentSegment == null || segmentPlaced)) {
            // 2 = middle
            if (mouseButton == 2) {
                if (currentSegment != null)
                    deselectSegment();
                selectSegment(hoveredSegment);
                segmentPlaced = true;
                return;
            } else if (mouseButton == LEFT && !hoveringSegmentInfo()) {
                branchSegment(hoveredSegment, hoveredAnchor);
            }
        }
    }

    public void mouseDragged() {
        if (mouseButton != LEFT || sketch.mode != SimulationMode.BUILD)
            return;
        if (cursor.draggedAnchor != null) {
            cursor.draggedAnchor.setPos(cursor.pos);
            if (cursor.snappingWall) {
                if (cursor.draggedAnchor.beginSegments.size() == 0 && cursor.draggedAnchor.endSegments.size() != 0) {
                    cursor.draggedAnchor.setClosingAnchor(true);
                } else if (cursor.draggedAnchor.beginSegments.size() != 0
                        && cursor.draggedAnchor.endSegments.size() == 0) {
                    cursor.draggedAnchor.setOpeningAnchor(true);
                }
            }
        }
    }

    public void keyPressed() {
        if (sketch.mode != SimulationMode.BUILD)
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
                currentSegment.updateUIWithValues();
                currentSegment.updatePath();
            }
        } else if (key == 's') {
            if (currentSegment != null && segmentPlaced) {
                Segment hovered = getHoveredSegment();
                if (hovered != null) {
                    if (currentSegment.segmentsNext.contains(hovered)) {
                        if (currentSegment.segmentsNextOptions.contains(hovered))
                            currentSegment.segmentsNextOptions.remove(hovered);
                        else
                            currentSegment.segmentsNextOptions.add(hovered);
                    }
                }
            }
        } else if (key == 'S') {
            if (currentSegment != null && segmentPlaced) {
                if (currentSegment.segmentsNextOptions.size() > 0)
                    currentSegment.segmentsNextOptions.clear();
                else {
                    for (Segment segment : currentSegment.segmentsNext) {
                        currentSegment.segmentsNextOptions.add(segment);
                    }
                }
            }
        } else if (key == 'r') {
            if (currentSegment != null && segmentPlaced) {
                currentSegment.reverse();
            }
        } else if (key == 'i') {
            makeIntermediateAnchors = !makeIntermediateAnchors;
        } else if (keyString.equals("Delete")) {
            if (currentSegment != null)
                deleteCurrentSegment();
            Anchor hovered = getHoveredAnchor();
            if (hovered != null)
                deleteAnchor(hovered);
        } else if (keyString.equals("Up") && keysPressed.contains("Ctrl")) {
            referenceImageOffset.add(0, 25);
        } else if (keyString.equals("Down") && keysPressed.contains("Ctrl")) {
            referenceImageOffset.add(0, -25);
        } else if (keyString.equals("Left") && keysPressed.contains("Ctrl")) {
            referenceImageOffset.add(25, 0);
        } else if (keyString.equals("Right") && keysPressed.contains("Ctrl")) {
            referenceImageOffset.add(-25, 0);
        } else if (key == ' ' && keysPressed.contains("Ctrl")) {
            referenceImageShow = !referenceImageShow;
        }
    }

    public void mouseScrolled(int amount) {
        if (keysPressed.contains("Ctrl")) {
            if (amount > 0)
                referenceImageScale *= 0.9f;
            else
                referenceImageScale *= 1.1f;
        }
    }
}
