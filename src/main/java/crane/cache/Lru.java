package crane.cache;

import java.util.HashMap;
import java.util.Map;

/**
 * Lru
 */
public class Lru implements CacheDemo{

    public void add(String key, String value){
        Node current = new Node(tail, key, value);
        cache.put(key, current);
        tail.next = current;
        tail = current;
    }

    public void print(){
        Node current = head.next;
        while (current != null) {
            System.out.print("=>"+current.value);
            current = current.next;
        }
        System.out.println();
    }

    public String get(String key){
        Node value = cache.get(key);
        if (value == null){
            return null;
        }

        if (tail != value){
            // 不是尾节点
            value.pre.next = value.next;
            value.next.pre = value.pre;
            tail.next = value;
            value.pre = tail;
            value.next = null;
            tail = value;
        }

        return value.value;
    }

    private Map<String, Node> cache = new HashMap<>();
    // 空
    private Node head = new Node();
    private Node tail = head;


}
