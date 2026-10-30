package crane.pattern.visitor;

/**
 * VehicleVisitor
 * 访问者接口：针对每一种具体元素声明一个 visit 重载。
 * 新增导出格式（如 XML、YAML）时只需新增一个实现类，无需改动元素类。
 */
public interface VehicleVisitor {

    void visit(Car car);

    void visit(Bike bike);
}
