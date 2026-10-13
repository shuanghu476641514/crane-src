package crane.collection.demo;

import crane.collection.CraneHashMap;
import crane.collection.CraneLinkedHashMap;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 观察 {@link crane.collection.CraneHashMap#put(Object, Object)} 与 {@link crane.collection.CraneLinkedHashMap}
 * 的 put 链各条执行路径下的内部结构变化。
 * 副本里只有 put（没有 get/entrySet），所以用反射读取内部 table/head 展示桶结构和链表序，
 * 并同步把相同数据 put 进 java.util.HashMap / java.util.LinkedHashMap 做结果比对。
 */
public class HashMapPutDemo {

    /** hash 固定的 key，制造哈希碰撞，把一个桶从链表逼成红黑树 */
    static class FixedHashKey implements Comparable<FixedHashKey> {
        final String name;
        final int hash;

        FixedHashKey(String name) { this(name, 1); }

        FixedHashKey(String name, int hash) {
            this.name = name;
            this.hash = hash;
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object o) {
            return o instanceof FixedHashKey k && k.name.equals(name);
        }

        @Override public int compareTo(FixedHashKey o) { return name.compareTo(o.name); }

        @Override public String toString() { return name + "(h=" + hash + ")"; }
    }

    public static void main(String[] args) throws Exception {
        HashMap<String, String> map = new HashMap<>(6);
        map.clear();
        basicPut();
        collisionTreeifyAndPutTreeVal();
        resizeSplitTreeBin();
        linkedOrderAndAccessOrder();
        linkedTreeifyKeepOrder();
        lruEviction();
    }

    /** 空桶 newNode / 命中旧 key 覆盖 / null key */
    static void basicPut() throws Exception {
        System.out.println("========== 1. 基本路径：懒初始化 / 空桶 newNode / key 覆盖 / null key ==========");
        CraneHashMap<String, Integer> map = new CraneHashMap<>();
        java.util.HashMap<Object, Object> jdk = new java.util.HashMap<>();

        System.out.println("put(\"a\", 1)  返回 " + map.put("a", 1) + "   ← 新 key；table 为 null，这里才第一次 resize() 分配");
        jdk.put("a", 1);
        System.out.println("put(\"a\", 2)  返回 " + map.put("a", 2) + "   ← 命中桶头已有 key，走 afterNodeAccess 后覆盖");
        jdk.put("a", 2);
        System.out.println("put(null, 0) 返回 " + map.put(null, 0) + "   ← null key 的 hash 固定为 0");
        jdk.put(null, 0);
        dump(map);
        checkSameAsJdk(map, jdk);
    }

    /** 链表追加 → treeifyBin 树化 → putTreeVal */
    static void collisionTreeifyAndPutTreeVal() throws Exception {
        System.out.println("========== 2. 碰撞 → 链表追加 → treeifyBin 树化 → putTreeVal ==========");
        // 初始容量 64 ≥ MIN_TREEIFY_CAPACITY(64)，链表满 8 个后再 put 直接触发树化（而不是先扩容）
        CraneHashMap<FixedHashKey, Integer> map = new CraneHashMap<>(64, 0.2f);
        java.util.HashMap<Object, Object> jdk = new java.util.HashMap<>();

        for (int i = 1; i <= 8; i++) {
            FixedHashKey key = new FixedHashKey("k" + i);
            map.put(key, i);
            jdk.put(key, i);
        }
        System.out.println("---- 8 个同 hash key 全在 bucket[1]，还是链表（依次挂在 next 上） ----");
        dump(map);

        FixedHashKey k9 = new FixedHashKey("k9");
        System.out.println("put(k9) 返回 " + map.put(k9, 9) + "   ← 桶里已有 8 个节点：binCount>=7 触发 treeifyBin → treeify");
        jdk.put(k9, 9);
        dump(map);

        FixedHashKey k10 = new FixedHashKey("k10");
        map.put(k10, 10);
        jdk.put(k10, 10);
        // 与树中已有 k3 equals（不同实例、同名）
        FixedHashKey k3 = new FixedHashKey("k3");
        System.out.println("put(k3→33) 返回 " + map.put(k3, 33) + "   ← 桶头已是 TreeNode，走 putTreeVal：树内找到旧 k3 覆盖");
        jdk.put(k3, 33);
        dump(map);
        checkSameAsJdk(map, jdk);
    }

    /** 扩容 resize：TreeNode.split 拆分红黑树桶 / untreeify 退化回链表 */
    static void resizeSplitTreeBin() throws Exception {
        System.out.println("========== 3. 扩容 resize：TreeNode.split 拆分红黑树桶 / untreeify 退化回链表 ==========");
        CraneHashMap<FixedHashKey, Integer> map = new CraneHashMap<>(64, 0.2f);  // 首次 resize 后 threshold = 12
        java.util.HashMap<Object, Object> jdk = new java.util.HashMap<>();

        for (int i = 1; i <= 11; i++) {
            FixedHashKey key = new FixedHashKey("k" + i);   // hash=1 → bucket[1]，k9 时树化，k10/k11 走 putTreeVal
            map.put(key, i);
            jdk.put(key, i);
        }
        FixedHashKey m1 = new FixedHashKey("m1", 65);       // 65 & 63 == 1，和 k1..k11 落在同一个桶
        map.put(m1, 101);
        jdk.put(m1, 101);
        System.out.println("---- 12 个 key 全在 bucket[1]，是一棵红黑树 ----");
        dump(map);

        FixedHashKey m2 = new FixedHashKey("m2", 65);
        System.out.println("put(m2) 返回 " + map.put(m2, 102) + "   ← ++size=13 > threshold=12：putVal 末尾触发 resize(128)");
        jdk.put(m2, 102);
        // 扩容时按 (hash & 64) 拆分：hash=1 的 11 个留在 bucket[1]（>6，重新 treeify）；
        // hash=65 的 m1、m2 挪到 bucket[65]，只有 2 个 ≤ UNTREEIFY_THRESHOLD(6)，untreeify 退化回链表
        dump(map);
        checkSameAsJdk(map, jdk);
    }

    /** CraneLinkedHashMap：before/after 双向链表保持插入序；putFirst 置顶；accessOrder 覆盖时挪到尾 */
    static void linkedOrderAndAccessOrder() throws Exception {
        System.out.println("========== 4. CraneLinkedHashMap：插入序 / putFirst / accessOrder ==========");
        CraneLinkedHashMap<String, Integer> map = new CraneLinkedHashMap<>();
        java.util.LinkedHashMap<Object, Object> jdk = new java.util.LinkedHashMap<>();

        map.put("a", 1); jdk.put("a", 1);
        map.put("b", 2); jdk.put("b", 2);
        map.put("c", 3); jdk.put("c", 3);
        System.out.println("---- 插入 a,b,c：newNode → linkNodeAtEnd 挂到链表尾，head 沿 after 走即插入序 ----");
        dumpLinked(map);

        System.out.println("put(a→11) 返回 " + map.put("a", 11) + "   ← 插入序模式：afterNodeAccess 不挪动位置");
        jdk.put("a", 11);
        dumpLinked(map);

        System.out.println("putFirst(b→22) 返回 " + map.putFirst("b", 22) + "   ← putMode=PUT_FIRST：afterNodeAccess 把 b 挪到链表头");
        jdk.putFirst("b", 22);
        dumpLinked(map);
        checkSameAsJdk(map, jdk);

        CraneLinkedHashMap<String, Integer> access = new CraneLinkedHashMap<>(16, 0.75f, true);
        java.util.LinkedHashMap<Object, Object> jdkAccess = new java.util.LinkedHashMap<>(16, 0.75f, true);
        access.put("a", 1); jdkAccess.put("a", 1);
        access.put("b", 2); jdkAccess.put("b", 2);
        access.put("c", 3); jdkAccess.put("c", 3);
        System.out.println("---- accessOrder=true：put 覆盖旧 key 触发 afterNodeAccess，把 a 挪到链表尾 ----");
        System.out.println("put(a→11) 返回 " + access.put("a", 11));
        jdkAccess.put("a", 11);
        dumpLinked(access);
        checkSameAsJdk(access, jdkAccess);
    }

    /** 树化后 before/after 链表序不乱：replacementTreeNode → transferLinks 原位接管 */
    static void linkedTreeifyKeepOrder() throws Exception {
        System.out.println("========== 5. CraneLinkedHashMap：树化时 transferLinks 保持链表序 ==========");
        CraneLinkedHashMap<FixedHashKey, Integer> map = new CraneLinkedHashMap<>(64, 0.2f);
        java.util.LinkedHashMap<Object, Object> jdk = new java.util.LinkedHashMap<>(64, 0.2f);
        for (int i = 1; i <= 9; i++) {
            FixedHashKey key = new FixedHashKey("k" + i);
            map.put(key, i);
            jdk.put(key, i);
        }
        System.out.println("---- k9 触发 treeifyBin：每个链表节点经 replacementTreeNode 换成 TreeNode，transferLinks 保持 before/after 序 ----");
        dump(map);
        dumpLinked(map);
        checkSameAsJdk(map, jdk);
    }

    /** afterNodeInsertion → removeNode：removeEldestEntry 淘汰最老节点（LRU 的核心一环） */
    static void lruEviction() throws Exception {
        System.out.println("========== 6. afterNodeInsertion → removeNode：淘汰最老节点（LRU） ==========");
        int[] count = {0};
        CraneLinkedHashMap<String, Integer> map = new CraneLinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                return count[0] > 3;
            }
        };
        java.util.LinkedHashMap<Object, Object> jdk = new java.util.LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<Object, Object> eldest) {
                return size() > 3;
            }
        };
        for (String k : new String[]{"a", "b", "c", "d", "e"}) {
            count[0]++;
            System.out.println("put(" + k + ", " + count[0] + ")");
            map.put(k, count[0]);
            jdk.put(k, count[0]);
        }
        System.out.println("---- 超过 3 个：afterNodeInsertion 调 removeNode 摘掉链表头（最老），afterNodeRemoval 同步摘 before/after 链 ----");
        dumpLinked(map);
        checkSameAsJdk(map, jdk);
    }

    /* ---------------- 以下为反射辅助：副本没有 get/entrySet，只能这样看内部结构 -------------- */

    static void dump(Object map) throws Exception {
        Object[] table = (Object[]) field(map.getClass(), "table").get(map);
        int size = field(map.getClass(), "size").getInt(map);
        int threshold = field(map.getClass(), "threshold").getInt(map);
        String len = (table == null) ? "null（未分配）" : String.valueOf(table.length);
        System.out.printf("size=%d, threshold=%d, table.length=%s%n", size, threshold, len);
        if (table == null) return;
        for (int i = 0; i < table.length; i++) {
            Object head = table[i];
            if (head == null) continue;
            StringBuilder sb = new StringBuilder(String.format("bucket[%d]%s:", i, isTreeNode(head) ? "（红黑树）" : "（链表）"));
            for (Object e = head; e != null; e = field(e.getClass(), "next").get(e)) {
                sb.append(' ').append(field(e.getClass(), "key").get(e))
                  .append('=').append(field(e.getClass(), "value").get(e));
                if (isTreeNode(e)) {
                    Object parent = field(e.getClass(), "parent").get(e);
                    boolean red = field(e.getClass(), "red").getBoolean(e);
                    sb.append(parent == null ? "[根" : "[").append(red ? "红" : "黑").append(']');
                }
            }
            System.out.println(sb);
        }
        System.out.println();
    }

    /** 把副本内部结构收集成 Map，与 JDK 的 put 结果比对 */
    static void checkSameAsJdk(Object ours, java.util.Map<Object, Object> jdk) throws Exception {
        Map<Object, Object> actual = new java.util.HashMap<>();
        Object[] table = (Object[]) field(ours.getClass(), "table").get(ours);
        if (table != null) {
            for (Object head : table) {
                for (Object e = head; e != null; e = field(e.getClass(), "next").get(e)) {
                    actual.put(field(e.getClass(), "key").get(e), field(e.getClass(), "value").get(e));
                }
            }
        }
        if (!actual.equals(jdk)) {
            throw new AssertionError("与 JDK 结果不一致!\n副本 = " + actual + "\nJDK  = " + jdk);
        }
        System.out.println("(校验通过：与 JDK 的 put 结果一致)\n");
    }

    /** 打印 before/after 双向链表序（LinkedHashMap 的 head 沿 after 走） */
    static void dumpLinked(Object map) throws Exception {
        Object head = field(map.getClass(), "head").get(map);
        StringBuilder sb = new StringBuilder("head");
        for (Object e = head; e != null; e = field(e.getClass(), "after").get(e)) {
            sb.append(" → ").append(field(e.getClass(), "key").get(e))
              .append('=').append(field(e.getClass(), "value").get(e));
        }
        System.out.println(sb);
        System.out.println();
    }

    static boolean isTreeNode(Object node) {
        return node.getClass().getSimpleName().equals("TreeNode");
    }

    static Field field(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
