package crane.pattern.visitor;

/**
 * Vehicle
 * 访问者模式中的「元素」接口：车辆。
 * 每个具体元素通过 {@link #accept(VehicleVisitor)} 将自身交给访问者，实现双分派。
 */
public interface Vehicle {

    /**
     * 接受一个访问者，访问者会回调对应的 visit 方法。
     *
     * @param visitor 访问者
     */
    void accept(VehicleVisitor visitor);
}
