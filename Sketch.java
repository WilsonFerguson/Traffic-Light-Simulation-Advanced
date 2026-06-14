import library.core.*;
import GameEngine.*;

class Sketch extends Applet {

    Builder builder;

    public void setup() {
        size(1000, 1000);

        builder = new Builder();
        exitOnEscape(false);
    }

    public void draw() {
        GameEngine.Run();
        background(27, 135, 11);

        builder.update();
        builder.show();
    }

}
