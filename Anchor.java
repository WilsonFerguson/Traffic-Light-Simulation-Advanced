import library.core.*;
import java.util.*;

class Anchor extends PComponent {
    PVector pos;
    Builder builder;

    boolean openingAnchor;
    boolean closingAnchor;

    /**
     * List of segments that start at this anchor
     */
    HashSet<Segment> beginSegments;
    /**
     * List of segments that end at this anchor
     */
    HashSet<Segment> endSegments;

    public Anchor(Builder builder, PVector pos) {
        this.builder = builder;
        this.pos = pos;

        beginSegments = new HashSet<Segment>();
        endSegments = new HashSet<Segment>();
    }

    public void setOpeningAnchor(boolean openingAnchor) {
        this.openingAnchor = openingAnchor;
        for (Segment segment : beginSegments) {
            segment.setOpeningSegment(openingAnchor);
        }
    }

    public void setClosingAnchor(boolean closingAnchor) {
        this.closingAnchor = closingAnchor;
        for (Segment segment : endSegments) {
            segment.setClosingSegment(closingAnchor);
        }
    }

    public void setPos(PVector pos) {
        this.pos = pos;
        for (Segment segment : beginSegments) {
            segment.setStartNode(pos);
        }
        for (Segment segment : endSegments) {
            segment.setEndNode(pos);
        }

        for (Segment segment : beginSegments) {
            segment.updatePath(true);
        }
        for (Segment segment : endSegments) {
            segment.updatePath(true);
        }
    }

    public void show() {
        fill(0);
        noStroke();
        if (builder.currentAnchor == this) {
            stroke(255);
            strokeWeight(3);
        }
        circle(pos, Settings.sizeAnchor);
    }

    public boolean hovering() {
        return dist(pos, mouse) < Settings.sizeAnchor;
    }
}
