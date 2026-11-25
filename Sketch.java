import library.core.*;
import GameEngine.*;
import java.util.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

class Sketch extends Applet {

    Builder builder;
    TrafficManager trafficManager;
    iVRI iVRI;

    SimulationMode mode = SimulationMode.BUILD;
    boolean running = false;

    Panel modePanel;
    ArrayList<Button> buttonsMode;

    PVector translation = PVector.zero();
    float scale = 0;

    public void setup() {
        size(1200, 1200);
        exitOnEscape(false);

        Panel.setDefaults(new PVector(width / 5, height / 5), color(62, 94, 171, 150), color(0, 150));
        InputField.setDefaults(
                new InputField(new PVector(width / 2, height / 2), new PVector(width / 8, width / 24),
                        Settings.buttonDefault, Settings.buttonHover, Settings.buttonActive,
                        color(0, 150), "", 20, color(0, 150)).setCornerRadius(0));
        Button.setDefaults(
                new Button(new PVector(width / 2, height / 2), new PVector(width / 8, width / 24),
                        Settings.buttonDefault, Settings.buttonHover, Settings.buttonActive,
                        color(0, 150), "yeet", 20, color(0, 150)).setCornerRadius(0));
        Text.setDefaults(20, color(0, 150));
        ColorPicker.setDefaults(new PVector(width / 8, width / 8), color(0, 150));

        createPanel();

        builder = new Builder(this);
        trafficManager = new TrafficManager(builder);
        iVRI = new iVRI(this);
    }

    public void draw() {
        GameEngine.Run();
        background(27, 135, 11);

        push();
        if (mode == SimulationMode.SIMULATE) {
            if ((scale == 0 && !mousePressed) && (translation.x != 0 || translation.y != 0)) {
                translation.mult(0.85);

                // Round the values towards 0
                if (translation.x > 0)
                    translation.x = floor(translation.x);
                else
                    translation.x = ceil(translation.x);
                if (translation.y > 0)
                    translation.y = floor(translation.y);
                else
                    translation.y = ceil(translation.y);
            }

            float zoom = 1 + scale;
            PVector center = PVector.center();

            translate(PVector.add(
                    translation,
                    PVector.mult(center, -scale / zoom)));
            scale(scale);
        }

        builder.update();
        builder.show();

        if (running && mode == SimulationMode.SIMULATE) {
            trafficManager.update(iVRI);
        }
        trafficManager.show();

        iVRI.setActiveUI(mode == SimulationMode.LIGHT);
        iVRI.update();
        iVRI.show();

        pop();
        modePanel.draw();
    }

    private void createPanel() {
        modePanel = new Panel(width / 8, height / 8, width / 4, height / 4);
        modePanel.setCornerRadius(0);

        float w = modePanel.size.x / ((TrafficType.values().length + 1) * 1.2f);
        float margin = w * 0.2f;
        int numButtonsRow = 6;

        createModeButtons(w, margin, numButtonsRow);

        // Set size
        float bottomY = modePanel.getElements().getLast().pos.y;
        float topY = modePanel.getElements().getFirst().pos.y;
        float padding = (w / 2 + margin) * 2;
        float sizeY = bottomY - topY + padding;
        modePanel.setSize(modePanel.size.x, sizeY);
        float marginPanel = 10;
        modePanel.pos = new PVector(modePanel.size.x / 2 + marginPanel, sizeY / 2 + marginPanel);
        for (UIElement element : modePanel.getElements()) {
            element.pos.add(marginPanel, marginPanel);
        }

        modePanel.setActive(true);
    }

    private float calculateButtonX(float i, float w, float margin) {
        int numButtons = TrafficType.values().length;

        return map(i, 0, numButtons - 1, -modePanel.size.x / 2 + w / 2 + margin,
                modePanel.size.x / 2 - w / 2 - margin);
    }

    private void setButtonColor(Button button, boolean active) {
        if (active) {
            button.setDefaultColor(Settings.buttonAccentDefault);
            button.setHoverColor(Settings.buttonAccentHover);
            button.setActiveColor(Settings.buttonAccentActive);
        } else {
            button.setDefaultColor(Settings.buttonDefault);
            button.setHoverColor(Settings.buttonHover);
            button.setActiveColor(Settings.buttonActive);
        }
    }

    private void createModeButtons(float w, float margin, int numButtonsRow) {
        modePanel.incrementElementHeight(margin + w / 2);
        buttonsMode = new ArrayList<>(SimulationMode.values().length);

        for (int i = 0; i < SimulationMode.values().length; i++) {
            Button button = new Button(calculateButtonX(i * 2 + 0.5f, w, margin), 0, w * 2 + margin * 2, w,
                    SimulationMode.values()[i].toString().substring(0, 1)
                            + SimulationMode.values()[i].toString().substring(1).toLowerCase());

            setButtonColor(button, mode == SimulationMode.values()[i]);

            final int index = i;
            button.onClick(new Runnable() {
                @Override
                public void run() {
                    mode = SimulationMode.values()[index];
                    if (mode == SimulationMode.SIMULATE) {
                        builder.currentSegment = null;
                        builder.currentAnchor = null;
                        addSegmentsToTrafficManager();

                        // If the Phases button was never clicked in iVRI but there are groups, then
                        // call the initial iVRI setup method
                        if (iVRI.movements.size() != 0 && iVRI.segmentMovementsMap.isEmpty()) {
                            println("Automatically \"pressing\" the Phases button");
                            iVRI.calculateMovementDetails();
                        }
                    }
                    for (Button b : buttonsMode) {
                        setButtonColor(b, false);
                    }
                    setButtonColor(button, true);
                }
            });

            buttonsMode.add(button);
            modePanel.addElementFromTop(button, false);
        }
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
        if (key == ' ' && !keysPressed.contains("Ctrl") && mode == SimulationMode.SIMULATE) {
            running = !running;
            println("Now running:", running);
            if (running && trafficManager.segments == null) {
                addSegmentsToTrafficManager();
            }
        } else if (key == 'f') {
            addSegmentsToTrafficManager();
        } else if (keyString.equals("S") && keysPressed.contains("Ctrl")) {
            saveIntersection();
        } else if (keyString.equals("O") && keysPressed.contains("Ctrl")) {
            loadIntersection();
        }
    }

    private void saveIntersection() {
        saveIntersectionSaveable(builder.anchors, "anchors");
        saveIntersectionSaveable(builder.segments, "segments");

        HashSet<Sensor> sensors = new HashSet<>();
        for (Movement movement : iVRI.movements) {
            for (int i = 0; i < movement.groups.size(); i++) {
                sensors.addAll(movement.sensors.get(i));
            }
        }
        saveIntersectionSaveable(sensors, "sensors");

        saveIntersectionSaveable(iVRI.movements, "movements");

        println("Saved!");
    }

    private void saveIntersectionSaveable(Collection<? extends Saveable> objects, String name) {
        String text = "";
        for (Saveable object : objects) {
            text += object.generateSaveString();
            text += "\n";
        }

        saveStrings(text.split("\n"), "intersection_data/" + name + ".txt");
    }

    private void loadIntersection() {
        // First pass loading in segments
        Segment.loadIntersectionFirstPass(this, "intersection_data/segments.txt");

        // Full loading of anchors (possible now that segments are made)
        Anchor.loadIntersection(this, "intersection_data/anchors.txt");

        // Full loading of sensors (possible now that segments are made)
        ArrayList<Sensor> sensors = Sensor.loadIntersection(this, "intersection_data/sensors.txt");

        // Full loading of movements (possible now that segments are made)
        Movement.loadIntersection(this, sensors, "intersection_data/movements.txt");

        // Second pass loading in segments
        Segment.loadIntersectionSecondPass(this, "intersection_data/segments.txt");

        setupIVRI();
    }

    private void setupIVRI() {
        iVRI.calculateMovementDetails();

        iVRI.panelModeSwitch.delete();
        iVRI.panelModeSwitch = null;
        for (Button button : iVRI.buttonsMode) {
            button.delete();
        }
        iVRI.buttonsMode.clear();
        for (Panel panel : iVRI.panelsMovementNames) {
            panel.delete();
        }
        iVRI.panelsMovementNames.clear();
        for (Panel panel : iVRI.panelsMovementSegments) {
            panel.delete();
        }
        iVRI.panelsMovementSegments.clear();
        iVRI.buttonAddMovement.delete();
        iVRI.buttonAddMovement = null;

        iVRI.createPanelModeSwitch();
        iVRI.createPanelsMovements();
    }

    public void mouseDragged() {
        if (mouseButton == 2 && mode == SimulationMode.SIMULATE) {
            float zoom = 1 + scale;
            translation.add(PVector.sub(mouse, pmouse).div(zoom));
        }
    }

    public void mouseScrolled(int amount) {
        if (mode == SimulationMode.SIMULATE) {
            float oldZoom = 1 + scale;

            scale += amount > 0 ? -0.1f : 0.1f;

            scale = max(scale, 0);

            float newZoom = 1 + scale;

            PVector center = PVector.center();
            PVector mouseOffset = PVector.sub(center, mouse);

            translation.add(
                    PVector.mult(mouseOffset, 1.0f / oldZoom - 1.0f / newZoom));
        }
    }
}
