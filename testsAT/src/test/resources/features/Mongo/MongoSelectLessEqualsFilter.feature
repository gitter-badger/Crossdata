Feature: MongoSelectLessEqualsFilter

  Scenario: [CROSSDATA-74 : MONGO NATIVE] SELECT * FROM tabletest WHERE ident <= 0;
    When I execute 'SELECT * FROM tabletest WHERE ident <= 0'
    Then The result has to have '1' rows:
      | ident-integer | name-string   | money-double  |  new-boolean  | date-date  |
      |    0          | name_0        | 10.2          |  true         | 1999-11-30 |

  Scenario: [MONGO NATIVE] SELECT * FROM tabletest WHERE ident = 10;
    When I execute 'SELECT * FROM tabletest WHERE ident <= -1'
    Then The result has to have '0' rows:
      | ident-integer | name-string   | money-double  |  new-boolean  | date-date  |


  Scenario: [MONGO NATIVE] SELECT ident AS identificador FROM tabletest WHERE ident <= 0;
    When I execute 'SELECT ident AS identificador FROM tabletest WHERE ident <= 0'
    Then The result has to have '1' rows:
      | identificador-integer |
      |    0                  |


  Scenario: [MONGO NATIVE] SELECT name AS nombre FROM tabletest WHERE name <= 'name_0';
    When I execute 'SELECT name AS nombre FROM tabletest WHERE name <= 'name_0''
    Then The result has to have '1' rows:
      | nombre-string |
      |    name_0     |

  Scenario: [MONGO NATIVE] SELECT money FROM tabletest WHERE money <= 10.2;
    When I execute 'SELECT money FROM tabletest WHERE money <= 10.2'
    Then The result has to have '1' rows:
      | money-double  |
      | 10.2          |

  Scenario: [MONGO NATIVE] SELECT new FROM tabletest WHERE new <= true;
    When I execute 'SELECT new FROM tabletest WHERE new <= true'
    Then The result has to have '10' rows:
      |  new-boolean  |
      |  true         |
      |  true         |
      |  true         |
      |  true         |
      |  true         |
      |  true         |
      |  true         |
      |  true         |
      |  true         |
      |  true         |
  Scenario: [CROSSDATA-79, CROSSDATA-81 : MONGO NATIVE] SELECT date FROM tabletest WHERE date <= '1999-11-30';
    When I execute 'SELECT date FROM tabletest WHERE date <= '1999-11-30''
    Then The result has to have '1' rows:
      | date-date  |
      | 1999-11-30 |
