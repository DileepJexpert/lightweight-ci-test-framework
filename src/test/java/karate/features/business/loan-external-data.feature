@smoke @business @external-data @loan
Feature: Loan applications driven from external data files

  # Demonstrates:
  #   - read() to load JSON / YAML / CSV from classpath
  #   - Separating test data from test logic (maintainability)
  #   - karate.map()    — transform an array (submit each profile, collect responses)
  #   - karate.filter() — select array elements matching a condition
  #   - print for observability during local development

  Background:
    * def newId = function(prefix){ return prefix + '-' + java.util.UUID.randomUUID() }

    # read() loads external files into a Karate variable.
    # Supported formats: JSON, YAML, CSV, JS, plain text.
    # 'classpath:' resolves from src/test/resources (or src/test/java on the test classpath).
    * def borrowers = read('classpath:karate/testdata/borrowers.json')

    * def submitProfile =
      """
      function(profile, idempotencyKey) {
        var correlationId = newId('loan-corr');
        var payload = {
          correlationId:   correlationId,
          customerId:      newId('customer'),
          requestedAmount: profile.requestedAmount,
          creditScore:     profile.creditScore,
          monthlyIncome:   profile.monthlyIncome,
          monthlyDebt:     profile.monthlyDebt,
          processingMode:  profile.mode
        };
        var result = karate.call(
          'classpath:karate/features/business/helpers/submit-loan.feature',
          { baseUrl: baseUrl, correlationId: correlationId, idempotencyKey: idempotencyKey, payload: payload }
        );
        return result.response;
      }
      """

  Scenario: Prime borrower profile loaded from JSON file is approved
    # Access a named profile from the external data object
    * def profile     = borrowers.profiles.prime
    * def application = submitProfile(profile, newId('idem'))
    * print 'External profile used:', profile.mode, profile.creditScore
    * match application.status == profile.expectedStatus

  Scenario: Subprime borrower from external data file is rejected
    * def profile     = borrowers.profiles.subprime
    * def application = submitProfile(profile, newId('idem'))
    * match application.status   == profile.expectedStatus
    * match application.decision == profile.expectedDecision

  Scenario: Bulk submissions — karate.map() to submit, karate.filter() to group results
    # Multi-line JS functions must be defined with triple quotes first,
    # then passed by reference to karate.map() / karate.filter()
    * def mapFn =
      """
      function(profile) {
        var app = submitProfile(profile, newId('idem'));
        return { label: profile.label, status: app.status, loanId: app.loanId }
      }
      """
    # karate.map() iterates the array and returns a new array of transformed elements
    * def results = karate.map(borrowers.bulkSubmissions, mapFn)

    * print 'All bulk results:', results

    # karate.filter() selects elements where the predicate returns true
    * def approved = karate.filter(results, function(r){ return r.status == 'APPROVED' })
    * def rejected = karate.filter(results, function(r){ return r.status == 'REJECTED' })

    * print 'Approved count:', approved.length
    * print 'Rejected count:', rejected.length

    # bulkSubmissions has 2 prime (APPROVED) + 1 subprime (REJECTED)
    * assert results.length  == 3
    * assert approved.length == 2
    * assert rejected.length == 1

    # Every result must have a loanId — validates the response shape
    * match each results contains { loanId: '#string' }
