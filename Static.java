class Mobile {
        String brand;
        static int price;
    
        public Mobile(String brand, int price) {
            this.brand = brand; //this keyword refers to the current object
            Mobile.price = price; //accessing static variable using class name
        }
    
        public void display() {
            System.out.println("Brand: " + brand);
            System.out.println("Price: " + price);
        }
    
}

public class Static {
    public static void main(String[] args) {
        System.out.println("This is a static method");

        Mobile m = new Mobile("OnePlus", 499);
        Mobile m2 = new Mobile("Google", 899);
        //static variable price will be shared among all instances of Mobile class, so it will be updated to 899 for both m and m2

        System.out.println("Mobile 1:");
        m.display();

        System.out.println("Mobile 2:");
        m2.display();
    }
}
