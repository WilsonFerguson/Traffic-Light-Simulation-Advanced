import library.core.*;
import GameEngine.*;
import java.util.*;

class Segment extends PComponent {
    Builder builder;

    Panel segmentEditorPanel;
    ArrayList<Button> buttonsTrafficTypes;
    ArrayList<Button> buttonsSnappingOptions;
    Button buttonPathColor;
    ColorPicker colorPickerPathColor;
    Text textPathColor;
    Text textSegmentWidth;
    Text textSegmentWidthUnits;
    InputField inputFieldSegmentWidth;
    Text textSegmentPriority;
    InputField inputFieldSegmentPriority;
    Button buttonSegmentPriorityDecrease;
    Button buttonSegmentPriorityIncrease;
    Button buttonParallel;
    Button buttonParallelDecrease;
    InputField inputFieldParallel;
    Button buttonParallelIncrease;
    Button buttonTypeStraight;
    Button buttonTypeBezier;

    Anchor startAnchor;
    Anchor endAnchor;
    SegmentType type;

    HashSet<Segment> segmentsPrevious = new HashSet<>();
    HashSet<Segment> segmentsNext = new HashSet<>();

    /**
     * List of segments that we are snapped to.
     */
    HashSet<Segment> snappedToSegments = new HashSet<>();

    /**
     * List of segments that are snapped to this one.
     */
    HashSet<Segment> controlledSegments = new HashSet<>();
    HashSet<Segment> controlledLeftStart = new HashSet<>();
    HashSet<Segment> controlledRightStart = new HashSet<>();
    HashSet<Segment> controlledLeftEnd = new HashSet<>();
    HashSet<Segment> controlledRightEnd = new HashSet<>();
    HashSet<Segment> controlledFullLeft = new HashSet<>();
    HashSet<Segment> controlledFullRight = new HashSet<>();

    PVector start;
    PVector end;
    float startHeading;
    float endHeading;
    // Control points magnitude
    float startControlPointMag;
    float endControlPointMag;

    ArrayList<PVector> path;

    TrafficType trafficType;
    float segmentWidth;
    color segmentColor;
    int priority = 5;

    boolean openingSegment;
    boolean closingSegment;

    public Segment(Builder builder, PVector pos) {
        this(builder, pos, TrafficType.CAR);
    }

    private Segment(Builder builder, PVector pos, TrafficType trafficType) {
        this.builder = builder;
        type = SegmentType.STRAIGHT;

        start = pos.copy();
        end = start.copy();

        startHeading = -Float.MIN_VALUE;
        endHeading = -Float.MIN_VALUE;

        setTrafficType(trafficType);

        openingSegment = false;
        closingSegment = false;

        createEditorPanel();
    }

    private void createEditorPanel() {
        segmentEditorPanel = new Panel(mouse.copy(), new PVector(width / 4, width / 4));
        segmentEditorPanel.setCornerRadius(0);

        float w = segmentEditorPanel.size.x / ((TrafficType.values().length + 1) * 1.2f);
        float margin = w * 0.2f;

        createButtonsTrafficType(w, margin);
        createButtonsSnappingOptions(w, margin);
        createButtonPathColor(w, margin);
        createInputFieldSegmentWidth(w, margin);
        createButtonsSegmentPriority(w, margin);
        createButtonsParallel(w, margin);
        createButtonsSegmentType(w, margin);

        // Set size
        float bottomY = segmentEditorPanel.getElements().getLast().pos.y;
        float topY = segmentEditorPanel.getElements().getFirst().pos.y;
        float padding = (w / 2 + margin) * 2;
        float previousSizeY = segmentEditorPanel.size.y;
        float sizeY = bottomY - topY + padding;
        segmentEditorPanel.setSize(segmentEditorPanel.size.x, sizeY);
        for (UIElement element : segmentEditorPanel.getElements()) {
            element.pos.y = element.pos.y + (previousSizeY - sizeY) / 2;
        }

        segmentEditorPanel.setActive(false);
    }

    private float calculateButtonX(float i, float w, float margin) {
        int numButtons = TrafficType.values().length;

        return map(i, 0, numButtons - 1, -segmentEditorPanel.size.x / 2 + w / 2 + margin,
                segmentEditorPanel.size.x / 2 - w / 2 - margin);
    }

    private void createButtonsTrafficType(float w, float margin) {
        int numButtons = TrafficType.values().length;
        buttonsTrafficTypes = new ArrayList<Button>(numButtons);

        String[] texts = new String[] { "B", "C", "F1", "F2", "P", "M" };

        segmentEditorPanel.incrementElementHeight(margin + w / 2);

        for (int i = 0; i < numButtons; i++) {
            TrafficType trafficTypeButton = TrafficType.values()[i];
            float x = calculateButtonX(i, w, margin);

            Button button = new Button(x, 0, w, w, texts[i]);

            setButtonColor(button, trafficTypeButton == trafficType);

            button.onClick(new Runnable() {
                @Override
                public void run() {
                    setTrafficType(trafficTypeButton);

                    for (Button b : buttonsTrafficTypes) {
                        setButtonColor(b, false);
                    }

                    setButtonColor(button, true);
                }
            });

            buttonsTrafficTypes.add(button);
            segmentEditorPanel.addElementFromTop(button, false);
        }
    }

    public void createButtonsSnappingOptions(float w, float margin) {
        segmentEditorPanel.incrementElementHeight(w + margin);

        int numButtons = 6;
        buttonsSnappingOptions = new ArrayList<Button>(numButtons);
        String[] texts = new String[] { "L/R", "A", "W", "90", "H", "G" };

        for (int i = 0; i < numButtons; i++) {
            float x = calculateButtonX(i, w, margin);
            Button button = new Button(x, 0, w, w, texts[i]);

            setButtonColor(button, builder.cursor.enabledSnappingOptions[i]);

            final int index = i;
            button.onClick(new Runnable() {
                @Override
                public void run() {
                    builder.cursor.enabledSnappingOptions[index] = !builder.cursor.enabledSnappingOptions[index];
                    setButtonColor(button, builder.cursor.enabledSnappingOptions[index]);
                }
            });

            buttonsSnappingOptions.add(button);
            segmentEditorPanel.addElementFromTop(button, false);
        }
    }

    public void createButtonPathColor(float w, float margin) {
        segmentEditorPanel.incrementElementHeight(w + margin);

        textPathColor = new Text(-segmentEditorPanel.size.x / 2 + margin, 0, "Path color")
                .setTextAlignment(TextAlignment.LEFT);

        buttonPathColor = new Button(segmentEditorPanel.size.x / 2 - margin - w / 2, 0, w, w, "");
        buttonPathColor.setDefaultColor(color(segmentColor, 150));
        buttonPathColor.setHoverColor(color(segmentColor.r + 15, segmentColor.g + 15, segmentColor.b + 15, 150));
        buttonPathColor.setActiveColor(color(segmentColor.r - 10, segmentColor.g - 10, segmentColor.b - 10, 150));
        buttonPathColor.onClick(new Runnable() {
            @Override
            public void run() {
                colorPickerPathColor.setColor(segmentColor);

                // Try to put it to the right of the panel. If that doesn't fit, put it to the
                // left
                float x = segmentEditorPanel.pos.x + segmentEditorPanel.size.x / 2 + margin
                        + colorPickerPathColor.size.x / 2;
                if (x + colorPickerPathColor.size.x / 2 + margin > width)
                    x = segmentEditorPanel.pos.x - segmentEditorPanel.size.x / 2 - margin
                            - colorPickerPathColor.size.x / 2;

                colorPickerPathColor.setPos(x,
                        segmentEditorPanel.pos.y - segmentEditorPanel.size.y / 2 + colorPickerPathColor.size.y / 2);
                colorPickerPathColor.setActive(true);
            }
        });

        colorPickerPathColor = new ColorPicker(PVector.center());
        colorPickerPathColor.setActive(false);
        colorPickerPathColor.onChangeColor(new Runnable() {
            @Override
            public void run() {
                segmentColor = colorPickerPathColor.getColor();
                buttonPathColor.setDefaultColor(color(segmentColor, 150));
                buttonPathColor.setHoverColor(color(segmentColor.r + 15, segmentColor.g + 15, segmentColor.b + 15,
                        150));
                buttonPathColor.setActiveColor(color(segmentColor.r - 10, segmentColor.g - 10, segmentColor.b - 10,
                        150));
            }
        });

        segmentEditorPanel.addElementFromTop(textPathColor, false);
        segmentEditorPanel.addElementFromTop(buttonPathColor, false);
        segmentEditorPanel.addElementFromTop(colorPickerPathColor, false);
    }

    public void createInputFieldSegmentWidth(float w, float margin) {
        segmentEditorPanel.incrementElementHeight(margin + w);

        textSegmentWidth = new Text(-segmentEditorPanel.size.x / 2 + margin, 0, "Path width")
                .setTextAlignment(TextAlignment.LEFT);

        float x = calculateButtonX(buttonsTrafficTypes.size() - 2, w, margin);
        inputFieldSegmentWidth = new InputField(x, 0, w, w)
                .setDefaultText("").setAutoResize(false).setTextAlignment(TextAlignment.CENTER).setNumbersOnly(true);

        // Set value
        float value = segmentWidth / Settings.pixelsPerMeter;
        try {
            String text = str((int) value);
            inputFieldSegmentWidth.setText(text);
        } catch (Exception e) {
            inputFieldSegmentWidth.setText(String.valueOf(segmentWidth / Settings.pixelsPerMeter));
        }

        inputFieldSegmentWidth.onInput(new Runnable() {
            @Override
            public void run() {
                if (inputFieldSegmentWidth.getText().length() == 0)
                    return;
                segmentWidth = Float.parseFloat(inputFieldSegmentWidth.getText()) * Settings.pixelsPerMeter;
            }
        });

        textSegmentWidthUnits = new Text(segmentEditorPanel.size.x / 2 - margin - w, 0, "m");

        segmentEditorPanel.addElementFromTop(textSegmentWidth, false);
        segmentEditorPanel.addElementFromTop(inputFieldSegmentWidth, false);
        segmentEditorPanel.addElementFromTop(textSegmentWidthUnits, false);
    }

    public void createButtonsSegmentPriority(float w, float margin) {
        segmentEditorPanel.incrementElementHeight(margin + w);

        textSegmentPriority = new Text(-segmentEditorPanel.size.x / 2 + margin, 0, "Priority")
                .setTextAlignment(TextAlignment.LEFT);

        buttonSegmentPriorityDecrease = new Button(calculateButtonX(TrafficType.values().length - 3, w, margin), 0, w,
                w, "-");
        buttonSegmentPriorityDecrease.onClick(new Runnable() {
            @Override
            public void run() {
                priority--;
                if (priority < 0)
                    priority = 0;
                inputFieldSegmentPriority.setText(String.valueOf(priority));
            }
        });

        inputFieldSegmentPriority = new InputField(calculateButtonX(TrafficType.values().length - 2, w, margin), 0, w,
                w).setTextAlignment(TextAlignment.CENTER).setNumbersOnly(true).setAutoResize(false);
        inputFieldSegmentPriority.setText(String.valueOf(priority));
        inputFieldSegmentPriority.onInput(new Runnable() {
            @Override
            public void run() {
                try {
                    priority = round(parseFloat(inputFieldSegmentPriority.getText()));
                    if (priority >= 99)
                        priority = 99;
                    if (priority < 0)
                        priority = 0;
                } catch (Exception e) {
                }
            }
        });
        inputFieldSegmentPriority.onEnter(new Runnable() {
            @Override
            public void run() {
                try {
                    priority = round(parseFloat(inputFieldSegmentPriority.getText()));
                    if (priority >= 99 || priority < 0) {
                        priority = (priority < 0) ? 0 : 99;
                        inputFieldSegmentPriority.setText(String.valueOf(priority));
                    }
                } catch (Exception e) {
                }
            }
        });

        buttonSegmentPriorityIncrease = new Button(calculateButtonX(TrafficType.values().length - 1, w, margin), 0, w,
                w, "+");
        buttonSegmentPriorityIncrease.onClick(new Runnable() {
            @Override
            public void run() {
                priority++;
                if (priority >= 99)
                    priority = 99;
                inputFieldSegmentPriority.setText(String.valueOf(priority));
            }
        });

        segmentEditorPanel.addElementFromTop(textSegmentPriority, false);
        segmentEditorPanel.addElementFromTop(buttonSegmentPriorityDecrease, false);
        segmentEditorPanel.addElementFromTop(inputFieldSegmentPriority, false);
        segmentEditorPanel.addElementFromTop(buttonSegmentPriorityIncrease, false);
    }

    public void createButtonsParallel(float w, float margin) {
        segmentEditorPanel.incrementElementHeight(margin + w);

        buttonParallel = new Button(calculateButtonX(1, w, margin), 0, w * 3 + margin * 4, w, "Parallel");
        buttonParallel.onClick(new Runnable() {
            @Override
            public void run() {
                builder.parallelMode = !builder.parallelMode;
                setButtonColor(buttonParallel, builder.parallelMode);
            }
        });

        buttonParallelDecrease = new Button(calculateButtonX(TrafficType.values().length - 3, w, margin), 0, w,
                w, "-");
        buttonParallelDecrease.onClick(new Runnable() {
            @Override
            public void run() {
                builder.parallelNumSegments--;
                if (builder.parallelNumSegments >= -1 && builder.parallelNumSegments <= 1)
                    builder.parallelNumSegments = -2;
                if (builder.parallelNumSegments < -99)
                    builder.parallelNumSegments = -99;
                inputFieldParallel.setText(String.valueOf(builder.parallelNumSegments));
            }
        });

        inputFieldParallel = new InputField(calculateButtonX(TrafficType.values().length - 2, w, margin), 0, w,
                w).setTextAlignment(TextAlignment.CENTER).setNumbersOnly(true).setAutoResize(false);
        inputFieldParallel.setText(String.valueOf(builder.parallelNumSegments));
        inputFieldParallel.onInput(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean originallyPositive = builder.parallelNumSegments >= 0;
                    builder.parallelNumSegments = round(parseFloat(inputFieldParallel.getText()));
                    if (builder.parallelNumSegments >= 99)
                        builder.parallelNumSegments = 99;
                    if (builder.parallelNumSegments < -99)
                        builder.parallelNumSegments = -99;
                    if (builder.parallelNumSegments >= -1 && builder.parallelNumSegments <= 1) {
                        if (originallyPositive)
                            builder.parallelNumSegments = 2;
                        else
                            builder.parallelNumSegments = -2;
                    }
                } catch (Exception e) {
                }
            }
        });
        inputFieldParallel.onEnter(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean originallyPositive = builder.parallelNumSegments >= 0;
                    builder.parallelNumSegments = round(parseFloat(inputFieldParallel.getText()));
                    if (builder.parallelNumSegments >= 99 || builder.parallelNumSegments < -99) {
                        builder.parallelNumSegments = (builder.parallelNumSegments < -99) ? -99 : 99;
                        inputFieldParallel.setText(String.valueOf(builder.parallelNumSegments));
                    }
                    if (builder.parallelNumSegments >= -1 && builder.parallelNumSegments <= 1) {
                        if (originallyPositive)
                            builder.parallelNumSegments = 2;
                        else
                            builder.parallelNumSegments = -2;
                        inputFieldParallel.setText(String.valueOf(builder.parallelNumSegments));
                    }
                } catch (Exception e) {
                }
            }
        });

        buttonParallelIncrease = new Button(calculateButtonX(TrafficType.values().length - 1, w, margin), 0, w,
                w, "+");
        buttonParallelIncrease.onClick(new Runnable() {
            @Override
            public void run() {
                builder.parallelNumSegments++;
                if (builder.parallelNumSegments >= -1 && builder.parallelNumSegments <= 1)
                    builder.parallelNumSegments = 2;
                if (builder.parallelNumSegments >= 99)
                    builder.parallelNumSegments = 99;
                inputFieldParallel.setText(String.valueOf(builder.parallelNumSegments));
            }
        });

        segmentEditorPanel.addElementFromTop(buttonParallel, false);
        segmentEditorPanel.addElementFromTop(buttonParallelDecrease, false);
        segmentEditorPanel.addElementFromTop(inputFieldParallel, false);
        segmentEditorPanel.addElementFromTop(buttonParallelIncrease, false);
    }

    public void createButtonsSegmentType(float w, float margin) {
        segmentEditorPanel.incrementElementHeight(margin + w);

        int numButtons = TrafficType.values().length;
        buttonTypeStraight = new Button(calculateButtonX(numButtons * 1.0f / 3.0f - 1, w, margin), 0,
                3 * w + 4 * margin, w, "Straight");
        buttonTypeStraight.onClick(new Runnable() {
            @Override
            public void run() {
                setType(SegmentType.STRAIGHT);

                setButtonColor(buttonTypeStraight, true);
                setButtonColor(buttonTypeBezier, false);

                updatePath();
            }
        });
        buttonTypeBezier = new Button(calculateButtonX(numButtons * 2.0f / 3.0f, w, margin), 0,
                3 * w + 4 * margin, w, "Bezier");
        buttonTypeBezier.onClick(new Runnable() {
            @Override
            public void run() {
                setType(SegmentType.BEZIER);

                setButtonColor(buttonTypeStraight, false);
                setButtonColor(buttonTypeBezier, true);

                updatePath();
            }
        });

        setButtonColor(buttonTypeStraight, type == SegmentType.STRAIGHT);
        setButtonColor(buttonTypeBezier, type == SegmentType.BEZIER);

        segmentEditorPanel.addElementFromTop(buttonTypeStraight, false);
        segmentEditorPanel.addElementFromTop(buttonTypeBezier, false);
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

    public void updatePath() {
        updatePath(new HashMap<Segment, Integer>(), true);
    }

    public void updatePath(HashMap<Segment, Integer> visited, boolean recursive) {
        if (!visited.containsKey(this)) {
            visited.put(this, 1);
        } else {
            if (visited.get(this) > Settings.updatePathMaxRecursion)
                return;
            visited.put(this, visited.get(this) + 1);
        }

        if (snappedToSegments.size() > 0 && recursive) {
            for (Segment segment : snappedToSegments) {
                segment.updateControlledPaths(visited);
            }
        } else if (snappedToSegments.size() == 0) {
            calculatePaths();
        }

        for (Segment segment : segmentsPrevious) {
            segment.updatePath(visited, false);
        }
        if (recursive) {
            for (Segment segment : segmentsNext) {
                segment.updatePath(visited, false);
            }
        }
        updateControlledPaths(visited);
    }

    private void calculatePaths() {
        path = new ArrayList<PVector>();
        switch (type) {
            case STRAIGHT:
                float dist = PVector.sub(end, start).mag();
                int numNodes = ceil(dist / Settings.distanceToNodeThreshold);

                if (numNodes > 10000)
                    println("Warning! numNodes > 10000 in calculatePaths() Cursor:", builder.cursor.pos);

                if (numNodes == 0) {
                    path.add(start);
                    path.add(end);
                    break;
                }

                for (int i = 0; i <= numNodes; i++) {
                    float t = (float) i / (float) numNodes;
                    PVector node = PVector.lerp(start, end, t);
                    path.add(node);
                }
                break;
            case BEZIER:
                if (startHeading == -Float.MIN_VALUE)
                    startHeading = 0;
                if (endHeading == -Float.MIN_VALUE)
                    endHeading = 0;
                if (startControlPointMag == 0)
                    startControlPointMag = width / 10;
                if (endControlPointMag == 0)
                    endControlPointMag = width / 10;

                dist = PVector.sub(end, start).mag();
                numNodes = ceil(dist / Settings.distanceToNodeThreshold);

                if (numNodes == 0) {
                    path.add(start);
                    path.add(end);
                    break;
                }

                PVector startControlPoint = PVector.fromAngle(startHeading).setMag(startControlPointMag).add(start);
                PVector endControlPoint = PVector.sub(end, PVector.fromAngle(endHeading).setMag(endControlPointMag));
                for (int i = 0; i <= numNodes; i++) {
                    float t = (float) i / (float) numNodes;
                    PVector node = bezierPoint(t, start, startControlPoint, endControlPoint, end);
                    path.add(node);
                }
                break;
        }
    }

    public void updateControlledPaths(HashMap<Segment, Integer> visited) {
        for (Segment segment : controlledLeftStart) {
            segment.setStartControlPoint(startHeading, startControlPointMag, visited);
        }
        for (Segment segment : controlledRightStart) {
            segment.setStartControlPoint(startHeading, startControlPointMag, visited);
        }
        for (Segment segment : controlledLeftEnd) {
            segment.setEndControlPoint(endHeading, endControlPointMag, visited);
        }
        for (Segment segment : controlledRightEnd) {
            segment.setEndControlPoint(endHeading, endControlPointMag, visited);
        }

        for (Segment segment : controlledSegments) {
            if (controlledFullLeft.contains(segment) || controlledFullRight.contains(segment))
                continue;
            segment.updatePath(visited, true);
        }

        for (Segment segment : controlledFullLeft) {
            parallelSegment(segment, -1, visited);
        }
        for (Segment segment : controlledFullRight) {
            parallelSegment(segment, 1, visited);
        }
    }

    public void updateControlledPaths() {
        updateControlledPaths(new HashMap<Segment, Integer>());
    }

    private PVector bezierPoint(float t, PVector p0, PVector p1, PVector p2, PVector p3) {
        float u = 1 - t;

        PVector first = PVector.mult(p0, u * u * u);
        PVector second = PVector.mult(p1, 3 * u * u * t);
        PVector third = PVector.mult(p2, 3 * u * t * t);
        PVector fourth = PVector.mult(p3, t * t * t);

        return PVector.add(PVector.add(first, second), PVector.add(third, fourth));
    }

    private void parallelSegment(Segment segment, int direction, HashMap<Segment, Integer> visited) {
        float separation = segmentWidth / 2 + segment.segmentWidth / 2;

        segment.path = parallelSegment(separation, direction);
        segment.setStart(segment.path.get(0), visited);
        segment.setEnd(segment.path.get(segment.path.size() - 1), visited);
    }

    public ArrayList<PVector> parallelSegment(float separation, int direction) {
        ArrayList<PVector> newPath = new ArrayList<PVector>(path.size());

        for (int i = 0; i < path.size(); i++) {
            PVector tangent;

            if (i == 0) {
                tangent = PVector.sub(path.get(1), path.get(0));
            } else if (i == path.size() - 1) {
                tangent = PVector.sub(path.get(i), path.get(i - 1));
            } else {
                tangent = PVector.sub(path.get(i + 1), path.get(i - 1));
            }
            tangent.normalize();

            PVector normal = tangent.copy().rotate(direction * PI / 2).setMag(separation);
            newPath.add(PVector.add(path.get(i), normal));
        }

        return newPath;
    }

    public void show() {
        segmentEditorPanel.setActive(false);
        for (UIElement element : segmentEditorPanel.getElements()) {
            if (element == colorPickerPathColor)
                continue;
            element.setActive(false);
        }
        // drawMarkings();

        fill(segmentColor);
        noStroke();
        drawPath(path);

        // drawPathPoints();
    }

    public void showSelected() {
        // drawMarkings();

        fill(segmentColor);
        stroke(255);
        strokeWeight(3);
        drawPath(path);

        noStroke();
        // drawPathPoints();
        drawControlPoints();

        updateEditorPanel();

        strokeWeight(3);
    }

    private void updateEditorPanel() {
        segmentEditorPanel.setActive(true);
        for (UIElement element : segmentEditorPanel.getElements()) {
            if (element == colorPickerPathColor)
                continue;
            element.setActive(true);
        }

        float margin = segmentEditorPanel.size.x / 10;
        segmentEditorPanel.setPos(margin + segmentEditorPanel.size.x / 2, margin + segmentEditorPanel.size.y / 2);

        colorPickerPathColor.setColorPreviewPosition();

        segmentEditorPanel.draw();
    }

    public void updateUIWithValues() {
        for (int i = 0; i < buttonsTrafficTypes.size(); i++) {
            setButtonColor(buttonsTrafficTypes.get(i), trafficType == TrafficType.values()[i]);
        }

        for (int i = 0; i < buttonsSnappingOptions.size(); i++) {
            setButtonColor(buttonsSnappingOptions.get(i), builder.cursor.enabledSnappingOptions[i]);
        }

        colorPickerPathColor.setColor(segmentColor);
        colorPickerPathColor.setColorPreviewPosition();
        buttonPathColor.setDefaultColor(color(segmentColor, 150));
        buttonPathColor.setHoverColor(color(segmentColor.r + 15, segmentColor.g + 15, segmentColor.b + 15, 150));
        buttonPathColor.setActiveColor(color(segmentColor.r - 10, segmentColor.g - 10, segmentColor.b - 10, 150));

        float value = segmentWidth / Settings.pixelsPerMeter;
        try {
            String text = str((int) value);
            inputFieldSegmentWidth.setText(text);
        } catch (Exception e) {
            inputFieldSegmentWidth.setText(String.valueOf(segmentWidth / Settings.pixelsPerMeter));
        }

        inputFieldSegmentPriority.setText(String.valueOf(priority));

        setButtonColor(buttonParallel, builder.parallelMode);
        inputFieldParallel.setText(String.valueOf(builder.parallelNumSegments));

        setButtonColor(buttonTypeStraight, type == SegmentType.STRAIGHT);
        setButtonColor(buttonTypeBezier, type == SegmentType.BEZIER);
    }

    public void drawPath(ArrayList<PVector> pathToTrace) {
        float effectiveWidth = segmentWidth * Settings.segmentWidthPadding;
        beginShape();

        // There
        for (int i = 0; i < pathToTrace.size() - 1; i++) {
            PVector node = pathToTrace.get(i);
            PVector next = pathToTrace.get(i + 1);

            PVector offset = PVector.sub(next, node).normalize().rotate(-PI / 2).mult(effectiveWidth / 2);
            vertex(PVector.add(node, offset));

            // Finish out this line by adding the same offset to the final node. This is
            // okay because the exit paths are always straight
            if (i == pathToTrace.size() - 2) {
                vertex(PVector.add(next, offset));
            }
        }
        // Back
        for (int i = pathToTrace.size() - 1; i > 0; i--) {
            PVector node = pathToTrace.get(i);
            PVector next = pathToTrace.get(i - 1);

            PVector offset = PVector.sub(next, node).normalize().rotate(-PI / 2).mult(effectiveWidth / 2);
            vertex(PVector.add(node, offset));

            // Finish out this line by adding the same offset to the final node. This is
            // okay because the exit paths are always straight
            if (i == 1) {
                vertex(PVector.add(next, offset));
            }
        }
        endShape();
    }

    public void drawPathPoints() {
        for (PVector point : path) {
            fill(0);
            noStroke();
            circle(point, Settings.sizeAnchor / 1.5);
        }

        // // Draw left and right snaps
        // for (PVector node : List.of(start, end)) {
        // float head = (node == start) ? startHeading : endHeading;
        // PVector pseudoAnchorLeft = PVector.add(node,
        // PVector.fromAngle(head - PI / 2).setMag(segmentWidth / 2 + segmentWidth /
        // 2));
        // PVector pseudoAnchorRight = PVector.add(node,
        // PVector.fromAngle(head + PI / 2).setMag(segmentWidth / 2 + segmentWidth /
        // 2));
        //
        // fill(200, 0, 0);
        // circle(pseudoAnchorLeft, Settings.sizeAnchor / 2);
        // fill(0, 0, 200);
        // circle(pseudoAnchorRight, Settings.sizeAnchor / 2);
        // stroke(255);
        // strokeWeight(3);
        // }
    }

    public void drawControlPoints() {
        if (type == SegmentType.STRAIGHT)
            return;

        PVector startControlPoint = getStartControlPoint();
        PVector endControlPoint = getEndControlPoint();

        noFill();
        stroke(Settings.controlPointColor);
        strokeWeight(5);

        line(start, PVector.fromAngle(startHeading).setMag(startControlPointMag - Settings.sizeAnchor / 2).add(start));
        circle(startControlPoint, Settings.sizeAnchor);
        line(end,
                PVector.sub(end, PVector.fromAngle(endHeading).setMag(endControlPointMag - Settings.sizeAnchor / 2)));
        circle(endControlPoint, Settings.sizeAnchor);
    }

    public PVector getStartControlPoint() {
        return PVector.fromAngle(startHeading).setMag(startControlPointMag).add(start);
    }

    public PVector getEndControlPoint() {
        return PVector.sub(end, PVector.fromAngle(endHeading).setMag(endControlPointMag));
    }

    public boolean hovering() {
        // We ignore the first and last points so that it's possible to click anchors
        for (int i = 1; i < path.size() - 2; i++) {
            float dist = PVector.dist(path.get(i), mouse);
            if (dist < segmentWidth / 2)
                return true;
        }

        return false;
    }

    private void setStart(PVector pos, HashMap<Segment, Integer> visited) {
        start = pos.copy();
        if (startAnchor != null)
            startAnchor.setPos(pos.copy(), visited);
    }

    private void setEnd(PVector pos, HashMap<Segment, Integer> visited) {
        end = pos.copy();
        if (endAnchor != null)
            endAnchor.setPos(pos.copy(), visited);
    }

    public void setStart(PVector pos) {
        setStart(pos, new HashMap<>());
    }

    public void setEnd(PVector pos) {
        setEnd(pos, new HashMap<>());
    }

    public void setStartAnchor(Anchor anchor) {
        if (startAnchor != null)
            startAnchor.beginSegments.remove(this);
        anchor.beginSegments.add(this);

        startAnchor = anchor;
        setOpeningSegment(anchor.openingAnchor);
    }

    public void setEndAnchor(Anchor anchor) {
        if (endAnchor != null)
            endAnchor.endSegments.remove(this);
        anchor.endSegments.add(this);

        endAnchor = anchor;
        setClosingSegment(anchor.closingAnchor);
    }

    public void setStartHeading(float heading, boolean recursive) {
        startHeading = heading;
        if (recursive) {
            for (Segment segment : segmentsPrevious) {
                segment.setEndHeading(heading, false);
            }
        }
        for (Segment segment : controlledLeftStart) {
            segment.setStartHeading(heading, true);
        }
        for (Segment segment : controlledRightStart) {
            segment.setStartHeading(heading, true);
        }
    }

    public void setEndHeading(float heading, boolean recursive) {
        endHeading = heading;
        if (recursive) {
            for (Segment segment : segmentsNext) {
                segment.setStartHeading(heading, false);
            }
        }
        for (Segment segment : controlledLeftEnd) {
            segment.setEndHeading(heading, true);
        }
        for (Segment segment : controlledRightEnd) {
            segment.setEndHeading(heading, true);
        }
    }

    public void setStartControlPointMag(float mag, boolean recursive) {
        startControlPointMag = mag;
        if (recursive) {
            for (Segment segment : segmentsPrevious) {
                segment.setEndControlPointMag(mag, false);
            }
        }
        for (Segment segment : controlledLeftStart) {
            segment.setStartControlPointMag(mag, true);
        }
        for (Segment segment : controlledRightStart) {
            segment.setStartControlPointMag(mag, true);
        }
    }

    public void setEndControlPointMag(float mag, boolean recursive) {
        endControlPointMag = mag;
        if (recursive) {
            for (Segment segment : segmentsNext) {
                segment.setStartControlPointMag(mag, false);
            }
        }
        for (Segment segment : controlledLeftEnd) {
            segment.setEndControlPointMag(mag, true);
        }
        for (Segment segment : controlledRightEnd) {
            segment.setEndControlPointMag(mag, true);
        }
    }

    public void setHeadings(float startHeading, float endHeading) {
        setStartHeading(startHeading, true);
        setEndHeading(endHeading, true);
    }

    public void setStartControlPoint(float heading, float mag) {
        setStartHeading(heading, true);
        setStartControlPointMag(mag, true);
        updateControlledPaths();
    }

    public void setEndControlPoint(float heading, float mag) {
        setEndHeading(heading, true);
        setEndControlPointMag(mag, true);
        updateControlledPaths();
    }

    public void setStartControlPoint(float heading, float mag, HashMap<Segment, Integer> visited) {
        setStartHeading(heading, true);
        setStartControlPointMag(mag, true);
        updateControlledPaths(visited);
    }

    public void setEndControlPoint(float heading, float mag, HashMap<Segment, Integer> visited) {
        setEndHeading(heading, true);
        setEndControlPointMag(mag, true);
        updateControlledPaths(visited);
    }

    /**
     * Sets the end heading of the previous segments. If there are no previous
     * segments, does nothing.
     */
    public void setPreviousEndHeading(float heading) {
        for (Segment segment : segmentsPrevious) {
            segment.setEndHeading(heading, true);
        }
    }

    /**
     * Returns the heading from the start to the end of the segment.
     */
    public float getStraightHeading() {
        return PVector.sub(end, start).heading();
    }

    public void addSegmentPrevious(Segment segment) {
        segmentsPrevious.add(segment);
        segment.segmentsNext.add(this);
    }

    public void addSegmentNext(Segment segment) {
        segmentsNext.add(segment);
        segment.segmentsPrevious.add(this);
    }

    public void controlLeftStart(Segment segment) {
        controlledLeftStart.add(segment);
        controlledSegments.add(segment);
        if (controlledLeftEnd.contains(segment)) {
            controlledFullLeft.add(segment);
            segment.snappedToSegments.add(this);
        }

        if (openingSegment)
            segment.openingSegment = true;

        updateControlledPaths();
    }

    public void controlRightStart(Segment segment) {
        controlledRightStart.add(segment);
        controlledSegments.add(segment);
        if (controlledRightEnd.contains(segment)) {
            controlledFullRight.add(segment);
            segment.snappedToSegments.add(this);
        }

        if (openingSegment)
            segment.openingSegment = true;

        updateControlledPaths();
    }

    public void controlLeftEnd(Segment segment) {
        controlledLeftEnd.add(segment);
        controlledSegments.add(segment);
        if (controlledLeftEnd.contains(segment)) {
            controlledFullLeft.add(segment);
            segment.snappedToSegments.add(this);
        }

        if (closingSegment)
            segment.closingSegment = true;

        updateControlledPaths();
    }

    public void controlRightEnd(Segment segment) {
        controlledRightEnd.add(segment);
        controlledSegments.add(segment);
        if (controlledRightEnd.contains(segment)) {
            controlledFullRight.add(segment);
            segment.snappedToSegments.add(this);
        }

        if (closingSegment)
            segment.closingSegment = true;

        updateControlledPaths();
    }

    public void setType(SegmentType type) {
        this.type = type;
    }

    public void setTrafficType(TrafficType trafficType) {
        this.trafficType = trafficType;
        switch (trafficType) {
            case OV:
                segmentWidth = Settings.segmentWidthCar;
                segmentColor = Settings.segmentColorOV;
                setPriority(Settings.segmentPriorityOV);
                break;
            case CAR:
                segmentWidth = Settings.segmentWidthCar;
                segmentColor = Settings.segmentColorCar;
                setPriority(Settings.segmentPriorityCar);
                break;
            case BIKE_1WAY:
                segmentWidth = Settings.segmentWidthBike1Way;
                segmentColor = Settings.segmentColorBike;
                setPriority(Settings.segmentPriorityBike);
                break;
            case BIKE_2WAY:
                segmentWidth = Settings.segmentWidthBike2Way;
                segmentColor = Settings.segmentColorBike;
                setPriority(Settings.segmentPriorityBike);
                break;
            case PEDESTRIAN:
                segmentWidth = Settings.segmentWidthPedestrian;
                segmentColor = Settings.segmentColorPedestrian;
                setPriority(Settings.segmentPriorityPedestrian);
                break;
            case MEDIAN:
                segmentWidth = Settings.segmentWidthPedestrian;
                segmentColor = Settings.segmentColorPedestrian;
                setPriority(1);
                break;
        }

        if (buttonPathColor != null) {
            buttonPathColor.setDefaultColor(segmentColor);
            buttonPathColor.setHoverColor(color(segmentColor.r + 15, segmentColor.g + 15, segmentColor.b + 15, 150));
            buttonPathColor.setActiveColor(color(segmentColor.r - 10, segmentColor.g - 10, segmentColor.b - 10, 150));
        }
        if (inputFieldSegmentWidth != null) {
            float value = segmentWidth / Settings.pixelsPerMeter;
            try {
                String text = str((int) value);
                inputFieldSegmentWidth.setText(text);
            } catch (Exception e) {
                inputFieldSegmentWidth.setText(String.valueOf(segmentWidth / Settings.pixelsPerMeter));
            }
        }
    }

    public void setPriority(int priority) {
        this.priority = priority;
        if (inputFieldSegmentPriority != null) {
            inputFieldSegmentPriority.setText(String.valueOf(priority));
        }
    }

    public void setSegmentColor(color segmentColor) {
        this.segmentColor = segmentColor;
        if (buttonPathColor != null) {
            buttonPathColor.setDefaultColor(segmentColor);
            buttonPathColor.setHoverColor(color(segmentColor.r + 15, segmentColor.g + 15, segmentColor.b + 15, 150));
            buttonPathColor.setActiveColor(color(segmentColor.r - 10, segmentColor.g - 10, segmentColor.b - 10, 150));
        }
    }

    public void setOpeningSegment(boolean openingSegment) {
        this.openingSegment = openingSegment;
    }

    public void setClosingSegment(boolean closingSegment) {
        this.closingSegment = closingSegment;
    }

    public void setStartNode(PVector start) {
        this.start = start;
    }

    public void setEndNode(PVector end) {
        this.end = end;
    }

    public PVector getStartNode() {
        return start;
    }

    public PVector getEndNode() {
        return end;
    }

    public PVector getNode(int index) {
        return path.get(index);
    }

    public ArrayList<Object[]> getCrossedSegments(ArrayList<Segment> segments, boolean includeEndpoints) {
        ArrayList<Object[]> crossed = new ArrayList<>();
        float distanceThreshold = Settings.distanceToNodeThreshold * 1.5f;

        for (Segment segment : segments) {
            if (segment == this || segment.path == null || segment.path.size() == 0)
                continue;
            if (controlledSegments.contains(segment) || snappedToSegments.contains(segment))
                continue;
            if (endAnchor == segment.startAnchor || startAnchor == segment.endAnchor)
                continue;
            if (startAnchor == segment.startAnchor)
                continue;

            // NOTE: we use this "found" variable here to not have a segment be added
            // multiple times. I worry that if it gets added multiple times, when it then
            // gets split when making a junction, the next intersection (with that same
            // segment) will then get messed up because now that segments is actually a
            // different segment
            boolean found = false;
            int startIndex = (includeEndpoints) ? 0 : 1;
            int endIndexMe = (includeEndpoints) ? path.size() - 1 : path.size() - 2;
            int endIndexOther = (includeEndpoints) ? segment.path.size() - 1 : segment.path.size() - 2;
            for (int i = startIndex; i < endIndexMe; i++) {
                PVector node = path.get(i);
                for (int j = startIndex; j < endIndexOther; j++) {
                    PVector otherNode = segment.path.get(j);
                    if (node.dist(otherNode) < distanceThreshold) {
                        int iFirst = (i >= 1) ? i - 1 : 0;
                        int iLast = (i <= segment.path.size() - 2) ? i + 1 : i;
                        int jFirst = (j >= 1) ? j - 1 : 0;
                        int jLast = (j <= segment.path.size() - 2) ? j + 1 : j;

                        PVector intersection = findIntersection(path.get(iFirst), path.get(iLast),
                                segment.path.get(jFirst), segment.path.get(jLast));
                        crossed.add(new Object[] { segment, intersection });
                        found = true;
                        break;
                    }
                }
                if (found)
                    break;
            }
        }

        return crossed;
    }

    public PVector findIntersection(PVector p1, PVector p2, PVector p3, PVector p4) {
        float x1 = p1.x;
        float y1 = p1.y;
        float x2 = p2.x;
        float y2 = p2.y;

        float x3 = p3.x;
        float y3 = p3.y;
        float x4 = p4.x;
        float y4 = p4.y;

        float denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);

        if (Math.abs(denom) < 1e-6f) {
            return null; // Parallel or coincident lines
        }

        float px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / denom;

        float py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / denom;

        return new PVector(px, py);
    }

    public void mousePressed() {
        if (mouseButton != LEFT)
            return;
        if (colorPickerPathColor != null && colorPickerPathColor.isActive()) {
            if (!colorPickerPathColor.hover())
                colorPickerPathColor.setActive(false);
        }
    }

    public void deleteSegment() {
        segmentEditorPanel.setActive(false);
        delete(segmentEditorPanel);
        for (Button button : buttonsTrafficTypes) {
            button.setActive(false);
            delete(button);
        }
        for (Button button : buttonsSnappingOptions) {
            button.setActive(false);
            delete(button);
        }
        buttonPathColor.setActive(false);
        delete(buttonPathColor);
        colorPickerPathColor.setActive(false);
        delete(colorPickerPathColor);
        textPathColor.setActive(false);
        delete(textPathColor);
        textSegmentWidth.setActive(false);
        delete(textSegmentWidth);
        textSegmentWidthUnits.setActive(false);
        delete(textSegmentWidthUnits);
        inputFieldSegmentWidth.setActive(false);
        delete(inputFieldSegmentWidth);
        textSegmentPriority.setActive(false);
        delete(textSegmentPriority);
        inputFieldSegmentPriority.setActive(false);
        delete(inputFieldSegmentPriority);
        buttonSegmentPriorityDecrease.setActive(false);
        delete(buttonSegmentPriorityDecrease);
        buttonSegmentPriorityIncrease.setActive(false);
        delete(buttonSegmentPriorityIncrease);
        buttonParallel.setActive(false);
        delete(buttonParallel);
        buttonParallelDecrease.setActive(false);
        delete(buttonParallelDecrease);
        inputFieldParallel.setActive(false);
        delete(inputFieldParallel);
        buttonParallelIncrease.setActive(false);
        delete(buttonParallelIncrease);
        buttonTypeStraight.setActive(false);
        delete(buttonTypeStraight);
        buttonTypeBezier.setActive(false);
        delete(buttonTypeBezier);

        startAnchor.beginSegments.remove(this);
        if (startAnchor.beginSegments.size() == 0 && startAnchor.endSegments.size() == 0) {
            delete(startAnchor);
            builder.anchors.remove(startAnchor);
        }
        endAnchor.endSegments.remove(this);
        if (endAnchor.beginSegments.size() == 0 && endAnchor.endSegments.size() == 0) {
            delete(endAnchor);
            builder.anchors.remove(endAnchor);
        }
        for (Segment segment : segmentsPrevious) {
            segment.segmentsNext.remove(this);
        }
        for (Segment segment : segmentsNext) {
            segment.segmentsPrevious.remove(this);
        }
        for (Segment segment : snappedToSegments) {
            segment.controlledSegments.remove(this);
            segment.controlledLeftStart.remove(this);
            segment.controlledRightStart.remove(this);
            segment.controlledLeftEnd.remove(this);
            segment.controlledRightEnd.remove(this);
            segment.controlledFullLeft.remove(this);
            segment.controlledFullRight.remove(this);
        }
        for (Segment segment : controlledSegments) {
            segment.snappedToSegments.remove(this);
        }

        builder.segments.remove(this);
        delete(this);
    }

    public void setSettings(Segment segment) {
        if (segment == null || segment == this)
            return;

        type = segment.type;
        trafficType = segment.trafficType;
        segmentWidth = segment.segmentWidth;
        segmentColor = segment.segmentColor.copy();
        priority = segment.priority;

        updateUIWithValues();
    }

    public Segment copy() {
        Segment copy = new Segment(builder, start, trafficType);

        copy.builder = builder;

        copy.startAnchor = startAnchor;
        copy.endAnchor = endAnchor;
        copy.type = type;

        for (Segment segment : segmentsPrevious) {
            copy.segmentsPrevious.add(segment);
        }
        for (Segment segment : segmentsNext) {
            copy.segmentsNext.add(segment);
        }
        for (Segment segment : snappedToSegments) {
            copy.snappedToSegments.add(segment);
            segment.controlledSegments.add(copy);
            if (segment.controlledLeftStart.contains(this))
                segment.controlledLeftStart.add(copy);
            if (segment.controlledRightStart.contains(this))
                segment.controlledRightStart.add(copy);
            if (segment.controlledLeftEnd.contains(this))
                segment.controlledLeftEnd.add(copy);
            if (segment.controlledRightEnd.contains(this))
                segment.controlledRightEnd.add(copy);
            if (segment.controlledFullLeft.contains(this))
                segment.controlledFullLeft.add(copy);
            if (segment.controlledFullRight.contains(this))
                segment.controlledFullRight.add(copy);
        }

        copy.controlledSegments = new HashSet<>(controlledSegments);
        copy.controlledLeftStart = new HashSet<>(controlledLeftStart);
        copy.controlledRightStart = new HashSet<>(controlledRightStart);
        copy.controlledLeftEnd = new HashSet<>(controlledLeftEnd);
        copy.controlledRightEnd = new HashSet<>(controlledRightEnd);
        copy.controlledFullLeft = new HashSet<>(controlledFullLeft);
        copy.controlledFullRight = new HashSet<>(controlledFullRight);

        copy.start = start.copy();
        copy.end = end.copy();
        copy.startHeading = startHeading;
        copy.endHeading = endHeading;
        copy.startControlPointMag = startControlPointMag;
        copy.endControlPointMag = endControlPointMag;

        copy.path = new ArrayList<PVector>();
        for (PVector point : path) {
            copy.path.add(point.copy());
        }

        copy.trafficType = trafficType;
        copy.segmentWidth = segmentWidth;
        copy.segmentColor = segmentColor.copy();
        copy.priority = priority;

        copy.openingSegment = openingSegment;
        copy.closingSegment = closingSegment;

        copy.colorPickerPathColor.setColor(copy.segmentColor);
        copy.buttonPathColor.setDefaultColor(color(copy.segmentColor, 150));
        copy.buttonPathColor.setHoverColor(
                color(copy.segmentColor.r + 15, copy.segmentColor.g + 15, copy.segmentColor.b + 15, 150));
        copy.buttonPathColor.setActiveColor(
                color(copy.segmentColor.r - 10, copy.segmentColor.g - 10, copy.segmentColor.b - 10, 150));

        float value = copy.segmentWidth / Settings.pixelsPerMeter;
        try {
            String text = str((int) value);
            copy.inputFieldSegmentWidth.setText(text);
        } catch (Exception e) {
            copy.inputFieldSegmentWidth.setText(String.valueOf(copy.segmentWidth / Settings.pixelsPerMeter));
        }

        copy.inputFieldSegmentPriority.setText(String.valueOf(copy.priority));

        return copy;
    }
}
