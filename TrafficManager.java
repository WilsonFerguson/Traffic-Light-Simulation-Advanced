import library.core.*;
import GameEngine.*;
import java.util.*;

public class TrafficManager extends PComponent {
    Builder builder;

    ArrayList<Segment> segments;
    HashMap<TrafficType, ArrayList<Segment>> segmentsOpening;
    ArrayList<Segment> segmentsOpeningAll;
    ArrayList<Segment> segmentsClosing;

    ArrayList<RoadUser> traffic;

    public TrafficManager(Builder builder) {
        this.builder = builder;

        this.segments = null;
        segmentsOpening = new HashMap<TrafficType, ArrayList<Segment>>();
        segmentsOpeningAll = new ArrayList<Segment>();
        this.segmentsClosing = null;

        traffic = new ArrayList<RoadUser>();
    }

    public void setSegments(ArrayList<Segment> segments, ArrayList<Segment> segmentsOpening,
            ArrayList<Segment> segmentsClosing) {
        this.segments = segments;

        this.segmentsOpening = new HashMap<TrafficType, ArrayList<Segment>>();
        this.segmentsOpeningAll = new ArrayList<Segment>();
        for (Segment segment : segmentsOpening) {
            addOpeningSegment(segment);
        }

        this.segmentsClosing = segmentsClosing;
        for (Segment segment : segmentsClosing) {
            if (segment.trafficType == TrafficType.BIKE_2WAY || segment.trafficType == TrafficType.PEDESTRIAN) {
                addOpeningSegment(segment);
            }
        }

        for (Segment segment : segments) {
            println("-----");
            println(segment);
            for (Segment seg : segment.segmentsNext)
                println(seg);
        }
    }

    public void addOpeningSegment(Segment segment) {
        if (!segmentsOpening.containsKey(segment.trafficType))
            segmentsOpening.put(segment.trafficType, new ArrayList<Segment>());
        segmentsOpening.get(segment.trafficType).add(segment);

        segmentsOpeningAll.add(segment);
    }

    public RoadUser spawnTraffic() {
        ArrayList<Segment> allowableSegments = new ArrayList<Segment>();
        int earliestIndexAllowed = 2;
        for (Segment segment : segmentsOpeningAll) {
            boolean invalid = false;
            for (RoadUser roadUser : traffic) {
                if (roadUser.currentSegment != segment)
                    continue;

                if (roadUser.targetSegmentIndex <= earliestIndexAllowed) {
                    invalid = true;
                    break;
                }
            }
            if (!invalid)
                allowableSegments.add(segment);
        }

        if (allowableSegments.size() == 0)
            return null;
        Segment segment = allowableSegments.get(floor(random(0, allowableSegments.size())));
        RoadUser roadUser = new RoadUser(this, segment);
        if (random(1) < Settings.OVSpawnChance
                && (segment.trafficType == TrafficType.OV || segment.trafficType == TrafficType.CAR))
            roadUser.setType(TrafficType.OV);
        traffic.add(roadUser);

        return roadUser;
    }

    public RoadUser spawnTraffic(TrafficType type) {
        // TODO: update to also disallow spawning when another vehicle is already at the
        // beginning (and make sure OV people don't get put on a non-OV/car lane)
        Segment segment = segmentsOpening.get(type).get(floor(random(0, segmentsOpening.get(type).size())));
        RoadUser roadUser = new RoadUser(this, segment);
        if (random(1) < Settings.OVSpawnChance)
            roadUser.type = TrafficType.OV;
        traffic.add(roadUser);

        return roadUser;
    }

    /**
     * ovExclusive: if true, only OV vehicles can spawn in OV lanes (and cannot
     * spawn in car lanes)
     */
    public RoadUser spawnTraffic(TrafficType type, boolean ovExclusive) {
        // TODO: update to also disallow spawning when another vehicle is already at the
        // beginning (and make sure OV people don't get put on a non-OV/car lane)
        if ((type == TrafficType.OV && ovExclusive) || type != TrafficType.OV) {
            RoadUser roadUser = spawnTraffic(type);
            roadUser.ovExclusive = ovExclusive;
            return roadUser;
        }

        // What remains is OV vehicles with ovExclusive == false
        // So we need to either pick car lane or ov lane.
        ArrayList<Segment> allowableSegments = new ArrayList<Segment>();
        for (Segment segment : segmentsOpening.get(TrafficType.CAR)) {
            allowableSegments.add(segment);
        }
        for (Segment segment : segmentsOpening.get(TrafficType.OV)) {
            allowableSegments.add(segment);
        }

        Segment segment = allowableSegments.get(floor(random(0, allowableSegments.size())));
        RoadUser roadUser = new RoadUser(this, segment, true);
        roadUser.ovExclusive = ovExclusive;
        traffic.add(roadUser);

        return roadUser;
    }

    public void update() {
        if (random(1) < Settings.trafficSpawnInterval / 60 && traffic.size() < Settings.maxTraffic) {
            spawnTraffic();
        }

        for (int i = traffic.size() - 1; i >= 0; i--) {
            RoadUser roadUser = traffic.get(i);
            if (roadUser.update()) {
                traffic.remove(i);
                delete(roadUser);
            }
        }
    }

    public void show() {
        for (RoadUser roadUser : traffic) {
            roadUser.show();
        }
    }

    public void mouseClicked() {
        if (mouseButton != LEFT)
            return;

        if (builder.currentSegment == null && builder.currentAnchor == null && segments != null) {
            Segment segment = builder.getHoveredSegment();
            if (segment == null)
                return;

            PVector closestNode = null;
            float closestDistance = Float.MAX_VALUE;
            int closestIndex = -1;
            for (int i = 0; i < segment.path.size(); i++) {
                PVector node = segment.path.get(i);
                float distance = PVector.dist(mouse, node);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestNode = node;
                    closestIndex = i;
                }
            }

            RoadUser roadUser = new RoadUser(this, segment);
            roadUser.targetSegmentIndex = closestIndex + 1;
            roadUser.pos = closestNode.copy();
            roadUser.heading = PVector.sub(segment.getNode(closestIndex + 1), segment.getNode(closestIndex - 1))
                    .heading();
            roadUser.speed = 0;

            traffic.add(roadUser);
        }
    }
}
