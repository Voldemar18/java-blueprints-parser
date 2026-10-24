//Что проверяет: Находит ли парсер if, else и switch.
//Ожидание: 1 узел if, 1 узел else, 1 узел switch.
public class Test_4_Conditions {
    public void check(int x) {
        if (x > 10) {
            System.out.println("Big");
        } else {
            System.out.println("Small");
        }

        switch (x) {
            case 1:
                System.out.println("One");
                break;
            case 2:
                System.out.println("Two");
                break;
            default:
                System.out.println("Other");
        }
    }
}