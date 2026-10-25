# Java Blueprints Parser Service

Бэкенд-сервис для безопасного рефакторинга Java-монолита с помощью ИИ. 

Проект состоит из двух модулей в одном Spring Boot-приложении:
- **Парсер (визуализация)** — строит карту кода в виде графа `Nodes & Edges` для отрисовки блок-схемы монолита
- **Quality Gate (проверка)** — динамически компилирует и прогоняет JUnit-тесты для проверки, что оптимизация ИИ не сломала поведение кода

## Стек
* Java 23
* Spring Boot 3.2.5
* JavaParser (com.github.javaparser)
* JUnit 4
* Java Compiler API (`javax.tools`)

## Как запустить
1. Склонируйте репозиторий.
2. Выполните `mvn clean compile` (для компиляции основного кода)
3. При необходимости выполните `mvn test-compile` (чтобы скомпилировать тесты для Quality Gate)
4. Запустите `Main.java` или выполните `mvn spring-boot:run`
5. Сервер запустится на `http://localhost:8080`

Требования: JDK 23 и Maven. Для Quality Gate сначала выполните `mvn test`, чтобы собрать JUnit-класс `TestMonolithTest`.

## 1. Модуль парсинга (визуализация)

Щёлкните узел, чтобы перейти к строке исходника. Дважды щёлкните узел,
чтобы заменить соответствующую строку Java-кодом. Сервер принимает изменение
только если обновлённый файл разбирается как Java; после сохранения граф строится заново.
Изменение ограничено строкой узла: это редактор строк, а не полноценный AST-редактор.

### Как проверить (Postman)
* **Метод:** POST
* **URL:** `http://localhost:8080/api/parse`
* **Body:** form-data
* **Key:** `file` (тип: File). Выберите любой `.java` файл

### Пример ответа (JSON)
```json
{
  "nodes": [
    { "id": "node_1", "type": "class", "text": "Test", "line": 4, "complexity": "O(1)" }
  ],
  "edges": [
    { "source": "node_1", "target": "node_2", "label": "contains" }
  ]
}
```

## 2. Модуль Quality Gate (проверка оптимизации)

Сервис берёт **изменённый** (оптимизированный ИИ) `.java` код, компилирует его на лету через `JavaCompiler API`, запускает готовые JUnit-тесты организаторов и проверяет, не изменилось ли поведение программы.

### Эндпоинты

#### Вариант 1. Проверка сырого кода
* **Метод:** POST
* **URL:** `http://localhost:8080/api/quality/check?testClass=<полное_имя_тестового_класса>`
* **Headers:** `Content-Type: text/plain`
* **Body:** raw (текст изменённого `.java` кода)

#### Вариант 2. Проверка файла
* **Метод:** POST
* **URL:** `http://localhost:8080/api/quality/check-file?testClass=<полное_имя_тестового_класса>`
* **Body:** form-data
* **Key:** `file` (тип: File). Выберите изменённый `.java` файл

> `testClass` — полное имя JUnit4-тест-класса, подготовленного организаторами (например, `com.axenix.MonolithTest` или `TestMonolithTest`). Тесты должны быть скомпилированы и находиться в classpath (`target/test-classes`).

### Пример ответа (успех)
```json
{
  "success": true,
  "status": "ok",
  "totalTests": 12,
  "passedTests": 12,
  "executionTimeMs": 140,
  "compilationErrors": [],
  "testResults": [],
  "message": "Все тесты пройдены"
}
```

### Пример ответа (ошибка компиляции)
```json
{
  "success": false,
  "status": "compilation_error",
  "totalTests": 0,
  "passedTests": 0,
  "executionTimeMs": 23,
  "compilationErrors": [
    { "line": "10", "column": "5", "message": "cannot find symbol: variable x", "kind": "ERROR" }
  ],
  "testResults": [],
  "message": "line 10:5 cannot find symbol: variable x"
}
```

### Пример ответа (тест упал)
```json
{
  "success": false,
  "status": "tests_failed",
  "totalTests": 5,
  "passedTests": 4,
  "executionTimeMs": 156,
  "compilationErrors": [],
  "testResults": [
    {
      "testName": "testCalculate(com.axenix.MonolithTest)",
      "passed": false,
      "expected": "5",
      "actual": "3",
      "message": "..."
    }
  ],
  "message": "Тест testCalculate(com.axenix.MonolithTest) упал, ожидалось 5, получено 3"
}
```

## Как работает связка

1. Загрузить исходный `.java` файл монолита -> `POST /api/parse` для построения блок-схем
2. Получить оптимизированный код от ИИ
3. Отправить его на `POST /api/quality/check-file` с указанием `testClass` для проверки
4. Если `success: true` — поведение сохранено, оптимизацию можно применять
5. Если нет — смотреть `message`, `compilationErrors` или `testResults` и исправить код

## Поддержка и ограничения

- Парсер JavaParser принимает конструкции языка, которые умеет разбирать установленная версия библиотеки. Представленные рёбра — обзорная визуализация, а не формальная модель потока управления: сложные ветвления, исключения и переходы могут отображаться упрощённо.
- Редактирование через граф заменяет одну исходную строку и проверяет синтаксис. Оно не гарантирует сохранение поведения; после правки запускайте Quality Gate.
- Quality Gate пока рассчитан на `public class TestMonolith` без `package` и `TestMonolithTest` из `target/test-classes`. Другие наборы входных тестов не подключаются автоматически.
- Анализ Groq является рекомендацией. Big O и привязка к строкам сформированы моделью и могут быть неточными; поле `behavior_verified` остаётся `false`, пока код не проверен Quality Gate.
- В репозитории есть примеры `Test_1_ClassAndMethods.java` … `Test_7_MegaMonolith.java`; их наличие само по себе не означает, что они подключены к веб-проверке.
