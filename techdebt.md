### RapidFTR Android Tech Debt ###

NB: Each item has a score from 1 - 5 to give an indication of how much pain it is causing us (5 is the most painful).  Each item has an explanation of why the debt is painful.

1. Incomplete dependency injection. [2]

2. Storing json in column in sql database. [3]

3. Each record has 3 ids. [2]

- The ids in question are '_id' (the CouchDB id on the server), 'id' (the Sqlite id on the android app), and 'unique_identifier', an id used to link the two records together.
- This makes the code more difficult to understand, as it can be confusing working out which id should be used at any given time.

4. Integration tests take a long long time to run (30 mins) [4]

- The longer these take to run, the slower the feedback is when introducing any new code.  If people are reluctant to kick off the tests because they take so long to run, then they are much less useful when refactoring.

5. Json is posted to API as form parameter. [2]

- It's non standard for a Json API to expect the json to be bundled into a form parameter.  Unconventional practises make it more difficult for the open source community to contribute.

6. FluentRequest and FluentResponse (custom http abstraction classes) are used. [2]

- Much of the functionality of these classes can be provided by a suitable library such as Jersey, instead of using some adhoc code.  The static method that instantiates the FluentRequest makes it more difficult to mock in tests.

7. Complex inheritance trees in Activities. [3]

