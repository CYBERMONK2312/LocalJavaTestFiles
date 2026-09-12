public class StringBufferExample{
    public static void main(String args[]){
        StringBuffer sb = new StringBuffer("Rushikesh"); //thread safe but slow
        System.out.println(sb.capacity());
        System.out.println(sb.length());

        StringBuilder sb1 = new StringBuilder("Rushikesh"); //not thread safe but fast
        System.out.println(sb1.capacity());
        System.out.println(sb1.length());
    }
}