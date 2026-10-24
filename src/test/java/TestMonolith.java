import java.util.List;
import java.util.ArrayList;

public class TestMonolith {

    public List<String> processData(List<String> input) {
        List<String> result = new ArrayList<>();

        // Пример O(n)
        for (String item : input) {
            if (item != null && !item.isEmpty()) {
                result.add(item);
            }
        }

        // Пример O(n^2) - вложенные циклы
        for (int i = 0; i < input.size(); i++) {
            for (int j = 0; j < input.size(); j++) {
                if (input.get(i).equals(input.get(j))) {
                    System.out.println("Match");
                }
            }
        }

        return result;
    }

    public int calculate(int a, int b) {
        while (a > b) {
            a--;
        }
        return a;
    }
}