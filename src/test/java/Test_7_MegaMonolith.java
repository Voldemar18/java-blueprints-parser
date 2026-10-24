//Этот файл нужен, чтобы показать на демо, что парсер не падает на сложном коде. Он включает в себя всё сразу.
//Ожидание: Парсер должен отработать, найти вложенные циклы, if, return, поля и вызовы методов, не упав с ошибкой.
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class Test_7_MegaMonolith {

    // Поля класса (Fields)
    private int state = 0;
    private String status = "INIT";
    private Map<String, Integer> cache = new HashMap<>();
    private List<String> history = new ArrayList<>();

    // Конструктор
    public Test_7_MegaMonolith() {
        this.status = "READY";
    }

    // Первый метод - сложная логика с вложенными циклами и ветвлениями
    public List<String> megaProcess(List<String> data) {
        if (data == null || data.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();

        // Внешний цикл for
        for (int i = 0; i < data.size(); i++) {
            String item = data.get(i);

            // Проверка на пустоту
            if (item == null || item.isEmpty()) {
                continue; // Пропускаем пустые
            }

            // Вложенный цикл for (O(n^2))
            for (int j = 0; j < data.size(); j++) {
                String other = data.get(j);

                // Сложное условие
                if (item.equals(other) && i != j) {
                    state++; // Работа с полем
                    cache.put(item, state);

                    if (state > 100) {
                        status = "OVERFLOW";
                        break; // Выход из внутреннего цикла
                    }
                }
            }

            result.add(item);
            history.add(item);
        }

        // Проверка через while
        int counter = 0;
        while (counter < result.size()) {
            if (result.get(counter).length() > 50) {
                result.remove(counter);
                continue;
            }
            counter++;
        }

        return result;
    }

    // Второй метод - switch и try/catch
    public void processCommand(String command) {
        switch (command) {
            case "START":
                status = "RUNNING";
                System.out.println("Started");
                break;
            case "STOP":
                status = "STOPPED";
                System.out.println("Stopped");
                break;
            case "RESET":
                state = 0;
                status = "INIT";
                break;
            default:
                System.out.println("Unknown command");
        }

        try {
            int parsed = Integer.parseInt(command);
            if (parsed > 0) {
                System.out.println("Positive number");
            } else if (parsed < 0) {
                System.out.println("Negative number");
            } else {
                System.out.println("Zero");
            }
        } catch (NumberFormatException e) {
            System.err.println("Not a number: " + command);
        } finally {
            System.out.println("Command processed");
        }
    }

    // Третий метод - цикл forEach и возврат значения
    public int calculateTotal(List<Integer> numbers) {
        int total = 0;

        for (Integer num : numbers) {
            if (num == null) {
                continue;
            }
            total += num;

            if (total > 1000) {
                return total; // Ранний возврат
            }
        }

        return total;
    }

    // Геттеры (простые методы)
    public int getState() {
        return state;
    }

    public String getStatus() {
        return status;
    }
}