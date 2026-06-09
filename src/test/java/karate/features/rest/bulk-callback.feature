@smoke @rest @bulk @callbacks
Feature: Bulk Salesforce callback processing

  # Demonstrates:
  #   - table keyword   — build a JSON array from an inline Gherkin table
  #   - karate.map()    — transform / submit each row, collect responses
  #   - karate.filter() — group results by condition (accepted vs rejected)
  #   - match each      — assert every element of an array satisfies a condition
  #   - karate.forEach()— side-effect iteration (logging each result)

  Background:
    * def newId = function(prefix){ return prefix + '-' + java.util.UUID.randomUUID() }

  Scenario: Submit multiple valid Salesforce callbacks in bulk
    # 'table' builds a JSON array; each cell is evaluated as a Karate expression.
    # String literals need single quotes; function calls are evaluated directly.
    * table customers
      | correlationId  | customerId     | firstName | lastName | email                      | source       |
      | newId('corr')  | newId('cust')  | 'Alice'   | 'Smith'  | 'alice@example.test'       | 'SALESFORCE' |
      | newId('corr')  | newId('cust')  | 'Bob'     | 'Jones'  | 'bob@example.test'         | 'SALESFORCE' |
      | newId('corr')  | newId('cust')  | 'Carol'   | 'White'  | 'carol@example.test'       | 'SALESFORCE' |

    # karate.map() iterates the array; returns a new array of { customerId, statusCode }
    * def results = karate.map(customers, function(c){
        var r = karate.call(
          'classpath:karate/features/rest/helpers/submit-callback.feature',
          { baseUrl: baseUrl, authToken: authToken, customer: c }
        );
        return { customerId: c.customerId, statusCode: r.statusCode }
      })

    * print 'Bulk callback results:', results

    # karate.filter() — only 202-Accepted responses
    * def accepted = karate.filter(results, function(r){ return r.statusCode == 202 })
    * print 'Accepted:', accepted.length, '/', results.length
    * assert accepted.length == 3

    # match each — verify every result in the array has a non-empty customerId
    * match each results contains { customerId: '#string' }

    # karate.forEach() — log each accepted entry (side-effect only, no return value)
    * karate.forEach(accepted, function(r){ karate.log('Accepted customerId:', r.customerId) })

  Scenario: Invalid callback payloads are all rejected — batch negative testing
    * table invalidCustomers
      | correlationId  | customerId | firstName | lastName | email         | source       |
      | newId('corr')  | ''         | 'Bad'     | 'User'   | 'not-an-email'| 'SALESFORCE' |
      | newId('corr')  | ''         | 'Also'    | 'Bad'    | 'no-at-sign'  | 'SALESFORCE' |

    * def statusCodes = karate.map(invalidCustomers, function(c){
        var r = karate.call(
          'classpath:karate/features/rest/helpers/submit-callback.feature',
          { baseUrl: baseUrl, authToken: authToken, customer: c }
        );
        return r.statusCode
      })

    * print 'Invalid callback status codes:', statusCodes

    # match each on a plain number array — every status must be 400
    * match each statusCodes == 400

  Scenario: Bank callbacks submitted via table with different request types
    * table bankRequests
      | requestType                | customerId     |
      | 'CUSTOMER_VERIFICATION'    | newId('cust')  |
      | 'ACCOUNT_VALIDATION'       | newId('cust')  |

    * def bankResults = karate.map(bankRequests, function(req){
        var corrId = newId('bank-corr');
        var body = {
          correlationId: corrId,
          customerId:    req.customerId,
          accountNumber: 'ACC-' + corrId,
          requestType:   req.requestType
        };
        karate.set('bankBody', body);
        var r = karate.call('classpath:karate/features/rest/helpers/submit-bank-callback.feature',
          { baseUrl: baseUrl, correlationId: corrId, requestBody: body });
        return { requestType: req.requestType, statusCode: r.statusCode }
      })

    * print 'Bank callback results:', bankResults
    * def allOk = karate.filter(bankResults, function(r){ return r.statusCode == 200 })
    * assert allOk.length == 2
