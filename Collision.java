import library.core.*;
import java.util.*;

class Collision implements Saveable {
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

    public String generateSaveString() {
        String output = "";

        output += point + " ";
        output += entranceSegment.id + " ";
        output += exitSegment.id + " ";
        output += entranceDistance + " ";
        output += exitDistance + " ";
        output += entranceTime + " ";
        output += exitTime + " ";
        output += clearanceTime;

        return output;
    }
}
