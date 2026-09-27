package crane.cache;

/**
 * CacheDemo
 */
public interface CacheDemo {
   default void test(String key){
        add(key, key);
    }

    void add(String key, String value);

    void print();

    String get(String key);

    class Node{
        public Node pre;
        public String value;
        public String key;
        public Node next;

        public Node(){
            this.pre = null;
            this.next = null;
            this.value = null;
        }

        public Node(Node pre, String key, String value){
            this.pre = pre;
            this.key = key;
            this.value = value;
            this.next = null;
        }
    }

    public static void main(String[] args) {
        CacheDemo lru = new Lfu();

        lru.test("A");
        lru.test("B");
        lru.get("A");

        lru.print();
        lru.test("C");
        lru.get("B");
        lru.get("B");
        lru.print();
    }
}
