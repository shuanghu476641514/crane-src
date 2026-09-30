package crane.juc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * 简化版 AQS（AbstractQueuedSynchronizer），参考 JDK8 源码精简实现，用于学习。
 * 参考：java.util.concurrent.locks.AbstractQueuedSynchronizer（OpenJDK, GPLv2+CPE）
 *
 * <p>保留了 AQS 的全部核心机制：</p>
 * <ul>
 *     <li>CLH 变体双向队列：head/tail + Node.prev/next，CAS 入队，park/unpark 挂起唤醒</li>
 *     <li>独占模式：{@link #acquire(int)} / {@link #release(int)}</li>
 *     <li>共享模式：{@link #acquireShared(int)} / {@link #releaseShared(int)}（含 PROPAGATE 传播）</li>
 *     <li>取消逻辑：{@link #cancelAcquire(Node)}（异常/中断后的出队清理）</li>
 *     <li>条件队列：{@link ConditionObject}（await/signal，与同步队列互转）</li>
 * </ul>
 *
 * <p>与 JDK 原版的差异（仅为可读性）：</p>
 * <ul>
 *     <li>state/tail 的 CAS 用 {@link AtomicIntegerFieldUpdater}/{@link AtomicReferenceFieldUpdater}
 *         代替 sun.misc.Unsafe，语义一致</li>
 *     <li>Node.waitStatus 的个别 CAS 改成 volatile 写（竞态窗口对演示无影响，源码中已标注）</li>
 *     <li>删减了超时版（doAcquireNanos/awaitNanos）、中断版（acquireInterruptibly）等重载</li>
 * </ul>
 *
 * <p>使用方式见 {@link #main(String[])}：用 AQS 实现一个互斥锁 Mutex 和一个单槽阻塞队列。</p>
 */
public abstract class Aqs {

    // ==========================================
    // 队列节点 Node
    // ==========================================

    /**
     * 等待队列节点。waitStatus 取值：
     * <pre>
     *   CANCELLED( 1)：已取消（中断/超时），终态，等待出队清理
     *   SIGNAL   (-1)：后继节点已挂起（或即将挂起），释放时负责唤醒后继
     *   CONDITION(-2)：当前在条件队列中等待（await），不在同步队列
     *   PROPAGATE(-3)：共享模式下，唤醒需要向后传播
     *   0 初始状态：新入队节点，尚未有人为它登记 SIGNAL
     * </pre>
     */
    static final class Node {

        static final Node SHARED = new Node();
        static final Node EXCLUSIVE = null;

        static final int CANCELLED = 1;
        static final int SIGNAL = -1;
        static final int CONDITION = -2;
        static final int PROPAGATE = -3;

        volatile int waitStatus;

        volatile Node prev;
        volatile Node next;

        volatile Thread thread;

        /**
         * 条件队列中的后继（await 链）；或者 SHARED/EXCLUSIVE 标记。
         * 条件队列是单向链表，只用 nextWaiter，不用 prev/next。
         */
        Node nextWaiter;

        Node() {
        }

        Node(Thread thread, Node mode) {
            this.thread = thread;
            this.nextWaiter = mode;
        }

        Node(Thread thread, int waitStatus) {
            this.thread = thread;
            this.waitStatus = waitStatus;
        }

        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 返回前驱节点；前驱为 null 说明已被取消清理断了链，直接抛异常走 cancelAcquire
         */
        final Node predecessor() {
            Node p = prev;
            if (p == null) {
                throw new NullPointerException();
            }
            return p;
        }
    }

    // ==========================================
    // 同步状态与队首尾
    // ==========================================

    /**
     * 同步状态，语义由子类定义：互斥锁里是 0/1，可重入锁里是重入次数，
     * 信号量里是许可数，CountDownLatch 里是剩余计数。
     */
    private volatile int state;

    /**
     * 同步队列头。头节点是"已经拿到锁（或刚释放完）"的占位节点（thread 为 null），
     * 真正的等待者从头节点之后开始。
     */
    private transient volatile Node head;

    /**
     * 同步队列尾。入队只能 CAS 追加到尾部（多线程竞争点）。
     */
    private transient volatile Node tail;

    protected final int getState() {
        return state;
    }

    protected final void setState(int newState) {
        state = newState;
    }

    private static final AtomicIntegerFieldUpdater<Aqs> STATE =
            AtomicIntegerFieldUpdater.newUpdater(Aqs.class, "state");

    private static final AtomicReferenceFieldUpdater<Aqs, Node> TAIL =
            AtomicReferenceFieldUpdater.newUpdater(Aqs.class, Node.class, "tail");

    protected final boolean compareAndSetState(int expect, int update) {
        return STATE.compareAndSet(this, expect, update);
    }

    private boolean compareAndSetTail(Node expect, Node update) {
        return TAIL.compareAndSet(this, expect, update);
    }

    // ==========================================
    // 留给子类的钩子方法
    // ==========================================

    /** 独占式尝试获取，成功返回 true。子类基于 getState/CAS 实现。 */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /** 独占式尝试释放，返回值表示"完全释放"（需要唤醒后继）。 */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /** 共享式尝试获取，返回值 &lt;0 失败；=0 成功但不能传播；&gt;0 成功且应传播唤醒。 */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /** 共享式尝试释放，返回值表示是否需要唤醒等待者。 */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /** 当前线程是否独占持有（Condition.signal 前校验用）。 */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    // ==========================================
    // 入队
    // ==========================================

    /**
     * 为当前线程建节点并入队。先走一次快速路径（CAS 追加尾部），失败再进 enq 自旋。
     */
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        Node pred = tail;
        if (pred != null) {
            // 队列已初始化过，直接尝试挂到尾上
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        enq(node);
        return node;
    }

    /**
     * 自旋入队（含队列初始化）。
     *
     * <p>初始化细节：第一次入队时先 CAS 一个空的"哨兵"头节点，再挂当前节点。
     * 这样 head 永远是占位节点，acquireQueued 里"p == head 才允许 tryAcquire"
     * 的判断就天然排除了"刚来就想抢"的新节点，保证了 FIFO。</p>
     */
    private Node enq(Node node) {
        for (; ; ) {
            Node t = tail;
            if (t == null) {
                // 队列还没初始化：先建哨兵头节点。head 与 tail 同时指向它。
                if (compareAndSetTail(null, new Node())) {
                    head = tail;
                }
            } else {
                // 先连 prev，再 CAS 尾，最后补 pred.next。
                // 注意 pred.next 的赋值不在 CAS 保护内，因此从尾沿 prev 反向遍历
                // 一定完整，沿 next 正向遍历可能短暂缺节点 —— unparkSuccessor 反向
                // 扫描正是利用这一点。
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return node;
                }
            }
        }
    }

    // ==========================================
    // 独占模式
    // ==========================================

    /**
     * 独占获取模板：先 tryAcquire 快速试一把；失败则入队排队。
     * 排队期间被中断不打断等待，只记下中断标记，拿到锁后补发中断（selfInterrupt）。
     */
    public final void acquire(int arg) {
        if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
            selfInterrupt();
        }
    }

    /**
     * 排队主循环："轮到我（前驱是 head）就试拿锁，拿不到就挂起"。
     *
     * @return 等待期间是否被中断过
     */
    final boolean acquireQueued(Node node, int arg) {
        boolean interrupted = false;
        try {
            for (; ; ) {
                Node p = node.predecessor();
                // 只有前驱是 head（即自己是第一个等待者）才有资格 tryAcquire。
                // head 刚释放锁，此刻 tryAcquire 大概率成功，省一次 park。
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // 帮助 GC：旧 head 出队
                    return interrupted;
                }
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } catch (RuntimeException ex) {
            // tryAcquire 抛异常：把自己取消掉再抛出，避免留下一个永远等不到唤醒的节点
            cancelAcquire(node);
            throw ex;
        }
    }

    /**
     * 拿锁失败后的善后：把前驱置为 SIGNAL（"你释放时记得叫醒我"），然后才可以安心挂起。
     *
     * <p>为什么不能直接 park？如果前驱还没承诺唤醒我就 park，万一前驱已经释放完锁、
     * 看不到我，我就永远睡死。先把前驱的 waitStatus 改成 SIGNAL 再 park，
     * 前驱释放时一定会沿 next 找到我并 unpark。</p>
     *
     * <p>沿途跳过已取消的节点：把它们从链上摘掉，自己的 prev 直接接到最近的有效节点上。</p>
     *
     * @return true 表示可以安全挂起
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL) {
            // 前驱已承诺唤醒我，可以安全挂起
            return true;
        }
        if (ws > 0) {
            // 前驱已取消：向前跳过一串取消节点
            do {
                pred = pred.prev;
                node.prev = pred;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            // 前驱是 0 或 PROPAGATE：登记 SIGNAL，让它释放时负责叫醒我。
            // 本轮先不挂起，再自旋一轮（万一这一轮就拿到锁了呢）。
            pred.waitStatus = Node.SIGNAL; // JDK 用 CAS；演示用 volatile 写即可
        }
        return false;
    }

    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted(); // 清掉中断标记，返回是否中断过
    }

    /**
     * 独占释放模板：tryRelease 成功后唤醒 head 的后继。
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            // h != null：有人排过队；h.waitStatus != 0：后继已挂起（SIGNAL）需要唤醒。
            // waitStatus == 0 说明后继还没 park，它自己会再试一次 tryAcquire，不用管。
            if (h != null && h.waitStatus != 0) {
                unparkSuccessor(h);
            }
            return true;
        }
        return false;
    }

    /**
     * 唤醒 node 的后继（释放锁/取消清理时调用）。
     *
     * <p>反向从 tail 沿 prev 找"最靠前的有效节点"，而不是直接用 node.next：
     * 入队时 prev 先于 CAS 连好、next 后补，正向链可能短暂不完整，反向链永远可靠。</p>
     */
    private void unparkSuccessor(Node node) {
        int ws = node.waitStatus;
        if (ws < 0) {
            node.waitStatus = 0; // 清掉 SIGNAL；JDK 用 CAS，失败也无妨，演示用 volatile 写
        }

        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev) {
                if (t.waitStatus <= 0) {
                    s = t;
                }
            }
        }
        if (s != null) {
            LockSupport.unpark(s.thread);
        }
    }

    /**
     * 取消排队（tryAcquire 抛异常、await 中断后重新排队失败等场景）。
     *
     * <p>核心思路：把自己标记 CANCELLED，跳过一串前驱取消节点接到有效前驱上；
     * 如果自己是尾节点直接 CAS 摘掉；否则让有效前驱接管"唤醒我后继"的职责，
     * 接不上就主动唤醒后继（让它重试并把自己清理掉）。</p>
     */
    private void cancelAcquire(Node node) {
        if (node == null) {
            return;
        }
        node.thread = null;

        // 向前跳过已取消的节点，找到有效前驱
        Node pred = node.prev;
        while (pred.waitStatus > 0) {
            pred = pred.prev;
            node.prev = pred;
        }

        node.waitStatus = Node.CANCELLED;

        if (node == tail && compareAndSetTail(node, pred)) {
            // 自己是尾：直接摘除，pred 变成新尾，它的 next 不再有人需要
            pred.next = null; // JDK 用 CAS，失败也无害
        } else {
            // 自己不是尾：如果 pred 能接管唤醒职责（非 head、能置 SIGNAL、线程还在），
            // 就把 pred.next 直接跨过自己指向后继；否则亲自唤醒后继。
            int ws;
            if (pred != head
                    && ((ws = pred.waitStatus) == Node.SIGNAL
                        || (ws <= 0 && (pred.waitStatus = Node.SIGNAL) == Node.SIGNAL))
                    && pred.thread != null) {
                Node next = node.next;
                if (next != null && next.waitStatus <= 0) {
                    pred.next = next;
                }
            } else {
                unparkSuccessor(node);
            }
            node.next = node; // 断开自己的 next，帮助 GC
        }
    }

    private void setHead(Node node) {
        head = node;
        node.thread = null; // 头节点退化为占位节点，不再属于任何线程
        node.prev = null;
    }

    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    // ==========================================
    // 共享模式（CountDownLatch/Semaphore 走的这条路）
    // ==========================================

    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0) {
            doAcquireShared(arg);
        }
    }

    /**
     * 与独占版唯一的区别：拿到锁后不直接返回，而是 setHeadAndPropagate ——
     * 如果状态允许（tryAcquireShared 返回值 &gt;0），继续唤醒下一个共享等待者，
     * 形成链式传播（这就是 PROPAGATE 状态存在的意义）。
     */
    private void doAcquireShared(int arg) {
        Node node = addWaiter(Node.SHARED);
        boolean interrupted = false;
        try {
            for (; ; ) {
                Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null;
                        if (interrupted) {
                            selfInterrupt();
                        }
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } catch (RuntimeException ex) {
            cancelAcquire(node);
            throw ex;
        }
    }

    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    /**
     * 共享模式的唤醒与独占不同：唤醒后继后，后继若发现还能再拿（比如 Latch 计数已归零，
     * 所有人都能通过），会再次进 doReleaseShared 继续传播。因此这里是循环，
     * 且要用"head 变了才继续"做退出条件，避免刚唤醒完又重复扫一遍。
     */
    private void doReleaseShared() {
        for (; ; ) {
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    h.waitStatus = 0; // JDK 用 CAS 失败则重试；演示用 volatile 写
                    unparkSuccessor(h);
                } else if (ws == 0) {
                    // 头刚释放、后继还没登记 SIGNAL：置 PROPAGATE 保证传播不中断
                    h.waitStatus = Node.PROPAGATE;
                }
            }
            if (h == head) {
                break;
            }
        }
    }

    private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head;
        setHead(node);
        // propagate > 0：明确还有余量；或老 head 处于待唤醒/传播状态 —— 继续叫醒下一个
        if (propagate > 0 || h == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared()) {
                doReleaseShared();
            }
        }
    }

    // ==========================================
    // 条件队列（Condition）
    // ==========================================

    /**
     * 条件队列实现。要点：
     * <ul>
     *     <li>await：释放锁 → 进条件队列（CONDITION 节点，单向链表）→ park；
     *         被 signal 后节点被搬回同步队列，重新排队拿锁，拿到才返回</li>
     *     <li>signal：把条件队列第一个节点搬回同步队列（transferForSignal），
     *         不直接唤醒线程 —— 它还要在同步队列里重新竞争锁</li>
     * </ul>
     * 所以"signal 之后 await 不会立刻执行"，中间隔着一次完整的锁竞争。
     */
    public class ConditionObject {

        private static final int REINTERRUPT = 1;
        private static final int THROW_IE = -1;

        private transient Node firstWaiter;
        private transient Node lastWaiter;

        public final void await() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            Node node = addConditionWaiter();
            // 完全释放锁（可重入锁要释放全部重入次数），保存状态供返回时恢复
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            // 没进同步队列就一直睡：signal 会把节点搬回同步队列，中断则自己搬自己
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    break;
                }
            }
            // 回到同步队列后重新排队拿锁；拿到才算 await 返回
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
                interruptMode = REINTERRUPT;
            }
            if (node.nextWaiter != null) {
                // signal 正常搬走的节点 nextWaiter 已断开；还连着说明是异常路径，顺带清理
                unlinkCancelledWaiters();
            }
            if (interruptMode == THROW_IE) {
                throw new InterruptedException();
            } else if (interruptMode == REINTERRUPT) {
                selfInterrupt();
            }
        }

        public final void signal() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            Node first = firstWaiter;
            if (first != null) {
                doSignal(first);
            }
        }

        public final void signalAll() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            Node first = firstWaiter;
            if (first != null) {
                doSignalAll(first);
            }
        }

        private Node addConditionWaiter() {
            Node t = lastWaiter;
            // 顺手清理队尾已取消的节点
            if (t != null && t.waitStatus != Node.CONDITION) {
                unlinkCancelledWaiters();
                t = lastWaiter;
            }
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (t == null) {
                firstWaiter = node;
            } else {
                t.nextWaiter = node;
            }
            lastWaiter = node;
            return node;
        }

        /**
         * 把条件队列首节点搬回同步队列。只搬一个；signalAll 循环搬全部。
         */
        private void doSignal(Node first) {
            do {
                firstWaiter = first.nextWaiter;
                if (firstWaiter == null) {
                    lastWaiter = null;
                }
                first.nextWaiter = null;
                // 首节点若已取消（transfer 失败），继续尝试下一个
            } while (!transferForSignal(first) && (first = firstWaiter) != null);
        }

        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * 把节点从条件队列转到同步队列：waitStatus 从 CONDITION 改回 0，然后 enq。
         * 状态已不是 CONDITION 说明节点因中断/取消被处理过，返回 false 让调用方跳过。
         */
        final boolean transferForSignal(Node node) {
            if (node.waitStatus != Node.CONDITION) {
                return false; // JDK 用 CAS；signal 由持锁线程调用，这里读判断即可
            }
            node.waitStatus = 0;
            Node p = enq(node);
            int ws = p.waitStatus;
            if (ws > 0) {
                // 前驱已取消，等不到它唤醒，直接取消自己并 unpark 去走取消流程
                node.waitStatus = Node.CANCELLED;
                LockSupport.unpark(node.thread);
            } else if (ws != Node.SIGNAL) {
                // 替前驱登记 SIGNAL，保证锁释放时有人叫醒我
                p.waitStatus = Node.SIGNAL;
            }
            return true;
        }

        /**
         * 遍历条件队列，摘掉所有已取消的节点。只有持锁线程会调用，无需同步。
         */
        private void unlinkCancelledWaiters() {
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null) {
                        firstWaiter = next;
                    } else {
                        trail.nextWaiter = next;
                    }
                    if (next == null) {
                        lastWaiter = trail;
                    }
                } else {
                    trail = t;
                }
                t = next;
            }
        }
    }

    public ConditionObject newCondition() {
        return new ConditionObject();
    }

    /**
     * 判断节点是否已在同步队列：waitStatus 被改成非 CONDITION 即已转移；
     * 或者 prev 已经接上（enq 先连 prev）说明正在/已经入队。
     */
    final boolean isOnSyncQueue(Node node) {
        if (node.waitStatus == Node.CONDITION || node.prev == null) {
            return false;
        }
        if (node.next != null) {
            return true;
        }
        // 从尾反向找一圈兜底（next 可能还没补上）
        return findNodeFromTail(node);
    }

    private boolean findNodeFromTail(Node node) {
        for (Node p = tail; ; ) {
            if (p == node) {
                return true;
            }
            if (p == null) {
                return false;
            }
            p = p.prev;
        }
    }

    final int fullyRelease(Node node) {
        try {
            int savedState = getState();
            if (release(savedState)) {
                return savedState;
            }
            throw new IllegalMonitorStateException();
        } catch (RuntimeException ex) {
            node.waitStatus = Node.CANCELLED;
            throw ex;
        }
    }

    /**
     * await 期间检查中断：
     * <ul>
     *     <li>signal 之前中断（节点还在条件队列）：THROW_IE，抛 InterruptedException</li>
     *     <li>signal 之后中断（节点已在同步队列）：REINTERRUPT，返回后补中断标记</li>
     * </ul>
     */
    private int checkInterruptWhileWaiting(Node node) {
        if (!Thread.interrupted()) {
            return 0;
        }
        return transferAfterCancelledWait(node) ? ConditionObject.THROW_IE : ConditionObject.REINTERRUPT;
    }

    /**
     * 中断后尝试自己把节点搬进同步队列。
     * 成功说明 signal 还没处理我（我抢先了）→ 中断发生在 signal 前 → 应抛异常；
     * 失败说明 signal 已把我搬走 → 中断发生在 signal 后 → 补中断标记即可。
     */
    final boolean transferAfterCancelledWait(Node node) {
        if (node.waitStatus == Node.CONDITION) {
            node.waitStatus = 0;
            enq(node);
            return true;
        }
        // signal 与本方法并发：自旋等它把我 enq 完
        while (!isOnSyncQueue(node)) {
            Thread.yield();
        }
        return false;
    }

    // ==========================================
    // 学习用观察方法（JDK 里也有类似的监控方法）
    // ==========================================

    /** 同步队列中排队线程的快照（从 head 向后，仅供观察）。 */
    public final List<String> queuedThreadNames() {
        List<String> names = new ArrayList<>();
        for (Node p = head; p != null; p = p.next) {
            Thread t = p.thread;
            if (t != null) {
                names.add(t.getName() + "(ws=" + p.waitStatus + ")");
            }
        }
        return names;
    }

    // ==========================================
    // Demo：用 AQS 实现互斥锁 & 单槽阻塞队列
    // ==========================================

    /** 最简单的不可重入互斥锁：state 0 空闲、1 持有。 */
    static final class Mutex extends Aqs {

        @Override
        protected boolean tryAcquire(int arg) {
            return compareAndSetState(0, 1);
        }

        @Override
        protected boolean tryRelease(int arg) {
            setState(0);
            return true;
        }

        @Override
        protected boolean isHeldExclusively() {
            return getState() == 1;
        }

        void lock() {
            acquire(1);
        }

        void unlock() {
            release(1);
        }
    }

    /**
     * 单槽阻塞队列：put 在槽满时等待 notFull，take 在槽空时等待 notEmpty。
     * 演示 Condition 与同步队列之间的节点迁移。
     */
    static final class OneSlotBlockingQueue {
        private final Mutex lock = new Mutex();
        private final ConditionObject notEmpty = lock.newCondition();
        private final ConditionObject notFull = lock.newCondition();
        private Object slot;

        void put(Object e) throws InterruptedException {
            lock.lock();
            try {
                while (slot != null) {
                    System.out.println(Thread.currentThread().getName() + " 槽满，await notFull");
                    notFull.await();
                }
                slot = e;
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        Object take() throws InterruptedException {
            lock.lock();
            try {
                while (slot == null) {
                    System.out.println(Thread.currentThread().getName() + " 槽空，await notEmpty");
                    notEmpty.await();
                }
                Object e = slot;
                slot = null;
                notFull.signal();
                return e;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // ---- demo 1：互斥锁保护计数器 ----
        Mutex mutex = new Mutex();
        int[] counter = {0};
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread t = new Thread(() -> {
                for (int j = 0; j < 10000; j++) {
                    mutex.lock();
                    try {
                        counter[0]++;
                    } finally {
                        mutex.unlock();
                    }
                }
            }, "counter-" + i);
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        System.out.println("counter 最终结果: " + counter[0] + "（应为 40000）");

        // ---- demo 2：条件队列 ----
        OneSlotBlockingQueue queue = new OneSlotBlockingQueue();
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 3; i++) {
                    queue.put("item-" + i);
                    System.out.println("producer 放入 item-" + i);
                }
            } catch (InterruptedException ignored) {
            }
        }, "producer");
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 3; i++) {
                    Object e = queue.take();
                    System.out.println("consumer 取出 " + e);
                    Thread.sleep(100); // 让消费者慢一点，观察 producer 的 await
                }
            } catch (InterruptedException ignored) {
            }
        }, "consumer");
        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println("条件队列 demo 完成");
    }
}
