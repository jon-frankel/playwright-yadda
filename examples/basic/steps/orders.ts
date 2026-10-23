import { expect } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";
import { parseCsv, parseTable, requireBlock } from "./support/blocks.js";

interface OrderLine {
  sku: string;
  qty: number;
}

interface Receipt {
  lines: { sku: string; qty: number; amount: string }[];
  total: string;
}

interface OrdersContext {
  catalogue: Map<string, { name: string; price: number }>;
  lines: OrderLine[];
  receipt?: Receipt;
  rejection?: string;
}

export const orderSteps = createStepLibrary();

// A definition that takes a block says so: `\n([\s\S]+)$` after the first line. Yadda matches the whole
// step, block included, so a step without its block is not this definition. The IDE plugin (0.8.0 and
// later) matches the same way. Before that it matched the first line only and showed "Undefined step
// reference" for these, which is why an older version of this file wrote the block as optional.

orderSteps.given(
  /^the catalogue is imported from:\n([\s\S]+)$/,
  function (this: OrdersContext, block: string) {
    const rows = parseCsv(requireBlock(block, "the catalogue is imported from:"));
    this.catalogue = new Map(
      rows.map((row) => [row.sku!, { name: row.name!, price: Number(row.price) }]),
    );
  },
);

orderSteps.given(/^the cart holds:\n([\s\S]+)$/, function (this: OrdersContext, block: string) {
  this.lines = parseTable(requireBlock(block, "the cart holds:")).map((row) => ({
    sku: row.sku!,
    qty: Number(row.qty),
  }));
});

orderSteps.when("the customer checks out", function (this: OrdersContext) {
  placeOrder(this, this.lines);
});

orderSteps.when(
  /^the customer places an order with:\n([\s\S]+)$/,
  function (this: OrdersContext, block: string) {
    const order = JSON.parse(requireBlock(block, "the customer places an order with:")) as {
      lines: OrderLine[];
    };
    placeOrder(this, order.lines);
  },
);

orderSteps.then(/^the receipt reads:\n([\s\S]+)$/, function (this: OrdersContext, block: string) {
  const expected = parseTable(requireBlock(block, "the receipt reads:"));
  expect(
    this.receipt?.lines.map(({ sku, qty, amount }) => ({ sku, qty: String(qty), amount })),
  ).toEqual(expected);
});

// A number pattern: in the outline the step reads "the order total is [total]", and the IDE fills in
// Yadda's [placeholders] from the first examples row before matching it against this.
orderSteps.then(/^the order total is ([\d.]+)$/, function (this: OrdersContext, total: string) {
  expect(this.receipt?.total).toBe(total);
});

orderSteps.then(
  /^the order is rejected with:\n([\s\S]+)$/,
  function (this: OrdersContext, block: string) {
    expect(this.rejection).toBe(requireBlock(block, "the order is rejected with:").trim());
  },
);

/** Three or more of a product earn 10% off that line. */
function placeOrder(context: OrdersContext, lines: OrderLine[]) {
  const priced: Receipt["lines"] = [];
  for (const { sku, qty } of lines) {
    const product = context.catalogue.get(sku);
    if (!product) {
      context.rejection = `Unknown product "${sku}"`;
      return;
    }
    const discount = qty >= 3 ? 0.9 : 1;
    priced.push({ sku, qty, amount: (product.price * qty * discount).toFixed(2) });
  }
  const total = priced.reduce((sum, line) => sum + Number(line.amount), 0);
  context.receipt = { lines: priced, total: total.toFixed(2) };
}
