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

    SimulationMode mode = SimulationMode.BUILD;
    boolean running = false;

    Panel modePanel;
    ArrayList<Button> buttonsMode;

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
    }

    public void draw() {
        GameEngine.Run();
        background(27, 135, 11);

        builder.update();
        builder.show();

        if (running && mode == SimulationMode.SIMULATE) {
            trafficManager.update();
        }
        trafficManager.show();

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
        if (key == ' ' && !keysPressed.contains("Ctrl")) {
            running = !running;
            if (running && trafficManager.segments == null) {
                addSegmentsToTrafficManager();
            }
        } else if (key == 'f') {
            addSegmentsToTrafficManager();
        }
    }

}
