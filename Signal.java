enum Signal {
    RED, YELLOW, GREEN;

    public static Signal compareSignalsTowardsTarget(Signal signal1, Signal signal2, Signal target) {
        if (signal1 == null)
            return signal2;
        if (signal2 == null)
            return signal1;

        switch (target) {
            case Signal.RED:
                if (signal1 == Signal.RED || signal2 == Signal.RED)
                    return Signal.RED;
                if (signal1 == Signal.YELLOW || signal2 == Signal.YELLOW)
                    return Signal.YELLOW;

                return Signal.GREEN;
            case Signal.YELLOW:
                if (signal1 == Signal.YELLOW || signal2 == Signal.YELLOW)
                    return Signal.YELLOW;

                return signal1;

            case Signal.GREEN:
                if (signal1 == Signal.GREEN || signal2 == Signal.GREEN)
                    return Signal.GREEN;
                if (signal1 == Signal.YELLOW || signal2 == Signal.YELLOW)
                    return Signal.YELLOW;

                return Signal.RED;
        }

        return null;
    }
}
