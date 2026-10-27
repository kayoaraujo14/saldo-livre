const { performance } = require('perf_hooks');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

const calls = [1, 2, 3];
async function execute(id) {
    await sleep(500); // simulate 500ms database operation
    return { id, success: true };
}

async function sequential() {
    const results = [];
    for (const call of calls) {
        results.push(await execute(call));
    }
    return results;
}

async function parallel() {
    return await Promise.all(calls.map(call => execute(call)));
}

async function run() {
    const start1 = performance.now();
    await sequential();
    const end1 = performance.now();
    console.log(`Sequential: ${end1 - start1} ms`);

    const start2 = performance.now();
    await parallel();
    const end2 = performance.now();
    console.log(`Parallel: ${end2 - start2} ms`);
}

run();
