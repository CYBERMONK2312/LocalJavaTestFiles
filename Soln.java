import java.util.*;

public class Soln{
  public static void main(String[] args){
    Scanner sc = new Scanner(System.in);
        
        int t = sc.nextInt();
        while (t-- > 0) {
            int n = sc.nextInt();
            
            for (int i = 1; i <= n; i += 2) {
                System.out.print((i + 1) + " " + i + " ");
            }
            System.out.println();
        }
        sc.close();
  }
}
