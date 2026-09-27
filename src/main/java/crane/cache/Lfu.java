package crane.cache;

import java.util.HashMap;
import java.util.Map;

/**
 * Lfu
 */
public class Lfu implements CacheDemo{

    static class LfuNode extends Node{
        private int count = 1;

        LfuNode(LfuNode next, String key, String value){
            super(null, key, value);
            super.next = next;
        }
    }
    @Override
    public void add(String key, String value) {
        LfuNode frist = fMap.get(1);
        LfuNode current = new LfuNode(frist, key, value);
        if (frist != null) {
            frist.pre = current;
        }

        fMap.put(1, current);

        valueMap.put(key, current);
    }

    @Override
    public void print() {
        for (int k: fMap.keySet()){
            Node node = fMap.get(k);
            System.out.print(k+":");
            while (node != null){
                System.out.print(node.value+" => ");
                node = node.next;
            }
            System.out.println();
        }
        System.out.println("#############");
    }

    @Override
    public String get(String key) {
        LfuNode node =  valueMap.get(key);
        if (node == null){
            return null;
        }


        LfuNode old = fMap.get(node.count);
        if (old == node){
            // 头节点
            if (old.next == null){
                fMap.remove(node.count);
            }else {
                old.next.pre = null;
                fMap.put(node.count, (LfuNode)old.next);
            }
        }else {
            if (node.pre != null){
                node.pre.next = node.next;
            }

            if (node.next != null){
                node.next.pre = node.pre;
            }
        }


        LfuNode node2 =  fMap.get(node.count+1);
        node.count++;
        if (node2 == null){
            node.next = null;
            node.pre = null;
            fMap.put(node.count, node);
        }else {
            // 放到第二节点上
            node.pre = node2;
            node.next = node2.next;
            if (node2.next != null){
                node2.next.pre = node;
            }
            node2.next = node;
        }

        return node.value;
    }

    private Map<String, LfuNode> valueMap = new HashMap<>();
    private Map<Integer, LfuNode> fMap = new HashMap<>();
}
