import library.core.*;

class Settings extends PFunctions {
    public static final int pixelsPerMeter = 10;

    public static boolean drawAnchors = true;

    /**
     * Statistically, how many seconds between each spawn of a road user.
     */
    public static float trafficSpawnInterval = 2.5f;
    public static int maxTraffic = 100;
    public static float OVSpawnChance = 0.065f;
    /**
     * Chance that if a vulnerable user was chosen to spawn that it will actually go
     * ahead with it instead of rerolling. If this is 0 then vulnerable users will
     * never spawn, if it's 1 then they will never get rerolled (and thereby have
     * the same spawn chance as cars)
     */
    public static float vulnerableSpawnChance = 0.6f;

    public static float sizeAnchor = 1.5f * pixelsPerMeter;
    public static float segmentWidthCar = 3 * pixelsPerMeter;
    public static float segmentWidthBike1Way = 2 * pixelsPerMeter;
    public static float segmentWidthBike2Way = 4 * pixelsPerMeter;
    public static float segmentWidthPedestrian = 2 * pixelsPerMeter;
    public static float segmentWidthMedian = 1 * pixelsPerMeter;
    /**
     * Multiplier when drawing the segments to remove the awkward green space that
     * sometimes occurs between segments.
     */
    public static float segmentWidthPadding = 1.05f;

    public static int segmentPriorityOV = 10;
    public static int segmentPriorityCar = 5;
    public static int segmentPriorityBike = 8;
    public static int segmentPriorityPedestrian = 7;

    public static float vehicleWidthOV = 2.5f * pixelsPerMeter;
    public static float vehicleWidthCar = 1.9f * pixelsPerMeter;
    public static float vehicleWidthBike = 0.55f * pixelsPerMeter;
    public static float vehicleWidthPedestrian = 0.45f * pixelsPerMeter;

    public static float vehicleLengthOV = 7 * pixelsPerMeter;
    public static float vehicleLengthCar = 4.5f * pixelsPerMeter;
    public static float vehicleLengthBike = 1.7f * pixelsPerMeter;
    public static float vehicleLengthPedestrian = vehicleWidthPedestrian;

    public static float vehicleAccelerationOV = 1.75f * pixelsPerMeter;
    public static float vehicleAccelerationCar = 1.75f * pixelsPerMeter;
    public static float vehicleAccelerationBike = 0.23f * pixelsPerMeter;
    public static float vehicleAccelerationPedestrian = 1f * pixelsPerMeter;

    public static float vehicleMaxSpeedOV = 8.3f * pixelsPerMeter;
    public static float vehicleMaxSpeedCar = 8.3f * pixelsPerMeter;
    public static float vehicleMaxSpeedBike = 3.6f * pixelsPerMeter;
    public static float vehicleMaxSpeedPedestrian = 1 * pixelsPerMeter;

    public static float reactionTime = 1;
    public static float turningRate = 0.08f;
    public static float distanceToNodeThreshold = 1.8f * pixelsPerMeter;
    /*
     * If the light is yellow, how long (as a percentage of the total time) could it
     * be yellow for and for people to still go through it. If the yellow has been
     * longer than this percentage, then people won't go
     */
    public static float percentageOfYellowToStillGo = 0.8f;

    public static float variationAcceleration = 0.22f;
    public static float variationMaxSpeed = 0.22f;
    public static float variationReactionTime = 0.2f;

    public static float yellowTime = 2.5f;
    public static float greenTimeMinimum = 4f;
    public static float redTimeMinimum = 1f;

    public static color lightGreen = new color(0, 135, 84);
    public static color lightYellow = new color(247, 181, 0);
    public static color lightRed = new color(187, 30, 16);
    /**
     * The opacity of the traffic lights when they aren't on
     */
    public static int lightOffAlpha = 10;
    /**
     * How many frames in between each toggle of on or off of the pedestrian light.
     */
    public static int lightPedestrianFlashInterval = 17;

    public static int weightsDetectedCount = 4;
    public static int weightsWaitTime = 2;
    /**
     * The idea for making this one the same value as weightsWaitTime is because
     * both are just about literally waiting for x number of seconds before a green
     */
    public static int weightsTimeUntilGreen = 2;
    public static int weightsQueueSensor = 21;

    public static int noDetectionTime = 1 * 60;

    public static color controlPointColor = new color(173, 68, 199);

    public static color segmentColorCar = new color(61);
    public static color segmentColorBike = new color(189, 74, 66);
    public static color segmentColorPedestrian = new color(75);
    public static color segmentColorOV = new color(128);

    public static color buttonDefault = new color(62, 94, 171, 150);
    public static color buttonHover = new color(77, 109, 186, 150);
    public static color buttonActive = new color(52, 84, 161, 150);
    public static color buttonAccentDefault = new color(70, 219, 75, 150);
    public static color buttonAccentHover = new color(85, 234, 90, 150);
    public static color buttonAccentActive = new color(60, 209, 65, 150);

    public static int updatePathMaxRecursion = 5;
}
