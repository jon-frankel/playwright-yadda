# Put the caret at the end of a half-typed row and type a pipe. Undo (Cmd+Z) between tries.
#
# 1. "| MS-2 | 20" in the first scenario: the pipe pads the row so it lines up under the pipe above.
# 2. Type a new row under it: "| X" then a space then a pipe, and the same for the next cell.
# 3. "| MS-2 | 2" at the very end of the file, where the table has no line after it: same thing.
# 4. Inside the text block, a pipe is just a character: nothing is padded.

@Libraries=orders
Feature: Typing in tables

  Scenario: A fenced table
    Given the cart holds:
      ---
      | sku  | qty |
      | KB-1 | 1   |
      | MS-2 | 20 
      ---
    And the order is rejected with:
      ---
      | not a table | just text
      | still text  | something
      ---

  Scenario: A table with the file's last line in it
    Given the cart holds:
      ---
      | sku  | qty |
      | KB-1 | 1   |
      | MS-2 | 2 
