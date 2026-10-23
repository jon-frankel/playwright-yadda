# Expect: nothing marked. This is Yadda's own style, which Gherkin's parser knows nothing about.
#
# 1. A line with no Given/When/Then is a step: Cmd+B on "the customer checks out" goes to steps/orders.ts.
# 2. "Where:" is "Examples:", and rows need no pipes: they are table rows (the columns are coloured as a
#    table's), and Reformat Code (Cmd+Alt+L) lines them up under the heading and leaves them there.
# 3. The "@skip" line annotates the row under it and stays in the table; so do the dashed lines that group a
#    row written over several lines.
# 4. Delete "| 216.00" from the last row of the first table, then type a "|" after "3   ": it pads the row so
#    the pipe lands under the one above, as in a table with outer pipes. (Yadda counts the fields of a row,
#    so the row is marked as an error until you finish it.)

@Libraries=orders
Feature: Yadda's own style

  Scenario: A cart with [qty] keyboards
    the cart holds:
      ---
      | sku  | qty   |
      | KB-1 | [qty] |
      ---
    the customer checks out
    the order total is [total]

    Where:
      qty | total
      1   | 80.00
      @skip
      2   | 160.00
      3   | 216.00

  Scenario: [sku] with a note, [note]
    the customer checks out

    where:
      sku  | note
      ---
      KB-1 | the first line
           | the second line
      ---
      MS-2 | one line
      ---
