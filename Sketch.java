import library.core.*;
import GameEngine.*;
import java.util.*;

class Sketch extends Applet {

    Builder builder;
    TrafficManager trafficManager;

    boolean running = false;

    public void setup() {
        size(1000, 1000);

        builder = new Builder(this);
        exitOnEscape(false);

        Panel.setDefaults(new PVector(width / 5, height / 5), color(62, 94, 171, 150), color(0, 150));
        InputField.setDefaults(
                new InputField(new PVector(width / 2, height / 2), new PVector(width / 8, width / 24),
                        Settings.buttonDefault, Settings.buttonHover, Settings.buttonActive,
                        color(0, 150), "", 20, color(255)).setCornerRadius(0));
        Button.setDefaults(
                new Button(new PVector(width / 2, height / 2), new PVector(width / 8, width / 24),
                        Settings.buttonDefault, Settings.buttonHover, Settings.buttonActive,
                        color(0, 150), "yeet", 20, color(255)).setCornerRadius(0));
        Text.setDefaults(20, color(255));
        ColorPicker.setDefaults(new PVector(width / 8, width / 8), color(255));

        trafficManager = new TrafficManager(builder);
    }

    public void draw() {
        GameEngine.Run();
        background(27, 135, 11);

        builder.update();
        builder.show();

        if (running) {
            trafficManager.update();
        }
        trafficManager.show();
    }

    public void addSegmentsToTrafficManager() {
        ArrayList<Segment> segmentsOpening = new ArrayList<>();
        ArrayList<Segment> segmentsClosing = new ArrayList<>();
        for (Segment segment : builder.segments) {
            if (segment.openingSegment)
                segmentsOpening.add(segment);
            if (segment.closingSegment)
                segmentsClosing.add(segment);
        }
        trafficManager.setSegments(builder.segments, segmentsOpening, segmentsClosing);
    }

    public void keyPressed() {
        if (key == ' ') {
            running = !running;
            if (running && trafficManager.segments == null) {
                addSegmentsToTrafficManager();
            }
        } else if (key == 'f') {
            addSegmentsToTrafficManager();
        }
    }

}
