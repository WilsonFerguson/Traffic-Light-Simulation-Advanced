import library.core.*;
import java.util.*;

class Sensor extends PComponent implements EventIgnorer {
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

    public Sensor(int nodeIndexStart, int nodeIndexEnd, float distanceFromEndToIntersection, Segment... segments) {
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

        for (RoadUser user : segments.iterator().next().builder.sketch.trafficManager.traffic) {
            if (!segments.contains(user.currentSegment))
                continue;

            // We include these bounds because in real life only the start of the car has to
            // be over the sensor so this sort of mimics that (because the nodes are close
            // enough together that if you are at one index, the start of your vehile is
            // probably roughly over the next one)
            if (!reverse) {
                if (user.targetSegmentIndex > nodeIndexStart && user.targetSegmentIndex <= nodeIndexEnd + 1) {
                    detected = true;
                    if (!previouslyDetected) {
                        lastDetectedFrame = frameCount;
                        detectedCount++;
                    }
                    return;
                }
            } else {
                if (user.targetSegmentIndex < nodeIndexStart && user.targetSegmentIndex >= nodeIndexEnd - 1) {
                    detected = true;
                    if (!previouslyDetected) {
                        lastDetectedFrame = frameCount;
                        detectedCount++;
                    }
                    return;
                }
            }
        }

        if (previouslyDetected)
            lastDetectedFrame = frameCount;
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
        // length == 0 for pedestrians so we obviously don't draw the sensor
        if (length == 0)
            return;

        push();
        translate(pos);
        rotate(rotation);

        if (detected) {
            noStroke();
            fill(255, 100);
        } else {
            stroke(0, 100);
            strokeWeight(3);
            noFill();
        }

        rect(0, 0, length, segments.iterator().next().segmentWidth * 0.9);

        pop();
    }

    @Override
    public String toString() {
        return "Sensor (node index start/end): " + nodeIndexStart + " " + nodeIndexEnd;
    }
}
