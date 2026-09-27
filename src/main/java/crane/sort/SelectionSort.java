package crane.sort;

/**
 * SelectionSort
 */
public class SelectionSort {
    public static void selectionSort(int[] arr){
        if (arr == null || arr.length < 2) {
            return;
        }

        for (int i = 0; i < arr.length-1; i++) {
            // 假设当前未排序部分的第一个元素就是最小值，记录其索引
            int minIndex = i;

            for (int j=i+1; j<arr.length; j++){
                if (arr[minIndex] > arr[j]){
                    minIndex = j;
                }
            }
            if (minIndex!=i){
                int tmp = arr[i];
                arr[i] = arr[minIndex];
                arr[minIndex] = tmp;
            }
        }
    }

}
