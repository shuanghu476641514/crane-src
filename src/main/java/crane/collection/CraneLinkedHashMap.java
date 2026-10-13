package crane.collection;

import java.util.Map;

public class CraneLinkedHashMap<K,V> extends CraneHashMap<K,V> {

    static class Entry<K,V> extends CraneHashMap.Node<K,V> {
        Entry<K,V> before, after;
        Entry(int hash, K key, V value, Node<K,V> next) {
            super(hash, key, value, next);
        }
    }

    transient CraneLinkedHashMap.Entry<K,V> head;

    transient CraneLinkedHashMap.Entry<K,V> tail;

    final boolean accessOrder;

    private void linkNodeAtEnd(CraneLinkedHashMap.Entry<K,V> p) {
        if (putMode == PUT_FIRST) {
            CraneLinkedHashMap.Entry<K,V> first = head;
            head = p;
            if (first == null)
                tail = p;
            else {
                p.after = first;
                first.before = p;
            }
        } else {
            CraneLinkedHashMap.Entry<K,V> last = tail;
            tail = p;
            if (last == null)
                head = p;
            else {
                p.before = last;
                last.after = p;
            }
        }
    }

    private void transferLinks(CraneLinkedHashMap.Entry<K,V> src,
                               CraneLinkedHashMap.Entry<K,V> dst) {
        CraneLinkedHashMap.Entry<K,V> b = dst.before = src.before;
        CraneLinkedHashMap.Entry<K,V> a = dst.after = src.after;
        if (b == null)
            head = dst;
        else
            b.after = dst;
        if (a == null)
            tail = dst;
        else
            a.before = dst;
    }

    Node<K,V> newNode(int hash, K key, V value, Node<K,V> e) {
        CraneLinkedHashMap.Entry<K,V> p =
            new CraneLinkedHashMap.Entry<>(hash, key, value, e);
        linkNodeAtEnd(p);
        return p;
    }

    Node<K,V> replacementNode(Node<K,V> p, Node<K,V> next) {
        CraneLinkedHashMap.Entry<K,V> q = (CraneLinkedHashMap.Entry<K,V>)p;
        CraneLinkedHashMap.Entry<K,V> t =
            new CraneLinkedHashMap.Entry<>(q.hash, q.key, q.value, next);
        transferLinks(q, t);
        return t;
    }

    TreeNode<K,V> newTreeNode(int hash, K key, V value, Node<K,V> next) {
        TreeNode<K,V> p = new TreeNode<>(hash, key, value, next);
        linkNodeAtEnd(p);
        return p;
    }

    TreeNode<K,V> replacementTreeNode(Node<K,V> p, Node<K,V> next) {
        CraneLinkedHashMap.Entry<K,V> q = (CraneLinkedHashMap.Entry<K,V>)p;
        TreeNode<K,V> t = new TreeNode<>(q.hash, q.key, q.value, next);
        transferLinks(q, t);
        return t;
    }

    void afterNodeRemoval(Node<K,V> e) {
        CraneLinkedHashMap.Entry<K,V> p =
            (CraneLinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
        p.before = p.after = null;
        if (b == null)
            head = a;
        else
            b.after = a;
        if (a == null)
            tail = b;
        else
            a.before = b;
    }

    void afterNodeInsertion(boolean evict) {
        CraneLinkedHashMap.Entry<K,V> first;
        if (evict && (first = head) != null && removeEldestEntry(first)) {
            K key = first.key;
            removeNode(hash(key), key, null, false, true);
        }
    }

    static final int PUT_NORM = 0;
    static final int PUT_FIRST = 1;
    static final int PUT_LAST = 2;
    transient int putMode = PUT_NORM;

    void afterNodeAccess(Node<K,V> e) {
        CraneLinkedHashMap.Entry<K,V> last;
        CraneLinkedHashMap.Entry<K,V> first;
        if ((putMode == PUT_LAST || (putMode == PUT_NORM && accessOrder)) && (last = tail) != e) {
            CraneLinkedHashMap.Entry<K,V> p =
                (CraneLinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
            p.after = null;
            if (b == null)
                head = a;
            else
                b.after = a;
            if (a != null)
                a.before = b;
            else
                last = b;
            if (last == null)
                head = p;
            else {
                p.before = last;
                last.after = p;
            }
            tail = p;
            ++modCount;
        } else if (putMode == PUT_FIRST && (first = head) != e) {
            CraneLinkedHashMap.Entry<K,V> p =
                (CraneLinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
            p.before = null;
            if (a == null)
                tail = b;
            else
                a.before = b;
            if (b != null)
                b.after = a;
            else
                first = a;
            if (first == null)
                tail = p;
            else {
                p.after = first;
                first.before = p;
            }
            head = p;
            ++modCount;
        }
    }

    public V putFirst(K k, V v) {
        try {
            putMode = PUT_FIRST;
            return this.put(k, v);
        } finally {
            putMode = PUT_NORM;
        }
    }

    public V putLast(K k, V v) {
        try {
            putMode = PUT_LAST;
            return this.put(k, v);
        } finally {
            putMode = PUT_NORM;
        }
    }

    public CraneLinkedHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        accessOrder = false;
    }

    public CraneLinkedHashMap(int initialCapacity) {
        super(initialCapacity);
        accessOrder = false;
    }

    public CraneLinkedHashMap() {
        super();
        accessOrder = false;
    }

    public CraneLinkedHashMap(int initialCapacity,
                              float loadFactor,
                              boolean accessOrder) {
        super(initialCapacity, loadFactor);
        this.accessOrder = accessOrder;
    }

    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
        return false;
    }
}
