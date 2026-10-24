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

## 1. Модуль парсинга (визуализация)

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
