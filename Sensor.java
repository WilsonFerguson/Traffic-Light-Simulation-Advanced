import library.core.*;
import java.util.*;
import java.awt.geom.Line2D;

class Sensor extends PComponent implements EventIgnorer, Saveable {
    int id;
    SensorType type;
    int nodeIndexStart;
    int nodeIndexEnd;
    HashSet<Segment> segments = new HashSet<>();
    boolean detected = false;
    boolean reverse = false;

    int lastDetectedFrame = 0;
    int detectedCount = 0;

    float distanceFromEndToIntersection = 0;

    PVector pos;
    float length, rotation;

    public Sensor(int id, SensorType type, int nodeIndexStart, int nodeIndexEnd, float distanceFromEndToIntersection,
            Segment... segments) {
        this.id = id;
        this.type = type;
        this.nodeIndexStart = nodeIndexStart;
        this.nodeIndexEnd = nodeIndexEnd;
        this.distanceFromEndToIntersection = distanceFromEndToIntersection;

        for (Segment segment : segments) {
            this.segments.add(segment);
        }

        Segment segment = segments[0];
        pos = PVector.add(segment.getNode(nodeIndexStart), segment.getNode(nodeIndexEnd)).div(2);
        length = segment.getNode(nodeIndexStart).dist(segment.getNode(nodeIndexEnd));
        rotation = PVector.sub(segment.getNode(nodeIndexEnd), segment.getNode(nodeIndexStart)).heading();
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    public void detect() {
        boolean previouslyDetected = detected;
        detected = false;

        PVector sensorStart = segments.iterator().next().getNode(nodeIndexStart);
        PVector sensorEnd = segments.iterator().next().getNode(nodeIndexEnd);
        for (RoadUser user : segments.iterator().next().builder.sketch.trafficManager.traffic) {
            if (!segments.contains(user.currentSegment) || user.segmentReverse != reverse)
                continue;

            PVector carStart = PVector.add(user.pos, PVector.fromAngle(user.heading).mult(user.length / 2));
            PVector carEnd = PVector.add(user.pos, PVector.fromAngle(user.heading).mult(-user.length / 2));
            if (isOverlapping(sensorStart, sensorEnd, carStart, carEnd, user.width)) {
                detected = true;
                if (!previouslyDetected) {
                    lastDetectedFrame = frameCount;
                    detectedCount++;
                }
                return;
            }
        }

        if (previouslyDetected)
            lastDetectedFrame = frameCount;
    }

    private boolean isOverlapping(PVector sensorStart, PVector sensorEnd,
            PVector carStart, PVector carEnd,
            float carWidth) {

        float sensorWidth = segments.iterator().next().segmentWidth;

        // Direction vectors
        float sdx = sensorEnd.x - sensorStart.x;
        float sdy = sensorEnd.y - sensorStart.y;

        float cdx = carEnd.x - carStart.x;
        float cdy = carEnd.y - carStart.y;

        // Normalize directions
        float sl = 1.0f / sqrt(sdx * sdx + sdy * sdy);
        float cl = 1.0f / sqrt(cdx * cdx + cdy * cdy);

        sdx *= sl;
        sdy *= sl;
        cdx *= cl;
        cdy *= cl;

        // Four separating axes (two directions and their perpendiculars)
        float[] axesX = { -sdy, sdx, -cdy, cdx };
        float[] axesY = { sdx, sdy, cdx, cdy };

        // Center difference
        float dx = (carStart.x + carEnd.x - sensorStart.x - sensorEnd.x) * 0.5f;
        float dy = (carStart.y + carEnd.y - sensorStart.y - sensorEnd.y) * 0.5f;

        float sensorLength = 1.0f / sl;
        float carLength = 1.0f / cl;

        for (int i = 0; i < 4; i++) {
            float ax = axesX[i];
            float ay = axesY[i];

            float distance = abs(dx * ax + dy * ay);

            float sensorRadius = sensorLength * 0.5f * abs(sdx * ax + sdy * ay)
                    + sensorWidth * 0.5f * abs(-sdy * ax + sdx * ay);

            float carRadius = carLength * 0.5f * abs(cdx * ax + cdy * ay)
                    + carWidth * 0.5f * abs(-cdy * ax + cdx * ay);

            if (distance > sensorRadius + carRadius) {
                return false;
            }
        }

        return true;
    }

    /**
     * Resets the number of detected road users as well as sets detected = false. We
     * do the latter so that if people are still just sitting on the sensor, then
     * detectedCount will instead be at 1 (rather than 0) so the traffic system
     * knows there are people waiting.
     */
    public void resetCount() {
        detectedCount = 0;
        detected = false;
    }

    public void show() {
        if (detected) {
            fill(255, 180);
        } else {
            noFill();
        }
        stroke(50);
        strokeWeight(2);

        Segment segment = segments.iterator().next();
        float segmentWidth = segment.segmentWidth;
        float sensorWidth = segmentWidth * 0.7f;
        if (type == SensorType.QUEUE)
            sensorWidth = segmentWidth * 0.4f;

        if (type == SensorType.QUEUE) {
            beginShape();

            // There
            for (int i = nodeIndexStart; i < nodeIndexEnd; i++) {
                PVector node = segment.path.get(i);
                PVector next = segment.path.get(i + 1);

                PVector offset = PVector.sub(next, node).normalize().rotate(-PI / 2).mult(sensorWidth / 2);
                vertex(PVector.add(node, offset));

                // Finish out this line by adding the same offset to the final node. This is
                // okay because the exit paths are always straight
                if (i == nodeIndexEnd - 1) {
                    vertex(PVector.add(next, offset));
                }
            }

            // Back
            for (int i = nodeIndexEnd; i > nodeIndexStart; i--) {
                PVector node = segment.path.get(i);
                PVector next = segment.path.get(i - 1);

                PVector offset = PVector.sub(next, node).normalize().rotate(-PI / 2).mult(sensorWidth / 2);
                vertex(PVector.add(node, offset));

                // Finish out this line by adding the same offset to the final node. This is
                // okay because the exit paths are always straight
                if (i == nodeIndexStart + 1) {
                    vertex(PVector.add(next, offset));
                }
            }
            endShape(CLOSE);
        } else {
            push();
            translate(pos);
            rotate(rotation);

            if (segments.iterator().next().trafficType == TrafficType.BIKE_2WAY) {
                rect(length / 4 + 1, 0, length / 2 - 1, sensorWidth);
                rect(-length / 4 - 1, 0, length / 2 - 1, sensorWidth);
            } else {
                rect(0, 0, length, sensorWidth);
            }
            pop();
        }

    }

    @Override
    public String toString() {
        return "Sensor (node index start/end): " + nodeIndexStart + " " + nodeIndexEnd;
    }

    public String generateSaveString() {
        String output = "";

        output += id + "\n";
        output += type + "\n";
        output += nodeIndexStart + "\n";
        output += nodeIndexEnd + "\n";
        output += detected + "\n";
        output += reverse + "\n";
        output += distanceFromEndToIntersection + "\n";
        output += pos + "\n";
        output += length + "\n";
        output += rotation + "\n";

        output += "s";
        output = generateSaveStringAddSegments(output, segments);

        return output;
    }

    private String generateSaveStringAddSegments(String output, HashSet<Segment> segments) {
        for (Segment segment : segments) {
            output += segment.id + " ";
        }
        return output.trim() + "\n";
    }

    public static ArrayList<Sensor> loadIntersection(Sketch sketch, String pathToData) {
        String[] data = Helper.loadStrings(pathToData);
        if (data.length == 1)
            return new ArrayList<>();
        int count = 0;

        ArrayList<Sensor> sensors = new ArrayList<>();
        while (count < data.length) {
            int id = Integer.parseInt(data[count]);
            count++;

            SensorType type = SensorType.valueOf(data[count]);
            count++;

            int nodeIndexStart = Integer.parseInt(data[count]);
            count++;

            int nodeIndexEnd = Integer.parseInt(data[count]);
            count++;

            boolean detected = Boolean.parseBoolean(data[count]);
            count++;

            boolean reverse = Boolean.parseBoolean(data[count]);
            count++;

            float distanceFromEndToIntersection = Float.parseFloat(data[count]);
            count++;

            PVector pos = PVector.parseString(data[count]);
            count++;

            float length = Float.parseFloat(data[count]);
            count++;

            float rotation = Float.parseFloat(data[count]);
            count++;

            HashSet<Segment> segments = getSegments(sketch, data[count]);
            count++;

            Sensor sensor = new Sensor(id, type, nodeIndexStart, nodeIndexEnd, distanceFromEndToIntersection,
                    (Segment[]) segments.toArray(new Segment[0]));
            sensor.setReverse(reverse);
            sensor.pos = pos;
            sensor.length = length;
            sensor.rotation = rotation;
            sensors.add(sensor);

            // Increase count to skip empty line
            count++;
        }

        return sensors;
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
