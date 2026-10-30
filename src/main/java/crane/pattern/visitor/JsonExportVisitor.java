package crane.pattern.visitor;

/**
 * JsonExportVisitor
 * 具体访问者：将车辆属性导出为 JSON 对象（每辆车一行）。
 */
public class JsonExportVisitor implements VehicleVisitor {

    private final StringBuilder json = new StringBuilder();

    @Override
    public void visit(Car car) {
        json.append("{\"type\":\"car\",")
                .append("\"brand\":\"").append(car.getBrand()).append("\",")
                .append("\"model\":\"").append(car.getModel()).append("\",")
                .append("\"year\":").append(car.getYear()).append(',')
                .append("\"doors\":").append(car.getDoors())
                .append("}\n");
    }

    @Override
    public void visit(Bike bike) {
        json.append("{\"type\":\"bike\",")
                .append("\"brand\":\"").append(bike.getBrand()).append("\",")
                .append("\"bikeType\":\"").append(bike.getType()).append("\",")
                .append("\"wheelSize\":").append(bike.getWheelSize()).append(',')
                .append("\"electric\":").append(bike.isElectric())
                .append("}\n");
    }

    /**
     * 获取累计导出的 JSON 文本。
     */
    public String getJson() {
        return json.toString();
    }
}
