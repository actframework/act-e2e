# act-e2e

Support end to end test of act application

## 1. Usage

### 1.1 Files

act-e2e use all settings in `e2e` profile to run e2e test. In addition to settings (found `resources/conf/e2e`), the following files in `resources/e2e` are read by act-e2e plugin to run e2e test:

* `fixtures/*`
    - stores all data fixtures files prepared for testing environment
* `scenarios.yml`
    - Defines test scenarios

#### 1.1.1 Sample Fixture file

```yaml
Course(math):
  id: 1
  name: Maths
Course(history):
  id: 2
  name: History
User(green):
  id: 1
  name: Green Luo
  birthday: 1919-01-01
  courses:
    - embed:math

User(black):
  id: 2
  name: Black Smith
  birthday: 1818-02-02
  courses:
    - embed:math
    - embed:history
User(john):
  id: 3
  name: John Brad
  courses:
    - id: 3
      name: Physics
    - id: 4
      name: Science
```

#### 1.1.2 Sample Scenario file

```yaml
Scenario(Hello Service):
  description: Test Hello Service
  interactions:
    - description: send request to hello service without parameter
      request:
        method: GET
        url: /hello
      response:
        text: Hello World
    - description: send request to hello servcie with parameter specified
      request:
        method: GET
        url: /hello?who=ActFramework
      response:
        text: Hello ActFramework
    - description: send request to hello servcie with parameter specified and requqire JSON response
      request:
        modifiers:
          - json
        method: GET
        url: /hello?who=Java
      response:
        json:
          result: Hello Java
```

1.2 Run test

First make sure you have act-e2e plugin dependency in your `pom.xml` file:

```xml
<dependency>
  <groupId>org.actframework</groupId>
  <artifactId>act-e2e</artifactId>
  <version>${act-e2e.version}</version>
</dependency>
```

At the moment the version of act-e2e version is `0.0.1`

Now you can run end to end test of the app using `e2e` profile:

```
./run_prod -p e2e
```

Note if you are using act-starter-parent-1.8.8.0 you don't need to add the dependency, and you can run e2e test with maven:

```
mvn compile act:e2e
```