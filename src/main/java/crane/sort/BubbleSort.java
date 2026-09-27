package crane.sort;

/**
 * BubbleSort
 */
public class BubbleSort {

    public static void sort(int[] arr){
        if (arr == null || arr.length < 2){
            return;
        }
        for (int i=0; i< arr.length; i++){
            boolean swapped = false;
            for (int j=0; j< arr.length-1; j++){
                if (arr[j]>arr[j+1]){
                    int tmp = arr[j];
                    arr[j] = arr[j+1];
                    arr[j+1] = tmp;
                    swapped = true;
                }
            }
            if (!swapped){
                break;
            }
        }
    }
}
