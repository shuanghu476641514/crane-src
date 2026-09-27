package crane.collection;

import java.util.TreeMap;

public class RedBlackTreeDemo {

    /**
     * Node color enum. Easier to read than boolean when debugging.
     */
    enum Color {
        RED, BLACK
    }

    private static final Color RED = Color.RED;
    private static final Color BLACK = Color.BLACK;

    // ANSI 终端控制颜色字符
    private static final String COLOR_RESET = "\u001B[0m";
    private static final String COLOR_RED = "\u001B[31;1m";    // 亮红色
    private static final String COLOR_BLACK = "\u001B[37;1m";  // 亮白/灰黑（黑背景下更清晰）

    static class Node {

        int key;
        /**
         * {@link TreeMap}里的color是boolean，更简洁
         */
        Color color;
        /**
         * {@link TreeMap} 里用的是继承Map.Entry 的自定义Entry
         */
        Node left, right, parent;

        Node(int key, Color color, Node parent) {
            this.key = key;
            this.color = color;
            this.parent = parent;
        }
    }

    private Node root;

    // ==========================================
    // 插入逻辑
    // ==========================================
    public void insert(int key) {
        System.out.println("===> 尝试插入节点: " + key);

        Node current = root;
        Node parent = null;

        while (current != null) {
            parent = current;
            if (key < current.key) {
                current = current.left;
            } else if (key > current.key) {
                current = current.right;
            } else {
                System.out.println("节点 " + key + " 已存在，跳过插入。");
                return;
            }
        }

        // 默认新插入的节点是RED
        Node newNode = new Node(key, RED, parent);

        if (parent == null) {
            root = newNode;
        } else if (key < parent.key) {
            parent.left = newNode;
        } else {
            parent.right = newNode;
        }

        fixAfterInsertion(newNode);
        //  System.out.println("\n[插入 " + key + " 完成后的最终树结构]:");

    }

    private void fixAfterInsertion(Node x) {
        // 因为插入的是RED节点，则需要判断，父节点是否是RED，是RED，则连续2个红色，需要调整
        while (x != null && x != root && x.parent.color == RED) {
            // 调整分左、右，是镜像的
            if (parentOf(x) == leftOf(parentOf(parentOf(x)))) {
                // 插入节点的父节点，是左孩子
                Node y = rightOf(parentOf(parentOf(x)));
                // 找到右叔父
                if (colorOf(y) == RED) {
                    // 右叔父是红的
                    /**
                     *          60(B)
                     *          /  \
                     *      50(R)   70(R)
                     *        /
                     *      40(R)【当前插入】
                     * 将父节点和叔叔节点都变黑，黑节点度+1
                     * 将爷爷节点变红，黑节点度-1，爷爷为root的子树，黑节点的度没有改变，颜色也满足了要求
                     *
                     * 爷爷从黑变成红，等于爷爷是个新插入的红节点，递归修改爷爷
                     *          60(R)
                     *          /  \
                     *      50(B)   70(B)
                     *        /
                     *      40(R)【当前插入】
                     */
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    // 右叔父是黑的，并且当前插入节点是右孩子
                    if (x == rightOf(parentOf(x))) {
                        /**
                         *             60(B)
                         *           /       \
                         *        50(R)        70(B)
                         *        /    \         / \
                         *      丙   [当前]40(R) 丁  戊
                         *              / \
                         *             甲  乙
                         * 如果当前待调整的是右孩子，则把父节点左旋
                         *             60(B)
                         *           /       \
                         *        40(R)      70(B)
                         *        /    \      / \
                         * [当前]50(R)  乙    丁  戊
                         *     /  \
                         *    丙  甲
                         *   将父节点左旋以后，父节点红色，变成了待调整的左孩子
                         */
                        x = parentOf(x);
                        printTree("开始左旋");
                        rotateLeft(x);
                        printTree("结束左旋");
                    }
                    /**
                     *            60(B)
                     *           /     \
                     *        50(R)     70(B)
                     *        /    \    / \
                     *  [当前]40(R) 丙   丁   戊
                     *      / \
                     *     甲  乙
                     *  将父节点变黑，左子树黑节点度+1，将爷爷节点变红，整颗子树黑节点度正常。但右子树黑节点度-1
                     *  将爷爷节点右旋，左子树黑节点度不变，之前的右子树，因为当前父节点是黑，变成新的root，黑节点度+1，正好正常
                     *
                     *            50(B)
                     *           /     \
                     *        40(R)     60(R)
                     *        /    \    / \
                     *       甲    乙  丙  70(B)
                     *                     /  \
                     *                    丁   戊
                     */
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateRight(parentOf(parentOf(x)));
                }
            } else {
                Node y = leftOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == leftOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateRight(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateLeft(parentOf(parentOf(x)));
                }
            }
        }
        root.color = BLACK;
    }

    // ==========================================
    // 删除逻辑
    // ==========================================
    public void delete(int key) {
        System.out.println("\n========================================");
        System.out.println("===> 尝试删除节点: " + key);
        System.out.println("========================================");

        Node p = getNode(key);
        if (p == null) {
            System.out.println("未找到节点 " + key + "，无需删除。");
            return;
        }

        deleteNode(p);
    }

    /**
     * 删除一个节点，节点分3种：
     * <p>
     * A、2个孩子; B、一个孩子; C、没有孩子;
     * <p>
     * A、节点如果有2个孩子，那么找到他的中序直接后继，肯定是个叶子节点，或者是个只有右孩子的单孩节点；
     * <p>
     * 【假设直接后继节点还有左孩子、则左孩子小于当前节点，同时大于当前节点，那么此节点不会是直接后继】
     *
     * A变成B或者C
     *
     */
    private void deleteNode(Node p) {
        if (p.left != null && p.right != null) {
            // 双孩节点，找到直接后继节点
            Node s = successor(p);
            // 交换值，但没有交换颜色，恰好不会改变树的颜色属性。因为是直接后继节点，整个树的结构也不会变。
            p.key = s.key;
            // 待删除p节点已经没有，只是相当于存在了2个s
            p = s;
            // 之前的直接后继s，现在已经多余了，将其赋值给p，变成待删除节点
            // 将双孩节点，变成单孩或者纯叶子节点
        }

        Node replacement = (p.left != null ? p.left : p.right);

        if (replacement != null) {
            // 如果存在孩子节点，则将孩子上升一级，替代当前的待删除节点。
            // 因为只有一个孩子，所以，正好直接替换
            replacement.parent = p.parent;
            if (p.parent == null) {
                // 删除的是root，则把孩子节点替换成root
                root = replacement;
            } else if (p == p.parent.left) {
                // 待删除节点是左孩子，则把孩子节点替换给父亲的左孩子
                p.parent.left = replacement;
            } else {
                // 待删除节点是右孩子，则把孩子节点替换给父亲的右孩子
                p.parent.right = replacement;
            }

            // 清空，当前节点的连接关系
            p.left = p.right = p.parent = null;

            if (p.color == BLACK) {
                // 删除的节点是黑，那么，以待删除节点为root的子树，黑节点的度，减1，需要调整
                fixAfterDeletion(replacement);
            }
        } else if (p.parent == null) {
            // 没有孩子，又是根节点，特殊处理
            root = null;
        } else {
            // 没有孩子节点，如果待删除节点是黑色，那么删除之后，该颗子树，黑节点的度，减1，需要调整
            if (p.color == BLACK) {
                fixAfterDeletion(p);
            }

            if (p.parent != null) { //这个地方，不知道为啥要判断
                if (p == p.parent.left) {
                    // 如果p是左孩子，则将父节点的left置null
                    p.parent.left = null;
                } else if (p == p.parent.right) {
                    p.parent.right = null;
                }
                p.parent = null;
                // p没有孩子，只要清空parent就可以了
            }
        }
    }

    private void fixAfterDeletion(Node x) {
        // 如果当前删除节点是黑的，则节点所在的父节点的整棵子树的黑色节点度-1
        while (x != root && colorOf(x) == BLACK) {
            // 调整分左、右，是镜像的
            if (x == leftOf(parentOf(x))) {
                // 待调整的是左子树
                Node sib = rightOf(parentOf(x));
                // 取出右兄弟
                if (colorOf(sib) == RED) {
                    // 右兄弟是红的，则父亲节点肯定是黑的
                    // 右兄弟的孩子节点，也肯定都是NULL或者黑的
                    /**
                     *            60(B)
                     *           /     \
                     *    [x]50(B)   70(R)[sib]
                     *        /    \   /   \
                     *       甲    乙   丙(B) 丁(B)
                     *
                     * 兄弟节点是红的，爷爷节点是黑。将兄弟节点和爷爷节点颜色互换，右子树的黑节点度不变，左子树的度-2
                     * 服务左旋之后，左子树，黑色度还是-1，右子树不变
                     *
                     *            70(B)
                     *           /     \
                     *        60(R)    丁(B)
                     *        /    \
                     *    [x]50(B) 丙(B)(sib)
                     *      /  \
                     *     甲   乙
                     */
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateLeft(parentOf(x));
                    sib = rightOf(parentOf(x));

                }

                if (colorOf(leftOf(sib)) == BLACK && colorOf(rightOf(sib)) == BLACK) {
                    // 如果右兄弟的2个孩子都是黑的
                    // 右兄弟的颜色，肯定是黑色的，因为红色，在上面已经通过左旋成黑色了
                    setColor(sib, RED);
                    // 右兄弟孩子都是黑的，变红，不影响以右兄弟为root整个子树的颜色关系；同时右子树的黑节点度-1
                    x = parentOf(x);
                    // 等于是父节点的度-1
                } else {
                    // 右兄弟的孩子，有黑有红
                    if (colorOf(rightOf(sib)) == BLACK) {
                        // 右兄弟的右孩子是黑的
                        /**
                         *              60
                         *           /     \
                         *    [x]50(B)     70(B)[sib]
                         *      /    \     /   \
                         *     甲    乙   丙(R) 丁(B)
                         *               /  \
                         *          丙左(B)  丙右(B)
                         *
                         * 右兄弟的右孩子是黑的，那么左孩子肯定是红的，因为2个都是黑的，那么在上面已经处理了
                         *
                         *              60
                         *           /     \
                         *    [x]50(B)     丙(B)[sib]
                         *      /    \       /   \
                         *     甲    乙  丙左(B)   70(R)
                         *                        /   \
                         *                    丙右(B)  丁(B)
                         *  通过调整以后，
                         */
                        setColor(leftOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateRight(sib);
                        sib = rightOf(parentOf(x));
                    }
                    // 右兄弟节点是黑，并且右兄弟的右孩子是红的，左孩子是黑的
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    // 上面2行，其实就是把右兄弟和父节点的颜色，交换了一下。
                    setColor(rightOf(sib), BLACK);
                    // 右兄弟右孩子的节点，从红变黑，右子树，黑节点度+1
                    rotateLeft(parentOf(x));
                    /**
                     *              60
                     *           /     \
                     *    [x]50(B)     丙(B)[sib]
                     *      /    \       /   \
                     *     甲    乙  丙左(B)   70(R)
                     *                        /   \
                     *                    丙右(B)  丁(B)
                     *
                     * 再经过左旋之后，之前的左子树，本身是黑节点度少1，现在用父节点(60)变黑补上了。
                     * root，之前是60，不管是红/黑，现在丙的颜色，还是和之前一样。并且因为2个孩子都是黑。红/黑都满足要求。
                     * 右子树，丙左的父节点，只是从丙变成了之前的root节点60。丙右和丁，因为父节点70从红变黑，也是度不变的
                     * 树变成如下：
                     *                丙
                     *             /      \
                     *           60(B)     70(B)
                     *          /    \     /     \
                     *      50(B)  丙左(B) 丙右(B) 丁(B)
                     */
                    x = root;
                }
            } else {
                Node sib = leftOf(parentOf(x));

                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateRight(parentOf(x));
                    sib = leftOf(parentOf(x));
                }

                if (colorOf(rightOf(sib)) == BLACK && colorOf(leftOf(sib)) == BLACK) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (colorOf(leftOf(sib)) == BLACK) {
                        setColor(rightOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateLeft(sib);
                        sib = leftOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(leftOf(sib), BLACK);
                    rotateRight(parentOf(x));
                    x = root;
                }
            }
        }
        setColor(x, BLACK);
    }

    /**
     * 这是 模拟{@link TreeMap} 内的函数
     * <p>
     * 用来查找一个中序遍历的直接后继节点
     */
    private Node successor(Node t) {
        if (t == null) {
            // 为空，则没有后继
            return null;
        } else if (t.right != null) {
            // 如果有右孩子，则直接后继在右孩子的最左边
            // 或者直接后继就是右孩子
            Node p = t.right;
            while (p.left != null) {
                p = p.left;
            }
            return p;
        } else {
            /**
             * 如果没有右孩子，则直接后继在右孩子父节点的第一个左孩子的父节点。比如50的后继是60。如下：
             *         60
             *        /
             *       40
             *      /  \
             *     30   50
             *
             * 下面这种情况，则没有后继
             * 60
             *  \
             *  70
             *    \
             *     80
             */
            Node p = t.parent;
            Node ch = t;
            while (p != null && ch == p.right) {
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }

    private Node getNode(int key) {
        Node p = root;
        while (p != null) {
            if (key < p.key) {
                p = p.left;
            } else if (key > p.key) {
                p = p.right;
            } else {
                return p;
            }
        }
        return null;
    }

    // ==========================================
    // 旋转及辅助操作
    // ==========================================
    private void rotateLeft(Node p) {
        if (p != null) {
            Node r = p.right;
            p.right = r.left;
            if (r.left != null) {
                r.left.parent = p;
            }
            r.parent = p.parent;
            if (p.parent == null) {
                root = r;
            } else if (p.parent.left == p) {
                p.parent.left = r;
            } else {
                p.parent.right = r;
            }
            r.left = p;
            p.parent = r;
        }
    }

    private void rotateRight(Node p) {
        if (p != null) {
            Node l = p.left;
            p.left = l.right;
            if (l.right != null) {
                l.right.parent = p;
            }
            l.parent = p.parent;
            if (p.parent == null) {
                root = l;
            } else if (p.parent.right == p) {
                p.parent.right = l;
            } else {
                p.parent.left = l;
            }
            l.right = p;
            p.parent = l;
        }
    }

    private Color colorOf(Node p) {
        return (p == null ? BLACK : p.color);
    }

    private Node parentOf(Node p) {
        return (p == null ? null : p.parent);
    }

    private void setColor(Node p, Color c) {
        if (p != null) {
            p.color = c;
        }
    }

    private Node leftOf(Node p) {
        return (p == null ? null : p.left);
    }

    private Node rightOf(Node p) {
        return (p == null ? null : p.right);
    }

    // ==========================================
    // 带颜色高亮的树结构打印
    // ==========================================
    public void printTree(String msg) {
        System.out.println(msg + " 打印结构");
        printTree();
    }

    // ==========================================
    // 带颜色高亮的树结构打印
    // ==========================================
    // ==========================================
    // 带颜色高亮 & 左右孩子属性标注的树结构打印
    // ==========================================
    public void printTree() {
        if (root == null) {
            System.out.println("(空树)");
            return;
        }
        printTreeHelper(root, "", true, "ROOT");
    }

    private void printTreeHelper(Node node, String indent, boolean isLast, String type) {
        if (node != null) {
            System.out.print(indent);
            if (isLast) {
                System.out.print("└── ");
                indent += "    ";
            } else {
                System.out.print("├── ");
                indent += "│   ";
            }

            // 根据颜色拼接带 ANSI Color 的节点字符串
            String ansiColor = node.color == RED ? COLOR_RED : COLOR_BLACK;
            String colorLabel = node.color == RED ? "R" : "B";

            // 格式化输出：例如 20(B)[L] 或 30(R)[R]
            System.out.println(ansiColor + node.key + "(" + colorLabel + ")[" + type + "]" + COLOR_RESET);

            printTreeHelper(node.left, indent, false, "L");
            printTreeHelper(node.right, indent, true, "R");
        }
    }

    // ==========================================
    // 测试 Demo
    // ==========================================
    public static void main(String[] args) {

        RedBlackTreeDemo rbt = new RedBlackTreeDemo();

        // 1. 插入测试
        int[] insertKeys = {100, 200, 300, 250};
        //int[] insertKeys = {100, 50, 30, 70, 20, 40, 60, 80};
        for (int key : insertKeys) {
            rbt.insert(key);
        }
        rbt.printTree("Finish");

        // 2. 删除测试
        int[] deleteKeys = {300};
        for (int key : deleteKeys) {
            rbt.delete(key);
            rbt.printTree("Finish");
        }

    }
}