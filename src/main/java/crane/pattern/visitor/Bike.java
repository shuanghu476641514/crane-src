package crane.pattern.visitor;

/**
 * Bike
 * 具体元素：自行车。
 */
public class Bike implements Vehicle {

    private final String brand;
    private final String type;
    private final int wheelSize;
    private final boolean electric;

    public Bike(String brand, String type, int wheelSize, boolean electric) {
        this.brand = brand;
        this.type = type;
        this.wheelSize = wheelSize;
        this.electric = electric;
    }

    public String getBrand() {
        return brand;
    }

    public String getType() {
        return type;
    }

    public int getWheelSize() {
        return wheelSize;
    }

    public boolean isElectric() {
        return electric;
    }

    @Override
    public void accept(VehicleVisitor visitor) {
        visitor.visit(this);
    }
}
