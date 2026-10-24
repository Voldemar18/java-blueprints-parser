//Что проверяет: Находит ли парсер return и method_call (включая вызовы других классов).
//Ожидание: 1 узел return (42), 2 узла method_call (getValue(), System.out.println()).
public class Test_6_ReturnAndCalls {
    public int getValue() {
        return 42;
    }

    public void execute() {
        getValue();
        System.out.println("Executed");
    }
}