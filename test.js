// Test the bundled function
const fs = require('fs');
eval(fs.readFileSync('out/leetcode.js', 'utf8'));

console.log('Testing fizzBuzz(15):');
console.log(fizzBuzz(15));
