@smoke @business @schema @loan
Feature: Loan API response schema and type validation

  # Demonstrates:
  #   - Karate native fuzzy matchers: #string, #number, #boolean, #array, #object, #null
  #   - #regex for format validation
  #   - #? predicate matching (inline JS expression per field)
  #   - match each — every element in an array must match a schema
  #   - match contains — partial object match (ignores extra fields)
  #   - get keyword with JsonPath to extract arrays
  #   - karate.jsonPath() for complex path expressions
  #   - print for debug output during development

  Background:
    * def newId = function(prefix){ return prefix + '-' + java.util.UUID.randomUUID() }
    * def submitLoan =
      """
      function(mode, score, income, debt, amount, idempotencyKey) {
        var correlationId = newId('loan-corr');
        var payload = {
          correlationId: correlationId,
          customerId:    newId('customer'),
          requestedAmount: amount,
          creditScore:   score,
          monthlyIncome: income,
          monthlyDebt:   debt,
          processingMode: mode
        };
        var result = karate.call(
          'classpath:karate/features/business/helpers/submit-loan.feature',
          { baseUrl: baseUrl, correlationId: correlationId, idempotencyKey: idempotencyKey, payload: payload }
        );
        return result.response;
      }
      """

  Scenario: Approved loan response matches full type schema
    * def application = submitLoan('STANDARD', 780, 110000, 18000, 900000, newId('idem'))

    # match contains: partial object schema — validates types, not specific values
    * match application contains
      """
      {
        loanId:         '#regex [A-Za-z0-9\\-]+',
        correlationId:  '#string',
        customerId:     '#string',
        status:         '#string',
        decision:       '#string',
        offerId:        '#string',
        idempotencyKey: '#string',
        timeline:       '#[] #object'
      }
      """

    # match each — every element of the array must satisfy this schema
    * match each application.timeline == { name: '#string', outcome: '#string' }

    # #? predicate — inline JS condition that must return true
    * match each application.timeline == { name: '#? _.length > 0', outcome: '#? _.length > 0' }

    # Straight-through approval always produces exactly 8 pipeline steps
    * assert application.timeline.length == 8

    # get keyword with JsonPath — extracts an array of values from a path
    * def timelineNames    = get application.timeline[*].name
    * def timelineOutcomes = get application.timeline[*].outcome
    * print 'Pipeline steps:', timelineNames
    * print 'Outcomes:',       timelineOutcomes

    * match timelineNames contains 'LOAN_INITIATED'
    * match timelineNames contains 'OFFER_GENERATION'
    * match timelineOutcomes contains 'APPROVED'

  Scenario: Rejected loan — null field and partial match validation
    * def application = submitLoan('STANDARD', 650, 45000, 26000, 1500000, newId('idem'))

    # #null — assert the field exists but is null
    * match application.offerId == '#null'

    # match contains — assert a subset of fields without caring about the rest
    * match application contains { status: 'REJECTED', decision: 'REJECTED' }

    # Negative assertion — offerId is absent/null on a rejected loan
    * match application.offerId == null

    # Timeline must still have entries for every pipeline step attempted
    * assert application.timeline.length > 0
    * def outcomes = get application.timeline[*].outcome
    * match outcomes contains 'REJECTED'

  Scenario: Timeline entries are consistently typed across all processing modes
    * def application = submitLoan('OFFER_FAILURE', 790, 120000, 15000, 950000, newId('idem'))

    # Reference schema variable — reuse the same schema in match
    * def timelineItemSchema = { name: '#string', outcome: '#string' }
    * match application.timeline == '#[] timelineItemSchema'

    # karate.jsonPath() — full JsonPath expression for precise extraction
    * def compensationStep = karate.jsonPath(application, "$.timeline[?(@.name == 'COMPENSATION')]")
    * print 'Compensation step found:', compensationStep
    * assert compensationStep.length == 1
    * match compensationStep[0].outcome == 'COMPLETED'

    # Verify approval reversal is present
    * def reversedStatus = karate.jsonPath(application, "$.timeline[?(@.outcome == 'REVERSED')]")
    * assert reversedStatus.length > 0
