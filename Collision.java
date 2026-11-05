import library.core.*;
import java.util.*;

class Collision {
    PVector point;

    Segment entranceSegment;
    Segment exitSegment;

    float entranceDistance;
    float exitDistance;

    float entranceTime;
    float exitTime;
    float clearanceTime;

    public Collision(PVector point, Segment entranceSegment, Segment exitSegment, float entranceDistance,
            float exitDistance) {
        this.point = point;

        this.entranceSegment = entranceSegment;
        this.exitSegment = exitSegment;

        this.entranceDistance = entranceDistance;
        this.exitDistance = exitDistance;

        entranceTime = entranceDistance / entranceSegment.trafficType.getMaxSpeed();
        exitTime = exitDistance / exitSegment.trafficType.getMaxSpeed();
        clearanceTime = exitTime - entranceTime;
    }
}
