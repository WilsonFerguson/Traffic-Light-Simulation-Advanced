class SignalGroup {
    Signal signalNormal, signalReverse;

    public SignalGroup(Signal signalNormal, Signal signalReverse) {
        this.signalNormal = signalNormal;
        this.signalReverse = signalReverse;
    }

    public static SignalGroup make(Signal signal, boolean reverse) {
        SignalGroup group = new SignalGroup(null, null);
        group.set(signal, reverse);
        return group;
    }

    public SignalGroup setNormal(Signal signal) {
        signalNormal = signal;
        return this;
    }

    public SignalGroup setReverse(Signal signal) {
        signalReverse = signal;
        return this;
    }

    public SignalGroup set(Signal signal, boolean reverse) {
        if (reverse)
            setReverse(signal);
        else
            setNormal(signal);

        return this;
    }

    public SignalGroup remove(boolean reverse) {
        return set(null, reverse);
    }

    public Signal getNormal() {
        return signalNormal;
    }

    public Signal getReverse() {
        return signalReverse;
    }

    public Signal get(boolean reverse) {
        return reverse ? signalReverse : signalNormal;
    }

    public boolean hasNormal() {
        return signalNormal != null;
    }

    public boolean hasReverse() {
        return signalReverse != null;
    }
}
