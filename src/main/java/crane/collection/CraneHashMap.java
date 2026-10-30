package crane.collection;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

/**
 * Hash表内桶数据里存储的是key的hash值
 *
 * 当list变成tree的时候，list还是维持的
 */
public class CraneHashMap<K,V> {

    // 默认的初始化容量，16
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;

    // 这是hashmap最大的数组容量，因为Java 中 int 是有符号的 32 位整型，最大正数是 【1 << 31 -1（2的31次方-1) 】
    // HashMap 要求容量必须是 2 的幂次方，因此 【 1 << 30 】 就是 Java 数组在数学和逻辑上能达到的最大 2 的幂容量上限
    static final int MAXIMUM_CAPACITY = 1 << 30;

    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    // 树化阈值
    static final int TREEIFY_THRESHOLD = 8;

    // 非树化阈值
    static final int UNTREEIFY_THRESHOLD = 6;

    // 树化，最小size阈值，size小于该值，进行resize，而不是树化，
    // 因为，树化也是需要成本的。
    // 容量太小，hash冲突本身就会很频繁，扩容比树化更合理
    static final int MIN_TREEIFY_CAPACITY = 64;

    static class Node<K,V> implements Map.Entry<K,V> {
        final int hash;
        final K key;
        V value;
        Node<K,V> next;

        Node(int hash, K key, V value, Node<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        public final K getKey()        { return key; }
        public final V getValue()      { return value; }
        public final String toString() { return key + "=" + value; }

        public final int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        public final V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public final boolean equals(Object o) {
            if (o == this)
                return true;

            return o instanceof Map.Entry<?, ?> e
                    && Objects.equals(key, e.getKey())
                    && Objects.equals(value, e.getValue());
        }
    }

    static final int hash(Object key) {
        int h;
        // hash表是用hash对table数组长度取模，只使用低位hash
        // 对象的默认hash值，取模后，则只有低位有效
        // h >>> 16 将高位移到低位，然后^，则高低位都有用
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {
            Class<?> c; Type[] ts, as; ParameterizedType p;
            if ((c = x.getClass()) == String.class)
                return c;
            if ((ts = c.getGenericInterfaces()) != null) {
                for (Type t : ts) {
                    if ((t instanceof ParameterizedType) &&
                        ((p = (ParameterizedType) t).getRawType() ==
                         Comparable.class) &&
                        (as = p.getActualTypeArguments()) != null &&
                        as.length == 1 && as[0] == c)
                        return c;
                }
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    static int compareComparables(Class<?> kc, Object k, Object x) {
        return (x == null || x.getClass() != kc ? 0 :
                ((Comparable)k).compareTo(x));
    }

    static final int tableSizeFor(int cap) {
        // 指定容量后的第一个2的次幂
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    // hash表
    transient Node<K,V>[] table;

    // map的大小
    transient int size;

    // fast-fail
    transient int modCount;

    // 扩容阈值
    int threshold;

    // 负载因子
    final float loadFactor;

    public CraneHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                                               initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                                               loadFactor);
        this.loadFactor = loadFactor;
        this.threshold = tableSizeFor(initialCapacity);
    }

    public CraneHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public CraneHashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
    }

    public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }

    /**
     * 删除一个key
     */
    public V remove(Object key) {
        Node<K,V> e;
        // 删除node并返回
        return (e = removeNode(hash(key), key, null, false, true)) == null ?
            null : e.value;
    }

    /**
     * 插入
     *
     * @param hash
     * @param key
     * @param value
     * @param onlyIfAbsent
     * @param evict
     * @return
     */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        Node<K,V>[] tab; Node<K,V> p; int n, i;
        if ((tab = table) == null || (n = tab.length) == 0)
            // 尚未初始化
            n = (tab = resize()).length;

        if ((p = tab[i = (n - 1) & hash]) == null)
            // hash表的位置上，没有元素，新建node放进去
            tab[i] = newNode(hash, key, value, null);
        else {
            //hash表的位置上有元素了
            // p在if条件里，已经被赋值为链表了
            Node<K,V> e; K k;
            if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
                // 元素已经存在，并且旧key和新key相等
                e = p;
            else if (p instanceof TreeNode)
                // 已经树化，则插入到树里
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
            else {
                for (int binCount = 0; ; ++binCount) {
                    // 遍历整个链表
                    // 1、是否存在相同的key
                    // 2、统计当前链表的元素个数
                    // 3、将新元素插入链表尾部
                    // 4、插入元素后，链表过长，将链表树化
                    if ((e = p.next) == null) {
                        // 将数据插入队尾
                        p.next = newNode(hash, key, value, null);
                        if (binCount >= TREEIFY_THRESHOLD - 1)
                            // 树化
                            treeifyBin(tab, hash);
                        break;
                    }
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        // 找到相等key的元素了，key已经存在
                        break;
                    p = e;
                }
            }

            if (e != null) {
                // 取出旧的node，将旧value返回
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    e.value = value;
                // 这是为了HashTreeNode访问元素后，调整访问次序
                afterNodeAccess(e);
                return oldValue;
            }
        }
        ++modCount;
        if (++size > threshold)
            resize();
        afterNodeInsertion(evict);
        return null;
    }

    final Node<K,V>[] resize() {
        // 先把旧table备份
        Node<K,V>[] oldTab = table;
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int oldThr = threshold;
        int newCap, newThr = 0;
        if (oldCap > 0) {
            if (oldCap >= MAXIMUM_CAPACITY) {
                // 因为已经不能扩容，但当前数组还可以继续插入，
                // 为了避免，每次插入之后，判断 size>=threshold，又继续扩容，则直接将阈值设为最大
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                     oldCap >= DEFAULT_INITIAL_CAPACITY)
                // 如果 oldCap < DEFAULT_INITIAL_CAPACITY，则不是
                newThr = oldThr << 1;
        } else if (oldThr > 0){
            // oldCap == 0，但oldThr>0；
            // 指定初始大小时，并没有初始化hash表，只是把大小保存在threshold
            // this.threshold = tableSizeFor(initialCapacity);
            // 此时，把初始化大小赋给newCap，进行table初始化
            newCap = oldThr;
        } else {
            // 没有指定初始化大小，使用默认值
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }

        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                      (int)ft : Integer.MAX_VALUE);
        }
        threshold = newThr;
        @SuppressWarnings({"rawtypes","unchecked"})
        Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
        table = newTab;
        if (oldTab != null) {
            // 旧table有数据，将旧数据，迁移到新table里
            for (int j = 0; j < oldCap; ++j) {
                Node<K,V> e;
                if ((e = oldTab[j]) != null) {
                    oldTab[j] = null;
                    if (e.next == null)
                        // 单元素，没有必要区分高位为0，还是1，一步到位了
                        // newCap - 1: newCap=16,则newCap-1 = 1111
                        newTab[e.hash & (newCap - 1)] = e;
                    else if (e instanceof TreeNode)
                        // 是红黑树
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                    else {
                        // 普通链表
                        Node<K,V> loHead = null, loTail = null;
                        Node<K,V> hiHead = null, hiTail = null;
                        Node<K,V> next;
                        do {
                            next = e.next;
                            // oldCap=16，则 oldCap=0b10000
                            // oldCap=16，table的下标为：0~0b1111
                            // 用e.hash和0b10000作与运算，正好取最高位
                            if ((e.hash & oldCap) == 0) {
                                // 最高位为0
                                if (loTail == null)
                                    // 新链表为空
                                    loHead = e;
                                else
                                    // 元素放到队尾
                                    loTail.next = e;
                                //
                                loTail = e;
                            }
                            else {
                                if (hiTail == null)
                                    hiHead = e;
                                else
                                    hiTail.next = e;
                                hiTail = e;
                            }
                        } while ((e = next) != null);

                        if (loTail != null) {
                            // 高位为0的，依旧保留在之前的位置上
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        if (hiTail != null) {
                            // 比如oldCap=15。那么之前为1的，正好是16；2==17
                            // 高位为1的，放到新的位置上
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        return newTab;
    }

    /**
     * 将hash表对应hash值的位置进行树化
     *
     * @param tab hash表
     * @param hash 待树化位置的hash值
     */
    final void treeifyBin(Node<K,V>[] tab, int hash) {
        int n,// n hash表的长度
            index; //index hash表待树化的下标
        Node<K,V> e;// 链表的表头
        if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
            // 虽然hash碰撞的厉害，但由于容量太小，还是先扩容，而不是树化
            resize();
        else if ((e = tab[index = (n - 1) & hash]) != null) {
            TreeNode<K,V> hd = null // 转换成Tree以后，还是有个链表结构，表头
                , tl = null; // 表尾
            do {
                // 将Node转成TreeNode
                TreeNode<K,V> p = replacementTreeNode(e, null);
                if (tl == null)
                    // 树结构为空，表头指针指向TreeNode
                    hd = p;
                else {
                    // 将树节点插入到队尾
                    p.prev = tl;
                    // 尾指针next指向新的节点
                    tl.next = p;
                }
                // 新节点成为最新的尾节点
                tl = p;
            } while ((e = e.next) != null);
            // 上述do-while循环，只是构建了一个TreeNode的链表
            if ((tab[index] = hd) != null)
                // 单线程肯定执行，多线程，可能出现hd==null
                // 头节点不为空，进行树化
                hd.treeify(tab);
        }
    }

    /**
     * 根据hash和key，删除node
     *
     * @param hash hash值
     * @param key 原始key
     * @param value 原始value
     * @param matchValue 是否通过value进行删除
     * @param movable true，允许将tree退化为list
     * @return 被删除的node
     */
    final Node<K,V> removeNode(int hash, Object key, Object value,
                               boolean matchValue, boolean movable) {
        Node<K,V>[] tab; // hash表指针
        Node<K,V> p; // 待删除节点的父节点
        int n, // hash表长度
            index; //
        if ((tab = table) != null // hash表不为null
            && (n = tab.length) > 0 // hash表长度大于0
            && (p = tab[
                    index = (n - 1) // 长度减1，用于定位index。n=15,index=1111
                    & hash]) != null // hash值对应的位置不为空
        ) {
            Node<K,V> node = null, // 目标node
                e; // 当前node链表位置
            K k;
            V v;
            if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
                // Node链表头元素，恰好是目标node
                node = p;
            else if ((e = p.next) != null) {
                // 遍历整个桶
                if (p instanceof TreeNode)
                    // 桶内是BR树
                    node = ((TreeNode<K,V>)p).getTreeNode(hash, key);
                else {
                    // 桶内是链表，遍历链表
                    do {
                        if (e.hash == hash &&
                            ((k = e.key) == key ||
                             (key != null && key.equals(k)))) {
                            // 相等的判断：1、hash值相等；2、key相等
                            node = e;
                            break;
                        }
                        p = e;
                    } while ((e = e.next) != null);
                }
            }

            if (node != null //找到node
                && (!matchValue // 对比value，如果matchValue为False，则直接进入下面的处理
                    || (v = node.value) == value // value ==
                    || (value != null && value.equals(v))
                    )
            ) {
                if (node instanceof TreeNode)
                    // 树节点，删除节点
                    ((TreeNode<K,V>)node).removeTreeNode(this, tab, movable);
                else if (node == p)
                    // 父节点和node一样，说明是head
                    // 如果是桶的头节点，则桶的head，改为next
                    tab[index] = node.next;
                else
                    // 正常的链表删除，父节点next指向node的next
                    p.next = node.next;
                ++modCount;
                --size;
                afterNodeRemoval(node);
                return node;
            }
        }
        return null;
    }

    Node<K,V> newNode(int hash, K key, V value, Node<K,V> next) {
        return new Node<>(hash, key, value, next);
    }

    /**
     * 将TreeNode，重新变成Node
     */
    Node<K,V> replacementNode(Node<K,V> p, Node<K,V> next) {
        return new Node<>(p.hash, p.key, p.value, next);
    }

    TreeNode<K,V> newTreeNode(int hash, K key, V value, Node<K,V> next) {
        return new TreeNode<>(hash, key, value, next);
    }

    /**
     *
     * @param p 链表节点
     * @param next 链表转树以后，还是维持链表的next
     * @return 树Node
     */
    TreeNode<K,V> replacementTreeNode(Node<K,V> p, Node<K,V> next) {
        return new TreeNode<>(p.hash, p.key, p.value, next);
    }

    void afterNodeAccess(Node<K,V> p) { }
    void afterNodeInsertion(boolean evict) { }
    void afterNodeRemoval(Node<K,V> p) { }

    static final class TreeNode<K,V> extends CraneLinkedHashMap.Entry<K,V> {
        TreeNode<K,V> parent;
        TreeNode<K,V> left;
        TreeNode<K,V> right;
        TreeNode<K,V> prev;
        boolean red;
        TreeNode(int hash, K key, V val, Node<K,V> next) {
            super(hash, key, val, next);
        }

        final TreeNode<K,V> root() {
            for (TreeNode<K,V> r = this, p;;) {
                if ((p = r.parent) == null)
                    return r;
                r = p;
            }
        }

        static <K,V> void moveRootToFront(Node<K,V>[] tab, TreeNode<K,V> root) {
            int n;
            if (root != null && tab != null && (n = tab.length) > 0) {
                int index = (n - 1) & root.hash;
                TreeNode<K,V> first = (TreeNode<K,V>)tab[index];
                if (root != first) {
                    Node<K,V> rn;
                    tab[index] = root;
                    TreeNode<K,V> rp = root.prev;
                    if ((rn = root.next) != null)
                        ((TreeNode<K,V>)rn).prev = rp;
                    if (rp != null)
                        rp.next = rn;
                    if (first != null)
                        first.prev = root;
                    root.next = first;
                    root.prev = null;
                }
                assert checkInvariants(root);
            }
        }

        final TreeNode<K,V> find(int h, Object k, Class<?> kc) {
            TreeNode<K,V> p = this;
            do {
                int ph, dir; K pk;
                TreeNode<K,V> pl = p.left, pr = p.right, q;
                if ((ph = p.hash) > h)
                    // 比当前节点小，目标值，在left
                    p = pl;
                else if (ph < h)
                    p = pr;
                else if ((pk = p.key) == k || (k != null && k.equals(pk)))
                    // key相等，返回
                    return p;
                else if (pl == null)
                    // hash值正好和当前节点一样，hash碰撞
                    // 如果左孩子为空，则肯定在右孩子里
                    p = pr;
                else if (pr == null)
                    // hash值正好和当前节点一样，hash碰撞
                    // 如果右孩子为空，则肯定在左孩子里
                    p = pl;
                else if ((kc != null ||
                          (kc = comparableClassFor(k)) != null) &&
                         (dir = compareComparables(kc, k, pk)) != 0)
                    p = (dir < 0) ? pl : pr;
                else if ((q = pr.find(h, k, kc)) != null)
                    // 实在不知道当前hash值会在哪个子孩子处，则先尝试在pr右孩子处查询
                    return q;
                else
                    // 右孩子没有，再在pl左孩子处查询
                    p = pl;
            } while (p != null);
            return null;
        }

        final TreeNode<K,V> getTreeNode(int h, Object k) {
            // 如果当前查找节点不是根节点，则强行回到根节点
            // parent != null，则不是根节点，强行回到根节点
            return ((parent != null) ? root() : this).find(h, k, null);
        }

        static int tieBreakOrder(Object a, Object b) {
            int d;
            if (a == null || b == null ||
                (d = a.getClass().getName().
                 compareTo(b.getClass().getName())) == 0)
                d = (System.identityHashCode(a) <= System.identityHashCode(b) ?
                     -1 : 1);
            return d;
        }

        /**
         * @param tab hash表
         */
        final void treeify(Node<K,V>[] tab) {
            TreeNode<K,V> root = null;// 树的root
            for (TreeNode<K,V> x = this, next // x就是当前链表队列待插入树内的node
                ; x != null;
                x = next) {
                next = (TreeNode<K,V>)x.next;
                // 先把节点的孩子置空，作为叶子节点插入
                x.left = x.right = null;
                if (root == null) {
                    // 根是黑的
                    x.parent = null;
                    x.red = false;
                    root = x;
                } else {
                    K k = x.key;
                    int h = x.hash;
                    Class<?> kc = null;
                    for (TreeNode<K,V> p = root;;) {
                        // 红黑树，是有序的，以key的hash值，来排序
                        int dir, // 2个key的大小关系
                            ph;
                        K pk = p.key;
                        if ((ph = p.hash) > h)
                            // 当前节点hash值，小于p(树内当前查找的节点)
                            dir = -1;
                        else if (ph < h)
                            dir = 1;
                        else if ((kc == null &&
                            // 如果两个Key的hash值完全一样，则进行Key的比较
                                  (kc = comparableClassFor(k)) == null) ||
                                 (dir = compareComparables(kc, k, pk)) == 0)
                            // 如果key的compare也一样，则强制比较出2个key的大小
                            dir = tieBreakOrder(k, pk);

                        TreeNode<K,V> xp = p;
                        if ((p = (dir <= 0) ? p.left : p.right) == null) {
                            // 根据dir判断x该插入左节点，还是右节点
                            // 如果当前p的孩子节点已经为空了，说明找到叶子了，可以将x插入了
                            // xp就是待插入的父节点，p此时已经变成了孩子节点为null了
                            x.parent = xp;
                            if (dir <= 0)
                                xp.left = x;
                            else
                                xp.right = x;
                            root = balanceInsertion(root, x);
                            break;
                        }
                    }
                }
            }
            moveRootToFront(tab, root);
        }

        final Node<K,V> untreeify(CraneHashMap<K,V> map) {
            Node<K,V> hd = null, tl = null;
            // 沿着双向链表遍历，退化成单向链表
            for (Node<K,V> q = this; q != null; q = q.next) {
                Node<K,V> p = map.replacementNode(q, null);
                if (tl == null)
                    hd = p;
                else
                    tl.next = p;
                tl = p;
            }
            return hd;
        }

        final TreeNode<K,V> putTreeVal(CraneHashMap<K,V> map, Node<K,V>[] tab,
                                       int h, K k, V v) {
            Class<?> kc = null;
            boolean searched = false;
            TreeNode<K,V> root = (parent != null) ? root() : this;
            for (TreeNode<K,V> p = root;;) {
                int dir, ph; K pk;
                if ((ph = p.hash) > h)
                    dir = -1;
                else if (ph < h)
                    dir = 1;
                else if ((pk = p.key) == k || (k != null && k.equals(pk)))
                    return p;
                else if ((kc == null &&
                          (kc = comparableClassFor(k)) == null) ||
                         (dir = compareComparables(kc, k, pk)) == 0) {
                    if (!searched) {
                        TreeNode<K,V> q, ch;
                        searched = true;
                        if (((ch = p.left) != null &&
                             (q = ch.find(h, k, kc)) != null) ||
                            ((ch = p.right) != null &&
                             (q = ch.find(h, k, kc)) != null))
                            return q;
                    }
                    dir = tieBreakOrder(k, pk);
                }

                TreeNode<K,V> xp = p;
                if ((p = (dir <= 0) ? p.left : p.right) == null) {
                    Node<K,V> xpn = xp.next;
                    TreeNode<K,V> x = map.newTreeNode(h, k, v, xpn);
                    if (dir <= 0)
                        xp.left = x;
                    else
                        xp.right = x;
                    xp.next = x;
                    x.parent = x.prev = xp;
                    if (xpn != null)
                        ((TreeNode<K,V>)xpn).prev = x;
                    moveRootToFront(tab, balanceInsertion(root, x));
                    return null;
                }
            }
        }

        /**
         * 把node（this）从RB树里删除
         *
         * 1、删除节点后，先判断是否节点数过少，过少，退化成链表
         *
         * 2、不能退化成链表，则调整RB
         *
         * 3、先解决节点删除后，孩子节点的父节点；
         * 3.1、叶子节点，删除后，无需新的父节点
         * 3.2、单孩子节点，删除后，直接把孩子节点交给父节点；
         * 3.3、双孩子节点，删除后，找到RB树的中序遍历的后继节点替代自己，即右孩子的最左孙子节点。
         * 3.3.1、交换2者的值
         * 3.3.2、改为删除最左孙子节点
         *
         * 4、删除的是红节点，不需要调整
         *
         * 5、删除的是黑节点，需要调整
         */
        final void removeTreeNode(CraneHashMap<K,V> map, Node<K,V>[] tab,
                                  boolean movable) {
            int n;// 桶数组长度
            if (tab == null || (n = tab.length) == 0)
                // 为空
                return;
            // 桶的index
            int index = (n - 1) & hash;
            TreeNode<K,V> first = (TreeNode<K,V>)tab[index], // 桶的first
                root = first, //tree的root，暂定
                rl;
            TreeNode<K,V> succ = (TreeNode<K,V>)next, // list后继
                pred = prev; // list的前继
            if (pred == null)
                // 没有前继，被删除的，就是桶的first节点，让后继next成为first
                tab[index] = first = succ;
            else
                // 前继的next指向next，删除当前节点
                pred.next = succ;

            if (succ != null)
                // 后继不为空，则后继的prev，指向前继
                succ.prev = pred;

            if (first == null)
                // 桶内就一个元素，删除后，桶为空，不需要继续操作了
                return;

            if (root.parent != null)
                // 桶的first不是root，找到真正的root
                root = root.root();

            if (root == null
                || (movable
                    && (root.right == null // 右节点空，则最多2个节点
                        || (rl = root.left) == null // 左节点空，最多2个节点
                        || rl.left == null) // 右节点不空，左孙子节点空，则树最多有：root、左孩子、右孩子，右孩子的2孩子
            )) {
                // 退化成链表
                tab[index] = first.untreeify(map);
                return;
            }
            // 树节点还很多
            TreeNode<K,V> p = this, pl = left, pr = right,
                replacement; // p节点删除后，p原来的位置，谁顶上
            if (pl != null && pr != null) {
                // 左右孩子都不空
                TreeNode<K,V> s = pr,// 找到右孩子的最左的节点，来替换当前被删除的节点
                    sl;
                while ((sl = s.left) != null)
                    s = sl;

                //替换节点的颜色，变成待删除节点颜色
                boolean c = s.red;
                s.red = p.red;
                p.red = c;
                // 待删除节点颜色，变成替换节点
                // 交换树位置，不交换数据.TreeMap是交换数据。因为HashMap的Node比较复杂，有pre/next等双向链表的指针关系
                TreeNode<K,V> sr = s.right;
                TreeNode<K,V> pp = p.parent;
                if (s == pr) {
                    // 替换节点正好就是删除节点的右孩子
                    /**
                     *           F()pp                              F
                     *            \                                  \
                     *             H(待删除)p                           J
                     *               \                                 \
                     *               J(待替换)pr/s   ===>                H
                     *                  \
                     *                  L()sr
                     */
                    p.parent = s;
                    s.right = p;
                }
                else {
                    /**
                     *        [ p ] (准备被删的节点)
                     *        /   ▲
                     *       /     \
                     *     ...     [ pr ] (p的右孩子)
                     *              /   ▲
                     *             /     \
                     *           [ ... ]  \
                     *            /        \
                     *          [ sp ] (s的原父亲)
                     *          /   ▲
                     *         /
                     *       [ s ]
                     *          \
                     *          sr
                     */
                    // 两者层级离的比较远
                    TreeNode<K,V> sp = s.parent;
                    if ((p.parent = sp) != null) {
                        if (s == sp.left)
                            sp.left = p;
                        else
                            sp.right = p;
                    }
                    /**
                     *        [  ] (准备被删的节点)
                     *        /   ▲
                     *       /     \
                     *     ...     [ pr ] (p的右孩子)
                     *              /   ▲
                     *             /     \
                     *           [ ... ]  \
                     *            /        \
                     *          [ sp ] (s的原父亲)
                     *          /
                     *         /
                     *       [ p ] [s]
                     */
                    if ((s.right = pr) != null) // 把之前p的右节点赋给s
                        // 如果p的右孩子不为空，则把p的右孩子的parent指向s
                        pr.parent = s;
                    // 等于是交换了s和p，及两者的右孩子，但s的左孩子还没有指向p的左孩子
                }

                // p现在已经和s交换了，左孩子为空了
                p.left = null;
                if ((p.right = sr) != null)
                    // 将s的右孩子调整为p的
                    sr.parent = p;
                if ((s.left = pl) != null)
                    // 将s左孩子调整为p的
                    pl.parent = s;

                if ((s.parent = pp) == null)// p的parent调整为s的
                    // p之前是root
                    root = s;
                else if (p == pp.left)
                    // p之前是左节点，s现在是p.parent的左节点
                    pp.left = s;
                else
                    // p之前是右节点，s现在是p.parent的右节点
                    pp.right = s;

                if (sr != null)
                    // 有右孩子，不可能有左孩子的，因为s是和p最邻近的节点，如果s有左孩子，则s会被自己左孩子替代
                    replacement = sr;
                else
                    // p的所有孩子节点都是null
                    replacement = p;
            }
            else if (pl != null)
                // 左孩子不为null，右孩子为null，删除之后，把左孩子顶上去
                replacement = pl;
            else if (pr != null)
                // 右孩子不为null，左孩子为null，删除之后，把右孩子顶上去
                replacement = pr;
            else
                // 左右孩子都是null，那直接删除了
                replacement = p;

            // 如果p有孩子节点，则直接把孩子节点顶上去，删除节点p，然后，再调整颜色
            // 如果p是叶子节点，则先调整颜色，再删除p
            if (replacement != p) {
                // p有孩子节点，删除之后，要顶上去
                TreeNode<K,V> pp =
                    replacement.parent = p.parent; // 先把p的parent赋给replacement
                if (pp == null)
                    // p就是root节点，更改root的颜色为黑
                    (root = replacement).red = false;
                else if (p == pp.left)
                    // p是左节点
                    pp.left = replacement;
                else
                    // p是右节点
                    pp.right = replacement;
                // 将节点p删掉，孩子及父节点关系，都清空
                p.left = p.right = p.parent = null;
            }

            // 删除的是红色节点，则不需要调整，是黑色节点，则需要调整
            TreeNode<K,V> r = p.red ? root : balanceDeletion(root, replacement);
            //
            if (replacement == p) {
                // 如果删除的p是叶子节点，则清理相关指针
                // p的parent，及parent的孩子节点
                TreeNode<K,V> pp = p.parent;
                p.parent = null;
                if (pp != null) {
                    if (p == pp.left)
                        pp.left = null;
                    else if (p == pp.right)
                        pp.right = null;
                }
            }
            if (movable)
                moveRootToFront(tab, r);
        }

        /**
         * 扩容时，将树的节点，拆开
         *
         * @param map map对象
         * @param tab 新的hash table
         * @param index 旧table的下标
         * @param bit 旧table的容量
         */
        final void split(CraneHashMap<K,V> map, Node<K,V>[] tab, int index, int bit) {
            TreeNode<K,V> b = this;
            TreeNode<K,V> loHead = null, loTail = null;
            TreeNode<K,V> hiHead = null, hiTail = null;
            int lc = 0, hc = 0;
            for (TreeNode<K,V> e = b, next; e != null; e = next) {
                next = (TreeNode<K,V>)e.next;
                e.next = null;
                if ((e.hash & bit) == 0) {
                    if ((e.prev = loTail) == null)
                        loHead = e;
                    else
                        loTail.next = e;
                    loTail = e;
                    ++lc;
                }
                else {
                    if ((e.prev = hiTail) == null)
                        hiHead = e;
                    else
                        hiTail.next = e;
                    hiTail = e;
                    ++hc;
                }
            }

            if (loHead != null) {
                if (lc <= UNTREEIFY_THRESHOLD)
                    tab[index] = loHead.untreeify(map);
                else {
                    tab[index] = loHead;
                    if (hiHead != null)
                        loHead.treeify(tab);
                }
            }
            if (hiHead != null) {
                if (hc <= UNTREEIFY_THRESHOLD)
                    tab[index + bit] = hiHead.untreeify(map);
                else {
                    tab[index + bit] = hiHead;
                    if (loHead != null)
                        hiHead.treeify(tab);
                }
            }
        }

        /**
         * 爷爷 (pp)                      爷爷 (pp)
         *        |                         |
         *      (p)      --【左旋】-->        r
         *     /  \                        / \
         *   A    (r)                   (p)  B（黑的）
         *       /  \                   / \
         *     (rl) B（肯定黑的）        A (rl)
         */
        static <K,V> TreeNode<K,V> rotateLeft(TreeNode<K,V> root,
                                              TreeNode<K,V> p) {
            TreeNode<K,V> r // p的右孩子，左旋后，成为新的p
                , pp // p的父节点
                , rl;
            if (p != null &&
                (r = p.right) != null // 右孩子为空，不能左旋
            ) {
                if ((rl = p.right = r.left) != null)
                    // rl不空，调整rl的parent指针，为空
                    rl.parent = p;

                if ((pp = r.parent = p.parent) // 将p的parent调整为r的parent
                    == null)
                    // 如果pp为null，说明p就是root
                    // 调整root的颜色
                    (root = r).red = false;
                else if (pp.left == p)
                    // p是左孩子，将pp的left，指向新的p
                    pp.left = r;
                else
                    // p是右孩子，将pp的right，指向新的p
                    pp.right = r;
                // 调整p和r的关系
                r.left = p;
                p.parent = r;
            }
            return root;
        }

        static <K,V> TreeNode<K,V> rotateRight(TreeNode<K,V> root,
                                               TreeNode<K,V> p) {
            TreeNode<K,V> l, pp, lr;
            if (p != null && (l = p.left) != null) {
                if ((lr = p.left = l.right) != null)
                    lr.parent = p;
                if ((pp = l.parent = p.parent) == null)
                    (root = l).red = false;
                else if (pp.right == p)
                    pp.right = l;
                else
                    pp.left = l;
                l.right = p;
                p.parent = l;
            }
            return root;
        }

        /**
         * 1、插入节点是红的
         *
         * 2、插入时root，直接变红
         *
         * 3、父节点是黑的，结束
         *
         * 4、父节点是红的，叔父节点也是红的，那么将父节点和叔父节点变黑，祖父节点变红，递归调整祖父节点。
         *
         * 5、父节点xp是红的，叔父节点是黑的。父节点是左孩子
         *
         *  5.1、插入x的是右节点，xp左旋，交换x和xp指针；变成x为左孩子
         *
         *  5.2、父节点变黑，祖父节点变红，祖父节点右旋；调整完成
         *
         * 6、父节点xp是红的，叔父节点是黑的。父节点是右孩子
         *
         *  6.1、插入x的是左节点，xp右旋，交换x和xp指针；变成x为右孩子
         * <p>
         *  6.2、父节点变黑，祖父节点变红，祖父节点左旋；调整完成
         */
        static <K,V> TreeNode<K,V> balanceInsertion(TreeNode<K,V> root,
                                                    TreeNode<K,V> x) {

            // 刚插入的节点是红色的
            x.red = true;
            for (TreeNode<K,V> xp // 父节点
                , xpp // 祖父节点
                , xppl // 左叔父，父节点的左兄弟
                , xppr // 右叔父，父节点的右兄弟
                ;;) {
                if ((xp = x.parent) == null) {
                    // 当前父节点为null，即根节点，变黑了事
                    x.red = false;
                    return x;
                }
                else if (!xp.red || (xpp = xp.parent) == null)
                    // 父节点是黑的，或者祖父节点是root，
                    // 父节点是root，但xp.red也是红，可能是中间状态，后续还会调整的。
                    return root;

                // 后续父节点的颜色，肯定是红的，因为，!xp.red，在上面的else if已经返回了
                if (xp == (xppl = xpp.left)) {
                    // 父节点左节点
                    if ((xppr = xpp.right) != null && xppr.red) {
                        // 如果右叔父也是红的
                        // 把父节点和叔父节点都变黑，祖父节点变红。等于在祖父节点处，插入了红的。
                        // 同时以祖父为root的子树，已经满足要求了
                        xppr.red = false;
                        xp.red = false;
                        xpp.red = true;
                        // 递归调整祖父节点
                        x = xpp;
                    } else {
                        // 右叔父是黑的， 如果右叔父是红的，则在上面已经处理了
                        /**
                         *          pp黑H                       xpp黑H    --【右旋】-->     黑D
                         *        /    \                       /   \                     /   \
                         *      xp红B  xppr黑J  --【左旋】-->  xp红D xppr黑J              红B   红H
                         *     /  \                          /  \                     /  \   /  \
                         *   黑A  x红D                     x红B  黑E                 黑A  黑C 黑E 黑J
                         *       /  \                     /  \
                         *     黑C 黑E                   黑A  黑C
                         *
                         * 如果开始时，是右孩子，比如【红D】，则需要先左旋。
                         */

                        if (x == xp.right) {
                            // 如果当前节点是右孩子，则将父节点xp左旋，将xp变成左孩子，x变成父节点
                            // 将xp和p调换，现在变成x左孩子，红的，xp左节点红，xppl，右节点，黑的
                            root = rotateLeft(root, x = xp);
                            xpp = (xp = x.parent) == null ? null : xp.parent;

                            // 这里为什么要左旋呢？
                            // 因为下面右旋xpp，并且要把xpp变红，如果x在右边，则x变成xpp的左孩子，那么两个都是红的了
                        }
                        //当前节点状态： x左孩子，红的；xp左节点，红；xppl，右节点，黑的
                        if (xp != null) {
                            // 将xp变黑，则xpp的左
                            xp.red = false;
                            if (xpp != null) {
                                xpp.red = true;
                                root = rotateRight(root, xpp);
                            }
                        }
                    }
                } else {
                    if (xppl != null && xppl.red) {
                        xppl.red = false;
                        xp.red = false;
                        xpp.red = true;
                        x = xpp;
                    }
                    else {
                        if (x == xp.left) {
                            root = rotateRight(root, x = xp);
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }
                        if (xp != null) {
                            xp.red = false;
                            if (xpp != null) {
                                xpp.red = true;
                                root = rotateLeft(root, xpp);
                            }
                        }
                    }
                }
            }
        }

        /**
         * 删除黑色节点后，调整树。删除root，则不需要
         *
         * 删除一个黑色节点后，树的黑色数量就不一致了，所以，需要调整
         */
        static <K,V> TreeNode<K,V> balanceDeletion(TreeNode<K,V> root,
                                                   TreeNode<K,V> x) {
            // 1、变成双黑兄弟
            // 1.1 如果右兄弟xpr是红，（此时，父节点肯定是黑的，右兄弟的2孩子也是黑的）
            //  那么则将xpr变黑，xp变红，则左旋xp。则变成xp红，x黑，xpr也黑了，是之前右兄弟的左黑孩子
            // 2、如果右黑兄弟xpr的是双黑孩子（双黑侄子），那么xpr变红，则右子树的黑少1，以xp为root的树黑少1
            //   无论此时xp的节点是什么，整棵树黑都-1，xp的左右孩子都-1了，那么递归调整xp
            // 3、如果右黑兄弟xpr的孩子，一红一黑，则需要左黑右红
            // 3.1 如果xpr的孩子是左红右黑=> 右兄弟变红，右兄弟左孩子变黑，右兄弟右旋。变成左黑右红
            // 4、将xpr和xp颜色交换，xp变黑，xpr保持xp颜色，xpr的右孩子从红变黑，黑度+1
            for (TreeNode<K,V> xp, xpl, xpr;;) {
                if (x == null || x == root)
                    // 如果是root，或者x已经是null，则返回
                    return root;
                else if ((xp = x.parent) == null) {
                    // 已经递归遍历到了root，把root改成黑色，则完成调整
                    x.red = false;
                    return x;
                }
                else if (x.red) {
                    // 删除了一个黑节点，现在遇到了一个红节点，把红色染黑，正好替补少的那个黑色的度
                    x.red = false;
                    return root;
                }
                else if ((xpl = xp.left) == x) {
                    // 待调整的节点是左孩子
                    if ((xpr = xp.right) != null && xpr.red) {
                        // x的右兄弟是红色的
                        // 将右兄弟变黑，父节点变红
                        // 再将父节点左旋，右兄弟成父节点
                        /**
                         *                [ xp: 50 黑]
                         *                 /      \
                         *       (黑高-1) /        \
                         *         [ x: 25 黑 ]   [ xpr: 80 红 ]
                         *                        /       \
                         *                 [ sl: 60 黑]  [ sr: 90 黑]
                         */
                        xpr.red = false;
                        xp.red = true;
                        /**
                         *                [ xp: 50 ] (红 ◀ 变红)
                         *                 /      \
                         *         [ x: 25 黑 ]   [ xpr: 80 ] (黑 ◀ 变黑)
                         *                        /       \
                         *                  [ sl: 60 ]  [ sr: 90 ]
                         */
                        root = rotateLeft(root, xp);
                        /**
                         *                        [ xpr: 80 ] (黑)
                         *                         /        \
                         *                [ xp: 50 ] (红)   [ sr: 90 ] (黑)
                         *                 /       \
                         *            [ x: 25 ]  [ sl: 60 ] (黑)
                         */
                        xpr = (xp = x.parent) == null ? null : xp.right;
                        /**
                         *                        [ 80 ] (黑)
                         *                       /      \
                         *              [ xp: 50 ] (红)  [ 90 ] (黑)
                         *               /      \
                         *          [ x: 25 ]  [ xpr: 60 ] (黑 ◀ xpr 成功换到黑兄弟身上)
                         */
                    }// 这个if的作用，就是将删除节点和兄弟节点都变成黑的，即“双黑兄弟”

                    if (xpr == null)
                        // TODO 右兄弟，不应该为null的
                        // 为null，则用父节点去补，假设删除的是父节点，去递归
                        x = xp;
                    else {
                        // 兄弟节点是黑的
                        TreeNode<K,V> sl = xpr.left, sr = xpr.right;
                        if ((sr == null || !sr.red) &&
                            (sl == null || !sl.red)) {
                            // 同时，黑兄弟的2个孩子都是黑的（或null），那么把兄弟变红，左右就平衡了
                            // 但父节点所在的子树，黑高少了1，那么以父节点为待删除节点，继续调整
                            xpr.red = true;
                            x = xp;
                        } else {
                            if (sr == null || !sr.red) {
                                // xpr的2孩子是左红右黑。
                                /**
                                 *                        [ xp:80 ] (黑)
                                 *                       /      \
                                 *              [ x: 50: B]   [ xpr：90：B ]
                                 *                              /        \
                                 *                        [ sl: 85: R ]    [ sr: 100:B ]
                                 *                            /      \
                                 *                         [甲：B]  [乙: B] 【sl是红的，那么甲乙肯定是黑的】
                                 */

                                if (sl != null)
                                    // sl必定是红的，如果sl是黑的，sr也是黑的，那xpr就是2个黑孩子【上面已经处理了】
                                    sl.red = false;
                                xpr.red = true;
                                root = rotateRight(root, xpr);
                                /**
                                 *                        [ xp:80 ]
                                 *                       /      \
                                 *              [ x: 50: B]   [ sl: 85: B ]
                                 *                              /        \
                                 *                        [ 甲 ]        [ xpr：90：R ]
                                 *                                         /   \
                                 *                                      [乙]    [ sr: 100:B ]
                                 */

                                xpr = (xp = x.parent) == null ?
                                    null : xp.right;
                                /**
                                 *                        [ xp:80 ]
                                 *                       /      \
                                 *              [ x: 50: B]   [ xpr: 85: B ]
                                 *                              /        \
                                 *                        [ 甲：B ]     [ 90：R ]
                                 *                                         /   \
                                 *                                   [乙：B]    [ sr: 100:B ]
                                 */
                            }

                            if (xpr != null) {
                                /**
                                 *                       [ xp:80 ]P颜色
                                 *                       /      \
                                 *             [ x: 50: B]   [ xpr: 85: B ]
                                 *                             /        \
                                 *                     [ 甲：B ]     [ sr：90：R ]
                                 *                                      /   \
                                 *                                [乙：B]   [100:B ]
                                 */
                                xpr.red = (xp == null) ? false : xp.red;
                                if ((sr = xpr.right) != null)
                                    // 刚才上面调整时，sr的指针已经错了。【if (sr == null || !sr.red)】
                                    // 这里重新指向
                                    sr.red = false;
                                /**
                                 *                       [ xp:80 ]P颜色
                                 *                       /      \
                                 *             [ x: 50: B]   [ xpr: 85: P颜色 ]
                                 *                             /        \
                                 *                     [ 甲：B ]     [ sr：90：B ]【R->B】
                                 *                                      /   \
                                 *                                [乙：B]   [100:B ]
                                 */
                            }
                            if (xp != null) {
                                // 这个地方和上面连在一起看，就是交换了xp和xpr的颜色。同时sr的颜色，从红变黑，黑的节点+1
                                xp.red = false;
                                /**
                                 *                       [ xp:80:B ]
                                 *                       /      \
                                 *             [ x: 50: B]   [ xpr: 85: P颜色 ]
                                 *                             /        \
                                 *                     [ 甲：B ]     [ sr：90：B ]【R->B，黑+1】
                                 *                                      /   \
                                 *                                [乙：B]   [100:B ]
                                 */
                                root = rotateLeft(root, xp);
                                // 左旋之后
                                /**
                                 *                        [ xpr: 85: P颜色 ]
                                 *                       /      \
                                 *            [ xp:80:B ]   [ sr：90：B ]【R->B，黑+1】
                                 *              /     \              /    \
                                 *   [ x: 50: B]     [ 甲：B ]   [乙：B]   [100:B ]
                                 *
                                 *
                                 */
                            }
                            x = root;
                        }
                    }
                } else {
                    // 被删除的右节点
                    if (xpl != null && xpl.red) {
                        // 左兄弟是红的
                        // 父节点必然是黑，左兄弟的孩子也是黑的
                        xpl.red = false;
                        xp.red = true;
                        root = rotateRight(root, xp);
                        xpl = (xp = x.parent) == null ? null : xp.left;
                        // 将左兄弟变黑，父节点变红。
                        // 父节点右旋，将左兄弟的黑右孩子，变成和x新的左兄弟，双黑兄弟
                    }
                    if (xpl == null)
                        // 左兄弟，不应该是空的，如果是空的，则调整父节点xp
                        x = xp;
                    else {
                        //
                        TreeNode<K,V> sl = xpl.left, sr = xpl.right;
                        if ((sl == null || !sl.red) &&
                            (sr == null || !sr.red)) {
                            // 左黑兄弟有2个黑孩子（或null）
                            xpl.red = true;
                            x = xp;
                            // 将左兄弟从黑变红，拉低左兄弟的黑节点层级和x匹配，则xp为根的子树，整体高，减1
                            // 两种情况：
                            // 1、xp是红的，则下一轮循环里，会将红色变成黑色，正好补足
                            // 2、xp是黑的，那继续向上调整
                        } else {
                            if (sl == null || !sl.red) {
                                if (sr != null)
                                    sr.red = false;
                                xpl.red = true;
                                root = rotateLeft(root, xpl);
                                xpl = (xp = x.parent) == null ?
                                    null : xp.left;
                            }
                            if (xpl != null) {
                                xpl.red = (xp == null) ? false : xp.red;
                                if ((sl = xpl.left) != null)
                                    sl.red = false;
                            }
                            if (xp != null) {
                                xp.red = false;
                                root = rotateRight(root, xp);
                            }
                            x = root;
                        }
                    }
                }
            }
        }

        static <K,V> boolean checkInvariants(TreeNode<K,V> t) {
            TreeNode<K,V> tp = t.parent, tl = t.left, tr = t.right,
                tb = t.prev, tn = (TreeNode<K,V>)t.next;
            if (tb != null && tb.next != t)
                return false;
            if (tn != null && tn.prev != t)
                return false;
            if (tp != null && t != tp.left && t != tp.right)
                return false;
            if (tl != null && (tl.parent != t || tl.hash > t.hash))
                return false;
            if (tr != null && (tr.parent != t || tr.hash < t.hash))
                return false;
            if (t.red && tl != null && tl.red && tr != null && tr.red)
                return false;
            if (tl != null && !checkInvariants(tl))
                return false;
            if (tr != null && !checkInvariants(tr))
                return false;
            return true;
        }
    }
}
