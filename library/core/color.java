package library.core;

import java.awt.Color;

public class color {

    public int r, g, b, a;

    public color(Color c) {
        this(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
    }

    public color(color c, double alpha) {
        this(c.r, c.g, c.b, alpha);
    }

    public color(double r, double g, double b, double alpha) {
        this.r = (int) MathHelper.constrain(r, 0, 255);
        this.g = (int) MathHelper.constrain(g, 0, 255);
        this.b = (int) MathHelper.constrain(b, 0, 255);
        this.a = (int) MathHelper.constrain(alpha, 0, 255);
    }

    public color(double r, double g, double b) {
        this(r, g, b, 255);
    }

    public color(double g, double a) {
        this(g, g, g, a);
    }

    public color(double g) {
        this(g, 255);
    }

    public color() {
        this(0);
    }

    /**
     * Values must be enclosed in parentheses. Assumes: __(colors)<br>
     * <br>
     * "colors" is either 1, 2, 3, or 4 values separated by commas.<br>
     * <br>
     * Example: color(255, 255) is the same as rgb(255) or rgb(255, 255, 255),
     * etc.<br>
     * <br>
     * Does not support non-rgb color spaces.
     */
    public color(String c) {
        c = c.toLowerCase().trim().replace(" ", "");
        if (c.contains("(")) {
            int openParenIndex = c.indexOf('(');
            c = c.substring(openParenIndex + 1, c.length() - 1);
        }

        String[] stringValues = c.split(",");
        double[] values = new double[stringValues.length];
        for (int i = 0; i < stringValues.length; i++) {
            values[i] = Double.parseDouble(stringValues[i]);
        }
        if (values.length == 1) {
            this.r = (int) MathHelper.constrain(values[0], 0, 255);
            this.g = (int) MathHelper.constrain(values[0], 0, 255);
            this.b = (int) MathHelper.constrain(values[0], 0, 255);
            this.a = 255;
        } else if (values.length == 2) {
            this.r = (int) MathHelper.constrain(values[0], 0, 255);
            this.g = (int) MathHelper.constrain(values[0], 0, 255);
            this.b = (int) MathHelper.constrain(values[0], 0, 255);
            this.a = (int) MathHelper.constrain(values[1], 0, 255);
        } else if (values.length == 3) {
            this.r = (int) MathHelper.constrain(values[0], 0, 255);
            this.g = (int) MathHelper.constrain(values[1], 0, 255);
            this.b = (int) MathHelper.constrain(values[2], 0, 255);
            this.a = 255;
        } else if (values.length == 4) {
            this.r = (int) MathHelper.constrain(values[0], 0, 255);
            this.g = (int) MathHelper.constrain(values[1], 0, 255);
            this.b = (int) MathHelper.constrain(values[2], 0, 255);
            this.a = (int) MathHelper.constrain(values[3], 0, 255);
        } else {
            throw new IllegalArgumentException("Invalid color string: " + c);
        }
    }

    public static color fromInt(int c) {
        return new color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >> 24) & 0xFF);
    }

    /**
     * Generates a color from HSB. hue, saturation, and brightness are from 0 to 1.
     */
    public static color fromHSB(double hue, double saturation, double brightness) {
        Color c = Color.getHSBColor((float) hue, (float) saturation, (float) brightness);
        return new color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
    }

    public int getRGB() {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public float getRed() {
        return r;
    }

    public float getGreen() {
        return g;
    }

    public float getBlue() {
        return b;
    }

    public float getAlpha() {
        return a;
    }

    public color setRed(double r) {
        this.r = (int) r;
        return this;
    }

    public color setGreen(double g) {
        this.g = (int) g;
        return this;
    }

    public color setBlue(double b) {
        this.b = (int) b;
        return this;
    }

    public color setAlpha(double a) {
        this.a = (int) a;
        return this;
    }

    public color setColor(color c) {
        color col = c.copy();
        this.r = col.r;
        this.g = col.g;
        this.b = col.b;
        this.a = col.a;
        return this;
    }

    /**
     * Sets the brightness of the color. Note: The brightness is in the range 0-1.
     */
    public color setBrightness(double brightness) {
        float[] hsb = getHSB();
        Color c = Color.getHSBColor(hsb[0], hsb[1], (float) brightness);
        this.r = c.getRed();
        this.g = c.getGreen();
        this.b = c.getBlue();
        return this;
    }

    /**
     * Constrains the RGBA values to be between 0 and 255.
     */
    public color constrain() {
        this.r = (int) MathHelper.constrain(r, 0, 255);
        this.g = (int) MathHelper.constrain(g, 0, 255);
        this.b = (int) MathHelper.constrain(b, 0, 255);
        this.a = (int) MathHelper.constrain(a, 0, 255);
        return this;
    }

    /**
     * Adds the RGBA values of the given color to this color. Then constrains this
     * color's values from 0 to 255.
     */
    public color add(color c) {
        this.r += c.r;
        this.g += c.g;
        this.b += c.b;
        this.a += c.a;
        constrain();
        return this;
    }

    /**
     * Adds the given RGBA values to this color. Then constrains this color's values
     * from 0 to 255.
     */
    public color add(double r, double g, double b, double a) {
        this.r += r;
        this.g += g;
        this.b += b;
        this.a += a;
        constrain();
        return this;
    }

    /**
     * Adds the given RGB values to this color. Then constrains this color's values
     * from 0 to 255.
     */
    public color add(double r, double g, double b) {
        return add(r, g, b, 0);
    }

    /**
     * Adds a value to all RGB values. Adds an alpha to the alpha channel. Then
     * constrains this color's values from 0 to 255.
     */
    public color add(double value, double a) {
        return add(value, value, value, a);
    }

    /**
     * Adds a value to all RGB values. Then constrains this color's values from 0 to
     * 255.
     */
    public color add(double value) {
        return add(value, 0);
    }

    /**
     * Subtracts the RGBA values of the given color from this color. Then constrains
     * this color's values from 0 to 255.
     */
    public color sub(color c) {
        this.r -= c.r;
        this.g -= c.g;
        this.b -= c.b;
        this.a -= c.a;
        constrain();
        return this;
    }

    /**
     * Subtracts the given RGBA values from this color. Then constrains this color's
     * values from 0 to 255.
     */
    public color sub(double r, double g, double b, double a) {
        this.r -= r;
        this.g -= g;
        this.b -= b;
        this.a -= a;
        constrain();
        return this;
    }

    /**
     * Subtracts the given RGB values from this color. Then constrains this color's
     * values from 0 to 255.
     */
    public color sub(double r, double g, double b) {
        return sub(r, g, b, 0);
    }

    /**
     * Subtracts a value from all RGB values. Subtracts an alpha from the alpha
     * channel. Then constrains this color's values from 0 to 255.
     */
    public color sub(double value, double a) {
        return sub(value, value, value, a);
    }

    /**
     * Subtracts a value from all RGB values. Then constrains this color's values
     * from 0 to 255.
     */
    public color sub(double value) {
        return sub(value, 0);
    }

    /**
     * Multiplies the RGBA values of this color by the RGBA values of the given
     * color. Then constrains this color's values from 0 to 255.
     */
    public color mult(color c) {
        this.r *= c.r;
        this.g *= c.g;
        this.b *= c.b;
        this.a *= c.a;
        constrain();
        return this;
    }

    /**
     * Multiplies the RGBA values of this color by the given RGBA values. Then
     * constrains this color's values from 0 to 255.
     */
    public color mult(double r, double g, double b, double a) {
        this.r *= r;
        this.g *= g;
        this.b *= b;
        this.a *= a;
        constrain();
        return this;
    }

    /**
     * Multiplies the RGB values of this color by the given RGB values. Then
     * constrains this color's values from 0 to 255.
     */
    public color mult(double r, double g, double b) {
        return mult(r, g, b, 1);
    }

    /**
     * Multiplies all RGB values by a value. Multiplies the alpha channel by a
     * value. Then constrains this color's values from 0 to 255.
     */
    public color mult(double value, double a) {
        return mult(value, value, value, a);
    }

    /**
     * Multiplies all RGB values by a value. Then constrains this color's values
     * from 0 to 255.
     */
    public color mult(double value) {
        return mult(value, 1);
    }

    /**
     * Divides the RGBA values of this color by the RGBA values of the given color.
     * Then constrains this color's values from 0 to 255.
     */
    public color div(color c) {
        this.r /= c.r;
        this.g /= c.g;
        this.b /= c.b;
        this.a /= c.a;
        constrain();
        return this;
    }

    /**
     * Divides the RGBA values of this color by the given RGBA values. Then
     * constrains this color's values from 0 to 255.
     */
    public color div(double r, double g, double b, double a) {
        this.r /= r;
        this.g /= g;
        this.b /= b;
        this.a /= a;
        constrain();
        return this;
    }

    /**
     * Divides the RGB values of this color by the given RGB values. Then constrains
     * this color's values from 0 to 255.
     */
    public color div(double r, double g, double b) {
        return div(r, g, b, 1);
    }

    /**
     * Divides all RGB values by a value. Divides the alpha channel by a value. Then
     * constrains this color's values from 0 to 255.
     */
    public color div(double value, double a) {
        return div(value, value, value, a);
    }

    /**
     * Divides all RGB values by a value. Then constrains this color's values from 0
     * to 255.
     */
    public color div(double value) {
        return div(value, 1);
    }

    public float[] getHSB() {
        return Color.RGBtoHSB(r, g, b, null);
    }

    public float getHue() {
        float[] hsb = getHSB();
        return hsb[0];
    }

    public float getSaturation() {
        float[] hsb = getHSB();
        return hsb[1];
    }

    public float getBrightness() {
        float[] hsb = getHSB();
        return hsb[2];
    }

    public String toString() {
        return "color(" + r + ", " + g + ", " + b + ", " + a + ")";
    }

    public boolean equals(color c) {
        return r == c.r && g == c.g && b == c.b && a == c.a;
    }

    public Color toColor() {
        return new Color(r, g, b, a);
    }

    public color copy() {
        return new color(r, g, b, a);
    }

    /**
     * Returns a random color. RGB values are between 100 and 255.
     */
    public static color randomColor() {
        return color.fromHSB(MathHelper.random(0, 1), MathHelper.random(0.6, 0.85), MathHelper.random(0.6, 0.9));
    }

}
