import library.core.*;
import java.util.*;

class Movement extends PComponent implements EventIgnorer {
    String name;
    iVRI iVRI;
    Sketch sketch;
    Builder builder;

    ArrayList<Segment> allMovements;
    ArrayList<ArrayList<Segment>> groups = new ArrayList<>();

    ArrayList<Float> delays = null;

    HashMap<Integer, ArrayList<Collision>> collisions = new HashMap<>();

    /**
     * Index of the entrance nodes for each group
     */
    HashMap<Integer, Integer> entranceNodes = new HashMap<>();
    HashMap<Integer, ArrayList<Sensor>> sensors = null;

    HashMap<Integer, Signal> signals = new HashMap<>();
    /**
     * At what frame the signal changed to its current color. -Integer.MAX_VALUE in
     * the beginning.
     */
    HashMap<Integer, Integer> signalStartTimes = new HashMap<>();
    HashMap<Integer, Signal> signalsNext = new HashMap<>();
    HashMap<Integer, Integer> signalNextStartTimes = new HashMap<>();

    /**
     * Set true for two stage crossings so that the first stage has to wait until
     * the timing works that once the person reaches the end of the first stage, the
     * second stage is ready to turn green.<br>
     * If false, then the first movement is free to turn whenever, even if the
     * second movement is not ready. False is used for bus/car lanes that have
     * different movements (so that buses get a green even if cars aren't ready
     * yet). In that case, buses must be the first group, cars the second
     */
    boolean mustWait;

    /**
     * Whether this movement is exclusively vulnerable road users or not.
     */
    boolean vulnerableOnly;
    boolean pedestrianOnly;
    boolean bikeOnly;

    public Movement(String name, iVRI iVRI, Sketch sketch) {
        this.name = name;
        this.iVRI = iVRI;
        this.sketch = sketch;
        builder = sketch.builder;
        mustWait = true;
    }

    public void update() {
        if (sensors != null) {
            for (int key : sensors.keySet()) {
                for (Sensor sensor : sensors.get(key)) {
                    sensor.detect();
                }
            }
        }

        for (int key : signalNextStartTimes.keySet()) {
            if (frameCount >= signalNextStartTimes.get(key)) {
                enactChangeSignal(key);
            }
        }
    }

    public void show() {
        if (sensors != null) {
            for (int key : sensors.keySet()) {
                for (Sensor sensor : sensors.get(key)) {
                    sensor.show();
                }
            }

            for (int key : signals.keySet()) {
                if (groups.get(key).getFirst().trafficType.isBidirectional()) {
                    // Draw the first set of stoplines/traffic lights
                    drawStopline(key);
                    drawTrafficLight(key);

                    // Draw the second set (in the opposite direction)
                    int sensorIndex = -1;
                    for (int i = 0; i < sensors.get(key).size(); i++) {
                        if (sensors.get(key).get(i).reverse) {
                            sensorIndex = i;
                            break;
                        }
                    }
                    if (sensorIndex == -1) {
                        System.err.println("Somehow didn't find a reverse sensor");
                    } else {
                        drawStopline(key, sensorIndex);
                        drawTrafficLight(key, sensorIndex);
                    }
                } else {
                    drawStopline(key);
                    drawTrafficLight(key);
                }
            }
        }
    }

    public boolean canTurnYellow(int index) {
        if (signals.get(index) != Signal.GREEN)
            return false;

        return frameCount - signalStartTimes.get(index) >= round(Settings.yellowTime * frameRate);
    }

    public boolean wantsGreen(int index) {
        for (Sensor sensor : sensors.get(index)) {
            if (sensor.detected || sensor.detectedCount > 0)
                return true;
        }

        return false;
    }

    public boolean hasConflictingGreen(int index) {
        for (Collision collision : collisions.get(index)) {
            if (iVRI.isOrTurningGreen(collision.exitSegment))
                return true;
        }

        if (mustWait) {
            if (index < groups.size() - 1) {
                for (int i = index + 1; i < groups.size(); i++) {
                    for (Collision collision : collisions.get(i)) {
                        if (iVRI.isOrTurningGreen(collision.exitSegment))
                            return true;
                    }
                }
            }
            if (index > 0) {
                for (int i = index - 1; i >= 0; i--) {
                    for (Collision collision : collisions.get(i)) {
                        if (iVRI.isOrTurningGreen(collision.exitSegment))
                            return true;
                    }
                }
            }
        }

        return false;
    }

    public int getEarliestRedStartTime(int index, boolean reverse) {
        if (signals.get(index) == Signal.RED)
            return frameCount;

        int myTime = 0;
        if (signals.get(index) == Signal.YELLOW) {
            myTime = abs(round(Settings.yellowTime * frameRate) - (frameCount - signalStartTimes.get(index)))
                    + frameCount;

            // It's yellow so of course this is simply the earliest red time
            return myTime;
        } else {
            int minTime = round(Settings.greenTimeMinimum * frameRate);
            int ongoingTime = frameCount - signalStartTimes.get(index);
            int yellowTime = round(Settings.yellowTime * frameRate);
            if (ongoingTime >= minTime)
                myTime = yellowTime + frameCount;
            else
                myTime = abs(minTime - ongoingTime) + yellowTime + frameCount;
        }

        if (!mustWait)
            return myTime;
        if ((reverse && index == groups.size() - 1) || (!reverse && index == 0))
            return myTime;

        int increment = reverse ? 1 : -1;
        int earliestTime = getEarliestRedStartTime(index + increment, reverse);
        // TODO: for this (and the following function) I don't think these entranceNodes stuff considers the fact that for reverse it's different. Maybe use the sensor nodeIndexEnd stuff
        int entranceTime = round(
                PVector.dist(groups.get(index).getFirst().getNode(entranceNodes.get(index)),
                        groups.get(index + increment).getFirst()
                                .getNode(entranceNodes.get(index + increment)))
                        / groups.get(index).getFirst().trafficType.getMaxSpeed() * frameRate);
        return max(myTime, earliestTime + entranceTime);
    }

    public int getEarliestGreenStartTime(int index, boolean reverse) {
        if (signals.get(index) == Signal.GREEN)
            return frameCount;

        int myTime = 0;
        int minimumRedTime = round(Settings.redTimeMinimum * frameRate);
        if (signals.get(index) == Signal.YELLOW) {
            int remainingYellowTime = abs(
                    round(Settings.yellowTime * frameRate) - (frameCount - signalStartTimes.get(index)));
            myTime = minimumRedTime + remainingYellowTime + frameCount;
        } else {
            int remainingRedTime = max(0, minimumRedTime - (frameCount - signalStartTimes.get(index)));
            myTime = remainingRedTime + frameCount;
        }

        if (!mustWait)
            return myTime;
        if ((reverse && index == 0) || (!reverse && index == groups.size() - 1))
            return myTime;

        int increment = reverse ? -1 : 1;
        int earliestTime = getEarliestGreenStartTime(index + increment, reverse);
        int entranceTime = round(
                PVector.dist(groups.get(index).getFirst().getNode(entranceNodes.get(index)),
                        groups.get(index + increment).getFirst()
                                .getNode(entranceNodes.get(index + increment)))
                        / groups.get(index).getFirst().trafficType.getMaxSpeed() * frameRate);
        return max(myTime, earliestTime - entranceTime);
    }

    public void changeSignal(int index, Signal signal, int startTime) {
        signalsNext.put(index, signal);
        signalNextStartTimes.put(index, startTime);
    }

    private void enactChangeSignal(int index) {
        signals.put(index, signalsNext.get(index));
        signalStartTimes.put(index, frameCount);

        signalsNext.remove(index);
        signalNextStartTimes.remove(index);

        if (signals.get(index) == Signal.YELLOW) {
            signalsNext.put(index, Signal.RED);
            signalNextStartTimes.put(index, frameCount + round(Settings.yellowTime * frameRate));

            // We also reset the sensor detectedCounts
            for (Sensor sensor : sensors.get(index)) {
                sensor.resetCount();
            }
        }
    }

    public void setMustWait(boolean mustWait) {
        this.mustWait = mustWait;
    }

    public void calculateDetailsStart() {
        // Remove any empty groups
        for (int i = groups.size() - 1; i >= 0; i--) {
            if (groups.get(i).size() == 0) {
                groups.remove(i);
            }
        }

        allMovements = new ArrayList<>();
        for (ArrayList<Segment> group : groups) {
            for (Segment segment : group) {
                allMovements.add(segment);
            }
        }

        calculateVulnerableOnly();
        calculateEntranceNodes();
    }

    public void calculateDetails() {
        calculateDelays();
        calculateClearanceTimes();
        calculateSensors();
    }

    public void calculateVulnerableOnly() {
        vulnerableOnly = true;
        pedestrianOnly = true;
        bikeOnly = true;
        for (Segment movement : allMovements) {
            if (!movement.trafficType.isVulnerable()) {
                vulnerableOnly = false;
                pedestrianOnly = false;
                bikeOnly = false;
                return;
            }
            if (movement.trafficType != TrafficType.PEDESTRIAN)
                pedestrianOnly = false;
            if (movement.trafficType != TrafficType.BIKE_1WAY && movement.trafficType != TrafficType.BIKE_2WAY)
                bikeOnly = false;
        }
    }

    public void calculateEntranceNodes() {
        for (int i = 0; i < groups.size(); i++) {
            entranceNodes.put(i, calculateEntranceNode(i, false));
            if (!signals.containsKey(i)) {
                signals.put(i, Signal.RED);
                signalStartTimes.put(i, -Integer.MAX_VALUE);
            }
        }
    }

    private int calculateEntranceNode(int index, boolean reverse) {
        if (groups.get(index).size() > 1) {
            if (!reverse) {
                return 0;
            } else {
                throw new UnsupportedOperationException(
                        "in Movement.calculateEntranceNode(), the size of the group is > 1 but it's also reversible which is unsupported");
            }
        }

        Segment movement = groups.get(index).getFirst();
        int minimumIndex = Integer.MAX_VALUE;
        int maximumIndex = 0;
        for (Segment segment : builder.segments) {
            if (allMovements.contains(segment))
                continue;
            if (movement.segmentsPrevious.contains(segment) || movement.segmentsNext.contains(segment))
                continue;

            int intersectionIndex = getIntersectionIndex(movement, segment, reverse);
            if (reverse) {
                maximumIndex = max(maximumIndex, intersectionIndex);
            } else {
                minimumIndex = min(minimumIndex, intersectionIndex);
            }
        }
        if (reverse) {
            if (maximumIndex != 0) {
                if (maximumIndex == movement.path.size() - 1)
                    System.err.println(
                            "Warning! maximumIndex = movement.path.size() - 1 in Movement.calculateEntranceNode()");
                return maximumIndex + 1;
            } else {
                return movement.path.size() - 1;
            }
        } else {
            if (minimumIndex != Integer.MAX_VALUE) {
                if (minimumIndex == 0)
                    System.err.println("Warning! minimumIndex = 0 in Movement.calculateEntranceNode()");
                return minimumIndex - 1;
            } else {
                return 0;
            }
        }
    }

    public void calculateDelays() {
        if (groups.size() < 2)
            return;

        delays = new ArrayList<>();
        for (int i = 0; i < groups.size() - 1; i++) {
            // We only can calculate delays if there is only 1 movement in groups.get(i)
            if (groups.get(i).size() > 1) {
                delays.add(0f);
                continue;
            }

            Segment thisSegment = groups.get(i).getFirst();
            PVector nextStart = null;
            if (groups.get(i + 1).size() == 1) {
                nextStart = groups.get(i + 1).getFirst().getNode(entranceNodes.get(i + 1));
            } else {
                nextStart = groups.get(i + 1).getFirst().start;
            }
            float distanceToTravel = PVector.dist(thisSegment.end, nextStart);
            float delay = distanceToTravel / thisSegment.trafficType.getMaxSpeed();
            if (delay < 0.0001f)
                delay = 0;
            delays.add(delay);
        }
    }

    public void calculateClearanceTimes() {
        for (int i = 0; i < groups.size(); i++) {
            collisions.put(i, new ArrayList<>());
            // int entranceStart = entranceNodes.get(i);

            for (Segment movement : groups.get(i)) {
                for (Segment segment : iVRI.segmentMovementsMap.keySet()) {

                    Collision collision = null;
                    if (movement.trafficType.isBidirectional()) {
                        for (boolean reverse : new boolean[] { false, true }) {
                            for (boolean reverse2 : new boolean[] { false, true }) {
                                int entranceStart = entranceNodes.get(i);
                                if (reverse)
                                    entranceStart = calculateEntranceNode(i, true);

                                Collision collision2 = getCollision(movement, segment, entranceStart, reverse,
                                        reverse2);

                                if (collision == null) {
                                    collision = collision2;
                                } else if (collision2 != null) {
                                    if (collision2.clearanceTime > collision.clearanceTime)
                                        collision = collision2;
                                }
                            }
                        }
                    } else {
                        collision = getCollision(movement, segment, entranceNodes.get(i), false, false);

                        if (collision != null && (segment.trafficType == TrafficType.PEDESTRIAN
                                || segment.trafficType == TrafficType.BIKE_2WAY)) {
                            Collision collision2 = getCollision(movement, segment, entranceNodes.get(i), false, true);
                            if (collision2.clearanceTime > collision.clearanceTime)
                                collision = collision2;
                        }
                    }

                    if (collision != null)
                        collisions.get(i).add(collision);
                }
            }
        }
    }

    public Collision getCollision(Segment entrance, Segment exit, int entranceStart, boolean reverseEntrance,
            boolean reverseExit) {
        if (allMovements.contains(exit))
            return null;
        if (entrance.start.dist(exit.start) < 0.0001f)
            return null;

        int exitStart = 0;
        for (Movement movement : iVRI.movements) {
            boolean found = false;
            for (int i = 0; i < movement.groups.size(); i++) {
                if (movement.groups.get(i).contains(exit)) {
                    exitStart = movement.entranceNodes.get(i);
                    if (reverseExit) {
                        exitStart = movement.calculateEntranceNode(i, true);
                    }
                    found = true;
                    break;
                }
            }
            if (found)
                break;
        }

        Object[] entranceArray = getEntranceDistance(entrance, exit, entranceStart, exitStart, reverseEntrance);
        Object[] exitArray = getEntranceDistance(exit, entrance, exitStart, entranceStart, reverseExit);

        if (entranceArray == null || exitArray == null)
            return null;

        PVector intersection = (PVector) entranceArray[0];
        float entranceDistance = (float) entranceArray[1];
        float exitDistance = (float) exitArray[1];

        return new Collision(intersection, entrance, exit, entranceDistance, exitDistance);
    }

    private Object[] getEntranceDistance(Segment entrance, Segment exit, int entranceStartIndex, int exitStartIndex,
            boolean reverse) {
        float thresholdDistance = Settings.distanceToNodeThreshold * 0.85f;

        int step = reverse ? -1 : 1;
        for (int i = entranceStartIndex; reverse ? i >= 0 : i < entrance.path.size(); i += step) {
            for (int j = exitStartIndex; j < exit.path.size(); j++) {
                float dist = entrance.path.get(i).dist(exit.path.get(j));
                if (dist > thresholdDistance)
                    continue;

                PVector intersection = entrance.path.get(i);
                float extraDistance = 0;
                if ((i > 0 && !reverse) || (i < entrance.path.size() - 1 && reverse)) {
                    int iStart = max(0, i - 1);
                    int iEnd = min(entrance.path.size() - 1, i + 1);
                    int jStart = max(0, j - 1);
                    int jEnd = min(exit.path.size() - 1, j + 1);
                    if (iStart == iEnd || jStart == jEnd)
                        continue;
                    intersection = entrance.findIntersection(entrance.path.get(iStart), entrance.path.get(iEnd),
                            exit.path.get(jStart), exit.path.get(jEnd));

                    // entranceDistance found the distance up to and including path.get(i). But of
                    // course the intersection isn't right on i, so if the intersection is actually
                    // between i and i - 1 (or i + 1 for reverse), we need to subtract off a little
                    // distance if ((i > 0 && !reverse) || (i < entrance.path.size() - 1 &&
                    // reverse)) {
                    extraDistance = entrance.path.get(i).dist(intersection);
                    float k = PVector.sub(entrance.path.get(i), entrance.path.get(i - step))
                            .dot(PVector.sub(intersection, entrance.path.get(i)));
                    if (k < 0) {
                        extraDistance *= -1;
                    }
                }

                float entranceDistance = 0;
                if (reverse) {
                    for (int k = entrance.path.size() - 1; k > i + 1; k--) {
                        entranceDistance += PVector.dist(entrance.path.get(k), entrance.path.get(k - 1));
                    }
                } else {
                    for (int k = 0; k < i - 1; k++) {
                        entranceDistance += PVector.dist(entrance.path.get(k), entrance.path.get(k + 1));
                    }
                }
                entranceDistance += extraDistance;

                entranceDistance -= exit.segmentWidth / 2;
                entranceDistance = max(0, entranceDistance);

                return new Object[] { intersection, entranceDistance };
            }
        }

        return null;
    }

    public void calculateSensors() {
        if (sensors != null)
            return;

        sensors = new HashMap<>();

        if (!vulnerableOnly) {
            for (int i = 0; i < groups.size(); i++) {
                ArrayList<Segment> group = groups.get(i);
                if (group.size() == 1)
                    makeSensorIndividualSegment(group.getFirst(), i, -1);
                else
                    makeSensors(i);
            }
        } else {
            if (allMovements.getFirst().trafficType == TrafficType.BIKE_2WAY) {
                for (int i = 0; i < groups.size(); i++) {
                    if (i == 0) {
                        makeBikeSensors(i, false);
                        makeBikeSensors(i, true);
                    } else if (i == groups.size() - 1) {
                        makeBikeSensors(i, true);
                        makeBikeSensors(i, false);
                    } else {
                        makeBikeSensorFirst(i, false);
                        makeBikeSensorFirst(i, true);
                    }
                }
            } else if (allMovements.getFirst().trafficType == TrafficType.BIKE_1WAY) {
                for (int i = 0; i < groups.size(); i++) {
                    makeBikeSensors(i, false);
                }
            } else if (allMovements.getFirst().trafficType == TrafficType.PEDESTRIAN) {
                for (int i = 0; i < groups.size(); i++) {
                    if (i == 0) {
                        makeBikeSensorFirst(i, false);
                        makeBikeSensorFirst(i, true);
                    } else if (i == groups.size() - 1) {
                        makeBikeSensorFirst(i, false);
                        makeBikeSensorFirst(i, true);
                    } else {
                        makeBikeSensorFirst(i, false);
                        makeBikeSensorFirst(i, true);
                    }
                }
            }
        }
    }

    private void makeSensors(int index) {
        sensors.put(index, new ArrayList<>());

        int start = 0;
        int end = 1;
        Segment[] segments = groups.get(index).toArray(new Segment[0]);
        sensors.get(index).add(new Sensor(start, end, 0, segments));

        if (segments[0].segmentsPrevious.size() != 1)
            return;

        Segment segment = segments[0].segmentsPrevious.iterator().next();
        makeSensorIndividualSegment(segment, index, segment.path.size());
    }

    private void makeSensorIndividualSegment(Segment segment, int arrayIndex, int start) {
        if (!sensors.containsKey(arrayIndex))
            sensors.put(arrayIndex, new ArrayList<>());

        if (start == -1) {
            start = entranceNodes.get(arrayIndex) - 1;
            int end = start + 1;
            if (start < 0) {
                println("start < 0");
                start = 0;
                end = 1;
            }
            sensors.get(arrayIndex).add(new Sensor(start, end, 0, segment));
        }

        // The queue sensor should end approximately 8m before the start of the
        // just-added sensor. It should be about 16m long
        int maxGapNodes = round(8.0f * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);
        int minGapNodes = round(4.0f * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);

        int maxLengthNodes = round(16 * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);
        int minLengthNodes = round(6.0f * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);

        float distance = 0;
        if (start < minGapNodes + minLengthNodes) {
            if (segment.segmentsPrevious.size() != 1)
                return;
            segment = segment.segmentsPrevious.iterator().next();
            if (segment.segmentsNext.size() != 1)
                return;

            if (start + minGapNodes + minLengthNodes > segment.path.size())
                return;

            start += segment.path.size();
            distance = Settings.distanceToNodeThreshold * entranceNodes.get(arrayIndex);
        }

        int nextStart = start;
        start = start - maxGapNodes - maxLengthNodes;
        start = max(start, 0);
        int end = start + maxLengthNodes;
        end = min(end, nextStart - minGapNodes);
        end = min(end, segment.path.size() - 1);
        distance += Settings.distanceToNodeThreshold * (segment.path.size() - 1 - end);
        sensors.get(arrayIndex).add(new Sensor(start, end, distance, segment));

        // The farthest out sensor should be another 50m from the start of the queue
        // sensor
        int previousEnd = end;
        int numNodes = round(50 * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);
        int minNumNodes = round(12 * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);
        if (start < minNumNodes)
            return;
        end = start - numNodes;
        end = max(end, 2);
        start = end - 1;
        distance += Settings.distanceToNodeThreshold * (previousEnd - end);
        sensors.get(arrayIndex).add(new Sensor(start, end, distance, segment));
    }

    private void makeBikeSensors(int index, boolean reverse) {
        int start = makeBikeSensorFirst(index, reverse);
        int minGapNodes = round(6.0f * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);
        int maxGapNodes = round(25.0f * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);
        if (reverse) {
            if (groups.get(index).size() == 1) {
                Segment segment = groups.get(index).getFirst();
                float distance = 0;
                if (start + minGapNodes + 1 >= segment.path.size()) {
                    if (segment.segmentsNext.size() != 1)
                        return;

                    distance += Settings.distanceToNodeThreshold * (segment.path.size() - start);
                    segment = segment.segmentsNext.iterator().next();

                    if (allMovements.contains(segment))
                        return;
                    if (start + minGapNodes + 1 - groups.get(index).getFirst().path.size() > segment.path.size())
                        return;

                    start -= groups.get(index).getFirst().path.size();
                }

                start = start + maxGapNodes + 1;
                start = min(start, segment.path.size() - 1);
                int end = start - 1;
                distance += Settings.distanceToNodeThreshold * end;
                Sensor sensor = new Sensor(start, end, distance, segment);
                sensor.setReverse(true);
                sensors.get(index).add(sensor);
            } else {
                // TODO: do
                throw new UnsupportedOperationException();
            }
        } else {
            if (groups.get(index).size() == 1) {
                float distance = 0;
                Segment segment = groups.get(index).getFirst();
                if (start < minGapNodes + 1) {
                    if (segment.segmentsPrevious.size() != 1)
                        return;
                    segment = segment.segmentsPrevious.iterator().next();

                    if (allMovements.contains(segment))
                        return;
                    if (start + minGapNodes + 1 > segment.path.size())
                        return;

                    start += segment.path.size();
                    distance = Settings.distanceToNodeThreshold * entranceNodes.get(index);
                }

                start = start - maxGapNodes - 1;
                start = max(start, 0);
                int end = start + 1;
                distance += Settings.distanceToNodeThreshold * (segment.path.size() - 1 - end);
                sensors.get(index).add(new Sensor(start, end, distance, segment));
            } else {
                // TODO: do
                throw new UnsupportedOperationException();
            }
        }
    }

    private int makeBikeSensorFirst(int index, boolean reverse) {
        if (!sensors.containsKey(index))
            sensors.put(index, new ArrayList<>());

        if (reverse) {
            if (groups.get(index).size() == 1) {
                int start = calculateEntranceNode(index, true) + 1;
                int end = start - 1;
                if (start >= groups.get(index).get(0).path.size()) {
                    println("Vulnerable user: start >= path size");
                    start = groups.get(index).get(0).path.size() - 1;
                    end = start - 1;
                }
                Sensor sensor = new Sensor(start, end, 0, groups.get(index).get(0));
                sensor.setReverse(true);
                sensors.get(index).add(sensor);
                return start;
            } else {
                throw new UnsupportedOperationException();
            }
        } else {
            if (groups.get(index).size() == 1) {
                int start = entranceNodes.get(index) - 1;
                int end = start + 1;
                if (start < 0) {
                    println("Vulnerable user: start < 0");
                    start = 0;
                    end = 1;
                }
                sensors.get(index).add(new Sensor(start, end, 0, groups.get(index).get(0)));
                return start;
            } else {
                int start = 0;
                int end = 1;
                Segment[] segments = groups.get(index).toArray(new Segment[0]);
                sensors.get(index).add(new Sensor(start, end, 0, segments));
                return start;
            }
        }
    }

    public int getIntersectionIndex(Segment incoming, Segment other, boolean reverse) {
        float thresholdDistance = Settings.distanceToNodeThreshold * 0.85f;

        if (reverse) {
            for (int i = incoming.path.size() - 1; i >= 0; i--) {
                for (int j = 0; j < other.path.size(); j++) {
                    float dist = incoming.path.get(i).dist(other.path.get(j));
                    if (dist < thresholdDistance) {
                        return i;
                    }
                }
            }
            return 0;
        } else {
            for (int i = 0; i < incoming.path.size(); i++) {
                for (int j = 0; j < other.path.size(); j++) {
                    float dist = incoming.path.get(i).dist(other.path.get(j));
                    if (dist < thresholdDistance) {
                        return i;
                    }
                }
            }
            return Integer.MAX_VALUE;
        }
    }

    private void drawStopline(int index) {
        drawStopline(index, 0);
    }

    private void drawStopline(int index, int sensorIndex) {
        if (groups.get(index).getFirst().trafficType == TrafficType.PEDESTRIAN)
            return;

        push();
        stroke(220, 150);
        strokeWeight(3);
        float rotation = sensors.get(index).get(sensorIndex).rotation;
        PVector offset = PVector.fromAngle(rotation + PI / 2).mult(groups.get(index).getFirst().segmentWidth / 2);
        PVector base = groups.get(index).getFirst().getNode(sensors.get(index).get(sensorIndex).nodeIndexEnd);
        line(PVector.sub(base, offset), PVector.add(base, offset));
        pop();
    }

    private void drawTrafficLight(int index) {
        drawTrafficLight(index, 0);
    }

    private void drawTrafficLight(int index, int sensorIndex) {
        Segment segment = groups.get(index).getFirst();
        float rotation = sensors.get(index).get(sensorIndex).rotation + PI / 2;
        float signalRadius = segment.segmentWidth / 2.2f;
        float effectiveRadius = segment.trafficType == TrafficType.BIKE_2WAY ? signalRadius / 2 : signalRadius;
        float w = effectiveRadius * 0.3f;
        float h = effectiveRadius * 2;
        float startY = -h;

        push();

        PVector translation = segment.getNode(sensors.get(index).get(sensorIndex).nodeIndexEnd).copy();
        translation.add(PVector.fromAngle(rotation).rotate(PI / 2).mult(h * 1.3));
        if (segment.trafficType == TrafficType.BIKE_2WAY) {
            translation.add(PVector.fromAngle(rotation).mult(segment.segmentWidth / 4));
        }

        translate(translation);
        rotate(rotation);
        stroke(200);
        strokeWeight(2);
        fill(30);
        rectMode(CENTER);

        // TODO: better determination of whether it's ov or not. Also see checklist item
        // in Trello about placement of lights (under Traffic light system)
        boolean ovLane = segment.trafficType == TrafficType.OV;

        if (ovLane) {
            translate(PVector.fromAngle(rotation - PI / 2).setMag(signalRadius));
            circle(PVector.zero(), signalRadius * 2);
        } else {
            if (segment.trafficType != TrafficType.PEDESTRIAN) {
                rect(PVector.zero(), effectiveRadius * 2, effectiveRadius * 4, effectiveRadius * 2);
            } else {
                rect(PVector.zero(), signalRadius * 2, signalRadius * 3, signalRadius * 2);
            }
        }
        noStroke();

        Signal signal = signals.get(index);
        if (segment.trafficType != TrafficType.PEDESTRIAN) {
            if (ovLane) {
                float radius = signalRadius * 2 / 4.5f;
                drawOVTrafficLightSymbol(index, radius);
                translate(PVector.fromAngle(rotation - PI / 2).setMag(-signalRadius));
            } else {

                drawTrafficLightSymbol(index, new PVector(0, -effectiveRadius * 1.1),
                        color(Settings.lightRed, signal == Signal.RED ? 255 : 40), effectiveRadius);
                drawTrafficLightSymbol(index, new PVector(0, effectiveRadius * 1.1),
                        color(Settings.lightGreen, signal == Signal.GREEN ? 255 : 40), effectiveRadius);
                drawTrafficLightSymbol(index, PVector.zero(),
                        color(Settings.lightYellow, signal == Signal.YELLOW ? 255 : 40),
                        effectiveRadius);
            }
        } else {
            drawTrafficLightSymbol(index, new PVector(0, -signalRadius * 0.55),
                    color(Settings.lightRed, signal == Signal.RED ? 255 : 20), signalRadius);
            float alpha = signal == Signal.GREEN ? 255 : 20;
            if (signal == Signal.YELLOW) {
                int colorPosition = floor(
                        (frameCount - signalStartTimes.get(index)) / (float) Settings.lightPedestrianFlashInterval);
                while (colorPosition > 2) {
                    colorPosition -= 2;
                }
                if (colorPosition < 1) {
                    alpha = map(colorPosition, 0, 1, 255, 20);
                } else {
                    alpha = map(colorPosition, 1, 2, 20, 255);
                }
            }
            drawTrafficLightSymbol(index, new PVector(0, signalRadius * 0.55),
                    color(Settings.lightGreen, alpha), signalRadius);
        }

        pop();
    }

    private void drawOVTrafficLightSymbol(int index, float radius) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                fill(255, 5);
                circle(x * radius, y * radius, radius);
            }
        }
        switch (signals.get(index)) {
            case RED:
                fill(Settings.lightRed);
                circle(-radius, 0, radius);
                circle(radius, 0, radius);
                break;
            case YELLOW:
                fill(Settings.lightYellow);
                circle(0, 0, radius);
                break;
            case GREEN:
                // So generic direction
                fill(255, 200);
                if (groups.get(index).size() > 1) {
                    circle(-radius, -radius, radius);
                    circle(0, -radius, radius);
                    circle(radius, -radius, radius);
                    circle(0, radius, radius);
                } else {
                    // switch (direction) {
                    // case LEFT:
                    // circle(-radius, -radius, radius);
                    // circle(radius, radius, radius);
                    // break;
                    // case RIGHT:
                    // circle(radius, -radius, radius);
                    // circle(-radius, radius, radius);
                    // break;
                    // case STRAIGHT:
                    // circle(0, -radius, radius);
                    // circle(0, radius, radius);
                    // break;
                    // default:
                    // break;
                    // }
                    // TODO: do

                    // TEMPORARY:
                    circle(-radius, -radius, radius);
                    circle(0, -radius, radius);
                    circle(radius, -radius, radius);
                    circle(0, radius, radius);
                }
                break;
        }
    }

    private void drawTrafficLightSymbol(int index, PVector pos, color col, float signalRadius) {
        // if (type == MovementType.CAR_RIGHT) {
        // strokeWeight(2);
        // stroke(col);
        // line(pos.x - signalRadius * 0.3, pos.y + 0, pos.x + signalRadius * 0.3, pos.y
        // + 0);
        // line(pos.x + 0, pos.y - signalRadius * 0.3, pos.x + signalRadius * 0.3, pos.y
        // + 0);
        // line(pos.x + 0, pos.y + signalRadius * 0.3, pos.x + signalRadius * 0.3, pos.y
        // + 0);
        // } else if (type == MovementType.CAR_LEFT) {
        // strokeWeight(2);
        // stroke(col);
        // line(pos.x + signalRadius * 0.3, pos.y + 0, pos.x - signalRadius * 0.3, pos.y
        // + 0);
        // line(pos.x + 0, pos.y - signalRadius * 0.3, pos.x - signalRadius * 0.3, pos.y
        // + 0);
        // line(pos.x + 0, pos.y + signalRadius * 0.3, pos.x - signalRadius * 0.3, pos.y
        // + 0);
        // } else {
        // noStroke();
        // fill(col);
        // circle(pos, signalRadius);
        // }
        if (groups.get(index).size() > 1) {
            noStroke();
            fill(col);
            circle(pos, signalRadius);
        } else {
            // TODO: do

            // TEMPORARY:
            noStroke();
            fill(col);
            circle(pos, signalRadius);
        }
    }
}
