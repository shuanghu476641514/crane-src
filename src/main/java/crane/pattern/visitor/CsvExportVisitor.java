package crane.pattern.visitor;

/**
 * CsvExportVisitor
 * 具体访问者：将车辆属性导出为 CSV 行。
 * 首行字段依次为：type,brand,extra1,extra2,extra3。
 */
public class CsvExportVisitor implements VehicleVisitor {

    private final StringBuilder csv = new StringBuilder();

    @Override
    public void visit(Car car) {
        csv.append("car,")
                .append(car.getBrand()).append(',')
                .append(car.getModel()).append(',')
                .append(car.getYear()).append(',')
                .append(car.getDoors())
                .append('\n');
    }

    @Override
    public void visit(Bike bike) {
        csv.append("bike,")
                .append(bike.getBrand()).append(',')
                .append(bike.getType()).append(',')
                .append(bike.getWheelSize()).append(',')
                .append(bike.isElectric())
                .append('\n');
    }

    /**
     * 获取累计导出的 CSV 文本。
     */
    public String getCsv() {
        return csv.toString();
    }
}
