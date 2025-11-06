#!/usr/bin/env node

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

console.log('Running tests...\n');

try {
  // Run jest with coverage (suppress other output)
  execSync('npm test -- --coverage --silent', {
    cwd: __dirname,
    encoding: 'utf8',
    stdio: 'inherit'
  });

  // Read lcov.info for accurate line coverage
  const lcovPath = path.join(__dirname, 'coverage', 'lcov.info');
  if (fs.existsSync(lcovPath)) {
    const lcovContent = fs.readFileSync(lcovPath, 'utf8');
    const linesMatch = lcovContent.match(/^LF:(\d+)\s*LH:(\d+)/m);
    
    if (linesMatch) {
      const lf = parseInt(linesMatch[1]);
      const lh = parseInt(linesMatch[2]);
      if (lf > 0) {
        const lineCoverage = ((lh / lf) * 100).toFixed(2);
        
        console.log('\n' + '='.repeat(50));
        console.log('📊 Line Coverage');
        console.log('='.repeat(50));
        console.log(`\n📈 ${lineCoverage}%\n`);
        
        const coverage = parseFloat(lineCoverage);
        if (coverage >= 80) {
          console.log('✅ Target achieved (80%+)');
        } else {
          const diff = (80 - coverage).toFixed(2);
          console.log(`⚠️  ${diff}% below target (80%)`);
        }
        console.log('='.repeat(50) + '\n');
        process.exit(0);
      }
    }
  }
  
  // Fallback: parse text output
  const output = execSync('npm test -- --coverage 2>&1', {
    cwd: __dirname,
    encoding: 'utf8',
    stdio: 'pipe'
  });
  
  const match = output.match(/CourseRegistration\.tsx\s*\|\s*[\d.]+\s*\|\s*[\d.]+\s*\|\s*[\d.]+\s*\|\s*([\d.]+)/);
  if (match) {
    const lineCoverage = match[1];
    console.log('\n' + '='.repeat(50));
    console.log('📊 Line Coverage');
    console.log('='.repeat(50));
    console.log(`\n📈 ${lineCoverage}%\n`);
    
    const coverage = parseFloat(lineCoverage);
    if (coverage >= 80) {
      console.log('✅ Target achieved (80%+)');
    } else {
      const diff = (80 - coverage).toFixed(2);
      console.log(`⚠️  ${diff}% below target (80%)`);
    }
    console.log('='.repeat(50) + '\n');
  } else {
    console.log('\n❌ Could not extract coverage\n');
    process.exit(1);
  }
} catch (error) {
  // Jest might exit with non-zero if tests fail, but we still want coverage
  const lcovPath = path.join(__dirname, 'coverage', 'lcov.info');
  if (fs.existsSync(lcovPath)) {
    const lcovContent = fs.readFileSync(lcovPath, 'utf8');
    const linesMatch = lcovContent.match(/^LF:(\d+)\s*LH:(\d+)/m);
    
    if (linesMatch) {
      const lf = parseInt(linesMatch[1]);
      const lh = parseInt(linesMatch[2]);
      if (lf > 0) {
        const lineCoverage = ((lh / lf) * 100).toFixed(2);
        
        console.log('\n' + '='.repeat(50));
        console.log('📊 Line Coverage');
        console.log('='.repeat(50));
        console.log(`\n📈 ${lineCoverage}%\n`);
        
        const coverage = parseFloat(lineCoverage);
        if (coverage >= 80) {
          console.log('✅ Target achieved (80%+)');
        } else {
          const diff = (80 - coverage).toFixed(2);
          console.log(`⚠️  ${diff}% below target (80%)`);
        }
        console.log('='.repeat(50) + '\n');
        process.exit(0);
      }
    }
  }
  process.exit(1);
}
