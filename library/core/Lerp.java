package library.core;

public class Lerp {

    /**
     * Linearly lerps between two {@code float} values.
     * 
     * @param start
     * @param stop
     * @param amt
     * 
     * @return float
     */
    public static float lerp(double start, double stop, double amt) {
        return (float) (start + (stop - start) * amt);
    }

    /**
     * Smoothly lerps between two {@code float} values.
     * 
     * @param start
     * @param stop
     * @param amt
     * 
     * @return float
     */
    public static float lerpSmooth(double start, double stop, double amt) {
        // return lerp(start, stop, amt * amt * (3 - 2 * amt));

        double progress = lerp(-Math.PI / 2, Math.PI / 2, amt);
        progress = Math.sin(progress);
        progress = (progress / 2) + 0.5;
        return lerp(start, stop, progress);

        // return lerp(start, stop, easeInOut(amt));
    }

    /**
     * Smoothly lerps between two {@code float} values. The ease-in-out part is
     * exaggerated compared to lerpSmooth.
     *
     * @param start
     * @param stop
     * @param amt
     *
     * @return float
     */
    public static float lerpSuperSmooth(double start, double stop, double amt) {
        double progress = lerp(-Math.PI / 2, Math.PI / 2, amt);
        progress = Math.sin(progress);
        progress = (progress / 2) + 0.5;
        return lerp(start, stop, easeInOut(progress));
    }

    /**
     * Overshoot lerps between two {@code float} values.
     * 
     * @param start
     * @param stop
     * @param amt
     */
    public static float lerpOvershoot(double start, double stop, double amt) {
        double x = amt * 3;
        if (x >= 3.14) {
            return (float) stop;
        }
        double progress = 1 - ((Math.sin(x) / x) * (Math.sin(3 * x) / (3 * x)));
        return lerp(start, stop, progress);
    }

    /**
     * Constantly accelerates from the start up to the stop value.
     *
     * @param start
     * @param stop
     * @param amt
     */
    public static float lerpConstantAcceleration(double start, double stop, double amt) {
        double progress = Math.pow(amt, 2);
        return lerp(start, stop, progress);
    }

    /**
     * Constantly decelerates from the start up to the stop value.
     *
     * @param start
     * @param stop
     * @param amt
     */
    public static float lerpConstantDeceleration(double start, double stop, double amt) {
        // Same function as constant acceleration, but "x" not runs from -1 to 0, as
        // oppposed to 0 to 1
        double progress = Math.pow(1 - amt, 2);
        return lerp(start, stop, progress);
    }

    /**
     * Returns a {@code float} amount that is eased in from the given {@code float}
     * amt.
     * 
     * @param amt
     * 
     * @return float
     */
    public static float easeIn(double amt) {
        return (float) (amt * amt);
    }

    /**
     * Returns a {@code float} amount that is flipped from the given {@code float}
     * amt.
     * 
     * @param amt
     * 
     * @return float
     */
    public static float flip(double amt) {
        return (float) (1 - amt);
    }

    /**
     * Returns a {@code float} amount that is eased out from the given {@code float}
     * amt.
     * 
     * @param amt
     * 
     * @return float
     */
    public static float easeOut(double amt) {
        return flip(easeIn(flip(amt)));
    }

    /**
     * Returns a {@code float} amount that is eased in and out from the given
     * {@code float} amt.
     * 
     * @param amt
     * 
     * @return float
     */
    public static float easeInOut(double amt) {
        return lerp(easeIn(amt), easeOut(amt), amt);
    }

}
