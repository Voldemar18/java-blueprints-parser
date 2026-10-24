//Что проверяет: Находит ли парсер поля (переменные на уровне класса).
//Ожидание: 1 узел class, 2 узла field (counter, name), 1 узел method.
public class Test_2_Fields {
    private int counter = 0;
    private String name = "Vova";

    public void increment() {
        counter++;
    }
}