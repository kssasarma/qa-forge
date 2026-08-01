import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import CoverageMap from './CoverageMap';
import type { TestCase } from '../api/qaForgeApi';

function testCase(overrides: Partial<TestCase>): TestCase {
  return {
    id: '1',
    fileName: 'checkout_pr1.spec.ts',
    scenarioTitle: 'Checkout',
    layer: 'PLAYWRIGHT',
    userFlow: 'Checkout',
    prNumber: '1',
    status: 'ACTIVE',
    tags: [],
    lastExecutionStatus: 'PASSED',
    lastExecutionMs: 100,
    executionCount: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('CoverageMap', () => {
  it('marks a layer covered only when a test exists for that flow and layer', () => {
    render(<CoverageMap tests={[testCase({ userFlow: 'Checkout', layer: 'PLAYWRIGHT' })]} />);

    expect(screen.getByLabelText('Playwright covered')).toBeInTheDocument();
    expect(screen.getByLabelText('RestAssured not covered')).toBeInTheDocument();
    expect(screen.getByLabelText('DB Validation not covered')).toBeInTheDocument();
  });

  it('shows an empty state with no tests', () => {
    render(<CoverageMap tests={[]} />);
    expect(screen.getByText(/no active tests/i)).toBeInTheDocument();
  });
});
