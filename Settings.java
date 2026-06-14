import library.core.*;

class Settings extends PFunctions {
    public static final int pixelsPerMeter = 10;

    public static boolean drawAnchors = true;

    public static float sizeAnchor = 1.5f * pixelsPerMeter;
    public static float segmentWidthCar = 3 * pixelsPerMeter;
    public static float segmentWidthBike1Way = 2 * pixelsPerMeter;
    public static float segmentWidthBike2Way = 4 * pixelsPerMeter;
    public static float segmentWidthPedestrian = 2 * pixelsPerMeter;

    public static float vehicleWidthCar = 1.9f * pixelsPerMeter;
    public static float vehicleWidthBike = 0.55f * pixelsPerMeter;
    public static float vehicleWidthPedestrian = 0.45f * pixelsPerMeter;

    public static float vehicleLengthCar = 4.5f * pixelsPerMeter;
    public static float vehicleLengthBike = 1.7f * pixelsPerMeter;
    public static float vehicleLengthPedestrian = vehicleWidthPedestrian;

    public static color lightGreen = new color(36, 156, 42);
    public static color lightYellow = new color(207, 176, 54);
    public static color lightRed = new color(189, 57, 47);

    public static color controlPointColor = new color(173, 68, 199);

    public static color segmentColorCar = new color(61);
    public static color segmentColorBike = new color(189, 74, 66);
    public static color segmentColorPedestrian = new color(75);
    public static color segmentColorOV = new color(128);
}
