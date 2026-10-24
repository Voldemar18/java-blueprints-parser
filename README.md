# Java Blueprints Parser Service

Бэкенд-сервис для парсинга Java-кода в AST и генерации JSON-структуры для визуализации графа (Nodes & Edges).

## Стек
* Java 17+
* Spring Boot 3.x
* JavaParser (com.github.javaparser)

## Как запустить
1. Склонируйте репозиторий.
2. Запустите `Main.java` или выполните `mvn spring-boot:run`.
3. Сервер запустится на `http://localhost:8080`.

## Как проверить (Postman) 
* **Метод:** POST
* **URL:** `http://localhost:8080/api/parse`
* **Body:** form-data
* **Key:** `file` (тип: File). Выберите любой `src/test/java` файл.

## Пример ответа (JSON)
```json
{
  "nodes": [
    { "id": "node_1", "type": "class", "text": "Test", "line": 4, "complexity": "O(1)" }
  ],
  "edges": [
    { "source": "node_1", "target": "node_2", "label": "contains" }
  ]
}
