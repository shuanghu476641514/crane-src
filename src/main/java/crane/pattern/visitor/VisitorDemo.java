package crane.pattern.visitor;

import java.util.List;

/**
 * VisitorDemo
 * 访问者模式样例入口。
 *
 * <p>演示流程：</p>
 * <ul>
 *     <li>定义车辆元素 {@link Car}、{@link Bike}</li>
 *     <li>定义访问者 {@link CsvExportVisitor}、{@link JsonExportVisitor}</li>
 *     <li>遍历元素并调用 {@code accept}，由访问者完成不同格式的导出</li>
 * </ul>
 */
public class VisitorDemo {

    public static void main(String[] args) {
        List<Vehicle> vehicles = List.of(
                new Car("Toyota", "Camry", 2022, 4),
                new Bike("Giant", "mountain", 26, false),
                new Car("Tesla", "Model 3", 2023, 4),
                new Bike("Xiaomi", "city", 24, true)
        );

        CsvExportVisitor csvVisitor = new CsvExportVisitor();
        JsonExportVisitor jsonVisitor = new JsonExportVisitor();

        for (Vehicle vehicle : vehicles) {
            vehicle.accept(csvVisitor);
            vehicle.accept(jsonVisitor);
        }

        System.out.println("=== CSV 导出 ===");
        System.out.print(csvVisitor.getCsv());

        System.out.println("=== JSON 导出 ===");
        System.out.print(jsonVisitor.getJson());
    }
}
