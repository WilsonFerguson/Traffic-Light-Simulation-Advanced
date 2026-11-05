import library.core.*;
import GameEngine.*;
import java.util.*;

class iVRI extends PComponent {
    Sketch sketch;
    Builder builder;
    ArrayList<Movement> movements;
    HashMap<Segment, SegmentEntry> segmentMovementsMap = new HashMap<>();
    ArrayList<Phase> phases;

    EditingMode mode = EditingMode.MOVEMENTS;

    float buttonWidth;
    float margin;
    float padding;
    float marginPanel = 10;
    Panel panelModeSwitch;
    ArrayList<Button> buttonsMode = new ArrayList<>();
    ArrayList<Panel> panelsMovementNames = new ArrayList<>();
    ArrayList<Panel> panelsMovementSegments = new ArrayList<>();
    Button buttonAddMovement;

    Movement editingMovement;
    int editingMovementGroupIndex;

    private enum EditingMode {
        MOVEMENTS, PHASES
    }

    private class SegmentEntry {
        Movement movement;
        HashSet<Integer> groups;

        public SegmentEntry(Movement movement) {
            this.movement = movement;
            groups = new HashSet<>();
        }

        public void addGroup(int group) {
            groups.add(group);
        }
    }

    private class Weights implements Comparable<Weights> {
        Option<Integer> trafficType;
        Option<Integer> waitTime;
        Option<Integer> detectedCount;
        Option<Integer> queueSensor;

        public Weights(Option<Integer> trafficType, Option<Integer> waitTime, Option<Integer> detectedCount,
                Option<Integer> queueSensor) {
            this.trafficType = trafficType;
            this.waitTime = waitTime;
            this.detectedCount = detectedCount;
            this.queueSensor = queueSensor;
        }

        @Override
        public int compareTo(Weights other) {
            int mine = 0;
            int theirs = 0;
            if (detectedCount.isSome() && other.detectedCount.isSome()) {
                mine += detectedCount.unwrap() * Settings.weightsDetectedCount;
                theirs += other.detectedCount.unwrap() * Settings.weightsDetectedCount;
            }

            if (waitTime.isSome() && other.waitTime.isSome()) {
                mine += floor((float) waitTime.unwrap() / (float) frameRate) * Settings.weightsWaitTime;
                theirs += floor((float) other.waitTime.unwrap() / (float) frameRate) * Settings.weightsWaitTime;
            }

            if (queueSensor.isSome() && other.queueSensor.isSome()) {
                mine += queueSensor.unwrap() * Settings.weightsQueueSensor;
                theirs += other.queueSensor.unwrap() * Settings.weightsQueueSensor;
            }

            mine *= trafficType.unwrap();
            theirs *= other.trafficType.unwrap();

            // negative if we come first
            return -(mine - theirs);
        }
    }

    public iVRI(Sketch sketch) {
        this.sketch = sketch;
        builder = sketch.builder;
        movements = new ArrayList<>();
        phases = new ArrayList<>();

        createPanelModeSwitch();
        createPanelsMovements();
    }

    public void update() {
        for (Movement movement : movements) {
            movement.update();
        }

        if (sketch.mode == SimulationMode.SIMULATE && sketch.running) {
            controlTrafficLight();
        }

        if (sketch.mode != SimulationMode.LIGHT)
            return;

        if (mode == EditingMode.MOVEMENTS) {
            if (editingMovement != null) {
                if (editingMovementGroupIndex < editingMovement.groups.size()) {
                    for (Segment segment : editingMovement.groups.get(editingMovementGroupIndex)) {
                        segment.highlight();
                    }
                }
            }
        }
    }

    private void controlTrafficLight() {
        // TODO: phases: make phases control certain Movement groups (not just
        // movements)

        while (true) {
            HashMap<SegmentEntry, Weights> possibleGreens = new HashMap<>();
            for (Movement movement : movements) {
                for (int i = 0; i < movement.groups.size(); i++) {
                    if (movement.signals.get(i) == Signal.YELLOW)
                        continue;

                    if (movement.signals.get(i) == Signal.RED) {
                        if (!movement.wantsGreen(i))
                            continue;
                        if (movement.hasConflictingGreen(i))
                            continue;
                        if (movement.signalsNext.get(i) == Signal.GREEN)
                            continue;

                        SegmentEntry entry = new SegmentEntry(movement);
                        entry.addGroup(i);

                        Option<Integer> trafficType = new Option<>(movement.groups.get(i).getFirst().priority);
                        Option<Integer> waitTime = new Option<>(frameCount - movement.signalStartTimes.get(i));

                        Option<Integer> detectedCount = new Option<>();
                        if (movement.sensors.get(i).size() >= 2) {
                            Sensor last = movement.sensors.get(i).getLast();
                            // == 1 to confirm it's not a queue sensor
                            if (abs(last.nodeIndexStart - last.nodeIndexEnd) == 1) {
                                detectedCount.insert(last.detectedCount);
                            }
                        }

                        Option<Integer> queueSensor = new Option<>();
                        if (movement.sensors.get(i).size() >= 2) {
                            Sensor second = movement.sensors.get(i).get(1);
                            // >= 2 to confirm it's a queue sensor
                            if (abs(second.nodeIndexStart - second.nodeIndexEnd) >= 2) {
                                queueSensor.insert(1);
                            }
                        }

                        possibleGreens.put(entry, new Weights(trafficType, waitTime, detectedCount, queueSensor));
                        continue;
                    }

                    // Turn yellow if we should and can
                    automaticTurnYellow(movement, i);
                }
            }
            if (possibleGreens.size() == 0)
                break;

            // TODO: turn le green
            // Sort array by weights
            ArrayList<SegmentEntry> sortedGreenKeys = new ArrayList<>(possibleGreens.keySet());
            sortedGreenKeys.sort(Comparator.comparing(possibleGreens::get));
            // You also have to consider the rest of the groups and turn them accordingly
            // green
            // If we turn anything green, set sensors detectedCount to 0
        }

    }

    private void automaticTurnYellow(Movement movement, int index) {
        if (!movement.canTurnYellow(index) || movement.signalsNext.containsKey(index))
            return;

        // If a sensor has too recently been detected, can't turn yellow
        for (Sensor sensor : movement.sensors.get(index)) {
            if (sensor.detected)
                return;

            int timeSinceDetection = frameCount - sensor.lastDetectedFrame;
            float threshold = max(Settings.noDetectionTime, sensor.distanceFromEndToIntersection
                    / movement.groups.get(index).getFirst().trafficType.getMaxSpeed() * frameRate);
            if (timeSinceDetection < threshold)
                return;
        }

        // If we have to wait on other groups, then don't change yellow
        if (movement.mustWait && movement.groups.size() > 1) {
            // TODO: do this but it's annoying because it sorta depends on direction
        }

        movement.changeSignal(index, Signal.YELLOW, frameCount);
    }

    public boolean isGreen(Segment segment) {
        Movement movement = segmentMovementsMap.get(segment).movement;
        for (int group : segmentMovementsMap.get(segment).groups) {
            if (movement.signals.get(group) == Signal.GREEN)
                return true;
        }
        return false;
    }

    public boolean isOrTurningGreen(Segment segment) {
        Movement movement = segmentMovementsMap.get(segment).movement;
        for (int group : segmentMovementsMap.get(segment).groups) {
            if (movement.signals.get(group) == Signal.GREEN || movement.signalsNext.get(group) == Signal.GREEN)
                return true;
        }
        return false;
    }

    public int getEarliestRedStartTime(Segment segment, boolean reverse) {
        int time = frameCount;
        Movement movement = segmentMovementsMap.get(segment).movement;
        for (int group : segmentMovementsMap.get(segment).groups) {
            time = max(time, movement.getEarliestRedStartTime(group, reverse));
        }

        return time;
    }

    public void setActiveUI(boolean active) {
        panelModeSwitch.setActive(active);
        for (UIElement element : panelModeSwitch.getElements()) {
            element.setActive(active);
        }
        for (Button button : buttonsMode) {
            button.setActive(active);
        }
        for (Panel panel : panelsMovementNames) {
            panel.setActive(active);
            for (UIElement element : panel.getElements()) {
                element.setActive(active);
            }
        }
        for (Panel panel : panelsMovementSegments) {
            panel.setActive(active);
            for (UIElement element : panel.getElements()) {
                element.setActive(active);
            }
        }
        buttonAddMovement.setActive(active);
    }

    public void show() {
        for (Movement movement : movements) {
            movement.show();
        }
        panelModeSwitch.draw();
        for (Panel panel : panelsMovementNames) {
            panel.draw();
        }
        for (Panel panel : panelsMovementSegments) {
            panel.draw();
        }
        buttonAddMovement.draw();
    }

    public void calculateMovementDetails() {
        for (Movement movement : movements) {
            movement.calculateDetailsStart();
        }
        for (Movement movement : movements) {
            movement.calculateDetails();
        }

        segmentMovementsMap = new HashMap<>();
        for (Movement movement : movements) {
            for (int i = 0; i < movement.groups.size(); i++) {
                for (Segment segment : movement.groups.get(i)) {
                    if (!segmentMovementsMap.containsKey(segment))
                        segmentMovementsMap.put(segment, new SegmentEntry(movement));
                    segmentMovementsMap.get(segment).addGroup(i);
                }
            }
        }
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

    private float calculateButtonX(float i, float w, float margin) {
        int numButtons = TrafficType.values().length;

        return map(i, 0, numButtons - 1, -sketch.modePanel.size.x / 2 + w / 2 + margin,
                sketch.modePanel.size.x / 2 - w / 2 - margin);
    }

    private void createPanelModeSwitch() {
        panelModeSwitch = new Panel(width / 8, height / 8, width / 4, height / 4);
        panelModeSwitch.setCornerRadius(0);

        buttonWidth = panelModeSwitch.size.x / ((TrafficType.values().length + 1) * 1.2f);
        margin = buttonWidth * 0.2f;
        padding = (buttonWidth / 2 + margin) * 2;

        createModeButtons(buttonWidth, margin);

        // Set size
        float[] widths = new float[] { buttonWidth * 3, buttonWidth * 2 };

        float bottomY = panelModeSwitch.getElements().getLast().pos.y;
        float topY = panelModeSwitch.getElements().getFirst().pos.y;
        float sizeX = widths[0] + margin * 4 + widths[1];
        float sizeY = bottomY - topY + padding;
        panelModeSwitch.setSize(sizeX, sizeY);
        panelModeSwitch.setPos(sizeX / 2 + marginPanel + sketch.modePanel.pos.x +
                sketch.modePanel.size.x / 2,
                sizeY / 2 + marginPanel);

        float x = margin + widths[0] / 2.0f + panelModeSwitch.pos.x - panelModeSwitch.size.x / 2;
        for (UIElement element : panelModeSwitch.getElements()) {
            element.pos.x = x;
            x += (widths[0] / 2 + widths[1] / 2) + margin * 2;
            element.pos.y = panelModeSwitch.pos.y;
        }

        panelModeSwitch.setActive(true);
    }

    private void createModeButtons(float w, float margin) {
        panelModeSwitch.incrementElementHeight(margin + w / 2);
        buttonsMode = new ArrayList<>(EditingMode.values().length);

        float x = margin + w;
        float[] widths = new float[] { w * 3, w * 2 };
        for (int i = 0; i < EditingMode.values().length; i++) {
            Button button = new Button(x, 0, widths[i], w,
                    EditingMode.values()[i].toString().substring(0, 1)
                            + EditingMode.values()[i].toString().substring(1).toLowerCase());
            x += widths[i] / 2 + widths[(i + 1) % widths.length] / 2 + margin * 2;

            setButtonColor(button, mode == EditingMode.values()[i]);

            final int index = i;
            button.onClick(new Runnable() {
                @Override
                public void run() {
                    mode = EditingMode.values()[index];
                    for (Button b : buttonsMode) {
                        setButtonColor(b, false);
                    }
                    setButtonColor(button, true);

                    if (mode == EditingMode.PHASES)
                        calculateMovementDetails();
                }
            });

            buttonsMode.add(button);
            panelModeSwitch.addElementFromTop(button, false);
        }
    }

    private void createPanelsMovements() {
        for (Movement movement : movements) {
            createPanelMovement(movement);
        }

        buttonAddMovement = new Button(buttonWidth / 2 + marginPanel,
                sketch.modePanel.pos.y + sketch.modePanel.size.y / 2 + marginPanel + buttonWidth / 2, buttonWidth,
                buttonWidth, "+");

        buttonAddMovement.onClick(new Runnable() {
            @Override
            public void run() {
                Movement movement = new Movement("Mov. " + str(movements.size() + 1), iVRI.this, sketch);
                movements.add(movement);
                createPanelMovement(movement);
            }
        });
    }

    private void createPanelMovement(Movement movement) {
        float baseY = (panelsMovementNames.size() > 0) ? panelsMovementNames.getLast().pos.y : sketch.modePanel.pos.y;
        float y = baseY + sketch.modePanel.size.y / 2 + marginPanel + margin + buttonWidth / 2;
        // We do margin * 6 because we are trying to make a panel of 4 1x1 buttons wide
        // which totals 3 gaps in between, each gap is margin * 2
        float w = buttonWidth * 4 + margin * 2 + margin * 6;

        Panel nameAndWait = new Panel(w / 2 + marginPanel, y, w, buttonWidth + margin * 2).setCornerRadius(0);
        InputField name = new InputField(calculateButtonX(1.5f, buttonWidth, margin), 0, buttonWidth * 2 + margin * 2,
                buttonWidth).setText(movement.name).setTextAlignment(TextAlignment.CENTER);
        name.onEnter(new Runnable() {
            @Override
            public void run() {
                movement.name = name.getText();
            }
        });
        Button forceWait = new Button(calculateButtonX(3.5f, buttonWidth, margin), 0, buttonWidth * 2 + margin * 2,
                buttonWidth, "Force Wait");
        forceWait.setTextSize(16);
        setButtonColor(forceWait, movement.mustWait);
        forceWait.onClick(new Runnable() {
            @Override
            public void run() {
                movement.mustWait = !movement.mustWait;
                setButtonColor(forceWait, movement.mustWait);
            }
        });

        nameAndWait.addElement(name);
        nameAndWait.addElement(forceWait);
        panelsMovementNames.add(nameAndWait);

        int numButtons = movement.groups.size() + 1;
        w = buttonWidth * numButtons + margin * (numButtons - 1) + margin * 2;
        Panel movementsPanel = new Panel(nameAndWait.pos.x + nameAndWait.size.x / 2 + marginPanel + w / 2, y, w,
                buttonWidth + margin * 2).setCornerRadius(0);
        float x = margin + buttonWidth / 2;
        for (int i = 0; i < movement.groups.size(); i++) {
            createMovementButton(movementsPanel, movement, i, new PVector(x, y).sub(movementsPanel.pos));
            x += buttonWidth + margin;
        }
        Button newMovement = new Button(w - margin * 2 - buttonWidth, 0, buttonWidth, buttonWidth, "+");
        newMovement.onClick(new Runnable() {
            @Override
            public void run() {
                movement.groups.add(new ArrayList<>());
                createMovementButton(movementsPanel, movement, movement.groups.size() - 1,
                        newMovement.pos.copy().sub(movementsPanel.pos));
                newMovement.pos.x += buttonWidth + margin;
                movementsPanel.size.x += buttonWidth + margin;
                movementsPanel.pos = new PVector(movementsPanel.pos.x + (buttonWidth + margin) / 2,
                        movementsPanel.pos.y);
            }
        });
        movementsPanel.addElement(newMovement);

        panelsMovementSegments.add(movementsPanel);

        buttonAddMovement.pos.y += nameAndWait.size.y + marginPanel;
    }

    private void createMovementButton(Panel panel, Movement movement, int index, PVector pos) {
        Button button = new Button(pos.x, pos.y, buttonWidth, buttonWidth, str(index + 1));
        button.onClick(new Runnable() {
            @Override
            public void run() {
                editingMovement = movement;
                editingMovementGroupIndex = index;

                for (UIElement element : panel.getElements()) {
                    if (element instanceof Button) {
                        setButtonColor((Button) element, false);
                    }
                }
                setButtonColor(button, true);
            }
        });
        panel.addElement(button);
    }

    public void mouseClicked() {
        if (sketch.mode == SimulationMode.LIGHT && mode == EditingMode.MOVEMENTS && mouseButton == LEFT) {
            if (editingMovement != null) {
                Segment hovered = builder.getHoveredSegment();
                if (hovered == null)
                    return;
                ArrayList<Segment> group = editingMovement.groups.get(editingMovementGroupIndex);
                if (group.contains(hovered))
                    group.remove(hovered);
                else
                    group.add(hovered);
            }
        }
    }
}
