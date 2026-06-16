import library.core.*;

class Settings extends PFunctions {
    public static final int pixelsPerMeter = 10;

    public static boolean drawAnchors = true;

    /**
     * Statistically, how many seconds between each spawn of a road user
     */
    public static float trafficSpawnInterval = 0.2f;
    public static int maxTraffic = 100;
    public static float OVSpawnChance = 0.05f;

    public static float sizeAnchor = 1.5f * pixelsPerMeter;
    public static float segmentWidthCar = 3 * pixelsPerMeter;
    public static float segmentWidthBike1Way = 2 * pixelsPerMeter;
    public static float segmentWidthBike2Way = 4 * pixelsPerMeter;
    public static float segmentWidthPedestrian = 2 * pixelsPerMeter;

    public static int segmentPriorityOV = 8;
    public static int segmentPriorityCar = 5;
    public static int segmentPriorityBike = 7;
    public static int segmentPriorityPedestrian = 6;

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
    public static float distanceToNodeThreshold = 2f * pixelsPerMeter;

    public static float variationAcceleration = 0.22f;
    public static float variationMaxSpeed = 0.22f;
    public static float variationReactionTime = 0.2f;

    public static color lightGreen = new color(36, 156, 42);
    public static color lightYellow = new color(207, 176, 54);
    public static color lightRed = new color(189, 57, 47);

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
}
