//Что проверяет: Находит ли парсер все три вида циклов и правильно ли считает O(n) и O(n^2).
//Ожидание: 1 loop_for_each (O(n)), 1 loop_while (O(n)), 1 loop_for (O(n^2)), 1 loop_for (O(n)).
import java.util.List;

public class Test_5_Loops {
    public void loops(List<String> items) {
        // O(n)
        for (String item : items) {
            System.out.println(item);
        }

        // O(n)
        int i = 0;
        while (i < 10) {
            i++;
        }

        // O(n^2)
        for (int j = 0; j < items.size(); j++) {
            for (int k = 0; k < items.size(); k++) {
                System.out.println("Pair");
            }
        }
    }
}