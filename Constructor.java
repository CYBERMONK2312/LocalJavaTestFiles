public class Constructor {

    public Constructor() { //same name as class, no return type, called automatically when object is created
        System.out.println("This is a constructor");
    }
    
    public Constructor(String name) { //constructor overloading, multiple constructors with different parameters
        System.out.println("This is a constructor with a parameter: " + name);
    }
    
    public Constructor(int age) { //constructor overloading, multiple constructors with different parameters
        System.out.println("This is a constructor with a parameter: " + age);
    }

    public static void main(String[] args) {   
        Constructor c = new Constructor();
        Constructor c2 = new Constructor("Alice");
        Constructor c3 = new Constructor(25);

        Constructor c4;  //not initialized, will not call constructor
    }
}
