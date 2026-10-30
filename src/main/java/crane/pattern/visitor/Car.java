package crane.pattern.visitor;

/**
 * Car
 * 具体元素：汽车。
 */
public class Car implements Vehicle {

    private final String brand;
    private final String model;
    private final int year;
    private final int doors;

    public Car(String brand, String model, int year, int doors) {
        this.brand = brand;
        this.model = model;
        this.year = year;
        this.doors = doors;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public int getYear() {
        return year;
    }

    public int getDoors() {
        return doors;
    }

    @Override
    public void accept(VehicleVisitor visitor) {
        visitor.visit(this);
    }
}
