const fs = require('fs');

// We will mock the context just to run the counts function and see how long it takes.
const txs = [];
for (let i = 0; i < 10000; i++) {
  txs.push({
    id: `tx_${i}`,
    type: i % 2 === 0 ? 'expense' : 'income',
    status: i % 3 === 0 ? 'paid' : 'pending',
    category: 'Alimentação',
    totalAmount: 100,
    cardId: `card_${i % 100}`
  });
}

const cardsList = [];
for (let i = 0; i < 100; i++) {
  cardsList.push({
    id: `card_${i}`,
    cardName: `Card ${i}`,
    shared: i % 2 === 0
  });
}

const isSharedCard = (card) => card && card.shared;

function testCounts(txs, cardsList) {
  return txs.filter((t) => {
    if (!t) return false;
    const card = cardsList.find((c) => c && c.id === t.cardId);
    return isSharedCard(card) || t.reserveCardId;
  }).length;
}

const start = performance.now();
for (let i = 0; i < 100; i++) {
  testCounts(txs, cardsList);
}
const end = performance.now();
console.log(`Baseline time: ${(end - start).toFixed(2)}ms`);

function testCountsOptimized(txs, cardsList) {
  const cardsMap = new Map(cardsList.map(c => [c.id, c]));

  return txs.filter((t) => {
    if (!t) return false;
    const card = cardsMap.get(t.cardId);
    return isSharedCard(card) || t.reserveCardId;
  }).length;
}

const startOpt = performance.now();
for (let i = 0; i < 100; i++) {
  testCountsOptimized(txs, cardsList);
}
const endOpt = performance.now();
console.log(`Optimized time: ${(endOpt - startOpt).toFixed(2)}ms`);
