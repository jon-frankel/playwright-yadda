# Feature: Payees in Markdown

The payees list of the demo budget, written as a Markdown document. GitHub renders it as a page; a
heading is a keyword line and a list item is a step.

## Background:

- Given the demo budget is open
- And the Payees page is open

## Scenario: A payee is found by its name

- When the user filters the list for "Fast Internet"
- Then the payee list shows "Fast Internet"

## Scenario Outline: [payee] is listed

- When the user filters the list for "[payee]"
- Then the payee list shows "[payee]"

### Examples:

| payee            |
| ---------------- |
| Starting Balance |
| Deposit          |
