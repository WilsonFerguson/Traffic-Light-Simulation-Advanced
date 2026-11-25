import library.core.*;
import java.util.*;

class Anchor extends PComponent implements Saveable {
    int id;
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

    public Anchor(Builder builder, int id, PVector pos) {
        this.builder = builder;
        this.id = id;
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
            segment.updatePath();
        }
        for (Segment segment : endSegments) {
            segment.updatePath();
        }
    }

    public void setPos(PVector pos, HashMap<Segment, Integer> visited) {
        this.pos = pos;
        for (Segment segment : beginSegments) {
            segment.setStartNode(pos);
        }
        for (Segment segment : endSegments) {
            segment.setEndNode(pos);
        }

        for (Segment segment : beginSegments) {
            segment.updatePath(visited, true);
        }
        for (Segment segment : endSegments) {
            segment.updatePath(visited, true);
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

    public String generateSaveString() {
        String output = "";

        output += id + "\n";
        output += pos + "\n";
        output += openingAnchor + "\n";
        output += closingAnchor + "\n";

        output += "b";
        output = generateSaveStringAddSegments(output, beginSegments);
        output += "e";
        output = generateSaveStringAddSegments(output, endSegments);

        return output;
    }

    private String generateSaveStringAddSegments(String output, HashSet<Segment> segments) {
        for (Segment segment : segments) {
            output += segment.id + " ";
        }
        return output.trim() + "\n";
    }

    public static void loadIntersection(Sketch sketch, String pathToData) {
        String[] data = Helper.loadStrings(pathToData);
        int count = 0;

        while (count < data.length) {
            int id = Integer.parseInt(data[count]);
            count++;

            PVector pos = PVector.parseString(data[count]);
            count++;

            boolean openingAnchor = Boolean.parseBoolean(data[count]);
            count++;

            boolean closingAnchor = Boolean.parseBoolean(data[count]);
            count++;

            HashSet<Segment> beginSegments = getSegments(sketch, data[count]);
            count++;

            HashSet<Segment> endSegments = getSegments(sketch, data[count]);
            count++;

            Anchor anchor = new Anchor(sketch.builder, id, pos);
            anchor.openingAnchor = openingAnchor;
            anchor.closingAnchor = closingAnchor;
            anchor.beginSegments = beginSegments;
            anchor.endSegments = endSegments;
            sketch.builder.anchors.add(anchor);

            // Increase count to skip empty line
            count++;
        }
    }

    private static HashSet<Segment> getSegments(Sketch sketch, String lineString) {
        String[] line = lineString.substring(1).split(" ");
        HashSet<Segment> segments = new HashSet<>();
        for (String str : line) {
            if (str.length() == 0)
                continue;

            int segmentID = Integer.parseInt(str);
            for (Segment segment : sketch.builder.segments) {
                if (segment.id == segmentID) {
                    segments.add(segment);
                    break;
                }
            }
        }

        return segments;
    }
}
