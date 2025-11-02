// Test a bundled LeetCode solution
// Usage: node test.js <problem-name>
// Example: node test.js fizzbuzz

const fs = require('fs');

const problemName = process.argv[2];
if (!problemName) {
  console.error('Error: Problem name required');
  console.error('Usage: node test.js <problem-name>');
  console.error('Example: node test.js fizzbuzz');
  process.exit(1);
}

const filePath = `out/${problemName}.js`;
if (!fs.existsSync(filePath)) {
  console.error(`Error: File not found: ${filePath}`);
  console.error('Build the problem first with: ./build-one.sh ' + problemName);
  process.exit(1);
}

eval(fs.readFileSync(filePath, 'utf8'));

// Test based on problem name
if (problemName === 'fizzbuzz') {
  console.log('Testing fizzBuzz(15):');
  console.log(fizzBuzz(15));
} else if (problemName === 'maxprofit') {
  console.log('Testing maxProfit([7, 1, 5, 3, 6, 4]):');
  console.log(maxProfit([7, 1, 5, 3, 6, 4]));
} else {
  console.log(`Loaded ${filePath} successfully.`);
  console.log('Add a test case for this problem in test.js');
}
