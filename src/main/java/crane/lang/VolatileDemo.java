package crane.lang;

/**
 * VolatileDemo
 * <p>
 * volatile关键字保证2点：
 * <p>
 * 1、可见性，共享变量修改后，其他线程立刻可见，数据不放缓存内，直接读取主存里的值；
 * <p>
 * 2、禁止指令重排序
 */
public class VolatileDemo {

    String loadContext() {
        return "";
    }

    public void atomic() {
        //线程1
        boolean stop = false;
        while (!stop) {
            // doSomething();
        }

        //线程2，此时，线程1可能并不会停止循环，因为线程1可能在使用缓存内的值
        stop = true;
    }

    public void happenBefore() {
        /**线程1:*/

        String context = loadContext();   //语句1
        boolean inited = true;             //语句2

        /**
         * 线程2:
         *  <p>
         * 线程1可能会重排序，先执行语句2，再执行语句1；
         *  <p>
         * 那么当语句1执行完成后，线程的while循环就会退出；此时的init并没有完成，有风险
         *  <p>
         * 将inited声明为Volatile类型，则可以解决这个问题，保证语句1先于语句2执行。
         * */
        while (!inited) {
            // sleep
        }
        // TODO doSomethingwithconfig(context);
    }

    public static void main(String[] args) {

    }

}
