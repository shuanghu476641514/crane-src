package crane.juc.pool;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import jdk.internal.vm.SharedThreadContainer;

/**
 * 线程池的状态
 * <p>
 * 111 表示RUNNING （补码中它是负数，1是符号位）
 * 000 表示SHUTDOWN
 * 001 表示STOP
 * 010 TIDYING
 * 011 TERMINATED
 * <p>
 * RUNNING < SHUTDOWN < STOP < TIDYING < TERMINATED
 * <p>
 * 工作线程的流程：
 * 1、启动线程
 * 1.1、核心线程，并且是添加任务的，则顺带把任务放到线程里（少一次添加队列和取队列的操作，库的设计，太精妙了）
 * 1.2、非核心线程，顺带把任务放到线程里
 * 1.3、核心线程，但属于异常补数的，启动Thread，去任务队列拿任务
 * 2、任务运行结束，去队列获取任务；
 * 2.1、先判断池子状态，在RUNNING、SHUTDOWN 继续；否则，返回null，同时，减少线程计数；
 * 2.2、当前线程已经大于核心数，并且任务队列为空，则减少线程计数，返回null；
 * 2.3、取有任务，直接返回任务；
 * 2.4、如果当前线程数大于核心线程 或 允许核心线程超时；则超时返回null；
 * 2.5、取任务，可能会没取到，继续2.1
 * 3、拿到Runnable任务
 * 3.1、先加锁，保证执行过程中，不会被打断
 * 3.2、判断池子状态，是否继续运行任务
 * 3.3、运行任务
 * 3.4、解锁，去拿新任务
 * 4、任务获取超时，或者任务执行错误，则线程退出
 * 4.1、异常退出，要先把线程数减1
 * 4.2、
 */
public class ThreadPoolExecutor extends AbstractExecutorService {

    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    // 高三位是0 000 11111111111111111111111111111
    private static final int COUNT_MASK = (1 << COUNT_BITS) - 1;

    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS;

    private static int runStateOf(int c)     { return c & ~COUNT_MASK; }
    private static int workerCountOf(int c)  { return c & COUNT_MASK; }
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    /**
     * 因为，用高3位来表示状态，所以，低位的线程计数，不影响整个比较
     *
     * 111 表示RUNNING （补码中它是负数，1是符号位）
     * 000 表示SHUTDOWN
     * 001 表示STOP
     * 010 TIDYING
     * 011 TERMINATED
     *
     * RUNNING < SHUTDOWN < STOP < TIDYING < TERMINATED
     *
     * 线程池的这个状态，非常的巧妙，充分利用补码，最小的位数，最简单的状态判断方式
     *
     * 判断线程池是否关闭，所以只需要 c>=SHUTDOWN
     *
     * @param c 当前的线程计数及状态
     * @param s 预期状态
     *
     */
    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    private void decrementWorkerCount() {
        ctl.addAndGet(-1);
    }

    private final BlockingQueue<Runnable> workQueue;

    private final ReentrantLock mainLock = new ReentrantLock();

    private final HashSet<Worker> workers = new HashSet<>();

    private final Condition termination = mainLock.newCondition();

    private final SharedThreadContainer container;

    private int largestPoolSize;

    private long completedTaskCount;

    private volatile ThreadFactory threadFactory;

    private volatile RejectedExecutionHandler handler;

    private volatile long keepAliveTime;

    /**
     * 是否运行核心线程超时
     */
    private volatile boolean allowCoreThreadTimeOut;

    private volatile int corePoolSize;

    private volatile int maximumPoolSize;

    private static final RejectedExecutionHandler defaultHandler =
        new AbortPolicy();

    private static final RuntimePermission shutdownPerm =
        new RuntimePermission("modifyThread");

    private final class Worker
        extends AbstractQueuedSynchronizer
        implements Runnable
    {

        private static final long serialVersionUID = 6138294804551838833L;

        @SuppressWarnings("serial")
        final Thread thread;

        @SuppressWarnings("serial")
        Runnable firstTask;

        volatile long completedTasks;

        Worker(Runnable firstTask) {
            setState(-1);
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }

        public void run() {
            runWorker(this);
        }

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    private void advanceRunState(int targetState) {
        // 设置线程池状态，如果已经是当前状态之后的状态，则不需要继续操作了

        for (;;) {
            int c = ctl.get();
            //
            if (runStateAtLeast(c, targetState) ||
                ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                // 利用CAS 乐观锁，失败则继续重试
                break;
        }
    }

    final void tryTerminate() {
        for (;;) {
            int c = ctl.get();
            if (isRunning(c) || // 线程池还在运行
                runStateAtLeast(c, TIDYING) || // 线程池已经处于 TIDYING 或 TERMINATED 状态了，不需要重复处理
                (runStateLessThan(c, STOP) && ! workQueue.isEmpty()) // 处于 SHUTDOWN 状态但队列里还有未处理完的任务，必须等队列清空
            )
                return;
            if (workerCountOf(c) != 0) {
                // 还有活着的线程，中断一个线程，让线程退出。线程退出时，还会tryTerminate，继续执行中断，直到所有线程都退出
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // 尝试CAS 状态
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        terminated();
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0));

                        termination.signalAll();
                        // 关闭线程组
                        container.close();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }

        }
    }

    private void checkShutdownAccess() {
        // 安全管理器（SecurityManager）的权限校验，确保当前调用线程有权关闭线程池，并且有权修改/中断线程池中的所有工作线程。

        @SuppressWarnings("removal")
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            // 失败，抛SecurityException
            security.checkPermission(shutdownPerm);
            for (Worker w : workers)
                security.checkAccess(w.thread);
        }
    }

    private void interruptWorkers() {

        for (Worker w : workers)
            w.interruptIfStarted();
    }

    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                if (!t.isInterrupted() && w.tryLock()) {
                    //  w.tryLock() 确保，只向空闲堵塞线程发送中断
                    // 忙的线程，在任务运行结束，getTask的时候，会判断状态，会自己退出
                    // 同时，线程池里的线程，在感知状态已经stop后，会向当前线程发中断，任务也会感知到
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    void onShutdown() {
    }

    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            // drainTo之后，可能还会有新的数据加入
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

    /**
     * 添加任务
     *
     * firstTask core线程，才有；非core线程，或者，中途修改核心线程数了，也会null
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (int c = ctl.get();;) {

            if (runStateAtLeast(c, SHUTDOWN) // 线程池开始关闭
                && (runStateAtLeast(c, STOP)
                    || firstTask != null // 已经SHUTDOWN，并且，这是新的task
                    || workQueue.isEmpty()) //
            )
                // 不能再接收新任务了
                return false;

            for (;;) {
                if (workerCountOf(c)
                    >= ((core ? corePoolSize : maximumPoolSize) & COUNT_MASK))//& COUNT_MASK,防止越位
                    // 线程数已经够多了
                    return false;
                if (compareAndIncrementWorkerCount(c))
                    // 工作线程的个数新增成功，则跳出循环
                    // 新增失败，则继续for循环，判断线程数是否已经够了，或者继续新增线程数
                    break retry;
                c = ctl.get();
                if (runStateAtLeast(c, SHUTDOWN))
                    // 创建线程的过程中，线程池关闭了，跳到最开始的地方，关闭任务
                    continue retry;
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {

                    int c = ctl.get();

                    if (isRunning(c) ||
                        (runStateLessThan(c, STOP) && firstTask == null)) {
                        if (t.getState() != Thread.State.NEW)
                            throw new IllegalThreadStateException();
                        workers.add(w);
                        workerAdded = true;
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    // 启动线程
                    container.start(t);
                    workerStarted = true;
                }
            }
        } finally {
            if (! workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                workers.remove(w);
            decrementWorkerCount();
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * @param completedAbruptly 是否意外终止
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        if (completedAbruptly)
            // 异常退出，要清理线程个数
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 统计任务数
            completedTaskCount += w.completedTasks;
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        // 线程退出了，常识关闭池子，此时，也可能池子还在运行中。
        // 也可能此线程是池子的最后一个线程
        tryTerminate();

        int c = ctl.get();
        if (runStateLessThan(c, STOP)) {
            // 如果线程池还在 RUNNING 或 SHUTDOWN 状态
            if (!completedAbruptly) {
                // 正常退出，允许核心线程超时，很有可能所有的核心线程都退出了
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && ! workQueue.isEmpty())
                    // 队列非空，不允许核心线程都退出
                    min = 1;
                if (workerCountOf(c) >= min)
                    return;
            }
            // 异常退出的线程，要拉起来
            // 所有核心线程都退出了，但任务队列有任务，要拉起线程
            addWorker(null, false);
        }
    }

    private Runnable getTask() {
        boolean timedOut = false;

        for (;;) {
            int c = ctl.get();

            if (runStateAtLeast(c, SHUTDOWN) // 池子已SHUTDOWN
                && (runStateAtLeast(c, STOP) || workQueue.isEmpty()) // 池子已经STOP，或者SHUTDOWN && 池子空了
            ) {
                // 减少线程池的线程数量
                decrementWorkerCount();
                // 返回null，让上层线程退出
                return null;
            }

            int wc = workerCountOf(c);

            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            if ((wc > maximumPoolSize // 线程数量已经超过最大线程
                    || (timed && timedOut)) // 线程任务超时，空闲线程可以回收
                && (wc > 1 || workQueue.isEmpty()) //安全保障，如果任务池非空，则要至少保留一个线程
            ) {
                // 假设wc=2，此时，2个线程，同时来尝试把count-1，但只有一个线程能成功，失败的线程会继续for循环
                // 典型的CAS+循环的设计
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            // 线程池，并没有属性把线程分为core和非core。
            // 只是在线程池线程大于核心线程的时候，加入取任务的
            try {
                Runnable r = timed ?
                    workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                    workQueue.take();
                if (r != null)
                    // 拿到待执行的任务
                    return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        // 释放初始的 -1 状态，允许中断
        w.unlock();
        // 默认线程是异常退出
        boolean completedAbruptly = true;
        try {
            while (task != null || (task = getTask()) != null) {
                // 任务正在执行的时候，加上锁，每个线程一个锁
                w.lock();

                if ((runStateAtLeast(ctl.get(), STOP) ||
                     (Thread.interrupted() &&
                      runStateAtLeast(ctl.get(), STOP))) &&
                    !wt.isInterrupted())
                    wt.interrupt();
                try {
                    beforeExecute(wt, task);
                    try {
                        task.run();
                        afterExecute(task, null);
                    } catch (Throwable ex) {
                        afterExecute(task, ex);
                        throw ex;
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            // 线程是正常退出
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }

    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
            maximumPoolSize <= 0 ||
            maximumPoolSize < corePoolSize ||
            keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;

        String name = Objects.toIdentityString(this);
        this.container = SharedThreadContainer.create(name);
    }

    public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();

        int c = ctl.get();
        if (workerCountOf(c) < corePoolSize) {
            // 如果没有到核心线程数，则新建线程
            if (addWorker(command, true))
                // 创建core线程成功，任务已启动
                return;
            c = ctl.get();
        }
        if (isRunning(c) && workQueue.offer(command)) {
            // 运行中，任务队列未满
            int recheck = ctl.get();
            if (! isRunning(recheck) && remove(command))
                // 池子要关闭了，把任务从队列里删掉
                reject(command);
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        else if (!addWorker(command, false))
            reject(command);
    }

    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            //
            checkShutdownAccess();
            // 设置状态
            advanceRunState(SHUTDOWN);
            // 中断所有空闲线程，并且等待正在执行的任务结束
            interruptIdleWorkers();
            onShutdown();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
    }

    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(STOP);
            // 中断所有线程，即使线程正在执行任务
            interruptWorkers();
            // 将所有未执行的任务返回
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }

    public boolean isShutdown() {
        return runStateAtLeast(ctl.get(), SHUTDOWN);
    }

    boolean isStopped() {
        return runStateAtLeast(ctl.get(), STOP);
    }

    public boolean isTerminating() {
        int c = ctl.get();
        return runStateAtLeast(c, SHUTDOWN) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        // 外部线程等待线程池结束
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            while (runStateLessThan(ctl.get(), TERMINATED)) {
                if (nanos <= 0L)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
            return true;
        } finally {
            mainLock.unlock();
        }
    }

    @Deprecated(since="9", forRemoval=true)
    @SuppressWarnings("removal")
    protected void finalize() {}

    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();
        else if (delta > 0) {

            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize &&
            addWorker(null, true);
    }

    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            addWorker(null, true);
        else if (wc == 0)
            addWorker(null, false);
    }

    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }

    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value)
                interruptIdleWorkers();
        }
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0)
            interruptIdleWorkers();
    }

    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate();
        return removed;
    }

    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {

            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    q.remove(r);
        }

        tryTerminate();
    }

    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {

            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers)
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String runState =
            isRunning(c) ? "Running" :
            runStateAtLeast(c, TERMINATED) ? "Terminated" :
            "Shutting down";
        return super.toString() +
            "[" + runState +
            ", pool size = " + nworkers +
            ", active threads = " + nactive +
            ", queued tasks = " + workQueue.size() +
            ", completed tasks = " + ncompleted +
            "]";
    }

    protected void beforeExecute(Thread t, Runnable r) { }

    protected void afterExecute(Runnable r, Throwable t) { }

    protected void terminated() { }

    public static class CallerRunsPolicy implements RejectedExecutionHandler {

        public CallerRunsPolicy() { }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    public static class AbortPolicy implements RejectedExecutionHandler {

        public AbortPolicy() { }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                                                 " rejected from " +
                                                 e.toString());
        }
    }

    public static class DiscardPolicy implements RejectedExecutionHandler {

        public DiscardPolicy() { }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    public static class DiscardOldestPolicy implements RejectedExecutionHandler {

        public DiscardOldestPolicy() { }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
