import library.core.*;
import java.util.*;
import java.awt.geom.Line2D;

class Movement extends PComponent implements EventIgnorer, Saveable {
    int id;
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
    /**
     * Hashmap mapping the group number to the index into the sensors hashmap for
     * which the first reverse sensor appears. If there is no reverse entry, then
     * there is no entry into this map
     */
    HashMap<Integer, Integer> sensorReverseStartIndices = new HashMap<>();

    HashMap<Integer, SignalGroup> signals = new HashMap<>();
    /**
     * At what frame the signal changed to its current color. -Integer.MAX_VALUE in
     * the beginning.
     */
    HashMap<Integer, SignalTime> signalStartTimes = new HashMap<>();
    HashMap<Integer, SignalGroup> signalsNext = new HashMap<>();
    HashMap<Integer, SignalTime> signalNextStartTimes = new HashMap<>();

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

    boolean drawTrafficLightCountdown = true;
    int timeUntilGreenCounterMax = -1;

    public static class SignalTime {
        int timeNormal = Integer.MAX_VALUE;
        int timeReverse = Integer.MAX_VALUE;

        public SignalTime(int timeNormal, int timeReverse) {
            this.timeNormal = timeNormal;
            this.timeReverse = timeReverse;
        }

        public SignalTime() {
        }

        public static SignalTime make(int time, boolean reverse) {
            return new SignalTime().set(time, reverse);
        }

        public SignalTime set(int time, boolean reverse) {
            if (reverse)
                timeReverse = time;
            else
                timeNormal = time;

            return this;
        }

        public SignalTime remove(boolean reverse) {
            return set(Integer.MAX_VALUE, reverse);
        }

        public int get(boolean reverse) {
            if (reverse)
                return timeReverse;
            return timeNormal;
        }
    }

    public Movement(int id, String name, iVRI iVRI, Sketch sketch) {
        this.name = name;
        this.iVRI = iVRI;
        this.sketch = sketch;
        this.id = id;
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
            SignalTime signalTime = signalNextStartTimes.get(key);
            if (frameCount >= signalTime.get(false))
                enactChangeSignal(key, false);
            if (frameCount >= signalTime.get(true))
                enactChangeSignal(key, true);
        }
    }

    public void show() {
        if (sensors != null) {
            if (!pedestrianOnly) {
                for (int key : sensors.keySet()) {
                    for (Sensor sensor : sensors.get(key)) {
                        sensor.show();
                    }
                }
            }

            if (builder.drawRoads)
                drawCrosswalk();

            for (int key : signals.keySet()) {
                if (groups.get(key).getFirst().trafficType.isBidirectional()) {
                    // Draw the first set of stoplines/traffic lights
                    drawStopline(key);
                    drawTrafficLight(key);

                    drawStopline(key, sensorReverseStartIndices.get(key));
                    drawTrafficLight(key, sensorReverseStartIndices.get(key), true);
                } else {
                    drawStopline(key);
                    drawTrafficLight(key);
                }
            }
        }
    }

    public boolean canTurnYellow(int index, boolean reverse) {
        if (signals.get(index).get(reverse) != Signal.GREEN)
            return false;

        return frameCount - signalStartTimes.get(index).get(reverse) >= round(Settings.yellowTime * frameRate);
    }

    public boolean wantsGreen(int index, boolean reverse) {
        for (Sensor sensor : sensors.get(index)) {
            if (sensor.reverse == reverse && (sensor.detected || sensor.detectedCount > 0))
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
            // TODO: Is this entire thing not just "go through every i except i = index"?
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

    public boolean hasConflictingGreen(int index, boolean reverse) {
        int increment = reverse ? -1 : 1;
        for (int i = index; reverse ? i >= 0 : i < groups.size(); i += increment) {
            for (Collision collision : collisions.get(i)) {
                if (iVRI.isOrTurningGreen(collision.exitSegment))
                    return true;
            }

            if (!mustWait)
                break;
        }

        return false;
    }

    // TODO: Refactor the getEarliest[Red][Yellow][Green]StartTime to not be so DRY
    public int getEarliestRedStartTime(int index, boolean reverse) {
        // If we are red and not changing to green, then it's red
        Signal signal = signals.get(index).get(reverse);
        // if (signal == Signal.RED && signalsNext.containsKey(index)
        // && signalsNext.get(index).get(reverse) != Signal.GREEN)
        if (signal == Signal.RED) {
            if (!signalsNext.containsKey(index)
                    || (signalsNext.containsKey(index) && signalsNext.get(index).get(reverse) != Signal.GREEN)) {
                return signalStartTimes.get(index).get(reverse);
            }
        }

        int myTime = 0;
        if (signal == Signal.YELLOW) {
            myTime = abs(
                    round(Settings.yellowTime * frameRate) - (frameCount - signalStartTimes.get(index).get(reverse)))
                    + frameCount;

            // It's yellow so of course this is simply the earliest red time
            return myTime;
        } else if (signal == Signal.GREEN) {
            int minTime = round(Settings.greenTimeMinimum * frameRate);
            int ongoingTime = frameCount - signalStartTimes.get(index).get(reverse);
            int yellowTime = round(Settings.yellowTime * frameRate);
            if (ongoingTime >= minTime)
                myTime = yellowTime + frameCount;
            else
                myTime = abs(minTime - ongoingTime) + yellowTime + frameCount;
        } else if (signal == Signal.RED
                && signalsNext.containsKey(index) && signalsNext.get(index).get(reverse) == Signal.GREEN) {
            int timeToGreen = signalNextStartTimes.get(index).get(reverse) - frameCount;
            int minGreenYellowTime = round((Settings.greenTimeMinimum + Settings.yellowTime) * frameRate);
            myTime = timeToGreen + minGreenYellowTime + frameCount;
        }

        if (!mustWait)
            return myTime;
        if ((reverse && index == groups.size() - 1) || (!reverse && index == 0))
            return myTime;

        int increment = reverse ? 1 : -1;
        int earliestTime = getEarliestRedStartTime(index + increment, reverse);

        // TODO: This distance calculation assumes a straight line path between them so
        // curved paths would be wrong
        float dist;
        if (reverse) {
            dist = PVector.dist(sensors.get(index).get(sensorReverseStartIndices.get(index)).pos,
                    sensors.get(index + increment).get(sensorReverseStartIndices.get(index + increment)).pos);
        } else {
            dist = PVector.dist(sensors.get(index).getFirst().pos, sensors.get(index + increment).getFirst().pos);
        }
        int entranceTime = round(dist / groups.get(index).getFirst().trafficType.getMaxSpeed() * frameRate);

        return max(myTime, earliestTime + entranceTime);
    }

    public int getEarliestYellowStartTime(int index, boolean reverse) {
        if (signals.get(index).get(reverse) == Signal.YELLOW)
            return signalStartTimes.get(index).get(reverse);
        if (signals.get(index).get(reverse) == Signal.RED) {
            System.err.println(
                    "Warning! Called movement.getEarliestYellowStartTime() but the signal was already red. This shouldn't ever happen");
            return frameCount;
        }

        int yellowTime = round(Settings.yellowTime * frameRate);
        return getEarliestRedStartTime(index, reverse) - yellowTime;
    }

    public int getEarliestGreenStartTime(int index, boolean reverse) {
        if (signals.get(index).get(reverse) == Signal.GREEN)
            return signalStartTimes.get(index).get(reverse);

        // Figure out just looking at ourself, when we can first turn green
        int myTime = 0;
        int minimumRedTime = round(Settings.redTimeMinimum * frameRate);
        if (signals.get(index).get(reverse) == Signal.YELLOW) {
            int remainingYellowTime = abs(
                    round(Settings.yellowTime * frameRate) - (frameCount - signalStartTimes.get(index).get(reverse)));
            myTime = minimumRedTime + remainingYellowTime + frameCount;
        } else {
            // == max value if it has never been green (aka in the beginning). In this case
            // it's free to turn immediately
            if (signalStartTimes.get(index).get(reverse) == -Integer.MAX_VALUE) {
                myTime = frameCount;
            } else {
                int remainingRedTime = max(0, minimumRedTime - (frameCount - signalStartTimes.get(index).get(reverse)));
                myTime = remainingRedTime + frameCount;
            }
        }

        // But now we have to consider the fact that other movements might be
        // conflicting our ability to turn green, thus making us wait longer
        for (Collision collision : collisions.get(index)) {
            Segment other = collision.exitSegment;
            int otherRedStartTime = iVRI.getEarliestRedStartTime(other, false);

            if (otherRedStartTime == -Integer.MAX_VALUE)
                continue;

            if (other.trafficType.isBidirectional())
                otherRedStartTime = max(otherRedStartTime, iVRI.getEarliestRedStartTime(other, true));

            myTime = max(myTime, otherRedStartTime + round(collision.clearanceTime * frameRate));
        }

        if (!mustWait)
            return myTime;
        if ((reverse && index == 0) || (!reverse && index == groups.size() - 1))
            return myTime;

        int increment = reverse ? -1 : 1;
        int earliestTime = getEarliestGreenStartTime(index + increment, reverse);

        // TODO: This distance calculation assumes a straight line path between them so
        // curved paths would be wrong
        float dist;
        if (reverse) {
            dist = PVector.dist(sensors.get(index).get(sensorReverseStartIndices.get(index)).pos,
                    sensors.get(index + increment).get(sensorReverseStartIndices.get(index + increment)).pos);
        } else {
            dist = PVector.dist(sensors.get(index).getFirst().pos, sensors.get(index + increment).getFirst().pos);
        }
        int entranceTime = round(dist / groups.get(index).getFirst().trafficType.getMaxSpeed() * frameRate);

        return max(myTime, earliestTime - entranceTime);
    }

    public void changeSignal(int index, Signal signal, boolean reverse, int startTime) {
        if (signalNextStartTimes.get(index) == null) {
            signalsNext.put(index, SignalGroup.make(signal, reverse));
            signalNextStartTimes.put(index, SignalTime.make(startTime, reverse));
        } else {
            signalsNext.get(index).set(signal, reverse);
            signalNextStartTimes.get(index).set(startTime, reverse);
        }

        if (signal == Signal.GREEN)
            timeUntilGreenCounterMax = startTime - frameCount;
    }

    private void enactChangeSignal(int index, boolean reverse) {
        // Set our signal and signal start time (and be sure to not overwrite the
        // !reverse signal/time)
        Signal signal = signalsNext.get(index).get(reverse);
        if (signals.get(index) == null) {
            signals.put(index, SignalGroup.make(signal, reverse));
        } else {
            signals.get(index).set(signal, reverse);
        }
        if (signalStartTimes.get(index) == null) {
            signalStartTimes.put(index, SignalTime.make(frameCount, reverse));
        } else {
            signalStartTimes.get(index).set(frameCount, reverse);
        }

        signalsNext.get(index).remove(reverse);
        signalNextStartTimes.get(index).remove(reverse);

        if (signals.get(index).get(reverse) == Signal.YELLOW) {
            // Because this enact signal function was called, we already know
            // signalsNext.get(index) exists so we don't need to check
            signalsNext.get(index).set(Signal.RED, reverse);
            signalNextStartTimes.get(index).set(frameCount + round(Settings.yellowTime * frameRate), reverse);

            // We also reset the sensor detectedCounts
            // TODO: hmm this might not be the best time to reset. Maybe when we actually
            // turn red should we reset it but I'm not quite sure
            for (Sensor sensor : sensors.get(index)) {
                if (sensor.reverse == reverse)
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
                signals.put(i, new SignalGroup(Signal.RED, Signal.RED));
                signalStartTimes.put(i, new SignalTime(-Integer.MAX_VALUE, -Integer.MAX_VALUE));
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
            // Peds/cyclists intersecting each other doesn't matter and should just be
            // ignored
            if (movement.trafficType.isVulnerable() && segment.trafficType.isVulnerable())
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
                    System.err
                            .println("Warning! minimumIndex = 0 in Movement.calculateEntranceNode(). ID, group index: "
                                    + id + ", " + index);
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

            for (Segment movement : groups.get(i)) {
                for (Segment segment : iVRI.segmentMovementsMap.keySet()) {
                    if (movement == segment
                            || (movement.trafficType.isVulnerable() && segment.trafficType.isVulnerable()))
                        continue;

                    Collision collision = null;
                    if (movement.trafficType.isBidirectional()) {
                        for (boolean reverse : new boolean[] { false, true }) {
                            for (boolean reverse2 : new boolean[] { false, true }) {
                                if (!segment.trafficType.isBidirectional() && reverse2)
                                    continue;

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

                        if (collision != null && segment.trafficType.isBidirectional()) {
                            Collision collision2 = getCollision(movement, segment, entranceNodes.get(i), false, true);
                            if (collision2 != null && collision2.clearanceTime > collision.clearanceTime)
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

        Object[] entranceArray = getEntranceDistance(entrance, exit, entranceStart, exitStart, reverseEntrance,
                reverseExit);
        Object[] exitArray = getEntranceDistance(exit, entrance, exitStart, entranceStart, reverseExit,
                reverseEntrance);

        if (entranceArray == null || exitArray == null)
            return null;

        PVector intersection = (PVector) entranceArray[0];
        float entranceDistance = (float) entranceArray[1];
        float exitDistance = (float) exitArray[1];

        return new Collision(intersection, entrance, exit, entranceDistance, exitDistance);
    }

    private Object[] getEntranceDistance(Segment entrance, Segment exit, int entranceStartIndex, int exitStartIndex,
            boolean reverseEntrance, boolean reverseExit) {
        int stepEntrance = reverseEntrance ? -1 : 1;
        int stepExit = reverseExit ? -1 : 1;
        for (int i = entranceStartIndex; reverseEntrance ? i > 0 : i < entrance.path.size() - 1; i += stepEntrance) {
            for (int j = exitStartIndex; reverseExit ? j > 0 : j < exit.path.size() - 1; j += stepExit) {
                if (!isCollision(entrance, exit, i, stepEntrance, j, stepExit))
                    continue;

                // The intersection lies somewhere between i and i + stepEntrance and j and j +
                // stepExit. Let's calculate the distance from path(i) to the intersection
                PVector intersection = entrance.path.get(i);
                float extraDistance = 0;
                intersection = entrance.findIntersection(entrance.path.get(i), entrance.path.get(i + stepEntrance),
                        exit.path.get(j), exit.path.get(j + stepExit));

                extraDistance = entrance.path.get(i).dist(intersection);

                float entranceDistance = 0;
                if (reverseEntrance) {
                    for (int k = entranceStartIndex; k > i; k--) {
                        entranceDistance += PVector.dist(entrance.path.get(k), entrance.path.get(k -
                                1));
                    }
                } else {
                    for (int k = entranceStartIndex; k < i; k++) {
                        entranceDistance += PVector.dist(entrance.path.get(k), entrance.path.get(k +
                                1));
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
                        makeBikeSensors(i, false);
                        makeBikeSensors(i, true);
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
        sensors.get(index).add(createSensor(start, end, 0, segments));

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
            sensors.get(arrayIndex).add(createSensor(start, end, 0, segment));
        }

        // The queue sensor should end approximately 8m before the start of the
        // just-added sensor. It should be about 16m long
        int maxGapNodes = round(8.0f * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);
        int minGapNodes = round(4.0f * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);

        int maxLengthNodes = round(16 * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);
        int minLengthNodes = round(6.0f * Settings.pixelsPerMeter / Settings.distanceToNodeThreshold);

        float distance = 0;
        boolean movedToNextSegment = false;
        if (start < minGapNodes + minLengthNodes) {
            if (segment.segmentsPrevious.size() != 1)
                return;
            segment = segment.segmentsPrevious.iterator().next();
            if (segment.segmentsNext.size() != 1)
                return;

            if (start + minGapNodes + minLengthNodes > segment.path.size())
                return;

            start += segment.path.size();
            // TODO: this distance is for sure correct if groups.get(i).size() == 1, but if
            // it's > 1 I'm not sure this is right because then the end = 1 instead of
            // entranceNode

            // TODO: um this distance isn't fully correct because the spacing between nodes
            // isn't always exactly this distance... every place where I do this needs to be
            // changed then
            distance = Settings.distanceToNodeThreshold * entranceNodes.get(arrayIndex);
            movedToNextSegment = true;
        }

        int nextStart = start;
        start = start - maxGapNodes - maxLengthNodes;
        start = max(start, 0);
        int end = start + maxLengthNodes;
        end = min(end, nextStart - minGapNodes);
        end = min(end, segment.path.size() - 1);
        if (movedToNextSegment)
            distance += Settings.distanceToNodeThreshold * (segment.path.size() - 1 - end);
        else
            // nextStart + 1 is just the end of the kopLus (because it has a length of 1
            // node)
            distance += Settings.distanceToNodeThreshold * (nextStart + 1 - end);
        sensors.get(arrayIndex).add(createSensor(start, end, distance, segment));

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
        sensors.get(arrayIndex).add(createSensor(start, end, distance, segment));
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

                int nextStart = start;
                start = start + maxGapNodes + 1;
                start = min(start, segment.path.size() - 1);
                int end = start - 1;
                distance += Settings.distanceToNodeThreshold * (end - nextStart);
                Sensor sensor = createSensor(start, end, distance, segment);
                sensor.setReverse(true);
                sensors.get(index).add(sensor);
            } else {
                // TODO: do
                throw new UnsupportedOperationException();
            }
        } else {
            boolean movedToNextSegment = false;
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
                    movedToNextSegment = true;
                }

                int nextStart = start;
                start = start - maxGapNodes - 1;
                start = max(start, 0);
                int end = start + 1;
                if (movedToNextSegment)
                    distance += Settings.distanceToNodeThreshold * (segment.path.size() - 1 - end);
                else
                    // nextStart + 1 is just the end of the kopLus (because it has a length of 1
                    // node)
                    distance += Settings.distanceToNodeThreshold * (nextStart + 1 - end);

                sensors.get(index).add(createSensor(start, end, distance, segment));
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
                Sensor sensor = createSensor(start, end, 0, groups.get(index).get(0));
                sensor.setReverse(true);
                sensors.get(index).add(sensor);
                sensorReverseStartIndices.put(index, sensors.get(index).size() - 1);
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
                sensors.get(index).add(createSensor(start, end, 0, groups.get(index).get(0)));
                return start;
            } else {
                int start = 0;
                int end = 1;
                Segment[] segments = groups.get(index).toArray(new Segment[0]);
                sensors.get(index).add(createSensor(start, end, 0, segments));
                return start;
            }
        }
    }

    private Sensor createSensor(int nodeIndexStart, int nodeIndexEnd, float distanceFromEndToIntersection,
            Segment... segments) {
        Sensor sensor = new Sensor(iVRI.sensorID, nodeIndexStart, nodeIndexEnd, distanceFromEndToIntersection,
                segments);
        iVRI.sensorID++;
        return sensor;
    }

    public boolean isCollision(Segment incoming, Segment other, int i, int stepI, int j, int stepJ) {
        PVector incomingStart = incoming.path.get(i);
        PVector incomingEnd = incoming.path.get(i + stepI);
        PVector otherStart = other.path.get(j);
        PVector otherEnd = other.path.get(j + stepJ);

        Line2D incomingLine = new Line2D.Float(incomingStart.x, incomingStart.y, incomingEnd.x,
                incomingEnd.y);
        Line2D otherLine = new Line2D.Float(otherStart.x, otherStart.y, otherEnd.x, otherEnd.y);
        return incomingLine.intersectsLine(otherLine);
    }

    public int getIntersectionIndex(Segment incoming, Segment other, boolean reverse) {
        int start = reverse ? incoming.path.size() - 1 : 0;
        int step = reverse ? -1 : 1;
        for (int i = start; reverse ? i > 0 : i < incoming.path.size() - 1; i += step) {
            for (int j = 0; j < other.path.size() - 1; j++) {
                if (isCollision(incoming, other, i, step, j, 1))
                    return i;
            }
        }

        return reverse ? 0 : Integer.MAX_VALUE;
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
        drawTrafficLight(index, 0, false);
    }

    private void drawTrafficLight(int index, int sensorIndex, boolean reverse) {
        Segment segment = groups.get(index).getFirst();
        float rotation = sensors.get(index).get(sensorIndex).rotation + PI / 2;
        float signalRadius = segment.segmentWidth / 2.8f;
        float effectiveRadius = segment.trafficType == TrafficType.BIKE_2WAY ? signalRadius / 2 : signalRadius;
        float w = effectiveRadius * 0.3f;
        float h = segment.trafficType == TrafficType.PEDESTRIAN ? effectiveRadius * 1.5f : effectiveRadius * 2;

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

        Signal signal = signals.get(index).get(reverse);
        boolean inPracticeReverse = reverse;
        if (segment.trafficType != TrafficType.PEDESTRIAN) {
            if (ovLane) {
                float radius = signalRadius * 2 / 4.5f;
                drawOVTrafficLightSymbol(index, radius, reverse);
                translate(PVector.fromAngle(rotation - PI / 2).setMag(-signalRadius));
            } else {

                drawTrafficLightSymbol(index, new PVector(0, -effectiveRadius * 1.1),
                        color(Settings.lightRed, signal == Signal.RED ? 255 : Settings.lightOffAlpha), effectiveRadius);
                drawTrafficLightSymbol(index, new PVector(0, effectiveRadius * 1.1),
                        color(Settings.lightGreen, signal == Signal.GREEN ? 255 : Settings.lightOffAlpha),
                        effectiveRadius);
                drawTrafficLightSymbol(index, PVector.zero(),
                        color(Settings.lightYellow, signal == Signal.YELLOW ? 255 : Settings.lightOffAlpha),
                        effectiveRadius);
            }
        } else {
            signal = Signal.compareSignalsTowardsTarget(signal, signals.get(index).get(!reverse), Signal.GREEN);

            // Figure out what "reverse" actually is in this case
            inPracticeReverse = signal == signals.get(index).get(reverse) ? reverse : !reverse;
            // If both signals are the same, then we have to be more specific
            if (signals.get(index).get(false) == signals.get(index).get(true)) {
                if (signal == Signal.YELLOW) {
                    // If they are both yellow, then go with the one that will turn red later

                    inPracticeReverse = !(signalNextStartTimes.get(index).get(false) > signalNextStartTimes.get(index)
                            .get(true));
                } else if (signal == Signal.RED) {
                    // If they are both red, then we go with whichever wantsGreen(). This is done so
                    // that the countdown light will turn on for both, even if only one side is
                    // detected
                    // However if they both want green, then give it to whoever will change first

                    boolean wantsGreenReverse = wantsGreen(index, reverse);
                    boolean wantsGreenNotReverse = wantsGreen(index, !reverse);
                    if (wantsGreenReverse && wantsGreenNotReverse) {
                        inPracticeReverse = signalNextStartTimes.get(index).get(false) > signalNextStartTimes
                                .get(index).get(true);
                    } else if (wantsGreenReverse) {
                        inPracticeReverse = reverse;
                    } else if (wantsGreenNotReverse) {
                        inPracticeReverse = !reverse;
                    }
                }
            }

            drawTrafficLightSymbol(index, new PVector(0, -signalRadius * 0.55),
                    color(Settings.lightRed, signal == Signal.RED ? 255 : Settings.lightOffAlpha), signalRadius);
            float alpha = signal == Signal.GREEN ? 255 : Settings.lightOffAlpha;
            if (signal == Signal.YELLOW) {
                int colorPosition = floor((frameCount - signalStartTimes.get(index).get(inPracticeReverse))
                        / (float) Settings.lightPedestrianFlashInterval);
                while (colorPosition > 2) {
                    colorPosition -= 2;
                }
                if (colorPosition < 1) {
                    alpha = map(colorPosition, 0, 1, 255, Settings.lightOffAlpha);
                } else {
                    alpha = map(colorPosition, 1, 2, 20, 255);
                }
            }
            drawTrafficLightSymbol(index, new PVector(0, signalRadius * 0.55),
                    color(Settings.lightGreen, alpha), signalRadius);
        }

        drawTrafficLightCountdown(signal, sensors.get(index).get(sensorIndex), index, inPracticeReverse,
                segment.trafficType, w, effectiveRadius);

        pop();
    }

    private void drawOVTrafficLightSymbol(int index, float radius, boolean reverse) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                fill(255, 5);
                circle(x * radius, y * radius, radius);
            }
        }
        switch (signals.get(index).get(reverse)) {
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

    private void drawTrafficLightCountdown(Signal signal, Sensor kopLus, int index, boolean reverse,
            TrafficType trafficType, float w, float signalRadius) {
        if (!drawTrafficLightCountdown)
            return;

        if (trafficType.isVulnerable()) {
            noStroke();
            rectMode(CORNER);

            // Draw backdrop for lights
            fill(0);
            float height = trafficType.isBike() ? signalRadius * 4 : signalRadius * 3;
            float startY = -height / 2;
            rect(-signalRadius - w, startY, w, height);

            if (signal != Signal.RED)
                return;

            float counter, counterMax;

            if (signalsNext.containsKey(index) && signalsNext.get(index).get(reverse) == Signal.GREEN) {
                counter = signalNextStartTimes.get(index).get(reverse) - frameCount;
                counterMax = timeUntilGreenCounterMax;
            } else {
                if (!wantsGreen(index, reverse))
                    return;
                counter = 1;
                counterMax = counter;
            }

            fill(224, 199, 72);
            int n = 30; // Real life value maybe around 60 or so based on quick estimate
            for (int i = 0; i <= n; i++) {
                float y = map(i, n, 0, startY + w / 2, startY + height - w / 2);
                if ((float) i / n > (float) counter / counterMax)
                    break;
                circle(-signalRadius - w / 2, y, w / 2);
            }
        } else {
            // If it's OV or there's not a car waiting at the stopline, don't show it
            if (trafficType == TrafficType.OV || !kopLus.detected)
                return;
            if (signal != Signal.RED)
                return;
            if (!signalsNext.containsKey(index) || signalsNext.get(index).get(reverse) != Signal.GREEN)
                return;

            int timeUntilGreenCounter = signalNextStartTimes.get(index).get(reverse) - frameCount;
            int seconds = ceil(timeUntilGreenCounter / frameRate);
            if (seconds > 3)
                return;

            textSize(signalRadius * 0.75);
            textAlign(CENTER);
            fill(255, 200);
            text(seconds, PVector.zero());
        }
    }

    private void drawCrosswalk() {
        if (!pedestrianOnly)
            return;

        // TODO: this drawing code assumes a straight path through the intersection (so
        // without any bezier curves)

        // Target width and gap between crossing (it's the same value in real life)
        float target = 0.5f * Settings.pixelsPerMeter;

        for (int i = 0; i < groups.size(); i++) {
            PVector start = groups.get(i).getFirst().path.get(entranceNodes.get(i)).copy();
            PVector end = null;
            float largestDistance = 0;
            float otherWidth = 0;

            for (Collision collision : collisions.get(i)) {
                float dist = collision.point.dist(start);
                // ASSUMPTION MADE: Assuming the farthest distance is the last collision assumes
                // that the path,
                // for example, doesn't loop 180 on itself to end up closer to the start
                if (dist > largestDistance) {
                    largestDistance = dist;
                    end = collision.point.copy();
                    otherWidth = collision.exitSegment.segmentWidth;
                }
            }
            if (end == null)
                continue;
            PVector dir = PVector.sub(end, start).normalize();

            // We now need to shift the start target/2 towards the intersection
            // ASSUMPTION: straight line path when doing these shifts. Note for the start
            // shift that you can't
            // just use the start heading of the segment because this start node might
            // actually be in the middle of the path
            start.add(PVector.mult(dir, target / 2));
            end.add(PVector.mult(dir, otherWidth / 2));

            // ASSUMPTION: distance calculation assumes as the crow flies
            float dist = start.dist(end);
            // Formula: num crosswalks = 1 / (2 * target) * dist + 1
            int numCrosswalks = round(dist / (2 * target)) + 1;
            // Now we update the target so that everything aligns perfectly (by inversing
            // formula)
            target = dist / (float) (2 * numCrosswalks - 2);

            // Draw the crosswalk (ASSUMPTION: straight line very much assumed)
            float heading = dir.heading();
            noStroke();
            fill(220, 150);
            push();
            translate(start);
            rotate(heading);
            for (int count = 0; count < numCrosswalks; count++) {
                rect(0, 0, target, groups.get(i).getFirst().segmentWidth);

                // Distance between rectangles is target * 2
                translate(PVector.mult(dir, target * 2));
            }
            pop();
        }
    }

    public String generateSaveString() {
        String output = "";

        output += id + "\n";
        output += name + "\n";
        output += mustWait + "\n";
        output += vulnerableOnly + "\n";
        output += pedestrianOnly + "\n";
        output += bikeOnly + "\n";
        output += drawTrafficLightCountdown + "\n";

        output += "a";
        output = generateSaveStringAddSegments(output, allMovements);

        output += "d";
        if (delays != null) {
            for (Float delay : delays) {
                output += delay + " ";
            }
            output = output.trim();
        }
        output += "\n";

        for (int i = 0; i < groups.size(); i++) {
            output += "#" + i + "\n";

            output += "g";
            output = generateSaveStringAddSegments(output, groups.get(i));
            for (int k = 0; k < collisions.get(i).size(); k++) {
                output += "c" + collisions.get(i).get(k).generateSaveString() + "\n";
            }

            output += "e";
            if (entranceNodes.containsKey(i)) {
                output += entranceNodes.get(i);
            }
            output += "\n";

            output += "r";
            if (sensorReverseStartIndices.containsKey(i)) {
                output += sensorReverseStartIndices.get(i);
            }
            output += "\n";

            output += "s";
            if (sensors != null) {
                for (Sensor sensor : sensors.get(i)) {
                    output += sensor.id + " ";
                }
                output = output.trim();
            }
            output += "\n";
        }

        return output;
    }

    private String generateSaveStringAddSegments(String output, List<Segment> segments) {
        for (Segment segment : segments) {
            output += segment.id + " ";
        }
        return output.trim() + "\n";
    }

    public static void loadIntersection(Sketch sketch, ArrayList<Sensor> sensorsAll, String pathToData) {
        String[] data = Helper.loadStrings(pathToData);
        if (data.length == 1)
            return;
        int count = 0;

        while (count < data.length) {
            int id = Integer.parseInt(data[count]);
            count++;

            String name = String.valueOf(data[count]);
            count++;

            boolean mustWait = Boolean.parseBoolean(data[count]);
            count++;

            boolean vulnerableOnly = Boolean.parseBoolean(data[count]);
            count++;

            boolean pedestrianOnly = Boolean.parseBoolean(data[count]);
            count++;

            boolean bikeOnly = Boolean.parseBoolean(data[count]);
            count++;

            boolean drawTrafficLightCountdown = Boolean.parseBoolean(data[count]);
            count++;

            ArrayList<Segment> allMovements = getSegments(sketch, data[count]);
            count++;

            ArrayList<Float> delays = getFloats(sketch, data[count]);
            count++;

            ArrayList<ArrayList<Segment>> groups = new ArrayList<>();
            HashMap<Integer, ArrayList<Collision>> collisions = new HashMap<>();
            HashMap<Integer, Integer> entranceNodes = new HashMap<>();
            HashMap<Integer, Integer> sensorReverseStartIndices = new HashMap<>();
            HashMap<Integer, ArrayList<Sensor>> sensors = null;
            int i = 0;
            while (data[count].startsWith("#")) {
                groups.add(new ArrayList<>());
                count++;

                groups.get(i).addAll(getSegments(sketch, data[count]));
                count++;

                while (data[count].startsWith("c")) {
                    if (!collisions.containsKey(i))
                        collisions.put(i, new ArrayList<>());

                    collisions.get(i).add(findCollision(sketch, data[count]));
                    count++;
                }

                if (data[count].length() > 1)
                    entranceNodes.put(i, Integer.parseInt(data[count].substring(1)));
                count++;

                if (data[count].length() > 1)
                    sensorReverseStartIndices.put(i, Integer.parseInt(data[count].substring(1)));
                count++;

                ArrayList<Sensor> sensorsGroup = getSensors(sketch, data[count], sensorsAll);
                if (sensorsGroup.size() != 0) {
                    if (sensors == null) {
                        sensors = new HashMap<>();
                    }
                    sensors.put(i, sensorsGroup);
                }

                count++;
                i++;

                if (count >= data.length)
                    break;
            }

            Movement movement = new Movement(id, name, sketch.iVRI, sketch);
            movement.mustWait = mustWait;
            movement.vulnerableOnly = vulnerableOnly;
            movement.pedestrianOnly = pedestrianOnly;
            movement.bikeOnly = bikeOnly;
            movement.drawTrafficLightCountdown = drawTrafficLightCountdown;
            movement.allMovements = allMovements;
            movement.delays = delays;
            movement.groups = groups;
            movement.collisions = collisions;
            movement.entranceNodes = entranceNodes;
            movement.sensorReverseStartIndices = sensorReverseStartIndices;
            movement.sensors = sensors;

            sketch.iVRI.movements.add(movement);

            // Increase count to skip empty line
            count++;
        }
    }

    private static Collision findCollision(Sketch sketch, String lineString) {
        String[] line = lineString.substring(1).split(" ");
        int count = 0;

        PVector point = PVector.parseString(line[count] + line[count + 1]);
        count += 2;

        Segment entranceSegment = findSegment(sketch, Integer.parseInt(line[count]));
        count++;

        Segment exitSegment = findSegment(sketch, Integer.parseInt(line[count]));
        count++;

        float entranceDistance = Float.parseFloat(line[count]);
        count++;

        float exitDistance = Float.parseFloat(line[count]);
        count++;

        return new Collision(point, entranceSegment, exitSegment, entranceDistance, exitDistance);
    }

    private static Segment findSegment(Sketch sketch, int id) {
        for (Segment segment : sketch.builder.segments) {
            if (segment.id == id) {
                return segment;
            }
        }
        System.err.println("Couldn't find segment in Movement.findSegment for id " + id);
        return null;
    }

    private static Sensor findSensor(Sketch sketch, int id, ArrayList<Sensor> sensorsAll) {
        for (Sensor sensor : sensorsAll) {
            if (sensor.id == id) {
                return sensor;
            }
        }
        System.err.println("Couldn't find sensor in Movement.findSensor for id " + id);
        return null;
    }

    private static ArrayList<Segment> getSegments(Sketch sketch, String lineString) {
        String[] line = lineString.substring(1).split(" ");
        ArrayList<Segment> segments = new ArrayList<>();
        for (String str : line) {
            if (str.length() == 0)
                continue;

            int segmentID = Integer.parseInt(str);
            segments.add(findSegment(sketch, segmentID));
        }

        return segments;
    }

    private static ArrayList<Float> getFloats(Sketch sketch, String lineString) {
        String[] line = lineString.substring(1).split(" ");
        ArrayList<Float> floats = new ArrayList<>();
        for (String str : line) {
            if (str.length() == 0)
                continue;

            floats.add(Float.parseFloat(str));
        }

        if (floats.isEmpty())
            return null;
        return floats;
    }

    private static ArrayList<Sensor> getSensors(Sketch sketch, String lineString, ArrayList<Sensor> sensorsAll) {
        String[] line = lineString.substring(1).split(" ");
        ArrayList<Sensor> sensors = new ArrayList<>();
        for (String str : line) {
            if (str.length() == 0)
                continue;

            int segmentID = Integer.parseInt(str);
            sensors.add(findSensor(sketch, segmentID, sensorsAll));
        }

        return sensors;
    }
}
