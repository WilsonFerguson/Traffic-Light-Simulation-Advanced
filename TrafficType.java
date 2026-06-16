enum TrafficType {
    OV,
    CAR,
    BIKE_1WAY,
    BIKE_2WAY,
    PEDESTRIAN,
    MEDIAN;

    public float getLength() {
        switch (this) {
            case OV:
                return Settings.vehicleLengthOV;
            case CAR:
                return Settings.vehicleLengthCar;
            case BIKE_1WAY:
                return Settings.vehicleLengthBike;
            case BIKE_2WAY:
                return Settings.vehicleLengthBike;
            case PEDESTRIAN:
                return Settings.vehicleLengthPedestrian;
            case MEDIAN:
                throw new RuntimeException("getLength() was called for TrafficType MEDIAN");
        }

        throw new RuntimeException("Somehow getLength() could not find a value for TrafficType " + this);
    }

    public float getWidth() {
        switch (this) {
            case OV:
                return Settings.vehicleWidthOV;
            case CAR:
                return Settings.vehicleWidthCar;
            case BIKE_1WAY:
                return Settings.vehicleWidthBike;
            case BIKE_2WAY:
                return Settings.vehicleWidthBike;
            case PEDESTRIAN:
                return Settings.vehicleWidthPedestrian;
            case MEDIAN:
                throw new RuntimeException("getWidth() was called for TrafficType MEDIAN");
        }

        throw new RuntimeException("Somehow getLength() could not find a value for TrafficType " + this);
    }

    public float getAcceleration() {
        switch (this) {
            case OV:
                return Settings.vehicleAccelerationOV;
            case CAR:
                return Settings.vehicleAccelerationCar;
            case BIKE_1WAY:
                return Settings.vehicleAccelerationBike;
            case BIKE_2WAY:
                return Settings.vehicleAccelerationBike;
            case PEDESTRIAN:
                return Settings.vehicleAccelerationPedestrian;
            case MEDIAN:
                throw new RuntimeException("getAcceleration() was called for TrafficType MEDIAN");
        }

        throw new RuntimeException("Somehow getAcceleration() could not find a value for TrafficType " + this);
    }

    public float getMaxSpeed() {
        switch (this) {
            case OV:
                return Settings.vehicleMaxSpeedOV;
            case CAR:
                return Settings.vehicleMaxSpeedCar;
            case BIKE_1WAY:
                return Settings.vehicleMaxSpeedBike;
            case BIKE_2WAY:
                return Settings.vehicleMaxSpeedBike;
            case PEDESTRIAN:
                return Settings.vehicleMaxSpeedPedestrian;
            case MEDIAN:
                throw new RuntimeException("getMaxSpeed() was called for TrafficType MEDIAN");
        }

        throw new RuntimeException("Somehow getMaxSpeed() could not find a value for TrafficType " + this);
    }

    public boolean isVulnerable() {
        return this == BIKE_1WAY || this == BIKE_2WAY || this == PEDESTRIAN;
    }
}
