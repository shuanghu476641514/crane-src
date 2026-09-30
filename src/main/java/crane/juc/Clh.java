package crane.juc;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Clh
 */
public class Clh {
    private  final ThreadLocal<Node> node = ThreadLocal.withInitial(Node::new);

    private final AtomicReference<Node> tail = new AtomicReference<>(new Node());

    public void lock(){
        node.get().locked = true;
        Node pre =  tail.getAndSet(node.get());
        while (pre.locked) {
            // 告诉 CPU：当前线程正在进行自旋等待
            // 本身不会让线程进入阻塞状态，也不会像 Thread.sleep() 那样真正睡眠。
            Thread.onSpinWait();
        }
    }

    public void unlock(){
        // unlock 和 lock在一个线程内，是顺序执行的
        node.get().locked = false;
        node.set(new Node());
    }

    private static class Node{
        private volatile boolean locked = false;
    }
}
