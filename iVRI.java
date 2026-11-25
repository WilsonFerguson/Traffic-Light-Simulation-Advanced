import library.core.*;
import GameEngine.*;
import java.util.*;
import java.awt.geom.Line2D;

class iVRI extends PComponent {
    Sketch sketch;
    Builder builder;
    ArrayList<Movement> movements;
    HashMap<Segment, SegmentEntry> segmentMovementsMap = new HashMap<>();
    ArrayList<Phase> phases;

    int sensorID = 0;

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
    int movementIDCounter = 0;

    Movement editingMovement;
    int editingMovementGroupIndex;

    private enum EditingMode {
        MOVEMENTS, PHASES
    }

    private class SegmentEntry {
        Movement movement;
        HashSet<Integer> groups;
        boolean reverse;

        public SegmentEntry(Movement movement, boolean reverse) {
            this.movement = movement;
            groups = new HashSet<>();
            this.reverse = reverse;
        }

        public void addGroup(int group) {
            groups.add(group);
        }
    }

    private class Weights implements Comparable<Weights> {
        Option<Integer> trafficType;
        Option<Integer> waitTime;
        Option<Integer> timeUntilGreen;
        Option<Integer> detectedCount;
        Option<Integer> queueSensor;

        public Weights(Option<Integer> trafficType, Option<Integer> waitTime, Option<Integer> timeUntilGreen,
                Option<Integer> detectedCount,
                Option<Integer> queueSensor) {
            this.trafficType = trafficType;
            this.waitTime = waitTime;
            this.timeUntilGreen = timeUntilGreen;
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
                mine += floor((float) waitTime.unwrap() / (float) frameRate * Settings.weightsWaitTime);
                theirs += floor((float) other.waitTime.unwrap() / (float) frameRate * Settings.weightsWaitTime);
            }

            if (timeUntilGreen.isSome() && other.timeUntilGreen.isSome()) {
                // We subtract because having a positive timeUntilGreen means just waiting
                // around which is bad for efficiency
                mine -= floor((float) timeUntilGreen.unwrap() / (float) frameRate * Settings.weightsTimeUntilGreen);
                theirs -= floor(
                        (float) other.timeUntilGreen.unwrap() / (float) frameRate * Settings.weightsTimeUntilGreen);
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
            // The following does not consider phases, it just ranks the best movements to
            // turn green in order based on various weights and then goes down that list and
            // turns them green until there's a collision
            HashMap<SegmentEntry, Weights> possibleGreens = new HashMap<>();
            for (Movement movement : movements) {
                for (int i = 0; i < movement.groups.size(); i++) {
                    controlTrafficLightIgnorePhases(movement, i, false, possibleGreens);
                    if (movement.groups.get(i).getFirst().trafficType.isBidirectional())
                        controlTrafficLightIgnorePhases(movement, i, true, possibleGreens);
                }
            }
            if (possibleGreens.size() == 0)
                break;

            // Sort array by weights
            ArrayList<SegmentEntry> sortedGreenKeys = new ArrayList<>(possibleGreens.keySet());
            sortedGreenKeys.sort(Comparator.comparing(possibleGreens::get));
            // You also have to consider the rest of the groups and turn them accordingly
            // green
            // If we turn anything green, set sensors detectedCount to 0

            while (sortedGreenKeys.size() > 0) {
                SegmentEntry entry = sortedGreenKeys.removeFirst();
                Movement movement = entry.movement;
                int index = entry.groups.iterator().next();

                // Check to make sure any of the groups that would turn green won't collide with
                // any other segments that are green or turning green
                if (movement.hasConflictingGreen(index, entry.reverse))
                    continue;

                // Find the related groups to this entry. Note that necessarily
                // entry.groups.size() = 1 and is just the first group (but if mustWait = true,
                // well we must consider other groups)
                HashSet<Integer> groups = new HashSet<>();
                groups.add(index);
                if (movement.mustWait) {
                    int increment = entry.reverse ? -1 : 1;
                    for (int i = index + increment; entry.reverse ? i >= 0
                            : i < movement.groups.size(); i += increment) {
                        groups.add(i);
                    }
                }

                // Turn the groups green
                // TODO: This is a bit slow because we call getEarliestGreenStartTime for every
                // group, but in reality every time just uses the result from the next group so
                // we technically only should have to run it once while storing the current
                // result each time
                for (int i : groups) {
                    movement.changeSignal(i, Signal.GREEN, entry.reverse,
                            movement.getEarliestGreenStartTime(i, entry.reverse));
                    for (Sensor sensor : movement.sensors.get(i)) {
                        sensor.detectedCount = 0;
                    }
                }
            }
        }

    }

    private void controlTrafficLightIgnorePhases(Movement movement, int i, boolean reverse,
            HashMap<SegmentEntry, Weights> possibleGreens) {
        if (movement.signals.get(i).get(reverse) == Signal.YELLOW)
            return;
        if (movement.signals.get(i).get(reverse) == Signal.GREEN) {
            // Turn yellow if we should and can
            automaticTurnYellow(movement, i, reverse);
            return;
        }

        // So it's red
        if (movement.signalsNext.containsKey(i) && movement.signalsNext.get(i).get(reverse) == Signal.GREEN)
            return;
        if (!movement.wantsGreen(i, reverse))
            return;
        if (movement.hasConflictingGreen(i))
            return;

        SegmentEntry entry = new SegmentEntry(movement, reverse);
        entry.addGroup(i);

        Option<Integer> trafficType = new Option<>(movement.groups.get(i).getFirst().priority);
        Option<Integer> waitTime = new Option<>(frameCount - movement.signalStartTimes.get(i).get(reverse));
        Option<Integer> timeUntilGreen = new Option<>(movement.getEarliestGreenStartTime(i, reverse) - frameCount);

        Option<Integer> detectedCount = new Option<>();
        if (movement.sensors.get(i).size() >= 2
                && movement.groups.get(i).getFirst().trafficType != TrafficType.PEDESTRIAN) {
            Sensor last = movement.sensors.get(i).getLast();
            // We can't just use the last index if the traffic type is bidirection and we
            // are looking at the !reverse case
            if (movement.groups.get(i).getFirst().trafficType.isBidirectional() && !reverse) {
                // So we instead get the index of the first reverse sensor and subtract 1 to get
                // the last normal sensor
                last = movement.sensors.get(i).get(movement.sensorReverseStartIndices.get(i) - 1);
            }

            // == 1 to confirm it's not a queue sensor (well for most cases)
            if (abs(last.nodeIndexStart - last.nodeIndexEnd) == 1) {
                detectedCount.insert(last.detectedCount);
            }
        }

        Option<Integer> queueSensor = new Option<>();
        if (movement.sensors.get(i).size() >= 2 && !movement.groups.get(i).getFirst().trafficType.isVulnerable()) {
            Sensor second = movement.sensors.get(i).get(1);
            // >= 2 to confirm it's a queue sensor
            if (abs(second.nodeIndexStart - second.nodeIndexEnd) >= 2) {
                queueSensor.insert(1);
            }
        }

        possibleGreens.put(entry, new Weights(trafficType, waitTime, timeUntilGreen, detectedCount, queueSensor));
        return;
    }

    private void automaticTurnYellow(Movement movement, int index, boolean reverse) {
        if (!movement.canTurnYellow(index, reverse))
            return;
        if (movement.signalsNext.containsKey(index) && movement.signalsNext.get(index).get(reverse) != null)
            return;

        // If a sensor has too recently been detected, can't turn yellow
        for (Sensor sensor : movement.sensors.get(index)) {
            if (sensor.reverse != reverse)
                continue;
            if (sensor.detected)
                return;

            int timeSinceDetection = frameCount - sensor.lastDetectedFrame;
            float threshold = max(Settings.noDetectionTime, sensor.distanceFromEndToIntersection
                    / movement.groups.get(index).getFirst().trafficType.getMaxSpeed() * frameRate);

            if (timeSinceDetection < threshold) {
                return;
            }
        }

        // If we have to wait on other groups, then don't change yellow
        if (movement.mustWait && movement.groups.size() > 1) {
            int earliestYellowStartTime = movement.getEarliestYellowStartTime(index, reverse);
            if (earliestYellowStartTime > frameCount)
                return;
        }

        movement.changeSignal(index, Signal.YELLOW, reverse, frameCount);
    }

    public boolean isGreen(Segment segment) {
        if (!segmentMovementsMap.containsKey(segment))
            return false;

        Movement movement = segmentMovementsMap.get(segment).movement;
        for (int group : segmentMovementsMap.get(segment).groups) {
            if (movement.signals.get(group).getNormal() == Signal.GREEN
                    || movement.signals.get(group).getReverse() == Signal.GREEN)
                return true;
        }
        return false;
    }

    public boolean isOrTurningGreen(Segment segment) {
        if (!segmentMovementsMap.containsKey(segment))
            return false;

        Movement movement = segmentMovementsMap.get(segment).movement;
        for (int group : segmentMovementsMap.get(segment).groups) {
            SignalGroup signalGroup = movement.signals.get(group);
            if (signalGroup.getNormal() == Signal.GREEN || signalGroup.getReverse() == Signal.GREEN)
                return true;

            if (!movement.signalsNext.containsKey(group))
                continue;

            signalGroup = movement.signalsNext.get(group);
            if (signalGroup.getNormal() == Signal.GREEN || signalGroup.getReverse() == Signal.GREEN)
                return true;
        }
        return false;
    }

    public int getEarliestRedStartTime(Segment segment, boolean reverse) {
        int time = -Integer.MAX_VALUE;
        Movement movement = segmentMovementsMap.get(segment).movement;
        for (int group : segmentMovementsMap.get(segment).groups) {
            time = max(time, movement.getEarliestRedStartTime(group, reverse));
        }

        return time;
    }

    public Movement getMovement(Segment segment) {
        if (!segmentMovementsMap.containsKey(segment))
            return null;

        return segmentMovementsMap.get(segment).movement;
    }

    public HashSet<Integer> getGroups(Segment segment) {
        if (!segmentMovementsMap.containsKey(segment))
            return null;

        return segmentMovementsMap.get(segment).groups;
    }

    public void setActiveUI(boolean active) {
        if (panelModeSwitch == null)
            return;

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

        if (panelModeSwitch != null) {
            panelModeSwitch.draw();
            for (Panel panel : panelsMovementNames) {
                panel.draw();
            }
            for (Panel panel : panelsMovementSegments) {
                panel.draw();
            }
            buttonAddMovement.draw();
        }
    }

    public void automaticallyAssignMovements() {
        // If segment not already in iVRI, then consider it
        HashSet<Segment> addedSegments = new HashSet<>();
        for (Segment segment : builder.segments) {
            if (addedSegments.contains(segment) || !hasCollision(segment, true)
                    || segment.trafficType == TrafficType.MEDIAN)
                continue;

            // Make a new movement and group
            makeNewMovement();
            editingMovement = movements.getLast();

            // Add this segment to the first group
            editingMovement.groups.get(0).add(segment);
            addedSegments.add(segment);

            // Look at the start anchor, make a list of all other segments that share that
            // start anchor and have a collision with something else hasCollision(, true).
            // Add all of these to the first group and don't bother with 2 stages
            HashSet<Segment> segments = new HashSet<>();
            for (Segment s : segment.startAnchor.beginSegments) {
                if (s == segment)
                    continue;

                if (hasCollision(s, true))
                    segments.add(s);
            }
            if (!segments.isEmpty()) {
                editingMovement.groups.get(0).addAll(segments);
                addedSegments.addAll(segments);
                continue;
            }

            // Multiple stages
            Segment current = segment;
            while (current.segmentsNext.size() == 1) {
                current = current.segmentsNext.iterator().next();
                if (!hasCollision(current, true))
                    break;

                Panel panel = panelsMovementSegments.get(editingMovement.id);
                Button button = createNewGroup(editingMovement, panel, (Button) panel.getElements().getFirst());
                button.onClick();

                editingMovement.groups.getLast().add(current);
                addedSegments.add(current);
            }
            // Diagnosis report after multiple stages for a scenario that couldn't be
            // resolved automatically
            if (current.segmentsNext.size() == 2) {
                boolean allNextHaveCollision = true;
                for (Segment s : current.segmentsNext) {
                    if (!hasCollision(s, true)) {
                        allNextHaveCollision = false;
                        break;
                    }
                }
                if (allNextHaveCollision) {
                    println("Warning! iVRI.automaticalyAssignMovements() couldn't make movement to the fullest degree (because segmentsNext.size() ==",
                            current.segmentsNext.size(), "> 1) for segment of id", current.id);
                }
            }
        }

        editingMovement = null;
    }

    private boolean hasCollision(Segment segment, boolean ignoreSegmentsOfSamePrevious) {
        for (Segment other : builder.segments) {
            if (segment == other)
                continue;
            if (segment.trafficType.isVulnerable() && other.trafficType.isVulnerable())
                continue;

            // TODO: technically there could be times (for really loopy segments) that we
            // wouldn't want to eliminate these segmentsNext/segmentsPrevious stuff, but
            // this is actually important to prevent some false positives
            if (segment.segmentsNext.contains(other) || segment.segmentsPrevious.contains(other))
                continue;

            if (ignoreSegmentsOfSamePrevious) {
                boolean stop = false;
                for (Segment s : segment.segmentsPrevious) {
                    if (other.segmentsPrevious.contains(s)) {
                        stop = true;
                        break;
                    }
                }
                if (stop) {
                    continue;
                }
            }

            if (hasCollision(segment, other))
                return true;
        }

        return false;
    }

    public boolean hasCollision(Segment segment, Segment other) {
        for (int i = 0; i < segment.path.size() - 1; i++) {
            for (int j = 0; j < other.path.size() - 1; j++) {
                PVector segmentStart = segment.path.get(i);
                PVector segmentEnd = segment.path.get(i + 1);
                PVector otherStart = other.path.get(j);
                PVector otherEnd = other.path.get(j + 1);

                Line2D segmentLine = new Line2D.Float(segmentStart.x, segmentStart.y, segmentEnd.x, segmentEnd.y);
                Line2D otherLine = new Line2D.Float(otherStart.x, otherStart.y, otherEnd.x, otherEnd.y);
                if (segmentLine.intersectsLine(otherLine))
                    return true;
            }
        }

        return false;
    }

    public void calculateMovementDetails() {
        for (Movement movement : movements) {
            movement.calculateDetailsStart();
        }

        segmentMovementsMap = new HashMap<>();
        for (Movement movement : movements) {
            for (int i = 0; i < movement.groups.size(); i++) {
                for (Segment segment : movement.groups.get(i)) {
                    if (!segmentMovementsMap.containsKey(segment)) {
                        // We let reverse = false, I don't think it's ever used here so it's just
                        // arbitrary
                        segmentMovementsMap.put(segment, new SegmentEntry(movement, false));
                    }
                    segmentMovementsMap.get(segment).addGroup(i);

                    segment.setIVRI(this);
                }
            }
        }

        for (Movement movement : movements) {
            movement.calculateDetails();
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

    public void createPanelModeSwitch() {
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

    public void createPanelsMovements() {
        buttonAddMovement = new Button(buttonWidth / 2 + marginPanel,
                sketch.modePanel.pos.y + sketch.modePanel.size.y / 2 + marginPanel + buttonWidth / 2, buttonWidth,
                buttonWidth, "+");

        buttonAddMovement.onClick(new Runnable() {
            @Override
            public void run() {
                makeNewMovement();
            }
        });

        for (Movement movement : movements) {
            createPanelMovement(movement);
        }
    }

    private void makeNewMovement() {
        Movement movement = new Movement(movementIDCounter, "Mov. " + str(movements.size() + 1), iVRI.this, sketch);
        movementIDCounter++;
        movements.add(movement);
        createPanelMovement(movement);
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
        float x = -movementsPanel.size.x / 2 + buttonWidth / 2 + margin;
        for (int i = 0; i < movement.groups.size(); i++) {
            createMovementButton(movementsPanel, movement, i, new PVector(x, y).sub(0, movementsPanel.pos.y));
            x += buttonWidth + margin;
        }

        Button newMovement = new Button(x, 0, buttonWidth, buttonWidth, "+");
        newMovement.onClick(new Runnable() {
            @Override
            public void run() {
                createNewGroup(movement, movementsPanel, newMovement);
            }
        });
        movementsPanel.addElement(newMovement);

        // If groups = 0, then we already make the first one and select it
        if (movement.groups.size() == 0) {
            Button newGroup = createNewGroup(movement, movementsPanel, newMovement);
            newGroup.onClick();
        }

        panelsMovementSegments.add(movementsPanel);

        buttonAddMovement.pos.y += nameAndWait.size.y + marginPanel;
    }

    private Button createNewGroup(Movement movement, Panel movementsPanel, Button newMovement) {
        movement.groups.add(new ArrayList<>());
        Button button = createMovementButton(movementsPanel, movement, movement.groups.size() - 1,
                newMovement.pos.copy().sub(movementsPanel.pos));
        newMovement.pos.x += buttonWidth + margin;
        movementsPanel.size.x += buttonWidth + margin;
        movementsPanel.pos = new PVector(movementsPanel.pos.x + (buttonWidth + margin) / 2,
                movementsPanel.pos.y);

        return button;
    }

    private Button createMovementButton(Panel panel, Movement movement, int index, PVector pos) {
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

        return button;
    }

    public boolean hoveringPanel() {
        if (panelModeSwitch.hover() || sketch.modePanel.hover())
            return true;

        for (Panel panel : panelsMovementNames) {
            if (panel.hover())
                return true;
        }
        for (Panel panel : panelsMovementSegments) {
            if (panel.hover())
                return true;
        }

        return false;
    }

    public void mousePressed() {
        if (sketch.mode == SimulationMode.LIGHT && mode == EditingMode.MOVEMENTS && mouseButton == LEFT
                && !hoveringPanel()) {
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

    public void keyPressed() {
        if (!(sketch.mode == SimulationMode.LIGHT && mode == EditingMode.MOVEMENTS))
            return;

        // TODO: this is very janky, especially the createNewGroup (and specifically
        // just selected getFirst() which would break I believe if when the movement
        // panel was first made, if it already had a non-zero number of groups)
        if (key == 'a') {
            makeNewMovement();
            editingMovement = movements.getLast();
        } else if (key == 'g') {
            Movement movement = editingMovement;
            if (movement == null)
                movement = movements.getLast();
            if (movement == null)
                return;

            Panel panel = panelsMovementSegments.get(movement.id);
            Button button = createNewGroup(movement, panel, (Button) panel.getElements().getFirst());
            button.onClick();
        } else if (key == 'e') {
            automaticallyAssignMovements();
        }
    }
}
