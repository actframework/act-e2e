# act-e2e

Support end to end test of act application

## 1. Usage

### 1.1 Files

act-e2e use all settings in `e2e` profile to run e2e test. In addition to settings (found `resources/conf/e2e`), the following files in `resources` are read by act-e2e plugin to run e2e test:

* `resources/e2e/fixtures/*`
    - stores all data fixtures files prepared for testing environment
* `resources/e2e/scenarios.yml`
    - Defines test scenarios
* `resources/e2e/scenarios/*.yml`
    - stores test scenarios in multiple files for easier management
* `resources/e2e/requests.yml`
    - defines request templates which can be referred in request definition in scenario files

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
# Test hello service
Scenario(Hello Service):
  description: a service says hello
  interactions:
    - description: send request to hello service without parameter
      request:
        method: GET
        url: /hello
      response:
        text: Hello World # response text must be "Hello World"
    - description: send request to hello servcie with parameter specified
      request:
        method: GET
        url: /hello?who=ActFramework
      response:
        # this time we demonstrate verify text with a list of verifiers
        text:
          - eq: Hello ActFramework # value must be equal to "Hello ActFramework"
          - contains: ActFramework # value must contains "ActFramework"
          - starts: Hello # value must starts with "Hello"
          - ends: Framework # value must ends with "Framework"
    - description: send request to hello servcie with parameter specified and require JSON response
      request:
        json: true # specify accept type is application/json
        method: GET
        url: /hello?who=Java
      response:
        json: # treat result as a JSON object
          result: Hello Java # result property of hte JSON object must be "Hello World"

# Test date service
Scenario(Date Service):
  description: A service returns a date
  interactions:
    - description: send request to the service
      request:
        method: GET
        url: /date
      response:
        text:
          - before: 2000-01-01
          - after: 01/Jan/1990
    - description: send request to the service and request response be JSON format
      request:
        json: true
        method: GET
        url: /date
      response:
        json:
          result:
            - before: 2000-01-01
            - after: 01/Jan/1990

# Test todo-task service
Scenario(task-service):
  description: A basic todo task service test
  interactions:
    - description: Create the a task with description specified
      request:
        method: POST
        url: /tasks
        params:
          task.description: TaskA
      response:
        json:
          id: ...
          description: TaskA
    - description: Retrieve the Task just created
      request:
        method: GET
        url: /tasks/${last:0.id}
      response:
        json:
          id: <any>
          description: TaskA
    - description: Create a task without description
      request:
        method: POST
        url: /tasks
      response:
        status: 400
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

At the moment the version of act-e2e version is `0.0.6`. Note if your have the following parent in your pom.xml file you get act-e2e-0.0.6 automatically, no need to add it into your dependency.

```xml
 <parent>
    <groupId>org.actframework</groupId>
    <artifactId>act-starter-parent</artifactId>
    <version>1.8.8.4</version>
</parent>
```

Now you can run end to end test of the app using `e2e` profile:

```
./run_prod -p e2e
```

Note if you are using act-starter-parent-1.8.8.0 you don't need to add the dependency, and you can run e2e test with maven:

```
mvn compile act:e2e
```