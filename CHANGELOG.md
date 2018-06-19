# act-e2e CHANGE LOG

1.0.0 - 19/Jun/2018
* update act to 1.8.8-RC10
* It shall always run dependent scenario first #27
* Add `ReadContent` macro #26
* Inherit state from dependent scenario #25
* Increase HTTP timeout when running in dev mode #24
* `CookieStore` is not working as expected #23
* `RequestSpec` - rename `jsonBody` to `json` #22
* RequestSpec - add `accept` property #21
* Allow `RequestSpec` extend URL and method from parent spec #20
* Improve error reporting on invalid scenario spec #19
* NPE encountered when ResponseSpec is not specified #18
* act-e2e introduces dependency to act-sql-common #17

0.0.8
* Update act to 1.8.8-RC9
* Update act-sql-common to 1.4.1
* Display error reason on e2e web UI #16
* Add Empty verifier to check if a String response is empty #15
* When run automated e2e test it show exit with an non-zero number #14
* It shows the rest interactions passed with after one interaction failed #13
* Provide a mechanism to store/fetch a data with name #11

0.0.7 - 30/May/2018
* Use `LinkedHashMap` for Request and Response internal data structure #12

0.0.6 - 28/May/2018
* Support getting last object's value through traversal path #7
* `IllegalArgumentException` encountered during clear fixture process #6

0.0.5 - 28/May/2018
* Support testing html response #5
* Support testing objects in unorderred list #4

0.0.4 - 20/May/2018
* It shall support verifying JSONObject in a list #3

0.0.3 - 20/May/2018
* `E2E.clearFixtures` failed when there are relationships between models #2
* update act to 1.8.8-RC7

0.0.2
* Support running e2e in browser #1
* update act to 1.8.8-RC6

0.0.1
* First version
