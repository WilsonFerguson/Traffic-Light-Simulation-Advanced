import library.core.*;
import java.util.*;

class RoadUser extends PComponent {
    TrafficManager trafficManager;
    PVector pos;
    float heading;
    float speed;

    TrafficType type;
    boolean ovExclusive;

    Segment currentSegment;
    ArrayList<Segment> potentialNextSegments;
    int targetSegmentIndex;
    /**
     * Used for 2 way bikes and pedestrians to have them go in the reverse
     * direction.
     */
    boolean segmentReverse = false;
    /**
     * Offset lanes used for peds/bikes because you can have multiple people abreast
     */
    int laneOffset = 0;

    float length;
    float width;

    float acceleration;
    float maxSpeed;
    float reactionTime;
    float dt;

    public RoadUser(TrafficManager trafficManager, Segment openingSegment) {
        this(trafficManager, openingSegment, false);
    }

    public RoadUser(TrafficManager trafficManager, Segment openingSegment, boolean isOV) {
        this.trafficManager = trafficManager;

        currentSegment = openingSegment;
        if (openingSegment.trafficType == TrafficType.BIKE_2WAY
                || openingSegment.trafficType == TrafficType.PEDESTRIAN) {
            if (!currentSegment.openingSegment) {
                segmentReverse = true;
            } else if (currentSegment.openingSegment && currentSegment.closingSegment) {
                segmentReverse = random(1) < 0.5;
            }
        }

        pos = openingSegment.getStartNode().copy();
        heading = openingSegment.startHeading;
        if (segmentReverse) {
            pos = openingSegment.getEndNode().copy();
            heading = PVector.sub(openingSegment.getNode(openingSegment.path.size() - 2), openingSegment.getEndNode())
                    .heading();
        }

        type = openingSegment.trafficType;
        if (isOV || type == TrafficType.OV)
            type = TrafficType.OV;

        determinePotentialNextSegments();
        targetSegmentIndex = segmentReverse ? currentSegment.path.size() - 2 : 1;

        setType(type);

        if (type.isVulnerable()) {
            if (type == TrafficType.PEDESTRIAN)
                laneOffset = random(new int[] { -1, 1 });
            else
                laneOffset = random(new int[] { -1, 0, 1 });

            if (segmentReverse)
                pos = getOffsetNode(currentSegment.path.size() - 1);
            else
                pos = getOffsetNode(0);
        }
        speed = maxSpeed;
        dt = 1 / frameRate;
    }

    public boolean update() {
        move();

        boolean done = advanceSegment();

        return done;
    }

    public void show() {
        fill(255, 150);
        if (type == TrafficType.OV)
            fill(Settings.buttonAccentDefault);
        noStroke();
        rectMode(CENTER);

        push();
        translate(pos);
        rotate(heading);
        rect(0, 0, length, width);
        pop();
    }

    private void move() {
        // Target speed will be based on how far off the heading we are
        PVector targetNode = getOffsetNode(targetSegmentIndex);
        float targetHeading = PVector.sub(targetNode, pos).heading();
        float targetHeadingDeviation = abs(targetHeading - heading);
        float targetSpeed = map(targetHeadingDeviation, 0, PI / 2.5, maxSpeed, 0);

        // Accelerate as if there is no reason to stop
        int accelerationDirection = targetSpeed > speed ? 1 : -1;
        float appliedAcceleration = acceleration * accelerationDirection;

        appliedAcceleration = min(appliedAcceleration, stopForCarsInFront());
        float a = yieldRightOfWay(appliedAcceleration);
        if (a == Float.MIN_VALUE)
            appliedAcceleration = 0;
        else
            appliedAcceleration = min(appliedAcceleration, a);

        // TODO: consider other segments that just cross ours
        // AND: consider the width of the segment for determining where to stop

        speed += appliedAcceleration * dt;
        if (speed > maxSpeed)
            speed = maxSpeed;
        else if (speed < 0)
            speed = 0;

        heading = lerp(heading, targetHeading, Settings.turningRate);
        pos.add(PVector.fromAngle(heading).setMag(speed * dt));
    }

    private PVector getOffsetNode(int index) {
        PVector node = currentSegment.getNode(index).copy();
        if (!type.isVulnerable())
            return node;

        float trueHeading = 0;
        if (!segmentReverse) {
            if (index < currentSegment.path.size() - 1) {
                trueHeading = PVector.sub(currentSegment.getNode(index + 1), currentSegment.getNode(index)).heading();
            } else {
                trueHeading = PVector.sub(currentSegment.getNode(index), currentSegment.getNode(index - 1)).heading();
            }
        } else {
            if (index > 0) {
                trueHeading = PVector.sub(currentSegment.getNode(index - 1), currentSegment.getNode(index)).heading();
            } else {
                trueHeading = PVector.sub(currentSegment.getNode(index), currentSegment.getNode(index + 1)).heading();
            }
        }

        if (type == TrafficType.BIKE_2WAY || type == TrafficType.PEDESTRIAN) {
            node.add(PVector.fromAngle(trueHeading + PI / 2).setMag(currentSegment.segmentWidth / 4));
        }

        trueHeading += (PI / 2) * laneOffset;
        float divisor = 4;
        if (type == TrafficType.BIKE_2WAY)
            divisor = 8;
        else if (type == TrafficType.PEDESTRIAN)
            divisor = 6;
        node.add(PVector.fromAngle(trueHeading).setMag(currentSegment.segmentWidth / divisor));

        return node;
    }

    private float stopForCarsInFront() {
        // Stop for vehicles in front of us (maintaining x second following distance)
        // TODO: optimize this
        float closestVehicle = Float.MAX_VALUE;
        for (RoadUser roadUser : trafficManager.traffic) {
            if (roadUser == this)
                continue;

            if (roadUser.segmentReverse != segmentReverse)
                continue;

            if (roadUser.type == type && type.isVulnerable() && roadUser.laneOffset != laneOffset)
                continue;

            if (roadUser.currentSegment == currentSegment) {
                if (segmentReverse && targetSegmentIndex < roadUser.targetSegmentIndex)
                    continue;
                if (!segmentReverse && targetSegmentIndex > roadUser.targetSegmentIndex)
                    continue;

                if (targetSegmentIndex == roadUser.targetSegmentIndex) {
                    if (pos.dist(currentSegment.getNode(targetSegmentIndex)) < roadUser.pos
                            .dist(currentSegment.getNode(targetSegmentIndex)))
                        continue;
                }

                // TODO: this is a loose approximation for distance that doesn't consider the
                // actual shape of the road or heading of vehicles
                float distance = PVector.dist(roadUser.pos, pos) - length / 2 - roadUser.length / 2;
                if (distance < closestVehicle) {
                    closestVehicle = distance;
                }
                continue;
            }

            for (Segment segment : potentialNextSegments) {
                if (segment == roadUser.currentSegment) {
                    float distance = PVector.dist(roadUser.pos, pos) - length / 2 - roadUser.length / 2;
                    if (distance < closestVehicle) {
                        closestVehicle = distance;
                    }
                    break;
                }
            }
        }
        // x second following distance
        if (closestVehicle < speed * 2 || closestVehicle < length / 3) {
            // float targetDistance = closestVehicle - closestVehicleRoadUser.speed * 2;
            float targetDistance = closestVehicle - length / 3;
            float a = pow(speed, 2) / (2 * targetDistance);
            // appliedAcceleration = min(appliedAcceleration, -abs(a));
            return -abs(a);
        }
        return Float.MAX_VALUE;
    }

    private float yieldRightOfWay(float appliedAcceleration) {
        HashSet<Segment> segmentsToSearch = null;
        Anchor anchor = segmentReverse ? currentSegment.startAnchor : currentSegment.endAnchor;

        if (!segmentReverse) {
            segmentsToSearch = new HashSet<>(currentSegment.endAnchor.endSegments);
            segmentsToSearch.addAll(currentSegment.endAnchor.beginSegments);
        }
        if (segmentReverse) {
            segmentsToSearch = new HashSet<>(currentSegment.startAnchor.endSegments);
            segmentsToSearch.addAll(currentSegment.startAnchor.beginSegments);
        }
        for (Segment segment : segmentsToSearch) {
            if (segment.trafficType == TrafficType.MEDIAN || segment.priority <= currentSegment.priority)
                continue;

            // Roughly calculate how long it will take for the farthest car in that segment
            // to reach the anchor
            // TODO: this is a crude approximation as before that doesn't consider geometry
            // of the path (so both distance and speed that it would travel along it though
            // speed is okay to not consider if we just assume max speed)

            // Find the car closest to the anchor
            float closestDistance = Float.MAX_VALUE;
            RoadUser closestCar = null;
            float closestDistanceExiting = Float.MAX_VALUE;
            RoadUser closestCarExiting = null;
            for (RoadUser roadUser : trafficManager.traffic) {
                if (roadUser.currentSegment != segment)
                    continue;

                // Check where this road user has already exited the intersection
                boolean exiting = false;
                if (!roadUser.segmentReverse) {
                    exiting = anchor.beginSegments.contains(roadUser.currentSegment);
                } else {
                    exiting = anchor.endSegments.contains(roadUser.currentSegment);
                }

                float distance = PVector.dist(roadUser.pos, segment.getEndNode()) - roadUser.length / 2;
                if (distance > closestDistance && !exiting)
                    continue;
                if (distance > closestDistanceExiting && exiting)
                    continue;

                if (exiting) {
                    closestDistanceExiting = distance;
                    closestCarExiting = roadUser;
                } else {
                    closestDistance = distance;
                    closestCar = roadUser;
                }
            }

            float timeMargin = 0.2f;
            if (closestCar == null && closestDistanceExiting < Float.MAX_VALUE) {
                float timeFromIntersection = closestDistanceExiting / closestCarExiting.maxSpeed;
                if (timeFromIntersection < timeMargin && appliedAcceleration > 0) {
                    return Float.MIN_VALUE;
                }
            }
            if (closestCar != null) {
                float theirTimeToIntersection = closestDistance / closestCar.maxSpeed - timeMargin;
                float theirTimeToClearIntersection = theirTimeToIntersection + closestCar.length / closestCar.maxSpeed
                        + timeMargin;
                PVector targetNode = segment.getEndNode();
                float myTimeToIntersection = (PVector.dist(pos, targetNode) - length / 2) / speed;
                float myTimeToClearIntersection = myTimeToIntersection + length / speed;
                float distanceToIntersection = PVector.dist(pos, targetNode) - length * 3 / 2;

                if (theirTimeToIntersection < myTimeToClearIntersection
                        && (myTimeToIntersection < theirTimeToClearIntersection)) {
                    // Need to set acceleration s.t. we reach the last node (well a bit before it)
                    // when they reach the intersection
                    float a = 2 * ((distanceToIntersection - length / 2) - speed * theirTimeToIntersection)
                            / pow(theirTimeToIntersection, 2);
                    // float a = pow(speed, 2) / (2 * (distanceToIntersection - length));

                    return -abs(a);
                }

                if (theirTimeToIntersection < timeMargin && appliedAcceleration > 0)
                    return Float.MIN_VALUE;
            }
        }

        return Float.MAX_VALUE;
    }

    private boolean advanceSegment() {
        float dist = pos.dist(currentSegment.getNode(targetSegmentIndex));
        if (dist > Settings.distanceToNodeThreshold)
            return false;

        if (segmentReverse) {
            targetSegmentIndex--;
            if (targetSegmentIndex < 0) {
                return chooseNextSegment();
            }
        } else {
            targetSegmentIndex++;
            if (targetSegmentIndex >= currentSegment.path.size()) {
                targetSegmentIndex = 1;
                return chooseNextSegment();
            }
        }

        return false;
    }

    private boolean chooseNextSegment() {
        if (potentialNextSegments.size() == 0) {
            if (!segmentReverse && currentSegment.closingSegment)
                return true;
            if (segmentReverse && currentSegment.openingSegment)
                return true;
            throw new RuntimeException(
                    "Cannot continue because there is no new segment and my current segment is not a closing segment. Position: "
                            + pos.toString() + ", traffic type " + type + ", reverse segment " + segmentReverse);
        }

        int index = floor(random(0, potentialNextSegments.size()));
        currentSegment = potentialNextSegments.get(index);

        if (segmentReverse)
            targetSegmentIndex = currentSegment.path.size() - 2;

        determinePotentialNextSegments();
        return false;
    }

    private void determinePotentialNextSegments() {
        potentialNextSegments = findCandidates();

        refineCandidatesOV(potentialNextSegments);
        refineCandidatesBikes(potentialNextSegments);
        refineCandidatesPedestrians(potentialNextSegments);
    }

    private ArrayList<Segment> findCandidates() {
        HashSet<Segment> options = currentSegment.segmentsNextOptions;
        if (segmentReverse) {
            // options = currentSegment.segmentsPrevious;
            for (Segment segment : currentSegment.segmentsPrevious) {
                if (segment.segmentsNextOptions.contains(currentSegment))
                    options.add(segment);
            }
        }
        ArrayList<Segment> candidates = new ArrayList<Segment>();

        // Initial candidates
        for (Segment segment : options) {
            if (segment.trafficType == TrafficType.MEDIAN)
                continue;

            switch (type) {
                case OV:
                    // For now we just add both car and ov
                    if (segment.trafficType == TrafficType.CAR || segment.trafficType == TrafficType.OV) {
                        candidates.add(segment);
                    }
                    break;
                case CAR:
                    if (segment.trafficType == TrafficType.CAR) {
                        candidates.add(segment);
                    }
                    break;
                case BIKE_1WAY:
                    // For bikes we for now add everything so that bikes are allowed to merge in
                    // with peds/cars/etc.
                    // We need to make sure we aren't going to go the wrong way down this bike lane
                    if (!segmentReverse) {
                        if (currentSegment.endAnchor.endSegments.contains(segment))
                            continue;
                    } else {
                        if (currentSegment.startAnchor.beginSegments.contains(segment))
                            continue;
                    }
                    candidates.add(segment);
                    break;
                case BIKE_2WAY:
                    candidates.add(segment);
                    break;
                case PEDESTRIAN:
                    // Same as bike
                    candidates.add(segment);
                    break;
                default:
                    throw new RuntimeException("findCandidates() was called for an unhandled TrafficType");
            }
        }

        return candidates;
    }

    private void refineCandidatesOV(ArrayList<Segment> candidates) {
        if (!(type == TrafficType.OV && ovExclusive))
            return;

        // We will remove all car segments IF there are no ov segments
        boolean hasOV = false;
        for (Segment segment : candidates) {
            if (segment.trafficType == TrafficType.OV) {
                hasOV = true;
                break;
            }
        }
        if (!hasOV)
            return;

        for (int i = candidates.size() - 1; i >= 0; i--) {
            Segment segment = candidates.get(i);
            if (segment.trafficType != TrafficType.OV) {
                candidates.remove(segment);
            }
        }
    }

    private void refineCandidatesBikes(ArrayList<Segment> candidates) {
        if (!(type == TrafficType.BIKE_1WAY || type == TrafficType.BIKE_2WAY))
            return;

        // If there are any segments that are bikes, that we remove all non-bike
        // segments
        boolean hasBike = false;
        for (Segment segment : candidates) {
            if (segment.trafficType == TrafficType.BIKE_1WAY || segment.trafficType == TrafficType.BIKE_2WAY) {
                hasBike = true;
                break;
            }
        }

        if (hasBike) {
            for (int i = candidates.size() - 1; i >= 0; i--) {
                Segment segment = candidates.get(i);
                if (segment.trafficType != TrafficType.BIKE_1WAY && segment.trafficType != TrafficType.BIKE_2WAY) {
                    candidates.remove(segment);
                }
            }
            return;
        }

        // There were no bikes, now we do the same for pedestrians
        boolean hasPedestrian = false;
        for (Segment segment : candidates) {
            if (segment.trafficType == TrafficType.PEDESTRIAN) {
                hasPedestrian = true;
                break;
            }
        }
        if (hasPedestrian) {
            for (int i = candidates.size() - 1; i >= 0; i--) {
                Segment segment = candidates.get(i);
                if (segment.trafficType != TrafficType.PEDESTRIAN) {
                    candidates.remove(segment);
                }
            }
            return;
        }

        // Only cars/ov left, so we just go with that
    }

    private void refineCandidatesPedestrians(ArrayList<Segment> candidates) {
        if (type != TrafficType.PEDESTRIAN)
            return;

        // If there are any segments that are pedestrians, that we remove all
        // non-pedestrians
        boolean hasPedestrian = false;
        for (Segment segment : candidates) {
            if (segment.trafficType == TrafficType.PEDESTRIAN) {
                hasPedestrian = true;
                break;
            }
        }
        if (hasPedestrian) {
            for (int i = candidates.size() - 1; i >= 0; i--) {
                Segment segment = candidates.get(i);
                if (segment.trafficType != TrafficType.PEDESTRIAN) {
                    candidates.remove(segment);
                }
            }
            return;
        }

        // Now we elect for bike paths
        boolean hasBike = false;
        for (Segment segment : candidates) {
            if (segment.trafficType == TrafficType.BIKE_1WAY || segment.trafficType == TrafficType.BIKE_2WAY) {
                hasBike = true;
                break;
            }
        }
        if (hasBike) {
            for (int i = candidates.size() - 1; i >= 0; i--) {
                Segment segment = candidates.get(i);
                if (segment.trafficType != TrafficType.BIKE_1WAY && segment.trafficType != TrafficType.BIKE_2WAY) {
                    candidates.remove(segment);
                }
            }

            return;
        }

        // Only cars/ov left, so we just go with that
    }

    public void setType(TrafficType type) {
        this.type = type;

        length = type.getLength();
        width = type.getWidth();

        length = type.getLength();
        width = type.getWidth();

        acceleration = type.getAcceleration()
                * random(1 + (-Settings.variationAcceleration / 2), 1 + Settings.variationAcceleration);
        maxSpeed = type.getMaxSpeed() * random(1 + (-Settings.variationMaxSpeed / 3), 1 + Settings.variationMaxSpeed);
        reactionTime = Settings.reactionTime
                * random(1 + (-Settings.variationReactionTime / 2), 1 + Settings.variationReactionTime);
    }
}
