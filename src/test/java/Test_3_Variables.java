//Что проверяет: Находит ли парсер переменные, объявленные внутри метода (и не засоряет ли граф счетчиками циклов).
//Ожидание: 3 узла variable (a, b, c).
public class Test_3_Variables {
    public void process() {
        int a = 10;
        String b = "test";
        double c = 3.14;
    }
}